package ch.sbb.matsim.accessibility;

import com.google.common.base.Charsets;
import com.google.common.io.Files;
import java.io.File;
import java.io.IOException;
import org.junit.Test;
import org.locationtech.jts.util.Assert;

public class CalculateAccessibilityTest {


    @Test
    public void main() throws IOException {
        final String folder = "test/input/scenarios/mobi31test/";
        var coordinatesFile = folder + "zone_coordinates_grid.csv";
        var network = folder + "network.xml.gz";
        var schedule = folder + "transitSchedule.xml.gz";
        final String testpath = "ch/sbb/matsim/accessibility/CalculateAccessibilityTest";
        var outputDirectory = "test/output/" + testpath;
        var inputDirectory = "test/input/" + testpath;
        var events = folder + "MOBI31.output_events.xml";
        var detectTrainLines = "true";
        var threads = "1";
        var calcString = "7:00:00;08:00:00";
        var car1 = "06:30:00;06:50:00;07:10:00;07:30:00";
        var car2 = "16:50:00;17:10:00;17:30:00;17:50:00";

        var gridSize = "10";

        var zones = folder + "zones/andermatt-zones.shp";
        var facilities = folder + "facilities.xml.gz";
        var population = folder + "population.xml.gz";
        var personWeight = "100";

        var modes = "mm;car;pt";

        CalculateAccessibility.main(new String[]{
                coordinatesFile, zones, facilities, population, personWeight, network, schedule,
                network, events, outputDirectory, gridSize, threads, detectTrainLines, modes, calcString, car1, car2});
        File f1 = new File(inputDirectory + "/accessibility.csv");
        File f2 = new File(outputDirectory + "/accessibility.csv");
        Assert.equals(true, Files.readLines(f1, Charsets.UTF_8).equals(Files.readLines(f2, Charsets.UTF_8)));
        File f3 = new File(inputDirectory + "/attractions_10.csv");
        File f4 = new File(outputDirectory + "/attractions_10.csv");
        Assert.equals(true, Files.readLines(f3, Charsets.UTF_8).equals(Files.readLines(f4, Charsets.UTF_8)));
    }
}