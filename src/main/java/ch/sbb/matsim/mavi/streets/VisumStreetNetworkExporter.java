package ch.sbb.matsim.mavi.streets;

import ch.sbb.matsim.config.variables.SBBModes;
import ch.sbb.matsim.config.variables.Variables;
import ch.sbb.matsim.mavi.MaviHelper;
import ch.sbb.matsim.mavi.PolylinesCreator;
import ch.sbb.matsim.mavi.counts.VisumToCounts;
import ch.sbb.matsim.mavi.visum.Visum;
import com.jacob.com.Dispatch;
import com.jacob.com.SafeArray;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.network.NetworkFactory;
import org.matsim.api.core.v01.network.Node;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.network.NetworkUtils;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.core.utils.geometry.CoordUtils;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class VisumStreetNetworkExporter {

	public static final Set<String> fullModeset = Set.of(SBBModes.CAR, SBBModes.RIDE, SBBModes.BIKE);
	public static final Set<String> modeSetWithoutBike = Set.of(SBBModes.CAR, SBBModes.RIDE);
	public static final Set<String> modeSetBikeOnly = Set.of(SBBModes.BIKE);
	private final static Logger log = LogManager.getLogger(VisumStreetNetworkExporter.class);
	private final Map<Id<Link>, String> wktLineStringPerVisumLink = new HashMap<>();
	private Scenario scenario;
	private NetworkFactory nf;

	public void run(Visum visumObject, String outputPath, int visumVersion, boolean exportCounts, boolean exportPolylines) throws IOException {

		this.scenario = ScenarioUtils.createScenario(ConfigUtils.createConfig());
		this.nf = scenario.getNetwork().getFactory();

		Dispatch net = visumObject.getComObject("Net").getDispatch();

		Dispatch filters = visumObject.getComObject("Filters").getDispatch();
		Dispatch.call(filters, "InitAll");
		if (exportCounts) {
			this.exportCountStations(visumObject, outputPath);
		}
		String[][] nodes = importNodes(net, "No", "XCoord", "YCoord", "ZCoord");
		String[][] links = importLinks(net, "FromNodeNo", "ToNodeNo", "Length", "CapPrT", "V0PrT", "TypeNo",
				"NumLanes", "TSysSet", Variables.ACCESS_CONTROLLED, "WKTPoly", "No");
		createNetwork(nodes, links);
		// Export Polylines
		if (exportPolylines) {
			new PolylinesCreator().runStreets(this.scenario.getNetwork(), wktLineStringPerVisumLink, "polylines.csv", outputPath);
		}

	}


	private void exportCountStations(Visum visum, String outputFolder) throws IOException {
		VisumToCounts visumToCounts = new VisumToCounts();

		File file = new File(outputFolder, "counts");
		File csv = new File(outputFolder, "counts.csv");
		visumToCounts.exportCountStations(visum, file.getAbsolutePath(), csv.getAbsolutePath());
	}

	public static String[][] importNodes(Dispatch net, String... attribute) {
		Dispatch nodes = Dispatch.get(net, "Nodes").toDispatch();//import nodes
		return toArray(nodes, attribute);
	}

	public static String[][] importLinks(Dispatch net, String... attribute) {
		Dispatch links = Dispatch.get(net, "Links").toDispatch();
		return toArray(links, attribute);
	}

	public static String[][] toArray(Dispatch objects, String... attributes) {
		int n = Integer.parseInt(Dispatch.call(objects, "CountActive").toString()); //number of nodes

		String[][] attarray = new String[n][attributes.length]; //2d array containing all attributes of all nodes
		int j = 0;

		for (String att : attributes) {
			log.info(att);
			SafeArray a = Dispatch.call(objects, "GetMultiAttValues", att).toSafeArray();
			int i = 0;
			while (i < n) {
				attarray[i][j] = a.getString(i, 1);
				i++;
			}
			log.info("done");
			j++;
		}
		return attarray;
	}

	private void createNetwork(String[][] attarraynode, String[][] attarraylink) {
		Network network = this.scenario.getNetwork();
		network.setCapacityPeriod(3600);
		double sumOfZCoords = Arrays.stream(attarraynode).mapToDouble(s -> Double.parseDouble(s[3])).sum();
		boolean threeDimensionalNetwork = true;
		if (sumOfZCoords < 1.) {
			threeDimensionalNetwork = false;
			log.warn("Network is two-dimensional, will not set any zcoords");
		}
		for (String[] anAttarraynode : attarraynode) {
			double x = Double.parseDouble(anAttarraynode[1]);
			double y = Double.parseDouble(anAttarraynode[2]);
			double z = Double.parseDouble(anAttarraynode[3]);
			Coord coord = threeDimensionalNetwork ? new Coord(x, y, z) : new Coord(x, y);
			Node node = nf.createNode(Id.createNodeId("C_" + anAttarraynode[0]), coord);
			network.addNode(node);
		}

		boolean carAllowed;
		boolean bikeAllowed;
		for (String[] anAttarraylink : attarraylink) {
			carAllowed = (anAttarraylink[7].contains("P"));
			bikeAllowed = (anAttarraylink[7].contains("V"));
			if (carAllowed | bikeAllowed) {
				final String fromNode = anAttarraylink[0];
				final String toNode = anAttarraylink[1];
				final String visumLinkNo = anAttarraylink[10];
				Id<Link> id = MaviHelper.createLinkId(fromNode, visumLinkNo);
				Link link = createLink(id, fromNode, toNode, Double.parseDouble(anAttarraylink[2]),
						Double.parseDouble(anAttarraylink[3]), (Double.parseDouble(anAttarraylink[4])),
						Integer.parseInt(anAttarraylink[6]));
				if (link != null) {
					NetworkUtils.setType(link, anAttarraylink[5]);
					int ac;
					try {
						ac = Integer.parseInt(anAttarraylink[8]);
					} catch (NumberFormatException e) {
						log.warn("Access Control not defined for link " + link.getId() + ". Assuming = 1");
						ac = 1;
					}
					link.getAttributes().putAttribute(Variables.ACCESS_CONTROLLED, ac);
					if (ac == 1) {
						if (carAllowed) {
							link.setAllowedModes(modeSetWithoutBike);
						}
					} else if (!carAllowed) {
						link.setAllowedModes(modeSetBikeOnly);
					} else {
						link.setAllowedModes(fullModeset);
					}
					network.addLink(link);
				}
				this.wktLineStringPerVisumLink.put(id, anAttarraylink[9]);
			}
		}
	}

	private Link createLink(Id<Link> id, String fromNode, String toNode, double length, double cap, double v, int numlanes) {
		Node fnode = scenario.getNetwork().getNodes().get(Id.createNodeId("C_" + fromNode));
		Node tnode = scenario.getNetwork().getNodes().get(Id.createNodeId("C_" + toNode));

		if (fnode == null || tnode == null) {
			return null;
		}

		Link link = nf.createLink(id, fnode, tnode);
		if (length < 0.01) {
			length = 0.01;
		}
		if (numlanes < 1) {
			numlanes = 1;
		}
		length *= 1000.;
		double beelineDistance = CoordUtils.calcEuclideanDistance(fnode.getCoord(), tnode.getCoord());
		if (length < beelineDistance) {
			length = Math.max(beelineDistance, 1.0);
		}
		link.setLength(length);
		link.setCapacity(cap);
		link.setFreespeed(v / 3.6);
		link.setNumberOfLanes(numlanes);
		link.setAllowedModes(fullModeset);

		return link;
	}

	public Network getNetwork() {
		return scenario.getNetwork();
	}
}
