package ch.sbb.matsim.umlego;

import ch.sbb.matsim.csv.CSVReader;
import ch.sbb.matsim.projects.synpop.OMXODParser;
import ch.sbb.matsim.routing.pt.raptor.RaptorParameters;
import ch.sbb.matsim.routing.pt.raptor.RaptorRoute;
import ch.sbb.matsim.routing.pt.raptor.RaptorStaticConfig;
import ch.sbb.matsim.routing.pt.raptor.RaptorUtils;
import ch.sbb.matsim.routing.pt.raptor.SwissRailRaptor;
import ch.sbb.matsim.routing.pt.raptor.SwissRailRaptorData;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.network.io.MatsimNetworkReader;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.core.utils.misc.Time;
import org.matsim.pt.transitSchedule.api.TransitScheduleReader;
import org.matsim.pt.transitSchedule.api.TransitStopFacility;
import org.matsim.vehicles.MatsimVehicleReader;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * @author mrieser / Simunto
 */
public class Umlego {

	private static final Logger LOG = LogManager.getLogger(Umlego.class);

	private final OMXODParser demand;
	private final Scenario scenario;
	private final Map<String, List<ConnectedStop>> stopsPerZone;

	public static void main(String[] args) {
		long startTime = System.currentTimeMillis();
		String omxFilename = "/Users/Shared/data/projects/Umlego/input_data/demand/demand_matrices.omx";
		String networkFilename = "/Users/Shared/data/projects/Umlego/input_data/timetable_v2/output/transitNetwork.xml.gz";
		String scheduleFilename = "/Users/Shared/data/projects/Umlego/input_data/timetable_v2/output/transitSchedule.xml.gz";
		String vehiclesFilename = "/Users/Shared/data/projects/Umlego/input_data/timetable_v2/output/transitVehicles.xml.gz";
		String zoneConnectionsFilename = "/Users/Shared/data/projects/Umlego/input_data/timetable/Anbindungszeiten.csv";
		String csvOutputFilename = "/Users/Shared/data/projects/Umlego/umlego_testoutput.csv";
		int threads = 6;

		// load transit schedule
		Scenario scenario = ScenarioUtils.createScenario(ConfigUtils.createConfig());
		new MatsimNetworkReader(scenario.getNetwork()).readFile(networkFilename);
		new TransitScheduleReader(scenario).readFile(scheduleFilename);
		new MatsimVehicleReader(scenario.getVehicles()).readFile(vehiclesFilename);

		OMXODParser demand = new OMXODParser();
		demand.openMatrix(omxFilename);

//		List<String> relevantZones = null; // null uses all zones

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
//		List<String> relevantZones = List.of("85", "503"); // Bern, Luzern
//		List<String> relevantZones = List.of("237", "985"); // Dietikon, ZUE
//		List<String> relevantZones = List.of("910", "700"); // Wattwil, Rapperswil
//		List<String> relevantZones = List.of("404", "361"); // Interlaken Ost, Gstaad
//		List<String> relevantZones = List.of("281", "361"); // Frick, Gstaad

		SearchImpedanceParameters search = new SearchImpedanceParameters(1.0, 1.0, 1.0, 1.0, 1.0, 10.0);
		PreselectionParameters preselection = new PreselectionParameters(2.0, 60.0);
		PerceivedJourneyTimeParameters pjt = new PerceivedJourneyTimeParameters(1.0, 2.94, 2.94, 2.25, 1.13, 17.24, 0.03, 58.0);
		RouteImpedanceParameters impedance = new RouteImpedanceParameters(1.0, 1.85, 1.85);
		RouteSelectionParameters routeSelection = new RouteSelectionParameters(false,3600.0, 3600.0, RouteUtilityCalculators.boxcox(1.536, 0.5)); // take routes 1 hour before and after into account
		WriterParameters writer = new WriterParameters(false, true);
		UmlegoParameters params = new UmlegoParameters(5, search, preselection, pjt, impedance, routeSelection, writer);

		Map<String, List<ConnectedStop>> zoneConnections = Umlego.readZoneConnections(zoneConnectionsFilename, scenario);
		long calcStartTime = System.currentTimeMillis();
		new Umlego(demand, scenario, zoneConnections).run(relevantZones, params, threads, csvOutputFilename);
		long endTime = System.currentTimeMillis();
		LOG.info("total time: {} seconds", (endTime - startTime) / 1000.0);
		LOG.info("calculation+write time: {} seconds", (endTime - calcStartTime) / 1000.0);
		LOG.info("threads: {}, writeRoutesWithoutDemand {}, writeDetails {}", threads, writer.writeRoutesWithoutDemand, writer.writeDetails);
	}

