package ch.sbb.matsim.analysis.modalsplit;

import ch.sbb.matsim.analysis.tripsandlegsanalysis.RailTripsAnalyzer;
import ch.sbb.matsim.config.variables.SBBModes;
import ch.sbb.matsim.csv.CSVWriter;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.population.Leg;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.Plan;
import org.matsim.core.config.Config;
import org.matsim.core.router.TripStructureUtils;
import org.matsim.pt.routes.TransitPassengerRoute;
import org.matsim.utils.objectattributes.attributable.Attributes;

import java.util.*;

import static ch.sbb.matsim.analysis.modalsplit.MSVariables.*;

/**
 * Counts trip departure times (middle of trip) and travel times binned into intervals,
 * disaggregated by subpopulation, mode, submode, and activity type.
 *
 * <p>Replaces {@code ModalSplitStats#analyzeTimes} and {@code ModalSplitStats#writeTimeSteps}.
 */
public class TimesCollector implements TripDataCollector {

    private final Config config;
    private final RailTripsAnalyzer railTripsAnalyzer;
    private final LongestMainModeIdentifier mainModeIdentifier = new LongestMainModeIdentifier();
    private final List<String> analysisSubpopulations;
    private final Map<String, Integer> variablesTimeStepsMap;

    /** subpopulation → [timeBucketIndex][variableIndex] */
    private final Map<String, int[][]> timeMap;
    private final Map<String, int[][]> travelTimeMap;

    public TimesCollector(Config config,
                           RailTripsAnalyzer railTripsAnalyzer,
                           List<String> analysisSubpopulations) {
        this.config = config;
        this.railTripsAnalyzer = railTripsAnalyzer;
        this.analysisSubpopulations = analysisSubpopulations;
        this.variablesTimeStepsMap = buildVariablesTimeStepsMap();

        int timeBuckets = (int) (config.qsim().getEndTime().seconds() / timeSplit);
        int travelTimeBuckets = (lastTravelTimeValue / travelTimeSplit) + 1;
        this.timeMap       = initMap(timeBuckets, variablesTimeStepsMap.size());
        this.travelTimeMap = initMap(travelTimeBuckets, variablesTimeStepsMap.size());
    }

    // -------------------------------------------------------------------------
    // TripDataCollector
    // -------------------------------------------------------------------------

    @Override
    public void collect(Person person, Plan experiencedPlan) {
        Attributes attributes = person.getAttributes();
        String subpop = CollectorUtils.getSubpopulation(attributes);
        int[][] timeArr       = timeMap.get(subpop);
        int[][] travelTimeArr = travelTimeMap.get(subpop);
        if (timeArr == null) return;

        for (TripStructureUtils.Trip trip : TripStructureUtils.getTrips(experiencedPlan)) {
            String mainMode = mainModeIdentifier.identifyMainMode(trip.getTripElements());
            if (mainMode.equals(SBBModes.WALK_MAIN_MAINMODE) || mainMode.equals(TransportMode.non_network_walk)) {
                mainMode = SBBModes.WALK_FOR_ANALYSIS;
            }

            boolean isRail = false;
            for (Leg leg : trip.getLegsOnly()) {
                if (PtLegUtils.isPtLeg(leg)) {
                    // suppress unused route variable
                    @SuppressWarnings("unused") TransitPassengerRoute ignored = (TransitPassengerRoute) leg.getRoute();
                    if (PtLegUtils.isRailLeg(leg)) isRail = true;
                }
            }

            String actType = trip.getDestinationActivity().getType();
            if (actType.contains(separator)) actType = actType.substring(0, actType.indexOf("_"));

            int middle = (int) ((trip.getOriginActivity().getEndTime().seconds()
                    + trip.getDestinationActivity().getStartTime().seconds()) / 2);
            int timeBucket = clamp((middle - (middle % timeSplit)) / timeSplit, timeArr.length - 1);

            int travelSecs = (int) (trip.getDestinationActivity().getStartTime().seconds()
                    - trip.getOriginActivity().getEndTime().seconds());
            int travelBucket = clamp((travelSecs - (travelSecs % travelTimeSplit)) / travelTimeSplit,
                    travelTimeArr.length - 1);

            // all-trips counter
            timeArr[timeBucket][variablesTimeStepsMap.get(all)]++;
            travelTimeArr[travelBucket][variablesTimeStepsMap.get(all)]++;

            // mode counter
            String modeKey = mode + separator + mainMode;
            if (variablesTimeStepsMap.containsKey(modeKey)) {
                timeArr[timeBucket][variablesTimeStepsMap.get(modeKey)]++;
                travelTimeArr[travelBucket][variablesTimeStepsMap.get(modeKey)]++;
            }

            // rail submode counters
            if (isRail) {
                String subRailKey = submode + separator + SBBModes.RAIL;
                timeArr[timeBucket][variablesTimeStepsMap.get(subRailKey)]++;
                travelTimeArr[travelBucket][variablesTimeStepsMap.get(subRailKey)]++;

                double fqDist = railTripsAnalyzer.getFQDistance(trip, true);
                if (fqDist > 0) {
                    String subFqKey = submode + separator + SBBModes.FQRAIL;
                    timeArr[timeBucket][variablesTimeStepsMap.get(subFqKey)]++;
                    travelTimeArr[travelBucket][variablesTimeStepsMap.get(subFqKey)]++;
                }
            }

            // activity type counter
            String actKey = toActType + separator + actType;
            if (variablesTimeStepsMap.containsKey(actKey)) {
                timeArr[timeBucket][variablesTimeStepsMap.get(actKey)]++;
                travelTimeArr[travelBucket][variablesTimeStepsMap.get(actKey)]++;
            }
        }
    }

