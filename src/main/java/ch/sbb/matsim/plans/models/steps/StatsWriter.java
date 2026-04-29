package ch.sbb.matsim.plans.models.steps;

import ch.sbb.matsim.csv.CSVWriter;
import ch.sbb.matsim.plans.MOBiPlansRunner;
import ch.sbb.matsim.plans.models.config.MOBiPlansConfig;
import ch.sbb.matsim.plans.models.utils.BuildPredicate;
import ch.sbb.matsim.plans.models.utils.ExpressionEvaluator;
import ch.sbb.matsim.plans.stats.LookupAttributeHandler;
import ch.sbb.matsim.plans.stats.LookupFormulaParser;
import ch.sbb.matsim.plans.stats.ReadReportJson;
import ch.sbb.matsim.zones.Zone;
import ch.sbb.matsim.zones.Zones;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.inject.Inject;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.Route;
import org.matsim.core.router.TripStructureUtils;
import org.matsim.utils.objectattributes.attributable.Attributable;
import org.matsim.utils.objectattributes.attributable.Attributes;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.function.Predicate;

import static ch.sbb.matsim.plans.stats.LookupFormulaParser.parseFormula;

public class StatsWriter implements ModelStep {
    @Inject
    Scenario scenario;
    @Inject
    MOBiPlansConfig config;
    @Inject
    Zones zones;

    @Override
    public String getName() {
        return MOBiPlansRunner.WRITE_STATS;
    }

