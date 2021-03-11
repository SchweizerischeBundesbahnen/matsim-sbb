package ch.sbb.matsim.utils;

import ch.sbb.matsim.config.variables.SBBModes;
import java.io.BufferedWriter;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.network.Node;
import org.matsim.core.api.experimental.events.EventsManager;
import org.matsim.core.events.EventsManagerImpl;
import org.matsim.core.events.MatsimEventsReader;
import org.matsim.core.network.NetworkUtils;
import org.matsim.core.network.io.MatsimNetworkReader;
import org.matsim.core.trafficmonitoring.TravelTimeCalculator;
import org.matsim.core.utils.geometry.CoordinateTransformation;
import org.matsim.core.utils.geometry.transformations.TransformationFactory;
import org.matsim.core.utils.io.IOUtils;

public class Network2OSMExporter {

    private final static double TIME = 8 * 3600;
    private final CoordinateTransformation transformation;
    private final Map<Id<Node>, Integer> nodeMapper = new HashMap<>();

    public Network2OSMExporter(String inputCRS) {
        this.transformation = TransformationFactory.getCoordinateTransformation(inputCRS, TransformationFactory.WGS84);
    }

    public static void main(String[] args) throws IOException {
        String networkFile = args[0];
        String eventsFile = args[1];
        String osmFileBelastet = args[2];
        String osmFileUnbelastet = args[3];

        new Network2OSMExporter(TransformationFactory.CH1903_LV03_Plus).run(networkFile, osmFileBelastet, osmFileUnbelastet, eventsFile);
    }

    private void run(String networkFile, String osmOutputfileCongested, String osmOutputfileUncongested, String eventsFile) throws IOException {
        Network network = NetworkUtils.createNetwork();
        new MatsimNetworkReader(network).readFile(networkFile);
        TravelTimeCalculator tc = readEvents(eventsFile, network);
        BufferedWriter bw = IOUtils.getBufferedWriter(osmOutputfileCongested);
        BufferedWriter bwUc = IOUtils.getBufferedWriter(osmOutputfileUncongested);
        writeHeader(bw, bwUc, NetworkUtils.getBoundingBox(network.getNodes().values()));
        writeNodes(bw, bwUc, network);
        writeWays(bw, bwUc, network, tc);
        closeFile(bw);
        closeFile(bwUc);
    }

    private void writeWays(BufferedWriter bw, BufferedWriter bwUc, Network network, TravelTimeCalculator tc) throws IOException {
        int linkId = 0;
        for (Link link : network.getLinks().values()) {
            if (link.getAllowedModes().contains(SBBModes.CAR)) {
                bw.write("<way id='" + linkId + "' visible='true'>\r\n");
                bw.write("<nd ref='" + nodeMapper.get(link.getFromNode().getId()) + "'/>\r\n");
                bw.write("<nd ref='" + nodeMapper.get(link.getToNode().getId()) + "'/>\r\n");
                String type = findType(link);
                bw.write("<tag k=\"highway\" v=\"" + type + "\"/>\r\n");
                double travelTime = tc.getLinkTravelTimes().getLinkTravelTime(link, TIME, null, null);
                double averageSpeed = link.getLength() / travelTime;
                bw.write("<tag k=\"maxspeed\" v=\"" + Integer.valueOf((int) (Math.round(averageSpeed * 3.6))) + "\"/>\r\n");
                bw.write("<tag k=\"freespeed_mobi\" v=\"" + Integer.valueOf((int) (Math.round(link.getFreespeed() * 3.6))) + "\"/>\r\n");
                bw.write("<tag k=\"matsimLink\" v=\"" + link.getId() + "\"/>\r\n");

                bw.write("</way>\r\n");

                bwUc.write("<way id='" + linkId + "' visible='true'>\r\n");
                bwUc.write("<nd ref='" + nodeMapper.get(link.getFromNode().getId()) + "'/>\r\n");
                bwUc.write("<nd ref='" + nodeMapper.get(link.getToNode().getId()) + "'/>\r\n");
                bwUc.write("<tag k=\"highway\" v=\"" + type + "\"/>\r\n");
                bwUc.write("<tag k=\"maxspeed\" v=\"" + Integer.valueOf((int) (Math.round(link.getFreespeed() * 3.6))) + "\"/>\r\n");
                bwUc.write("<tag k=\"matsimLink\" v=\"" + link.getId() + "\"/>\r\n");

                bwUc.write("</way>\r\n");
                linkId++;
            }
        }
    }

    private String findType(Link link) {
        if (link.getFreespeed() < 20 / 3.6) {
            return "residential";
        }
        if (link.getFreespeed() < 60 / 3.6) {
            return "tertiary";
        }
        if (link.getFreespeed() < 80 / 3.6) {
            return "secondary";
        } else {
            return "primary";
        }
    }

    private void closeFile(BufferedWriter bw) throws IOException {
        bw.write("</osm>");
        bw.flush();
        bw.close();
    }

    private void writeNodes(BufferedWriter bw, BufferedWriter bwUc, Network network) throws IOException {
        int nodeid = 0;
        for (Node n : network.getNodes().values()) {

            Coord nodeCoord = transformation.transform(n.getCoord());
            bw.write("<node id='");
            bw.write(Integer.toString(nodeid));
            bw.write("' visible='true' version='6' lat='");
            bw.write(Double.toString(nodeCoord.getY()));
            bw.write("' lon='");
            bw.write(Double.toString(nodeCoord.getX()));
            bw.write("' user='" + n.getId().toString() + "'/>\r\n");

            bwUc.write("<node id='");
            bwUc.write(Integer.toString(nodeid));
            bwUc.write("' visible='true' version='6' lat='");
            bwUc.write(Double.toString(nodeCoord.getY()));
            bwUc.write("' lon='");
            bwUc.write(Double.toString(nodeCoord.getX()));
            bwUc.write("' user='" + n.getId().toString() + "'/>\r\n");

            nodeMapper.put(n.getId(), nodeid);

            nodeid++;

        }
    }

    private void writeHeader(BufferedWriter bw, BufferedWriter bwUc, double[] boundingBox) throws IOException {
        bw.write("<?xml version='1.0' encoding='UTF-8'?>\r\n");
        bwUc.write("<?xml version='1.0' encoding='UTF-8'?>\r\n");
        bw.write("<osm version='0.6' generator='JOSM' >\r\n");
        bwUc.write("<osm version='0.6' generator='JOSM' >\r\n");
        Coord lowerLeft = transformation.transform(new Coord(boundingBox[0], boundingBox[1]));
        Coord upperRight = transformation.transform(new Coord(boundingBox[2], boundingBox[3]));
        bw.write("<bounds minlat='" + lowerLeft.getY() + "'  minlon='" + lowerLeft.getX() + "' maxlat='" + upperRight.getY() + "' maxlon='" + upperRight.getX() + "'/>\r\n");
        bwUc.write("<bounds minlat='" + lowerLeft.getY() + "'  minlon='" + lowerLeft.getX() + "' maxlat='" + upperRight.getY() + "' maxlon='" + upperRight.getX() + "'/>\r\n");
    }

    private TravelTimeCalculator readEvents(String eventsfile, Network network) {
        EventsManager manager = new EventsManagerImpl();
        TravelTimeCalculator tc = new TravelTimeCalculator.Builder(network).build();
        manager.addHandler(tc);
        new MatsimEventsReader(manager).readFile(eventsfile);
        return tc;
    }

}