    @Override
    public void write(String outputBaseDir, String runId, double sampleSize) {
        String[] columns = new String[3 + variablesTimeStepsMap.size()];
        columns[0] = runID;
        columns[1] = subpopulation;
        columns[2] = "time";
        int idx = 3;
        for (String var : variablesTimeStepsMap.keySet()) columns[idx++] = var;

        int maxTimeBuckets = (int) (config.qsim().getEndTime().seconds() / timeSplit);
        int maxTravelBuckets = (lastTravelTimeValue / travelTimeSplit) + 1;

        writeTimeFile(timeMap, outputBaseDir + oNMiddleTimeSteps, columns,
                runId, sampleSize, maxTimeBuckets, false);
        writeTimeFile(travelTimeMap, outputBaseDir + oNTravelTimeDistribution, columns,
                runId, sampleSize, maxTravelBuckets, true);
    }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    private void writeTimeFile(Map<String, int[][]> dataMap, String path, String[] columns,
                                String runId, double sampleSize, int bucketCount,
                                boolean lastBucketLabel) {
        try (CSVWriter writer = new CSVWriter("", columns, path)) {
            for (Map.Entry<String, int[][]> entry : dataMap.entrySet()) {
                for (int i = 0; i < bucketCount; i++) {
                    writer.set(runID, runId);
                    writer.set(subpopulation, entry.getKey());
                    if (lastBucketLabel && i == bucketCount - 1) {
                        writer.set("time", ">18000");
                    } else {
                        writer.set("time", Integer.toString(i * (lastBucketLabel ? travelTimeSplit : timeSplit)));
                    }
                    for (Map.Entry<String, Integer> var : variablesTimeStepsMap.entrySet()) {
                        writer.set(var.getKey(), Integer.toString(
                                (int) (entry.getValue()[i][var.getValue()] / sampleSize)));
                    }
                    writer.writeRow();
                }
            }
        } catch (Exception ex) {
            //noinspection CallToPrintStackTrace
            ex.printStackTrace();
        }
    }

    private static int clamp(int value, int max) {
        return Math.min(value, max);
    }

    private static Map<String, Integer> buildVariablesTimeStepsMap() {
        Map<String, Integer> vars = new LinkedHashMap<>();
        vars.put(all, 0);
        int i = 1;
        for (List<String> group : varTimeList) {
            for (String v : group) vars.put(v, i++);
        }
        return vars;
    }

    private Map<String, int[][]> initMap(int rows, int cols) {
        Map<String, int[][]> map = new HashMap<>();
        for (String subpop : analysisSubpopulations) map.put(subpop, new int[rows][cols]);
        return map;
    }
}

