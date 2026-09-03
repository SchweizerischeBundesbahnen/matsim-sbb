package ch.sbb.matsim.plans.models.steps;

import static ch.sbb.matsim.plans.stats.LookupFormulaParser.parseFormula;

import ch.sbb.matsim.csv.CSVWriter;
import ch.sbb.matsim.plans.MOBiPlansRunner;
import ch.sbb.matsim.plans.constants.SynpopConstants;
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
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.DoubleAdder;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
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

public class StatsWriter implements ModelStep {
    private static final Logger log = LogManager.getLogger(StatsWriter.class);
    Scenario scenario;
    MOBiPlansConfig config;
    Zones zones;
    ExpressionEvaluator expressionEvaluator = new ExpressionEvaluator();
    LookupAttributeHandler lookupAttributeHandler = new LookupAttributeHandler();
    Attributables.AttributableProvider attributableProvider;
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

    // ---- Per-sheet precomputed state (kept across the single person loop) ----

    private static final class PersonSheetCtx {
        final ReportJson.Sheet sheet;
        final int nGroups, nVars;
        final Predicate<Person>[] groupPreds;
        final LookupFormulaParser.ParseResult[] parsed;
        final Predicate<Attributables.AttributableProvider.TripAttributable>[] tripFilters;
        final boolean[] varIsTripLookup;
        final String[][] requiredVars;
        final boolean needsTrips;
        final DoubleAdder[][] sums;
        final LongAdder[][] counts;

        PersonSheetCtx(ReportJson.Sheet sheet, int nGroups, int nVars,
                       Predicate<Person>[] groupPreds,
                       LookupFormulaParser.ParseResult[] parsed,
                       Predicate<Attributables.AttributableProvider.TripAttributable>[] tripFilters,
                       boolean[] varIsTripLookup, String[][] requiredVars, boolean needsTrips) {
            this.sheet = sheet;
            this.nGroups = nGroups;
            this.nVars = nVars;
            this.groupPreds = groupPreds;
            this.parsed = parsed;
            this.tripFilters = tripFilters;
            this.varIsTripLookup = varIsTripLookup;
            this.requiredVars = requiredVars;
            this.needsTrips = needsTrips;
            this.sums = new DoubleAdder[nGroups][nVars];
            this.counts = new LongAdder[nGroups][nVars];
            for (int g = 0; g < nGroups; g++) {
                for (int v = 0; v < nVars; v++) {
                    this.sums[g][v] = new DoubleAdder();
                    this.counts[g][v] = new LongAdder();
                }
            }
        }
    }

    private static final class TripSheetCtx {
        final ReportJson.Sheet sheet;
        final int nGroups, nVars;
        final Predicate<Person>[] personPreds;
        final Predicate<Attributables.AttributableProvider.TripAttributable>[] tripPreds;
        final String[][] requiredVars;
        final DoubleAdder[][] sums;
        final LongAdder[][] counts;

        TripSheetCtx(ReportJson.Sheet sheet, int nGroups, int nVars,
                     Predicate<Person>[] personPreds,
                     Predicate<Attributables.AttributableProvider.TripAttributable>[] tripPreds,
                     String[][] requiredVars) {
            this.sheet = sheet;
            this.nGroups = nGroups;
            this.nVars = nVars;
            this.personPreds = personPreds;
            this.tripPreds = tripPreds;
            this.requiredVars = requiredVars;
            this.sums = new DoubleAdder[nGroups][nVars];
            this.counts = new LongAdder[nGroups][nVars];
            for (int g = 0; g < nGroups; g++) {
                for (int v = 0; v < nVars; v++) {
                    this.sums[g][v] = new DoubleAdder();
                    this.counts[g][v] = new LongAdder();
                }
            }
        }
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

        // --- Categorize sheets ---
        List<PersonSheetCtx> personCtxs = new ArrayList<>();
        List<TripSheetCtx> tripCtxs = new ArrayList<>();
        List<ReportJson.Sheet> zoneSheets = new ArrayList<>();
        List<ReportJson.Sheet> pivotSheets = new ArrayList<>();

