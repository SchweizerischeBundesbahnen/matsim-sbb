package ch.sbb.matsim.mavi.streets;

import ch.sbb.matsim.config.variables.Variables;
import ch.sbb.matsim.counts.VisumToCounts;
import ch.sbb.matsim.csv.CSVReader;
import com.jacob.activeX.ActiveXComponent;
import com.jacob.com.Dispatch;
import com.jacob.com.DispatchProxy;
import com.jacob.com.SafeArray;
import org.apache.log4j.Logger;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.network.*;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.core.utils.geometry.CoordinateTransformation;
import org.matsim.core.utils.geometry.transformations.CH1903LV03PlustoCH1903LV03;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.*;


public class VisumStreetNetworkExporter {

    private final static Logger log = Logger.getLogger(VisumStreetNetworkExporter.class);

    private Scenario scenario;
    private NetworkFactory nf;

    public static void main(String[] args) throws IOException {
        String inputvisum = args[0];
        String outputFolder = args[1];
        int visumVersion = Integer.parseInt(args[2]);
        VisumStreetNetworkExporter exp = new VisumStreetNetworkExporter();
        exp.run(inputvisum, outputFolder, visumVersion);

    }

    public void run(String inputvisum, String outputFolder, int visumVersion) throws IOException {
        ActiveXComponent visum = new ActiveXComponent("Visum.Visum." + visumVersion);
        log.info("VISUM Client gestartet.");
        Dispatch.call(visum, "LoadVersion", inputvisum);

        this.scenario = ScenarioUtils.createScenario(ConfigUtils.createConfig());
        this.nf = scenario.getNetwork().getFactory();

        Dispatch net = Dispatch.get(visum, "Net").toDispatch();

        Dispatch filters = Dispatch.get(visum, "Filters").toDispatch();
        Dispatch.call(filters, "InitAll");

        this.exportCountStations(visum, outputFolder);

        String[][] nodes = importNodes(net, "No", "XCoord", "YCoord");
        String[][] links = importLinks(net, "FromNodeNo", "ToNodeNo", "Length", "CapPrT", "V0PrT", "TypeNo", "NumLanes", "TSysSet", "No");
        createNetwork(nodes, links);
        writeNetwork(outputFolder);

    }

    private void exportCountStations(Dispatch net, String outputFolder) throws IOException {
        VisumToCounts visumToCounts = new VisumToCounts();

        File file = new File(outputFolder, "counts.xml.gz");
        visumToCounts.exportCountStations(net, file.getAbsolutePath());
    }

    private String[][] toArray(Dispatch objects, String... attributes) {
        int n = Integer.parseInt(Dispatch.call(objects, "CountActive").toString()); //number of nodes


        String[][] attarray = new String[n][attributes.length];//2d array containing all attributes of all nodes

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

    private String[][] importNodes(Dispatch net, String... attribute) {

        Dispatch nodes = Dispatch.get(net, "Nodes").toDispatch();//import nodes


        return this.toArray(nodes, attribute);
    }

    private String[][] importLinks(Dispatch net, String... attribute) {

        Dispatch links = Dispatch.get(net, "Links").toDispatch();
        return toArray(links, attribute);
    }

    private void createNetwork(String[][] attarraynode, String[][] attarraylink) {

        Network network = this.scenario.getNetwork();
        network.setCapacityPeriod(3600);
        CoordinateTransformation transformation = new CH1903LV03PlustoCH1903LV03();

        for (int i = 0; i < attarraynode.length; i++) {
            Coord oldCoord = new Coord(Double.parseDouble(attarraynode[i][1]),
                    Double.parseDouble(attarraynode[i][2]));
            Coord newCoord = transformation.transform(oldCoord);
            Node node = nf.createNode(Id.createNodeId(attarraynode[i][0]), newCoord);
            network.addNode(node);
        }

        for (int i = 0; i < attarraylink.length; i++) {
            if (attarraylink[i][7].contains("P")) {
                //int idsupp = idOccurrence(i,attarraylink[i][8],attarraylink);
                Id<Link> id = Id.createLinkId(attarraylink[i][8] + "_" + attarraylink[i][0] + "_" + attarraylink[i][1]);
                Link link = createLink(id, attarraylink[i][0], attarraylink[i][1], Double.parseDouble(attarraylink[i][2]),
                        Double.parseDouble(attarraylink[i][3]), (Double.parseDouble(attarraylink[i][4])), Integer.parseInt(attarraylink[i][5]),
                        Integer.parseInt(attarraylink[i][6]), attarraylink[i][7]);
                if (link != null) {
                    network.addLink(link);
                }
            }
        }

    }


    private Link createLink(Id<Link> id, String fromNode, String toNode, double length, double cap, double v, int typeno, int numlanes, String modes) {

        Node fnode = scenario.getNetwork().getNodes().get(Id.createNodeId(fromNode));
        Node tnode = scenario.getNetwork().getNodes().get(Id.createNodeId(toNode));
        //if (modes.contains("P")) {
        modes = "car,ride";
        //}

        if (fnode == null || tnode == null) {
            return null;
        }
        String[] mode = modes.split(",");
        Set<String> modeset = new HashSet<>(Arrays.asList(mode));
        Link link = nf.createLink(id, fnode, tnode);
        if (length == 0.0) {
            length = 0.000001;
        }
        link.setLength(1000 * length);
        link.setCapacity(cap);
        link.setFreespeed(v / 3.6);
        //link.getAttributes().putAttribute("type",typeno);
        link.setNumberOfLanes(numlanes);
        link.setAllowedModes(modeset);

        return link;
    }

    private void writeNetwork(String outputFolder) {

        org.matsim.core.network.algorithms.NetworkCleaner cleaner = new org.matsim.core.network.algorithms.NetworkCleaner();
        cleaner.run(scenario.getNetwork());


        File file = new File(outputFolder, "network.xml.gz");
        new NetworkWriter(this.scenario.getNetwork()).write(file.getAbsolutePath());
    }

}