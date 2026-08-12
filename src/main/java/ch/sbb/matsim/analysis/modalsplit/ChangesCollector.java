package ch.sbb.matsim.analysis.modalsplit;

import ch.sbb.matsim.analysis.tripsandlegsanalysis.RailTripsAnalyzer;
import ch.sbb.matsim.csv.CSVWriter;
import org.matsim.api.core.v01.population.Leg;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.Plan;
import org.matsim.core.router.TripStructureUtils;
import org.matsim.utils.objectattributes.attributable.Attributes;

import java.util.*;

import static ch.sbb.matsim.analysis.modalsplit.MSVariables.*;

/**
 * Counts rail-trip transfer patterns (number of legs) disaggregated by subpopulation and distance class.
 *
 * <p>Replaces {@code ModalSplitStats#analyzeChanges} and {@code ModalSplitStats#writeChanges}.
 */
public class ChangesCollector implements TripDataCollector {

    private final RailTripsAnalyzer railTripsAnalyzer;
    private final List<String> analysisSubpopulations;

    /** subpopulation → [changeTypeIndex][legCountIndex] */
    private final Map<String, double[][]> changeMap;
    private final Map<String, double[][]> changePkmMap;
    private final Map<String, double[][]> distanceChangeMap;
    private final Map<String, double[][]> distanceChangePkmMap;

    public ChangesCollector(RailTripsAnalyzer railTripsAnalyzer, List<String> analysisSubpopulations) {
        this.railTripsAnalyzer = railTripsAnalyzer;
        this.analysisSubpopulations = analysisSubpopulations;
        int types = changeOrderList.size();
        int labels = changeLabelList.size();
        int distTypes = types * distanceClassesLabel.size();
        this.changeMap           = initMap(types,     labels);
        this.changePkmMap        = initMap(types,     labels);
        this.distanceChangeMap   = initMap(distTypes, labels);
        this.distanceChangePkmMap= initMap(distTypes, labels);
    }

    // -------------------------------------------------------------------------
    // TripDataCollector
    // -------------------------------------------------------------------------

    @Override
    public void collect(Person person, Plan experiencedPlan) {
        Attributes attributes = person.getAttributes();
        String subpop = CollectorUtils.getSubpopulation(attributes);
        double[][] change    = changeMap.get(subpop);
        double[][] changePkm = changePkmMap.get(subpop);
        double[][] distChange    = distanceChangeMap.get(subpop);
        double[][] distChangePkm = distanceChangePkmMap.get(subpop);
        if (change == null) return;

        for (TripStructureUtils.Trip trip : TripStructureUtils.getTrips(experiencedPlan)) {
            int ptLegs = 0;
            int railLegs = 0;
            double distance = 0;

            for (Leg leg : trip.getLegsOnly()) {
                if (PtLegUtils.isPtLeg(leg)) {
                    distance += leg.getRoute().getDistance() / 1000.0;
                    ptLegs++;
                    if (PtLegUtils.isRailLeg(leg)) railLegs++;
                }
            }
            if (ptLegs == 0) continue;

            double fqDistance = 0;
            boolean isFQ = false;
            if (railLegs > 0) {
                fqDistance = railTripsAnalyzer.getFQDistance(trip, true);
                isFQ = fqDistance > 0;
            }

            int distClassOffset = computeDistanceClassOffset(distance);
            boolean isMixed = (railLegs > 0) && (ptLegs > railLegs);

            int cappedPt   = Math.min(ptLegs,   6);
            int cappedRail = Math.min(railLegs,  6);

            if (railLegs > 0) {
                inc(change,    distChange,    distClassOffset, changeTrainAll, cappedRail - 1, 1);
                inc(changePkm, distChangePkm, distClassOffset, changeTrainAll, cappedRail - 1, distance);

                if (isMixed) {
                    inc(change,    distChange,    distClassOffset, changeOEV, cappedPt - 1, 1);
                    inc(changePkm, distChangePkm, distClassOffset, changeOEV, cappedPt - 1, distance);
                } else {
                    inc(change,    distChange,    distClassOffset, changeTrain, cappedRail - 1, 1);
                    inc(changePkm, distChangePkm, distClassOffset, changeTrain, cappedRail - 1, distance);
                }

                if (isFQ) {
                    inc(change,    distChange,    distClassOffset, changeTrainFQ, cappedRail - 1, 1);
                    inc(changePkm, distChangePkm, distClassOffset, changeTrainFQ, cappedRail - 1, fqDistance);
                }
            } else {
                inc(change,    distChange,    distClassOffset, changeOPNV, cappedPt - 1, 1);
                inc(changePkm, distChangePkm, distClassOffset, changeOPNV, cappedPt - 1, distance);
            }
        }
    }

