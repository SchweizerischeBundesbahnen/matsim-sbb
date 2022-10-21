package ch.sbb.matsim.analysis.modalsplit;

import ch.sbb.matsim.config.PostProcessingConfigGroup;
import ch.sbb.matsim.config.variables.SBBActivities;
import ch.sbb.matsim.config.variables.SBBModes;
import ch.sbb.matsim.config.variables.Variables;
import ch.sbb.matsim.csv.CSVWriter;
import ch.sbb.matsim.routing.SBBAnalysisMainModeIdentifier;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.NoSuchElementException;
import javax.inject.Inject;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.Plan;
import org.matsim.api.core.v01.population.Population;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.router.TripStructureUtils;
import org.matsim.core.router.TripStructureUtils.Trip;
import org.matsim.core.scoring.ExperiencedPlansService;
import org.matsim.utils.objectattributes.attributable.Attributes;
import smile.stat.Hypothesis.F;

public class ModalSplitStats {

    @Inject
    private ExperiencedPlansService experiencedPlansService;
    @Inject
    private Population population;
    @Inject
    private Config config;
    //usesd person attrubutes
    private final String carAvailable1 = Variables.CAR_AVAIL + "_1";
    private final String carAvailable0 = Variables.CAR_AVAIL + "_0";
    private final String mode = "mode";
    private final String all = "all";
    private final String activity = "activity";
    private final String ptSubNone = Variables.PT_SUBSCRIPTION + "_" + Variables.PT_SUBSCRIPTION_NONE;
    private final String ptSubGA = Variables.PT_SUBSCRIPTION + "_" + Variables.GA;
    private final String ptSubVA = Variables.PT_SUBSCRIPTION + "_" + Variables.VA;
    private final String ptSubHTA = Variables.PT_SUBSCRIPTION + "_" + Variables.HTA;

    private final String carNone = "car_no_pt_sub";
    private final String carGa = "car_GA_sub";
    private final String carHTA = "car_HTA_sub";
    private final String carVA = "car_VA_sub";
    private final String nocarNone = "no_car_no_pt_sub";
    private final String nocarGa = "no_car_GA_sub";
    private final String nocarHTA = "no_car_HTA_sub";
    private final String nocarVA = "no_car_VA_sub";

    private final String notInEducation = Variables.CURRENT_EDUCATION + "_" + Variables.NOT_IN_EDUCATION;
    private final String primary = Variables.CURRENT_EDUCATION + "_" + Variables.PRIMRAY;
    private final String secondary = Variables.CURRENT_EDUCATION + "_" + Variables.SECONDARY;
    private final String student = Variables.CURRENT_EDUCATION + "_" + Variables.STUDENT;

    private final int timeSplit = 30 * 60;


    private Map<String, Integer> getVariables() {
        Map<String, Integer> variables = new HashMap<>();
        variables.put(mode, 0);
        variables.put(all, 1);
        variables.put(carAvailable1, 2);
        variables.put(carAvailable0, 3);
        variables.put(ptSubNone, 4);
        variables.put(ptSubGA, 5);
        variables.put(ptSubVA, 6);
        variables.put(ptSubHTA, 7);
        variables.put(carNone, 8);
        variables.put(carGa, 9);
        variables.put(carHTA, 10);
        variables.put(carVA, 11);
        variables.put(nocarNone, 12);
        variables.put(nocarGa, 13);
        variables.put(nocarHTA, 14);
        variables.put(nocarVA, 15);
        variables.put(notInEducation, 16);
        variables.put(primary, 17);
        variables.put(secondary, 18);
        variables.put(student, 19);
        return variables;
    }

