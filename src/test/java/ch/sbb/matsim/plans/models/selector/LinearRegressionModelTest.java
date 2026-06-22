package ch.sbb.matsim.plans.models.selector;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.matsim.utils.objectattributes.attributable.Attributable;
import org.matsim.utils.objectattributes.attributable.Attributes;
import org.matsim.utils.objectattributes.attributable.AttributesImpl;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LinearRegressionModelTest {

    @TempDir
    Path tempDir;

    /**
     * A lightweight dummy object that implements Attributable.
     * This proves the model works independently of Persons, Scenarios, or Zones.
     */
    private static class DummyAttributable implements Attributable {
        private final Attributes attributes = new AttributesImpl();

        @Override
        public Attributes getAttributes() {
            return attributes;
        }
    }

    @Test
    void testCreateFromCSV_AndCalculate_Success() throws IOException {
        // 1. Create a valid CSV.
        // The column MUST be named beta_value to match ChoiceModelCSVReader.VALUE
        Path validCsv = tempDir.resolve("valid_regression.csv");
        String csvContent = """
                type;attribute;values;lambda;value
                constant;;;;10.5
                linear;test_attr;;;2.0
                condition;flag_attr;==1;;5.0
                """;
        Files.writeString(validCsv, csvContent);

        // 2. Setup the dummy attributable object
        DummyAttributable dummy = new DummyAttributable();
        dummy.getAttributes().putAttribute("test_attr", 4.0);
        dummy.getAttributes().putAttribute("flag_attr", 1);

        // 3. Load the model using the new, cleaner factory method
        LinearRegressionModel model = LinearRegressionModel.createFromCSV(validCsv.toString());

        // 4. Calculate and Verify
        // Expected: 10.5 (constant) + (4.0 * 2.0) (linear) + 5.0 (condition) = 23.5
        double result = model.calculate(dummy);

        assertEquals(23.5, result, 0.0001, "The linear regression should correctly sum all utility components.");
    }
}