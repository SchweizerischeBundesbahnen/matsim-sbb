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
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

public class Umlego {

	private static final Logger LOG = LogManager.getLogger(Umlego.class);

	public static void main(String[] args) {
		String omxFilename = "/Users/Shared/data/projects/Umlego/input_data/demand/demand_matrices.omx";
		String networkFilename = "/Users/Shared/data/projects/Umlego/input_data/timetable/output/transitNetwork.xml.gz";
		String scheduleFilename = "/Users/Shared/data/projects/Umlego/input_data/timetable/output/transitSchedule.xml.gz";
		String vehiclesFilename = "/Users/Shared/data/projects/Umlego/input_data/timetable/output/transitVehicles.xml.gz";
		String zoneConnectionsFilename = "/Users/Shared/data/projects/Umlego/input_data/timetable/Anbindungszeiten.csv";
		String csvOutputFilename = "/Users/Shared/data/projects/Umlego/test_output.csv";

		// load transit schedule
		Scenario scenario = ScenarioUtils.createScenario(ConfigUtils.createConfig());
		new MatsimNetworkReader(scenario.getNetwork()).readFile(networkFilename);
		new TransitScheduleReader(scenario).readFile(scheduleFilename);
		new MatsimVehicleReader(scenario.getVehicles()).readFile(vehiclesFilename);

//		List<String> relevantZones = null;

		List<String> relevantZones = List.of("1", "14", "18", "47", "48", "52", "68", "69", "85",
				"108", "123", "124", "132", "136", "162", "163", "187", "213", "220", "222", "237", "281", "288",
				"302", "307", "318", "325", "329", "358", "361", "404", "439", "450", "467", "468", "480", "487", "492",
				"500", "503", "504", "526", "534", "537", "539", "546", "565", "582", "587", "599", "623", "662", "681", "682",
				"700", "718", "748", "763", "768", "778", "785", "786", "797", "835", "844", "863", "877", "893",
				"907", "909", "910", "914", "938", "960", "962", "967", "971", "973", "979", "985", "996",
				"3182", "6025");

//		List<String> relevantZones = List.of("1", "14", "18");

//		List<String> relevantZones = List.of("281", "979"); // Frick, ZH Oerlikon
//		List<String> relevantZones = List.of("1", "85"); // Aarau, Bern

		run(omxFilename, scenario, zoneConnectionsFilename, relevantZones, csvOutputFilename);
	}

