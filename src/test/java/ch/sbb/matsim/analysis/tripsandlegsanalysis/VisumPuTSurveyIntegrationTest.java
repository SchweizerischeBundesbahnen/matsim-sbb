package ch.sbb.matsim.analysis.tripsandlegsanalysis;

import ch.sbb.matsim.analysis.TestFixtures.PtTestFixture;
import ch.sbb.matsim.config.PostProcessingConfigGroup;
import ch.sbb.matsim.zones.Zones;
import ch.sbb.matsim.zones.ZonesCollection;
import ch.sbb.matsim.zones.ZonesImpl;
import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.stream.Collectors;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.matsim.api.core.v01.Id;
import org.matsim.testcases.MatsimTestUtils;

public class VisumPuTSurveyIntegrationTest {

	@Rule
	public MatsimTestUtils utils = new MatsimTestUtils();

	/**
	 * This test fails in IntelliJ when run as part of all tests in the project, but works if run individually. It always works if run with Maven. I guess there's no easy fix, as the test seems to be
	 * dependent on some internal ordering of Ids, which might be different when other tests have run before.
	 *
	 * @throws IOException
	 */
	@Test
	public void test() throws IOException {
		PtTestFixture fixture = new PtTestFixture();

		fixture.addSingleTransitDemand();
		fixture.scenario.getPopulation();
		Zones zones = new ZonesImpl(Id.create("zones", Zones.class));
		ZonesCollection c = new ZonesCollection();
		c.addZones(zones);
		PostProcessingConfigGroup ppc = new PostProcessingConfigGroup();
		ppc.setSimulationSampleSize(1.0);
		ppc.setZonesId("zones");

		PutSurveyWriter putSurveyWriter = new PutSurveyWriter(fixture.scenario, c, ppc);

		String expected = "$VISION\n* VisumInst\n* 10.11.06\n*\n*\n" +
				"* Tabelle: Versionsblock\n$VERSION:VERSNR;FILETYPE;LANGUAGE;UNIT" +
				"\n4.00;Att;DEU;KM\n*\n*\n* Tabelle: Ã–V-Teilwege" +
				"\n$OEVTEILWEG:DATENSATZNR;TWEGIND;VONHSTNR;NACHHSTNR;VSYSCODE;" +
				"LINNAME;LINROUTENAME;RICHTUNGSCODE;FZPNAME;TEILWEG-KENNUNG;EINHSTNR;EINHSTABFAHRTSTAG;" +
				"EINHSTABFAHRTSZEIT;PFAHRT;SUBPOP;ORIG_GEM;DEST_GEM;ACCESS_TO_RAIL_MODE;EGRESS_FROM_RAIL_MODE;" +
				"ACCESS_TO_RAIL_DIST;EGRESS_FROM_RAIL_DIST;PERSONID;TOURID_TRIPID;FROM_ACT;TO_ACT\n" +
				"1;1;B;D;code;code;code;code;code;E;B;1;08:11:40;1.0;regular;999999999;999999999;;;0;0;1;;home;work\n";

		putSurveyWriter.collectAndWritePUTSurvey(this.utils.getOutputDirectory() + "matsim_put_survey.att",
				fixture.scenario.getPopulation().getPersons().values().stream().collect(Collectors.toMap(p -> p.getId(), p -> p.getSelectedPlan())));

		// Add Assert
		BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(this.utils.getOutputDirectory() + "matsim_put_survey.att"), "Cp1252"));
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
