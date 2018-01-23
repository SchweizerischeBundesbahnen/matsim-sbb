package ch.sbb.matsim.analysis;

import javafx.util.Pair;
import org.apache.log4j.Logger;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.network.Node;
import org.matsim.core.config.groups.QSimConfigGroup;
import org.matsim.core.events.algorithms.EventWriter;
import org.matsim.core.utils.io.IOUtils;
import org.matsim.pt.transitSchedule.api.TransitSchedule;
import org.matsim.pt.transitSchedule.api.TransitStopFacility;
import ch.sbb.matsim.config.PostProcessingConfigGroup;
import ch.sbb.matsim.csv.CSVReader;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class NetworkToVisumNetFile implements EventWriter {

    private int iteration;
    private String filename;

    private final Scenario scenario;
    private final PostProcessingConfigGroup ppConfig;
    private static final String HEADER =
            "$VISION\n" +
            "* Schweizerische Bundesbahnen AG Personenverkehr Bern 65\n" +
            "* 10.07.17\n" +
            "* \n" +
            "* Tabelle: Versionsblock\n" +
            "* \n" +
            "$VERSION:VERSNR;FILETYPE;LANGUAGE;UNIT\n" +
            "10.000;Net;DEU;KM\n";

    private static final String BENDEFATTR_NET_STRING =
            "$BENUTZERDEFATTRIBUTE:OBJID;ATTID;CODE;NAME;DATENTYP\n" +
                    "KNOTEN;MATSIMID;MATSimId;MATSimId;Text\n" +
                    "STRECKE;COUNTVALUE;countValue;countValue;Double\n" +
                    "STRECKE;MATSIMID;MATSimId;MATSimId;Text\n" +
                    "STRECKE;NBVEHICLES;nbVehicles;nbVehicles;Double\n" +
                    "STRECKE;NBPASSENGERS;nbPassengers;nbPassengers;Double\n" +
                    "HALTEPUNKT;COUNTVALUEALIGHTING;countValueAlighting;countValueAlighting;Double\n" +
                    "HALTEPUNKT;COUNTVALUEBOARDING;countValueBoarding;countValueBoarding;Double\n" +
                    "HALTEPUNKT;MATSIMID;MATSimId;MATSimId;Text\n" +
                    "HALTEPUNKT;MATSIMNAME;MATSimName;MATSimName;Text\n" +
                    "HALTEPUNKT;NBPERSONSALIGHTING;nbPersonsAlighting;nbPersonsAlighting;Double\n" +
                    "HALTEPUNKT;NBPERSONSBOARDING;nbPersonsBoarding;nbPersonsBoarding;Double\n";

    private static final String VSY_NET_STRING =
            "$VSYS:CODE;NAME;TYP;PKWE;SBAREAKTIONSZEIT;SBAEFFFAHRZEUGLAENGE;SBAMAXWARTEZEIT;HATFESTENBUCHUNGSZEITRAUM;MITTLERETAEGLICHEENTLEIHDAUERJEFAHRZEUG;MITTLERETAEGLICHEANZAHLENTLEIHENJEFAHRZEUG;RUECKGABENURANENTLEIHSTATION;ISTSTATIONSGEBUNDEN;ERLAUBTUMSETZFAHRTEN;MAXANZUMGESETZTERFZGPROSTUNDE;HATDEPOT;ANZAHLFAHRZEUGEIMNETZ;BGRAD\n" +
            "car;car;IV;1;1.2s;7m;120s;0;0s;0;0;1;0;0;0;0;0\n" +
            "pt;pt;OV;1;1.2s;7m;120s;0;0s;0;0;1;0;0;0;0;0\n" +
            "ride;ride;IV;1;1.2s;7m;120s;0;0s;0;0;1;0;0;0;0;0\n" +
            "walk;walk;OVFuss;1;1.2s;7m;120s;0;0s;0;0;1;0;0;0;0;0\n";

    private static final String MODUS_NET_STRING =
            "$MODUS:CODE;VSYSSET\n" +
                    "car;car\n" +
                    "pt;pt\n" +
                    "ride;ride\n";

    private static final String NACHFRAGESEGMENT_NET_STRING =
            "$NACHFRAGESEGMENT:CODE;MODUS\n" +
                    "car;car\n" +
                    "pt;pt\n" +
                    "ride;ride\n";

    private final static Logger log = Logger.getLogger(NetworkToVisumNetFile.class);

    public NetworkToVisumNetFile(Scenario scenario, String filename, PostProcessingConfigGroup ppConfig) {
        this.filename = filename;
        this.scenario = scenario;
        this.ppConfig = ppConfig;
    }

    public void write() {
        log.info("start preprocessing to visum-net-file");
        Network network = scenario.getNetwork();
        TransitSchedule schedule = scenario.getTransitSchedule();
        double scaleFactor = 1.0 / ((QSimConfigGroup) scenario.getConfig().getModule(QSimConfigGroup.GROUP_NAME)).getFlowCapFactor();

        // read count data per stop
        Map<TransitStopFacility, Double> countAlightingsPerStop = new HashMap<>();
        Map<TransitStopFacility, Double> countBoardingsPerStop = new HashMap<>();

        if (ppConfig.getStopCountDataFile() != null) {
            File stopCountData = new File(ppConfig.getStopCountDataFile());

            if (stopCountData.isFile()) {
                readAlightingBoardingDataPerStop(ppConfig.getStopCountDataFile(), schedule, countAlightingsPerStop, countBoardingsPerStop, 1.0);
            } else {
                log.warn("The stop file defined in the config is not valid. Leaving this column empty.");
            }
        } else {
            log.info("No stop count data file specified. Leaving this column empty.");
        }

        Map<TransitStopFacility, Double> nbAlightingsPerStop = new HashMap<>();
        Map<TransitStopFacility, Double> nbBoardingsPerStop = new HashMap<>();
        if (ppConfig.getPtVolumes()) {
            readAlightingBoardingDataPerStop(this.filename + PtVolumeToCSV.FILENAME_STOPS, schedule, nbAlightingsPerStop, nbBoardingsPerStop, scaleFactor);
        }

        // read count data per link
        Map<Link, Double> countVehiclesPerLink = new HashMap<>();

        if (ppConfig.getLinkCountDataFile() != null) {
            File linkCountData = new File(ppConfig.getLinkCountDataFile());

            if (linkCountData.isFile()) {
                readVolumeDataPerLink(ppConfig.getLinkCountDataFile(), network, countVehiclesPerLink, 1.0);
            } else {
                log.warn("The link file defined in the config is not valid. Leaving this column empty.");
            }
        } else {
            log.info("No link count data file specified. Leaving this column empty.");
        }

        // read number of vehicles and number of pt passengers per link
        Map<Link, Double> nbVehiclesPerLink = new HashMap<>();
        Map<Link, Double> nbPassengersPerLink = new HashMap<>();
        if (ppConfig.getLinkVolumes()) {
            readVolumeDataPerLink(this.filename + LinkVolumeToCSV.FILENAME_VOLUMES, network, nbVehiclesPerLink, scaleFactor);
            readPassengerVolumeDataPerLink(this.filename + LinkVolumeToCSV.FILENAME_VOLUMES, network, nbPassengersPerLink, scaleFactor);
        }
        try {
            BufferedWriter writer = IOUtils.getBufferedWriter(this.filename + "net.net");
            writer.write(HEADER);
            writer.write(BENDEFATTR_NET_STRING);
            writer.write(VSY_NET_STRING);
            writer.write(MODUS_NET_STRING);
            writer.write(NACHFRAGESEGMENT_NET_STRING);

            log.info("start writing nodes to visum-net-file");
            List<String> nodeFields = Arrays.asList(
                    "$KNOTEN:NR",
                    "XKOORD",
                    "YKOORD",
                    "MATSimId");
            writer.write(getCSVLine(nodeFields));

            Map<Node, Integer> visumNodeNrPerNode = new HashMap<>();
            int visumNodeNr = 1;
            for (Node aNode: network.getNodes().values()) {
                visumNodeNrPerNode.put(aNode, visumNodeNr);
                writer.write(getCSVLine(Arrays.asList(
                        String.valueOf(visumNodeNr),
                        String.valueOf(aNode.getCoord().getX()),
                        String.valueOf(aNode.getCoord().getY()),
                        aNode.getId().toString())));
                visumNodeNr++;
            }

            log.info("start writing links to visum-net-file");
            Map<Pair<Integer, Integer>, List<Link>> linksPerNodePair = new HashMap<>();
            for (Link aLink: network.getLinks().values()) {
                Integer fromNodeVisumNr = visumNodeNrPerNode.get(aLink.getFromNode());
                Integer toNodeVisumNr = visumNodeNrPerNode.get(aLink.getToNode());
                Pair<Integer, Integer> nodePair = (fromNodeVisumNr <= toNodeVisumNr) ?
                        new Pair<>(fromNodeVisumNr, toNodeVisumNr):
                        new Pair<>(toNodeVisumNr, fromNodeVisumNr);
                List<Link> links = linksPerNodePair.get(nodePair);
                if (links == null) {
                    links = new ArrayList<>();
                    linksPerNodePair.put(nodePair, links);
                }
                links.add(aLink);
            }
            List<String> linkFields = Arrays.asList(
                    "$STRECKE:NR",
                    "VONKNOTNR",
                    "NACHKNOTNR",
                    "VSYSSET",
                    "LAENGE",
                    "MATSimId",
                    "nbVehicles",
                    "countValue",
                    "nbPassengers");

            writer.write(getCSVLine(linkFields));
            Map<Link, Integer> visumLinkNrPerLink = new HashMap<>();
            int visumLinkNr = 1;
            for (Pair<Integer, Integer> aNodePair: linksPerNodePair.keySet()) {
                if (!aNodePair.getKey().equals(aNodePair.getValue())) {
                    Set<String> allowedModesH = new HashSet<>();
                    Set<String> allowedModesR = new HashSet<>();
                    List<String> matSimIdsH = new ArrayList<>();
                    List<String> matSimIdsR = new ArrayList<>();
                    Double nbVehiclesH = null;
                    Double nbVehiclesR = null;
                    Double countVehiclesH = null;
                    Double countVehiclesR = null;
                    Double nbPassengersH = null;
                    Double nbPassengersR = null;
                    List<Double> lengthListH = new ArrayList<>();
                    List<Double> lengthListR = new ArrayList<>();
                    for (Link aLink: linksPerNodePair.get(aNodePair)) {
                        Double nbVehicles = (nbVehiclesPerLink.get(aLink) == null) ? null : nbVehiclesPerLink.get(aLink);
                        Double nbCounts = (countVehiclesPerLink.get(aLink) == null) ? null: countVehiclesPerLink.get(aLink);
                        Double nbPassengers = (nbPassengersPerLink.get(aLink) == null) ? null : nbPassengersPerLink.get(aLink);
                        if (visumNodeNrPerNode.get(aLink.getFromNode()) <= visumNodeNrPerNode.get(aLink.getToNode())) {
                            allowedModesH.addAll(aLink.getAllowedModes());
                            matSimIdsH.add(aLink.getId().toString());
                            lengthListH.add(aLink.getLength());
                            if (nbVehicles != null) {
                                if (nbVehiclesH != null) nbVehiclesH += nbVehicles;
                                else nbVehiclesH = nbVehicles;
                            }
                            if (nbCounts != null) {
                                if (countVehiclesH != null) countVehiclesH += nbCounts;
                                else {
                                    countVehiclesH = nbCounts;
                                }
                            }
                            if(nbPassengers != null)    {
                                if (nbPassengersH != null) nbPassengersH += nbPassengers;
                                else nbPassengersH = nbPassengers;
                            }

                        } else {
                            allowedModesR.addAll(aLink.getAllowedModes());
                            matSimIdsR.add(aLink.getId().toString());
                            lengthListR.add(aLink.getLength());
                            if (nbVehicles != null) {
                                if (nbVehiclesR != null) nbVehiclesR += nbVehicles;
                                else nbVehiclesR = nbVehicles;
                            }
                            if (nbCounts != null) {
                                if (countVehiclesR != null) countVehiclesR += nbCounts;
                                else {
                                    countVehiclesR = nbCounts;
                                }
                            }
                            if (nbPassengers != null)   {
                                if (nbPassengersR != null) nbPassengersR += nbPassengers;
                                else nbPassengersR = nbPassengers;
                            }
                        }
                        visumLinkNrPerLink.put(aLink, visumLinkNr);
                    }
                    // write H
                    if (!matSimIdsH.isEmpty()) {
                        writer.write(getCSVLine(Arrays.asList(
                                String.valueOf(visumLinkNr),
                                String.valueOf(aNodePair.getKey()),
                                String.valueOf(aNodePair.getValue()),
                                String.join(",", allowedModesH),
                                String.valueOf(getMean(lengthListH)),
                                String.join(",", matSimIdsH),
                                (nbVehiclesH == null) ? "" : String.valueOf(nbVehiclesH),
                                (countVehiclesH == null) ? "": String.valueOf(countVehiclesH),
                                (nbPassengersH == null) ? "" : String.valueOf(nbPassengersH))));
                    }

                    // write R
                    if (!matSimIdsR.isEmpty()) {
                        writer.write(getCSVLine(Arrays.asList(
                                String.valueOf(visumLinkNr),
                                String.valueOf(aNodePair.getValue()),
                                String.valueOf(aNodePair.getKey()),
                                String.join(",", allowedModesR),
                                String.valueOf(getMean(lengthListR)),
                                String.join(",", matSimIdsR),
                                (nbVehiclesR == null) ? "" : String.valueOf(nbVehiclesR),
                                (countVehiclesR == null) ? "": String.valueOf(countVehiclesR),
                                (nbPassengersR == null) ? "" : String.valueOf(nbPassengersR))));
                    }
                    visumLinkNr++;
                }
            }

            log.info("start writing stops, stop-areas and stop-points to visum-net-file");
            List<String> stopFields = Arrays.asList(
                    "$HALTESTELLE:NR",
                    "XKOORD",
                    "YKOORD");
            List<String> stopAreaFields = Arrays.asList(
                    "$HALTESTELLENBEREICH:NR",
                    "HSTNR",
                    "XKOORD",
                    "YKOORD");
            List<String> stopPointFields = Arrays.asList(
                    "$HALTEPUNKT:NR",
                    "HSTBERNR",
                    "VSYSSET",
                    "GERICHTET",
                    "KNOTNR",
                    "VONKNOTNR",
                    "STRNR",
                    "RELPOS",
                    "MATSimId",
                    "MATSimName",
                    "nbPersonsAlighting",
                    "nbPersonsBoarding",
                    "countValueAlighting",
                    "countValueBoarding");

            String stopString = getCSVLine(stopFields);
            String stopAreaString = getCSVLine(stopAreaFields);
            String stopPointString = getCSVLine(stopPointFields);

            int index = 1;
            Set<Node> stopPointNodes = new HashSet<>();
            for (TransitStopFacility aStop: schedule.getFacilities().values()) {
                stopString += getCSVLine(Arrays.asList(
                        String.valueOf(index),
                        String.valueOf(aStop.getCoord().getX()),
                        String.valueOf(aStop.getCoord().getY())));
                stopAreaString += getCSVLine(Arrays.asList(
                        String.valueOf(index),
                        String.valueOf(index),
                        String.valueOf(aStop.getCoord().getX()),
                        String.valueOf(aStop.getCoord().getY())));
                Link link = network.getLinks().get(Id.create(aStop.getLinkId(), Link.class));
                boolean isLoopLink = link.getFromNode().equals(link.getToNode());
                if (isLoopLink && stopPointNodes.contains(link.getFromNode())) continue;
                String nbAlightingStr = (nbAlightingsPerStop.get(aStop) == null) ? "" : String.valueOf(nbAlightingsPerStop.get(aStop));
                String nbBoardingStr = (nbBoardingsPerStop.get(aStop) == null) ? "" : String.valueOf(nbBoardingsPerStop.get(aStop));
                String countAlightingStr = (countAlightingsPerStop.get(aStop) == null) ? "" : String.valueOf(countAlightingsPerStop.get(aStop));
                String countBoardingStr = (countBoardingsPerStop.get(aStop) == null) ? "" : String.valueOf(countBoardingsPerStop.get(aStop));
                stopPointString += getCSVLine(Arrays.asList(
                        String.valueOf(index),
                        String.valueOf(index),
                        TransportMode.pt,
                        "0",
                        isLoopLink ? String.valueOf(visumNodeNrPerNode.get(link.getFromNode())) : "",
                        isLoopLink ? "" : String.valueOf(visumNodeNrPerNode.get(link.getFromNode())),
                        isLoopLink ? "" : String.valueOf(visumLinkNrPerLink.get(link)),
                        isLoopLink ? "0" : String.valueOf(0.51),
                        aStop.getId().toString(),
                        (aStop.getName() == null) ? "" : aStop.getName(),
                        nbAlightingStr,
                        nbBoardingStr,
                        countAlightingStr,
                        countBoardingStr));
                index++;
            }
            writer.write(stopString);
            writer.write(stopAreaString);
            writer.write(stopPointString);
            writer.close();
        } catch (IOException ex) {
            log.info("writing network to visum-net-file failed: " + ex.getMessage());
        }
    }

    private void readVolumeDataPerLink(String pathFile, Network network, Map<Link, Double> nbVehiclesPerLink, double scaleFactor) {
        CSVReader reader = new CSVReader(LinkVolumeToCSV.COLUMNS);
        reader.read(pathFile, ";");
        Iterator<HashMap<String, String>> iterator = reader.data.iterator();
        iterator.next(); // header line
        while (iterator.hasNext()) {
            HashMap<String, String> aRow = iterator.next();
            Link link = network.getLinks().get(Id.create(aRow.get(LinkVolumeToCSV.COL_LINK_ID), Link.class));
            Double nbVehiclesBefore = nbVehiclesPerLink.get(link);
            double actScaleFactor = (link.getAllowedModes().contains(TransportMode.car)) ? scaleFactor : 1.0; // this is a problem, if pt and car is allowed on the same link
            if (nbVehiclesBefore == null) {
                nbVehiclesPerLink.put(link, actScaleFactor * Double.valueOf(aRow.get(LinkVolumeToCSV.COL_VOLUME)));
            } else {
                nbVehiclesPerLink.put(link, actScaleFactor * Double.valueOf(aRow.get(LinkVolumeToCSV.COL_VOLUME)) + nbVehiclesBefore);
            }
        }
    }

    private void readPassengerVolumeDataPerLink(String pathFile, Network network, Map<Link, Double> nbPassengersPerLink, double scaleFactor) {
        CSVReader reader = new CSVReader(LinkVolumeToCSV.COLUMNS);
        reader.read(pathFile, ";");
        Iterator<HashMap<String, String>> iterator = reader.data.iterator();
        iterator.next(); // header line
        while (iterator.hasNext()) {
            HashMap<String, String> aRow = iterator.next();
            Link link = network.getLinks().get(Id.create(aRow.get(LinkVolumeToCSV.COL_LINK_ID), Link.class));
            Double nbPassengersBefore = nbPassengersPerLink.get(link);
            if (nbPassengersBefore == null) {
                nbPassengersPerLink.put(link, scaleFactor * Double.valueOf(aRow.get(LinkVolumeToCSV.COL_NBPASSENGERS)));
            } else {
                nbPassengersPerLink.put(link, scaleFactor * Double.valueOf(aRow.get(LinkVolumeToCSV.COL_NBPASSENGERS)) + nbPassengersBefore);
            }
        }
    }

    private void readAlightingBoardingDataPerStop(String pathFile,
                                                  TransitSchedule schedule,
                                                  Map<TransitStopFacility, Double> nbAlightingsPerStop,
                                                  Map<TransitStopFacility, Double> nbBoardingsPerStop,
                                                  double scaleFactor) {
        CSVReader reader = new CSVReader(PtVolumeToCSV.COLS_STOPS);
        reader.read(pathFile, ";");
        Iterator<HashMap<String, String>> iterator = reader.data.iterator();
        iterator.next(); // header line
        while (iterator.hasNext()) {
            HashMap<String, String> aRow = iterator.next();
            TransitStopFacility stop = schedule.getFacilities().get(Id.create(aRow.get(PtVolumeToCSV.COL_STOP_ID), TransitStopFacility.class));
            Double nbAlightingsBefore = nbAlightingsPerStop.get(stop);
            if (nbAlightingsBefore == null) {
                nbAlightingsPerStop.put(stop, scaleFactor * Double.valueOf(aRow.get(PtVolumeToCSV.COL_ALIGHTING)));
            } else {
                nbAlightingsPerStop.put(stop, scaleFactor * Double.valueOf(aRow.get(PtVolumeToCSV.COL_ALIGHTING)) + nbAlightingsBefore);
            }
            Double nbBoardingsBefore = nbBoardingsPerStop.get(stop);
            if (nbBoardingsBefore == null) {
                nbBoardingsPerStop.put(stop, scaleFactor * Double.valueOf(aRow.get(PtVolumeToCSV.COL_BOARDING)));
            } else {
                nbBoardingsPerStop.put(stop, scaleFactor * Double.valueOf(aRow.get(PtVolumeToCSV.COL_BOARDING)) + nbBoardingsBefore);
            }
        }
    }

    private String getCSVLine(List<String> l) {
        return String.join(";", l) + "\n";
    }

    private Double getMean(List<Double> l) {
        Double sum = 0.0;
        for (Double d : l) {
            sum += d;
        }
        return sum / l.size();
    }

    @Override
    public void closeFile() {
        this.write();
    }

    @Override
    public void reset(int iteration) {
        this.iteration = iteration;
    }
}
