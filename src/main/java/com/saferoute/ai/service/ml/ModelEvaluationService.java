package com.saferoute.ai.service.ml;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.saferoute.ai.config.SafeRouteProperties;

import weka.classifiers.Classifier;
import weka.classifiers.Evaluation;
import weka.classifiers.functions.Logistic;
import weka.classifiers.trees.J48;
import weka.classifiers.trees.RandomForest;
import weka.core.Instances;

/**
 * Measures how good the risk model is, for the "How it works" screen:
 * <ul>
 *   <li>Random Forest (used in the app), Decision Tree (J48) and Logistic Regression compared with
 *       k-fold cross-validation: accuracy, macro F1 and Cohen's kappa;</li>
 *   <li>the Random Forest confusion matrix;</li>
 *   <li>permutation feature importance: how much accuracy drops when one feature is shuffled.</li>
 * </ul>
 * Evaluation runs in the background after startup and is cached on disk until the data changes.
 */
@Service
public class ModelEvaluationService {

    private static final Logger log = LoggerFactory.getLogger(ModelEvaluationService.class);
    private static final long SEED = 42;

    private final RiskPredictionService riskPredictionService;
    private final SafeRouteProperties properties;
    private final ObjectMapper objectMapper;
    private final AtomicReference<Map<String, Object>> metrics = new AtomicReference<>();
    private final AtomicReference<CompletableFuture<Void>> running = new AtomicReference<>();

