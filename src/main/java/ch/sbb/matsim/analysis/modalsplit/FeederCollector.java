package ch.sbb.matsim.analysis.modalsplit;

import ch.sbb.matsim.config.variables.SBBModes;
import ch.sbb.matsim.config.variables.Variables;
import ch.sbb.matsim.csv.CSVWriter;
import ch.sbb.matsim.plans.models.utils.AttributeUtils;
import ch.sbb.matsim.zones.Zone;
import ch.sbb.matsim.zones.Zones;
import org.matsim.api.core.v01.population.Leg;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.Plan;
import org.matsim.core.router.TripStructureUtils;
import org.matsim.utils.objectattributes.attributable.Attributes;

import java.util.*;

import static ch.sbb.matsim.analysis.modalsplit.MSVariables.*;
import static ch.sbb.matsim.config.variables.SBBModes.PT;

/**
 * Counts access/egress mode split for rail trips, disaggregated by subpopulation,
 * demographics, distance class, settlement type, and origin/destination zone.
 *
 * <p>Replaces {@code ModalSplitStats#analyzeFeederModalSplit} and
 * {@code ModalSplitStats#writeFeederModalSplit}.
 */
public class FeederCollector implements TripDataCollector {


    private final LongestMainModeIdentifier mainModeIdentifier = new LongestMainModeIdentifier();
    private final Zones zones;
    private final List<String> analysisSubpopulations;
    private final Map<String, Integer> feederModesMap;
    private final Map<String, Integer> variablesMSFeederMap;

    /** subpopulation → [feederModeIndex][variableIndex] */
    private final Map<String, double[][]> accessPfMap;
    private final Map<String, double[][]> accessPkmMap;
    private final Map<String, double[][]> egressPfMap;
    private final Map<String, double[][]> egressPkmMap;

    /** zoneId → subpopulation → [feederModeIndex][variableIndex] */
    private final Map<String, Map<String, double[][]>> zonesAccessPfMap  = new HashMap<>();
    private final Map<String, Map<String, double[][]>> zonesAccessPkmMap = new HashMap<>();
    private final Map<String, Map<String, double[][]>> zonesEgressPfMap  = new HashMap<>();
    private final Map<String, Map<String, double[][]>> zonesEgressPkmMap = new HashMap<>();

    public FeederCollector(Zones zones,
                            Set<String> feederModes,
                            List<String> analysisSubpopulations) {
        this.zones = zones;

        this.analysisSubpopulations = analysisSubpopulations;
        this.feederModesMap = buildFeederModesMap(feederModes);
        this.variablesMSFeederMap = buildVariablesMSFeederMap();

        int modes = feederModesMap.size();
        int vars  = variablesMSFeederMap.size();
        this.accessPfMap  = initSubpopMap(modes, vars);
        this.accessPkmMap = initSubpopMap(modes, vars);
        this.egressPfMap  = initSubpopMap(modes, vars);
        this.egressPkmMap = initSubpopMap(modes, vars);
    }

    // -------------------------------------------------------------------------
    // TripDataCollector
    // -------------------------------------------------------------------------