	public Umlego(OMXODParser demand, Scenario scenario, Map<String, List<ConnectedStop>> stopsPerZone) {
		this.demand = demand;
		this.scenario = scenario;
		this.stopsPerZone = stopsPerZone;
	}

	public void run(List<String> relevantZones, UmlegoParameters params, int threadCount, String csvOutputFilename) {
		List<String> zoneIds = relevantZones == null ? new ArrayList<>(demand.getAllLookupValues()) : new ArrayList<>(relevantZones);
		zoneIds.sort(String::compareTo);

		// detect relevant stops
		List<ConnectedStop> emptyList = Collections.emptyList();
		IntSet relevantStopIndices = new IntOpenHashSet();
		for (String zoneId : zoneIds) {
			List<TransitStopFacility> stops = this.stopsPerZone.getOrDefault(zoneId, emptyList).stream().map(stop -> stop.stopFacility).toList();
			for (TransitStopFacility stop : stops) {
				relevantStopIndices.add(stop.getId().index());
			}
		}
		LOG.info("Detected {} stops as potential origin or destinations", relevantStopIndices.size());

		// prepare SwissRailRaptor
		RaptorParameters raptorParams = RaptorUtils.createParameters(scenario.getConfig());
		raptorParams.setTransferPenaltyFixCostPerTransfer(0.01);
		raptorParams.setTransferPenaltyMinimum(0.01);
		raptorParams.setTransferPenaltyMaximum(0.01);

		RaptorStaticConfig raptorConfig = RaptorUtils.createStaticConfig(scenario.getConfig());
		raptorConfig.setOptimization(RaptorStaticConfig.RaptorOptimization.OneToAllRouting);
		raptorConfig.setBeelineWalkConnectionDistance(400.0);
		SwissRailRaptorData raptorData = SwissRailRaptorData.create(this.scenario.getTransitSchedule(), this.scenario.getTransitVehicles(), raptorConfig, this.scenario.getNetwork(), null);

		// prepare queues with work items
		/* Writing might actually be slower than the computation, resulting in more and more
		   memory being used for the found routes until they get written. To prevent
		   OutOfMemoryErrors, we use a blocking queue for the writer with a limited capacity.
		 */
		UmlegoWorker.WorkItem workEndMarker = new UmlegoWorker.WorkItem(null, null);
		CompletableFuture<UmlegoWorker.WorkResult> writeEndMarker = new CompletableFuture<>();
		writeEndMarker.complete(new UmlegoWorker.WorkResult(null, null, null));

		BlockingQueue<UmlegoWorker.WorkItem> workerQueue = new LinkedBlockingQueue<>(5 * threadCount);
		BlockingQueue<Future<UmlegoWorker.WorkResult>> writerQueue = new LinkedBlockingQueue<>(4 * threadCount);

		// start worker threads
		Thread[] threads = new Thread[threadCount];
		for (int i = 0; i < threads.length; i++) {
			SwissRailRaptor raptor = new SwissRailRaptor.Builder(raptorData, this.scenario.getConfig()).build();
//			threads[i] = new Thread(new UmlegoWorker(workerQueue, params, this.demand, raptor, raptorParams, relevantStops, zoneIds, this.stopsPerZone));
			threads[i] = new Thread(new UmlegoWorker(workerQueue, params, this.demand, raptor, raptorParams, relevantStopIndices, zoneIds, this.stopsPerZone));
			threads[i].start();
		}

		// start writer threads
		UmlegoWriter writer = new UmlegoWriter(writerQueue, csvOutputFilename, zoneIds, params.writer);
		new Thread(writer).start();

		// submit work items into queues
		for (String originZoneId : zoneIds) {
			try {
				CompletableFuture<UmlegoWorker.WorkResult> future = new CompletableFuture<>();
				UmlegoWorker.WorkItem workItem = new UmlegoWorker.WorkItem(originZoneId, future);
				writerQueue.put(future);
				workerQueue.put(workItem);
			} catch (InterruptedException e) {
				throw new RuntimeException(e);
			}
		}
		// once all zones are submitted for calculation, add the end-markers to the queues
		try {
			for (int i = 0; i < threadCount; i++) {
				workerQueue.put(workEndMarker);
			}
			writerQueue.put(writeEndMarker);
		} catch (InterruptedException e) {
			throw new RuntimeException(e);
		}

		// once the unroutable demand becomes available, the calculation and writing has finished
		UnroutableDemand unroutableDemand;
		try {
			unroutableDemand = writer.getUnroutableDemand().get();
		} catch (InterruptedException | ExecutionException e) {
			throw new RuntimeException(e);
		}

		if (unroutableDemand.demand >= 0) {
			LOG.warn("Unroutable demand: {}", unroutableDemand.demand);
		} else {
			LOG.info("Unroutable demand: {}", unroutableDemand.demand);
		}
	}

