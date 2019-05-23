package ch.sbb.matsim.mavi.pt;

import ch.sbb.matsim.csv.CSVReader;
import ch.sbb.matsim.csv.CSVWriter;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.network.io.MatsimNetworkReader;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.core.utils.misc.Counter;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * calculates correct polylines between stops for visualization.
 *
 * Input:
 * - TransitSchedule
 * - TransitNetwork
 * - CSV containing polylines for all Visum-Links
 * - CSV containing ordered list of Visum-Links for each MATSim-Link
 *
 * Output:
 * - CSV file with polyline for each MATSim-Link, to be used as link-geometries in Via
 *
 * @author mrieser
 */
public class PolylinesCreator {

    private final Network network;
    private final Map<String, double[]> polylinePerVisumLink = new HashMap<>();
    private final Map<String, String[]> linkSequencePerMatsimLink = new HashMap<>();

    public PolylinesCreator() {
        Scenario scenario = ScenarioUtils.createScenario(ConfigUtils.createConfig());
        this.network = scenario.getNetwork();
    }

    public void run(String networkFilename, String visumPolylinesFilename, String linkSequencesFilename, String matsimPolylinesFilename) throws IOException {
        new MatsimNetworkReader(this.network).readFile(networkFilename);
        loadVisumPolylines(visumPolylinesFilename);
        loadLinkSequences(linkSequencesFilename);
        processNetwork(matsimPolylinesFilename);
    }

    private void loadVisumPolylines(String visumFilename) throws IOException {
        try (CSVReader in = new CSVReader(visumFilename, ";")) {
            Map<String, String> row;
            while ((row = in.readLine()) != null) {
                String visumLinkId = row.get("LINKNO");
                String polyline = row.get("WKTPOLY");
                if (polyline != null) {
                    double[] xys = parseWktLinestring(polyline);
                    this.polylinePerVisumLink.put(visumLinkId, xys);
                }
            }
        }
    }

    private double[] parseWktLinestring(String wkt) {
        if (!wkt.startsWith("LINESTRING(")) {
            throw new RuntimeException("not a wkt linestring: " + wkt);
        }
        String coordString = wkt.substring(11, wkt.length() - 1);
        String[] coords = coordString.split(",");
        double[] xys = new double[coords.length * 2];

        for (int i = 0; i < coords.length; i++) {
            String c = coords[i];
            String[] xy = c.split(" ");
            double x = Double.parseDouble(xy[0]);
            double y = Double.parseDouble(xy[1]);
            xys[i * 2] = x;
            xys[i * 2 + 1] = y;
        }
        return xys;
    }

    private void loadLinkSequences(String linkSequencesFilename) throws IOException {
        try (CSVReader in = new CSVReader(linkSequencesFilename, ";")) {
            Map<String, String> row;
            while ((row = in.readLine()) != null) {
                String matsimLinkId = row.get("matsim_link");
                String linkSequence = row.get("link_sequence_visum");
                String[] links = linkSequence.split(", *");
                this.linkSequencePerMatsimLink.put(matsimLinkId, links);
            }
        }
    }

    private void processNetwork(String outputFilename) throws IOException {
        try (CSVWriter out = new CSVWriter(null, new String[] {"LINK", "WKT"}, outputFilename)) {
            Counter cnter = new Counter("#", " / " + this.network.getLinks().size());
            for (Link link : this.network.getLinks().values()) {
                cnter.incCounter();
                String linkId = link.getId().toString();
                String[] sequence = this.linkSequencePerMatsimLink.get(linkId);
                if (sequence != null) {
                    double[] xys = processLink(link, sequence);
                    if (xys != null && xys.length > 0) {
                        out.set("LINK", linkId);
                        out.set("WKT", xyToWkt(xys));
                        out.writeRow();
                    }
                }
            }
        }
    }

    private double[] processLink(Link link, String[] parts) {
        if (parts.length == 0) {
            return null;
        }
        if (parts.length == 1) {
            double[] xys = this.polylinePerVisumLink.get(parts[0]);
            if (xys == null) {
                return null;
            }
            return cut(link, xys);
        }
        double[] combinedXys = combine(parts);
        return cut(link, combinedXys);
    }

