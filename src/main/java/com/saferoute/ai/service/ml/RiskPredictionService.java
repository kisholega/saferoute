package com.saferoute.ai.service.ml;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;

import com.saferoute.ai.config.SafeRouteProperties;
import com.saferoute.ai.dto.RiskPredictionRequest;
import com.saferoute.ai.dto.RiskPredictionResponse;
import com.saferoute.ai.model.RiskLevel;

import jakarta.annotation.PostConstruct;
import weka.classifiers.Classifier;
import weka.classifiers.trees.RandomForest;
import weka.core.Attribute;
import weka.core.DenseInstance;
import weka.core.Instance;
import weka.core.Instances;
import weka.core.converters.CSVLoader;

/**
 * Predicts how risky a journey is with a Weka Random Forest.
 * <p>
 * The trained model is cached on disk together with a SHA-256 fingerprint of the training CSV.
 * When the CSV changes, the fingerprint no longer matches and the model is retrained on startup.
 */
@Service
public class RiskPredictionService {

    private static final Logger log = LoggerFactory.getLogger(RiskPredictionService.class);

    static final String MODEL_FILE = "risk-model.model";
    static final String FINGERPRINT_FILE = "risk-model.sha256";
    private static final int TREES = 100;
    private static final int RANDOM_SEED = 42;

    private static final String EXPLANATION = "Estimated by a Random Forest model trained on synthetic safety data. "
            + "It is a guide, not a guarantee of personal safety.";

    private final ResourceLoader resourceLoader;
    private final SafeRouteProperties properties;

    private Instances header;
    private Instances trainingData;
    private double[] classWeights;
    private Classifier model;
    private ModelInfo modelInfo;

    public RiskPredictionService(ResourceLoader resourceLoader, SafeRouteProperties properties) {
        this.resourceLoader = resourceLoader;
        this.properties = properties;
    }

    /** What the About page shows about the model. */
    public record ModelInfo(String algorithm, int trainingRows, List<String> features, String dataFingerprint) {
    }

    @PostConstruct
    void initialize() {
        try {
            Resource resource = resourceLoader.getResource(properties.ml().trainingData());
            byte[] csv;
            try (InputStream in = resource.getInputStream()) {
                csv = in.readAllBytes();
            }
            String fingerprint = sha256(csv);
            Instances data = loadCsv(csv);
            header = new Instances(data, 0);
            trainingData = data;
            classWeights = new double[header.numClasses()];
            for (int i = 0; i < classWeights.length; i++) {
                classWeights[i] = RiskLevel.fromLabel(header.classAttribute().value(i)).getRiskWeight();
            }

            Path dir = Paths.get(properties.ml().modelDir());
            Path modelPath = dir.resolve(MODEL_FILE);
            Path fingerprintPath = dir.resolve(FINGERPRINT_FILE);

            Classifier cached = loadCachedModel(modelPath, fingerprintPath, fingerprint);
            if (cached != null) {
                model = cached;
                log.info("Loaded cached risk model from {} ({} training rows)", modelPath, data.numInstances());
            } else {
                model = train(data);
                saveModel(model, modelPath, fingerprintPath, fingerprint);
                log.info("Trained new risk model on {} rows (data fingerprint {})",
                        data.numInstances(), fingerprint.substring(0, 12));
            }

            List<String> features = new ArrayList<>();
            for (int i = 0; i < header.numAttributes(); i++) {
                if (i != header.classIndex()) {
                    features.add(header.attribute(i).name());
                }
            }
            modelInfo = new ModelInfo("Random Forest (Weka, " + TREES + " trees)", data.numInstances(),
                    List.copyOf(features), fingerprint.substring(0, 12));
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to initialise the risk prediction model.", ex);
        }
    }

    public ModelInfo getModelInfo() {
        return modelInfo;
    }

    /** The training data (a copy), for model evaluation. */
    public Instances trainingData() {
        return new Instances(trainingData);
    }

    public String dataFingerprint() {
        return modelInfo.dataFingerprint();
    }

    /**
     * Weighted risk probability (0..1) only. Used to score hundreds of route points quickly;
     * skips building explanations.
     */
    public double riskProbability(RiskPredictionRequest request) {
        validate(request);
        double[] distribution = distribution(request);
        double risk = 0.0;
        for (int i = 0; i < distribution.length; i++) {
            risk += distribution[i] * classWeights[i];
        }
        return clamp(risk, 0.0, 1.0);
    }

    public RiskPredictionResponse predictRisk(RiskPredictionRequest request) {
        validate(request);
        double[] distribution = distribution(request);

        int best = 0;
        double riskProbability = 0.0;
        for (int i = 0; i < distribution.length; i++) {
            if (distribution[i] > distribution[best]) {
                best = i;
            }
            riskProbability += distribution[i] * classWeights[i];
        }
        riskProbability = clamp(riskProbability, 0.0, 1.0);
        RiskLevel riskLevel = RiskLevel.fromLabel(header.classAttribute().value(best));
        double safetyScore = Math.round((1 - riskProbability) * 1000.0) / 10.0;

        return new RiskPredictionResponse(riskLevel, safetyScore, riskProbability, EXPLANATION, explainFactors(request));
    }

