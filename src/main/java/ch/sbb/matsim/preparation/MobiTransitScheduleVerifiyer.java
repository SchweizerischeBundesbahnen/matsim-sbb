package ch.sbb.matsim.preparation;

import ch.sbb.matsim.config.variables.SBBModes;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.core.utils.geometry.CoordUtils;
import org.matsim.pt.transitSchedule.api.TransitSchedule;
import org.matsim.pt.transitSchedule.api.TransitScheduleReader;
import org.matsim.pt.transitSchedule.api.TransitStopFacility;

import java.util.*;

/**
 * @author jbischoff / SBB
 */
public class MobiTransitScheduleVerifiyer {

	public static final Logger LOGGER = LogManager.getLogger(MobiTransitScheduleVerifiyer.class);

	public static void main(String[] args) {
        String scheduleFile = "\\\\wsbbrz0283\\mobi\\50_Ergebnisse\\MOBi_4.0\\2040\\pt\\BAVAK35\\output\\transitSchedule.xml.gz";
		Scenario scenario = ScenarioUtils.createScenario(ConfigUtils.createConfig());
		new TransitScheduleReader(scenario).readFile(scheduleFile);
		verifyTransitSchedule(scenario.getTransitSchedule());
	}

	public static void verifyTransitSchedule(TransitSchedule schedule) {
		//hard coded comparison values from schedule 2017, 2020, 2030 and 2040
		Coord bernCoord31 = CoordUtils.createCoord(2600050.78090007, 1199824.09952451);
		Map<String, Integer> modeComparison31 = new HashMap<>();
		modeComparison31.put(SBBModes.PTSubModes.RAIL, 16076);
		modeComparison31.put(SBBModes.PTSubModes.TRAM, 11342);
		modeComparison31.put(SBBModes.PTSubModes.OTHER, 27775);
		modeComparison31.put(SBBModes.PTSubModes.BUS, 113038);
		int linesComparison31 = 1879;
		int routesComparison31 = 20165;
		int stopsComparison31 = 25037;
		int timeProfiles31 = 18134;

		Map<String, Integer> modeComparison2020 = new HashMap<>();
		modeComparison2020.put(SBBModes.PTSubModes.RAIL, 16275);
		modeComparison2020.put(SBBModes.PTSubModes.TRAM, 11068);
		modeComparison2020.put(SBBModes.PTSubModes.OTHER, 43201);
		modeComparison2020.put(SBBModes.PTSubModes.BUS, 121349);
		int linesComparison2020 = 2011;
		int routesComparison2020 = 21910;
		int stopsComparison2020 = 25873;
		int timeProfiles2020 = 19420;

		Map<String, Integer> modeComparison2025 = new HashMap<>();
		modeComparison2025.put(SBBModes.PTSubModes.RAIL, 17345);
		modeComparison2025.put(SBBModes.PTSubModes.TRAM, 12124);
		modeComparison2025.put(SBBModes.PTSubModes.OTHER, 43201);
		modeComparison2025.put(SBBModes.PTSubModes.BUS, 121088);
		int linesComparison2025 = 2024;
		int routesComparison2025 = 21928;
		int stopsComparison2025 = 25955;
		int timeProfiles2025 = 19354;

		Map<String, Integer> modeComparison2035 = new HashMap<>();
		modeComparison2035.put(SBBModes.PTSubModes.RAIL, 18730);
		modeComparison2035.put(SBBModes.PTSubModes.TRAM, 12124);
		modeComparison2035.put(SBBModes.PTSubModes.OTHER, 43201);
		modeComparison2035.put(SBBModes.PTSubModes.BUS, 121088);
		int linesComparison2035 = 2014;
		int routesComparison2035 = 21976;
		int stopsComparison2035 = 25950;
		int timeProfiles2035 = 19342;

		List<String> routeNames = new ArrayList<>();
		Map<String, Integer> departuresPerMode = new HashMap<>();
		schedule.getTransitLines().values().forEach(transitLine -> transitLine.getRoutes().values()
				.forEach(route -> {
					String mode = route.getTransportMode();
					int departures = route.getDepartures().size();
					int prev_departures = departuresPerMode.getOrDefault(mode, 0);
					departuresPerMode.put(mode, prev_departures + departures);
					routeNames.add(route.getId().toString().split("_")[0]);
				}));
		Set<String> timeProfilesVisum = new TreeSet<>(routeNames);

        TransitStopFacility bernFacility = schedule.getFacilities().get(Id.create(1311, TransitStopFacility.class));

        if (bernFacility != null) {
            Coord bernCoord = bernFacility.getCoord();
            LOGGER.info(" - Coordinates of the rail station Bern: " + bernCoord.toString());
            LOGGER.info("\t - MOBi 3.1 (2017): " + bernCoord31 + "; Distance: " + CoordUtils.calcEuclideanDistance(bernCoord, bernCoord));
            if (CoordUtils.calcEuclideanDistance(bernCoord, bernCoord) > 200) {
                throw new RuntimeException("Bern has not the same coordinates as in 2017 (the station moved more than 200m).");
            }
        } else {
            LOGGER.warn("Station in Bern does not exist. Assuming this is intended.");
		}

		LOGGER.info(" - Existing transit modes: " + departuresPerMode.keySet());
		LOGGER.info("\t - MOBi 3.1 (2017): " + modeComparison31.keySet());
		LOGGER.info(" - Transit lines: " + schedule.getTransitLines().size());
		LOGGER.info("\t - MOBi 3.1 (2017): " + linesComparison31 + "; Diff: " + (schedule.getTransitLines().size() - linesComparison31));
		LOGGER.info("\t - MOBi 3.1 (2020): " + linesComparison2020 + "; Diff: " + (schedule.getTransitLines().size() - linesComparison2020));
		LOGGER.info("\t - MOBi 3.1 (2030): " + linesComparison2025 + "; Diff: " + (schedule.getTransitLines().size() - linesComparison2025));
		LOGGER.info("\t - MOBi 3.1 (2040): " + linesComparison2035 + "; Diff: " + (schedule.getTransitLines().size() - linesComparison2035));
		LOGGER.info(" - Visum time profiles with min. one vehicle journey: " + timeProfilesVisum.size());
		LOGGER.info("\t - MOBi 3.1 (2017): " + timeProfiles31 + "; Diff: " + (timeProfilesVisum.size() - timeProfiles31));
		LOGGER.info("\t - MOBi 3.1 (2020): " + timeProfiles2020 + "; Diff: " + (timeProfilesVisum.size() - timeProfiles2020));
		LOGGER.info("\t - MOBi 3.1 (2030): " + timeProfiles2025 + "; Diff: " + (timeProfilesVisum.size() - timeProfiles2025));
		LOGGER.info("\t - MOBi 3.1 (2040): " + timeProfiles2035 + "; Diff: " + (timeProfilesVisum.size() - timeProfiles2035));
		LOGGER.info(" - Transit routes: " + routeNames.size());
		LOGGER.info("\t - MOBi 3.1 (2017): " + routesComparison31 + "; Diff: " + (routeNames.size() - routesComparison31));
		LOGGER.info("\t - MOBi 3.1 (2020): " + routesComparison2020 + "; Diff: " + (routeNames.size() - routesComparison2020));
		LOGGER.info("\t - MOBi 3.1 (2030): " + routesComparison2025 + "; Diff: " + (routeNames.size() - routesComparison2025));
		LOGGER.info("\t - MOBi 3.1 (2040): " + routesComparison2035 + "; Diff: " + (routeNames.size() - routesComparison2035));
		LOGGER.info(" - Stops (Visum stop points with min. one serving vehicle journey): " + schedule.getFacilities().size());
		LOGGER.info("\t - MOBi 3.1 (2017): " + stopsComparison31 + "; Diff: " + (schedule.getFacilities().size() - stopsComparison31));
		LOGGER.info("\t - MOBi 3.1 (2020): " + stopsComparison2020 + "; Diff: " + (schedule.getFacilities().size() - stopsComparison2020));
		LOGGER.info("\t - MOBi 3.1 (2030): " + stopsComparison2025 + "; Diff: " + (schedule.getFacilities().size() - stopsComparison2025));
		LOGGER.info("\t - MOBi 3.1 (2040): " + stopsComparison2035 + "; Diff: " + (schedule.getFacilities().size() - stopsComparison2035));
		departuresPerMode.forEach((mode, value) -> {
			LOGGER.info(" - Daily " + mode + " departures (Visum vehicle journeys): " + value);
			LOGGER.info("\t - MOBi 3.1 (2017): " + modeComparison31.getOrDefault(mode, 0) + "; Diff: " + (value - modeComparison31.getOrDefault(mode, 0)));
			LOGGER.info("\t - MOBi 3.1 (2020): " + modeComparison2020.getOrDefault(mode, 0) + "; Diff: " + (value - modeComparison2020.getOrDefault(mode, 0)));
			LOGGER.info("\t - MOBi 3.1 (2030): " + modeComparison2025.getOrDefault(mode, 0) + "; Diff: " + (value - modeComparison2025.getOrDefault(mode, 0)));
			LOGGER.info("\t - MOBi 3.1 (2040): " + modeComparison2035.getOrDefault(mode, 0) + "; Diff: " + (value - modeComparison2035.getOrDefault(mode, 0)));
		});
	}
}
