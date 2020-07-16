package ch.sbb.matsim.analysis.zonebased;

import ch.sbb.matsim.config.variables.SBBModes;
import ch.sbb.matsim.zones.Zone;
import ch.sbb.matsim.zones.Zones;
import ch.sbb.matsim.zones.ZonesImpl;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.commons.lang3.mutable.MutableInt;
import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;
import org.apache.log4j.Logger;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.population.Activity;
import org.matsim.api.core.v01.population.Leg;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.Route;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.network.io.MatsimNetworkReader;
import org.matsim.core.population.algorithms.PersonAlgorithm;
import org.matsim.core.population.io.StreamingPopulationReader;
import org.matsim.core.router.MainModeIdentifier;
import org.matsim.core.router.TripStructureUtils;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.facilities.MatsimFacilitiesReader;
import org.matsim.pt.routes.TransitPassengerRoute;
import org.matsim.pt.transitSchedule.api.TransitRoute;
import org.matsim.pt.transitSchedule.api.TransitScheduleReader;
import org.matsim.pt.transitSchedule.api.TransitStopFacility;

/**
 * @author jbischoff / SBB
 */
public class ZoneBasedAnalysis {

	public static final String ZONE_ID = "zoneId";
	private final RunZonebasedAnalysis.ZonebasedAnalysisConfig config;
	private final Zones zones;
	final private Set<String> ptModes;
	final private MainModeIdentifier identifier;
	private final Scenario scenario;
	private final Map<Id<Zone>, ZoneStats> zoneStatsMap;
	Map<Id<TransitRoute>, String> modePerRoute;

	ZoneBasedAnalysis(Zones zones, RunZonebasedAnalysis.ZonebasedAnalysisConfig config, MainModeIdentifier identifier) {
		this.zones = zones;
		this.config = config;
		this.identifier = identifier;
		zoneStatsMap = ((ZonesImpl) zones).getZones().stream().collect(Collectors.toMap(zone -> zone.getId(), zone -> new ZoneStats()));
		scenario = ScenarioUtils.createScenario(ConfigUtils.createConfig());
		new TransitScheduleReader(scenario).readFile(config.transitScheduleFile);
		new MatsimFacilitiesReader(scenario).readFile(config.facilitiesFile);
		new MatsimNetworkReader(scenario.getNetwork()).readFile(config.networkFile);
		prepareSchedule(zones);
		modePerRoute = scenario.getTransitSchedule().getTransitLines().values().stream()
				.flatMap(l -> l.getRoutes().values().stream())
				.collect(Collectors.toMap(r -> r.getId(), r -> r.getTransportMode()));

		ptModes = modePerRoute.values().stream().collect(Collectors.toSet());

		zoneStatsMap.values().forEach(z -> {
			z.modalPtDepartures = new HashMap<>();
			z.ptBoardings = new HashMap<>();
			z.ptdeBoardings = new HashMap<>();
			z.travelDistance = new HashMap<>();
			z.travelTimes = new HashMap<>();

			for (String mode : ptModes) {
				z.modalPtDepartures.put(mode, 0);
				z.ptBoardings.put(mode, new MutableInt(0));
				z.ptdeBoardings.put(mode, new MutableInt(0));
			}
		});

	}

	public Map<Id<Zone>, ZoneStats> runAnalysis() {
		fillModalDepartures();
		analyzePersons();
		return zoneStatsMap;

	}

	private void analyzePersons() {

		StreamingPopulationReader spr = new StreamingPopulationReader(scenario);
		spr.addAlgorithm(new BoardingsAnalyzer());
		spr.addAlgorithm(new TripAnalyzer());
		spr.readFile(config.plansFile);

	}

	private Coord findCoord(Activity originActivity) {
		if (originActivity.getCoord() != null) {
			return originActivity.getCoord();
		} else if (originActivity.getFacilityId() != null) {
			return scenario.getActivityFacilities().getFacilities().get(originActivity.getFacilityId()).getCoord();
		} else if (originActivity.getLinkId() != null) {
			return scenario.getNetwork().getLinks().get(originActivity.getLinkId()).getCoord();
		} else {
			throw new RuntimeException("No coordinate found for " + originActivity);
		}
	}

	private void fillModalDepartures() {
		for (String mode : ptModes) {
			Set<TransitRoute> modeRoutes = scenario.getTransitSchedule().getTransitLines().values()
					.stream()
					.flatMap(l -> l.getRoutes().values().stream())
					.filter(r -> mode.equals(r.getTransportMode()))
					.collect(Collectors.toSet());

			for (TransitRoute route : modeRoutes) {
				int departures = route.getDepartures().size();
				Set<Id<Zone>> zonesServed = route.getStops().stream()
						.map(s -> s.getStopFacility())
						.map(s -> ((Id<Zone>) s.getAttributes().getAttribute(ZONE_ID)))
						.filter(Objects::nonNull)
						.collect(Collectors.toSet());
				for (Id<Zone> zoneId : zonesServed) {
					Map<String, Integer> modeDeps = zoneStatsMap.get(zoneId).modalPtDepartures;
					int deps = modeDeps.get(mode) + departures;
					modeDeps.put(mode, deps);
				}
			}

		}
	}

