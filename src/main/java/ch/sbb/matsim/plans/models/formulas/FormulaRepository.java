package ch.sbb.matsim.plans.models.formulas;

import ch.sbb.matsim.config.variables.SBBModes;
import ch.sbb.matsim.plans.synpop.DecisionStrata;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;

import java.io.FileReader;
import java.io.Reader;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class FormulaRepository {
    public static final String ACTID = "actid";
    public static final String DSTRAT = "dstrat";
    // The key is now our new CompositeFormulaKey
    private final Map<CompositeFormulaKey, Map<String, String>> formulaStore = new ConcurrentHashMap<>();

    public FormulaRepository(String filePath) {
        // Use semicolon as a delimiter
        CSVFormat format = CSVFormat.DEFAULT.builder()
                .setDelimiter(';')
                .setHeader()
                .setSkipHeaderRecord(true)
                .build();

        try (Reader reader = new FileReader(filePath);
             CSVParser csvParser = new CSVParser(reader, format)) {

            for (CSVRecord record : csvParser) {
                // Use the enums for type safety
                String activity = record.get(ACTID);
                DecisionStrata strata = DecisionStrata.fromStrataName(record.get(DSTRAT));
                CompositeFormulaKey key = new CompositeFormulaKey(activity, strata);

                Map<String, String> modeFormulas = new ConcurrentHashMap<>();
                for (String mode : List.of(SBBModes.BIKE, SBBModes.CAR, SBBModes.PT, SBBModes.RIDE, SBBModes.WALK_FOR_ANALYSIS)) {
                    modeFormulas.put(mode, record.get(mode));
                }
                formulaStore.put(key, modeFormulas);
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to load formula file: " + filePath, e);
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
}