    @Override
    public void collect(Person person, Plan experiencedPlan) {
        Attributes attributes = person.getAttributes();
        String subpop = CollectorUtils.getSubpopulation(attributes);
        double[][] apf  = accessPfMap.get(subpop);
        if (apf == null) return;

        double[][] apkm = accessPkmMap.get(subpop);
        double[][] epf  = egressPfMap.get(subpop);
        double[][] epkm = egressPkmMap.get(subpop);

        for (TripStructureUtils.Trip trip : TripStructureUtils.getTrips(experiencedPlan)) {
            if (trip.getOriginActivity().getFacilityId() != null
                    && trip.getOriginActivity().getFacilityId()
                           .equals(trip.getDestinationActivity().getFacilityId())) continue;

            if (!mainModeIdentifier.identifyMainMode(trip.getTripElements()).equals(PT)) continue;

            List<Leg> legs = trip.getLegsOnly();
            int firstRail = PtLegUtils.firstRailLegIndex(legs);
            int lastRail  = PtLegUtils.lastRailLegIndex(legs);
            if (firstRail < 0) continue;

            // --- access (before first rail leg) ---
            int accessModeIdx = feederModesMap.getOrDefault(SBBModes.ACCESS_EGRESS_WALK, 0);
            double distAccess = 0;
            for (int i = 0; i < firstRail; i++) {
                Leg leg = legs.get(i);
                if (leg.getRoute() != null) distAccess += leg.getRoute().getDistance();
                if (PtLegUtils.isPtLeg(leg)) {
                    Integer idx = feederModesMap.get(leg.getMode());
                    if (idx != null) accessModeIdx = idx;
                } else if (!leg.getMode().contains(SBBModes.WALK_FOR_ANALYSIS)) {
                    Integer idx = feederModesMap.get(leg.getMode());
                    if (idx != null) accessModeIdx = idx;
                }
            }

            // --- egress (after last rail leg) ---
            int egressModeIdx = feederModesMap.getOrDefault(SBBModes.ACCESS_EGRESS_WALK, 0);
            double distEgress = 0;
            for (int i = lastRail + 1; i < legs.size(); i++) {
                Leg leg = legs.get(i);
                if (leg.getRoute() != null) distEgress += leg.getRoute().getDistance();
                if (PtLegUtils.isPtLeg(leg)) {
                    Integer idx = feederModesMap.get(leg.getMode());
                    if (idx != null) egressModeIdx = idx;
                } else if (!leg.getMode().contains(SBBModes.WALK_FOR_ANALYSIS)) {
                    Integer idx = feederModesMap.get(leg.getMode());
                    if (idx != null) egressModeIdx = idx;
                }
            }

            // --- zone lookup ---
            Zone originZone = zones.findZone(trip.getOriginActivity().getCoord());
            Zone destZone   = zones.findZone(trip.getDestinationActivity().getCoord());
            String originZoneId = originZone != null ? String.valueOf(originZone.getId()) : "";
            String destZoneId   = destZone   != null ? String.valueOf(destZone.getId())   : "";
            String sl3Origin = originZone != null ? originZone.getAttribute("sl3_id").toString() : "";
            String sl3Dest   = destZone   != null ? destZone.getAttribute("sl3_id").toString()   : "";

            ensureZoneMaps(originZoneId, destZoneId);
            double[][] zApf  = zonesAccessPfMap.get(originZoneId).get(subpop);
            double[][] zApkm = zonesAccessPkmMap.get(originZoneId).get(subpop);
            double[][] zEpf  = zonesEgressPfMap.get(destZoneId).get(subpop);
            double[][] zEpkm = zonesEgressPkmMap.get(destZoneId).get(subpop);

            // all
            int allIdx = variablesMSFeederMap.get(all);
            apf[accessModeIdx][allIdx]++;  zApf[accessModeIdx][allIdx]++;
            epf[egressModeIdx][allIdx]++;  zEpf[egressModeIdx][allIdx]++;
            apkm[accessModeIdx][allIdx] += distAccess;  zApkm[accessModeIdx][allIdx] += distAccess;
            epkm[egressModeIdx][allIdx] += distEgress;  zEpkm[egressModeIdx][allIdx] += distEgress;

            // demographic subgroups
            addSubgroupStats(apf, apkm, epf, epkm, zApf, zApkm, zEpf, zEpkm,
                    accessModeIdx, egressModeIdx, distAccess, distEgress, attributes);

            // activity type
            String actType = trip.getDestinationActivity().getType();
            if (actType.contains(separator)) actType = actType.substring(0, actType.indexOf("_"));
            String actKey = toActType + separator + actType;
            for (String act : toActTypeList) {
                if (act.equals(actKey)) {
                    int vi = variablesMSFeederMap.get(act);
                    apf[accessModeIdx][vi]++;  zApf[accessModeIdx][vi]++;
                    epf[egressModeIdx][vi]++;  zEpf[egressModeIdx][vi]++;
                    apkm[accessModeIdx][vi] += distAccess;  zApkm[accessModeIdx][vi] += distAccess;
                    epkm[egressModeIdx][vi] += distEgress;  zEpkm[egressModeIdx][vi] += distEgress;
                    break;
                }
            }

            // feeder distance class
            addFeederDistClass(apf, apkm, zApf, zApkm, accessModeIdx, distAccess);
            addFeederDistClass(epf, epkm, zEpf, zEpkm, egressModeIdx, distEgress);

            // settlement type (sl3)
            addSl3(apf, apkm, zApf, zApkm, accessModeIdx, distAccess, sl3Origin);
            addSl3(epf, epkm, zEpf, zEpkm, egressModeIdx, distEgress, sl3Dest);
        }
    }

