package ch.sbb.matsim.analysis.VisumPuTSurvey;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Assert;
import org.junit.Test;
import org.matsim.api.core.v01.Id;
import org.matsim.pt.transitSchedule.api.TransitSchedule;

import ch.sbb.matsim.analysis.EventsToTravelDiaries;

public class VisumPuTSurveyIntegrationTest {

    @Test
    public void test() throws IOException {

        TestFixture fixture = new TestFixture();
        fixture.addSingleTransitDemand();
        fixture.addEvents();


        EventsToTravelDiaries eventsToTravelDiaries = fixture.eventsToTravelDiaries;
        TransitSchedule transitSchedule = fixture.scenario.getTransitSchedule();

        VisumPuTSurvey visumPuTSurvey = new VisumPuTSurvey(eventsToTravelDiaries.getChains(), transitSchedule, 10.0);

        System.out.println(eventsToTravelDiaries.getChains().get(Id.createPersonId("1")).getJourneys().getFirst().getTrips().size());

        visumPuTSurvey.write("./");

        System.out.println(visumPuTSurvey.getWriter().getData());

        String expected = "$VISION\n* VisumInst\n* 10.11.06\n*\n*\n* Tabelle: Versionsblock\n$VERSION:VERSNR;FILETYPE;LANGUAGE;UNIT\n4.00;Att;DEU;KM\n*\n*\n* Tabelle: ÖV-Teilwege\n$OEVTEILWEG:DATENSATZNR;TWEGIND;VONHSTNR;NACHHSTNR;VSYSCODE;LINNAME;LINROUTENAME;RICHTUNGSCODE;FZPNAME;TEILWEG-KENNUNG;EINHSTNR;EINHSTABFAHRTSTAG;EINHSTABFAHRTSZEIT;PFAHRT;\n2;1;B;D;code;code;code;code;code;E;B;1;08:22:00;10;\n";


        // Add Assert
        BufferedReader br = new BufferedReader(new FileReader("./matsim_put_survey.att"));
        StringBuilder sb = new StringBuilder();
        String line = br.readLine();

        while (line != null) {
            sb.append(line);
            sb.append("\n");
            line = br.readLine();
        }
        String everything = sb.toString();
        System.out.println(everything);
        Assert.assertEquals(expected, everything);
    }
}
