package ch.sbb.matsim.plans.models.utils;

import ch.sbb.matsim.config.variables.Variables;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.population.Activity;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.Plan;
import org.matsim.api.core.v01.population.PlanElement;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.population.PopulationUtils;
import org.matsim.core.population.io.PopulationReader;
import org.matsim.core.scenario.ScenarioUtils;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

public class CompareActivityDurations {

    public static void main(String[] args) {
        String newPlansFile = args.length >= 1
                ? args[0]
                : "/Users/joschka.bischoff/Desktop/fulltest.plans.xml.zst";
        String oldPlansFile = args.length >= 2
                ? args[1]
                : "/Users/joschka.bischoff/Desktop/106_release.plans.xml.gz";
        Path outputDir = args.length >= 3 ? Path.of(args[2]) : Path.of("/Users/joschka.bischoff/Desktop/");

        RunStats newStats = readRunStats(newPlansFile);
        RunStats oldStats = readRunStats(oldPlansFile);

        try {
            Files.createDirectories(outputDir);
            Path durationCsv = outputDir.resolve("activity_duration_comparison.csv");
            Path startTimeCsv = outputDir.resolve("activity_start_time_distribution_comparison.csv");

            writeDurationComparisonCsv(durationCsv, "new", newStats, "old", oldStats);
            writeStartTimeDistributionComparisonCsv(startTimeCsv, "new", newStats, "old", oldStats);

            System.out.println("Wrote: " + durationCsv.toAbsolutePath());
            System.out.println("Wrote: " + startTimeCsv.toAbsolutePath());
        } catch (IOException e) {
            throw new RuntimeException("Could not write CSV output", e);
        }
    }

    private static RunStats readRunStats(String plansFile) {
        Scenario scenario = ScenarioUtils.createScenario(ConfigUtils.createConfig());
        new PopulationReader(scenario).readFile(plansFile);

        Map<String, List<Double>> durationsByType = new LinkedHashMap<>();
        Map<String, Map<Integer, Long>> startTimeDistributionByType = new LinkedHashMap<>();

        for (Person person : scenario.getPopulation().getPersons().values()) {
            if (!Variables.REGULAR.equals(PopulationUtils.getSubpopulation(person))) {
                continue;
            }
            Plan plan = person.getSelectedPlan();
            if (plan == null) {
                continue;
            }
            for (PlanElement planElement : plan.getPlanElements()) {
                if (!(planElement instanceof Activity activity)) {
                    continue;
                }
                if (activity.getType().equals("home")){
                   if (activity.getStartTime().isUndefined()) {
                       activity.setStartTime(activity.getEndTime().orElse(24*3600));
                   }
                   if (activity.getEndTime().isUndefined()) {
                       activity.setEndTime(activity.getStartTime().seconds());
                   }
                }
                if (!activity.getStartTime().isDefined() || !activity.getEndTime().isDefined()) {
                    continue;
                }

                double startTime = activity.getStartTime().seconds();
                double endTime = activity.getEndTime().seconds();
                double duration = endTime - startTime;

                String activityType = activity.getType();
                durationsByType.computeIfAbsent(activityType, unused -> new ArrayList<>()).add(duration);

                int startHour = (int) Math.floor(startTime / 3600.0);
                startTimeDistributionByType
                        .computeIfAbsent(activityType, unused -> new TreeMap<>())
                        .merge(startHour, 1L, Long::sum);
            }
        }

        Map<String, DurationStats> durationStatsByType = new LinkedHashMap<>();
        for (Map.Entry<String, List<Double>> entry : durationsByType.entrySet()) {
            durationStatsByType.put(entry.getKey(), DurationStats.fromDurations(entry.getValue()));
        }

        return new RunStats(durationStatsByType, startTimeDistributionByType);
    }

