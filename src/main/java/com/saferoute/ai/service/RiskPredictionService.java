package com.saferoute.ai.service;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;

import com.saferoute.ai.dto.RiskPredictionRequest;
import com.saferoute.ai.dto.RiskPredictionResponse;

import jakarta.annotation.PostConstruct;
import weka.classifiers.Classifier;
import weka.classifiers.trees.RandomForest;
import weka.core.DenseInstance;
import weka.core.Instance;
import weka.core.Instances;
import weka.core.converters.CSVLoader;

@Service
public class RiskPredictionService {

    private static final String TRAINING_DATA_PATH = "classpath:ml/safety_risk_training.csv";
    private static final Path MODEL_PATH = Paths.get("target", "ml", "random_forest_model.model");
    private final ResourceLoader resourceLoader;
    private volatile Classifier trainedModel;
    private volatile Instances trainingData;

    public RiskPredictionService(ResourceLoader resourceLoader) {
        this.resourceLoader = resourceLoader;
    }

    @PostConstruct
    public void initializeModel() {
        try {
            Instances data = getTrainingData();
            trainedModel = loadOrTrainModel(data);
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to initialize the risk prediction model.", ex);
        }
    }

    public RiskPredictionResponse predictRisk(RiskPredictionRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Request body is required.");
        }
        validateRequest(request);

        try {
            Instances data = getTrainingData();
            if (trainedModel == null) {
                trainedModel = loadOrTrainModel(data);
            }

            Instance input = new DenseInstance(data.numAttributes());
            input.setDataset(data);
            input.setValue(data.attribute("time_of_travel"), request.getTimeOfTravel());
            input.setValue(data.attribute("lighting_condition"), request.getLightingCondition());
            input.setValue(data.attribute("crowd_density"), request.getCrowdDensity());
            input.setValue(data.attribute("incident_count"), request.getReportedIncidentCount());
            input.setValue(data.attribute("road_isolation"), request.getRoadIsolationLevel());
            input.setValue(data.attribute("police_station_km"), request.getDistanceToPoliceStation());
            input.setValue(data.attribute("hospital_km"), request.getDistanceToHospital());
            input.setValue(data.attribute("public_space_available"), request.getNearbyPublicPlaces());

            double predictedIndex = trainedModel.classifyInstance(input);
            String rawRiskLevel = data.classAttribute().value((int) predictedIndex);
            String riskLevel = normalizeRiskLevel(rawRiskLevel);
            double[] distribution = trainedModel.distributionForInstance(input);
            double riskProbability = Math.max(0.0, Math.min(1.0, distribution[(int) predictedIndex]));
            double safetyScore = 100 * (1 - riskProbability);
            List<String> factors = explainFactors(request);

            return new RiskPredictionResponse(
                    riskLevel,
                    Math.max(0.0, Math.min(100.0, safetyScore)),
                    riskProbability,
                    "This result comes from a demonstration Random Forest model trained on synthetic safety data. The prediction is an estimate only, not a guarantee of personal safety.",
                    factors
            );
        } catch (Exception ex) {
            throw new IllegalArgumentException("Unable to predict risk with the model: " + ex.getMessage(), ex);
        }
    }

    private void validateRequest(RiskPredictionRequest request) {
        if (request.getTimeOfTravel() < 0 || request.getTimeOfTravel() > 23) {
            throw new IllegalArgumentException("Time of travel must be between 0 and 23.");
        }
        if (request.getLightingCondition() < 1 || request.getLightingCondition() > 5) {
            throw new IllegalArgumentException("Lighting condition must be between 1 and 5.");
        }
        if (request.getCrowdDensity() < 1 || request.getCrowdDensity() > 5) {
            throw new IllegalArgumentException("Crowd density must be between 1 and 5.");
        }
        if (request.getReportedIncidentCount() < 0 || request.getReportedIncidentCount() > 10) {
            throw new IllegalArgumentException("Incident count must be between 0 and 10.");
        }
        if (request.getRoadIsolationLevel() < 1 || request.getRoadIsolationLevel() > 5) {
            throw new IllegalArgumentException("Road isolation level must be between 1 and 5.");
        }
        if (request.getDistanceToPoliceStation() < 0 || request.getDistanceToPoliceStation() > 20) {
            throw new IllegalArgumentException("Distance to police station must be between 0 and 20 km.");
        }
        if (request.getDistanceToHospital() < 0 || request.getDistanceToHospital() > 20) {
            throw new IllegalArgumentException("Distance to hospital must be between 0 and 20 km.");
        }
        if (request.getNearbyPublicPlaces() < 0 || request.getNearbyPublicPlaces() > 10) {
            throw new IllegalArgumentException("Nearby public places must be between 0 and 10.");
        }
    }

    private Instances getTrainingData() throws Exception {
        if (trainingData == null) {
            Resource resource = resourceLoader.getResource(TRAINING_DATA_PATH);
            try (var inputStream = resource.getInputStream()) {
                CSVLoader loader = new CSVLoader();
                loader.setSource(inputStream);
                trainingData = loader.getDataSet();
            }
            trainingData.setClassIndex(trainingData.numAttributes() - 1);
        }
        return new Instances(trainingData);
    }

    private Classifier loadOrTrainModel(Instances data) throws Exception {
        File modelFile = MODEL_PATH.toFile();
        if (modelFile.exists()) {
            return loadModel(modelFile);
        }

        Classifier model = trainModel(data);
        saveModel(model, modelFile);
        return model;
    }

    private Classifier trainModel(Instances data) throws Exception {
        RandomForest forest = new RandomForest();
        forest.setNumIterations(100);
        forest.setNumExecutionSlots(2);
        forest.buildClassifier(data);
        return forest;
    }

    private void saveModel(Classifier model, File file) throws IOException {
        Files.createDirectories(file.toPath().getParent());
        try (ObjectOutputStream outputStream = new ObjectOutputStream(new FileOutputStream(file))) {
            outputStream.writeObject(model);
        }
    }

    private Classifier loadModel(File file) throws IOException, ClassNotFoundException {
        try (ObjectInputStream inputStream = new ObjectInputStream(new FileInputStream(file))) {
            return (Classifier) inputStream.readObject();
        }
    }

    private String normalizeRiskLevel(String riskLevel) {
        if (riskLevel == null) {
            return "Medium Risk";
        }
        return switch (riskLevel.trim()) {
            case "Moderate Risk" -> "Medium Risk";
            default -> riskLevel.trim();
        };
    }

    private List<String> explainFactors(RiskPredictionRequest request) {
        List<String> factors = new ArrayList<>();

        if (request.getLightingCondition() <= 2) {
            factors.add("Low lighting conditions reduce visibility and increase risk.");
        }
        if (request.getReportedIncidentCount() >= 3) {
            factors.add("Higher incident count indicates more safety concerns in the area.");
        }
        if (request.getRoadIsolationLevel() >= 4) {
            factors.add("The route is more isolated, which can limit immediate help access.");
        }
        if (request.getDistanceToPoliceStation() > 3.0) {
            factors.add("Distance to police support is relatively high.");
        }
        if (request.getDistanceToHospital() > 3.0) {
            factors.add("Distance to medical assistance is a concern.");
        }
        if (request.getNearbyPublicPlaces() <= 2) {
            factors.add("Fewer nearby public places may reduce opportunities for quick help.");
        }
        if (factors.isEmpty()) {
            factors.add("The route appears reasonably balanced in terms of visibility and support access.");
        }
        return factors;
    }
}
