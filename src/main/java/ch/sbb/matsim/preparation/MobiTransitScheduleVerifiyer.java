package ch.sbb.matsim.preparation;

import ch.sbb.matsim.config.variables.SBBModes;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.apache.log4j.Logger;
import org.matsim.api.core.v01.Scenario;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.pt.transitSchedule.api.TransitLine;
import org.matsim.pt.transitSchedule.api.TransitRoute;
import org.matsim.pt.transitSchedule.api.TransitSchedule;
import org.matsim.pt.transitSchedule.api.TransitScheduleReader;

/**
 * @author jbischoff / SBB
 */
public class MobiTransitScheduleVerifiyer {

	public static final Logger LOGGER = Logger.getLogger(MobiTransitScheduleVerifiyer.class);

	public static void main(String[] args) {
		String scheduleFile = "\\\\k13536\\mobi\\40_Projekte\\20191007_Prognose\\transit\\20191220_Angebot_NBL_REF_SubModes\\transitSchedule.xml.gz";
		Scenario scenario = ScenarioUtils.createScenario(ConfigUtils.createConfig());
		new TransitScheduleReader(scenario).readFile(scheduleFile);
		verifyTransitSchedule(scenario.getTransitSchedule());
	}

	public static void verifyTransitSchedule(TransitSchedule schedule) {
		//hard coded comparison values from MOBI 2.1 (schedule 2017)
		Map<String, Integer> modeComparison21 = new HashMap<>();
		modeComparison21.put(SBBModes.PTSubModes.RAIL, 1390);
		modeComparison21.put(SBBModes.PTSubModes.TRAM, 1428);
		modeComparison21.put(SBBModes.PTSubModes.OTHER, 524);
		modeComparison21.put(SBBModes.PTSubModes.BUS, 16823);
		int linesComparison21 = 1879;
		int routesComparison21 = 20165;
		int stopsComparison21 = 25037;

		List<String> transportModes = new ArrayList<>();
		schedule.getTransitLines().values().forEach(transitLine -> transitLine.getRoutes().values()
				.forEach(l -> transportModes.add(l.getTransportMode())));
		Set<String> uniqueModes = new TreeSet<>(transportModes);
		LOGGER.info("Existing transit modes: " + uniqueModes);
		LOGGER.info("Existing Modes in MOBi 2.1: " + modeComparison21.keySet());
		LOGGER.info("Number of transit lines: " + schedule.getTransitLines().size() + " Number in MOBi 2.1: " + linesComparison21);
		LOGGER.info("Number of transit routes: " + transportModes.size() + " Number in MOBi 2.1: " + routesComparison21);
		LOGGER.info("Number of transit stops: " + schedule.getFacilities().size() + " Number in MOBi 2.1: " + stopsComparison21);
		for (String mode : uniqueModes) {
			int count = 0;
			for (TransitLine line : schedule.getTransitLines().values()) {
				for (TransitRoute transitRoute : line.getRoutes().values()) {
					if (transitRoute.getTransportMode().equals(mode)) {
						count++;
					}
				}
			}
			int old = modeComparison21.get(mode);
			LOGGER.info("Number of daily " + mode + " departures: " + count + " Number in MOBi 2.1: " + old + " Diff: " + (count - old));
		}

	}
}
