package ch.sbb.matsim.umlego;

import ch.sbb.matsim.csv.CSVReader;
import ch.sbb.matsim.projects.synpop.OMXODParser;
import ch.sbb.matsim.routing.pt.raptor.RaptorParameters;
import ch.sbb.matsim.routing.pt.raptor.RaptorRoute;
import ch.sbb.matsim.routing.pt.raptor.RaptorStaticConfig;
import ch.sbb.matsim.routing.pt.raptor.RaptorUtils;
import ch.sbb.matsim.routing.pt.raptor.SwissRailRaptor;
import ch.sbb.matsim.routing.pt.raptor.SwissRailRaptorData;
import com.opencsv.CSVWriter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.network.io.MatsimNetworkReader;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.core.utils.io.IOUtils;
import org.matsim.core.utils.misc.Time;
import org.matsim.pt.transitSchedule.api.TransitScheduleReader;
import org.matsim.pt.transitSchedule.api.TransitStopFacility;
import org.matsim.vehicles.MatsimVehicleReader;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

public class Umlego {

	private static final Logger LOG = LogManager.getLogger(Umlego.class);

	private final OMXODParser demand;
	private final Scenario scenario;
	private final String zoneConnectionsFilename;
	private final Map<String, Map<String, List<FoundRoute>>> foundRoutes = new ConcurrentHashMap<>();
	private Map<String, List<ConnectedStop>> stopsPerZone;
	private final List<String> zoneIds;

	public static void main(String[] args) {
		String omxFilename = "/Users/Shared/data/projects/Umlego/input_data/demand/demand_matrices.omx";
		String networkFilename = "/Users/Shared/data/projects/Umlego/input_data/timetable/output/transitNetwork.xml.gz";
		String scheduleFilename = "/Users/Shared/data/projects/Umlego/input_data/timetable/output/transitSchedule.xml.gz";
		String vehiclesFilename = "/Users/Shared/data/projects/Umlego/input_data/timetable/output/transitVehicles.xml.gz";
		String zoneConnectionsFilename = "/Users/Shared/data/projects/Umlego/input_data/timetable/Anbindungszeiten.csv";
		String csvOutputFilename = "/Users/Shared/data/projects/Umlego/umlego_unfiltered.csv";
		int threads = 6;

		// load transit schedule
		Scenario scenario = ScenarioUtils.createScenario(ConfigUtils.createConfig());
		new MatsimNetworkReader(scenario.getNetwork()).readFile(networkFilename);
		new TransitScheduleReader(scenario).readFile(scheduleFilename);
		new MatsimVehicleReader(scenario.getVehicles()).readFile(vehiclesFilename);

		OMXODParser demand = new OMXODParser();
		demand.openMatrix(omxFilename);

//		List<String> relevantZones = null; // null will use all zones

		List<String> relevantZones = java.util.List.of("1", "14", "18", "47", "48", "52", "68", "69", "85",
				"108", "123", "124", "132", "136", "162", "163", "187", "213", "220", "222", "237", "281", "288",
				"302", "307", "318", "325", "329", "358", "361", "404", "439", "450", "467", "468", "480", "487", "492",
				"500", "503", "504", "526", "534", "537", "539", "546", "565", "582", "587", "599", "623", "662", "681", "682",
				"700", "718", "748", "763", "768", "778", "785", "786", "797", "835", "844", "863", "877", "893",
				"907", "909", "910", "914", "938", "960", "962", "967", "971", "973", "979", "985", "996",
				"3182", "6025");

//		List<String> relevantZones = List.of("1", "14", "18");

//		List<String> relevantZones = List.of("281", "979"); // Frick, ZH Oerlikon
//		List<String> relevantZones = List.of("1", "85"); // Aarau, Bern

		new Umlego(demand, scenario, zoneConnectionsFilename, relevantZones).run(csvOutputFilename, threads);
	}

	public Umlego(OMXODParser demand, Scenario scenario, String zoneConnectionsFilename, List<String> relevantZones) {
		this.demand = demand;
		this.scenario = scenario;
		this.zoneConnectionsFilename = zoneConnectionsFilename;
		this.zoneIds = relevantZones == null ? demand.getAllLookupValues() : relevantZones;
	}