	private void prepareSchedule(Zones zones) {
		for (TransitStopFacility f : scenario.getTransitSchedule().getFacilities().values()) {
			Zone zone = zones.findZone(f.getCoord());
			if (zone != null) {
				f.getAttributes().putAttribute(ZONE_ID, zone.getId());
			}
		}

	}

	public static class ZoneStats {

		public static final String DEPARTURES = "_departures";
		public static final String BOARDINGS = "_boardings";
		public static final String DEBOARDINGS = "_deboardings";
		public static final String TRIPS = "_trips";
		public static final String AVERAGE_TRAVEL_TIME = "_averageTravelTime";
		public static final String AVERAGE_TRAVEL_DISTANCE = "_averageTravelDistance";
		public static final String TRANSFERS = "_averagePtTransfers";
		public static List<String> basicHeader = Arrays.asList(DEPARTURES, BOARDINGS, DEBOARDINGS, TRIPS, AVERAGE_TRAVEL_TIME, AVERAGE_TRAVEL_DISTANCE);
		Map<String, Integer> modalPtDepartures;
		Map<String, MutableInt> ptBoardings;
		Map<String, MutableInt> ptdeBoardings;
		Map<String, DescriptiveStatistics> travelTimes;
		Map<String, DescriptiveStatistics> travelDistance;
		DescriptiveStatistics transfers = new DescriptiveStatistics();

	}

	private class TripAnalyzer implements PersonAlgorithm {

		@Override
		public void run(Person person) {
			List<TripStructureUtils.Trip> trips = TripStructureUtils.getTrips(person.getSelectedPlan());
			for (TripStructureUtils.Trip trip : trips) {
				Coord startCoord = findCoord(trip.getOriginActivity());
				Zone startZone = zones.findZone(startCoord);
				if (startZone != null) {
					//Id<Zone> endZone = zones.findZone(trip.getDestinationActivity().getCoord()).getId();
					String mainMode = identifier.identifyMainMode(trip.getTripElements());
					Double travelTime = trip.getLegsOnly().stream().map(Leg::getRoute).map(Route::getTravelTime).map(t -> t.orElse(0)).reduce(0., Double::sum);
					Double distance = trip.getLegsOnly().stream().map(Leg::getRoute).map(Route::getDistance).reduce(0., Double::sum);
					ZoneStats zoneStats = zoneStatsMap.get(startZone.getId());
					zoneStats.travelDistance.computeIfAbsent(mainMode, a -> new DescriptiveStatistics()).addValue(distance);
					zoneStats.travelTimes.computeIfAbsent(mainMode, a -> new DescriptiveStatistics()).addValue(travelTime);
					if (mainMode.equals(SBBModes.PT)) {
						long transfers = trip.getLegsOnly().stream().filter(t -> t.getMode().equals(SBBModes.PT)).count() - 1;
						if (transfers >= 0) {
							zoneStats.transfers.addValue(transfers);
						}
					}
				}
			}
		}

	}

	private class BoardingsAnalyzer implements PersonAlgorithm {

		@Override
		public void run(Person person) {
			List<Leg> ptLegs = TripStructureUtils.getLegs(person.getSelectedPlan())
					.stream()
					.filter(l -> l.getMode().equals(SBBModes.PT))
					.collect(Collectors.toList());
			for (Leg ptleg : ptLegs) {
				if (ptleg.getRoute() instanceof TransitPassengerRoute) {
					TransitPassengerRoute route = (TransitPassengerRoute) ptleg.getRoute();

					String mode = modePerRoute.get(route.getRouteId());
					Id<Zone> boardingZone = (Id<Zone>) scenario.getTransitSchedule().getFacilities().get(route.getAccessStopId()).getAttributes().getAttribute(ZONE_ID);
					Id<Zone> deboardingZone = (Id<Zone>) scenario.getTransitSchedule().getFacilities().get(route.getEgressStopId()).getAttributes().getAttribute(ZONE_ID);

					if (boardingZone != null) {
						zoneStatsMap.get(boardingZone).ptBoardings.get(mode).increment();
					}
					if (deboardingZone != null) {
						zoneStatsMap.get(deboardingZone).ptdeBoardings.get(mode).increment();
					}
				} else {
					Logger.getLogger(getClass()).warn("unexpected pt-route in leg " + ptleg.toString() + ptleg.getRoute().getClass());
				}

			}
		}

	}

}
