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
		Map<TransitStopFacility, String> zonePerStop = new HashMap<>();
		Map<TransitStopFacility, ConnectedStop> connectionPerStop = new HashMap<>();
		stopsPerZone.forEach((zoneId, stops) -> stops.forEach(stop -> {
			zonePerStop.put(stop.stopFacility, zoneId);
			connectionPerStop.put(stop.stopFacility, stop);
		}));

		// initialize SwissRailRaptor
		RaptorStaticConfig raptorConfig = RaptorUtils.createStaticConfig(scenario.getConfig());
		raptorConfig.setOptimization(RaptorStaticConfig.RaptorOptimization.OneToAllRouting);
		raptorConfig.setBeelineWalkConnectionDistance(400.0);
		SwissRailRaptorData raptorData = SwissRailRaptorData.create(scenario.getTransitSchedule(), scenario.getTransitVehicles(), raptorConfig, scenario.getNetwork(), null);
		SwissRailRaptor raptor = new SwissRailRaptor.Builder(raptorData, scenario.getConfig()).build();

		List<ODDemand> unroutableDemand = new ArrayList<>();
		List<ConnectedStop> emptyList = Collections.emptyList();
		List<String> allZoneIds = demand.getAllLookupValues();

		List<String> zoneIds = relevantZones == null ? allZoneIds : relevantZones;
		Set<TransitStopFacility> relevantDestinationStops = new HashSet<>();
		if (relevantZones == null) {
			relevantDestinationStops.addAll(scenario.getTransitSchedule().getFacilities().values());
		} else {
			for (String zoneId : zoneIds) {
				List<TransitStopFacility> stops = stopsPerZone.getOrDefault(zoneId, emptyList).stream().map(connectedStop -> connectedStop.stopFacility).toList();
				relevantDestinationStops.addAll(stops);
			}
		}

		final Map<TransitStopFacility, Map<TransitStopFacility, Set<FoundRoute>>> foundRoutes = new HashMap<>();
		for (String originZoneId : zoneIds) {
			LOG.info("calculating for origin-zone {}", originZoneId);

			double[][] originDemand = new double[matrixCount][];
			for (int i = 0; i < matrixCount; i++) {
				originDemand[i] = demand.getMatrixRow(originZoneId, Integer.toString(i + 1));
			}

			List<ConnectedStop> stops = stopsPerZone.getOrDefault(originZoneId, emptyList);

			if (stops.isEmpty()) {
				LOG.warn("no stops found for zone {}", originZoneId);
				unroutableDemand.add(new ODDemand(originZoneId, "*", sum(originDemand)));
			} else {
				// calculate connections
				for (ConnectedStop originStop : stops) {
					raptor.calcTreesObservable(
							originStop.stopFacility,
							0,
							Double.POSITIVE_INFINITY,
							raptorParams,
							null,
							(departureTime, arrivalStop, arrivalTime, transferCount, route) -> {
								if (relevantDestinationStops.contains(arrivalStop)) {
									foundRoutes
											.computeIfAbsent(originStop.stopFacility, s -> new HashMap<>())
											.computeIfAbsent(arrivalStop, s -> new HashSet<>())
											.add(new FoundRoute(originZoneId, originStop, zonePerStop.get(arrivalStop), connectionPerStop.get(arrivalStop), route.get()));
								}
							});
				}
			}
		}

		LOG.info("unroutable demand:");
		double sum = 0;
		for (ODDemand dmd : unroutableDemand) {
			LOG.info("- " + dmd.originZone() + " > " + dmd.destinationZone() + ": " + dmd.demand());
			sum += dmd.demand();
		}
		LOG.info("total unroutable demand: " + sum);

		try (CSVWriter writer = new CSVWriter(IOUtils.getBufferedWriter(csvOutputFilename), ',', '"', '\\', "\n")) {
			writer.writeNext(new String[]{"ORIGZONENO", "DESTZONENO", "ORIGNAME", "DESTNAME", "ACCESS_TIME", "EGRESS_TIME", "DEPTIME", "ARRTIME", "TRAVTIME", "NUMTRANSFERS", "DETAILS"});
			for (Map.Entry<TransitStopFacility, Map<TransitStopFacility, Set<FoundRoute>>> entry : foundRoutes.entrySet()) {
				TransitStopFacility originStop = entry.getKey();
				String originZone = zonePerStop.get(originStop);
				for (Map.Entry<TransitStopFacility, Set<FoundRoute>> destEntry : entry.getValue().entrySet()) {
					TransitStopFacility destinationStop = destEntry.getKey();
					String destinationZone = zonePerStop.get(destinationStop);

					List<FoundRoute> routes = new ArrayList<>(destEntry.getValue());
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
								originZone,
								destinationZone,
								route.originStop.stopFacility.getName(),
								route.destinationStop.stopFacility.getName(),
								Time.writeTime(route.originStop.walkTime),
								Time.writeTime(route.destinationStop.walkTime),
								Time.writeTime(route.depTime),
								Time.writeTime(route.arrTime),
								Time.writeTime(route.travelTime),
								Integer.toString(route.transfers),
								details.toString()
						});
					}
				}
			}
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	public record ConnectedStop(double walkTime, TransitStopFacility stopFacility) {
	}

	public record ODDemand(String originZone, String destinationZone, double demand) {
	}

	public static class FoundRoute {
		public final String originZone;
		public final ConnectedStop originStop;
		public final String destinationZone;
		public final ConnectedStop destinationStop;
		public final double depTime;
		public final double arrTime;
		public final double travelTime;
		public final int transfers;
		public final List<RaptorRoute.RoutePart> routeParts = new ArrayList<>();

		public FoundRoute(String originZone, ConnectedStop originStop, String destinationZone, ConnectedStop destinationStop, RaptorRoute route) {
			this.originZone = originZone;
			this.originStop = originStop;
			this.destinationZone = destinationZone;
			this.destinationStop = destinationStop;

			double firstDepTime = Double.NaN;
			double lastArrTime = Double.NaN;

			for (RaptorRoute.RoutePart part : route.getParts()) {
				if (part.line != null) {
					if (routeParts.isEmpty()) {
						firstDepTime = part.vehicleDepTime;
					}
					this.routeParts.add(part);
					lastArrTime = part.arrivalTime;
				}
			}
			this.depTime = firstDepTime;
			this.arrTime = lastArrTime;
			this.travelTime = this.arrTime - this.depTime;
			this.transfers = this.routeParts.size() - 1;
		}

		@Override
		public boolean equals(Object o) {
			if (this == o) return true;
			if (o == null || getClass() != o.getClass()) return false;
			FoundRoute that = (FoundRoute) o;
			boolean isEqual = Double.compare(depTime, that.depTime) == 0
					&& Double.compare(arrTime, that.arrTime) == 0
					&& transfers == that.transfers
					&& Objects.equals(originZone, that.originZone)
					&& Objects.equals(destinationZone, that.destinationZone)
					&& Objects.equals(originStop.stopFacility.getId(), that.originStop.stopFacility.getId())
					&& Objects.equals(destinationStop.stopFacility.getId(), that.destinationStop.stopFacility.getId());
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
			return Objects.hash(originZone, destinationZone, depTime, arrTime, transfers);
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
