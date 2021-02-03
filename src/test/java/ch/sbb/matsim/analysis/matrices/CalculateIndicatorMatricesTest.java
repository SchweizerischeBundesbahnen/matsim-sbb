package ch.sbb.matsim.analysis.matrices;

import java.io.IOException;
import org.junit.Test;

public class CalculateIndicatorMatricesTest {

    @Test
    public void main() throws IOException {
        final String folder = "test/input/scenarios/mobi31test/";
        var coordinatesFile = folder + "zone_coordinates.csv";
        var network = folder + "network.xml.gz";
        var schedule = folder + "transitSchedule.xml.gz";
        var events = "-";
        var outputDirectory = "test/output/ch/sbb/matsim/analysis/matrices/CalculateIndicatorMatricesTest";
        var detectTrainLines = "true";
        var threads = "1";
        var calcString = "pt=;7:00:00;08:00:00";
        CalculateIndicatorMatrices.main(new String[]{coordinatesFile, network, schedule, events, outputDirectory, threads, detectTrainLines, calcString});

    }
}