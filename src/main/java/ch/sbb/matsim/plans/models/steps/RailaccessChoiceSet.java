package ch.sbb.matsim.plans.models.steps;

import ch.sbb.matsim.config.variables.SBBActivities;
import ch.sbb.matsim.csv.CSVReader;
import ch.sbb.matsim.plans.MOBiPlansRunner;
import ch.sbb.matsim.plans.constants.SynpopConstants;
import ch.sbb.matsim.plans.constants.ZonalAttributes;
import ch.sbb.matsim.plans.models.config.MOBiPlansConfig;
import ch.sbb.matsim.plans.models.selector.BinaryLinearProbabilitySelector;
import ch.sbb.matsim.plans.models.selector.ChoiceSelector;
import ch.sbb.matsim.plans.models.utilitycomponents.UtilityCalculator;
import ch.sbb.matsim.plans.models.utils.AttributeUtils;
import ch.sbb.matsim.plans.models.utils.ChoiceModelCSVReader;
import ch.sbb.matsim.plans.synpop.SynPopUtils;
import ch.sbb.matsim.zones.Zone;
import ch.sbb.matsim.zones.Zones;
import jakarta.inject.Inject;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.population.Person;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ForkJoinPool;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static ch.sbb.matsim.plans.constants.SynpopConstants.AGE_CAT;

public class RailaccessChoiceSet implements ModelStep {
    Scenario scenario;
    MOBiPlansConfig config;
    Zones zones;


    private static final Logger logger = LogManager.getLogger(RailaccessChoiceSet.class);
    private static final Pattern RULE_PATTERN = Pattern.compile("\\s*([a-zA-Z_0-9]+)\\s*(==|!=|<=|>=|<|>|=)\\s*([0-9.-]+)\\s*");

    private final List<String> PERSON_AGE_GROUP_VARS = Arrays.asList(
            "pop_0017", "pop_1824", "pop_2544", "pop_4564", "pop_6574", "pop_75xx"
    );

    private static final String[] ACTIVITY_TYPES = {
            SBBActivities.accompany, SBBActivities.business, SBBActivities.education, SBBActivities.education_continued,
            SBBActivities.leisure, SBBActivities.other, SBBActivities.shopping, SBBActivities.home, SBBActivities.work
    };

    private final String csvFilePath;
    private final HashMap<String, SegmentModel> segmentModels;

    private static class SegmentModel {
        private final Predicate<Person> filterPredicate;
        private final String attribute;
        private ChoiceSelector selectionModel;
        private final HashMap<String, Double> activityProbabilities;

        public SegmentModel(Predicate<Person> filterPredicate, String attribute) {
            this.filterPredicate = filterPredicate;
            this.attribute = attribute;
            this.activityProbabilities = new HashMap<>();
        }
    }

    @Inject
    public RailaccessChoiceSet(Scenario scenario, MOBiPlansConfig config, Zones zones) {
        this.scenario = scenario;
        this.config = config;
        this.zones = zones;
        this.segmentModels = new HashMap<>();
        Path modelFolder = Paths.get(config.data().modelBaseDir()).resolve(MOBiPlansConfig.MODEL_BASE_FOLDER_CHOICE_MODELS);
        this.csvFilePath = modelFolder.resolve(MOBiPlansConfig.RAILACCESS_CHOICESET_CSV).toString();
    }

    @Override
    public String getName() {
        return MOBiPlansRunner.RAIL_ACCESS_CHOICESET;
    }

    @Override
    public void run() {
        logger.info("Starting railaccess choiceset calculation...");
        try {
            prepareSegmentModels(csvFilePath);
        } catch (IOException e) {
            throw new RuntimeException("Failed to load Railaccess config", e);
        }

        // ONE single parallel stream for both Extra Variables AND Model Calculation
        ForkJoinPool customPool = new ForkJoinPool(config.models().threads());
        try {
            customPool.submit(this::processPopulation).get();
        } catch (Exception e) {
            throw new RuntimeException("Parallel execution failed", e);
        } finally {
            customPool.shutdown();
        }

        logger.info("Finished railaccess choiceset calculation.");
    }

