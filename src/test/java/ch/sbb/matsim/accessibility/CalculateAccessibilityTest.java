package ch.sbb.matsim.accessibility;

import com.google.common.base.Charsets;
import com.google.common.io.Files;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class CalculateAccessibilityTest {

    private static final double EPS = 1e-6;

    @Test
    public void main() throws IOException {
        final String folder = "test/input/scenarios/mobi31test/";
        var coordinatesFile = folder + "zone_coordinates_grid.csv";
        var network = folder + "network.xml.gz";
        var schedule = folder + "transitSchedule.xml.gz";
        final String testpath = "ch/sbb/matsim/accessibility/CalculateAccessibilityTest";
        var outputDirectory = "test/output/" + testpath;
        var inputDirectory = "test/input/" + testpath;
        var events = inputDirectory + "/MOBI31.output_events.xml";
        var detectTrainLines = "true";
        var threads = "1";
        var calcString = "7:00:00;08:00:00";
        var car1 = "06:30:00;06:50:00;07:10:00;07:30:00";
        var car2 = "16:50:00;17:10:00;17:30:00;17:50:00";

        var gridSize = "10";

        var zones = folder + "zones/andermatt-zones.shp";
        var facilities = folder + "facilities.xml.gz";
        var population = inputDirectory + "/population.xml.gz";
        var personWeight = "100";

        var modes = "mm;car;pt";

        CalculateAccessibility.main(new String[]{
                coordinatesFile, zones, facilities, population, personWeight, network, schedule,
                network, events, outputDirectory, gridSize, threads, detectTrainLines, modes, calcString, car1, car2});

        File f1 = new File(inputDirectory + "/accessibility.csv");
        File f2 = new File(outputDirectory + "/accessibility.csv");
        compareCsvWithNumericTolerance(f1, f2, EPS);

        File f3 = new File(inputDirectory + "/attractions_10.csv");
        File f4 = new File(outputDirectory + "/attractions_10.csv");
        compareCsvWithNumericTolerance(f3, f4, EPS);
    }

    private void compareCsvWithNumericTolerance(File expected, File actual, double eps) throws IOException {
        List<String> eLines = Files.readLines(expected, Charsets.UTF_8);
        List<String> aLines = Files.readLines(actual, Charsets.UTF_8);

        assertEquals(eLines.size(), aLines.size(),
                () -> String.format("Different line counts for %s: expected=%d actual=%d",
                        expected.getName(), eLines.size(), aLines.size()));

        for (int i = 0; i < eLines.size(); i++) {
            String el = eLines.get(i).replace("\r", ""); // einfache EOL-Normalisierung
            String al = aLines.get(i).replace("\r", "");
            String[] eCols = el.split(",", -1);
            String[] aCols = al.split(",", -1);

            int finalI = i;
            assertEquals(eCols.length, aCols.length,
                    () -> String.format("Different column counts at line %d in %s: expected=%d actual=%d",
                            finalI + 1, expected.getName(), eCols.length, aCols.length));

            for (int c = 0; c < eCols.length; c++) {
                String es = eCols[c].trim();
                String as = aCols[c].trim();

                Double ev = tryParseDouble(es);
                Double av = tryParseDouble(as);

                if (ev != null && av != null) {
                    if (!almostEqual(ev, av, eps)) {
                        fail(String.format("Numeric mismatch in %s at line %d column %d: expected=%s actual=%s (diff=%g)",
                                expected.getName(), i + 1, c + 1, es, as, Math.abs(ev - av)));
                    }
                } else {
                    if (!es.equals(as)) {
                        fail(String.format("String mismatch in %s at line %d column %d: expected='%s' actual='%s'",
                                expected.getName(), i + 1, c + 1, es, as));
                    }
                }
            }
        }
    }

    private static Double tryParseDouble(String s) {
        if (s == null || s.isEmpty()) return null;
        try {
            return Double.parseDouble(s);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static boolean almostEqual(double a, double b, double eps) {
        if (Double.isNaN(a) || Double.isNaN(b)) return false;
        if (Double.isInfinite(a) || Double.isInfinite(b)) return a == b;
        double diff = Math.abs(a - b);
        if (diff <= eps) return true;
        double largest = Math.max(Math.abs(a), Math.abs(b));
        return diff <= largest * eps;
    }
}
