package ch.sbb.matsim.mavi.pt;

import ch.sbb.matsim.csv.CSVWriter;
import ch.sbb.matsim.mavi.visum.Visum;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.network.NetworkFactory;
import org.matsim.api.core.v01.network.Node;
import org.matsim.core.population.routes.NetworkRoute;
import org.matsim.core.population.routes.RouteUtils;
import org.matsim.core.utils.collections.Tuple;
import org.matsim.pt.transitSchedule.api.*;
import org.matsim.utils.objectattributes.attributable.Attributes;
import org.matsim.vehicles.*;
import org.matsim.vehicles.VehicleType.DoorOperationMode;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static ch.sbb.matsim.mavi.streets.VisumStreetNetworkExporter.createLinkId;
import static ch.sbb.matsim.mavi.streets.VisumStreetNetworkExporter.extractVisumNodeAndLinkId;

public class TimeProfileExporter {

	private static final Logger log = LogManager.getLogger(TimeProfileExporter.class);

	private final NetworkFactory networkBuilder;
	private final TransitScheduleFactory scheduleBuilder;
	private final Vehicles vehicles;
	private final VehiclesFactory vehicleBuilder;
	public final Map<Id<Link>, String> linkToVisumSequence = new HashMap<>();
	private final Network network;
	private final TransitSchedule schedule;

	public TimeProfileExporter(Scenario scenario) {
		this.network = scenario.getNetwork();
		this.schedule = scenario.getTransitSchedule();
		this.vehicles = scenario.getVehicles();
		this.networkBuilder = this.network.getFactory();
		this.scheduleBuilder = this.schedule.getFactory();
		this.vehicleBuilder = scenario.getVehicles().getFactory();
	}

	private void createLink(Id<Link> linkId, Node fromNode, Node toNode, String mode, double length, String visumLinkSequence) {
		Link link = this.networkBuilder.createLink(linkId, fromNode, toNode);
		link.setLength(length * 1000);
		link.setFreespeed(10000);
		link.setCapacity(10000);
		link.setNumberOfLanes(10000);
		link.setAllowedModes(Collections.singleton(mode));
		link.getAttributes().putAttribute("visum_link_sequence", visumLinkSequence);
		this.network.addLink(link);
	}