    @Override
    public void write(String outputBaseDir, String runId, double sampleSize) {
        String[] colsPop = buildColumns(false);
        String[] colsZone = buildColumns(true);

        writeAggregated(outputBaseDir + oNModalSplitFeederPF,  colsPop,  runId, sampleSize, accessPfMap,  egressPfMap);
        writeAggregated(outputBaseDir + oNModalSplitFeederPKM, colsPop,  runId, sampleSize, accessPkmMap, egressPkmMap);
        writeZonal(outputBaseDir + oNModalSplitZoneFeederPF,   colsZone, runId, sampleSize,
                zonesAccessPfMap, zonesEgressPfMap);
    }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    private void addSubgroupStats(double[][] apf, double[][] apkm,
                                   double[][] epf, double[][] epkm,
                                   double[][] zApf, double[][] zApkm,
                                   double[][] zEpf, double[][] zEpkm,
                                   int accessIdx, int egressIdx,
                                   double distAccess, double distEgress,
                                   Attributes attributes) {
        // car available
        String carAva = attributes.getAttribute(Variables.CAR_AVAIL) != null
                && AttributeUtils.hasSomething(Variables.CAR_AVAIL, attributes) ? "1" : "0";
        addSubgroupVar(apf, apkm, epf, epkm, zApf, zApkm, zEpf, zEpkm,
                accessIdx, egressIdx, distAccess, distEgress,
                Variables.CAR_AVAIL + "_" + carAva, carAvailable);

        // pt subscription
        String ptSub = "none";
        if (attributes.getAttribute(Variables.PT_SUBSCRIPTION) != null) {
            ptSub = attributes.getAttribute(Variables.PT_SUBSCRIPTION).toString().toUpperCase();
            if ("NONE".equalsIgnoreCase(ptSub)) ptSub = "none";
            if ("HTV".equals(ptSub)) ptSub = "VA";
        }
        addSubgroupVar(apf, apkm, epf, epkm, zApf, zApkm, zEpf, zEpkm,
                accessIdx, egressIdx, distAccess, distEgress,
                Variables.PT_SUBSCRIPTION + "_" + ptSub, ptSubscription);

        // car + pt combined
        addSubgroupVar(apf, apkm, epf, epkm, zApf, zApkm, zEpf, zEpkm,
                accessIdx, egressIdx, distAccess, distEgress,
                Variables.CAR_AVAIL + "_" + carAva + "_" + Variables.PT_SUBSCRIPTION + "_" + ptSub, carAndPt);

        // education
        String edu = "null";
        if (attributes.getAttribute(Variables.CURRENT_EDUCATION) != null)
            edu = attributes.getAttribute(Variables.CURRENT_EDUCATION).toString();
        addSubgroupVar(apf, apkm, epf, epkm, zApf, zApkm, zEpf, zEpkm,
                accessIdx, egressIdx, distAccess, distEgress,
                Variables.CURRENT_EDUCATION + "_" + edu, educationType);

        // employment
        String emp = "0";
        if (attributes.getAttribute(Variables.LEVEL_OF_EMPLOYMENT_CAT) != null)
            emp = attributes.getAttribute(Variables.LEVEL_OF_EMPLOYMENT_CAT).toString();
        addSubgroupVar(apf, apkm, epf, epkm, zApf, zApkm, zEpf, zEpkm,
                accessIdx, egressIdx, distAccess, distEgress,
                Variables.LEVEL_OF_EMPLOYMENT_CAT + "_" + emp, employmentRate);

        // age
        if (attributes.getAttribute(Variables.AGE_CATEGORY) != null) {
            addSubgroupVar(apf, apkm, epf, epkm, zApf, zApkm, zEpf, zEpkm,
                    accessIdx, egressIdx, distAccess, distEgress,
                    Variables.AGE_CATEGORY + "_" + attributes.getAttribute(Variables.AGE_CATEGORY), ageCategory);
        }
    }