    public void analyzeAndWriteStats(String filename) {
        Map<String, Integer> modesMap = getModes();
        Map<String, Integer> activityMap = getActivities();
        Map<String, Integer> variablesPF = getVariables();
        Map<String, Integer> variablesPKM = getVariablesPKM();
        Map<String, Integer> variablesAciviy = getVariablesActivity();
        Map<String, int[][]> subpopulaionMapPF = getSubpopulation(modesMap.size(), variablesPF.size());
        Map<Integer, Map<String, int[][]>> subpopulaionMapDayMode = getSubpopulationDay(modesMap.size(), variablesPKM.size());
        Map<Integer, Map<String, int[][]>> subpopulaionMapDayAcivity = getSubpopulationDay(activityMap.size(), variablesAciviy.size());
        analyze(modesMap, variablesPF, subpopulaionMapPF);
        analyzePKM(modesMap, variablesPKM, subpopulaionMapDayMode, activityMap, subpopulaionMapDayAcivity, variablesAciviy);
        write(filename, modesMap, variablesPF, subpopulaionMapPF);
        write(modesMap, variablesPKM, subpopulaionMapDayMode, filename, subpopulaionMapDayAcivity, variablesAciviy, activityMap);
    }

    private Map<String, Integer> getVariablesActivity() {
        Map<String, Integer> variables = new HashMap<>();
        variables.put(activity, 0);
        variables.put(all, 1);
        return variables;
    }

