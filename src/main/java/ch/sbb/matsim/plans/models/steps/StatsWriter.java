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
import ch.sbb.matsim.zones.ZonesLoader;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.inject.Inject;
import org.apache.logging.log4j.LogManager;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.Plan;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.population.io.PopulationReader;
import org.matsim.core.router.TripStructureUtils;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.utils.objectattributes.attributable.Attributable;
import org.matsim.utils.objectattributes.attributable.Attributes;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.atomic.DoubleAdder;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static ch.sbb.matsim.plans.stats.LookupFormulaParser.parseFormula;

public class StatsWriter implements ModelStep {
    Scenario scenario;
    MOBiPlansConfig config;
    Zones zones;
    ExpressionEvaluator expressionEvaluator = new ExpressionEvaluator();
    LookupAttributeHandler lookupAttributeHandler = new LookupAttributeHandler();
    Attributables.AttributableProvider attributableProvider = null;
    @Override
    public String getName() {
        return MOBiPlansRunner.WRITE_STATS;
    }

    @Inject
    public StatsWriter(Scenario scenario, MOBiPlansConfig config, Zones zones) {
        this.scenario = scenario;
        this.config = config;
        this.zones = zones;
        attributableProvider = new Attributables.AttributableProvider(zones);
    }

    @Override
    public void run() {
        Path reportsFolder = Paths.get(config.data().modelBaseDir()).resolve(MOBiPlansConfig.MODEL_BASE_FOLDER_REPORTS);
        String reportConfigFilePath = reportsFolder.resolve(MOBiPlansConfig.REPORT_CONFIG_JSON).toString();

        File jsonFile = new File(reportConfigFilePath);
        ReportJson.ReportConfig reportConfig;
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
        // ---- Pre-compute everything that does not depend on the iterated element ----
        final int nGroups = s.groups.size();
        final int nVars = s.variables.size();

        final List<String> variable_names = new ArrayList<>(nVars);
        // Per variable: required short variable names (as String[] for fast iteration, no per-call allocation)
        final String[][] requiredVariables = new String[nVars][];
        for (int v = 0; v < nVars; v++) {
            ReportJson.ReportVariable variable = s.variables.get(v);
            List<String> req = expressionEvaluator.getShortVariableNames(variable.formula);
            requiredVariables[v] = req.toArray(new String[0]);
            variable_names.add(variable.name);
        }

        // Sanitize group filter expressions once (the original code did this in-place every call)
        for (ReportJson.ReportGroup group : s.groups) {
            group.filter_expression = group.filter_expression.replace("[", "").replace("]", "");
        }

        // Use thread-safe accumulators so we can run with parallel streams.
        final DoubleAdder[][] sums = new DoubleAdder[nGroups][nVars];
        final LongAdder[][] counts = new LongAdder[nGroups][nVars];
        for (int g = 0; g < nGroups; g++) {
            for (int v = 0; v < nVars; v++) {
                sums[g][v] = new DoubleAdder();
                counts[g][v] = new LongAdder();
            }
        }

        switch (s.table_ref) {
            case "Persons": {
                // Pre-build per-group person predicates and per-variable parsed formulas (incl. trip filters).
                @SuppressWarnings("unchecked")
                final Predicate<Person>[] groupPreds = new Predicate[nGroups];
                for (int g = 0; g < nGroups; g++) {
                    groupPreds[g] = BuildPredicate.buildPredicate(s.groups.get(g).filter_expression, Person.class);
                }
                final LookupFormulaParser.ParseResult[] parsed = new LookupFormulaParser.ParseResult[nVars];
                @SuppressWarnings("unchecked")
                final Predicate<Attributables.AttributableProvider.TripAttributable>[] tripFilters = new Predicate[nVars];
                final boolean[] varIsTripLookup = new boolean[nVars];
                for (int v = 0; v < nVars; v++) {
                    ReportJson.ReportVariable variable = s.variables.get(v);
                    LookupFormulaParser.ParseResult pr = parseFormula(variable.formula);
                    parsed[v] = pr;
                    varIsTripLookup[v] = "Trips".equals(pr.lookupTable);
                    if (varIsTripLookup[v] && pr.filterExpression != null) {
                        tripFilters[v] = BuildPredicate.buildPredicate(
                                pr.filterExpression, Attributables.AttributableProvider.TripAttributable.class);
                    }
                }

                scenario.getPopulation().getPersons().values().parallelStream()
                        .filter(person -> "regular".equals(person.getAttributes().getAttribute("subpopulation")))
                        .forEach(person -> {
                            // Determine which groups apply to this person
                            boolean[] inGroup = new boolean[nGroups];
                            boolean any = false;
                            for (int g = 0; g < nGroups; g++) {
                                if (groupPreds[g].test(person)) {
                                    inGroup[g] = true;
                                    any = true;
                                }
                            }
                            if (!any) return;

                    // Pre-collect attribute maps for non-trip variables once per person
                    Map<Integer, Map<String, Double>> personAttrCache = null;

                    // Lazily build trips list (only if any variable needs it)
                    List<Attributables.AttributableProvider.TripAttributable> trips = null;
                    boolean needsTrips = false;
                    for (int v = 0; v < nVars; v++) if (varIsTripLookup[v]) { needsTrips = true; break; }
                    if (needsTrips) {
                        Plan plan = person.getSelectedPlan();
                        if (plan != null) {
                            List<TripStructureUtils.Trip> rawTrips = TripStructureUtils.getTrips(plan);
                            trips = new ArrayList<>(rawTrips.size());
                            for (TripStructureUtils.Trip trip : rawTrips) {
                                Attributables.AttributableProvider.TripAttributable tripAtt =
                                        new Attributables.AttributableProvider.TripAttributable(trip, person);
                                tripAtt.addLookupAttributes(lookupAttributeHandler.getLookupAttributesForTrip(tripAtt));
                                trips.add(tripAtt);
                            }
                        } else {
                            trips = Collections.emptyList();
                        }
                    }

                    for (int v = 0; v < nVars; v++) {
                        ReportJson.ReportVariable variable = s.variables.get(v);
                        if (varIsTripLookup[v]) {
                            if (trips == null || trips.isEmpty()) continue;
                            LookupFormulaParser.ParseResult pr = parsed[v];
                            Predicate<Attributables.AttributableProvider.TripAttributable> tripFilter = tripFilters[v];
                            String[] reqVars = requiredVariables[v];
                            for (Attributables.AttributableProvider.TripAttributable tripAtt : trips) {
                                boolean tripPass = (tripFilter == null) || tripFilter.test(tripAtt);
                                if (tripPass) {
                                    if ("Sum".equals(pr.aggregator)) {
                                        double val = expressionEvaluator.evaluate(
                                                pr.lookupExpression,
                                                collectAttributeStrings(tripAtt, reqVars));
                                        for (int g = 0; g < nGroups; g++) {
                                            if (inGroup[g]) sums[g][v].add(val);
                                        }
                                    } else if ("Count".equals(pr.aggregator)) {
                                        for (int g = 0; g < nGroups; g++) {
                                            if (inGroup[g]) sums[g][v].add(1.0);
                                        }
                                    }
                                }
                                // Preserve original semantics: counts incremented for every trip regardless of filter.
                                for (int g = 0; g < nGroups; g++) {
                                    if (inGroup[g]) counts[g][v].increment();
                                }
                            }
                        } else {
                            // Non-trip variable on Persons table
                            String[] reqVars = requiredVariables[v];
                            double val = expressionEvaluator.evaluate(
                                    variable.formula,
                                    collectAttributeStrings(person, reqVars));
                            for (int g = 0; g < nGroups; g++) {
                                if (inGroup[g]) {
                                    sums[g][v].add(val);
                                    counts[g][v].increment();
                                }
                            }
                        }
                    }
                });
                break;
            }
            case "Zones": {
                @SuppressWarnings("unchecked")
                final Predicate<Zone>[] groupPreds = new Predicate[nGroups];
                for (int g = 0; g < nGroups; g++) {
                    groupPreds[g] = BuildPredicate.buildPredicate(s.groups.get(g).filter_expression, Zone.class);
                }
                zones.getZones().parallelStream().forEach(zone -> {
                    for (int g = 0; g < nGroups; g++) {
                        if (!groupPreds[g].test(zone)) continue;
                        for (int v = 0; v < nVars; v++) {
                            ReportJson.ReportVariable variable = s.variables.get(v);
                            double val = expressionEvaluator.evaluate(
                                    variable.formula,
                                    collectAttributeStrings(zone, requiredVariables[v]));
                            sums[g][v].add(val);
                            counts[g][v].increment();
                        }
                    }
                });
                break;
            }
            case "Trips": {
                @SuppressWarnings("unchecked")
                final Predicate<Person>[] personPreds = new Predicate[nGroups];
                @SuppressWarnings("unchecked")
                final Predicate<Attributables.AttributableProvider.TripAttributable>[] tripPreds = new Predicate[nGroups];
                for (int g = 0; g < nGroups; g++) {
                    ReportJson.ReportGroup group = s.groups.get(g);
                    if (group.filter_expression_person!=null) {
                        personPreds[g] = BuildPredicate.buildPredicate(group.filter_expression_person, Person.class);
                    } else {
                        personPreds[g] = null;
                    }
                    tripPreds[g] = BuildPredicate.buildPredicate(
                            group.filter_expression, Attributables.AttributableProvider.TripAttributable.class);
                }
                scenario.getPopulation().getPersons().values().parallelStream().filter(person -> "regular".equals(person.getAttributes().getAttribute("subpopulation"))).forEach(person -> {
                    // Determine which groups potentially match by person
                    boolean[] personMatch = new boolean[nGroups];
                    boolean any = false;
                    for (int g = 0; g < nGroups; g++) {
                        if (personPreds[g] != null) {
                            if (personPreds[g].test(person)) {
                                personMatch[g] = true;
                                any = true;
                            }
                        } else {
                            personMatch[g] = true;
                            any = true;
                        }
                    }
                    if (!any) return;
                    Plan plan = person.getSelectedPlan();
                    if (plan == null) return;
                    for (TripStructureUtils.Trip trip : TripStructureUtils.getTrips(plan)) {
                        Attributables.AttributableProvider.TripAttributable tripAtt =
                                new Attributables.AttributableProvider.TripAttributable(trip, person);
                        tripAtt.addLookupAttributes(lookupAttributeHandler.getLookupAttributesForTrip(tripAtt));
                        for (int g = 0; g < nGroups; g++) {
                            if (!personMatch[g]) continue;
                            if (!tripPreds[g].test(tripAtt)) continue;
                            for (int v = 0; v < nVars; v++) {
                                ReportJson.ReportVariable variable = s.variables.get(v);
                                double val = expressionEvaluator.evaluate(
                                        variable.formula,
                                        collectAttributeStrings(tripAtt, requiredVariables[v]));
                                sums[g][v].add(val);
                                counts[g][v].increment();
                            }
                        }
                    }
                });
                break;
            }
        }

        String outputFile = config.data().outputBaseDir() +"/"+ config.models().run_id() + "." + s.name.replace(" ", "_") + ".csv";
        variable_names.addFirst("Name");
        try (CSVWriter csvWriter = new CSVWriter("", variable_names.toArray(String[]::new), outputFile)) {
            for (int groupIndex = 0; groupIndex < nGroups; groupIndex++) {
                ReportJson.ReportGroup group = s.groups.get(groupIndex);
                csvWriter.set("Name", group.name);
                for (int varIndex = 0; varIndex < nVars; varIndex++) {
                    ReportJson.ReportVariable variable = s.variables.get(varIndex);
                    double sumVal = sums[groupIndex][varIndex].sum();
                    long countVal = counts[groupIndex][varIndex].sum();
                    Double val = 0.0;
                    switch (variable.aggregation) {
                        case "Sum":
                            val = sumVal;
                            break;
                        case "Count":
                            val = (double) countVal;
                            break;
                        case "Mean": {
                            if (countVal != 0)
                                val = sumVal / countVal;
                            break;
                        }
                    }
                    if (variable.normalize != null) {
                        double total = 0.0;
                        for (int vIndex = 0; vIndex < nVars; vIndex++) {
                            if (s.variables.get(vIndex).name.equals(variable.normalize)) {
                                total = sums[groupIndex][vIndex].sum();
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

    private static Map<String, Double> collectAttributeStrings(Attributable obj, String[] variables) {
        if (obj == null || variables == null || variables.length == 0) return Collections.emptyMap();
        Attributes attrs = obj.getAttributes();
        Map<String, Double> result = new HashMap<>(variables.length * 2);
        for (String variable : variables) {
            if (variable == null) continue;
            Object att_value = attrs.getAttribute(variable);
            if (att_value == null) continue;
            if (!(att_value instanceof Number)) continue;
            result.put(variable, ((Number) att_value).doubleValue());
        }
        return result;
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

    public static void main(String[] args) throws IOException {
        String experiencedPlansFile = args[0];
        String zonesFile = args[2];
        String plansFile = args[1];
        String configFile = args[3];
        Scenario scenario = null;
        Zones zones = ZonesLoader.loadZones("zones", zonesFile, "zone_id");
        final Config matsimConfig = ConfigUtils.createConfig();
        scenario = ScenarioUtils.createScenario(matsimConfig);
        MOBiPlansConfig config = MOBiPlansConfig.readFromYaml(configFile);
        if (!"-".equals(experiencedPlansFile)) {
            new PopulationReader(scenario).readFile(experiencedPlansFile);
        } else {
            new PopulationReader(scenario).readFile(plansFile);
        }
        LogManager.getLogger(StatsWriter.class).info("write zone_id");
        scenario.getPopulation().getPersons().values().stream().forEach(person -> person.getAttributes().putAttribute("zone_id", person.getAttributes().getAttribute("residence_zone_id")));
        StatsWriter statsWriter = new StatsWriter(scenario, config, zones);
        statsWriter.run();
    }
}

