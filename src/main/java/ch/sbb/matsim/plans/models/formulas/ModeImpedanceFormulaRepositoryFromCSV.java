package ch.sbb.matsim.plans.models.formulas;

import ch.sbb.matsim.config.variables.SBBModes;
import ch.sbb.matsim.csv.CSVReader;
import ch.sbb.matsim.plans.models.config.MOBiPlansConfig;
import ch.sbb.matsim.plans.models.steps.demandstrata.DemandStrata;
import ch.sbb.matsim.plans.models.steps.demandstrata.DemandStrataRegistry;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.io.IOException;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Implementation of ModeImpedanceFormulaRepository that loads formulas from a CSV file.
 * The CSV is expected to have columns: actid, dstrat, and one column per mode (e.g., bike, car, pt, ride, walk).
 */
@Singleton
public class ModeImpedanceFormulaRepositoryFromCSV implements ModeImpedanceFormulaRepository {
    public static final String ACTID = "actid";
    public static final String DSTRAT = "dstrat";
    private final Map<CompositeFormulaKey, Map<String, String>> formulaStore = new ConcurrentHashMap<>();
    Map<String, Set<DemandStrata>> stratasPerActivityType = new HashMap<>();


    @Inject
    public ModeImpedanceFormulaRepositoryFromCSV(MOBiPlansConfig config, DemandStrataRegistry demandStrataRegistry) {
        String filePath = Paths.get(config.data().modelBaseDir()).resolve(MOBiPlansConfig.MODEL_BASE_FOLDER_CHOICE_MODELS).resolve(MOBiPlansConfig.MODE_IMPEDANCE_FORMULAS).toString();
        try (CSVReader csvReader = new CSVReader(filePath, ";")) {
            Map<String, String> record;
            while ((record = csvReader.readLine()) != null && !record.isEmpty()) {
                String activity = record.get(ACTID);
                DemandStrata strata = demandStrataRegistry.getByName(record.get(DSTRAT));
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
    @Override
    public Map<String, String> getModeFormulas(String activity, DemandStrata strata) {
        CompositeFormulaKey key = new CompositeFormulaKey(activity, strata);
        Map<String, String> formulas = formulaStore.get(key);
        if (formulas == null) {
            throw new IllegalArgumentException("No formulas found for key: " + key);
        }
        return formulas;
    }
    @Override
    public Map<String, Set<DemandStrata>> getStratasPerActivityType() {
        return stratasPerActivityType;
    }
}