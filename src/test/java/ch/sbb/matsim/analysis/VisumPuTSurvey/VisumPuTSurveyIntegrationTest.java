package ch.sbb.matsim.analysis.VisumPuTSurvey;
import ch.sbb.matsim.analysis.EventsToTravelDiaries;
import org.junit.Assert;
import org.junit.Test;
import org.matsim.api.core.v01.Id;
import org.matsim.pt.transitSchedule.api.TransitSchedule;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;

public class VisumPuTSurveyIntegrationTest {

    @Test
    public void test() {

        TestFixture fixture = new TestFixture();
        fixture.addSingleTransitDemand();
        fixture.addEvents();


        EventsToTravelDiaries eventsToTravelDiaries = fixture.eventsToTravelDiaries;
        TransitSchedule transitSchedule = fixture.scenario.getTransitSchedule();

        VisumPuTSurvey visumPuTSurvey = new VisumPuTSurvey(eventsToTravelDiaries.getChains(), transitSchedule, 10.0);

        System.out.println(eventsToTravelDiaries.getChains().get(Id.createPersonId("1")).getJourneys().getFirst().getTrips().size());

        visumPuTSurvey.write("./");

        System.out.println(visumPuTSurvey.getWriter().getData());


        String expected = "$VISION\n* VisumInst\n* 10.11.06\n*\n*\n* Tabelle: Versionsblock\n$VERSION:VERSNR;FILETYPE;LANGUAGE;UNIT\n4.00;Att;DEU;KM\n*\n*\n* Tabelle: ÖV-Teilwege\n$OEVTEILWEG:DATENSATZNR;TWEGIND;VONHSTNR;NACHHSTNR;VSYSCODE;LINNAME;LINROUTENAME;RICHTUNGSCODE;FZPNAME;TEILWEG-KENNUNG;EINHSTNR;EINHSTABFAHRTSTAG;EINHSTABFAHRTSZEIT;PFAHRT;\n1;1;B;D;code;code;code;code;code;E;B;1;08:21:41;10;\n";


        // Add Assert
        try {
            BufferedReader br = new BufferedReader(new FileReader("./matsim_put_survey.att"));
            try {
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
            } finally {
                br.close();
            }
        }catch (FileNotFoundException e){
            Assert.assertTrue(false);
        }
        catch (IOException e){
            Assert.assertTrue(false);
        }
    }
}