    @Override
    public void write(String outputBaseDir, String runId, double sampleSize) {
        final String[] cols     = {runID, subpopulation, "Umsteigetyp", "0", "1", "2", "3", "4", ">=5"};
        final String[] colsDist = {runID, subpopulation, "Umsteigetyp", "distanceClass", "0", "1", "2", "3", "4", ">=5"};

        // counts
        writeChangeFile(changeMap,           outputBaseDir + oNChangesCount,         cols, runId, sampleSize, false);
        writeChangeFile(changePkmMap,        outputBaseDir + oNChangesPKM,           cols, runId, sampleSize, false);
        writeChangeFile(distanceChangeMap,   outputBaseDir + oNDistanceChangesCount, colsDist, runId, sampleSize, true);
    }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    private void inc(double[][] arr, double[][] distArr, int distOffset,
                     String changeType, int legIdx, double pfDelta) {
        int typeIdx = changeOrderList.indexOf(changeType);
        arr[typeIdx][legIdx] += pfDelta;
        distArr[distOffset + typeIdx][legIdx] += pfDelta;
    }

    private int computeDistanceClassOffset(double distance) {
        int idx = distanceClassesValue.size() - 1;
        for (int i = 0; i < distanceClassesValue.size(); i++) {
            if (distance <= distanceClassesValue.get(i)) { idx = i; break; }
        }
        return idx * changeOrderList.size();
    }

    private void writeChangeFile(Map<String, double[][]> dataMap, String path,
                                  String[] columns, String runId, double sampleSize,
                                  boolean includeDistanceColumn) {
        Map<String, Integer> mapChange = new LinkedHashMap<>();
        mapChange.put("changesTrain",   0);
        mapChange.put("changesOPNV",    1);
        mapChange.put("changesOEV",     2);
        mapChange.put("changesTrainFQ", 3);
        mapChange.put("changesTrainAll",4);

        try (CSVWriter writer = new CSVWriter("", columns, path)) {
            for (Map.Entry<String, double[][]> entry : dataMap.entrySet()) {
                if (includeDistanceColumn) {
                    for (int d = 0; d < distanceClassesLabel.size(); d++) {
                        int offset = d * changeOrderList.size();
                        for (Map.Entry<String, Integer> changeEntry : mapChange.entrySet()) {
                            writer.set("distanceClass", distanceClassesLabel.get(d));
                            writer.set(runID, runId);
                            writer.set(subpopulation, entry.getKey());
                            writer.set("Umsteigetyp", changeEntry.getKey());
                            for (int i = 0; i < 6; i++) {
                                writer.set(changeLabelList.get(i), Integer.toString(
                                        (int) (entry.getValue()[offset + changeEntry.getValue()][i] / sampleSize)));
                            }
                            writer.writeRow();
                        }
                    }
                } else {
                    for (Map.Entry<String, Integer> changeEntry : mapChange.entrySet()) {
                        writer.set(runID, runId);
                        writer.set(subpopulation, entry.getKey());
                        writer.set("Umsteigetyp", changeEntry.getKey());
                        for (int i = 0; i < 6; i++) {
                            writer.set(changeLabelList.get(i), Integer.toString(
                                    (int) (entry.getValue()[changeEntry.getValue()][i] / sampleSize)));
                        }
                        writer.writeRow();
                    }
                }
            }
        } catch (Exception ex) {
            //noinspection CallToPrintStackTrace
            ex.printStackTrace();
        }
    }

    private Map<String, double[][]> initMap(int rows, int cols) {
        Map<String, double[][]> map = new HashMap<>();
        for (String subpop : analysisSubpopulations) map.put(subpop, new double[rows][cols]);
        return map;
    }
}