	private static HashMap<Integer, TimeProfile> loadTimeProfileInfos(Visum visum, VisumPtExporterConfigGroup config) {
		HashMap<Integer, TimeProfile> timeProfileMap = new HashMap<>();

		log.info("loading LineRouteItems");
		// line route items
		Visum.ComObject lineRouteItems = visum.getNetObject("LineRouteItems");
		if (lineRouteItems == null) {
			throw new NullPointerException("could not get LineRouteItems");
		}
		Map<String, List<LineRouteItem>> lrItemsPerLineRoute = new HashMap<>();
		int nrOfLRItems = lineRouteItems.countActive();
		String[][] lineRouteItemAttributes = Visum.getArrayFromAttributeList(nrOfLRItems, lineRouteItems,
				"LineName", "LineRoute\\Name", "DirectionCode", "Index", "Node\\No", "OutLink\\No");
		for (int i = 0; i < nrOfLRItems; i++) {
			String[] row = lineRouteItemAttributes[i];
			String lrKey = row[0] + "||" + row[1] + "||" + row[2];
			lrItemsPerLineRoute.computeIfAbsent(lrKey, k -> new ArrayList<>()).add(new LineRouteItem(Integer.parseInt(row[3]), row[4], row[5]));
		}
		log.info("sorting LineRouteItems");
		lrItemsPerLineRoute.forEach((key, list) -> list.sort(Comparator.comparingInt(o -> o.index)));

		log.info("loading TimeProfiles");
		// time profiles
		Visum.ComObject timeProfiles = visum.getNetObject("TimeProfiles");
		int nrOfTimeProfiles = timeProfiles.countActive();
		String[][] timeProfileAttributes = Visum.getArrayFromAttributeList(nrOfTimeProfiles, timeProfiles,
				"ID", "LineName", "LineRoute\\Line\\Datenherkunft", "TSysCode",
				"LineRoute\\Line\\TSys\\TSys_MOBi");
		String[][] customAttributes = Visum.getArrayFromAttributeList(nrOfTimeProfiles, timeProfiles,
				config.getRouteAttributeParams().values().stream().
						map(VisumPtExporterConfigGroup.RouteAttributeParams::getAttributeValue).
						toArray(String[]::new));

		for (int tp = 0; tp < nrOfTimeProfiles; tp++) {
			timeProfileMap.put((int) Double.parseDouble(timeProfileAttributes[tp][0]),
					new TimeProfile(timeProfileAttributes[tp][1],
							timeProfileAttributes[tp][2],
							timeProfileAttributes[tp][3],
							timeProfileAttributes[tp][4],
							customAttributes[tp]));
		}

		log.info("loading VehicleJourneys");
		// vehicles journeys
		Visum.ComObject vehJourneys = visum.getNetObject("VehicleJourneys");
		int nrOfVehJourneys = vehJourneys.countActive();
		String[][] vehJourneyAttributes = Visum.getArrayFromAttributeList(nrOfVehJourneys, vehJourneys,
				"TimeProfile\\ID", "No", "FromTProfItemIndex", "ToTProfItemIndex", "Dep", "VehCapacity", "StandingRoom");
		for (int vj = 0; vj < nrOfVehJourneys; vj++) {
			TimeProfile tp = timeProfileMap.get((int) Double.parseDouble(vehJourneyAttributes[vj][0]));
			if (tp == null) {
				log.error((int) Double.parseDouble(vehJourneyAttributes[vj][0]));
				throw new NullPointerException();
			}
			tp.addVehicleJourney(new VehicleJourney((int) Double.parseDouble(vehJourneyAttributes[vj][1]),
					(int) Double.parseDouble(vehJourneyAttributes[vj][2]),
					(int) Double.parseDouble(vehJourneyAttributes[vj][3]),
					Double.parseDouble(vehJourneyAttributes[vj][4]),
					(int) Double.parseDouble(vehJourneyAttributes[vj][5].isEmpty() ? "-1" : vehJourneyAttributes[vj][5]),
					(int) Double.parseDouble(vehJourneyAttributes[vj][6].isEmpty() ? "-1" : vehJourneyAttributes[vj][6])));
		}

		// time profile items
		Visum.ComObject timeProfileItems = visum.getNetObject("TimeProfileItems");
		int nrOfTimeProfileItems = timeProfileItems.countActive();
		String[][] timeProfileItemAttributes = Visum.getArrayFromAttributeList(nrOfTimeProfileItems, timeProfileItems,
				"TimeProfile\\ID", "Index", "LineRouteItem\\StopPointNo", "Dep", "Arr", "PostLength",
				"LineName", "LineRouteName", "DirectionCode", "LRITEMINDEX");
		int lastLRItemIndex = 0;
		for (int tpi = 0; tpi < nrOfTimeProfileItems; tpi++) {
			String[] row = timeProfileItemAttributes[tpi];
			String visumLinkSequence = "";
			String lineRouteKey = row[6] + "||" + row[7] + "||" + row[8];
			List<LineRouteItem> tpLineRouteItems = lrItemsPerLineRoute.get(lineRouteKey);
			if (tpLineRouteItems == null) {
				log.error("Could not find line route items for " + lineRouteKey);
			} else {
				int thisLRItemIndex = Integer.parseInt(row[9]);
				boolean useIt = false;
				StringBuilder seq = new StringBuilder();
				String lastLink = null;
				for (LineRouteItem lri : tpLineRouteItems) {
					if (!useIt && lri.index >= lastLRItemIndex) {
						useIt = true;
					}
					if (useIt) {
						if (!lri.outLink.equals(lastLink) && !lri.outLink.isBlank() && !lri.node.isBlank()) {
							if (seq.length() > 0) {
								seq.append(',');
							}
							seq.append(createLinkId(lri.node, lri.outLink).toString());
							lastLink = lri.outLink;
						}
					}
					if (useIt && lri.index >= thisLRItemIndex) {
						break;
					}
				}
				visumLinkSequence = seq.toString();
				lastLRItemIndex = thisLRItemIndex;
			}

			TimeProfile tp = timeProfileMap.get((int) Double.parseDouble(row[0]));
			try {
				tp.addTimeProfileItem(new TimeProfileItem((int) Double.parseDouble(row[1]),
						(int) Double.parseDouble(row[2]),
						Double.parseDouble(row[3]),
						Double.parseDouble(row[4]),
						Double.parseDouble(row[5]),
						visumLinkSequence));
			} catch (Exception e) {
				LogManager.getLogger(TimeProfileExporter.class).error(" Could not add TPI for row " + Arrays.stream(row).toList());
				e.printStackTrace();
			}
		}

		return timeProfileMap;
	}

