package ch.sbb.matsim.analysis.modalsplit;

import ch.sbb.matsim.analysis.tripsandlegsanalysis.RailTripsAnalyzer;
import ch.sbb.matsim.config.SwissRailRaptorConfigGroup;
import ch.sbb.matsim.config.variables.SBBModes;
import ch.sbb.matsim.config.variables.SBBModes.PTSubModes;
import ch.sbb.matsim.config.variables.Variables;
import ch.sbb.matsim.csv.CSVWriter;
import ch.sbb.matsim.plans.models.utils.AttributeUtils;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.population.Leg;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.Plan;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.router.TripStructureUtils;
import org.matsim.utils.objectattributes.attributable.Attributes;

import java.util.*;
import java.util.stream.Collectors;

import static ch.sbb.matsim.analysis.modalsplit.MSVariables.*;

/**
 * Counts modal split in person-trips (PF) and person-km (PKM) disaggregated by subpopulation,
 * demographic attributes, and activity type.
 *
 * <p>Replaces {@code ModalSplitStats#analyzeModalSplit} and
 * {@code ModalSplitStats#writeModalSplit}.
 */
public class ModalSplitCollector implements TripDataCollector {

    private final Config config;
    private final RailTripsAnalyzer railTripsAnalyzer;
    private final LongestMainModeIdentifier mainModeIdentifier = new LongestMainModeIdentifier();
    private final List<String> analysisSubpopulations;

    private final Map<String, Integer> modesMap;
    private final Map<String, Integer> variablesMSMap;

    /** subpopulation → [modeIndex][variableIndex] */
    private final Map<String, double[][]> pfMap;
    private final Map<String, double[][]> pkmMap;

    public ModalSplitCollector(Config config,
                                RailTripsAnalyzer railTripsAnalyzer,
                                List<String> analysisSubpopulations) {
        this.config = config;
        this.railTripsAnalyzer = railTripsAnalyzer;
        this.analysisSubpopulations = analysisSubpopulations;
        this.modesMap = buildModesMap();
        this.variablesMSMap = buildVariablesMSMap();
        this.pfMap  = initSubpopMap(modesMap.size(), variablesMSMap.size());
        this.pkmMap = initSubpopMap(modesMap.size(), variablesMSMap.size());
    }

    // -------------------------------------------------------------------------
    // TripDataCollector
    // -------------------------------------------------------------------------

    @Override
    public void collect(Person person, Plan experiencedPlan) {
        Attributes attributes = person.getAttributes();
        String subpop = CollectorUtils.getSubpopulation(attributes);
        double[][] pf  = pfMap.get(subpop);
        double[][] pkm = pkmMap.get(subpop);
        if (pf == null) return; // not a tracked subpopulation

        for (TripStructureUtils.Trip trip : TripStructureUtils.getTrips(experiencedPlan)) {
            // skip same-facility "home-office" trips
            if (trip.getOriginActivity().getFacilityId() != null
                    && trip.getOriginActivity().getFacilityId()
                           .equals(trip.getDestinationActivity().getFacilityId())) continue;

            String mainMode = mainModeIdentifier.identifyMainMode(trip.getTripElements());
            if (mainMode.equals(SBBModes.WALK_MAIN_MAINMODE) || mainMode.equals(TransportMode.non_network_walk)) {
                mainMode = SBBModes.WALK_FOR_ANALYSIS;
            }
            Integer modeId = modesMap.get(mainMode);
            if (modeId == null) continue;

            double distance = 0;
            double railDistance = 0;
            boolean isRail = false;

            for (Leg leg : trip.getLegsOnly()) {
                double legDist = leg.getRoute().getDistance() / 1000.0;
                distance += legDist;
                if (PtLegUtils.isPtLeg(leg) && PtLegUtils.isRailLeg(leg)) {
                    railDistance += legDist;
                    isRail = true;
                }
            }

            double fqDistance = 0;
            boolean isFQRail = false;
            if (isRail) {
                fqDistance = railTripsAnalyzer.getFQDistance(trip, true) / 1000.0;
                isFQRail = fqDistance > 0;
            }

            pf[modeId][variablesMSMap.get(all)]++;
            pkm[modeId][variablesMSMap.get(all)] += distance;

            if (isRail) {
                int subRailIdx = variablesMSMap.get(submode + separator + SBBModes.RAIL);
                pf[modeId][subRailIdx]++;
                pkm[modeId][subRailIdx] += railDistance;
                if (isFQRail) {
                    int subFqIdx = variablesMSMap.get(submode + separator + SBBModes.FQRAIL);
                    pf[modeId][subFqIdx]++;
                    pkm[modeId][subFqIdx] += fqDistance;
                }
            }

            // demographic segmentations
            addCarAvailableStats(pf, pkm, modeId, attributes, distance);
            addPtSubscriptionStats(pf, pkm, modeId, attributes, distance);
            addCarAndPtStats(pf, pkm, modeId, attributes, distance);
            addEducationStats(pf, pkm, modeId, attributes, distance);
            addEmploymentStats(pf, pkm, modeId, attributes, distance);
            addAgeStats(pf, pkm, modeId, attributes, distance);
            addActTypeStats(pf, pkm, modeId, trip.getDestinationActivity().getType(), distance);
        }
    }