    private void processPopulation() {
        Map<String, Integer> ageGroupLowerBounds = computeAgeGroupLowerBounds(PERSON_AGE_GROUP_VARS);
        List<String> orderedAgeLabels = orderAgeLabelsByLowerBound(PERSON_AGE_GROUP_VARS, ageGroupLowerBounds);

        scenario.getPopulation().getPersons().values().parallelStream().forEach(person -> {

            // 1. Calculate Extra Variables
            int age = AttributeUtils.getAsInteger(person, SynpopConstants.AGE, -1);
            String age_cat = String.valueOf(getAgeCat(age, orderedAgeLabels, ageGroupLowerBounds));
            person.getAttributes().putAttribute(AGE_CAT, age_cat);
            Random random = SynPopUtils.getRandom(person.getId(), getName());

            Zone zone = (Zone) person.getAttributes().getAttribute(SynpopConstants.ZONE);
            if (zone != null) {
                Double motorisation_level = AttributeUtils.getAsDouble(zone, ZonalAttributes.MOTORISATION_LEVEL, 0.0);
                person.getAttributes().putAttribute(ZonalAttributes.MOTORISATION_LEVEL, motorisation_level);

                Integer sl3_id = AttributeUtils.getAsInteger(zone, ZonalAttributes.SL3_ID, -1);
                person.getAttributes().putAttribute(ZonalAttributes.SL3_ID, sl3_id);
            }

            // 2. Evaluate Segment Models
            for (SegmentModel segment : segmentModels.values()) {
                if (segment.filterPredicate.test(person)) {

                    // A. Main Access - Let your LogitSelector do the math!
                    // It returns "0" or "1" based on the utilities and random draw inside the selector
                    String accessAvailable = segment.selectionModel.select(person);
                    person.getAttributes().putAttribute(segment.attribute + "2pt", accessAvailable);

                    // B. Activity-Specific Access
                    String attributeForActivities = segment.attribute + "2pt_act";
                    StringBuilder valueBuilder = new StringBuilder();

                    if ("1".equals(accessAvailable)) {
                        for (String activityType : ACTIVITY_TYPES) {
                            Double actProb = segment.activityProbabilities.get(activityType);

                            // If actProb exists, do a random draw. If it doesn't exist, assume true.
                            boolean actAccessAvailable = (actProb == null) || (random.nextDouble() <= actProb);

                            if (actAccessAvailable) {
                                if (!valueBuilder.isEmpty()) valueBuilder.append(",");
                                valueBuilder.append(activityType.toLowerCase());
                            }
                        }
                    }
                    person.getAttributes().putAttribute(attributeForActivities, valueBuilder.toString());
                }
            }
        });
    }
    public void prepareSegmentModels(String filePath) throws IOException {
        HashMap<String, ArrayList<Map<String, String>>> linesForModels = new HashMap<>();

        try (CSVReader reader = new CSVReader(filePath, ";")) {
            var row = reader.readLine();
            while (row != null) {
                String attribute = row.get("railaccess_attribute").toLowerCase();
                String segment = row.get("segment").toLowerCase();
                String filter_expression = row.get("filter_expression").toLowerCase();
                String key = attribute + "_" + segment;

                segmentModels.putIfAbsent(key, new SegmentModel(buildPredicate(filter_expression), attribute));
                SegmentModel model = segmentModels.get(key);

                if ("activity".equals(row.get("type"))) {
                    // Store the simple probabilities for the sub-activities
                    String activityType = row.get("attribute").toLowerCase();
                    Double activityProbability = Double.valueOf(row.get("beta_1"));
                    model.activityProbabilities.put(activityType, activityProbability);
                } else {
                    // Save the raw row to pass to your ChoiceModelCSVReader
                    linesForModels.computeIfAbsent(key, k -> new ArrayList<>()).add(row);
                }
                row = reader.readLine();
            }
        }

        // Build the Logit models using your existing architecture!
        for (String segmentKey : linesForModels.keySet()) {
            SegmentModel segmentModel = segmentModels.get(segmentKey);
            ArrayList<Map<String, String>> rows = linesForModels.get(segmentKey);

            // Create the Utility Calculators for alternatives "0" and "1"
            Map<String, UtilityCalculator> calculators = ChoiceModelCSVReader.getChoiceModelFromRows(rows);

            segmentModel.selectionModel = new BinaryLinearProbabilitySelector(calculators,  getName()+ segmentKey);
        }
    }

