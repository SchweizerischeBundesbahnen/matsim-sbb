package ch.sbb.matsim.plans.models.utils;

import org.junit.jupiter.api.Test;

import java.io.FileWriter;
import java.io.IOException;
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

        Map<String, Map<String, Double>> result = ChoiceModelCSVReader.readShadowUtilitiesFromCSV(tempFile);

        assertTrue(result.containsKey("A"));
        assertTrue(result.containsKey("B"));
        assertEquals(1.5, result.get("A").get("agg1"));
        assertEquals(2.5, result.get("B").get("agg1"));
        assertEquals(3.0, result.get("A").get("agg2"));
        assertEquals(4.0, result.get("B").get("agg2"));
    }
}