package ch.sbb.matsim.plans.models.steps;

import ch.sbb.matsim.csv.CSVWriter;
import ch.sbb.matsim.plans.MOBiPlansRunner;
import ch.sbb.matsim.plans.models.config.MOBiPlansConfig;
import ch.sbb.matsim.plans.models.utils.BuildPredicate;
import ch.sbb.matsim.plans.models.utils.ExpressionEvaluator;
import ch.sbb.matsim.plans.stats.Attributables;
import ch.sbb.matsim.plans.stats.LookupAttributeHandler;
import ch.sbb.matsim.plans.stats.LookupFormulaParser;
import ch.sbb.matsim.plans.stats.ReportJson;
import ch.sbb.matsim.zones.Zone;
import ch.sbb.matsim.zones.Zones;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.inject.Inject;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.Plan;
import org.matsim.core.router.TripStructureUtils;
import org.matsim.utils.objectattributes.attributable.Attributable;
import org.matsim.utils.objectattributes.attributable.Attributes;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static ch.sbb.matsim.plans.stats.LookupFormulaParser.parseFormula;

public class StatsWriter implements ModelStep {
    @Inject
    Scenario scenario;
    @Inject
    MOBiPlansConfig config;
    @Inject
    Zones zones;
    ExpressionEvaluator expressionEvaluator = new ExpressionEvaluator();
    LookupFormulaParser lookupFormulaParser = new LookupFormulaParser();
    Attributables.AttributableProvider attributableProvider = new Attributables.AttributableProvider(zones);
    LookupAttributeHandler lookupAttributeHandler = new LookupAttributeHandler();

    @Override
    public String getName() {
        return MOBiPlansRunner.WRITE_STATS;
    }