    private List<String> orderAgeLabelsByLowerBound(List<String> personAgeGroupVars, Map<String, Integer> ageGroupLowerBounds) {
        return personAgeGroupVars.stream()
                .filter(ageGroupLowerBounds::containsKey)
                .sorted(Comparator.comparingInt(ageGroupLowerBounds::get))
                .toList();
    }

    private Map<String, Integer> computeAgeGroupLowerBounds(List<String> personAgeGroupVars) {
        Map<String, Integer> lowerBounds = new HashMap<>();
        for (String var : personAgeGroupVars) {
            String[] parts = var.split("_");
            if (parts.length != 2) continue;

            String agePart = parts[1];
            try {
                int lowerBound = Integer.parseInt(agePart.substring(0, agePart.length() - 2));
                lowerBounds.put(var, lowerBound);
            } catch (NumberFormatException e) {
                logger.warn("Could not parse age bounds from variable '{}'.", var);
            }
        }
        return lowerBounds;
    }

    private static int getAgeCat(int age, List<String> orderedLabels, Map<String, Integer> lowerBounds) {
        for (int i = 0; i < orderedLabels.size(); i++) {
            String label = orderedLabels.get(i);
            int lower = lowerBounds.get(label);
            int upper = (i + 1 < orderedLabels.size()) ? lowerBounds.get(orderedLabels.get(i + 1)) : 1000;
            if (age >= lower && age < upper) {
                return i + 1; // FIX: Make it 1-indexed to match the CSV (age_cat_1, age_cat_2, etc.)
            }
        }
        return -1;
    }

    private Predicate<Person> buildPredicate(String expr) {
        if (expr == null || expr.isEmpty()) return p -> true;

        Predicate<Person> combined = p -> true;
        String[] conditions = expr.split(" & ");

        for (String cond : conditions) {
            cond = cond.trim();

            Matcher m = RULE_PATTERN.matcher(cond);
            if (m.matches()) {
                String attrName = m.group(1);
                String operator = m.group(2);
                double targetValue = Double.parseDouble(m.group(3));

                combined = combined.and(createAttributeCondition(attrName, operator, targetValue));
            } else {
                logger.warn("Could not parse rule condition: '{}'. This condition will be ignored.", cond);
            }
        }
        return combined;
    }

    private Predicate<Person> createAttributeCondition(String attrName, String operator, double targetValue) {
        // Treat single '=' from CSV as '=='
        final String cleanOperator = operator.equals("=") ? "==" : operator;

        return person -> {
            Object val = person.getAttributes().getAttribute(attrName);

            double personValue;
            if (val == null) {
                personValue = 0.0; // Default to 0 if attribute is missing
            } else if (val instanceof Boolean) {
                personValue = ((Boolean) val) ? 1.0 : 0.0;
            } else if (val instanceof Number) {
                personValue = ((Number) val).doubleValue();
            } else {
                try {
                    personValue = Double.parseDouble(val.toString());
                } catch (NumberFormatException e) {
                    return false;
                }
            }

            return switch (cleanOperator) {
                case "==" -> personValue == targetValue;
                case "!=" -> personValue != targetValue;
                case ">" -> personValue > targetValue;
                case "<" -> personValue < targetValue;
                case ">=" -> personValue >= targetValue;
                case "<=" -> personValue <= targetValue;
                default -> false;
            };
        };
    }
}