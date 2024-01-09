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
        var CAR1 = "car=am_;06:30:00;06:50:00;07:10:00;07:30:00";
        var CAR2 = "car=pm_;16:50:00;17:10:00;17:30:00;17:50:00";

        //OMX calculation does not work with CI
        //CalculateIndicatorOMXMatrices.main(new String[]{coordinatesFile, network, schedule, events, outputDirectory, threads, detectTrainLines, calcString});
        //CalculateIndicatorOMXMatrices.main(new String[]{coordinatesFile, "\\\\wsbbrz0283\\mobi\\50_Ergebnisse\\MOBi_2.2\\streets\\v2.2.3\\output\\network.xml.gz", schedule, events, outputDirectory, threads, "false", CAR1, CAR2});

        CalculateIndicatorMatrices.main(new String[]{coordinatesFile, network, schedule, events, outputDirectory, threads, detectTrainLines, calcString});

        Assert.equals(true, Files.equal(new File(inputDirectory + "/pt_traveltimes.csv.gz"), new File(outputDirectory + "/pt_travel_times.csv.gz")));

    }
}