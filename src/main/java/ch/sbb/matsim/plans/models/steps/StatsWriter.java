package ch.sbb.matsim.plans.models.steps;

import ch.sbb.matsim.csv.CSVWriter;
import ch.sbb.matsim.plans.MOBiPlansRunner;
import ch.sbb.matsim.plans.models.config.MOBiPlansConfig;
import ch.sbb.matsim.plans.models.utils.BuildPredicate;
import ch.sbb.matsim.plans.models.utils.ExpressionEvaluator;
import ch.sbb.matsim.plans.stats.ReadReportJson;
import ch.sbb.matsim.zones.Zone;
import ch.sbb.matsim.zones.Zones;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.inject.Inject;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.population.Person;
import org.matsim.utils.objectattributes.attributable.Attributable;
import org.matsim.utils.objectattributes.attributable.Attributes;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
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
            Map<String, List<String>> requiredVariables = new HashMap<>();
            List<String> variable_names = new ArrayList<>();
            for (ReadReportJson.ReportVariable variable : s.variables) {
                requiredVariables.put(variable.name, expressionEvaluator.getShortVariableNames(variable.formula));
                variable_names.add(variable.name);
            }

            Double[][] sums;
            Integer[][] counts;
            sums = new Double[s.groups.size()][s.variables.size()];
            counts = new Integer[s.groups.size()][s.variables.size()];
            for (int groupIndex = 0; groupIndex < s.groups.size(); groupIndex++) {
                ReadReportJson.ReportGroup group = s.groups.get(groupIndex);
                switch (s.table_ref) {
                    case "Persons": {
                        Predicate<Person> groupFilterPredicate = BuildPredicate.buildPredicate(group.filter_expression, Person.class);
                        int finalGroupIndex1 = groupIndex;
                        scenario.getPopulation().getPersons().values().stream().forEach(person -> {
                            if (groupFilterPredicate.test(person)) {
                                for (int varIndex = 0; varIndex < s.variables.size(); varIndex++) {
                                    ReadReportJson.ReportVariable variable = s.variables.get(varIndex);
                                    sums[finalGroupIndex1][varIndex] += expressionEvaluator.evaluate(variable.formula, collectAttributeStrings(person, requiredVariables.get(variable.name)));
                                    counts[finalGroupIndex1][varIndex]++;
                                }
                            }
                        });
                    }
                    case "Zones": {
                        Predicate<Zone> groupFilterPredicate = BuildPredicate.buildPredicate(group.filter_expression, Zone.class);
                        int finalGroupIndex = groupIndex;
                        zones.getZones().stream().forEach(zone -> {
                            if (groupFilterPredicate.test(zone)) {
                                for (int varIndex = 0; varIndex < s.variables.size(); varIndex++) {
                                    ReadReportJson.ReportVariable variable = s.variables.get(varIndex);
                                    sums[finalGroupIndex][varIndex] += expressionEvaluator.evaluate(variable.formula, collectAttributeStrings(zone, requiredVariables.get(variable.name)));
                                    counts[finalGroupIndex][varIndex]++;
                                }
                            }
                        });
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
                        switch (variable.aggregation) {
                            case "Sum": csvWriter.set(variable.name, String.valueOf(sums[groupIndex][varIndex]));
                            case "Count": csvWriter.set(variable.name, String.valueOf(counts[groupIndex][varIndex]));
                            case "Mean": {
                                if (counts[groupIndex][varIndex] != 0) csvWriter.set(variable.name, String.valueOf((Double) sums[groupIndex][varIndex]/counts[groupIndex][varIndex]));
                            }
                        }
                        varIndex++;
                    }
                    csvWriter.writeRow();
                    groupIndex++;
                }
            } catch (Exception e) {
                //noinspection CallToPrintStackTrace
                e.printStackTrace();
            }
            String hasPivots = (s.pivots != null && !s.pivots.isEmpty()) ? "yes" : "no";
        }
    }

    private static Map<String, Double> collectAttributeStrings(Attributable obj, Iterable<String> variables) {
        Map<String, Double> result = new HashMap<>();
        if (obj == null || variables == null) return result;

        Attributes attrs = obj.getAttributes();
        if (attrs == null) return result;

        for (String variable : variables) {
            if (variable == null) continue;
            Object att_value = attrs.getAttribute(variable);
            if (att_value == null) continue;
            Double value = Double.valueOf(att_value.toString());
            if (value != null) result.put(variable, value);
        }
        return result;
    }
}