    private void write(Map<String, Integer> modesMap, Map<String, Integer> variablesPKM, Map<Integer, Map<String,int[][]>> subpopulaionMapPKM, String filename,
        Map<Integer, Map<String, int[][]>> subpopulaionMapDayAcivity, Map<String, Integer> variablesAciviy, Map<String, Integer> activityMap) {
        final double sampleSize = ConfigUtils.addOrGetModule(config, PostProcessingConfigGroup.class).getSimulationSampleSize();
        String[] columns = {"RunID", "subpopulation", "time", mode, all};
        try (CSVWriter csvWriter = new CSVWriter("", columns, filename + "modal_split_distribution.csv")) {
            for (String subpopulation : Variables.SUBPOPULATIONS) {
                for (Entry<Integer, Map<String, int[][]>> entryTime : subpopulaionMapPKM.entrySet()) {
                    for (Entry<String, Integer> entryMode : modesMap.entrySet()) {
                        csvWriter.set("RunID", config.controler().getRunId());
                        csvWriter.set("subpopulation", subpopulation);
                        csvWriter.set("time", Integer.toString(entryTime.getKey()));
                        for (Entry<String, Integer> entryVar : variablesPKM.entrySet()) {
                            if (entryVar.getKey().equals(mode)) {
                                csvWriter.set(mode, entryMode.getKey());
                            } else {
                                String key = entryVar.getKey();
                                Integer value = entryVar.getValue();
                                csvWriter.set(key, Integer.toString((int) (subpopulaionMapPKM.get(entryTime.getKey()).get(subpopulation)[entryMode.getValue()][value] / sampleSize)));
                            }
                        }
                        csvWriter.writeRow();
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        String[] columns2 = {"RunID", "subpopulation", "time", activity, all};
        try (CSVWriter csvWriter = new CSVWriter("", columns2, filename + "activity_time_distribution.csv")) {
            for (String subpopulation : Variables.SUBPOPULATIONS) {
                for (Entry<Integer, Map<String, int[][]>> entryTime : subpopulaionMapDayAcivity.entrySet()) {
                    for (Entry<String, Integer> entryMode : activityMap.entrySet()) {
                        csvWriter.set("RunID", config.controler().getRunId());
                        csvWriter.set("subpopulation", subpopulation);
                        csvWriter.set("time", Integer.toString(entryTime.getKey()));
                        for (Entry<String, Integer> entryVar : variablesAciviy.entrySet()) {
                            if (entryVar.getKey().equals(activity)) {
                                csvWriter.set(activity, entryMode.getKey());
                            } else {
                                String key = entryVar.getKey();
                                Integer value = entryVar.getValue();
                                csvWriter.set(key, Integer.toString((int) (subpopulaionMapDayAcivity.get(entryTime.getKey()).get(subpopulation)[entryMode.getValue()][value] / sampleSize)));
                            }
                        }
                        csvWriter.writeRow();
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private Map<Integer, Map<String,int[][]>> getSubpopulationDay(int modeSize, int varSize) {
        Map<Integer, Map<String,int[][]>> subpopulaionMapDay = new HashMap<>();
        int size = (int) config.qsim().getEndTime().seconds()/(timeSplit);
        for (int i = 0; i <= size; i++) {
            subpopulaionMapDay.put(i * timeSplit, getSubpopulation(modeSize, varSize));
        }
        return subpopulaionMapDay;
    }

    private Map<String, Integer> getVariablesPKM() {
        Map<String, Integer> variables = new HashMap<>();
        variables.put(mode, 0);
        variables.put(all, 1);
        return variables;
    }

    private void analyzePKM(Map<String, Integer> modesMap, Map<String, Integer> variablesPKM, Map<Integer, Map<String, int[][]>> subpopulaionMapPKM, Map<String, Integer> activityMap,
        Map<Integer, Map<String, int[][]>> subpopulaionMapDayAcivity, Map<String, Integer> variablesAciviy) {

        for (Entry<Id<Person>, Plan> entry : experiencedPlansService.getExperiencedPlans().entrySet()) {
            Attributes attributes = population.getPersons().get(entry.getKey()).getAttributes();
            for (Trip trip : TripStructureUtils.getTrips(entry.getValue())) {
                SBBAnalysisMainModeIdentifier mainModeIdentifier = new SBBAnalysisMainModeIdentifier();
                String tmpMode = mainModeIdentifier.identifyMainMode(trip.getTripElements());
                if (tmpMode.equals("walk_main")) {
                    tmpMode = "walk";
                }
                int idMode = modesMap.get(tmpMode);
                int idActivity = activityMap.get(trip.getDestinationActivity().getType().substring(0, trip.getDestinationActivity().getType().indexOf("_")));
                int middle = (int) ((trip.getOriginActivity().getEndTime().seconds()+trip.getDestinationActivity().getStartTime().seconds())/2);
                int[][] subpopulationArray = subpopulaionMapPKM.get(middle-(middle%timeSplit)).get(attributes.getAttribute(Variables.SUBPOPULATION));
                int[][] subpopulaionMapDayAcivityArray = subpopulaionMapDayAcivity.get(middle-(middle%timeSplit)).get(attributes.getAttribute(Variables.SUBPOPULATION));
                subpopulationArray[idMode][variablesPKM.get(all)] = subpopulationArray[idMode][variablesPKM.get(all)] + 1;
                subpopulaionMapDayAcivityArray[idActivity][variablesAciviy.get(all)] = subpopulaionMapDayAcivityArray[idActivity][variablesAciviy.get(all)] + 1;
            }
        }

    }

    private Map<String, int[][]> getSubpopulation(int modeSize, int varSize) {
        Map<String, int[][]> subpopulaionMap = new HashMap<>();
        for (String subpopulation : Variables.SUBPOPULATIONS) {
            subpopulaionMap.put(subpopulation, new int[modeSize][varSize]);
        }
        return subpopulaionMap;
    }

    private void analyze(Map<String, Integer> modesMap, Map<String, Integer> variables, Map<String, int[][]> subpopulaionMap) {
        for (Entry<Id<Person>, Plan> entry : experiencedPlansService.getExperiencedPlans().entrySet()) {
            Attributes attributes = population.getPersons().get(entry.getKey()).getAttributes();
            for (Trip trip : TripStructureUtils.getTrips(entry.getValue())) {
                SBBAnalysisMainModeIdentifier mainModeIdentifier = new SBBAnalysisMainModeIdentifier();
                String tmpMode = mainModeIdentifier.identifyMainMode(trip.getTripElements());
                if (tmpMode.equals("walk_main")) {
                    tmpMode = "walk";
                }
                int id = modesMap.get(tmpMode);
                int[][] subpopulationArray = subpopulaionMap.get(attributes.getAttribute(Variables.SUBPOPULATION));
                subpopulationArray[id][variables.get(all)] = subpopulationArray[id][variables.get(all)] + 1;
                // car available
                if (attributes.getAttribute(Variables.CAR_AVAIL).toString().equals(Variables.CAR_AVAL_TRUE)) {
                    subpopulationArray[id][variables.get(carAvailable1)] = subpopulationArray[id][variables.get(carAvailable1)] + 1;
                } else if (attributes.getAttribute(Variables.CAR_AVAIL).toString().equals("0")) {
                    subpopulationArray[id][variables.get(carAvailable0)] = subpopulationArray[id][variables.get(carAvailable0)] + 1;
                } else {
                    throw new NoSuchElementException("Unknow value of: " + Variables.CAR_AVAIL);
                }
                // pt subscription
                if (attributes.getAttribute(Variables.PT_SUBSCRIPTION).toString().equals(Variables.GA)) {
                    subpopulationArray[id][variables.get(ptSubGA)] = subpopulationArray[id][variables.get(ptSubGA)] + 1;
                } else if (attributes.getAttribute(Variables.PT_SUBSCRIPTION).toString().equals(Variables.VA)) {
                    subpopulationArray[id][variables.get(ptSubVA)] = subpopulationArray[id][variables.get(ptSubVA)] + 1;
                } else if (attributes.getAttribute(Variables.PT_SUBSCRIPTION).toString().equals(Variables.HTA)) {
                    subpopulationArray[id][variables.get(ptSubHTA)] = subpopulationArray[id][variables.get(ptSubHTA)] + 1;
                } else if (attributes.getAttribute(Variables.PT_SUBSCRIPTION).toString().equals(Variables.PT_SUBSCRIPTION_NONE)) {
                    subpopulationArray[id][variables.get(ptSubNone)] = subpopulationArray[id][variables.get(ptSubNone)] + 1;
                } else {
                    throw new NoSuchElementException("Unknow value of: " + Variables.PT_SUBSCRIPTION);
                }
                // combination car and pt
                if (attributes.getAttribute(Variables.CAR_AVAIL).toString().equals(Variables.CAR_AVAL_TRUE)) {
                    if (attributes.getAttribute(Variables.PT_SUBSCRIPTION).toString().equals(Variables.GA)) {
                        subpopulationArray[id][variables.get(carGa)] = subpopulationArray[id][variables.get(carGa)] + 1;
                    } else if (attributes.getAttribute(Variables.PT_SUBSCRIPTION).toString().equals(Variables.VA)) {
                        subpopulationArray[id][variables.get(carVA)] = subpopulationArray[id][variables.get(carVA)] + 1;
                    } else if (attributes.getAttribute(Variables.PT_SUBSCRIPTION).toString().equals(Variables.HTA)) {
                        subpopulationArray[id][variables.get(carHTA)] = subpopulationArray[id][variables.get(carHTA)] + 1;
                    } else if (attributes.getAttribute(Variables.PT_SUBSCRIPTION).toString().equals(Variables.PT_SUBSCRIPTION_NONE)) {
                        subpopulationArray[id][variables.get(carNone)] = subpopulationArray[id][variables.get(carNone)] + 1;
                    }
                } else if (attributes.getAttribute(Variables.CAR_AVAIL).toString().equals("0")) {
                    subpopulationArray[id][variables.get(carAvailable0)] = subpopulationArray[id][variables.get(carAvailable0)] + 1;
                    if (attributes.getAttribute(Variables.PT_SUBSCRIPTION).toString().equals(Variables.GA)) {
                        subpopulationArray[id][variables.get(nocarGa)] = subpopulationArray[id][variables.get(nocarGa)] + 1;
                    } else if (attributes.getAttribute(Variables.PT_SUBSCRIPTION).toString().equals(Variables.VA)) {
                        subpopulationArray[id][variables.get(nocarVA)] = subpopulationArray[id][variables.get(nocarVA)] + 1;
                    } else if (attributes.getAttribute(Variables.PT_SUBSCRIPTION).toString().equals(Variables.HTA)) {
                        subpopulationArray[id][variables.get(nocarHTA)] = subpopulationArray[id][variables.get(nocarHTA)] + 1;
                    } else if (attributes.getAttribute(Variables.PT_SUBSCRIPTION).toString().equals(Variables.PT_SUBSCRIPTION_NONE)) {
                        subpopulationArray[id][variables.get(nocarNone)] = subpopulationArray[id][variables.get(nocarNone)] + 1;
                    }
                }
                // education
                if (attributes.getAttribute(Variables.CURRENT_EDUCATION).toString().equals(Variables.NOT_IN_EDUCATION)) {
                    subpopulationArray[id][variables.get(notInEducation)] = subpopulationArray[id][variables.get(notInEducation)] + 1;
                } else if (attributes.getAttribute(Variables.CURRENT_EDUCATION).toString().equals(Variables.PRIMRAY)) {
                    subpopulationArray[id][variables.get(primary)] = subpopulationArray[id][variables.get(primary)] + 1;
                } else if (attributes.getAttribute(Variables.CURRENT_EDUCATION).toString().equals(Variables.SECONDARY)) {
                    subpopulationArray[id][variables.get(secondary)] = subpopulationArray[id][variables.get(secondary)] + 1;
                } else if (attributes.getAttribute(Variables.CURRENT_EDUCATION).toString().equals(Variables.STUDENT)) {
                    subpopulationArray[id][variables.get(student)] = subpopulationArray[id][variables.get(student)] + 1;
                } else {
                    throw new NoSuchElementException("Unknow value of: " + Variables.CURRENT_EDUCATION);
                }
            }
        }
    }

    private void write(String filename, Map<String, Integer> coding, Map<String, Integer> variables, Map<String, int[][]> subpopulationArray) {
        final double sampleSize = ConfigUtils.addOrGetModule(config, PostProcessingConfigGroup.class).getSimulationSampleSize();
        String[] columns = {"RunID", "subpopulation", mode, all, carAvailable1, carAvailable0, ptSubNone, ptSubGA, ptSubVA, ptSubHTA, carNone,
            carGa, carHTA, carVA, nocarNone, nocarGa, nocarHTA, nocarVA, notInEducation, primary, secondary, student};
        try (CSVWriter csvWriter = new CSVWriter("", columns, filename + "modal_split.csv")) {
            for (String subpopulation : Variables.SUBPOPULATIONS) {
                for (Entry<String, Integer> col : coding.entrySet()) {
                    csvWriter.set("RunID", config.controler().getRunId());
                    csvWriter.set("subpopulation", subpopulation);
                    for (Entry<String, Integer> entry : variables.entrySet()) {
                        if (entry.getKey().equals(mode)) {
                            csvWriter.set(mode, col.getKey());
                        } else {
                            String key = entry.getKey();
                            Integer value = entry.getValue();
                            csvWriter.set(key, Integer.toString((int) (subpopulationArray.get(subpopulation)[col.getValue()][value] / sampleSize)));
                        }
                    }
                    csvWriter.writeRow();
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private Map<String, Integer> getModes() {
        List<String> modes = SBBModes.MAIN_MODES;
        Map<String, Integer> coding = new HashMap<>();
        for (int x = 0; x < modes.size(); x++) {
            coding.put(modes.get(x), x);
        }
        return coding;
    }

    private Map<String, Integer> getActivities() {
        List<String> activities = SBBActivities.activities;
        Map<String, Integer> coding = new HashMap<>();
        for (int x = 0; x < activities.size(); x++) {
            coding.put(activities.get(x), x);
        }
        return coding;
    }

}
