package ch.sbb.matsim.analysis.LinkAnalyser.VisumNetwork;

import ch.sbb.matsim.csv.CSVWriter;
import javafx.util.Pair;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Node;
import org.matsim.core.utils.io.UncheckedIOException;

import java.io.IOException;
import java.util.HashMap;

public class VisumNetwork {
    private HashMap<Pair<Id<Node>, Id<Node>>, VisumLink> links;
    private HashMap<Id<Node>, VisumNode> nodes;

    private static final String HEADER = "$VISION\n" +
            "* Schweizerische Bundesbahnen SBB Personenverkehr Bern 65\n" +
            "* 23.03.18\n" +
            "*\n" +
            "* Tabelle: Versionsblock\n" +
            "*\n" +
            "$VERSION:VERSNR;FILETYPE;LANGUAGE;UNIT\n" +
            "10.000;Net;DEU;KM\n" +
            "\n" +
            "*\n" +
            "* Tabelle: ";

    private static final String[] NODES_COLUMNS = new String[]{
            "$KNOTEN:NR",
            "XKOORD",
            "YKOORD"
    };


    private static final String[] LINKS_COLUMNS = new String[]{
            "$STRECKE:NR",
            "VONKNOTNR",
            "NACHKNOTNR",
            "VSYSSET",
            "LAENGE",
            "NBVEHICLES",
            "CAPACITY",
            "FREESPEED",
            "MATSIMID"
    };

    public VisumNetwork() {
        links = new HashMap<>();
        nodes = new HashMap<>();
    }

    public VisumLink getOrCreateLink(Link link) {
        Pair<Id<Node>, Id<Node>> key = this.getLinkKey(link, false);
        if (!this.links.containsKey(key)) {

            final VisumNode fromNode = this.getNode(link.getFromNode());
            final VisumNode toNode = this.getNode(link.getToNode());

            final VisumLink link1 = new VisumLink(fromNode, toNode);
            final VisumLink link2 = link1.create_opposite_direction();

            this.links.put(key, link1);

            key = this.getLinkKey(link, true);
            this.links.put(key, link2);
        }
        final VisumLink visumLink = this.links.get(key);
        visumLink.setMATSimLink(link);
        return visumLink;
    }

    private VisumNode getNode(final Node node) {
        if (!this.nodes.containsKey(node.getId())) {
            final VisumNode visumNode = new VisumNode(node);
            this.nodes.put(node.getId(), visumNode);
        }
        return this.nodes.get(node.getId());
    }

    private Pair<Id<Node>, Id<Node>> getLinkKey(final Link link, final Boolean inverse) {
        final Id fromId;
        final Id toId;
        if (inverse) {
            fromId = link.getToNode().getId();
            toId = link.getFromNode().getId();
        } else {
            fromId = link.getFromNode().getId();
            toId = link.getToNode().getId();
        }
        return new Pair<Id<Node>, Id<Node>>(fromId, toId);
    }


    public void writeUserDefinedAttributes(String filename) {


        final String BENDEFATTR_NET_STRING =
                "$BENUTZERDEFATTRIBUTE:OBJID;ATTID;CODE;NAME;DATENTYP\n" +
                        "KNOTEN;MATSIMID;MATSimId;MATSimId;Text\n" +
                        "STRECKE;COUNTVALUE;countValue;countValue;Double\n" +
                        "STRECKE;MATSIMID;MATSimId;MATSimId;Text\n" +
                        "STRECKE;CAPACITY;CAPACITY;CAPACITY;Double\n" +
                        "STRECKE;FREESPEED;FREESPEED;FREESPEED;Double\n" +
                        "STRECKE;NBVEHICLES;nbVehicles;nbVehicles;Double\n";
        String[] COLUMNS = new String[]{};

        try (CSVWriter writer = new CSVWriter(HEADER+"\n"+BENDEFATTR_NET_STRING + "\n", COLUMNS, filename)) {
            writer.writeRow();

        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }


    public void writeLinks(String filename) {
        try (CSVWriter writer = new CSVWriter(HEADER + "Strecken\n", LINKS_COLUMNS, filename)) {
            for (VisumLink link : this.links.values()) {
                Link matsimLink = link.getMATSimLink();
                if (matsimLink != null) {
                    writer.set("$STRECKE:NR", link.getId().toString());
                    writer.set("VONKNOTNR", link.getFromNode().getId().toString());
                    writer.set("NACHKNOTNR", link.getToNode().getId().toString());
                    writer.set("VSYSSET", "P");
                    writer.set("LAENGE", Double.toString(matsimLink.getLength()));
                    writer.set("NBVEHICLES", Double.toString(link.getVolume()));
                    writer.set("CAPACITY", Double.toString(matsimLink.getCapacity()));
                    writer.set("FREESPEED", Double.toString(matsimLink.getFreespeed()));
                    writer.set("MATSIMID", matsimLink.getId().toString());
                    writer.writeRow();
                }
            }

        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public void write(String folder) {
        this.writeNodes(folder + "/visum_nodes.net");
        this.writeLinks(folder + "/visum_links.net");
        this.writeUserDefinedAttributes(folder + "/visum_userdefined.net");
    }


    public void writeNodes(String filename) {
        try (CSVWriter writer = new CSVWriter(HEADER + "Knoten\n", NODES_COLUMNS, filename)) {
            for (VisumNode node : this.nodes.values()) {
                writer.set("$KNOTEN:NR", node.getId().toString());
                writer.set("XKOORD", Double.toString(node.getCoord().getX()));
                writer.set("YKOORD", Double.toString(node.getCoord().getY()));
                writer.writeRow();
            }

        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