	public void run(String csvOutputFilename, int threadCount) {
		loadZoneConnections();
		calculateRoutes(threadCount);
		filterRoutes();
		sortRoutes();
		writeRoutes(csvOutputFilename);
	}

	private void loadZoneConnections() {
		this.stopsPerZone = readZoneConnections(zoneConnectionsFilename, scenario);
	}

	private void calculateRoutes(int threadCount) {
		RaptorParameters raptorParams = RaptorUtils.createParameters(scenario.getConfig());
		raptorParams.setTransferPenaltyFixCostPerTransfer(0);
		raptorParams.setTransferPenaltyMinimum(0);
		raptorParams.setTransferPenaltyMaximum(0);

		// initialize SwissRailRaptor
		RaptorStaticConfig raptorConfig = RaptorUtils.createStaticConfig(scenario.getConfig());
		raptorConfig.setOptimization(RaptorStaticConfig.RaptorOptimization.OneToAllRouting);
		raptorConfig.setBeelineWalkConnectionDistance(400.0);
		SwissRailRaptorData raptorData = SwissRailRaptorData.create(this.scenario.getTransitSchedule(), this.scenario.getTransitVehicles(), raptorConfig, this.scenario.getNetwork(), null);

		List<ConnectedStop> emptyList = Collections.emptyList();

		Set<TransitStopFacility> relevantStops = new HashSet<>();
		for (String zoneId : this.zoneIds) {
			List<TransitStopFacility> stops = this.stopsPerZone.getOrDefault(zoneId, emptyList).stream().map(stop -> stop.stopFacility).toList();
			relevantStops.addAll(stops);
		}
		ConcurrentLinkedQueue<TransitStopFacility> stopsQueue = new ConcurrentLinkedQueue<>(relevantStops);

		LOG.info("Detected {} stops as potential origin or destinations", relevantStops.size());

		// there is no good concurrent set, thus use ConcurrentHashMap<T, Boolean> instead
		Map<TransitStopFacility, Map<TransitStopFacility, Map<FoundRoute, Boolean>>> foundRoutesByStops = new ConcurrentHashMap<>();
		Thread[] threads = new Thread[threadCount];
		for (int i = 0; i < threads.length; i++) {
			SwissRailRaptor raptor = new SwissRailRaptor.Builder(raptorData, this.scenario.getConfig()).build();
			threads[i] = new Thread(new StopsQueueWorker(stopsQueue, raptor, raptorParams, relevantStops, foundRoutesByStops));
			threads[i].start();
		}
		for (int i = 0; i < threads.length; i++) {
			try {
				threads[i].join();
			} catch (InterruptedException e) {
				LOG.error("Problem while waiting for worker {} to finish.", i, e);
			}
		}

		LOG.info("Aggregate to zones");
		for (String originZoneId : zoneIds) {
			Map<String, List<FoundRoute>> routesPerOrigin = this.foundRoutes.computeIfAbsent(originZoneId, zoneId -> new HashMap<>());

			List<ConnectedStop> stopsPerOriginZone = this.stopsPerZone.getOrDefault(originZoneId, emptyList);
			Map<TransitStopFacility, ConnectedStop> originStopLookup = new HashMap<>();
			for (ConnectedStop stop : stopsPerOriginZone) {
				originStopLookup.put(stop.stopFacility, stop);
			}

			for (String destinationZoneId : zoneIds) {

				List<ConnectedStop> stopsPerDestinationZone = this.stopsPerZone.getOrDefault(destinationZoneId, emptyList);
				Map<TransitStopFacility, ConnectedStop> destinationStopLookup = new HashMap<>();
				for (ConnectedStop stop : stopsPerDestinationZone) {
					destinationStopLookup.put(stop.stopFacility, stop);
				}

				Set<FoundRoute> allRoutesFromTo = new HashSet<>();
				for (ConnectedStop originStop : stopsPerOriginZone) {
					Map<TransitStopFacility, Map<FoundRoute, Boolean>> routesPerDestinationStop = foundRoutesByStops.get(originStop.stopFacility);
					if (routesPerDestinationStop != null) {
						for (ConnectedStop destinationStop : this.stopsPerZone.getOrDefault(destinationZoneId, emptyList)) {
							Map<FoundRoute, Boolean> routesPerOriginDestinationStop = routesPerDestinationStop.get(destinationStop.stopFacility);
							if (routesPerOriginDestinationStop != null) {
								for (FoundRoute route : routesPerOriginDestinationStop.keySet()) {
									ConnectedStop originConnectedStop = originStopLookup.get(route.originStop);
									ConnectedStop destinationConnectedStop = destinationStopLookup.get(route.destinationStop);

									if (originConnectedStop != null && destinationConnectedStop != null) {
										// otherwise the route would not be valid, e.g. due to an additional transfer at the start or end
										route.originConnectedStop = originConnectedStop;
										route.destinationConnectedStop = destinationConnectedStop;
										allRoutesFromTo.add(route);
									}
								}
							}
						}
					}
				}
				routesPerOrigin.put(destinationZoneId, new ArrayList<>(allRoutesFromTo));
			}
		}
		LOG.info("All routes calculated");
	}

