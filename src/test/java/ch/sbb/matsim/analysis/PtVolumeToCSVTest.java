package ch.sbb.matsim.analysis;

import ch.sbb.matsim.analysis.TestFixtures.PtTestFixture;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.matsim.core.utils.io.IOUtils;
import org.matsim.testcases.MatsimTestUtils;


import java.io.BufferedReader;
import java.io.IOException;

public class PtVolumeToCSVTest {

    @Rule
    public MatsimTestUtils utils = new MatsimTestUtils();

    // tests whether the files are written correctly for one iteration
    @Test
    public void test_writeResults() throws IOException {

        PtTestFixture testFixture = new PtTestFixture();
        PtVolumeToCSV ptVolumeToCSV = new PtVolumeToCSV(testFixture.scenario, this.utils.getOutputDirectory(), false);

        testFixture.eventsManager.addHandler(ptVolumeToCSV);
        testFixture.addSingleTransitDemand();
        testFixture.addEvents();

        ptVolumeToCSV.writeResults(true);

        Assert.assertEquals(expectedStops, readResult(this.utils.getOutputDirectory() + "matsim_stops.csv.gz"));
        Assert.assertEquals(expectedVehJourneys, readResult(this.utils.getOutputDirectory() + "matsim_vehjourneys.csv.gz"));
    }

    // tests whether a file is correctly written containing daily volumes for multiple iterations
    @Test
    public void test_writeLastIteration() throws IOException {

        PtTestFixture testFixture = new PtTestFixture();
        PtVolumeToCSV ptVolumeToCSV = new PtVolumeToCSV(testFixture.scenario, this.utils.getOutputDirectory(), true);

        testFixture.eventsManager.addHandler(ptVolumeToCSV);
        testFixture.addSingleTransitDemand();
        testFixture.addEvents();
        ptVolumeToCSV.reset(0);

        // increment iteration and write events again
        testFixture.addEvents();
        ptVolumeToCSV.reset(1);
        testFixture.addEvents();
        ptVolumeToCSV.reset(2);

        // write final daily volumes and check results
        ptVolumeToCSV.writeResults(true);
        Assert.assertEquals(expectedStopsDaily, readResult(this.utils.getOutputDirectory() + "matsim_stops_daily.csv.gz"));
    }

    private String readResult(String filePath) throws IOException {
        BufferedReader br = IOUtils.getBufferedReader(filePath);
        StringBuilder sb = new StringBuilder();
        String line = br.readLine();

        while (line != null) {
            sb.append(line);
            sb.append("\n");
            line = br.readLine();
        }

        return sb.toString();
    }

    static String expectedStops =
            "index;stop_id;boarding;alighting;line;lineroute;departure_id;vehicle_id;departure;arrival\n" +
            "0;A;0.0;0.0;S2016_1;A2E;1;train1;30000.0;30000.0\n" +
            "1;B;1.0;0.0;S2016_1;A2E;1;train1;30120.0;30100.0\n" +
            "2;C;0.0;0.0;S2016_1;A2E;1;train1;30300.0;30300.0\n" +
            "3;D;0.0;1.0;S2016_1;A2E;1;train1;30600.0;30570.0\n" +
            "4;E;0.0;0.0;S2016_1;A2E;1;train1;30720.0;30720.0\n";


    static String expectedVehJourneys =
            "index;from_stop_id;to_stop_id;passengers;line;lineroute;departure_id;vehicle_id;departure;arrival\n" +
            "1;A;B;0.0;S2016_1;A2E;1;train1;30000.0;30100.0\n" +
            "2;B;C;1.0;S2016_1;A2E;1;train1;30120.0;30300.0\n" +
            "3;C;D;1.0;S2016_1;A2E;1;train1;30300.0;30570.0\n" +
            "4;D;E;0.0;S2016_1;A2E;1;train1;30600.0;30720.0\n";

    static String expectedStopsDaily =
            "it;A;B;C;D;E\n" +
            "0;0;1;0;1;0\n" +
            "1;0;1;0;1;0\n" +
            "2;0;1;0;1;0\n";

}