	public static void run(String omxFilename, Scenario scenario, String zoneConnectionsFilename, List<String> relevantZones, String csvOutputFilename) {

		RaptorParameters raptorParams = RaptorUtils.createParameters(scenario.getConfig());
		raptorParams.setTransferPenaltyFixCostPerTransfer(0);
		raptorParams.setTransferPenaltyMinimum(0);
		raptorParams.setTransferPenaltyMaximum(0);

		// load demand tables from omx file
		OMXODParser demand = new OMXODParser();
		demand.openMatrix(omxFilename);
		// we assume all matrices are named as continuous integer numbers starting at 1:  "1", "2", ... "n"
		int matrixCount = demand.getMatrixNames().size();

		// load zoneConnections from file
		Map<String, List<ConnectedStop>> stopsPerZone = readZoneConnections(zoneConnectionsFilename, scenario);
		Map<TransitStopFacility, ConnectedStop> connectionPerStop = new HashMap<>();
		stopsPerZone.forEach((zoneId, stops) -> stops.forEach(stop -> {
			connectionPerStop.put(stop.stopFacility, stop);
		}));

		// initialize SwissRailRaptor
		RaptorStaticConfig raptorConfig = RaptorUtils.createStaticConfig(scenario.getConfig());
		raptorConfig.setOptimization(RaptorStaticConfig.RaptorOptimization.OneToAllRouting);
		raptorConfig.setBeelineWalkConnectionDistance(400.0);
		SwissRailRaptorData raptorData = SwissRailRaptorData.create(scenario.getTransitSchedule(), scenario.getTransitVehicles(), raptorConfig, scenario.getNetwork(), null);

		List<ODDemand> unroutableDemand = new ArrayList<>();
		List<ConnectedStop> emptyList = Collections.emptyList();
		List<String> allZoneIds = demand.getAllLookupValues();

		List<String> zoneIds = relevantZones == null ? allZoneIds : relevantZones;

		Set<TransitStopFacility> relevantStops = new HashSet<>();
		for (String zoneId : zoneIds) {
			List<TransitStopFacility> stops = stopsPerZone.getOrDefault(zoneId, emptyList).stream().map(stop -> stop.stopFacility).toList();
			relevantStops.addAll(stops);
		}
		ConcurrentLinkedQueue<TransitStopFacility> stopsQueue = new ConcurrentLinkedQueue<>(relevantStops);

		LOG.info("Detected {} stops as potential origin or destinations", relevantStops.size());

		final Map<TransitStopFacility, Map<TransitStopFacility, Set<FoundRoute>>> foundRoutes = new ConcurrentHashMap<>();

		Thread[] threads = new Thread[4];
		for (int i = 0; i < threads.length; i++) {
			SwissRailRaptor raptor = new SwissRailRaptor.Builder(raptorData, scenario.getConfig()).build();
			threads[i] = new Thread(new StopsQueueWorker(stopsQueue, raptor, raptorParams, relevantStops, foundRoutes));
			threads[i].start();
		}
		for (int i = 0; i < threads.length; i++) {
			try {
				threads[i].join();
			} catch (InterruptedException e) {
				LOG.error("Problem while waiting for worker {} to finish.", i, e);
			}
		}
		LOG.info("All routes calculated");


//		TransitStopFacility originStop;
//		while ((originStop = stopsQueue.poll()) != null) {
//			Map<TransitStopFacility, Set<FoundRoute>> destinationStopRoutes = foundRoutes.computeIfAbsent(originStop, s -> new HashMap<>());
//			calcRoutesFromStop(originStop, destinationStopRoutes, raptor, raptorParams, relevantStops);
//		}

//		for (String originZoneId : zoneIds) {
//			LOG.info("calculating for origin-zone {}", originZoneId);
//
//			double[][] originDemand = new double[matrixCount][];
//			for (int i = 0; i < matrixCount; i++) {
//				originDemand[i] = demand.getMatrixRow(originZoneId, Integer.toString(i + 1));
//			}
//
//			List<ConnectedStop> stops = stopsPerZone.getOrDefault(originZoneId, emptyList);
//
//			if (stops.isEmpty()) {
//				LOG.warn("no stops found for zone {}", originZoneId);
//				unroutableDemand.add(new ODDemand(originZoneId, "*", sum(originDemand)));
//			} else {
//				// calculate connections
//				for (ConnectedStop originStop : stops) {
//					raptor.calcTreesObservable(
//							originStop.stopFacility,
//							0,
//							Double.POSITIVE_INFINITY,
//							raptorParams,
//							null,
//							(departureTime, arrivalStop, arrivalTime, transferCount, route) -> {
//								if (relevantStops.contains(arrivalStop)) {
//									foundRoutes
//											.computeIfAbsent(originStop.stopFacility, s -> new HashMap<>())
//											.computeIfAbsent(arrivalStop, s -> new HashSet<>())
//											.add(new FoundRoute(originStop, connectionPerStop.get(arrivalStop), route.get()));
//								}
//							});
//				}
//			}
//		}

//		LOG.info("unroutable demand:");
//		double sum = 0;
//		for (ODDemand dmd : unroutableDemand) {
//			LOG.info("- " + dmd.originZone() + " > " + dmd.destinationZone() + ": " + dmd.demand());
//			sum += dmd.demand();
//		}
//		LOG.info("total unroutable demand: " + sum);

		try (CSVWriter writer = new CSVWriter(IOUtils.getBufferedWriter(csvOutputFilename), ',', '"', '\\', "\n")) {
			writer.writeNext(new String[]{"ORIGZONENO", "DESTZONENO", "ORIGNAME", "DESTNAME", "ACCESS_TIME", "EGRESS_TIME", "DEPTIME", "ARRTIME", "TRAVTIME", "NUMTRANSFERS", "DISTANZ", "DETAILS"});

			for (String origZone : zoneIds) {
				for (String destZone : zoneIds) {
					if (origZone.equals(destZone)) {
						// we're not interested in intrazonal trips
						continue;
					}
					for (ConnectedStop originStop : stopsPerZone.getOrDefault(origZone, emptyList)) {
						Map<TransitStopFacility, Set<FoundRoute>> destinationStopRoutes = foundRoutes.get(originStop.stopFacility);
						for (ConnectedStop destinationStop : stopsPerZone.getOrDefault(destZone, emptyList)) {
							if (originStop.stopFacility.equals(destinationStop.stopFacility)) {
								continue;
							}
							var routesToDestinationStop = destinationStopRoutes.get(destinationStop.stopFacility);
							if (routesToDestinationStop == null) {
//								LOG.info("no routes from {} ({}) to {} ({})", originStop.stopFacility.getName(), originStop.stopFacility.getId(), destinationStop.stopFacility.getName(), destinationStop.stopFacility.getId());
								continue;
							}
							List<FoundRoute> routes = new ArrayList<>(routesToDestinationStop);

							routes.sort((o1, o2) -> {
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
							});

							for (FoundRoute route : routes) {
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
										Time.writeTime(originStop.walkTime),
										Time.writeTime(destinationStop.walkTime),
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
		final Map<TransitStopFacility, Map<TransitStopFacility, Set<FoundRoute>>> foundRoutes;

		public StopsQueueWorker(ConcurrentLinkedQueue<TransitStopFacility> stopsQueue, SwissRailRaptor raptor, RaptorParameters raptorParams, Set<TransitStopFacility> relevantStops, Map<TransitStopFacility, Map<TransitStopFacility, Set<FoundRoute>>> foundRoutes) {
			this.stopsQueue = stopsQueue;
			this.raptor = raptor;
			this.raptorParams = raptorParams;
			this.relevantStops = relevantStops;
			this.foundRoutes = foundRoutes;
		}

		public void run() {
			TransitStopFacility originStop;
			while ((originStop = stopsQueue.poll()) != null) {
				Map<TransitStopFacility, Set<FoundRoute>> destinationStopRoutes = foundRoutes.computeIfAbsent(originStop, s -> new HashMap<>());
				calcRoutesFromStop(originStop, destinationStopRoutes);
			}
		}

		private void calcRoutesFromStop(TransitStopFacility originStop, Map<TransitStopFacility, Set<FoundRoute>> destinationStopRoutes) {
			LOG.info("calculating routes starting at {} ({})", originStop.getName(), originStop.getId());
			this.raptor.calcTreesObservable(
					originStop,
					0,
					Double.POSITIVE_INFINITY,
					this.raptorParams,
					null,
					(departureTime, arrivalStop, arrivalTime, transferCount, route) -> {
						if (this.relevantStops.contains(arrivalStop)) {
							destinationStopRoutes
									.computeIfAbsent(arrivalStop, s -> new HashSet<>())
									.add(new FoundRoute(originStop, arrivalStop, route.get()));
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
		public final double depTime;
		public final double arrTime;
		public final double travelTime;
		public final int transfers;
		public final double distance;
		public final List<RaptorRoute.RoutePart> routeParts = new ArrayList<>();

		public FoundRoute(TransitStopFacility originStop, TransitStopFacility destinationStop, RaptorRoute route) {
			this.originStop = originStop;
			this.destinationStop = destinationStop;

			double firstDepTime = Double.NaN;
			double lastArrTime = Double.NaN;

			double distanceSum = 0;
			for (RaptorRoute.RoutePart part : route.getParts()) {
				if (part.line != null) {
					if (routeParts.isEmpty()) {
						firstDepTime = part.vehicleDepTime;
					}
					this.routeParts.add(part);
					lastArrTime = part.arrivalTime;
					distanceSum += part.distance;
				}
			}
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

}
