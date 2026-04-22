package ch.sbb.matsim.plans.models.steps;

import ch.sbb.matsim.plans.MOBiPlansRunner;
import ch.sbb.matsim.plans.models.config.MOBiPlansConfig;
import ch.sbb.matsim.plans.models.utils.BuildPredicate;
import ch.sbb.matsim.plans.models.utils.ExpressionEvaluator;
import ch.sbb.matsim.plans.stats.ReadReportJson;
import ch.sbb.matsim.zones.Zones;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.inject.Inject;
import org.apache.poi.ss.formula.functions.T;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.Population;
import org.matsim.utils.objectattributes.attributable.Attributable;
import org.matsim.utils.objectattributes.attributable.Attributes;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

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

        for (ReadReportJson.Sheet s : reportConfig.sheets) {
            Map<String, Set<String>> requiredVariables = new HashMap<>();
            for (ReadReportJson.ReportVariable variable: s.variables) {
                requiredVariables.put(variable.name, (Set<String>) expressionEvaluator.getRequiredVariables(variable.formula).values());
            }

            Map<String, Map<String, Double>> values = new HashMap<>();
            for (ReadReportJson.ReportGroup group: s.groups) {
                values.put(group.name, new HashMap<>());
                for (ReadReportJson.ReportVariable variable: s.variables) {
                    values.get(group.name).put(variable.name, 0.0);
                }
            }

            if (s.table_ref.equals("Persons")) {
                for (ReadReportJson.ReportGroup group: s.groups) {
                    Map<String, Double> groupValues = values.get(group.name);
                    switch (s.table_ref) {
                        case "Persons": {
                            Predicate<Person> groupFilterPredicate = BuildPredicate.buildPredicate(group.filter_expression, Person.class);
                            scenario.getPopulation().getPersons().values().parallelStream().forEach(person -> {
                                for (ReadReportJson.ReportVariable variable: s.variables) {
                                    groupValues.put(variable.name, groupValues.get(variable.name) + expressionEvaluator.evaluate(variable.formula, collectAttributeStrings(person, expressionEvaluator.getRequiredVariables(variable.formula).values())));
                                }
                            });
                        }
                        case "Zones": {

                        }
                    }
                }
            }
            int groups = s.groups == null ? 0 : s.groups.size();
            int vars = s.variables == null ? 0 : s.variables.size();
            String hasPivots = (s.pivots != null && !s.pivots.isEmpty()) ? "yes" : "no";
            // System.out.printf(fmtRow, s.name, safe(s.table_ref), groups, vars, hasPivots);
        }

    }

    private static Map<String, Double> collectAttributeStrings(Person person, Iterable<String> variables) {
        Map<String, Double> result = new HashMap<>();
        if (person == null || variables == null) return result;

        Attributes attrs = person.getAttributes();
        if (attrs == null) return result;

        for (String variable : variables) {
            if (variable == null) continue;
            Object value = attrs.getAttribute(variable);
            if (value != null) result.put(variable, (Double) value);
        }
        return result;
    }
}
