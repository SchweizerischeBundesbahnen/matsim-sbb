package ch.sbb.matsim.analysis.skims;

import com.google.common.io.Files;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.util.Assert;

import java.io.File;
import java.io.IOException;

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

        CalculateIndicatorOMXMatrices.main(new String[]{coordinatesFile, network, schedule, events, outputDirectory, threads, detectTrainLines, calcString});
        Assert.equals(true, Files.equal(new File(inputDirectory + "/pt_skims.omx"), new File(outputDirectory + "/pt_skims.omx")));

    }
}