    private static void writeDurationComparisonCsv(
            Path csvFile,
            String runAName,
            RunStats runA,
            String runBName,
            RunStats runB
    ) throws IOException {
        Set<String> allActivityTypes = new TreeSet<>();
        allActivityTypes.addAll(runA.durationStatsByType.keySet());
        allActivityTypes.addAll(runB.durationStatsByType.keySet());

        try (BufferedWriter writer = Files.newBufferedWriter(csvFile)) {
            writer.write("activityType;count_" + runAName + ";count_" + runBName
                    + ";avgSec_" + runAName + ";avgSec_" + runBName + ";avgSec_diff"
                    + ";medianSec_" + runAName + ";medianSec_" + runBName + ";medianSec_diff"
                    + ";minSec_" + runAName + ";minSec_" + runBName + ";minSec_diff"
                    + ";maxSec_" + runAName + ";maxSec_" + runBName + ";maxSec_diff\n");

            for (String activityType : allActivityTypes) {
                DurationStats a = runA.durationStatsByType.getOrDefault(activityType, DurationStats.empty());
                DurationStats b = runB.durationStatsByType.getOrDefault(activityType, DurationStats.empty());

                writer.write(escape(activityType) + ";"
                        + a.count + ";" + b.count + ";"
                        + format(a.avg) + ";" + format(b.avg) + ";" + format(diff(a.avg, b.avg)) + ";"
                        + format(a.median) + ";" + format(b.median) + ";" + format(diff(a.median, b.median)) + ";"
                        + format(a.min) + ";" + format(b.min) + ";" + format(diff(a.min, b.min)) + ";"
                        + format(a.max) + ";" + format(b.max) + ";" + format(diff(a.max, b.max)) + "\n");
            }
        }
    }

    private static void writeStartTimeDistributionComparisonCsv(
            Path csvFile,
            String runAName,
            RunStats runA,
            String runBName,
            RunStats runB
    ) throws IOException {
        Set<String> allActivityTypes = new TreeSet<>();
        allActivityTypes.addAll(runA.startTimeDistributionByType.keySet());
        allActivityTypes.addAll(runB.startTimeDistributionByType.keySet());

        try (BufferedWriter writer = Files.newBufferedWriter(csvFile)) {
            writer.write("activityType;startHour;count_" + runAName + ";count_" + runBName + ";count_diff\n");

            for (String activityType : allActivityTypes) {
                Map<Integer, Long> aDist = runA.startTimeDistributionByType.getOrDefault(activityType, Map.of());
                Map<Integer, Long> bDist = runB.startTimeDistributionByType.getOrDefault(activityType, Map.of());
                Set<Integer> allHours = new TreeSet<>();
                allHours.addAll(aDist.keySet());
                allHours.addAll(bDist.keySet());

                for (Integer hour : allHours) {
                    long aCount = aDist.getOrDefault(hour, 0L);
                    long bCount = bDist.getOrDefault(hour, 0L);
                    writer.write(escape(activityType) + ";" + hour + ";" + aCount + ";" + bCount + ";" + (aCount - bCount) + "\n");
                }
            }
        }
    }

    private static double diff(double a, double b) {
        if (Double.isNaN(a) || Double.isNaN(b)) {
            return Double.NaN;
        }
        return a - b;
    }

    private static String format(double value) {
        if (Double.isNaN(value)) {
            return "";
        }
        return String.format(Locale.ROOT, "%.2f", value);
    }

    private static String escape(String value) {
        return value.replace(';', '_');
    }

    private static final class RunStats {
        final Map<String, DurationStats> durationStatsByType;
        final Map<String, Map<Integer, Long>> startTimeDistributionByType;

        private RunStats(
                Map<String, DurationStats> durationStatsByType,
                Map<String, Map<Integer, Long>> startTimeDistributionByType
        ) {
            this.durationStatsByType = durationStatsByType;
            this.startTimeDistributionByType = startTimeDistributionByType;
        }
    }

    private static final class DurationStats {
        final long count;
        final double avg;
        final double median;
        final double min;
        final double max;

        private DurationStats(long count, double avg, double median, double min, double max) {
            this.count = count;
            this.avg = avg;
            this.median = median;
            this.min = min;
            this.max = max;
        }

        static DurationStats empty() {
            return new DurationStats(0, Double.NaN, Double.NaN, Double.NaN, Double.NaN);
        }

        static DurationStats fromDurations(List<Double> durations) {
            if (durations.isEmpty()) {
                return empty();
            }

            List<Double> sorted = new ArrayList<>(durations);
            sorted.sort(Comparator.naturalOrder());

            double avg = sorted.stream().mapToDouble(Double::doubleValue).average().orElse(Double.NaN);
            double min = sorted.get(0);
            double max = sorted.get(sorted.size() - 1);
            double median;
            int n = sorted.size();
            if (n % 2 == 0) {
                median = (sorted.get(n / 2 - 1) + sorted.get(n / 2)) / 2.0;
            } else {
                median = sorted.get(n / 2);
            }
            return new DurationStats(n, avg, median, min, max);
        }
    }
}