        for (ReportJson.Sheet s : reportConfig.sheets) {
            if (s.pivots != null && !s.pivots.isEmpty()) {
                pivotSheets.add(s);
            } else if ("Persons".equals(s.table_ref)) {
                personCtxs.add(buildPersonCtx(s));
            } else if ("Trips".equals(s.table_ref)) {
                tripCtxs.add(buildTripCtx(s));
            } else if ("Zones".equals(s.table_ref)) {
                zoneSheets.add(s);
            }
        }

        boolean anyNeedsTrips = !tripCtxs.isEmpty();
        if (!anyNeedsTrips) {
            for (PersonSheetCtx c : personCtxs) {
                if (c.needsTrips) { anyNeedsTrips = true; break; }
            }
        }
        final boolean needsTripsFinal = anyNeedsTrips;

        // --- Single parallel pass over the regular population ---
        if (!personCtxs.isEmpty() || !tripCtxs.isEmpty()) {
            scenario.getPopulation().getPersons().values().parallelStream()
                    .filter(person -> "regular".equals(person.getAttributes().getAttribute("subpopulation")))
                    .forEach(person -> {
                        List<Attributables.AttributableProvider.TripAttributable> trips = null;
                        if (needsTripsFinal) {
                            trips = buildTripsForPerson(person);
                        }
                        for (PersonSheetCtx ctx : personCtxs) {
                            processPersonSheet(ctx, person, trips);
                        }
                        if (!tripCtxs.isEmpty() && trips != null && !trips.isEmpty()) {
                            for (TripSheetCtx ctx : tripCtxs) {
                                processTripsSheet(ctx, person, trips);
                            }
                        }
                    });
        }

        // --- Write outputs ---
        for (PersonSheetCtx ctx : personCtxs) writeAggregatedSheet(ctx.sheet, ctx.nGroups, ctx.nVars, ctx.sums, ctx.counts);
        for (TripSheetCtx ctx : tripCtxs) writeAggregatedSheet(ctx.sheet, ctx.nGroups, ctx.nVars, ctx.sums, ctx.counts);

        // Zones and pivots use the legacy code paths (they are not the dominant cost)
        for (ReportJson.Sheet s : zoneSheets) writeSheet(s);
        for (ReportJson.Sheet s : pivotSheets) writePivotSheet(s);

        scenario.getPopulation().getPersons().values().forEach(person -> {
            Set<String> attributesThatAreNull = person.getAttributes().getAsMap().entrySet().stream().filter(attr -> attr.getValue() == null).map(Map.Entry::getKey).collect(Collectors.toSet());
            attributesThatAreNull.forEach(attr -> person.getAttributes().removeAttribute(attr));
        });
    }

    @SuppressWarnings("unchecked")
    private PersonSheetCtx buildPersonCtx(ReportJson.Sheet s) {
        final int nGroups = s.groups.size();
        final int nVars = s.variables.size();
        // sanitize group filters once
        for (ReportJson.ReportGroup group : s.groups) {
            group.filter_expression = group.filter_expression.replace("[", "").replace("]", "");
        }
        Predicate<Person>[] groupPreds = new Predicate[nGroups];
        for (int g = 0; g < nGroups; g++) {
            groupPreds[g] = BuildPredicate.buildPredicate(s.groups.get(g).filter_expression, Person.class);
        }
        LookupFormulaParser.ParseResult[] parsed = new LookupFormulaParser.ParseResult[nVars];
        Predicate<Attributables.AttributableProvider.TripAttributable>[] tripFilters = new Predicate[nVars];
        boolean[] varIsTripLookup = new boolean[nVars];
        String[][] requiredVars = new String[nVars][];
        boolean needsTrips = false;
        for (int v = 0; v < nVars; v++) {
            ReportJson.ReportVariable variable = s.variables.get(v);
            LookupFormulaParser.ParseResult pr = parseFormula(variable.formula);
            parsed[v] = pr;
            varIsTripLookup[v] = "Trips".equals(pr.lookupTable);
            if (varIsTripLookup[v]) {
                needsTrips = true;
                if (pr.filterExpression != null) {
                    tripFilters[v] = BuildPredicate.buildPredicate(
                            pr.filterExpression, Attributables.AttributableProvider.TripAttributable.class);
                }
            }
            requiredVars[v] = expressionEvaluator.getShortVariableNames(variable.formula).toArray(new String[0]);
        }
        return new PersonSheetCtx(s, nGroups, nVars, groupPreds, parsed, tripFilters, varIsTripLookup, requiredVars, needsTrips);
    }

