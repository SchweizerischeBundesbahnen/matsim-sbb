package ch.sbb.matsim.plans.models.steps;

import ch.sbb.matsim.config.variables.SBBActivities;
import ch.sbb.matsim.csv.CSVReader;
import ch.sbb.matsim.plans.MOBiPlansRunner;
import ch.sbb.matsim.plans.models.config.MOBiPlansConfig;
import ch.sbb.matsim.plans.models.steps.csvmodels.StringSelectionModelFromRows;
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
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


public class RailaccessChoiceSet implements ModelStep {

    private final List<String> PERSON_AGE_GROUP_VARS = Arrays.asList(
            "pop_0017", "pop_1824", "pop_2544", "pop_4564", "pop_6574",
            "pop_75xx"
    );

    private static final String[] ACTIVITY_TYPES = {
            SBBActivities.accompany, SBBActivities.business, SBBActivities.education, SBBActivities.education_continued,
            SBBActivities.leisure, SBBActivities.other, SBBActivities.shopping, SBBActivities.home
    };

    private static class SegmentModel {
        private final Predicate<Person>  filterPredicate;
        private final String attribute;
        private StringSelectionModelFromRows selectionModel;
        private final HashMap<String, StringSelectionModelFromRows> modelsPerActivity;

        public SegmentModel(Predicate<Person> filterPredicate, String attribute) {
            this.filterPredicate = filterPredicate;
            this.attribute = attribute;
            this.selectionModel = null;
            this.modelsPerActivity = new HashMap<>();
        }
    }

    final Scenario scenario;
    final MOBiPlansConfig mobiPlansConfig;
    final Zones zones;
    HashMap<String, SegmentModel> segmentModels;
    private final String csvFilePath;

    private static final Logger logger = LogManager.getLogger(RailaccessChoiceSet.class);

    @Inject
    public RailaccessChoiceSet(Scenario scenario, MOBiPlansConfig mobiPlansConfig, Zones zones) {
        this.scenario = scenario;
        this.mobiPlansConfig = mobiPlansConfig;
        this.zones = zones;
        this.segmentModels = new HashMap<>();
        Path modelFolder = Paths.get(mobiPlansConfig.data().modelBaseDir()).resolve(MOBiPlansConfig.MODEL_BASE_FOLDER_CHOICE_MODELS);
        this.csvFilePath = modelFolder.resolve(MOBiPlansConfig.RAILACCESS_CHOICESET_CSV).toString();

    }

    @Override
    public void run() {
        logger.info("Starting railaccess choiceset calculation...");
        try {
            prepareSegmentModels(csvFilePath);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        calculateExtraVariables();
        calculate();
    }

    public void calculate() {
        HashMap<String, Long> counts = new HashMap<>();

        for (String segmentKey: segmentModels.keySet()) {
            SegmentModel segment = segmentModels.get(segmentKey);
            String attribute = segment.attribute + "2pt";
            StringSelectionModelFromRows model = segment.selectionModel;
            counts.put(segmentKey, scenario.getPopulation().getPersons().values().parallelStream()
                    .filter(segment.filterPredicate)
                    .peek(person -> {
                        String accessAvailable = model.calculate(person);
                        person.getAttributes().putAttribute(attribute, accessAvailable);
                    })
                    .count());
            String attributeForActivities = segment.attribute + "2pt_act";
            scenario.getPopulation().getPersons().values().parallelStream()
                    .filter(segment.filterPredicate)
                    .forEach(person -> person.getAttributes().putAttribute(attributeForActivities, ""));
            for (String activityType: ACTIVITY_TYPES) {
                StringSelectionModelFromRows activityModel = segment.modelsPerActivity.get(activityType);
                scenario.getPopulation().getPersons().values().parallelStream()
                        .filter(segment.filterPredicate)
                        .filter(person -> ("1").equals(person.getAttributes().getAttribute(attribute).toString()))
                        .forEach(person -> {
                            boolean accessAvailable = activityModel==null?true:("1").equals(activityModel.calculate(person));
                            String value;
                            if (accessAvailable) {
                                value = activityType.toLowerCase();
                                String prevValue = person.getAttributes().getAttribute(attributeForActivities).toString();
                                if (!prevValue.isEmpty()) {
                                    value = prevValue + "," + value;
                                }
                                person.getAttributes().putAttribute(attributeForActivities, value);
                            }
                        });
            }
        }
    }

    public void calculateExtraVariables() {
        Map<String, Integer> ageGroupLowerBounds = computeAgeGroupLowerBounds(PERSON_AGE_GROUP_VARS);
        List<String> orderedAgeLabels = orderAgeLabelsByLowerBound(PERSON_AGE_GROUP_VARS, ageGroupLowerBounds);
        Map<String, Zone> zoneById = indexZonesById(zones);
        scenario.getPopulation().getPersons().values().parallelStream()
                .forEach(person -> {
                    int age = (Integer) person.getAttributes().getAttribute("age");
                    String age_cat = String.valueOf(getAgeCat(age, orderedAgeLabels, ageGroupLowerBounds));
                    person.getAttributes().putAttribute("age_cat", age_cat);

                    String zone_id = person.getAttributes().getAttribute("zone_id").toString();
                    Double motorisation_level = (Double) zoneById.get(zone_id).getAttribute("motorisation_level");
                    person.getAttributes().putAttribute("motorisation_level", motorisation_level);

                    Integer sl3_id = (Integer) zoneById.get(zone_id).getAttribute("sl3_id");
                    person.getAttributes().putAttribute("sl3_id", sl3_id);

                    Boolean car_available = (Boolean) person.getAttributes().getAttribute("car_available");
                    person.getAttributes().putAttribute("car_avail", car_available ? 1 : 0);
                });
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
            if (parts.length != 2) {
                logger.warn("Unexpected age group variable format: '{}'. Expected format is 'pop_XY' where XY are age bounds. This variable will be ignored.", var);
                continue;
            }
            String agePart = parts[1];
            int lowerBound;
            try {
                lowerBound = Integer.parseInt(agePart.substring(0, agePart.length() - 2));
                lowerBounds.put(var, lowerBound);
            } catch (NumberFormatException e) {
                logger.warn("Could not parse age bounds from variable '{}'. This variable will be ignored.", var);
            }
        }
        return lowerBounds;
    }