    private double[] combine(String[] parts) {
        double[][] partXys = new double[parts.length][];
        for (int i = 0; i < parts.length; i++) {
            partXys[i] = this.polylinePerVisumLink.get(parts[i]);
            if (i == 1) {
                // figure out if we have to reverse the first or second (or both)
                double[] xys0 = partXys[0];
                double[] xys1 = partXys[1];
                double distFlipNone = squareDistance(xys0[xys0.length - 2], xys0[xys0.length - 1], xys1[0], xys1[1]);
                double distFlip0 = squareDistance(xys0[0], xys0[1], xys1[0], xys1[1]);
                double distFlip1 = squareDistance(xys0[xys0.length - 2], xys0[xys0.length - 1], xys1[xys1.length - 2], xys1[xys1.length - 1]);
                double distFlipBoth = squareDistance(xys0[0], xys0[1], xys1[xys1.length - 2], xys1[xys1.length - 1]);

                boolean flip0 = /* distFlipBoth is best */ (distFlipBoth < distFlipNone && distFlipBoth <= distFlip0 && distFlipBoth < distFlip1)
                        /* or distFlip0 is best */ || (distFlip0 < distFlipNone && distFlip0 < distFlip1 && distFlip0 < distFlipBoth);
                boolean flip1 = /* distFlipBoth is best */ (distFlipBoth < distFlipNone && distFlipBoth < distFlip0 && distFlipBoth <= distFlip1)
                        /* or distFlip1 is best */ || (distFlip1 < distFlipNone && distFlip1 < distFlip0 && distFlip1 < distFlipBoth);


                if (flip0) {
                    reverseXyArray(xys0);
                }
                if (flip1) {
                    reverseXyArray(xys1);
                }
            }
            if (i > 1) {
                // figure out if we have to reverse the newly added part
                double[] xys0 = partXys[i - 1];
                double[] xys1 = partXys[i];
                double fromX = xys0[xys0.length - 2];
                double fromY = xys0[xys0.length - 1];
                double distNoflip = squareDistance(fromX, fromY, xys1[0], xys1[1]);
                double distFlip = squareDistance(fromX, fromY, xys1[xys1.length - 2], xys1[xys1.length - 1]);
                if (distFlip < distNoflip) {
                    reverseXyArray(xys1);
                }
            }
        }
        int count = 0;
        for (double[] xys : partXys) {
            count += xys.length;
        }
        double[] xys = new double[count];
        int idx = 0;
        double lastX = Double.NaN;
        double lastY = Double.NaN;
        for (double[] part : partXys) {
            for (int i = 0; i < part.length; i += 2) {
                double x = part[i];
                double y = part[i+1];
                if (x != lastX && y != lastY) {
                    xys[idx] = x;
                    xys[idx + 1] = y;
                    idx += 2;
                    lastX = x;
                    lastY = y;
                }
            }
        }
        if (idx == count) {
            return xys;
        }
        return Arrays.copyOfRange(xys, 0, idx);
    }

    private double[] cut(Link link, double[] xys) {
        Coord fromCoord = link.getFromNode().getCoord();
        Coord toCoord = link.getToNode().getCoord();
        int fromIdx = -1;
        int toIdx = -1;
        double minFromDist = Double.POSITIVE_INFINITY;
        double minToDist = Double.POSITIVE_INFINITY;
        for (int i = 0; i < xys.length; i += 2) {
            double x = xys[i];
            double y = xys[i+1];
            double dist = squareDistance(fromCoord.getX(), fromCoord.getY(), x, y);
            if (dist < minFromDist) {
                minFromDist = dist;
                fromIdx = i;
            }
            dist = squareDistance(toCoord.getX(), toCoord.getY(), x, y);
            if (dist < minToDist) {
                minToDist = dist;
                toIdx = i;
            }
        }
        if (fromIdx <= toIdx) {
            return Arrays.copyOfRange(xys, fromIdx, toIdx + 2);
        }
        // it's backwards, we need to reverse
        double[] range = Arrays.copyOfRange(xys, toIdx, fromIdx + 2);
        reverseXyArray(range);
        return range;
    }

    private void reverseXyArray(double[] array) {
        for (int i = 0; i < array.length / 2; i += 2) {
            double temp = array[i];
            array[i] = array[array.length - 2 - i];
            array[array.length - 2 - i] = temp;
            temp = array[i+1];
            array[i+1] = array[array.length - 1 - i];
            array[array.length - 1 - i] = temp;
        }
    }

    /**
     * Calculates the square of the distance between the given coord and (x/y).
     * This is a minor optimization, as we don't need the absolute distance, but just need
     * to find the smallest one, and the smallest distance also has the smallest square distance,
     * so we don't need to call the expensive Math.sqrt() method.
     */
    private double squareDistance(double x1, double y1, double x2, double y2) {
        double xx = x1 - x2;
        double yy = y1 - y2;
        return xx*xx + yy*yy;
    }

    private String xyToWkt(double[] xys) {
        StringBuilder wkt = new StringBuilder(128);
        wkt.append("LINESTRING(");
        for (int i = 0; i < xys.length; i += 2) {
            if (i > 0) {
                wkt.append(',');
            }
            wkt.append(xys[i]);
            wkt.append(' ');
            wkt.append(xys[i+1]);
        }
        wkt.append(')');
        return wkt.toString();
    }

    public static void main(String[] args) throws IOException {
        String networkFilename = "C:\\devsbb\\codes\\_data\\polylines\\links_geo\\transit\\transitNetwork.xml.gz";
        String visumPolylinesFilename = "C:\\devsbb\\codes\\_data\\polylines\\links_geo\\transit\\polylines.csv";
        String linkSequencesFilename = "C:\\devsbb\\codes\\_data\\polylines\\links_geo\\transit\\link_sequences.csv";
        String matsimPolylinesFilename = "C:\\devsbb\\codes\\_data\\polylines\\links_geo\\transit\\link_geometries.csv";

        new PolylinesCreator().run(networkFilename, visumPolylinesFilename, linkSequencesFilename, matsimPolylinesFilename);
    }
}
