package ch.sbb.matsim.plans.models.utils;

import ch.sbb.matsim.plans.models.utilitycomponents.*;
import org.junit.jupiter.api.Test;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.population.Person;
import org.matsim.core.population.PopulationUtils;

import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChoiceModelCSVReaderTest {

    @Test
    void readShadowUtilitiesFromCSVReadsCorrectValues() throws IOException {
        String tempFile = "test_shadow_utilities.csv";
        try (FileWriter writer = new FileWriter(tempFile)) {
            writer.write("aggregate_id;beta_A;beta_B\n");
            writer.write("agg1;1.5;2.5\n");
            writer.write("agg2;3.0;4.0\n");
        }

        try {
            Map<String, Map<String, Double>> result = ChoiceModelCSVReader.readShadowUtilitiesFromCSV(tempFile);

            assertTrue(result.containsKey("A"));
            assertTrue(result.containsKey("B"));
            assertEquals(1.5, result.get("A").get("agg1"));
            assertEquals(2.5, result.get("B").get("agg1"));
            assertEquals(3.0, result.get("A").get("agg2"));
            assertEquals(4.0, result.get("B").get("agg2"));
        } finally {
            Files.deleteIfExists(Path.of(tempFile));
        }
    }

    @Test
    void testReadChoiceModelFromCSV() throws IOException {
        // Beispiel CSV-Inhalt
        String csvContent = """
                type;attribute;values;lambda;beta_GA;beta_HTA
                constant;;;;-8.0;-7.0
                linear;is_swiss;;;1.1;0.9
                condition;language;==2;;0.5;0.4
                boxcox;zone.acc_pt_sc;;0.2;-0.3;-0.25
                piecewise;age;22,26,69,89;;0.0,-0.1,-0.2,0.3,0.1;0.1,0.0,-0.05,-0.1,0.2
                """;

        // Temporäre Datei schreiben
        Path tempFile = Files.createTempFile("utility_model_test", ".csv");
        try (FileWriter writer = new FileWriter(tempFile.toFile())) {
            writer.write(csvContent);
        }

        // Methode testen
        Map<String, UtilityCalculator> result = ChoiceModelCSVReader.readChoiceModelFromCSV(tempFile.toString());

        // Ergebnisse prüfen
        assertEquals(2, result.size());
        assertTrue(result.containsKey("GA"));
        assertTrue(result.containsKey("HTA"));

        UtilityCalculator ga = result.get("GA");
        assertEquals(5, ga.getComponents().size());

        assertTrue(ga.getComponents().stream().anyMatch(c -> c instanceof ConstantComponent));
        assertTrue(ga.getComponents().stream().anyMatch(c -> c instanceof LinearComponent));
        assertTrue(ga.getComponents().stream().anyMatch(c -> c instanceof PredicateComponent));
        assertTrue(ga.getComponents().stream().anyMatch(c -> c instanceof BoxCoxComponent));
        assertTrue(ga.getComponents().stream().anyMatch(c -> c instanceof PiecewiseComponent));
    }

    @Test
    void testReadRegressionModelWithScaledAttributeExpression() throws IOException {
        String csvContent = """
                type;attribute;values;lambda;value
                linear;demand;/1000000.0;;2.0
                boxcox;;demand*0.000001;0;3.0
                """;

        Path tempFile = Files.createTempFile("utility_model_scale_test", ".csv");
        try (FileWriter writer = new FileWriter(tempFile.toFile())) {
            writer.write(csvContent);
        }

        UtilityCalculator model = ChoiceModelCSVReader.readRegressionModelFromCSV(tempFile.toString());
        Person person = PopulationUtils.getFactory().createPerson(Id.createPersonId("test"));
        person.getAttributes().putAttribute("demand", 2_000_000.0);

        double result = model.calculate(person);
        double expected = 2.0 * 2.0 + (Math.log(2.0) * 3.0);
        assertEquals(expected, result, 1e-9);

        Files.deleteIfExists(tempFile);
    }

    @Test
    void testConditionOrSyntaxWithPipe() throws IOException {
        String csvContent = """
                type;attribute;values;lambda;value
                condition;pt_subscr;==GA|==VA|==HTV;;1.5
                """;

        Path tempFile = Files.createTempFile("utility_model_condition_or_test", ".csv");
        try (FileWriter writer = new FileWriter(tempFile.toFile())) {
            writer.write(csvContent);
        }

        UtilityCalculator model = ChoiceModelCSVReader.readRegressionModelFromCSV(tempFile.toString());

        Person gaPerson = PopulationUtils.getFactory().createPerson(Id.createPersonId("ga"));
        gaPerson.getAttributes().putAttribute("pt_subscr", "GA");
        assertEquals(1.5, model.calculate(gaPerson), 1e-9);

        Person vaPerson = PopulationUtils.getFactory().createPerson(Id.createPersonId("va"));
        vaPerson.getAttributes().putAttribute("pt_subscr", "VA");
        assertEquals(1.5, model.calculate(vaPerson), 1e-9);

        Person nonePerson = PopulationUtils.getFactory().createPerson(Id.createPersonId("none"));
        nonePerson.getAttributes().putAttribute("pt_subscr", "none");
        assertEquals(0.0, model.calculate(nonePerson), 1e-9);

        Files.deleteIfExists(tempFile);
    }

    @Test
    void testScaledAttributeExpressionInAttributeColumn() throws IOException {
        String csvContent = """
                type;attribute;values;lambda;value
                boxcox;demand/1000000;;1;3.0
                """;

        Path tempFile = Files.createTempFile("utility_model_attribute_scale_test", ".csv");
        try (FileWriter writer = new FileWriter(tempFile.toFile())) {
            writer.write(csvContent);
        }

        UtilityCalculator model = ChoiceModelCSVReader.readRegressionModelFromCSV(tempFile.toString());
        Person person = PopulationUtils.getFactory().createPerson(Id.createPersonId("attribute-scale"));
        person.getAttributes().putAttribute("demand", 2_000_000.0);

        double result = model.calculate(person);
        double expected = ((Math.pow(2.0, 1.0) - 1.0) / 1.0) * 3.0;
        assertEquals(expected, result, 1e-9);

        Files.deleteIfExists(tempFile);
    }

}