    private double[] distribution(RiskPredictionRequest request) {
        Instance input = new DenseInstance(header.numAttributes());
        input.setDataset(header);
        input.setValue(attribute("time_of_travel"), request.timeOfTravel());
        input.setValue(attribute("lighting_condition"), request.lightingCondition());
        input.setValue(attribute("crowd_density"), request.crowdDensity());
        input.setValue(attribute("incident_count"), request.reportedIncidentCount());
        input.setValue(attribute("road_isolation"), request.roadIsolationLevel());
        input.setValue(attribute("police_station_km"), request.distanceToPoliceStation());
        input.setValue(attribute("hospital_km"), request.distanceToHospital());
        input.setValue(attribute("public_space_available"), request.nearbyPublicPlaces());

        try {
            synchronized (this) {
                return model.distributionForInstance(input);
            }
        } catch (Exception ex) {
            throw new IllegalStateException("The risk model failed to evaluate the request.", ex);
        }
    }

    private Attribute attribute(String name) {
        Attribute attribute = header.attribute(name);
        if (attribute == null) {
            throw new IllegalStateException("Training data is missing the column '" + name + "'.");
        }
        return attribute;
    }

    /** The API validates with annotations; this also protects calls made from inside the server. */
    private static void validate(RiskPredictionRequest r) {
        if (r == null) {
            throw new IllegalArgumentException("Request body is required.");
        }
        requireRange("Time of travel", r.timeOfTravel(), 0, 23);
        requireRange("Lighting condition", r.lightingCondition(), 1, 5);
        requireRange("Crowd density", r.crowdDensity(), 1, 5);
        requireRange("Reported incident count", r.reportedIncidentCount(), 0, 10);
        requireRange("Road isolation level", r.roadIsolationLevel(), 1, 5);
        requireRange("Distance to police station", r.distanceToPoliceStation(), 0, 20);
        requireRange("Distance to hospital", r.distanceToHospital(), 0, 20);
        requireRange("Nearby public places", r.nearbyPublicPlaces(), 0, 10);
    }

    private static void requireRange(String name, Number value, double min, double max) {
        if (value == null) {
            throw new IllegalArgumentException(name + " is required.");
        }
        if (value.doubleValue() < min || value.doubleValue() > max) {
            throw new IllegalArgumentException(name + " must be between " + fmt(min) + " and " + fmt(max) + ".");
        }
    }

    private static String fmt(double value) {
        return value == Math.rint(value) ? String.valueOf((long) value) : String.valueOf(value);
    }

    private static List<String> explainFactors(RiskPredictionRequest r) {
        List<String> factors = new ArrayList<>();
        if (r.lightingCondition() <= 2) {
            factors.add("Poor lighting reduces visibility.");
        }
        if (r.reportedIncidentCount() >= 3) {
            factors.add("Several incidents have been reported in this area.");
        }
        if (r.roadIsolationLevel() >= 4) {
            factors.add("The roads are isolated, so help may be slow to arrive.");
        }
        if (r.crowdDensity() <= 2) {
            factors.add("Few people are likely to be around.");
        }
        if (r.distanceToPoliceStation() > 3.0) {
            factors.add("The nearest police station is relatively far.");
        }
        if (r.distanceToHospital() > 3.0) {
            factors.add("The nearest hospital is relatively far.");
        }
        if (r.nearbyPublicPlaces() <= 2) {
            factors.add("Few open public places along the way.");
        }
        if (factors.isEmpty()) {
            factors.add("Good visibility and access to help along this route.");
        }
        return factors;
    }

    static Instances loadCsv(byte[] csv) throws IOException {
        CSVLoader loader = new CSVLoader();
        loader.setSource(new ByteArrayInputStream(csv));
        Instances data = loader.getDataSet();
        data.setClassIndex(data.numAttributes() - 1);
        if (!data.classAttribute().isNominal()) {
            throw new IllegalStateException("The last CSV column (risk_category) must contain risk labels.");
        }
        return data;
    }

    static Classifier train(Instances data) throws Exception {
        RandomForest forest = new RandomForest();
        forest.setNumIterations(TREES);
        forest.setSeed(RANDOM_SEED);
        forest.buildClassifier(data);
        return forest;
    }

    private static Classifier loadCachedModel(Path modelPath, Path fingerprintPath, String fingerprint) {
        try {
            if (!Files.exists(modelPath) || !Files.exists(fingerprintPath)) {
                return null;
            }
            String stored = Files.readString(fingerprintPath, StandardCharsets.UTF_8).trim();
            if (!stored.equals(fingerprint)) {
                log.info("Training data changed since the model was saved; retraining.");
                return null;
            }
            try (ObjectInputStream in = new ObjectInputStream(Files.newInputStream(modelPath))) {
                return (Classifier) in.readObject();
            }
        } catch (Exception ex) {
            log.warn("Could not load the cached model ({}); retraining.", ex.getMessage());
            return null;
        }
    }

    private static void saveModel(Classifier model, Path modelPath, Path fingerprintPath, String fingerprint) {
        try {
            Files.createDirectories(modelPath.getParent());
            try (ObjectOutputStream out = new ObjectOutputStream(Files.newOutputStream(modelPath))) {
                out.writeObject(model);
            }
            Files.writeString(fingerprintPath, fingerprint, StandardCharsets.UTF_8);
        } catch (IOException ex) {
            log.warn("Could not cache the trained model at {} ({}). It will be retrained next start.",
                    modelPath, ex.getMessage());
        }
    }

    private static String sha256(byte[] bytes) throws NoSuchAlgorithmException {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
