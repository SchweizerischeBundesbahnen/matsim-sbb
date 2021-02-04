package ch.sbb.matsim.analysis.matrices;

import com.google.common.io.Files;
import java.io.File;
import java.io.IOException;
import org.junit.Ignore;
import org.junit.Test;
import org.locationtech.jts.util.Assert;

public class CalculateIndicatorMatricesTest {

    //FIXME: This test is failing with the current MATSim release, an update of matsim will fix this and is scheduled for April
    @Test
    @Ignore
    public void main() throws IOException {
        final String folder = "test/input/scenarios/mobi31test/";
        var coordinatesFile = folder + "zone_coordinates.csv";
        var network = folder + "network.xml.gz";
        var schedule = folder + "transitSchedule.xml.gz";
        var events = "-";
        final String testpath = "ch/sbb/matsim/analysis/matrices/CalculateIndicatorMatricesTest";
        var outputDirectory = "test/output/" + testpath;
        var inputDirectory = "test/input/" + testpath;
        var detectTrainLines = "true";
        var threads = "1";
        var calcString = "pt=;7:00:00;08:00:00";
        CalculateIndicatorMatrices.main(new String[]{coordinatesFile, network, schedule, events, outputDirectory, threads, detectTrainLines, calcString});
        Assert.equals(true, Files.equal(new File(inputDirectory + "/pt_traveltimes.csv.gz"), new File(outputDirectory + "/pt_traveltimes.csv.gz")));

    }
}