	private void filterRoutes() {
		for (Map<String, List<FoundRoute>> routesPerDestination : this.foundRoutes.values()) {
			for (List<FoundRoute> routes : routesPerDestination.values()) {
				routes.sort(this::compareFoundRoutesByArrivalTimeAndTravelTime);
				// routs are now sorted by arrival time (ascending) and travel time (ascending) and transfers (ascending)
				double lastArrivalTime = -1;
				FoundRoute thisBestRoute = null; // best route with
				FoundRoute lastBestRoute = null;
				for (ListIterator<FoundRoute> iterator = routes.listIterator(); iterator.hasNext(); ) {
					FoundRoute route = iterator.next();
					if (route.arrTime != lastArrivalTime) {
						lastArrivalTime = route.arrTime;
						lastBestRoute = route;
						thisBestRoute = route;
					} else {
						if (route.transfers > thisBestRoute.transfers) {
							if (route.travelTime > lastBestRoute.travelTime) {
								// route has a longer travelTime and more transfers than lastBestRoute, get rid of it
								iterator.remove();
							} else {
								lastBestRoute = thisBestRoute;
								thisBestRoute = route;
							}
						} else {
							if (route.travelTime > lastBestRoute.travelTime) {
								// route has a longer travelTime and more transfers than lastBestRoute, get rid of it
								iterator.remove();
							}
						}
					}
				}
			}
		}
	}

	private void sortRoutes() {
		for (Map<String, List<FoundRoute>> routesPerDestination : this.foundRoutes.values()) {
			for (List<FoundRoute> routes : routesPerDestination.values()) {
				routes.sort(this::compareFoundRoutesByDepartureTime);
			}
		}
	}

