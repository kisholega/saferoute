import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;

/**
 * Generates the synthetic training set for the SafeRoute risk model.
 *
 * Run from the project root:   java tools/TrainingDataGenerator.java
 * Output:                      src/main/resources/ml/safety_risk_training.csv
 *
 * How the data is made:
 * 1. Pick an hour of the day and an "area type" (busy commercial street, residential lane,
 *    transit hub, park edge, isolated outskirts). Each area type has typical levels of
 *    lighting, activity and access to help, so the features are correlated the way they are
 *    in a real city, and night reduces lighting and crowds.
 * 2. Compute a latent risk score from the features with fixed, documented weights. The weights
 *    follow the parameters used in women's safety audits (lighting, openness/isolation, people
 *    around, presence of security, public transport, reported incidents) plus time of day.
 *    A night x darkness interaction makes dark streets much worse at night than in the evening.
 * 3. Add Gaussian noise and flip 4% of labels to a neighbouring class, so a model must generalise
 *    instead of memorising a formula.
 *
 * The data is synthetic. It encodes reasonable assumptions, not measured crime statistics.
 */
public class TrainingDataGenerator {

    static final int ROWS = 3000;
    static final long SEED = 20261118L;
    static final Path OUTPUT = Path.of("src", "main", "resources", "ml", "safety_risk_training.csv");

    // area: name, lighting, crowd, isolation, policeKm, hospitalKm, publicPlaces, incidents (typical daytime values)
    record Area(String name, double lighting, double crowd, double isolation, double policeKm,
                double hospitalKm, double publicPlaces, double incidents, double weight) { }

    static final List<Area> AREAS = List.of(
            new Area("busy commercial", 4.4, 4.5, 1.3, 1.0, 1.2, 8.5, 1.2, 0.26),
            new Area("transit hub", 4.2, 4.6, 1.4, 0.8, 1.5, 7.5, 2.0, 0.14),
            new Area("residential", 3.4, 2.9, 2.6, 1.8, 2.2, 4.0, 0.8, 0.30),
            new Area("park edge", 2.6, 2.2, 3.4, 2.4, 2.6, 2.5, 1.5, 0.12),
            new Area("isolated outskirts", 1.8, 1.4, 4.4, 4.5, 5.0, 1.0, 2.2, 0.18));

    public static void main(String[] args) throws IOException {
        Random random = new Random(SEED);
        List<String> lines = new ArrayList<>();
        lines.add("time_of_travel,lighting_condition,crowd_density,incident_count,road_isolation,"
                + "police_station_km,hospital_km,public_space_available,risk_category");
        int[] counts = new int[3];

        for (int i = 0; i < ROWS; i++) {
            Area area = pickArea(random);
            int hour = random.nextInt(24);
            double night = nightFactor(hour);            // 0 = midday ... 1 = 1 AM
            boolean daylight = hour >= 7 && hour < 18;

            int lighting = clamp(round(daylight ? 4.6 + random.nextGaussian() * 0.4
                    : area.lighting - 0.9 * night + random.nextGaussian() * 0.8), 1, 5);
            int crowd = clamp(round(area.crowd - 2.2 * night + random.nextGaussian() * 0.7), 1, 5);
            int isolation = clamp(round(area.isolation + 1.3 * night + random.nextGaussian() * 0.6), 1, 5);
            int incidents = clamp((int) Math.round(Math.max(0, area.incidents + 1.4 * night
                    + Math.abs(random.nextGaussian()) * 1.4 - 0.6)), 0, 10);
            double policeKm = round1(clampD(area.policeKm * (0.5 + random.nextDouble()) + random.nextGaussian() * 0.3, 0.1, 12));
            double hospitalKm = round1(clampD(area.hospitalKm * (0.5 + random.nextDouble()) + random.nextGaussian() * 0.3, 0.1, 12));
            int publicPlaces = clamp(round(area.publicPlaces * (1 - 0.65 * night) + random.nextGaussian() * 1.2), 0, 10);

            double risk = latentRisk(hour, lighting, crowd, incidents, isolation, policeKm, hospitalKm, publicPlaces)
                    + random.nextGaussian() * 0.045;
            int label = risk < 0.36 ? 0 : risk < 0.56 ? 1 : 2;
            if (random.nextDouble() < 0.04) {
                label = label == 1 ? (random.nextBoolean() ? 0 : 2) : 1; // flip to a neighbouring class
            }
            counts[label]++;
            lines.add(String.format(Locale.ROOT, "%d,%d,%d,%d,%d,%.1f,%.1f,%d,%s",
                    hour, lighting, crowd, incidents, isolation, policeKm, hospitalKm, publicPlaces,
                    new String[] {"Low Risk", "Medium Risk", "High Risk"}[label]));
        }

        Files.createDirectories(OUTPUT.getParent());
        Files.write(OUTPUT, lines);
        System.out.printf("Wrote %d rows to %s  (Low %d, Medium %d, High %d)%n",
                ROWS, OUTPUT, counts[0], counts[1], counts[2]);
    }

    /** Documented risk function: each term is normalised to 0..1 and weighted. */
    static double latentRisk(int hour, int lighting, int crowd, int incidents, int isolation,
                             double policeKm, double hospitalKm, int publicPlaces) {
        double night = nightFactor(hour);
        double dark = (5 - lighting) / 4.0;
        double empty = (5 - crowd) / 4.0;
        double isolated = (isolation - 1) / 4.0;
        double incident = Math.min(1.0, incidents / 6.0);
        double farPolice = Math.min(1.0, policeKm / 5.0);
        double farHospital = Math.min(1.0, hospitalKm / 6.0);
        double fewPlaces = (10 - publicPlaces) / 10.0;
        return 0.15 * night
                + 0.17 * dark
                + 0.13 * empty
                + 0.15 * incident
                + 0.13 * isolated
                + 0.07 * farPolice
                + 0.04 * farHospital
                + 0.07 * fewPlaces
                + 0.12 * night * dark;  // darkness matters most late at night
    }

    /** 0.0 around midday, rising through the evening to 1.0 between 23:00 and 04:00. */
    static double nightFactor(int hour) {
        return switch (hour) {
            case 23, 0, 1, 2, 3, 4 -> 1.0;
            case 22, 5 -> 0.85;
            case 21 -> 0.7;
            case 20, 6 -> 0.55;
            case 19 -> 0.4;
            case 18 -> 0.25;
            case 7, 17 -> 0.1;
            default -> 0.0;
        };
    }

    static Area pickArea(Random random) {
        double r = random.nextDouble();
        double sum = 0;
        for (Area area : AREAS) {
            sum += area.weight;
            if (r <= sum) {
                return area;
            }
        }
        return AREAS.get(AREAS.size() - 1);
    }

    static int round(double v) {
        return (int) Math.round(v);
    }

    static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    static double clampD(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    static double round1(double v) {
        return Math.round(v * 10.0) / 10.0;
    }
}
