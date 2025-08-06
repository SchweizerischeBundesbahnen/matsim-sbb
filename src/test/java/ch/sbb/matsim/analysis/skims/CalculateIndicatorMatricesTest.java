package ch.sbb.matsim.analysis.skims;

import ch.sbb.matsim.utils.matrix.OmxUtils;
import ch.sbb.matsim.zones.Zone;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.matsim.api.core.v01.Id;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.Set;

public class CalculateIndicatorMatricesTest {

    @Test
    public void main() throws IOException {
        final String folder = "test/input/scenarios/mobi31test/";
        var coordinatesFile = folder + "zone_coordinates.csv";
        var network = folder + "network.xml.gz";
        var schedule = folder + "transitSchedule.xml.gz";
        final String testpath = "ch/sbb/matsim/analysis/matrices/CalculateIndicatorMatricesTest";
        var outputDirectory = "test/output/" + testpath;
        var inputDirectory = "test/input/" + testpath;
        var events = folder + "MOBI31.output_events.xml";
        var detectTrainLines = "true";
        var threads = "1";
        var calcString = "pt=;7:00:00;08:00:00";
        var outputFile = outputDirectory + "/pt_skims.omx";
        var expectedFile = inputDirectory + "/pt_skims.omx";

        CalculateIndicatorOMXMatrices.main(new String[]{coordinatesFile, network, schedule, events, outputDirectory, threads, detectTrainLines, calcString});

        File writtenFile = new File(outputFile);
        Assertions.assertTrue(writtenFile.exists(), "Output OMX file should exist at: " + outputFile);

        Map<String, FloatMatrix<Id<Zone>>> writtenMatrices = OmxUtils.readOmxMatrices(outputFile);
        Map<String, FloatMatrix<Id<Zone>>> existingMatrices = OmxUtils.readOmxMatrices(expectedFile);
        Assertions.assertEquals(existingMatrices.keySet(), writtenMatrices.keySet(), "The set of matrix names should be identical.");

        for (String matrixName : existingMatrices.keySet()) {
            FloatMatrix<Id<Zone>> writtenMatrix = writtenMatrices.get(matrixName);
            FloatMatrix<Id<Zone>> existingMatrix = existingMatrices.get(matrixName);

            Assertions.assertNotNull(writtenMatrix, "Written matrix '" + matrixName + "' should not be null.");
            Assertions.assertNotNull(existingMatrix, "Existing matrix '" + matrixName + "' should not be null.");

            // 4. Assert that the identifiers (the zones) for each matrix are identical.
            Set<Id<Zone>> existingIdentifiers = existingMatrix.getIdentifiers();
            Set<Id<Zone>> writtenIdentifiers = writtenMatrix.getIdentifiers();
            Assertions.assertEquals(existingIdentifiers, writtenIdentifiers, "Identifiers for matrix '" + matrixName + "' must match.");
            final float delta = 1e-6f;
            // 5. Assert that every cell value is the same within the given tolerance.
            for (Id<Zone> fromZone : existingIdentifiers) {
                for (Id<Zone> toZone : existingIdentifiers) {
                    float existingValue = existingMatrix.get(fromZone, toZone);
                    float writtenValue = writtenMatrix.get(fromZone, toZone);
                    Assertions.assertEquals(existingValue, writtenValue, delta,
                            "Value mismatch in matrix '" + matrixName + "' for cell (" + fromZone + " -> " + toZone + ")");
                }
            }
            System.out.println("Successfully validated matrix: " + matrixName);
        }

    }
}