	private void writeRoutes(String csvFilename) {
		List<ConnectedStop> emptyList = Collections.emptyList();
		LOG.info("writing output to {}", csvFilename);
		try (CSVWriter writer = new CSVWriter(IOUtils.getBufferedWriter(csvFilename), ',', '"', '\\', "\n")) {
			writer.writeNext(new String[]{"ORIGZONENO", "DESTZONENO", "ORIGNAME", "DESTNAME", "ACCESS_TIME", "EGRESS_TIME", "DEPTIME", "ARRTIME", "TRAVTIME", "NUMTRANSFERS", "DISTANZ", "DETAILS"});

			for (String origZone : zoneIds) {
				Map<String, List<FoundRoute>> routesPerDestination = this.foundRoutes.get(origZone);
				if (routesPerDestination == null) {
					// looks like this zone cannot reach any destination
					continue;
				}
				for (String destZone : zoneIds) {
					if (origZone.equals(destZone)) {
						// we're not interested in intrazonal trips
						continue;
					}
					List<FoundRoute> routesToDestination = routesPerDestination.get(destZone);
					if (routesToDestination == null) {
						// looks like there are no routes to this destination from the given origin zone
						continue;
					}

					for (FoundRoute route : routesToDestination) {
						StringBuilder details = new StringBuilder();
						for (RaptorRoute.RoutePart part : route.routeParts) {
							if (!details.isEmpty()) {
								details.append(", ");
							}
							details.append(part.line.getId());
							details.append(": ");
							details.append(part.fromStop.getName());
							details.append(' ');
							details.append(Time.writeTime(part.vehicleDepTime));
							details.append(" - ");
							details.append(part.toStop.getName());
							details.append(' ');
							details.append(Time.writeTime(part.arrivalTime));
						}
						writer.writeNext(new String[]{
								origZone,
								destZone,
								route.originStop.getName(),
								route.destinationStop.getName(),
								Time.writeTime(route.originConnectedStop.walkTime),
								Time.writeTime(route.destinationConnectedStop.walkTime),
								Time.writeTime(route.depTime),
								Time.writeTime(route.arrTime),
								Time.writeTime(route.travelTime),
								Integer.toString(route.transfers),
								String.format("%.2f", route.distance / 1000.0),
								details.toString()
						});
					}
				}
			}
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	private static class StopsQueueWorker implements Runnable {
		final ConcurrentLinkedQueue<TransitStopFacility> stopsQueue;
		final SwissRailRaptor raptor;
		final RaptorParameters raptorParams;
		final Set<TransitStopFacility> relevantStops;
		final Map<TransitStopFacility, Map<TransitStopFacility, Map<FoundRoute, Boolean>>> foundRoutes;

		public StopsQueueWorker(ConcurrentLinkedQueue<TransitStopFacility> stopsQueue, SwissRailRaptor raptor, RaptorParameters raptorParams, Set<TransitStopFacility> relevantStops, Map<TransitStopFacility, Map<TransitStopFacility, Map<FoundRoute, Boolean>>> foundRoutes) {
			this.stopsQueue = stopsQueue;
			this.raptor = raptor;
			this.raptorParams = raptorParams;
			this.relevantStops = relevantStops;
			this.foundRoutes = foundRoutes;
		}

		public void run() {
			TransitStopFacility originStop;
			while ((originStop = stopsQueue.poll()) != null) {
				calcRoutesFromStop(originStop);
			}
		}

		private void calcRoutesFromStop(TransitStopFacility originStop) {
			LOG.info("calculating routes starting at {} ({})", originStop.getName(), originStop.getId());
			// use Set to collect individual FoundRoutes
			this.raptor.calcTreesObservable(
					originStop,
					0,
					Double.POSITIVE_INFINITY,
					this.raptorParams,
					null,
					(departureTime, arrivalStop, arrivalTime, transferCount, route) -> {
						if (this.relevantStops.contains(arrivalStop)) {
							FoundRoute foundRoute = new FoundRoute(route.get());
							this.foundRoutes
											.computeIfAbsent(foundRoute.originStop, stop -> new ConcurrentHashMap<>())
													.computeIfAbsent(foundRoute.destinationStop, stop -> new ConcurrentHashMap<>())
															.put(foundRoute, Boolean.TRUE);
						}
					});
		}
	}


	public record ConnectedStop(double walkTime, TransitStopFacility stopFacility) {
	}

	public record ODDemand(String originZone, String destinationZone, double demand) {
	}

	public static class FoundRoute {
		public final TransitStopFacility originStop;
		public final TransitStopFacility destinationStop;
		public ConnectedStop originConnectedStop;
		public ConnectedStop destinationConnectedStop;
		public final double depTime;
		public final double arrTime;
		public final double travelTime;
		public final int transfers;
		public final double distance;
		public final List<RaptorRoute.RoutePart> routeParts = new ArrayList<>();

		public FoundRoute(RaptorRoute route) {
			double firstDepTime = Double.NaN;
			double lastArrTime = Double.NaN;

			TransitStopFacility originStopFacility = null;
			TransitStopFacility destinationStopFacility = null;

			double distanceSum = 0;
			for (RaptorRoute.RoutePart part : route.getParts()) {
				if (part.line != null) {
					if (routeParts.isEmpty()) {
						firstDepTime = part.vehicleDepTime;
						originStopFacility = part.fromStop;
					}
					this.routeParts.add(part);
					lastArrTime = part.arrivalTime;
					destinationStopFacility = part.toStop;
					distanceSum += part.distance;
				}
			}
			this.originStop = originStopFacility;
			this.destinationStop = destinationStopFacility;
			this.depTime = firstDepTime;
			this.arrTime = lastArrTime;
			this.travelTime = this.arrTime - this.depTime;
			this.transfers = this.routeParts.size() - 1;
			this.distance = distanceSum;
		}

		@Override
		public boolean equals(Object o) {
			if (this == o) return true;
			if (o == null || getClass() != o.getClass()) return false;
			FoundRoute that = (FoundRoute) o;
			boolean isEqual = Double.compare(depTime, that.depTime) == 0
					&& Double.compare(arrTime, that.arrTime) == 0
					&& transfers == that.transfers
					&& Objects.equals(originStop.getId(), that.originStop.getId())
					&& Objects.equals(destinationStop.getId(), that.destinationStop.getId());
			if (isEqual) {
				// also check route parts
				for (int i = 0; i < routeParts.size(); i++) {
					RaptorRoute.RoutePart routePartThis = this.routeParts.get(i);
					RaptorRoute.RoutePart routePartThat = that.routeParts.get(i);
					boolean partIsEqual = Objects.equals(routePartThis.line.getId(), routePartThat.line.getId())
							&& Objects.equals(routePartThis.route.getId(), routePartThat.route.getId())
							&& Objects.equals(routePartThis.fromStop.getId(), routePartThat.fromStop.getId())
							&& Objects.equals(routePartThis.toStop.getId(), routePartThat.toStop.getId());
					if (!partIsEqual) {
						return false;
					}
				}
			}
			return isEqual;
		}

		@Override
		public int hashCode() {
			return Objects.hash(depTime, arrTime, transfers);
		}
	}

	private static Map<String, List<ConnectedStop>> readZoneConnections(String filename, Scenario scenario) {
		Map<String, List<ConnectedStop>> stopsPerZone = new HashMap<>();
		Map<Id<TransitStopFacility>, TransitStopFacility> stops = scenario.getTransitSchedule().getFacilities();
		try (CSVReader reader = new CSVReader(filename, ",")) {
			Map<String, String> row;
			while ((row = reader.readLine()) != null) {
				String zoneId = row.get("zone");
				String stopPoint = row.get("stop_point");
				double walkTime = Double.parseDouble(row.get("walk_time"));
				TransitStopFacility stopFacility = stops.get(Id.create(stopPoint, TransitStopFacility.class));
				if (stopFacility == null) {
					LOG.warn("stop {} referenced by zone {} cannot be found.", stopPoint, zoneId);
				} else {
					stopsPerZone.computeIfAbsent(zoneId, z -> new ArrayList<>(5)).add(new ConnectedStop(walkTime, stopFacility));
				}
			}
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
		return stopsPerZone;
	}

	private static double sum(double[][] values) {
		double sum = 0;
		for (double[] nested : values) {
			sum += sum(nested);
		}
		return sum;
	}

	private static double sum(double[] values) {
		double sum = 0;
		for (double value : values) {
			sum += value;
		}
		return sum;
	}

	private int compareFoundRoutesByDepartureTime(FoundRoute o1, FoundRoute o2) {
		if (o1.depTime < o2.depTime) {
			return -1;
		}
		if (o1.depTime > o2.depTime) {
			return +1;
		}
		if (o1.travelTime < o2.travelTime) {
			return -1;
		}
		if (o1.travelTime > o2.travelTime) {
			return +1;
		}
		return Integer.compare(o1.transfers, o2.transfers);
	};

	private int compareFoundRoutesByArrivalTimeAndTravelTime(FoundRoute o1, FoundRoute o2) {
		if (o1.arrTime < o2.arrTime) {
			return -1;
		}
		if (o1.arrTime > o2.arrTime) {
			return +1;
		}
		if (o1.travelTime < o2.travelTime) {
			return -1;
		}
		if (o1.travelTime > o2.travelTime) {
			return +1;
		}
		return Integer.compare(o1.transfers, o2.transfers);
	};

}