	public record ConnectedStop(double walkTime, TransitStopFacility stopFacility) {
	}

	public static class UnroutableDemand {
		double demand = 0;
	}

	public record SearchImpedanceParameters(
			double betaInVehicleTime,
			double betaAccessTime,
			double betaEgressTime,
			double betaWalkTime,
			double betaTransferWaitTime,
			double betaTransferCount
	) {
	}

	public record PreselectionParameters(
			double betaMinImpedance,
			double constImpedance
	) {
	}

	public record PerceivedJourneyTimeParameters(
			double betaInVehicleTime,
			double betaAccessTime,
			double betaEgressTime,
			double betaWalkTime,
			double betaTransferWaitTime,
			double transferFix,
			double transferTraveltimeFactor,
			double secondsPerAdditionalStop
	) {
	}

	public record RouteImpedanceParameters(
			double betaPerceivedJourneyTime,
			double betaDeltaTEarly,
			double betaDeltaTLate
	) {
	}

	public record RouteSelectionParameters(
			boolean limitSelectionToTimewindow,
			double beforeTimewindow,
			double afterTimewindow,
			RouteUtilityCalculator utilityCalculator
	) {
	}

	public record WriterParameters(
			boolean writeRoutesWithoutDemand,
			boolean writeDetails
	) {}

	public record UmlegoParameters(
			int maxTransfers,
			SearchImpedanceParameters search,
			PreselectionParameters preselection,
			PerceivedJourneyTimeParameters pjt,
			RouteImpedanceParameters impedance,
			RouteSelectionParameters routeSelection,
			WriterParameters writer
	) {
	}

	public static class FoundRoute {
		public final TransitStopFacility originStop;
		public final TransitStopFacility destinationStop;
		public ConnectedStop originConnectedStop;
		public ConnectedStop destinationConnectedStop;
		public final double depTime;
		public final double arrTime;
		public final double travelTimeWithoutAccess;
		public double travelTimeWithAccess = Double.NaN;
		public final int transfers;
		public final double distance;
		public final List<RaptorRoute.RoutePart> routeParts = new ArrayList<>();
		public double searchImpedance = Double.NaN;
		public double perceivedJourneyTime_min = Double.NaN;
		public double demand = 0;

		public FoundRoute(RaptorRoute route) {
			double firstDepTime = Double.NaN;
			double lastArrTime = Double.NaN;

			TransitStopFacility originStopFacility = null;
			TransitStopFacility destinationStopFacility = null;

			double distanceSum = 0;
			RaptorRoute.RoutePart prevTransfer = null;
			int stageCount = 0;
			for (RaptorRoute.RoutePart part : route.getParts()) {
				if (part.line == null) {
					// it is a transfer
					prevTransfer = part;
				} else {
					stageCount++;
					if (routeParts.isEmpty()) {
						// it is the first real stage
						firstDepTime = part.vehicleDepTime;
						originStopFacility = part.fromStop;
					} else if (prevTransfer != null) {
						this.routeParts.add(prevTransfer);
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
			this.travelTimeWithoutAccess = this.arrTime - this.depTime;
			this.transfers = stageCount - 1;
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

					boolean partIsEqual =
							((routePartThis.line == null && routePartThat.line == null) || (routePartThis.line != null && routePartThat.line != null && Objects.equals(routePartThis.line.getId(), routePartThat.line.getId())))
									&& ((routePartThis.route == null && routePartThat.route == null) || (routePartThis.route != null && routePartThat.route != null && Objects.equals(routePartThis.route.getId(), routePartThat.route.getId())))
					;
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

		public String getRouteAsString() {
			StringBuilder details = new StringBuilder();
			for (RaptorRoute.RoutePart part : this.routeParts) {
				if (part.line == null) {
					continue;
				}
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
			return details.toString();
		}
	}

	public static Map<String, List<ConnectedStop>> readZoneConnections(String filename, Scenario scenario) {
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

}
