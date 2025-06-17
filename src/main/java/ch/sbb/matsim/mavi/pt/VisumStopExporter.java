package ch.sbb.matsim.mavi.pt;

import ch.sbb.matsim.config.variables.SBBModes;
import ch.sbb.matsim.mavi.MaviHelper;
import ch.sbb.matsim.mavi.visum.VisumDataSource;
import ch.sbb.matsim.mavi.visum.VisumDataSource.DataTable;
import java.util.stream.Collectors;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.network.NetworkFactory;
import org.matsim.api.core.v01.network.Node;
import org.matsim.pt.transitSchedule.api.TransitSchedule;
import org.matsim.pt.transitSchedule.api.TransitScheduleFactory;
import org.matsim.pt.transitSchedule.api.TransitStopFacility;
import org.matsim.utils.objectattributes.attributable.Attributes;

import java.util.*;
import java.util.stream.IntStream;

public class VisumStopExporter {

	private static final Logger log = LogManager.getLogger(VisumStopExporter.class);

	private final Network network;
	private final TransitSchedule schedule;
	private final NetworkFactory networkBuilder;
	private final TransitScheduleFactory scheduleBuilder;

	private final HashMap<Integer, Set<Id<TransitStopFacility>>> stopAreasToStopPoints = new HashMap<>();

	public VisumStopExporter(Scenario scenario) {
		this.network = scenario.getNetwork();
		this.schedule = scenario.getTransitSchedule();
		this.networkBuilder = this.network.getFactory();
		this.scheduleBuilder = this.schedule.getFactory();
	}

	private static void addAttribute(Attributes attributes, String name, String value, String dataType) {
		if (!value.isEmpty() && !value.equals("null")) {
			switch (dataType) {
				case "java.lang.String":
				case "java.lang.Boolean":
					attributes.putAttribute(name, value);
					break;
				case "java.lang.Double":
					attributes.putAttribute(name, Double.parseDouble(value));
					break;
				case "java.lang.Integer":
					attributes.putAttribute(name, (int) Double.parseDouble(value));
					break;

				default:
					throw new IllegalArgumentException(dataType);
			}
		}
	}


	public void loadStopPoints(VisumDataSource visum, VisumPtExporterConfigGroup config) {
		DataTable stopPoints = visum.getTable("StopPoints");
		int nrOfStopPoints = stopPoints.size();
		log.info("loading " + nrOfStopPoints + " stop points");

		String[][] stopPointAttributes = stopPoints.attrs(
			"No", "Name", "NodeNo", "StopAreaNo");
		Map<String, Coord> nodeCoordMap = getNodeCoordMap(visum);

		String[] customAttributeNames = config.getStopAttributeParams().values().stream().
			map(VisumPtExporterConfigGroup.StopAttributeParams::getAttributeValue).
			toArray(String[]::new);
		String[][] customAttributes = stopPoints.attrs(customAttributeNames);

		IntStream.range(0, nrOfStopPoints).forEach(m -> createStopPoint(m, nodeCoordMap, stopPointAttributes, customAttributes, config));

		log.info("finished loading " + nrOfStopPoints + " stop points");
	}

	private void createStopPoint(int i, Map<String, Coord> nodeCoordMap, String[][] stopPointAttributes, String[][] customAttributes, VisumPtExporterConfigGroup config) {
		int stopPointNo = (int) Double.parseDouble(stopPointAttributes[i][0]);
		Id<TransitStopFacility> stopPointID = Id.create(stopPointNo, TransitStopFacility.class);

		String stopPointName = stopPointAttributes[i][1];
		String nodeNo = stopPointAttributes[i][2];
		Coord stopPointCoord = nodeCoordMap.get(nodeNo);

		Node stopNode;
		if (!nodeNo.isEmpty()) {
			int stopNodeIDNo = (int) Double.parseDouble(nodeNo);
			Id<Node> stopNodeID = MaviHelper.createPtNodeId(String.valueOf(stopNodeIDNo));
			stopNode = this.networkBuilder.createNode(stopNodeID, stopPointCoord);
			if (!this.network.getNodes().containsKey(stopNodeID)) {
				this.network.addNode(stopNode);
			}
		} else {
			throw new RuntimeException("Convert Stops to Nodes before converting (One click within Visum)");
		}

		Id<Link> loopLinkID = Id.createLinkId(config.getNetworkMode() + "_" + stopPointNo);
		Link loopLink = this.networkBuilder.createLink(loopLinkID, stopNode, stopNode);
		loopLink.setLength(0.0);
		loopLink.setFreespeed(10000);
		loopLink.setCapacity(10000);
		loopLink.setNumberOfLanes(10000);
        Set<String> allowedModes = new HashSet<>();
        allowedModes.add(config.getNetworkMode());
        allowedModes.add(SBBModes.PT);
        loopLink.setAllowedModes(allowedModes);
		this.network.addLink(loopLink);

		int stopAreaNo = (int) Double.parseDouble(stopPointAttributes[i][3]);
		Set<Id<TransitStopFacility>> set = this.stopAreasToStopPoints.computeIfAbsent(stopAreaNo, k -> new HashSet<>());
		set.add(stopPointID);

		// create transitStopFacility
		TransitStopFacility st = this.scheduleBuilder.createTransitStopFacility(stopPointID, stopPointCoord, false);
		st.setName(stopPointName);
		st.setLinkId(loopLinkID);

		String[] values = customAttributes[i];
		List<VisumPtExporterConfigGroup.StopAttributeParams> custAttNames = new ArrayList<>(config.getStopAttributeParams().values());
		IntStream.range(0, values.length).forEach(j -> addAttribute(st.getAttributes(), custAttNames.get(j).getAttributeName(),
				values[j], custAttNames.get(j).getDataType()));

		this.schedule.addStopFacility(st);
	}

	public static Map<String, Coord> getNodeCoordMap(VisumDataSource visum) {
		DataTable nodes = visum.getTable("Nodes");
		String[][] nodeCoords = nodes.attrs(
			"No", "XCoord", "YCoord");
		Map<String, Coord> stopCoordMap = IntStream.range(0, nodeCoords.length)
			.boxed()
			.collect(Collectors.toMap(
				i -> nodeCoords[i][0],
				i -> new Coord(
					Double.parseDouble(nodeCoords[i][1]),
					Double.parseDouble(nodeCoords[i][2])
				),
				(existing, replacement) -> existing
			));
		return stopCoordMap;
	}

	public HashMap<Integer, Set<Id<TransitStopFacility>>> getStopAreasToStopPoints() {
		return stopAreasToStopPoints;
	}
}