    @SuppressWarnings("unchecked")
    private TripSheetCtx buildTripCtx(ReportJson.Sheet s) {
        final int nGroups = s.groups.size();
        final int nVars = s.variables.size();
        for (ReportJson.ReportGroup group : s.groups) {
            group.filter_expression = group.filter_expression.replace("[", "").replace("]", "");
        }
        Predicate<Person>[] personPreds = new Predicate[nGroups];
        Predicate<Attributables.AttributableProvider.TripAttributable>[] tripPreds = new Predicate[nGroups];
        for (int g = 0; g < nGroups; g++) {
            ReportJson.ReportGroup group = s.groups.get(g);
            personPreds[g] = (group.filter_expression_person != null)
                    ? BuildPredicate.buildPredicate(group.filter_expression_person, Person.class)
                    : null;
            tripPreds[g] = BuildPredicate.buildPredicate(
                    group.filter_expression, Attributables.AttributableProvider.TripAttributable.class);
        }
        String[][] requiredVars = new String[nVars][];
        for (int v = 0; v < nVars; v++) {
            requiredVars[v] = expressionEvaluator.getShortVariableNames(s.variables.get(v).formula).toArray(new String[0]);
        }
        return new TripSheetCtx(s, nGroups, nVars, personPreds, tripPreds, requiredVars);
    }

    private List<Attributables.AttributableProvider.TripAttributable> buildTripsForPerson(Person person) {
        Plan plan = person.getSelectedPlan();
        if (plan == null) return Collections.emptyList();
        List<TripStructureUtils.Trip> rawTrips = TripStructureUtils.getTrips(plan);
        if (rawTrips.isEmpty()) return Collections.emptyList();
        List<Attributables.AttributableProvider.TripAttributable> trips = new ArrayList<>(rawTrips.size());
        for (TripStructureUtils.Trip trip : rawTrips) {
            Attributables.AttributableProvider.TripAttributable tripAtt =
                    new Attributables.AttributableProvider.TripAttributable(trip, person);
            tripAtt.addLookupAttributes(lookupAttributeHandler.getLookupAttributesForTrip(tripAtt));
            trips.add(tripAtt);
        }
        return trips;
    }