    public ModelEvaluationService(RiskPredictionService riskPredictionService, SafeRouteProperties properties,
                                  ObjectMapper objectMapper) {
        this.riskPredictionService = riskPredictionService;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void evaluateOnStartup() {
        if (properties.ml().evaluateOnStartup()) {
            startEvaluation();
        }
    }

    /** The metrics, or a {"status": "computing"} placeholder while evaluation is still running. */
    public Map<String, Object> metrics() {
        Map<String, Object> current = metrics.get();
        if (current != null) {
            return current;
        }
        startEvaluation();
        return Map.of("status", "computing");
    }

    /** Runs the evaluation now on the calling thread (used by tests). */
    public Map<String, Object> evaluateNow() {
        Map<String, Object> result = loadCached().orElseGet(this::compute);
        metrics.set(result);
        return result;
    }

    private void startEvaluation() {
        CompletableFuture<Void> task = new CompletableFuture<>();
        if (!running.compareAndSet(null, task)) {
            return; // already running or finished
        }
        CompletableFuture.runAsync(() -> {
            try {
                evaluateNow();
            } catch (Exception ex) {
                log.warn("Model evaluation failed: {}", ex.getMessage(), ex);
                metrics.set(Map.of("status", "failed", "message", "Model evaluation failed: " + ex.getMessage()));
            } finally {
                task.complete(null);
            }
        });
    }

    private java.util.Optional<Map<String, Object>> loadCached() {
        Path file = cacheFile();
        try {
            if (Files.exists(file)) {
                @SuppressWarnings("unchecked")
                Map<String, Object> cached = objectMapper.readValue(file.toFile(), Map.class);
                if (riskPredictionService.dataFingerprint().equals(cached.get("dataFingerprint"))
                        && Integer.valueOf(properties.ml().cvFolds()).equals(cached.get("folds"))) {
                    return java.util.Optional.of(cached);
                }
            }
        } catch (Exception ex) {
            log.debug("Ignoring unreadable metrics cache: {}", ex.getMessage());
        }
        return java.util.Optional.empty();
    }

    private Map<String, Object> compute() {
        long started = System.currentTimeMillis();
        Instances data = riskPredictionService.trainingData();
        int folds = properties.ml().cvFolds();

        List<Map<String, Object>> models = new ArrayList<>();
        Evaluation forestEvaluation = null;
        for (String name : List.of("Random Forest", "Decision Tree (J48)", "Logistic Regression")) {
            try {
                Evaluation evaluation = new Evaluation(data);
                evaluation.crossValidateModel(newClassifier(name), data, folds, new Random(SEED));
                if (name.startsWith("Random")) {
                    forestEvaluation = evaluation;
                }
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("name", name);
                m.put("usedInApp", name.startsWith("Random"));
                m.put("accuracy", round(evaluation.pctCorrect() / 100.0));
                m.put("macroF1", round(macroF1(evaluation, data.numClasses())));
                m.put("kappa", round(evaluation.kappa()));
                models.add(m);
            } catch (Exception ex) {
                throw new IllegalStateException("Could not evaluate " + name, ex);
            }
        }

        List<String> labels = new ArrayList<>();
        for (int i = 0; i < data.numClasses(); i++) {
            labels.add(data.classAttribute().value(i));
        }
        Map<String, Integer> distribution = new LinkedHashMap<>();
        for (int i = 0; i < data.numInstances(); i++) {
            distribution.merge(labels.get((int) data.instance(i).classValue()), 1, Integer::sum);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", "ready");
        result.put("dataFingerprint", riskPredictionService.dataFingerprint());
        result.put("folds", folds);
        result.put("trainingRows", data.numInstances());
        result.put("classDistribution", distribution);
        result.put("models", models);
        result.put("confusion", Map.of("labels", labels, "matrix", toIntMatrix(forestEvaluation.confusionMatrix())));
        result.put("importance", permutationImportance(data));
        result.put("evaluationSeconds", round((System.currentTimeMillis() - started) / 1000.0));
        saveCache(result);
        log.info("Model evaluation finished in {} s", result.get("evaluationSeconds"));
        return result;
    }

    /** Train on 75%, then for each feature shuffle its column in the other 25% and measure the accuracy drop. */
    private List<Map<String, Object>> permutationImportance(Instances data) {
        try {
            Instances shuffled = new Instances(data);
            shuffled.randomize(new Random(SEED));
            int trainSize = (int) Math.round(shuffled.numInstances() * 0.75);
            Instances train = new Instances(shuffled, 0, trainSize);
            Instances test = new Instances(shuffled, trainSize, shuffled.numInstances() - trainSize);
            Classifier forest = newClassifier("Random Forest");
            forest.buildClassifier(train);
            double baseline = accuracy(forest, test);

            List<Map<String, Object>> importance = new ArrayList<>();
            Random random = new Random(SEED);
            for (int a = 0; a < data.numAttributes(); a++) {
                if (a == data.classIndex()) {
                    continue;
                }
                double drop = 0;
                int repeats = 3;
                for (int r = 0; r < repeats; r++) {
                    Instances permuted = new Instances(test);
                    double[] column = permuted.attributeToDoubleArray(a);
                    for (int i = column.length - 1; i > 0; i--) {
                        int j = random.nextInt(i + 1);
                        double tmp = column[i];
                        column[i] = column[j];
                        column[j] = tmp;
                    }
                    for (int i = 0; i < permuted.numInstances(); i++) {
                        permuted.instance(i).setValue(a, column[i]);
                    }
                    drop += baseline - accuracy(forest, permuted);
                }
                Map<String, Object> entry = new LinkedHashMap<>();
                entry.put("feature", data.attribute(a).name());
                entry.put("importance", round(Math.max(0, drop / repeats)));
                importance.add(entry);
            }
            importance.sort((x, y) -> Double.compare((Double) y.get("importance"), (Double) x.get("importance")));
            return importance;
        } catch (Exception ex) {
            throw new IllegalStateException("Could not compute feature importance", ex);
        }
    }

    private static double accuracy(Classifier classifier, Instances test) throws Exception {
        int correct = 0;
        for (int i = 0; i < test.numInstances(); i++) {
            if (classifier.classifyInstance(test.instance(i)) == test.instance(i).classValue()) {
                correct++;
            }
        }
        return (double) correct / test.numInstances();
    }

    private static double macroF1(Evaluation evaluation, int classes) {
        double sum = 0;
        for (int c = 0; c < classes; c++) {
            double f = evaluation.fMeasure(c);
            sum += Double.isNaN(f) ? 0 : f;
        }
        return sum / classes;
    }

    private static Classifier newClassifier(String name) {
        if (name.startsWith("Random")) {
            RandomForest forest = new RandomForest();
            forest.setNumIterations(100);
            forest.setSeed((int) SEED);
            return forest;
        }
        if (name.startsWith("Decision")) {
            return new J48();
        }
        return new Logistic();
    }

    private static int[][] toIntMatrix(double[][] matrix) {
        int[][] result = new int[matrix.length][];
        for (int i = 0; i < matrix.length; i++) {
            result[i] = new int[matrix[i].length];
            for (int j = 0; j < matrix[i].length; j++) {
                result[i][j] = (int) Math.round(matrix[i][j]);
            }
        }
        return result;
    }

    private Path cacheFile() {
        return Paths.get(properties.ml().modelDir()).resolve("metrics.json");
    }

    private void saveCache(Map<String, Object> result) {
        try {
            Files.createDirectories(cacheFile().getParent());
            objectMapper.writeValue(cacheFile().toFile(), result);
        } catch (Exception ex) {
            log.debug("Could not cache metrics: {}", ex.getMessage());
        }
    }

    private static double round(double value) {
        return Math.round(value * 1000.0) / 1000.0;
    }
}