    private void addSubgroupVar(double[][] apf, double[][] apkm,
                                 double[][] epf, double[][] epkm,
                                 double[][] zApf, double[][] zApkm,
                                 double[][] zEpf, double[][] zEpkm,
                                 int accessIdx, int egressIdx,
                                 double distAccess, double distEgress,
                                 String key, List<String> candidates) {
        for (String attr : candidates) {
            if (!attr.equals(key)) continue;
            int vi = variablesMSFeederMap.get(attr);
            apf[accessIdx][vi]++;  zApf[accessIdx][vi]++;
            epf[egressIdx][vi]++;  zEpf[egressIdx][vi]++;
            apkm[accessIdx][vi] += distAccess;  zApkm[accessIdx][vi] += distAccess;
            epkm[egressIdx][vi] += distEgress;  zEpkm[egressIdx][vi] += distEgress;
            break;
        }
    }

    private void addFeederDistClass(double[][] pf, double[][] pkm,
                                     double[][] zpf, double[][] zpkm,
                                     int modeIdx, double dist) {
        for (int i = 0; i < distanceClassesFeederValue.size(); i++) {
            if (dist <= distanceClassesFeederValue.get(i)) {
                int vi = variablesMSFeederMap.get(distanceClassesFeederLabel.get(i));
                pf[modeIdx][vi]++;  zpf[modeIdx][vi]++;
                pkm[modeIdx][vi] += dist;  zpkm[modeIdx][vi] += dist;
                break;
            }
        }
    }

    private void addSl3(double[][] pf, double[][] pkm,
                         double[][] zpf, double[][] zpkm,
                         int modeIdx, double dist, String sl3) {
        String sl3Type = switch (sl3) {
            case "1" -> sl3Urban;
            case "2" -> sl3Suburban;
            case "3" -> sl3Rural;
            default  -> null;
        };
        if (sl3Type == null) return;
        int vi = variablesMSFeederMap.get(sl3Type);
        pf[modeIdx][vi]++;  zpf[modeIdx][vi]++;
        pkm[modeIdx][vi] += dist;  zpkm[modeIdx][vi] += dist;
    }

    private void ensureZoneMaps(String originZoneId, String destZoneId) {
        if (!zonesAccessPfMap.containsKey(originZoneId)) {
            zonesAccessPfMap.put(originZoneId,  initSubpopMap(feederModesMap.size(), variablesMSFeederMap.size()));
            zonesAccessPkmMap.put(originZoneId, initSubpopMap(feederModesMap.size(), variablesMSFeederMap.size()));
        }
        if (!zonesEgressPfMap.containsKey(destZoneId)) {
            zonesEgressPfMap.put(destZoneId,  initSubpopMap(feederModesMap.size(), variablesMSFeederMap.size()));
            zonesEgressPkmMap.put(destZoneId, initSubpopMap(feederModesMap.size(), variablesMSFeederMap.size()));
        }
    }

    private void writeAggregated(String path, String[] columns, String runId, double sampleSize,
                                  Map<String, double[][]> pfData,
                                  Map<String, double[][]> pkmData) {
        try (CSVWriter w = new CSVWriter("", columns, path)) {
            for (String subpop : analysisSubpopulations) {
                // access rows
                for (Map.Entry<String, Integer> me : feederModesMap.entrySet()) {
                    w.set(runID, runId); w.set(subpopulation, subpop); w.set(mode, PT);
                    for (Map.Entry<String, Integer> ve : variablesMSFeederMap.entrySet()) {
                        if (ve.getKey().equals(accessMode)) w.set(accessMode, me.getKey());
                        else if (ve.getKey().equals(egressMode)) w.set(egressMode, all);
                        else w.set(ve.getKey(), Integer.toString(
                                (int) (pfData.get(subpop)[me.getValue()][ve.getValue()] / sampleSize)));
                    }
                    w.writeRow();
                }
                // egress rows
                for (Map.Entry<String, Integer> me : feederModesMap.entrySet()) {
                    w.set(runID, runId); w.set(subpopulation, subpop); w.set(mode, PT);
                    for (Map.Entry<String, Integer> ve : variablesMSFeederMap.entrySet()) {
                        if (ve.getKey().equals(egressMode)) w.set(egressMode, me.getKey());
                        else if (ve.getKey().equals(accessMode)) w.set(accessMode, all);
                        else w.set(ve.getKey(), Integer.toString(
                                (int) (pkmData.get(subpop)[me.getValue()][ve.getValue()] / sampleSize)));
                    }
                    w.writeRow();
                }
            }
        } catch (Exception ex) { ex.printStackTrace(); }
    }