	private static void addAttribute(Attributes attributes, String name, String value, String dataType) {
		if (!value.isEmpty() && !value.equals("null")) {
			switch (dataType) {
				case "java.lang.String" -> attributes.putAttribute(name, value);
				case "java.lang.Double" -> attributes.putAttribute(name, Double.parseDouble(value));
				case "java.lang.Integer" -> attributes.putAttribute(name, (int) Double.parseDouble(value));
				default -> throw new IllegalArgumentException(dataType);
			}
        }
    }

    public void writeLinkSequence(String outputfolder, Network network) {
        try (CSVWriter writer = new CSVWriter("", new String[]{"matsim_link", "link_sequence_visum", "fromnode_sequence_visum"},
                outputfolder + "/link_sequences.csv")) {
            for (Map.Entry<Id<Link>, String> entry : this.linkToVisumSequence.entrySet()) {
				Id<Link> matsimLinkId = entry.getKey();
				String visumLinkSequence = entry.getValue();
				if (visumLinkSequence == null || visumLinkSequence.isEmpty()) {
					visumLinkSequence = "-1_-1";  // parseable integers representing null
				}
				if (network.getLinks().containsKey(matsimLinkId)) {
                    writer.set("matsim_link", matsimLinkId.toString());
					List<Tuple<Integer, Integer>> visumFromNodeToLinkTuples =
							Arrays.stream(visumLinkSequence.split(","))
									.map(s -> extractVisumNodeAndLinkId(Id.createLinkId(s)))
									.filter(Objects::nonNull)
									.toList();
					String fromNodeSequence = visumFromNodeToLinkTuples.stream().map(e -> String.valueOf(e.getFirst())).collect(Collectors.joining(","));
					String linkSequence = visumFromNodeToLinkTuples.stream().map(e -> String.valueOf(e.getSecond())).collect(Collectors.joining(","));
					writer.set("fromnode_sequence_visum", fromNodeSequence);
					writer.set("link_sequence_visum", linkSequence);
                    writer.writeRow();
                }
            }
        } catch (IOException e) {
            log.error("Could not write file. " + e.getMessage(), e);
		}
	}