    @Override
    public void run() {
        Path reportsFolder = Paths.get(config.data().modelBaseDir()).resolve(MOBiPlansConfig.MODEL_BASE_FOLDER_REPORTS);
        String reportConfigFilePath = reportsFolder.resolve(MOBiPlansConfig.REPORT_CONFIG_JSON).toString();

        File jsonFile = new File(reportConfigFilePath);
        ReadReportJson.ReportConfig reportConfig = null;
        ObjectMapper mapper = new ObjectMapper();
        try {
            reportConfig = mapper.readValue(jsonFile, ReadReportJson.ReportConfig.class);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        ExpressionEvaluator expressionEvaluator = new ExpressionEvaluator();
        LookupFormulaParser lookupFormulaParser = new LookupFormulaParser();
        LookupAttributeHandler lookupAttributeHandler = new LookupAttributeHandler();
        lookupAttributeHandler.createLookUpVariables(reportConfig);
        Collection<? extends Person> a = scenario.getPopulation().getPersons().values();
        lookupAttributeHandler.setLookupAttributes(scenario.getPopulation().getPersons().values(), zones.getZones());

        for (ReadReportJson.Sheet s : reportConfig.sheets) {
            Map<String, List<String>> requiredVariables = new HashMap<>();
            List<String> variable_names = new ArrayList<>();
            for (ReadReportJson.ReportVariable variable : s.variables) {
                requiredVariables.put(variable.name, expressionEvaluator.getShortVariableNames(variable.formula));
                variable_names.add(variable.name);
            }

            double[][] sums;
            int[][] counts;
            sums = new double[s.groups.size()][s.variables.size()];
            counts = new int[s.groups.size()][s.variables.size()];
            for (int groupIndex = 0; groupIndex < s.groups.size(); groupIndex++) {
                ReadReportJson.ReportGroup group = s.groups.get(groupIndex);
                switch (s.table_ref) {
                    case "Persons": {
                        String filterExpression = group.filter_expression;
                        Predicate<Person> groupFilterPredicate = BuildPredicate.buildPredicate(filterExpression, Person.class);
                        int finalGroupIndex1 = groupIndex;
                        scenario.getPopulation().getPersons().values().stream().forEach(person -> {
                            if (groupFilterPredicate.test(person)) {
                                for (int varIndex = 0; varIndex < s.variables.size(); varIndex++) {
                                    ReadReportJson.ReportVariable variable = s.variables.get(varIndex);
                                    LookupFormulaParser.ParseResult formulaParser = parseFormula(variable.formula);
                                    if (formulaParser.aggregator != null){
                                        for (TripStructureUtils.Trip trip : TripStructureUtils.getTrips(person.getSelectedPlan())) {
                                            TripAttributable tripAtt= new TripAttributable(trip);
                                            tripAtt.addLookupAttributes(lookupAttributeHandler.getLookupAttributesForTrip(tripAtt));
                                            boolean tripFilter = true;
                                            if (formulaParser.filterExpression != null){
                                                Predicate<TripAttributable> groupFilterPredicateTrip = BuildPredicate.buildPredicate(formulaParser.filterExpression, TripAttributable.class);
                                                tripFilter =  groupFilterPredicateTrip.test(tripAtt);
                                            }
                                            if (tripFilter) {
                                                switch (formulaParser.aggregator) {
                                                    case "Sum": {
                                                        sums[finalGroupIndex1][varIndex] += expressionEvaluator.evaluate(variable.formula, collectAttributeStrings(tripAtt, requiredVariables.get(variable.name), TripAttributable.class));
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
                        List<Zone> z = zones.getZones();
                        zones.getZones().stream().forEach(zone -> {
                            if (groupFilterPredicate.test(zone)) {
                                for (int varIndex = 0; varIndex < s.variables.size(); varIndex++) {
                                    ReadReportJson.ReportVariable variable = s.variables.get(varIndex);
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
                        Predicate<TripAttributable> groupFilterPredicateTrip = BuildPredicate.buildPredicate(filterExpression, TripAttributable.class);
                        Predicate<Person> groupFilterPredicatePerson = BuildPredicate.buildPredicate(filterExpressionPerson, Person.class);
                        int finalGroupIndex1 = groupIndex;
                        scenario.getPopulation().getPersons().values().stream().forEach(person -> {
                            if (groupFilterPredicatePerson.test(person)) {
                                for (TripStructureUtils.Trip trip : TripStructureUtils.getTrips(person.getSelectedPlan())) {
                                    TripAttributable tripAtt= new TripAttributable(trip);
                                    tripAtt.addLookupAttributes(lookupAttributeHandler.getLookupAttributesForTrip(tripAtt));
                                    if (groupFilterPredicateTrip.test(tripAtt)) {
                                        for (int varIndex = 0; varIndex < s.variables.size(); varIndex++) {
                                            ReadReportJson.ReportVariable variable = s.variables.get(varIndex);
                                            sums[finalGroupIndex1][varIndex] += expressionEvaluator.evaluate(variable.formula, collectAttributeStrings(tripAtt, requiredVariables.get(variable.name), TripAttributable.class));
                                            counts[finalGroupIndex1][varIndex]++;
                                        }
                                    }
                                }
                            }
                        });
                        break;
                    }
                }
            }

            String outputFile = config.data().outputBaseDir()+config.models().run_id()+"."+s.name.replace(" ", "_")+".csv";
            variable_names.addFirst("Name");
            try (CSVWriter csvWriter = new CSVWriter("", variable_names.toArray(String[]::new), outputFile )) {
                for (int groupIndex = 0; groupIndex < s.groups.size(); groupIndex++) {
                    ReadReportJson.ReportGroup group = s.groups.get(groupIndex);
                    csvWriter.set("Name", group.name);
                    for (int varIndex = 0; varIndex < s.variables.size(); varIndex++) {
                        ReadReportJson.ReportVariable variable = s.variables.get(varIndex);
                        Double val = 0.0;
                        switch (variable.aggregation) {
                            case "Sum":
                                val = sums[groupIndex][varIndex];
                                break;
                            case "Count":
                                val = Double.valueOf(String.valueOf(counts[groupIndex][varIndex]));
                                break;
                            case "Mean": {
                                if (counts[groupIndex][varIndex] != 0) val = sums[groupIndex][varIndex]/counts[groupIndex][varIndex];
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
            String hasPivots = (s.pivots != null && !s.pivots.isEmpty()) ? "yes" : "no";
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
                Double value = Double.valueOf(att_value.toString());
                result.put(variable, value);
            }
        }
        return result;
    }

    class TripAttributable implements Attributable {
        TripAttributes tripAttributes;

        public TripAttributable(TripStructureUtils.Trip trip) {
            this.tripAttributes = new TripAttributes(trip);
        }

        public void addLookupAttributes(Map<String, Object> lookupAttributes) {
            for (String attr: lookupAttributes.keySet()) {
                this.tripAttributes.putAttribute(attr, lookupAttributes.get(attr));
            }
        }

        @Override
        public Attributes getAttributes() {
            return tripAttributes;
        }
    }

    class TripAttributes implements Attributes {
        TripStructureUtils.Trip trip;
        Map<String, Object> attributes;

        TripAttributes(TripStructureUtils.Trip trip) {
            attributes = new HashMap<>();
            attributes.put("originActivity", trip.getOriginActivity());
            attributes.put("destinationActivity", trip.getDestinationActivity());
            attributes.put("originActivity/Zone", zones.findZone(trip.getOriginActivity().getCoord()));
            attributes.put("destinationActivity/Zone", zones.findZone(trip.getDestinationActivity().getCoord()));
            attributes.put("distance", trip.getLegsOnly().stream().mapToDouble(leg -> {
                Route r = leg.getRoute();
                Double d = null;
                if (r != null) {
                    d = r.getDistance();
                }
                return d == null ? 0.0 : d;
            }).sum());
            attributes.put("rail_distance", trip.getLegsOnly().stream().filter(leg -> leg.getMode().equals("rail")).mapToDouble(leg -> {
                Route r = leg.getRoute();
                Double d = null;
                if (r != null) {
                    d = r.getDistance();
                }
                return d == null ? 0.0 : d;
            }).sum());
        }

        @Override
        public Object putAttribute(String s, Object o) {
            attributes.put(s, o);
            return null;
        }

        @Override
        public Object getAttribute(String s) {
            return attributes.get(s);
        }

        @Override
        public Object removeAttribute(String s) {
            return null;
        }

        @Override
        public void clear() {
            attributes = new HashMap<>();
        }

        @Override
        public Map<String, Object> getAsMap() {
            return attributes;
        }

        @Override
        public int size() {
            return attributes.size();
        }

        @Override
        public boolean isEmpty() {
            return attributes.isEmpty();
        }
    }
}