    @Override
    public void run() {
        Path reportsFolder = Paths.get(config.data().modelBaseDir()).resolve(MOBiPlansConfig.MODEL_BASE_FOLDER_REPORTS);
        String reportConfigFilePath = reportsFolder.resolve(MOBiPlansConfig.REPORT_CONFIG_JSON).toString();

        File jsonFile = new File(reportConfigFilePath);
        ReportJson.ReportConfig reportConfig = null;
        ObjectMapper mapper = new ObjectMapper();
        try {
            reportConfig = mapper.readValue(jsonFile, ReportJson.ReportConfig.class);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        lookupAttributeHandler.createLookUpVariables(reportConfig);
        lookupAttributeHandler.setLookupAttributes(scenario.getPopulation().getPersons().values(), zones);

        for (ReportJson.Sheet s : reportConfig.sheets) {
            if (s.pivots != null && !s.pivots.isEmpty()) {
                writePivotSheet(s);
            } else {
                writeSheet(s);
            }
        }
    }

    public void writeSheet(ReportJson.Sheet s) {
        Map<String, List<String>> requiredVariables = new HashMap<>();
        List<String> variable_names = new ArrayList<>();
        for (ReportJson.ReportVariable variable : s.variables) {
            requiredVariables.put(variable.name, expressionEvaluator.getShortVariableNames(variable.formula));
            variable_names.add(variable.name);
        }
        for (ReportJson.ReportGroup group : s.groups) {
            group.filter_expression = group.filter_expression.replace("[", "").replace("]", "");
        }

        double[][] sums;
        int[][] counts;
        sums = new double[s.groups.size()][s.variables.size()];
        counts = new int[s.groups.size()][s.variables.size()];
        for (int groupIndex = 0; groupIndex < s.groups.size(); groupIndex++) {
            ReportJson.ReportGroup group = s.groups.get(groupIndex);
            switch (s.table_ref) {
                case "Persons": {
                    String filterExpression = group.filter_expression;
                    Predicate<Person> groupFilterPredicate = BuildPredicate.buildPredicate(filterExpression, Person.class);
                    int finalGroupIndex1 = groupIndex;
                    scenario.getPopulation().getPersons().values().stream().forEach(person -> {
                        if (groupFilterPredicate.test(person)) {
                            for (int varIndex = 0; varIndex < s.variables.size(); varIndex++) {
                                ReportJson.ReportVariable variable = s.variables.get(varIndex);
                                LookupFormulaParser.ParseResult formulaParser = parseFormula(variable.formula);
                                if ("Trips".equals(formulaParser.lookupTable)) {
                                    Plan plan = person.getSelectedPlan();
                                    if (plan != null) {
                                        for (TripStructureUtils.Trip trip : TripStructureUtils.getTrips(plan)) {
                                            Attributables.AttributableProvider.TripAttributable tripAtt = new Attributables.AttributableProvider.TripAttributable(trip, person);
                                            tripAtt.addLookupAttributes(lookupAttributeHandler.getLookupAttributesForTrip(tripAtt));
                                            boolean tripFilter = true;
                                            if (formulaParser.filterExpression != null) {
                                                Predicate<Attributables.AttributableProvider.TripAttributable> groupFilterPredicateTrip = BuildPredicate.buildPredicate(formulaParser.filterExpression, Attributables.AttributableProvider.TripAttributable.class);
                                                tripFilter = groupFilterPredicateTrip.test(tripAtt);
                                            }
                                            if (tripFilter) {
                                                switch (formulaParser.aggregator) {
                                                    case "Sum": {
                                                        sums[finalGroupIndex1][varIndex] += expressionEvaluator.evaluate(formulaParser.lookupExpression, collectAttributeStrings(tripAtt, requiredVariables.get(variable.name), Attributables.AttributableProvider.TripAttributable.class));
                                                        break;
                                                    }
                                                    case "Count": {
                                                        sums[finalGroupIndex1][varIndex]++;
                                                        break;
                                                    }
                                                }
                                            }
                                            counts[finalGroupIndex1][varIndex]++;
                                        }
                                    }
                                } else {
                                    sums[finalGroupIndex1][varIndex] += expressionEvaluator.evaluate(variable.formula, collectAttributeStrings(person, requiredVariables.get(variable.name), Person.class));
                                    counts[finalGroupIndex1][varIndex]++;
                                }
                            }
                        }
                    });
                    break;
                }
                case "Zones": {
                    String filterExpression = group.filter_expression;
                    Predicate<Zone> groupFilterPredicate = BuildPredicate.buildPredicate(filterExpression, Zone.class);
                    int finalGroupIndex = groupIndex;
                    zones.getZones().stream().forEach(zone -> {
                        if (groupFilterPredicate.test(zone)) {
                            for (int varIndex = 0; varIndex < s.variables.size(); varIndex++) {
                                ReportJson.ReportVariable variable = s.variables.get(varIndex);
                                sums[finalGroupIndex][varIndex] += expressionEvaluator.evaluate(variable.formula, collectAttributeStrings(zone, requiredVariables.get(variable.name), Zone.class));
                                counts[finalGroupIndex][varIndex]++;
                            }
                        }
                    });
                    break;
                }
                case "Trips": {
                    String filterExpression = group.filter_expression;
                    String filterExpressionPerson = group.filter_expression_person;
                    Predicate<Person> groupFilterPredicatePerson = BuildPredicate.buildPredicate(filterExpressionPerson, Person.class);
                    int finalGroupIndex1 = groupIndex;
                    scenario.getPopulation().getPersons().values().stream().forEach(person -> {
                        if (groupFilterPredicatePerson.test(person)) {
                            Plan plan = person.getSelectedPlan();
                            if (plan != null) {
                                for (TripStructureUtils.Trip trip : TripStructureUtils.getTrips(plan)) {
                                    Attributables.AttributableProvider.TripAttributable tripAtt = new Attributables.AttributableProvider.TripAttributable(trip, person);
                                    tripAtt.addLookupAttributes(lookupAttributeHandler.getLookupAttributesForTrip(tripAtt));
                                    Predicate<Attributables.AttributableProvider.TripAttributable> groupFilterPredicateTrip = BuildPredicate.buildPredicate(filterExpression, Attributables.AttributableProvider.TripAttributable.class);
                                    if (groupFilterPredicateTrip.test(tripAtt)) {
                                        for (int varIndex = 0; varIndex < s.variables.size(); varIndex++) {
                                            ReportJson.ReportVariable variable = s.variables.get(varIndex);
                                            sums[finalGroupIndex1][varIndex] += expressionEvaluator.evaluate(variable.formula, collectAttributeStrings(tripAtt, requiredVariables.get(variable.name), Attributables.AttributableProvider.TripAttributable.class));
                                            counts[finalGroupIndex1][varIndex]++;
                                        }
                                    }
                                }
                            }
                        }
                    });
                    break;
                }
            }
        }

        String outputFile = config.data().outputBaseDir() + config.models().run_id() + "." + s.name.replace(" ", "_") + ".csv";
        variable_names.addFirst("Name");
        try (CSVWriter csvWriter = new CSVWriter("", variable_names.toArray(String[]::new), outputFile)) {
            for (int groupIndex = 0; groupIndex < s.groups.size(); groupIndex++) {
                ReportJson.ReportGroup group = s.groups.get(groupIndex);
                csvWriter.set("Name", group.name);
                for (int varIndex = 0; varIndex < s.variables.size(); varIndex++) {
                    ReportJson.ReportVariable variable = s.variables.get(varIndex);
                    Double val = 0.0;
                    switch (variable.aggregation) {
                        case "Sum":
                            val = sums[groupIndex][varIndex];
                            break;
                        case "Count":
                            val = Double.valueOf(String.valueOf(counts[groupIndex][varIndex]));
                            break;
                        case "Mean": {
                            if (counts[groupIndex][varIndex] != 0)
                                val = sums[groupIndex][varIndex] / counts[groupIndex][varIndex];
                            break;
                        }
                    }
                    if (variable.normalize != null) {
                        double total = 0.0;
                        for (int vIndex = 0; vIndex < s.variables.size(); vIndex++) {
                            if (s.variables.get(vIndex).name.equals(variable.normalize)) {
                                total = sums[groupIndex][vIndex];
                                break;
                            }
                        }
                        if (total != 0) {
                            val = val / total;
                        }
                    }
                    csvWriter.set(variable.name, String.valueOf(val));
                }
                csvWriter.writeRow();
            }
        } catch (Exception e) {
            //noinspection CallToPrintStackTrace
            e.printStackTrace();
        }
    }

    public void writePivotSheet(ReportJson.Sheet s) {
        Map<String, List<String>> requiredVariables = new HashMap<>();
        List<String> variable_names = new ArrayList<>();
        requiredVariables.put("index_formula", expressionEvaluator.getShortVariableNames(s.pivots.get("index_formula").toString()));
        requiredVariables.put("column_formula", expressionEvaluator.getShortVariableNames(s.pivots.get("column_formula").toString()));
        requiredVariables.put("attribute", expressionEvaluator.getShortVariableNames(s.pivots.get("attribute").toString()));
        String indexName = s.pivots.get("index_name").toString();
        String columnFormula = s.pivots.get("column_formula").toString().replace("[", "").replace("]", "");
        String indexFormula = s.pivots.get("index_formula").toString().replace("[", "").replace("]", "");
        String attribute = s.pivots.get("attribute").toString().replace("[", "").replace("]", "");

        String filterExpression = s.pivots.get("filter_expression").toString().replace("[", "").replace("]", "");

        Map<Double, Map<Double, Double>> pivot = null;

        switch(s.table_ref){
            case "Persons": {
                Predicate<Person> filterPredicate = BuildPredicate.buildPredicate(filterExpression, Person.class);
                if ("Sum".equals(s.pivots.get("aggregation").toString())) {
                    pivot = scenario.getPopulation().getPersons().values().stream()
                            .filter(person -> filterPredicate.test(person))
                            .collect(Collectors.groupingBy(
                                    person -> expressionEvaluator.evaluate(indexFormula, collectAttributeStrings(person, requiredVariables.get("index_formula"), Person.class)),
                                    Collectors.groupingBy(
                                            person -> expressionEvaluator.evaluate(columnFormula, collectAttributeStrings(person, requiredVariables.get("column_formula"), Person.class)),
                                            Collectors.summingDouble(person -> expressionEvaluator.evaluate(attribute, collectAttributeStrings(person, requiredVariables.get("attribute"), Person.class)))
                                    )
                            ));
                } else if ("Count".equals(s.pivots.get("aggregation").toString())) {
                    pivot = scenario.getPopulation().getPersons().values().stream()
                            .filter(person -> filterPredicate.test(person))
                            .collect(Collectors.groupingBy(
                                    person -> expressionEvaluator.evaluate(indexFormula, collectAttributeStrings(person, requiredVariables.get("index_formula"), Person.class)),
                                    Collectors.groupingBy(
                                            person -> expressionEvaluator.evaluate(columnFormula, collectAttributeStrings(person, requiredVariables.get("column_formula"), Person.class)),
                                            Collectors.summingDouble(p -> 1.0)
                                    )
                            ));
                }
                break;
            }
            case "Trips": {
                Predicate<Attributables.AttributableProvider.TripAttributable> filterPredicate = BuildPredicate.buildPredicate(filterExpression, Attributables.AttributableProvider.TripAttributable.class);
                if ("Sum".equals(s.pivots.get("aggregation").toString())) {
                   pivot = scenario.getPopulation().getPersons().values().stream()
                            .collect(Collectors.groupingBy(
                                    person -> expressionEvaluator.evaluate(indexFormula, collectAttributeStrings(person, requiredVariables.get("index_formula"), Person.class)),
                                    // downstream collector: group by column and sum the values
                                    Collectors.groupingBy(
                                            person -> expressionEvaluator.evaluate(columnFormula, collectAttributeStrings(person, requiredVariables.get("column_formula"), Person.class)),
                                            Collectors.summingDouble(person -> expressionEvaluator.evaluate(attribute, collectAttributeStrings(person, requiredVariables.get("attribute"), Person.class)))
                                    )
                            ));
                } else if ("Count".equals(s.pivots.get("aggregation").toString())) {
                    pivot = scenario.getPopulation().getPersons().values().stream()
                            .collect(Collectors.groupingBy(
                                    person -> expressionEvaluator.evaluate(indexFormula, collectAttributeStrings(person, requiredVariables.get("index_formula"), Person.class)),
                                    // downstream collector: group by column and sum the values
                                    Collectors.groupingBy(
                                            person -> expressionEvaluator.evaluate(columnFormula, collectAttributeStrings(person, requiredVariables.get("column_formula"), Person.class)),
                                            Collectors.summingDouble(p -> 1.0)
                                    )
                            ));

                }
                break;
            }
            case "Zones": {
                Predicate<Zone> filterPredicate = BuildPredicate.buildPredicate(filterExpression, Zone.class);
                if ("Sum".equals(s.pivots.get("aggregation").toString())) {
                    pivot = zones.getZones().stream()
                            .filter(zone -> filterPredicate.test(zone))
                            .collect(Collectors.groupingBy(
                                    zone -> expressionEvaluator.evaluate(indexFormula, collectAttributeStrings(zone, requiredVariables.get("indexName"), Zone.class)),
                                    // downstream collector: group by column and sum the values
                                    Collectors.groupingBy(
                                            zone -> expressionEvaluator.evaluate(columnFormula, collectAttributeStrings(zone, requiredVariables.get("columnName"), Zone.class)),
                                            Collectors.summingDouble(zone -> expressionEvaluator.evaluate(attribute, collectAttributeStrings(zone, requiredVariables.get("attribute"), Zone.class)))
                                    )
                            ));
                } else if ("Count".equals(s.pivots.get("aggregation").toString())) {
                    pivot = zones.getZones().stream()
                            .filter(zone -> filterPredicate.test(zone))
                            .collect(Collectors.groupingBy(
                                    person -> expressionEvaluator.evaluate(indexFormula, collectAttributeStrings(person, requiredVariables.get("indexName"), Person.class)),
                                    // downstream collector: group by column and sum the values
                                    Collectors.groupingBy(
                                            person -> expressionEvaluator.evaluate(columnFormula, collectAttributeStrings(person, requiredVariables.get("columnName"), Person.class)),
                                            Collectors.summingDouble(p -> 1.0)
                                    )
                            ));

                }
                break;
            }
        }

        String outputFile = config.data().outputBaseDir() + config.models().run_id() + "." + s.name.replace(" ", "_") + ".csv";

        String[] cols = pivot.values().stream()
                .flatMap(inner -> inner.keySet().stream())
                .distinct()
                .sorted()
                .map(String::valueOf)              // convert Double to String (handles null too)
                .toArray(String[]::new);
        String[] columns = new String[cols.length + 1];

        columns[0] = indexName;
        System.arraycopy(cols, 0, columns, 1, cols.length);

        try (CSVWriter csvWriter = new CSVWriter("", columns, outputFile)) {
            for (Double index : pivot.keySet()) {
                csvWriter.set(indexName, index.toString());
                for (Double column : pivot.get(index).keySet()) {
                    csvWriter.set(column.toString(), pivot.get(index).get(column).toString());
                }
                csvWriter.writeRow();
            }
        } catch (Exception e) {
            //noinspection CallToPrintStackTrace
            e.printStackTrace();
        }

    }

    private static Map<String, Double> collectAttributeStrings(Attributable obj, Iterable<String> variables, Class<? extends Attributable> clazz) {
        Map<String, Double> result = new HashMap<>();
        if (obj == null || variables == null) return result;

        Attributes attrs = obj.getAttributes();
        for (String variable : variables) {
            if (variable == null) continue;
            if (attrs.getAttribute(variable) != null) {
                Object att_value = attrs.getAttribute(variable);
                if (att_value == null) continue;
                if (!(att_value instanceof Number)) continue;
                Double value = Double.valueOf(att_value.toString());
                result.put(variable, value);
            }
        }
        return result;
    }
}

