package ch.sbb.matsim.mavi.streets;

import ch.sbb.matsim.csv.CSVReader;
import ch.sbb.matsim.csv.CSVWriter;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.network.Node;
import org.matsim.core.network.NetworkUtils;

import java.io.IOException;


public class AddElevationDataToVisumNodes {

    public static final String XCOORD = "XCOORD";
    public static final String YCOORD = "YCOORD";
    public static final String ZCOORD = "ZCOORD";
    public static final String NODE_NO = "$NODE:NO";
    private static final String HEADER = "$VISION\n" +
            "* Schweizerische Bundesbahnen SBB Personenverkehr Bern\n" +
            "* 06.06.23\n" +
            "* \n" +
            "* Tabelle: Versionsblock\n" +
            "* \n" +
            "* VERSNR: VERSNR, VERSNR (Versionsnummer der vorliegenden Datei.)\n" +
            "* FILETYPE: FILETYPE, FILETYPE (Typ der vorliegenden Datei.)\n" +
            "* LANGUAGE: LANGUAGE, LANGUAGE (Sprache, in der die vorliegende Datei geschrieben wurde.)\n" +
            "* UNIT: Einheiten, Längeneinheiten (Einheiten für Längen und Geschwindigkeit: entweder km, m, km/h oder mi, ft, mph (metrisch=0, imperial=1).)\n" +
            "* \n" +
            "* VERSNR\tFILETYPE\tLANGUAGE\tEinheiten\n" +
            "* VERSNR\tFILETYPE\tLANGUAGE\tLängeneinheiten\n" +
            "*\n" +
            "$VERSION:VERSNR\tFILETYPE\tLANGUAGE\tUNIT\n" +
            "12\tAtt\tENG\tKM\n" +
            "\n" +
            "* \n" +
            "* Tabelle: Knoten\n" +
            "* \n" +
            "* NO: Nr, Nummer (Nummer des Knotens.)\n" +
            "* XCOORD: XKoord, X-Koordinate (X-Koordinate.)\n" +
            "* YCOORD: YKoord, Y-Koordinate (Y-Koordinate.)\n" +
            "* ZCOORD: ZKoord, Z-Koordinate (Z-Koordinate.)\n" +
            "* \n" +
            "* Nr\tXKoord\tYKoord\tZKoord\n" +
            "* Nummer\tX-Koordinate\tY-Koordinate\tZ-Koordinate\n" +
            "*";

    public static void main(String[] args) {
        String inputNodes = args[0];
        String outputNodes = args[1];
        String elevationData = args[2];

        Network network = createNodes(inputNodes);
        ElevationDataParser.addElevationDataToNetwork(elevationData, "EPSG:2056", network);
        writeNodes(outputNodes, network);

    }

    private static void writeNodes(String outputNodes, Network network) {
        try (CSVWriter writer = new CSVWriter(HEADER, new String[]{NODE_NO, XCOORD, YCOORD, ZCOORD}, outputNodes)) {
            for (var n : network.getNodes().values()) {
                writer.set(NODE_NO, n.getId().toString());
                writer.set(XCOORD, String.valueOf(n.getCoord().getX()));
                writer.set(YCOORD, String.valueOf(n.getCoord().getY()));
                writer.set(ZCOORD, String.valueOf(n.getCoord().getZ()));
                writer.writeRow();
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static Network createNodes(String inputNodes) {
        Network network = NetworkUtils.createNetwork();
        try (CSVReader reader = new CSVReader(inputNodes, "\t")) {

            var line = reader.readLine();
            while (line != null) {
                double x = Double.parseDouble(line.get(XCOORD));
                double y = Double.parseDouble(line.get(YCOORD));
                int nodeNo = Integer.parseInt(line.get(NODE_NO));
                Node node = network.getFactory().createNode(Id.createNodeId(nodeNo), new Coord(x, y));
                network.addNode(node);
                line = reader.readLine();
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        return network;

    }
}
