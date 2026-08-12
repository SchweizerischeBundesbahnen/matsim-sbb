package ch.sbb.matsim.analysis.modalsplit;

import ch.sbb.matsim.analysis.tripsandlegsanalysis.RailTripsAnalyzer;
import ch.sbb.matsim.config.variables.SBBModes;
import ch.sbb.matsim.csv.CSVWriter;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.population.Leg;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.Plan;
import org.matsim.core.router.TripStructureUtils;
import org.matsim.utils.objectattributes.attributable.Attributes;

import java.util.*;

import static ch.sbb.matsim.analysis.modalsplit.MSVariables.*;
import static ch.sbb.matsim.config.variables.SBBModes.PT;
import static ch.sbb.matsim.config.variables.SBBModes.RAIL;

/**
 * Counts trip distances per mode and distance class, disaggregated by subpopulation.
 *
 * <p>Replaces {@code ModalSplitStats#analyzeDistanceClasses} and
 * {@code ModalSplitStats#writeDistanceClassesAnalysis}.
 */
public class DistanceClassCollector implements TripDataCollector {

    private final RailTripsAnalyzer railTripsAnalyzer;
    private final LongestMainModeIdentifier mainModeIdentifier = new LongestMainModeIdentifier();
    private final List<String> analysisSubpopulations;
    private final Map<String, Integer> modesInclRailFQMap;

    /** subpopulation → [modeIndex][distanceClassIndex] */
    private final Map<String, double[][]> distanceMap;

    public DistanceClassCollector(Map<String, Integer> modesInclRailFQMap,
                                   RailTripsAnalyzer railTripsAnalyzer,
                                   List<String> analysisSubpopulations) {
        this.modesInclRailFQMap = modesInclRailFQMap;
        this.railTripsAnalyzer = railTripsAnalyzer;
        this.analysisSubpopulations = analysisSubpopulations;
        this.distanceMap = initMap(modesInclRailFQMap.size(), distanceClassesLabel.size());
    }

    // -------------------------------------------------------------------------
    // TripDataCollector
    // -------------------------------------------------------------------------

    @Override
    public void collect(Person person, Plan experiencedPlan) {
        Attributes attributes = person.getAttributes();
        String subpop = CollectorUtils.getSubpopulation(attributes);
        double[][] data = distanceMap.get(subpop);
        if (data == null) return;

        for (TripStructureUtils.Trip trip : TripStructureUtils.getTrips(experiencedPlan)) {
            if (trip.getOriginActivity().getFacilityId() != null
                    && trip.getOriginActivity().getFacilityId()
                           .equals(trip.getDestinationActivity().getFacilityId())) continue;

            String mainMode = mainModeIdentifier.identifyMainMode(trip.getTripElements());
            if (mainMode.equals(SBBModes.WALK_MAIN_MAINMODE) || mainMode.equals(TransportMode.non_network_walk)) {
                mainMode = SBBModes.WALK_FOR_ANALYSIS;
            }
            Integer modeId = modesInclRailFQMap.get(mainMode);
            if (modeId == null) throw new RuntimeException("Mode not mapped: " + mainMode);

            double distance = 0;
            for (Leg leg : trip.getLegsOnly()) distance += leg.getRoute().getDistance() / 1000.0;

            data[modeId][distanceClass(distance)]++;

            if (mainMode.equals(PT)) {
                List<Leg> legs = trip.getLegsOnly();
                int firstRail = PtLegUtils.firstRailLegIndex(legs);
                int lastRail  = PtLegUtils.lastRailLegIndex(legs);
                if (firstRail >= 0) {
                    double railDist = 0;
                    for (int i = firstRail; i <= lastRail; i++) {
                        railDist += legs.get(i).getRoute().getDistance() / 1000.0;
                    }
                    int railClass = distanceClass(railDist);
                    data[modesInclRailFQMap.get(RAIL)][railClass]++;

                    double fqDist = railTripsAnalyzer.getFQDistance(trip, true) / 1000.0;
                    if (fqDist > 0) {
                        data[modesInclRailFQMap.get("railFQ")][railClass]++;
                    }
                }
            }
        }
    }

    @Override
    public void write(String outputBaseDir, String runId, double sampleSize) {
        String[] columns = new String[3 + distanceClassesLabel.size()];
        columns[0] = runID;
        columns[1] = subpopulation;
        columns[2] = mode;
        for (int i = 0; i < distanceClassesLabel.size(); i++) columns[i + 3] = distanceClassesLabel.get(i);

        try (CSVWriter writer = new CSVWriter("", columns, outputBaseDir + oNDistanceClasses)) {
            for (String subpop : analysisSubpopulations) {
                for (Map.Entry<String, Integer> modeEntry : modesInclRailFQMap.entrySet()) {
                    writer.set(runID, runId);
                    writer.set(subpopulation, subpop);
                    writer.set(mode, modeEntry.getKey());
                    for (int i = 0; i < distanceClassesLabel.size(); i++) {
                        writer.set(distanceClassesLabel.get(i), Integer.toString(
                                (int) (distanceMap.get(subpop)[modeEntry.getValue()][i] / sampleSize)));
                    }
                    writer.writeRow();
                }
            }
        } catch (Exception ex) {
            //noinspection CallToPrintStackTrace
            ex.printStackTrace();
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static int distanceClass(double distKm) {
        for (int i = 0; i < distanceClassesValue.size(); i++) {
            if (distKm <= distanceClassesValue.get(i)) return i;
        }
        return distanceClassesLabel.size() - 1; // last class = "300+"
    }

    private Map<String, double[][]> initMap(int rows, int cols) {
        Map<String, double[][]> map = new HashMap<>();
        for (String subpop : analysisSubpopulations) map.put(subpop, new double[rows][cols]);
        return map;
    }
}

