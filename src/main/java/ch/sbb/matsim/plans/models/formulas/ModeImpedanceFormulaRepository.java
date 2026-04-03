package ch.sbb.matsim.plans.models.formulas;

import ch.sbb.matsim.config.variables.SBBModes;
import ch.sbb.matsim.csv.CSVReader;
import ch.sbb.matsim.plans.synpop.DecisionStrata;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class ModeImpedanceFormulaRepository {
    public static final String ACTID = "actid";
    public static final String DSTRAT = "dstrat";
    private final Map<CompositeFormulaKey, Map<String, String>> formulaStore = new ConcurrentHashMap<>();
    Map<String, Set<DecisionStrata>> stratasPerActivityType = new HashMap<>();


    public ModeImpedanceFormulaRepository(String filePath) {
        try (CSVReader csvReader = new CSVReader(filePath, ";")) {
            Map<String, String> record;
            while ((record = csvReader.readLine()) != null && !record.isEmpty()) {
                String activity = record.get(ACTID);
                DecisionStrata strata = DecisionStrata.fromStrataName(record.get(DSTRAT));
                CompositeFormulaKey key = new CompositeFormulaKey(activity, strata);
                Map<String, String> modeFormulas = new ConcurrentHashMap<>();
                for (String mode : List.of(SBBModes.BIKE, SBBModes.CAR, SBBModes.PT, SBBModes.RIDE, SBBModes.WALK_FOR_ANALYSIS)) {
                    modeFormulas.put(mode, record.get(mode));
                }
                formulaStore.put(key, modeFormulas);
            }
        }

        catch (IOException e) { // CSVReader throws IOException
            throw new RuntimeException("Failed to load formula file: " + filePath, e);
        }
        for (CompositeFormulaKey key : formulaStore.keySet()) {
            stratasPerActivityType.computeIfAbsent(key.getActivity(), k -> new HashSet<>()).add(key.getStrata());
        }
    }

    /**
     * Retrieves the map of mode->formula for a given activity and strata.
     */
    public Map<String, String> getModeFormulas(String activity, DecisionStrata strata) {
        CompositeFormulaKey key = new CompositeFormulaKey(activity, strata);
        Map<String, String> formulas = formulaStore.get(key);
        if (formulas == null) {
            throw new IllegalArgumentException("No formulas found for key: " + key);
        }
        return formulas;
    }

    public Map<String, Set<DecisionStrata>> getStratasPerActivityType() {
        return stratasPerActivityType;
    }
}