    private void writeZonal(String path, String[] columns, String runId, double sampleSize,
                             Map<String, Map<String, double[][]>> accessData,
                             Map<String, Map<String, double[][]>> egressData) {
        try (CSVWriter w = new CSVWriter("", columns, path)) {
            int allIdx = variablesMSFeederMap.get(all);
            for (String subpop : analysisSubpopulations) {
                for (Map.Entry<String, Map<String, double[][]>> ze : accessData.entrySet()) {
                    for (Map.Entry<String, Integer> me : feederModesMap.entrySet()) {
                        if (ze.getValue().get(subpop)[me.getValue()][allIdx] <= 0) continue;
                        w.set(runID, runId); w.set(subpopulation, subpop);
                        w.set("zone", ze.getKey()); w.set(mode, PT);
                        for (Map.Entry<String, Integer> ve : variablesMSFeederMap.entrySet()) {
                            if (ve.getKey().equals(accessMode)) w.set(accessMode, me.getKey());
                            else if (ve.getKey().equals(egressMode)) w.set(egressMode, all);
                            else w.set(ve.getKey(), Integer.toString(
                                    (int) (ze.getValue().get(subpop)[me.getValue()][ve.getValue()] / sampleSize)));
                        }
                        w.writeRow();
                    }
                }
                for (Map.Entry<String, Map<String, double[][]>> ze : egressData.entrySet()) {
                    for (Map.Entry<String, Integer> me : feederModesMap.entrySet()) {
                        if (ze.getValue().get(subpop)[me.getValue()][allIdx] <= 0) continue;
                        w.set(runID, runId); w.set(subpopulation, subpop);
                        w.set("zone", ze.getKey()); w.set(mode, PT);
                        for (Map.Entry<String, Integer> ve : variablesMSFeederMap.entrySet()) {
                            if (ve.getKey().equals(egressMode)) w.set(egressMode, me.getKey());
                            else if (ve.getKey().equals(accessMode)) w.set(accessMode, all);
                            else w.set(ve.getKey(), Integer.toString(
                                    (int) (ze.getValue().get(subpop)[me.getValue()][ve.getValue()] / sampleSize)));
                        }
                        w.writeRow();
                    }
                }
            }
        } catch (Exception ex) { ex.printStackTrace(); }
    }

    private String[] buildColumns(boolean includeZone) {
        List<String> cols = new ArrayList<>(List.of(runID, subpopulation, mode));
        if (includeZone) cols.add(zone);
        cols.addAll(variablesMSFeederMap.keySet());
        return cols.toArray(new String[0]);
    }

    private static Map<String, Integer> buildFeederModesMap(Set<String> feederModes) {
        Set<String> all = new HashSet<>(SBBModes.TRAIN_STATION_MODES);
        all.addAll(feederModes);
        Map<String, Integer> map = new LinkedHashMap<>();
        int i = 0;
        for (String m : all) map.put(m, i++);
        return map;
    }

    private static Map<String, Integer> buildVariablesMSFeederMap() {
        Map<String, Integer> vars = new LinkedHashMap<>();
        vars.put(accessMode, 0);
        vars.put(egressMode, 1);
        vars.put(all, 2);
        int i = 3;
        for (List<String> group : varListFeeder) {
            for (String v : group) vars.put(v, i++);
        }
        return vars;
    }

    private Map<String, double[][]> initSubpopMap(int rows, int cols) {
        Map<String, double[][]> map = new HashMap<>();
        for (String subpop : analysisSubpopulations) map.put(subpop, new double[rows][cols]);
        return map;
    }
}

