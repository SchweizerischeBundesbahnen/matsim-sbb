package ch.sbb.matsim.analysis.VisumPuTSurvey;

import ch.sbb.matsim.analysis.EventsToTravelDiaries;
import ch.sbb.matsim.analysis.travelcomponents.TravellerChain;
import org.junit.Assert;
import org.junit.Test;
import org.matsim.api.core.v01.Id;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Paths;

public class VisumPuTSurveyIntegrationTest {

    /**
     * This test fails in IntelliJ when run as part of all tests in the project, but works if run individually.
     * It always works if run with Maven. I guess there's no easy fix, as the test seems to be dependent on some
     * internal ordering of Ids, which might be different when other tests have run before.
     *
     * @throws IOException
     */
    @Test
    public void test() throws IOException {
        TestFixture fixture = new TestFixture();
        fixture.addSingleTransitDemand();
        fixture.addEvents();
        EventsToTravelDiaries eventsToTravelDiaries = fixture.eventsToTravelDiaries;
        VisumPuTSurvey visumPuTSurvey = new VisumPuTSurvey(eventsToTravelDiaries.getChains(), fixture.scenario, null,10.0);
        TravellerChain chain = eventsToTravelDiaries.getChains().get(Id.createPersonId("1"));
        Assert.assertNotNull("TravellerChain for person 1 not found.", chain);
        //Element ID is runtime dependent
        int elementId = chain.getLastTrip().getElementId();

        Files.createDirectories(Paths.get("./test/output/ch/sbb/matsim/analysis/VisumPuTSurvey/VisumPuTSurveyIntegrationTest/"));
        visumPuTSurvey.write("./test/output/ch/sbb/matsim/analysis/VisumPuTSurvey/VisumPuTSurveyIntegrationTest/");
        String expected = "$VISION\n* VisumInst\n* 10.11.06\n*\n*\n" +
                "* Tabelle: Versionsblock\n$VERSION:VERSNR;FILETYPE;LANGUAGE;UNIT" +
                "\n4.00;Att;DEU;KM\n*\n*\n* Tabelle: ÖV-Teilwege" +
                "\n$OEVTEILWEG:DATENSATZNR;TWEGIND;VONHSTNR;NACHHSTNR;VSYSCODE;" +
                "LINNAME;LINROUTENAME;RICHTUNGSCODE;FZPNAME;TEILWEG-KENNUNG;EINHSTNR;EINHSTABFAHRTSTAG;" +
                "EINHSTABFAHRTSZEIT;PFAHRT;SUBPOP;ORIG_GEM;DEST_GEM;ACCESS_TO_RAIL_MODE;EGRESS_FROM_RAIL_MODE;" +
                "ACCESS_TO_RAIL_DIST;EGRESS_FROM_RAIL_DIST\n" +
                elementId + ";1;B;D;code;code;code;code;code;E;B;1;08:22:00;10;regular;999999999;999999999;;;0;0\n";

        BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream("./test/output/ch/sbb/matsim/analysis/VisumPuTSurvey/VisumPuTSurveyIntegrationTest/matsim_put_survey.att"), "Cp1252"));
        StringBuilder sb = new StringBuilder();
        String line = br.readLine();
        while (line != null) {
            sb.append(line);
            sb.append("\n");
            line = br.readLine();
        }
        String everything = sb.toString();
        Assert.assertEquals(expected, everything);
    }
}
