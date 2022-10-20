package ch.sbb.matsim.analysis.modalsplit;

import ch.sbb.matsim.config.PostProcessingConfigGroup;
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
    private final String ptSubNone = Variables.PT_SUBSCRIPTION + "_none";
    private final String ptSubGA = Variables.PT_SUBSCRIPTION + "_GA";
    private final String ptSubVA = Variables.PT_SUBSCRIPTION + "_VA";
    private final String ptSubHTA = Variables.PT_SUBSCRIPTION + "_HTA";

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
        return variables;
    }

    public void analyzeAndWriteStats(String filename) {
        Map<String, Integer> modesMap = getModes();
        Map<String, Integer> variablesMap = getVariables();
        Map<String, int[][]> subpopulaionMap = getSubpopulation(modesMap.size(), variablesMap.size());
        analyze(modesMap, variablesMap, subpopulaionMap);
        write(filename, modesMap, variablesMap, subpopulaionMap);
    }

    private Map<String, int[][]> getSubpopulation(int modeSize, int varSize) {
        Map<String, int[][]> subpopulaionMap = new HashMap<>();
        for (String subpopulation : Variables.SUBPOPULATIONS) {
            subpopulaionMap.put(subpopulation, new int[modeSize][varSize]);
        }
        return subpopulaionMap;
    }

    private void analyze(Map<String, Integer> coding, Map<String, Integer> variables, Map<String, int[][]> subpopulaionMap) {
        for (Entry<Id<Person>, Plan> entry : experiencedPlansService.getExperiencedPlans().entrySet()) {
            Attributes attributes = population.getPersons().get(entry.getKey()).getAttributes();
            for (Trip trip : TripStructureUtils.getTrips(entry.getValue())) {
                SBBAnalysisMainModeIdentifier mainModeIdentifier = new SBBAnalysisMainModeIdentifier();
                String tmpMode = mainModeIdentifier.identifyMainMode(trip.getTripElements());
                if (tmpMode.equals("walk_main")) {
                    tmpMode = "walk";
                }
                int id = coding.get(tmpMode);
                int[][] subpopulationArray = subpopulaionMap.get(attributes.getAttribute(Variables.SUBPOPULATION));
                subpopulationArray[id][variables.get(all)] = subpopulationArray[id][variables.get(all)] + 1;
                if (attributes.getAttribute(Variables.CAR_AVAIL).toString().equals(Variables.CAR_AVAL_TRUE)) {
                    subpopulationArray[id][variables.get(carAvailable1)] = subpopulationArray[id][variables.get(carAvailable1)] + 1;
                } else if (attributes.getAttribute(Variables.CAR_AVAIL).toString().equals("0")) {
                    subpopulationArray[id][variables.get(carAvailable0)] = subpopulationArray[id][variables.get(carAvailable0)] + 1;
                } else {
                    throw new NoSuchElementException("Unknow value of: " + Variables.CAR_AVAIL);
                }
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
            }
        }
    }

    private void write(String filename, Map<String, Integer> coding, Map<String, Integer> variables, Map<String, int[][]> subpopulationArray) {
        final double sampleSize = ConfigUtils.addOrGetModule(config, PostProcessingConfigGroup.class).getSimulationSampleSize();
        String[] columns = {"RunID", "subpopulation", mode, all, carAvailable1, carAvailable0, ptSubNone, ptSubGA, ptSubVA, ptSubHTA};
        try (CSVWriter csvWriter = new CSVWriter("", columns, filename)) {
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
                            csvWriter.set(key, Integer.toString((int) (subpopulationArray.get(subpopulation)[col.getValue()][value]/sampleSize)));
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

}
