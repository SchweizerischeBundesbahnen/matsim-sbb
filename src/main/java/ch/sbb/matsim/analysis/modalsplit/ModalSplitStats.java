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
    private static final String FILENAME = "model_split.csv";

    //usesd person attrubutes
    private final String carAvailable1 = Variables.CAR_AVAIL + "_1";
    private final String carAvailable0 = Variables.CAR_AVAIL + "_0";



    private Map<String, Integer> getVariables() {
        Map<String, Integer> variables = new HashMap<>();
        variables.put("mode", 0);
        variables.put("all", 1);
        variables.put(carAvailable1, 2);
        variables.put(carAvailable0, 3);
        return variables;
    }

    public void analyzeAndWriteStats() {
        Map<String, Integer> modesMap = getModes();
        Map<String, Integer> variablesMap = getVariables();
        int[][] complte = new int[modesMap.size()][variablesMap.size()];
        double sampleSize = ConfigUtils.addOrGetModule(config, PostProcessingConfigGroup.class).getSimulationSampleSize();
        analyze(modesMap, variablesMap, complte);
        write(modesMap, variablesMap, complte);
    }

    private void analyze(Map<String, Integer> coding, Map<String, Integer> variables, int[][] complte) {
        for (Entry<Id<Person>, Plan> entry : experiencedPlansService.getExperiencedPlans().entrySet()) {
            Attributes attributes = population.getPersons().get(entry.getKey()).getAttributes();
            if (attributes.getAttribute(Variables.SUBPOPULATION).equals(Variables.REGULAR)) {
                for (Trip trip : TripStructureUtils.getTrips(entry.getValue())) {
                    SBBAnalysisMainModeIdentifier mainModeIdentifier = new SBBAnalysisMainModeIdentifier();
                    String mode = mainModeIdentifier.identifyMainMode(trip.getTripElements());
                    if (mode.equals("walk_main")) {
                        mode = "walk";
                    }
                    int id = coding.get(mode);
                    complte[id][variables.get("all")] = complte[id][variables.get("all")] + 1;
                    if (attributes.getAttribute(Variables.CAR_AVAIL).toString().equals(Variables.CAR_AVAL_TRUE)) {
                        complte[id][variables.get(carAvailable1)] = complte[id][variables.get(carAvailable1)] + 1;
                    } else {
                        complte[id][variables.get(carAvailable0)] = complte[id][variables.get(carAvailable0)] + 1;
                    }
                }
            }
        }
    }

    private void write(Map<String, Integer> coding, Map<String, Integer> variables, int[][] complte) {
        String[] columns = {"RunID","subpopulation", "mode", "all", carAvailable1, carAvailable0};
        try (CSVWriter csvWriter = new CSVWriter("", columns, FILENAME)) {
            for (Entry<String, Integer> col : coding.entrySet()) {
                csvWriter.set("RunID", config.controler().getRunId());
                csvWriter.set("subpopulation", "regular");
                for (Entry<String, Integer> entry : variables.entrySet()) {
                    if (entry.getKey().equals("mode")) {
                        csvWriter.set("mode", col.getKey());
                    } else {
                        String key = entry.getKey();
                        Integer value = entry.getValue();
                        csvWriter.set(key, Integer.toString(complte[col.getValue()][value]));
                    }
                }
               csvWriter.writeRow();
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