    private void processPersonSheet(PersonSheetCtx ctx,
                                    Person person,
                                    List<Attributables.AttributableProvider.TripAttributable> trips) {
        final int nGroups = ctx.nGroups;
        final int nVars = ctx.nVars;
        boolean[] inGroup = null;
        boolean any = false;
        for (int g = 0; g < nGroups; g++) {
            if (ctx.groupPreds[g].test(person)) {
                if (inGroup == null) inGroup = new boolean[nGroups];
                inGroup[g] = true;
                any = true;
            }
        }
        if (!any) return;

        for (int v = 0; v < nVars; v++) {
            ReportJson.ReportVariable variable = ctx.sheet.variables.get(v);
            if (ctx.varIsTripLookup[v]) {
                if (trips == null || trips.isEmpty()) continue;
                LookupFormulaParser.ParseResult pr = ctx.parsed[v];
                Predicate<Attributables.AttributableProvider.TripAttributable> tripFilter = ctx.tripFilters[v];
                String[] reqVars = ctx.requiredVars[v];
                final boolean isSum = "Sum".equals(pr.aggregator);
                final boolean isCount = "Count".equals(pr.aggregator);
                for (Attributables.AttributableProvider.TripAttributable tripAtt : trips) {
                    if (tripFilter != null && !tripFilter.test(tripAtt)) continue;
                    double valSum = 0.0;
                    if (isSum) {
                        valSum = expressionEvaluator.evaluate(pr.lookupExpression, reqVars, tripAtt);
                    } else if (isCount) {
                        valSum = 1.0;
                    }
                    for (int g = 0; g < nGroups; g++) {
                        if (inGroup[g]) {
                            if (isSum || isCount) ctx.sums[g][v].add(valSum);
                            ctx.counts[g][v].increment();
                        }
                    }
                }
            } else {
                String[] reqVars = ctx.requiredVars[v];
                double val = expressionEvaluator.evaluate(variable.formula, reqVars, person);
                for (int g = 0; g < nGroups; g++) {
                    if (inGroup[g]) {
                        ctx.sums[g][v].add(val);
                        ctx.counts[g][v].increment();
                    }
                }
            }
        }
    }

    private void processTripsSheet(TripSheetCtx ctx,
                                   Person person,
                                   List<Attributables.AttributableProvider.TripAttributable> trips) {
        final int nGroups = ctx.nGroups;
        final int nVars = ctx.nVars;
        // Determine which groups potentially match by person
        boolean[] personMatch = null;
        boolean any = false;
        for (int g = 0; g < nGroups; g++) {
            boolean ok = (ctx.personPreds[g] == null) || ctx.personPreds[g].test(person);
            if (ok) {
                if (personMatch == null) personMatch = new boolean[nGroups];
                personMatch[g] = true;
                any = true;
            }
        }
        if (!any) return;

        boolean[] tripMatches = new boolean[nGroups];
        for (Attributables.AttributableProvider.TripAttributable tripAtt : trips) {
            boolean anyTripMatch = false;
            for (int g = 0; g < nGroups; g++) {
                if (personMatch[g] && ctx.tripPreds[g].test(tripAtt)) {
                    tripMatches[g] = true;
                    anyTripMatch = true;
                } else {
                    tripMatches[g] = false;
                }
            }
            if (!anyTripMatch) continue;
            // Evaluate each variable ONCE per trip, then distribute to all matching groups.
            for (int v = 0; v < nVars; v++) {
                ReportJson.ReportVariable variable = ctx.sheet.variables.get(v);
                double val = expressionEvaluator.evaluate(variable.formula, ctx.requiredVars[v], tripAtt);
                for (int g = 0; g < nGroups; g++) {
                    if (tripMatches[g]) {
                        ctx.sums[g][v].add(val);
                        ctx.counts[g][v].increment();
                    }
                }
            }
        }
    }

