package ch.sbb.matsim.analysis;

import ch.sbb.matsim.analysis.TestFixtures.LinkTestFixture;
import java.io.BufferedReader;
import java.io.IOException;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.Link;
import org.matsim.core.utils.io.IOUtils;
import org.matsim.counts.Count;
import org.matsim.counts.Counts;
import org.matsim.testcases.MatsimTestUtils;

public class LinkVolumeToCSVTest {

    @Rule
    public MatsimTestUtils utils = new MatsimTestUtils();

    @Test
    public void test_allLinks() throws IOException {

        LinkTestFixture testFixture = new LinkTestFixture();
        LinkVolumeToCSV linkVolumeToCSV = new LinkVolumeToCSV(testFixture.scenario, this.utils.getOutputDirectory());

        testFixture.eventsManager.addHandler(linkVolumeToCSV);
        testFixture.addDemand();
        testFixture.addEvents();

        linkVolumeToCSV.writeResults(false);//TODO

        BufferedReader br = IOUtils.getBufferedReader(this.utils.getOutputDirectory() + "matsim_linkvolumes.csv.gz");
        StringBuilder sb = new StringBuilder();
        String line = br.readLine();

        while (line != null) {
            sb.append(line);
            sb.append("\n");
            line = br.readLine();
        }

        String data = sb.toString();
        String expectedFull = "link_id;mode;bin;volume\n" +
                "2;car;1;0.0\n" +
                "2;car;2;0.0\n" +
                "2;car;3;0.0\n" +
                "2;car;4;0.0\n" +
                "2;car;5;0.0\n" +
                "2;car;6;0.0\n" +
                "2;car;7;0.0\n" +
                "2;car;8;0.0\n" +
                "2;car;9;1.0\n" +
                "2;car;10;0.0\n" +
                "2;car;11;0.0\n" +
                "2;car;12;0.0\n" +
                "2;car;13;0.0\n" +
                "2;car;14;0.0\n" +
                "2;car;15;0.0\n" +
                "2;car;16;0.0\n" +
                "2;car;17;0.0\n" +
                "2;car;18;0.0\n" +
                "2;car;19;0.0\n" +
                "2;car;20;0.0\n" +
                "2;car;21;0.0\n" +
                "2;car;22;0.0\n" +
                "2;car;23;0.0\n" +
                "2;car;24;0.0\n" +
                "2;car;25;0.0\n" +
                "3;car;1;0.0\n" +
                "3;car;2;0.0\n" +
                "3;car;3;0.0\n" +
                "3;car;4;0.0\n" +
                "3;car;5;0.0\n" +
                "3;car;6;0.0\n" +
                "3;car;7;0.0\n" +
                "3;car;8;0.0\n" +
                "3;car;9;1.0\n" +
                "3;car;10;0.0\n" +
                "3;car;11;0.0\n" +
                "3;car;12;0.0\n" +
                "3;car;13;0.0\n" +
                "3;car;14;0.0\n" +
                "3;car;15;0.0\n" +
                "3;car;16;0.0\n" +
                "3;car;17;0.0\n" +
                "3;car;18;0.0\n" +
                "3;car;19;0.0\n" +
                "3;car;20;0.0\n" +
                "3;car;21;0.0\n" +
                "3;car;22;0.0\n" +
                "3;car;23;0.0\n" +
                "3;car;24;0.0\n" +
                "3;car;25;0.0\n";
        Assert.assertEquals(expectedFull, data);
    }

    @Test
    public void test_withLinkFilter() throws IOException {
        LinkTestFixture testFixture = new LinkTestFixture();
        Counts<Link> counts = new Counts<>();
        Count<Link> count = counts.createAndAddCount(Id.create(3, Link.class), "in the ghetto");
        count.createVolume(1, 987); // we'll probably only provide daily values in the first hour.
        testFixture.scenario.addScenarioElement(Counts.ELEMENT_NAME, counts);
        LinkVolumeToCSV linkVolumeToCSV = new LinkVolumeToCSV(testFixture.scenario, this.utils.getOutputDirectory());

        testFixture.eventsManager.addHandler(linkVolumeToCSV);
        testFixture.addDemand();
        testFixture.addEvents();

        linkVolumeToCSV.writeResults(false);//TODO

        BufferedReader br = IOUtils.getBufferedReader(this.utils.getOutputDirectory() + "matsim_linkvolumes.csv.gz");
        StringBuilder sb = new StringBuilder();
        String line = br.readLine();

        while (line != null) {
            sb.append(line);
            sb.append("\n");
            line = br.readLine();
        }

        String data = sb.toString();
        String expectedFiltered = "link_id;mode;bin;volume\n" +
                "3;car;1;0.0\n" +
                "3;car;2;0.0\n" +
                "3;car;3;0.0\n" +
                "3;car;4;0.0\n" +
                "3;car;5;0.0\n" +
                "3;car;6;0.0\n" +
                "3;car;7;0.0\n" +
                "3;car;8;0.0\n" +
                "3;car;9;1.0\n" +
                "3;car;10;0.0\n" +
                "3;car;11;0.0\n" +
                "3;car;12;0.0\n" +
                "3;car;13;0.0\n" +
                "3;car;14;0.0\n" +
                "3;car;15;0.0\n" +
                "3;car;16;0.0\n" +
                "3;car;17;0.0\n" +
                "3;car;18;0.0\n" +
                "3;car;19;0.0\n" +
                "3;car;20;0.0\n" +
                "3;car;21;0.0\n" +
                "3;car;22;0.0\n" +
                "3;car;23;0.0\n" +
                "3;car;24;0.0\n" +
                "3;car;25;0.0\n";
        Assert.assertEquals(expectedFiltered, data);
    }

}