	public void createTransitLines(Visum visum, VisumPtExporterConfigGroup config) {
		log.info("Loading all informations about transit lines...");
		HashMap<Integer, TimeProfile> timeProfileMap = loadTimeProfileInfos(visum, config);
		log.info("finished loading all informations for transit lines...");

		for (Map.Entry<Integer, TimeProfile> entrySet : timeProfileMap.entrySet()) {
			int tpId = entrySet.getKey();
			TimeProfile tp = entrySet.getValue();

			String lineName = tp.lineName;
			Id<TransitLine> lineID = Id.create(lineName, TransitLine.class);
			TransitLine line = this.schedule.getTransitLines().get(lineID);
			if (line == null) {
				line = this.scheduleBuilder.createTransitLine(lineID);
				this.schedule.addTransitLine(line);
			}

			String mode = tp.tSysMOBi.toLowerCase();
			final TransitLine finalLine = line;
			tp.vehicleJourneys.forEach(vj -> {
				int from_tp_index = vj.fromTProfItemIndex;
				int to_tp_index = vj.toTProfItemIndex;
				Id<TransitRoute> routeID = Id.create(tpId + "_" + from_tp_index + "_" + to_tp_index, TransitRoute.class);
				TransitRoute route = finalLine.getRoutes().get(routeID);

				if (route == null) {
					// Fahrzeitprofil-Verläufe
					List<TransitRouteStop> transitRouteStops = new ArrayList<>();
					List<Id<Link>> routeLinks = new ArrayList<>();
					Id<Link> startLink = null;
					Id<Link> endLink = null;
					TransitStopFacility fromStop = null;
					double postlength = 0.0;
					double delta = 0.0;
					boolean isFirstRouteStop = true;

					for (TimeProfileItem tpi : tp.timeProfileItems) {
						int stopPointNo = tpi.stopPoint;

						int index = tpi.index;
						if (from_tp_index > index || to_tp_index < index) {
							continue;
						} else if (from_tp_index == index) {
							startLink = Id.createLinkId(config.getNetworkMode() + "_" + stopPointNo);
							delta = tpi.dep;
						} else if (to_tp_index == index) {
							endLink = Id.createLinkId(config.getNetworkMode() + "_" + stopPointNo);
						}

						Id<TransitStopFacility> stopID = Id.create(stopPointNo, TransitStopFacility.class);
						TransitStopFacility stop = this.schedule.getFacilities().get(stopID);

						double arrTime = tpi.arr;
						double depTime = tpi.dep;
						TransitRouteStop rst;
						if (isFirstRouteStop) {
							rst = this.scheduleBuilder.createTransitRouteStopBuilder(stop).departureOffset(depTime - delta).build();
							isFirstRouteStop = false;
						} else {
							rst = this.scheduleBuilder.createTransitRouteStopBuilder(stop).arrivalOffset(arrTime - delta).departureOffset(depTime - delta).build();
						}
						rst.setAwaitDepartureTime(true);
						transitRouteStops.add(rst);

						if (fromStop != null) {
							// non-routed links (fly from stop to stop)
							Node fromNode = this.network.getLinks().get(fromStop.getLinkId()).getFromNode();
							Node toNode = this.network.getLinks().get(stop.getLinkId()).getFromNode();
							Id<Link> newLinkID = Id.createLinkId(fromNode.getId().toString() + "-" + toNode.getId().toString());
							if (!this.network.getLinks().containsKey(newLinkID)) {
								createLink(newLinkID, fromNode, toNode, mode, postlength, tpi.visumLinkSequence);
								this.linkToVisumSequence.put(newLinkID, tpi.visumLinkSequence);
							}
							// differentiate between links with the same from- and to-node but different length
							else {
								boolean hasSameLinkSequence = false;
								if (!this.linkToVisumSequence.get(newLinkID).equals(tpi.visumLinkSequence)) {
									int m = 1;
									Id<Link> linkID = Id.createLinkId(fromNode.getId().toString() + "-" + toNode.getId().toString() + "." + m);
									while (this.network.getLinks().containsKey(linkID)) {
										if (this.linkToVisumSequence.get(linkID).equals(tpi.visumLinkSequence)) {
											hasSameLinkSequence = true;
											newLinkID = linkID;
											Link link = this.network.getLinks().get(newLinkID);
											Set<String> allowedModesOld = link.getAllowedModes();
											if (!allowedModesOld.contains(mode)) {
												Set<String> allowedModesNew = new HashSet<>(allowedModesOld);
												allowedModesNew.add(mode);
												link.setAllowedModes(allowedModesNew);
											}
											break;
										}
										m++;
										linkID = Id.createLinkId(fromNode.getId().toString() + "-" + toNode.getId().toString() + "." + m);

									}
									if (!hasSameLinkSequence) {
										createLink(linkID, fromNode, toNode, mode, postlength, tpi.visumLinkSequence);
										this.linkToVisumSequence.put(linkID, tpi.visumLinkSequence);
										newLinkID = linkID;
									}
								}
							}
							routeLinks.add(newLinkID);
							routeLinks.add(stop.getLinkId());
						}
						postlength = tpi.length;
						fromStop = stop;
					}
					if (routeLinks.size() > 0) {
						routeLinks.remove(routeLinks.size() - 1);
					} else {
						LogManager.getLogger(getClass()).warn(routeID + " has no links along route");
					}
					NetworkRoute netRoute = RouteUtils.createLinkNetworkRouteImpl(startLink, endLink);
					netRoute.setLinkIds(startLink, routeLinks, endLink);

					route = this.scheduleBuilder.createTransitRoute(routeID, netRoute, transitRouteStops, mode);

					finalLine.addRoute(route);
				}

				int depName = vj.no;
				Id<Departure> depID = Id.create(depName, Departure.class);
				double depTime = vj.dep;
				Departure dep = this.scheduleBuilder.createDeparture(depID, depTime);

				Id<Vehicle> vehicleId = Id.createVehicleId(depID.toString());
				dep.setVehicleId(vehicleId);
				route.addDeparture(dep);

				String[] values = tp.customAttributes;
				List<VisumPtExporterConfigGroup.RouteAttributeParams> custAttNames = new ArrayList<>(config.getRouteAttributeParams().values());
				Attributes routeAttributes = route.getAttributes();
				IntStream.range(0, values.length).forEach(j -> addAttribute(routeAttributes, custAttNames.get(j).getAttributeName(),
						values[j], custAttNames.get(j).getDataType()));

				VehicleType vehType = getVehicleType(tp.tSysCode, vj.vehCapacity, vj.standingRoom);
				Vehicle vehicle = this.vehicleBuilder.createVehicle(vehicleId, vehType);
				this.vehicles.addVehicle(vehicle);
			});
		}
		log.info("Loading transit routes finished");
	}

