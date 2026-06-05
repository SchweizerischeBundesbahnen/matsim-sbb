package ch.sbb.matsim.plans.models.steps.demandstrata;

import ch.sbb.matsim.plans.models.config.MOBiPlansConfig;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.matsim.api.core.v01.population.Person;
import org.matsim.core.utils.io.IOUtils;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Predicate;
/*
   Loads the demand strata definitions from CSV.
 */
public final class StrataConfigLoader {

    private static final Logger logger = LogManager.getLogger(StrataConfigLoader.class);

    // Private constructor to prevent instantiation of utility class
    private StrataConfigLoader() {}

    /**
     * Statically parses the CSV and returns a fully populated DemandStrataRegistry.
     */
    public static DemandStrataRegistry createAndLoad(MOBiPlansConfig mobiPlansConfig) {
        DemandStrataRegistry registry = new DemandStrataRegistry();

        String filePath = Paths.get(mobiPlansConfig.data().modelBaseDir())
                .resolve(MOBiPlansConfig.MODEL_BASE_FOLDER_CHOICE_MODELS)
                .resolve(MOBiPlansConfig.STRATA_DEFINITIONS)
                .toString();

        // Temporary maps to hold parsed logic (keyed by lowercase string for robust merging)
        Map<String, String> prettyNameMap = new LinkedHashMap<>();
        Map<String, DemandStrata.Type> typeMap = new LinkedHashMap<>();
        Map<String, Predicate<Person>> conditionMap = new LinkedHashMap<>();

        logger.info("Loading strata configuration from file: " + filePath);

        try (BufferedReader br = IOUtils.getBufferedReader(filePath)) {
            String line;
            boolean isHeader = true;

            while ((line = br.readLine()) != null) {
                if (isHeader) {
                    isHeader = false;
                    continue;
                }

                if (line.trim().isEmpty() || line.startsWith("#")) continue;

                String[] parts = line.split(";", 3);
                if (parts.length < 2) continue;

                String rawName = parts[0].trim();
                String mapKey = rawName.toLowerCase();
                DemandStrata.Type type = DemandStrata.Type.valueOf(parts[1].trim());
                String conditionsStr = parts.length > 2 ? parts[2].trim() : "";

                prettyNameMap.putIfAbsent(mapKey, rawName);
                typeMap.putIfAbsent(mapKey, type);

                // Build the AND chain for this specific row
                Predicate<Person> rowPredicate = p -> true;
                if (!conditionsStr.isEmpty()) {
                    for (String cond : conditionsStr.split(",")) {
                        rowPredicate = rowPredicate.and(parseCondition(cond.trim()));
                    }
                }

                // If stratum already exists (from a previous row), merge using OR
                conditionMap.merge(mapKey, rowPredicate, Predicate::or);
            }

            // Create and register the final domain objects
            for (String key : typeMap.keySet()) {
                DemandStrata strata = new DemandStrata(
                        prettyNameMap.get(key), // Preserve original casing
                        typeMap.get(key),
                        conditionMap.get(key)
                );
                registry.register(strata);
            }

            logger.info("Loaded and registered " + registry.getAll().size() + " demand strata.");

        } catch (IOException e) {
            throw new RuntimeException("Failed to read strata configuration file: " + filePath, e);
        }

        return registry;
    }

    private static Predicate<Person> parseCondition(String condition) {
        String[] operators = {"!=", ">=", "<=", "=", ">", "<"};
        String op = null;
        int opIdx = -1;

        for (String o : operators) {
            opIdx = condition.indexOf(o);
            if (opIdx != -1) {
                op = o;
                break;
            }
        }

        if (op == null) throw new IllegalArgumentException("No operator found: " + condition);

        String key = condition.substring(0, opIdx).trim();
        String valueStr = condition.substring(opIdx + op.length()).trim();

        String finalOp = op;
        return person -> {
            Object attr = person.getAttributes().getAttribute(key);

            // Null-safe handling
            if (attr == null) {
                return finalOp.equals("!=");
            }

            // IN-list logic (e.g. pt_subscription = GA|VA)
            if ((finalOp.equals("=") || finalOp.equals("!=")) && valueStr.contains("|")) {
                String[] options = valueStr.split("\\|");
                boolean match = false;
                for (String opt : options) {
                    if (attr.toString().equals(opt.trim())) {
                        match = true;
                        break;
                    }
                }
                return finalOp.equals("=") ? match : !match;
            }

            if (attr instanceof Boolean) {
                boolean target = Boolean.parseBoolean(valueStr);
                if (finalOp.equals("=")) return ((Boolean) attr) == target;
                if (finalOp.equals("!=")) return ((Boolean) attr) != target;
                return false;
            }

            if (attr instanceof Number) {
                double attrNum = ((Number) attr).doubleValue();
                double targetNum;
                try {
                    targetNum = Double.parseDouble(valueStr);
                } catch (NumberFormatException e) {
                    return false;
                }
                return switch (finalOp) {
                    case "=" -> attrNum == targetNum;
                    case "!=" -> attrNum != targetNum;
                    case ">" -> attrNum > targetNum;
                    case "<" -> attrNum < targetNum;
                    case ">=" -> attrNum >= targetNum;
                    case "<=" -> attrNum <= targetNum;
                    default -> false;
                };
            }

            String attrStr = attr.toString();
            return switch (finalOp) {
                case "=" -> attrStr.equals(valueStr);
                case "!=" -> !attrStr.equals(valueStr);
                case ">" -> attrStr.compareTo(valueStr) > 0;
                case "<" -> attrStr.compareTo(valueStr) < 0;
                case ">=" -> attrStr.compareTo(valueStr) >= 0;
                case "<=" -> attrStr.compareTo(valueStr) <= 0;
                default -> false;
            };
        };
    }
}