    private void writeAggregatedSheet(ReportJson.Sheet s, int nGroups, int nVars,
                                      DoubleAdder[][] sums, LongAdder[][] counts) {
        List<String> variable_names = new ArrayList<>(nVars + 1);
        for (int v = 0; v < nVars; v++) variable_names.add(s.variables.get(v).name);
        String outputFile = config.data().outputBaseDir() + "/" + config.models().run_id() + "." + s.table_ref + "_" + s.name.replace(" ", "_") + ".csv";
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
                                total = switch (s.variables.get(vIndex).aggregation) {
                                    case "Sum" -> sums[groupIndex][vIndex].sum();
                                    case "Count" -> counts[groupIndex][vIndex].sum();
                                    case "Mean" -> sums[groupIndex][vIndex].sum() / counts[groupIndex][vIndex].sum();
                                    default -> total;
                                };
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
            log.error("Error writing CSV", e);
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

                            // Lazily build trips list (only if any variable needs it)
                            List<Attributables.AttributableProvider.TripAttributable> trips = null;
                            boolean needsTrips = false;
                            for (int v = 0; v < nVars; v++)
                                if (varIsTripLookup[v]) {
                                    needsTrips = true;
                                    break;
                                }
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
                                            // Preserve original semantics: counts incremented for every trip regardless of filter.
                                            for (int g = 0; g < nGroups; g++) {
                                                if (inGroup[g]) counts[g][v].increment();
                                            }
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
                @SuppressWarnings("unchecked") final Predicate<Zone>[] groupPreds = new Predicate[nGroups];
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
                final Predicate<Person>[] personPreds = new Predicate[nGroups];
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

        String outputFile = config.data().outputBaseDir() + "/" + config.models().run_id() + "." + s.name.replace(" ", "_") + ".csv";
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
                                total = switch (s.variables.get(vIndex).aggregation) {
                                    case "Sum" -> sums[groupIndex][vIndex].sum();
                                    case "Count" -> counts[groupIndex][vIndex].sum();
                                    case "Mean" -> sums[groupIndex][vIndex].sum() / counts[groupIndex][vIndex].sum();
                                    default -> total;
                                };
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
            log.error("Error writing CSV", e);
        }
    }

    public void writePivotSheet(ReportJson.Sheet s) {
        // ---- Pre-compute everything once (no per-person allocation of these) ----
        final String indexName = s.pivots.get("index_name").toString();
        final String columnFormula = s.pivots.get("column_formula").toString().replace("[", "").replace("]", "");
        final String indexFormula = s.pivots.get("index_formula").toString().replace("[", "").replace("]", "");
        final String attribute = s.pivots.get("attribute").toString().replace("[", "").replace("]", "");
        final String filterExpression = s.pivots.get("filter_expression").toString().replace("[", "").replace("]", "");
        final String aggregation = s.pivots.get("aggregation").toString();
        final boolean isSum = "Sum".equals(aggregation);

        final String[] indexVars = expressionEvaluator.getShortVariableNames(indexFormula).toArray(new String[0]);
        final String[] columnVars = expressionEvaluator.getShortVariableNames(columnFormula).toArray(new String[0]);
        final String[] attrVars = isSum ? expressionEvaluator.getShortVariableNames(attribute).toArray(new String[0]) : new String[0];

        // Concurrent accumulator: row -> col -> sum
        final ConcurrentHashMap<Double, ConcurrentHashMap<Double, DoubleAdder>> acc = new ConcurrentHashMap<>();

        switch (s.table_ref) {
            case "Persons": {
                final Predicate<Person> filterPredicate = BuildPredicate.buildPredicate(filterExpression, Person.class);
                scenario.getPopulation().getPersons().values().parallelStream()
                        .filter(person -> "regular".equals(person.getAttributes().getAttribute("subpopulation")))
                        .filter(filterPredicate::test)
                        .forEach(person -> {
                            double idx = expressionEvaluator.evaluate(indexFormula, collectAttributeStrings(person, indexVars));
                            double col = expressionEvaluator.evaluate(columnFormula, collectAttributeStrings(person, columnVars));
                            double val = isSum ? expressionEvaluator.evaluate(attribute, collectAttributeStrings(person, attrVars)) : 1.0;
                            acc.computeIfAbsent(idx, _ -> new ConcurrentHashMap<>())
                               .computeIfAbsent(col, _ -> new DoubleAdder())
                               .add(val);
                        });
                break;
            }
            case "Trips": {
                // Note: pivot for "Trips" historically iterates Persons (treating attributes as person attributes).
                // We keep that semantics but parallelize it.
                scenario.getPopulation().getPersons().values().parallelStream()
                        .filter(person -> "regular".equals(person.getAttributes().getAttribute("subpopulation")))
                        .forEach(person -> {
                            double idx = expressionEvaluator.evaluate(indexFormula, collectAttributeStrings(person, indexVars));
                            double col = expressionEvaluator.evaluate(columnFormula, collectAttributeStrings(person, columnVars));
                            double val = isSum ? expressionEvaluator.evaluate(attribute, collectAttributeStrings(person, attrVars)) : 1.0;
                            acc.computeIfAbsent(idx, k -> new ConcurrentHashMap<>())
                               .computeIfAbsent(col, k -> new DoubleAdder())
                               .add(val);
                        });
                break;
            }
            case "Zones": {
                final Predicate<Zone> filterPredicate = BuildPredicate.buildPredicate(filterExpression, Zone.class);
                zones.getZones().parallelStream()
                        .filter(filterPredicate::test)
                        .forEach(zone -> {
                            double idx = expressionEvaluator.evaluate(indexFormula, collectAttributeStrings(zone, indexVars));
                            double col = expressionEvaluator.evaluate(columnFormula, collectAttributeStrings(zone, columnVars));
                            double val = isSum ? expressionEvaluator.evaluate(attribute, collectAttributeStrings(zone, attrVars)) : 1.0;
                            acc.computeIfAbsent(idx, k -> new ConcurrentHashMap<>())
                               .computeIfAbsent(col, k -> new DoubleAdder())
                               .add(val);
                        });
                break;
            }
        }

        // Sort rows and columns into TreeMaps for stable, deterministic output
        Map<Double, Map<Double, Double>> pivot = new TreeMap<>();
        for (Map.Entry<Double, ConcurrentHashMap<Double, DoubleAdder>> rowEntry : acc.entrySet()) {
            TreeMap<Double, Double> row = new TreeMap<>();
            for (Map.Entry<Double, DoubleAdder> colEntry : rowEntry.getValue().entrySet()) {
                row.put(colEntry.getKey(), colEntry.getValue().sum());
            }
            pivot.put(rowEntry.getKey(), row);
        }

        String outputFile = config.data().outputBaseDir() + "/"+ config.models().run_id() + "." + s.name.replace(" ", "_") + ".csv";

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
            log.error("Error writing CSV", e);
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
        String plansFile = args[1];
        String zonesFile = args[2];
        String configFile = args[3];
        Scenario scenario = null;
        Zones zones = ZonesLoader.loadZones("zones", zonesFile);
        final Config matsimConfig = ConfigUtils.createConfig();
        scenario = ScenarioUtils.createScenario(matsimConfig);
        MOBiPlansConfig config = MOBiPlansConfig.readFromYaml(configFile);
        if (!"-".equals(experiencedPlansFile)) {
            new PopulationReader(scenario).readFile(experiencedPlansFile);
        } else {
            new PopulationReader(scenario).readFile(plansFile);
        }
        StatsWriter statsWriter = new StatsWriter(scenario, config, zones);
        statsWriter.checkPlans();
        statsWriter.run();
    }


    public boolean checkPlans() {
        List<String> requiredAttributes = List.of(
                "zone_id",
                "residence_zone_id",
                "active_in_model",
                "age",
                "current_edu",
                "current_job_rank",
                "is_working_from_home",
                "subpopulation"
        );
        Map<String, Boolean> result = new HashMap<>();

        for (Person person : scenario.getPopulation().getPersons().values()) {
            if (person.getAttributes().getAttribute("subpopulation") == null) {
                if (person.getAttributes().getAttribute("analysis_subpopulation")!=null){
                    person.getAttributes().putAttribute("subpopulation", person.getAttributes().getAttribute("analysis_subpopulation"));
                } else {
                    person.getAttributes().putAttribute("subpopulation", "regular");
                }
            }
            // Only the "regular" subpopulation participates in mobi-plans statistics. Skip everything
            // else (freight, cb_*, airport_*, ...) early to avoid NPEs (e.g. when destinations of
            // freight/cb trips are outside the zonal layer) and to save substantial runtime.
            if (!"regular".equals(person.getAttributes().getAttribute("subpopulation"))) {
                continue;
            }
            if (person.getAttributes().getAttribute("zone_id") == null) {
                if (person.getAttributes().getAttribute("residence_zone_id") != null) {
                    person.getAttributes().putAttribute("zone_id", person.getAttributes().getAttribute("residence_zone_id"));
                }
            }
            if (person.getAttributes().getAttribute("active_in_model") == null) {
                person.getAttributes().putAttribute("active_in_model", true);
            }
            if (person.getAttributes().getAttribute("age") == null) {
                person.getAttributes().putAttribute("age", 50);
                if ("65_to_74".equals(person.getAttributes().getAttribute("age_cat")) | "75_to_xx".equals(person.getAttributes().getAttribute("age_cat")))
                    person.getAttributes().putAttribute("age", 66);
            }
            if (person.getAttributes().getAttribute("current_job_rank") == null) {
                person.getAttributes().putAttribute("current_job_rank", 0);
                Plan plan = person.getSelectedPlan();
                if (plan != null) {
                    for (TripStructureUtils.Trip trip : TripStructureUtils.getTrips(plan)) {
                        if (trip.getDestinationActivity().getType().toLowerCase().contains("work")) {
                            person.getAttributes().putAttribute("current_job_rank", 1);
                            break;
                        }
                    }
                }
            }
            if (person.getAttributes().getAttribute("current_edu") == null) {
                person.getAttributes().putAttribute("current_edu", 0);
                Plan plan = person.getSelectedPlan();
                if (plan != null) {
                    for (TripStructureUtils.Trip trip : TripStructureUtils.getTrips(plan)) {
                        if (trip.getDestinationActivity().getType().toLowerCase().contains("education")) {
                            person.getAttributes().putAttribute("current_edu", 1);
                            break;
                        }
                    }
                }
            } else {
                if (person.getAttributes().getAttribute("current_edu") instanceof String) {
                    switch (person.getAttributes().getAttribute("current_edu").toString().toLowerCase()) {
                        case "pupil_primary": {
                            person.getAttributes().putAttribute("current_edu", 1);
                            break;
                        }
                        case "pupil_secondary": {
                            person.getAttributes().putAttribute("current_edu", 2);
                            break;
                        }
                        case "student": {
                            person.getAttributes().putAttribute("current_edu", 3);
                            break;
                        }
                        case "apprentice": {
                            person.getAttributes().putAttribute("current_edu", 4);
                            break;
                        }
                        default: {
                            person.getAttributes().putAttribute("current_edu", 0);
                        }

                    }
                }
            }

            for (String attribute : requiredAttributes) {
                if (!result.containsKey(attribute)) {
                    if (person.getAttributes().getAttribute(attribute) == null) {
                        LogManager.getLogger(StatsWriter.class)
                                .warn("Person {} is missing required attribute '{}'", person.getId(), attribute);
                        result.put(attribute, true);
                    }
                }
            }

            // only required for reports that use work-zone-based statistics
            if (person.getAttributes().getAttribute("current_job_rank") != null) {
                Object currentJobRank = person.getAttributes().getAttribute("current_job_rank");
                try {
                    double jobRank = Double.parseDouble(currentJobRank.toString());
                    if (!result.containsKey(SynpopConstants.WORK_ZONE_NO)) {
                        if (jobRank > 0 && person.getAttributes().getAttribute(SynpopConstants.WORK_ZONE_NO) == null) {
                            LogManager.getLogger(StatsWriter.class)
                                    .warn("Person {} is employed but missing required attribute '{}'", person.getId(), SynpopConstants.WORK_ZONE_NO);
                            result.put(SynpopConstants.WORK_ZONE_NO, true);
                        }
                    }
                } catch (NumberFormatException e) {
                    LogManager.getLogger(StatsWriter.class)
                            .warn("Person {} has non-numeric attribute 'current_job_rank': {}", person.getId(), currentJobRank);
                }
            }
        }
        return result.isEmpty();
    }

}