	private static class LineRouteItem {

		final int index;
		final String node;
		final String outLink;

		public LineRouteItem(int index, String node, String outLink) {
			this.index = index;
			this.node = node;
			this.outLink = outLink;
		}
	}

	private VehicleType getVehicleType(String tSysCode, int capacity, int standingRoom) {
		Id<VehicleType> vehicleTypeId = Id.create(tSysCode + "_" + capacity + "_" + standingRoom, VehicleType.class);
		VehicleType vehType = this.vehicles.getVehicleTypes().get(vehicleTypeId);
		if (vehType == null) {
			vehType = this.vehicleBuilder.createVehicleType(vehicleTypeId);
			vehType.setDescription(tSysCode);
			VehicleUtils.setDoorOperationMode(vehType, DoorOperationMode.serial);
			VehicleCapacity vehicleCapacity = vehType.getCapacity();
			if (capacity < 0) {
				vehicleCapacity.setSeats(150); // default in case of missing value
			} else {
				vehicleCapacity.setSeats(capacity);
			}
			if (standingRoom < 0) {
				vehicleCapacity.setStandingRoom(50); // default in case of missing value
			} else {
				vehicleCapacity.setStandingRoom(standingRoom);
			}
			if (capacity == 0 && standingRoom == 0) {
				log.warn("There exists a vehicle type with capacity and standingRoom both = 0. tSysCode = " + tSysCode);
			}

			// the following parameters do not have any influence in a deterministic simulation engine
			vehType.setLength(10);
			vehType.setWidth(2);
			vehType.setPcuEquivalents(1);
			vehType.setMaximumVelocity(10000);
			this.vehicles.addVehicleType(vehType);
		}
		return vehType;
	}

	private static class TimeProfile {

		final String lineName;
		final String datenHerkunft;
		final String tSysCode;
		final String tSysMOBi;
		final ArrayList<VehicleJourney> vehicleJourneys;
		final ArrayList<TimeProfileItem> timeProfileItems;
		final String[] customAttributes;

		public TimeProfile(String lineName, String datenHerkunft, String tSysCode, String tSysMOBi, String[] customAttributes) {
			this.lineName = lineName;
			this.datenHerkunft = datenHerkunft;
			this.tSysCode = tSysCode;
			this.tSysMOBi = tSysMOBi;
			this.customAttributes = customAttributes;
			this.vehicleJourneys = new ArrayList<>();
			this.timeProfileItems = new ArrayList<>();
		}

		public void addVehicleJourney(VehicleJourney vj) {
			this.vehicleJourneys.add(vj);
		}

		public void addTimeProfileItem(TimeProfileItem tpi) {
			this.timeProfileItems.add(tpi);
		}
	}

	private static class VehicleJourney {

		final int no;
		final int fromTProfItemIndex;
		final int toTProfItemIndex;
		final double dep;
		final int vehCapacity;
		final int standingRoom;

		public VehicleJourney(int no, int fromTProfItemIndex, int toTProfItemIndex, double dep, int vehCapacity, int standingRoom) {
			this.no = no;
			this.fromTProfItemIndex = fromTProfItemIndex;
			this.toTProfItemIndex = toTProfItemIndex;
			this.dep = dep;
			this.vehCapacity = vehCapacity;
			this.standingRoom = standingRoom;
		}
	}

	private static class TimeProfileItem {

		final int index;
		final int stopPoint;
		final double dep;
		final double arr;
		final double length;
		final String visumLinkSequence;

		public TimeProfileItem(int index, int stopPoint, double dep, double arr, double length, String visumLinkSequence) {
			this.index = index;
			this.stopPoint = stopPoint;
			this.dep = dep;
			this.arr = arr;
			this.length = length;
			this.visumLinkSequence = visumLinkSequence;
		}
	}
}