    private Map<String, Zone> indexZonesById(Zones zones) {
        Map<String, Zone> map = new HashMap<>();
        // Adapt the iteration over your Zones implementation
        for (Zone z : zones.getZones()) {
            map.put(z.getId().toString(), z);
        }
        return map;
    }

    private static int getAgeCat(int age, List<String> orderedLabels, Map<String, Integer> lowerBounds) {
        // Equivalent to pandas.cut with right=false and labels ordered by lower bound.
        // We create artificial upper bound 1000 like in Python.
        for (int i = 0; i < orderedLabels.size(); i++) {
            String label = orderedLabels.get(i);
            int lower = lowerBounds.get(label);
            int upper = (i + 1 < orderedLabels.size()) ? lowerBounds.get(orderedLabels.get(i + 1)) : 1000;
            if (age >= lower && age < upper) {
                return i;
            }
        }
        return -1;
    }

    public void prepareSegmentModels(String filePath) throws IOException {
        HashMap<String, ArrayList<Map<String, String>>> linesForModels = new HashMap<>();
        HashMap<String, Map<String, Double>> activityProbabilities = new HashMap<>();
        try (CSVReader reader = new CSVReader(filePath, ";")) {
            var row = reader.readLine();
            while (row != null) {
                String attribute = row.get("railaccess_attribute").toLowerCase();
                String segment = row.get("segment").toLowerCase();
                String filter_expression = row.get("filter_expression").toLowerCase();
                Predicate<Person> filterPredicate = buildPredicate(filter_expression);

                String key = attribute + "_" + segment;
                final List<String> attributes = List.of("type", "attribute", "values", "lambda", "beta_0", "beta_1");
                if (row.get("type").equals("activity")) {
                    if (!activityProbabilities.containsKey(key)) {
                        activityProbabilities.put(key, new HashMap<>());
                    }
                    String activityType = row.get("attribute").toLowerCase();
                    Double activityProbability = Double.valueOf(row.get("beta_1"));
                    activityProbabilities.get(key).put(activityType, activityProbability);
                } else {
                    if (!segmentModels.containsKey(key)) {
                        SegmentModel model = new SegmentModel(filterPredicate, attribute);
                        segmentModels.put(key, model);
                        ArrayList<Map<String, String>> rows = new ArrayList<>();
                        linesForModels.put(key, rows);
                    }
                    HashMap<String, String> newRow = new HashMap<>();
                    for (String att : attributes) {
                        newRow.put(att, row.get(att));
                    }
                    ArrayList<Map<String, String>> rows = linesForModels.get(key);
                    rows.add(newRow);
                }
                row = reader.readLine();
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        for (String segmentKey : linesForModels.keySet()) {
            SegmentModel segmentModel = segmentModels.get(segmentKey);
            segmentModel.selectionModel = new StringSelectionModelFromRows(linesForModels.get(segmentKey), MOBiPlansConfig.RAILACCESS_CHOICESET_CSV+"_"+segmentKey);
            for (String activityType: ACTIVITY_TYPES) {
                if (activityProbabilities.containsKey(segmentKey)) {
                    HashMap<String, String> newRow = new HashMap<>();
                    newRow.put("type", "constant");
                    Double prob_1 = activityProbabilities.get(segmentKey).get(activityType);
                    newRow.put("beta_1", String.valueOf(prob_1));
                    ArrayList<Map<String, String>> rows = new ArrayList<>();
                    rows.add(newRow);
                    segmentModel.modelsPerActivity.put(activityType, new StringSelectionModelFromRows(rows, MOBiPlansConfig.RAILACCESS_CHOICESET_CSV + "_" + segmentKey + "_" + activityType));
                }
            }
        }
    }

    private static final Pattern RULE_PATTERN = Pattern.compile("\\s*([a-zA-Z_0-9]+)\\s*(==|!=|<=|>=|<|>|=)\\s*([0-9.-]+)\\s*");

    private Predicate<Person> buildPredicate(String expr) {
        if (expr == null || expr.isEmpty()) return _ -> true;

        Predicate<Person> combined = _ -> true;
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
            switch (val) {
                case null -> personValue = 0.0; // Default to 0 if attribute is missing
                case Boolean b -> personValue = b ? 1.0 : 0.0;
                case Number number -> personValue = number.doubleValue();
                default -> {
                    try {
                        personValue = Double.parseDouble(val.toString());
                    } catch (NumberFormatException e) {
                        return false;
                    }
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

    @Override
    public String getName() {
        return MOBiPlansRunner.RAIL_ACCESS_CHOICESET;
    }
}