    @Override
    public void write(String outputBaseDir, String runId, double sampleSize) {
        String[] columns = new String[2 + variablesMSMap.size()];
        columns[0] = runID;
        columns[1] = subpopulation;
        int i = 2;
        for (String var : variablesMSMap.keySet()) columns[i++] = var;

        writeMap(pfMap,  outputBaseDir + oNModalSplitPF,  columns, runId, sampleSize);
        writeMap(pkmMap, outputBaseDir + oNModalSplitPKM, columns, runId, sampleSize);
    }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    private void writeMap(Map<String, double[][]> dataMap, String path,
                          String[] columns, String runId, double sampleSize) {
        try (CSVWriter writer = new CSVWriter("", columns, path)) {
            for (String subpop : analysisSubpopulations) {
                for (Map.Entry<String, Integer> modeEntry : modesMap.entrySet()) {
                    writer.set(runID, runId);
                    writer.set(subpopulation, subpop);
                    for (Map.Entry<String, Integer> varEntry : variablesMSMap.entrySet()) {
                        if (varEntry.getKey().equals(mode)) {
                            writer.set(mode, modeEntry.getKey());
                        } else {
                            writer.set(varEntry.getKey(), Integer.toString(
                                    (int) (dataMap.get(subpop)[modeEntry.getValue()][varEntry.getValue()] / sampleSize)));
                        }
                    }
                    writer.writeRow();
                }
            }
        } catch (Exception ex) {
            //noinspection CallToPrintStackTrace
            ex.printStackTrace();
        }
    }

    private void addCarAvailableStats(double[][] pf, double[][] pkm, int modeId,
                                       Attributes attributes, double distance) {
        String carAva = attributes.getAttribute(Variables.CAR_AVAIL) != null
                && AttributeUtils.hasSomething(Variables.CAR_AVAIL, attributes) ? "1" : "0";
        String key = Variables.CAR_AVAIL + "_" + carAva;
        for (String attr : carAvailable) {
            if (attr.equals(key)) {
                pf[modeId][variablesMSMap.get(attr)]++;
                pkm[modeId][variablesMSMap.get(attr)] += distance;
                break;
            }
        }
    }

    private void addPtSubscriptionStats(double[][] pf, double[][] pkm, int modeId,
                                         Attributes attributes, double distance) {
        String ptSub = "none";
        if (attributes.getAttribute(Variables.PT_SUBSCRIPTION) != null) {
            ptSub = attributes.getAttribute(Variables.PT_SUBSCRIPTION).toString().toUpperCase();
            if ("NONE".equalsIgnoreCase(ptSub)) ptSub = "none";
            if ("HTV".equals(ptSub)) ptSub = "VA";
        }
        String key = Variables.PT_SUBSCRIPTION + "_" + ptSub;
        for (String attr : ptSubscription) {
            if (attr.equals(key)) {
                pf[modeId][variablesMSMap.get(attr)]++;
                pkm[modeId][variablesMSMap.get(attr)] += distance;
                break;
            }
        }
    }

    private void addCarAndPtStats(double[][] pf, double[][] pkm, int modeId,
                                   Attributes attributes, double distance) {
        String carAva = attributes.getAttribute(Variables.CAR_AVAIL) != null
                && AttributeUtils.hasSomething(Variables.CAR_AVAIL, attributes) ? "1" : "0";
        String ptSub = "none";
        if (attributes.getAttribute(Variables.PT_SUBSCRIPTION) != null) {
            ptSub = attributes.getAttribute(Variables.PT_SUBSCRIPTION).toString().toUpperCase();
            if ("NONE".equalsIgnoreCase(ptSub)) ptSub = "none";
            if ("HTV".equals(ptSub)) ptSub = "VA";
        }
        String key = Variables.CAR_AVAIL + "_" + carAva + "_" + Variables.PT_SUBSCRIPTION + "_" + ptSub;
        for (String attr : carAndPt) {
            if (attr.equals(key)) {
                pf[modeId][variablesMSMap.get(attr)]++;
                pkm[modeId][variablesMSMap.get(attr)] += distance;
                break;
            }
        }
    }

    private void addEducationStats(double[][] pf, double[][] pkm, int modeId,
                                    Attributes attributes, double distance) {
        String edu = "null";
        if (attributes.getAttribute(Variables.CURRENT_EDUCATION) != null) {
            edu = attributes.getAttribute(Variables.CURRENT_EDUCATION).toString();
        }
        String key = Variables.CURRENT_EDUCATION + "_" + edu;
        for (String attr : educationType) {
            if (attr.equals(key)) {
                pf[modeId][variablesMSMap.get(attr)]++;
                pkm[modeId][variablesMSMap.get(attr)] += distance;
                break;
            }
        }
    }

    private void addEmploymentStats(double[][] pf, double[][] pkm, int modeId,
                                     Attributes attributes, double distance) {
        String empRate = "0";
        if (attributes.getAttribute(Variables.LEVEL_OF_EMPLOYMENT_CAT) != null) {
            empRate = attributes.getAttribute(Variables.LEVEL_OF_EMPLOYMENT_CAT).toString();
        }
        String key = Variables.LEVEL_OF_EMPLOYMENT_CAT + "_" + empRate;
        for (String attr : employmentRate) {
            if (attr.equals(key)) {
                pf[modeId][variablesMSMap.get(attr)]++;
                pkm[modeId][variablesMSMap.get(attr)] += distance;
                break;
            }
        }
    }

    private void addAgeStats(double[][] pf, double[][] pkm, int modeId,
                              Attributes attributes, double distance) {
        if (attributes.getAttribute(Variables.AGE_CATEGORY) == null) return;
        String key = Variables.AGE_CATEGORY + "_" + attributes.getAttribute(Variables.AGE_CATEGORY);
        for (String attr : ageCategory) {
            if (attr.equals(key)) {
                pf[modeId][variablesMSMap.get(attr)]++;
                pkm[modeId][variablesMSMap.get(attr)] += distance;
                break;
            }
        }
    }

    private void addActTypeStats(double[][] pf, double[][] pkm, int modeId,
                                  String actType, double distance) {
        if (actType.contains(separator)) actType = actType.substring(0, actType.indexOf("_"));
        String key = toActType + separator + actType;
        for (String attr : toActTypeList) {
            if (attr.equals(key)) {
                pf[modeId][variablesMSMap.get(attr)]++;
                pkm[modeId][variablesMSMap.get(attr)] += distance;
                break;
            }
        }
    }

    // -------------------------------------------------------------------------
    // Initialisation
    // -------------------------------------------------------------------------

    /** Also used by {@link ModalSplitStats} to build the shared modes-including-railFQ map. */
    static Map<String, Integer> buildModesMapStatic(Config config) {
        Set<String> modesSet = new HashSet<>(config.scoring().getAllModes());
        modesSet.remove(SBBModes.WALK_MAIN_MAINMODE);
        modesSet.removeAll(PTSubModes.submodes);

        var raptorCfg = ConfigUtils.addOrGetModule(config, SwissRailRaptorConfigGroup.class);
        Set<String> intermodalModes = raptorCfg.getIntermodalAccessEgressParameterSets().stream()
                .map(SwissRailRaptorConfigGroup.IntermodalAccessEgressParameterSet::getMode)
                .collect(Collectors.toSet());
        modesSet.removeAll(intermodalModes);
        if (intermodalModes.contains(SBBModes.DRT)) modesSet.add(SBBModes.DRT);

        modesSet.add(SBBModes.WALK_FOR_ANALYSIS);
        modesSet.add(SBBModes.PT);

        Map<String, Integer> result = new LinkedHashMap<>();
        int i = 0;
        for (String m : modesSet) result.put(m, i++);
        return result;
    }

    private Map<String, Integer> buildModesMap() {
        Set<String> modesSet = new HashSet<>(config.scoring().getAllModes());
        modesSet.remove(SBBModes.WALK_MAIN_MAINMODE);
        modesSet.removeAll(PTSubModes.submodes);

        var raptorCfg = ConfigUtils.addOrGetModule(config, SwissRailRaptorConfigGroup.class);
        Set<String> intermodalModes = raptorCfg.getIntermodalAccessEgressParameterSets().stream()
                .map(SwissRailRaptorConfigGroup.IntermodalAccessEgressParameterSet::getMode)
                .collect(Collectors.toSet());
        modesSet.removeAll(intermodalModes);
        if (intermodalModes.contains(SBBModes.DRT)) modesSet.add(SBBModes.DRT);

        modesSet.add(SBBModes.WALK_FOR_ANALYSIS);
        modesSet.add(SBBModes.PT);

        Map<String, Integer> result = new LinkedHashMap<>();
        int i = 0;
        for (String m : modesSet) result.put(m, i++);
        return result;
    }

    private Map<String, Integer> buildVariablesMSMap() {
        Map<String, Integer> vars = new LinkedHashMap<>();
        vars.put(mode, 0);
        vars.put(all,  1);
        int i = 2;
        for (List<String> group : varList) {
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

