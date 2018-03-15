/*
 * Copyright (C) Schweizerische Bundesbahnen SBB, 2017.
 */

package ch.sbb.matsim.mavi;

import com.jacob.activeX.ActiveXComponent;
import com.jacob.com.Dispatch;
import com.jacob.com.Variant;
import com.sun.org.apache.xalan.internal.xsltc.compiler.util.Type;
import org.apache.log4j.Logger;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.network.NetworkFactory;
import org.matsim.api.core.v01.network.Node;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.config.ConfigWriter;
import org.matsim.core.network.io.NetworkWriter;
import org.matsim.core.population.routes.LinkNetworkRouteImpl;
import org.matsim.core.population.routes.NetworkRoute;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.core.utils.collections.CollectionUtils;
import org.matsim.core.utils.misc.Time;
import org.matsim.pt.transitSchedule.api.*;
import org.matsim.utils.objectattributes.ObjectAttributesXmlWriter;
import org.matsim.vehicles.Vehicle;
import org.matsim.vehicles.VehicleCapacity;
import org.matsim.vehicles.VehicleCapacityImpl;
import org.matsim.vehicles.VehicleType;
import org.matsim.vehicles.VehicleWriterV1;
import org.matsim.vehicles.Vehicles;
import org.matsim.vehicles.VehiclesFactory;

import java.io.File;
import java.util.*;

/***
 *
 * @author pmanser / SBB
 *
 * IMPORTANT:
 * Download the JACOB library version 1.18 and
 * set path to the library in the VM Options (e.g. -Djava.library.path="C:\Users\u225744\Downloads\jacob-1.18\jacob-1.18")
 *
 */

public class Exporter {

    private final static String NETWORK_OUT = "transitNetwork.xml.gz";
    private final static String TRANSITSCHEDULE_OUT = "transitSchedule.xml.gz";
    private final static String TRANSITVEHICLES_OUT = "transitVehicles.xml.gz";

    private final static Logger log = Logger.getLogger(ExportPTSupplyFromVisum.class);;

    private ExportPTSupplyFromVisumConfigGroup exporterConfig;
    private Network network;
    private TransitSchedule schedule;
    private Vehicles vehicles;

    private TransitScheduleFactory scheduleBuilder;
    private NetworkFactory networkBuilder;
    private VehiclesFactory vehicleBuilder;

    private HashMap<Integer, Set<Id<TransitStopFacility>>> stopAreasToStopPoints = new HashMap<>();

    public static void main(String[] args) {
        new Exporter(args[0]);
    }

    public Exporter(String configFile) {
        Config config = ConfigUtils.loadConfig(configFile, new ExportPTSupplyFromVisumConfigGroup());
        this.exporterConfig = ConfigUtils.addOrGetModule(config, ExportPTSupplyFromVisumConfigGroup.class);
        run();
    }

    public void run() {
        File outputPath = new File(this.exporterConfig.getOutputPath());
        if(!outputPath.exists())
            outputPath.mkdir();

        ActiveXComponent visum = new ActiveXComponent("Visum.Visum.16");
        log.info("VISUM Client gestartet.");

        loadVersion(visum);

        if(this.exporterConfig.getPathToAttributeFile() != null)
            loadAttributes(visum);
        if(this.exporterConfig.getTimeProfilFilterParams().size() != 0)
            setTimeProfilFilter(visum);

        createMATSimScenario();

        Dispatch net = Dispatch.get(visum, "Net").toDispatch();
        loadStopPoints(net);
        if(this.exporterConfig.isExportMTT())
            integrateMinTransferTimes(visum);
        createVehicleTypes(net);
        loadTransitLines(net);

        visum.safeRelease();

        cleanStops();
        cleanNetwork();

        writeFiles();
    }

    private void loadVersion(ActiveXComponent visum) {
        log.info("LoadVersion started...");
        log.info("Start VISUM Client mit Version " + this.exporterConfig.getPathToVisum());
        Dispatch.call(visum, "LoadVersion", new Object[] { new Variant( this.exporterConfig.getPathToVisum() ) });
        log.info("LoadVersion finished");
    }

    private void loadAttributes(ActiveXComponent visum) {
        Dispatch io = Dispatch.get(visum, "IO").toDispatch();
        Dispatch.call(io, "LoadAttributeFile", this.exporterConfig.getPathToAttributeFile());
    }

    private void createMATSimScenario()   {
        Scenario scenario = ScenarioUtils.createScenario(ConfigUtils.createConfig());
        log.info("MATSim scenario created");
        this.scheduleBuilder = scenario.getTransitSchedule().getFactory();
        log.info("Schedule builder initialized");
        this.vehicleBuilder = scenario.getTransitVehicles().getFactory();
        log.info("Vehicle builder initialized");
        this.networkBuilder = scenario.getNetwork().getFactory();
        log.info("Network builder initialized");

        this.network = scenario.getNetwork();
        this.schedule = scenario.getTransitSchedule();
        this.schedule.getAttributes().putAttribute("Info","MOBi.OEV 2.0 (SBB) -> includes minimal transfer times");
        this.vehicles = scenario.getTransitVehicles();
    }

    private void setTimeProfilFilter(ActiveXComponent visum)    {
        Dispatch filters = Dispatch.get(visum, "Filters").toDispatch();
        Dispatch filter = Dispatch.call(filters, "LineGroupFilter").toDispatch();
        Dispatch tpFilter = Dispatch.call(filter, "TimeProfileFilter").toDispatch();
        Dispatch.call(tpFilter, "Init");

        for(ExportPTSupplyFromVisumConfigGroup.TimeProfilFilterParams f: this.exporterConfig.getTimeProfilFilterParams().values())
            Dispatch.call(tpFilter, "AddCondition", f.getOp(), f.isComplement(), f.getAttribute(), f.getComparator(),
                    f.getVal(), f.getPosition());

        Dispatch.put(filter, "UseFilterForTimeProfiles", true);
    }

    private void loadStopPoints(Dispatch net) {
        log.info("LoadStopPoints and CreateLoopLinks started...");
        Dispatch stopPoints = Dispatch.get(net, "StopPoints").toDispatch();
        Dispatch stopPointIterator = Dispatch.get(stopPoints, "Iterator").toDispatch();

        int nrOfStopPoints = Dispatch.call(stopPoints, "Count").getInt();
        int i = 0;

        while (i < nrOfStopPoints) {
            Dispatch item = Dispatch.get(stopPointIterator, "Item").toDispatch();

            // get the stop characteristics
            int stopPointNo = (int) Dispatch.call(item, "AttValue", "No").getDouble();
            Id<TransitStopFacility> stopPointID = Id.create(stopPointNo, TransitStopFacility.class);
            double xCoord = Dispatch.call(item, "AttValue", "XCoord").getDouble();
            double yCoord = Dispatch.call(item, "AttValue", "YCoord").getDouble();
            Coord stopPointCoord = new Coord(xCoord, yCoord);
            String stopPointName = Dispatch.call(item, "AttValue", "Name").toString();

            // create stop node and loop link
            double fromStopIsOnNode = Dispatch.call(item, "AttValue", "IsOnNode").getDouble();
            double fromStopIsOnLink = Dispatch.call(item, "AttValue", "IsOnLink").getDouble();
            Node stopNode = null;
            if(fromStopIsOnNode == 1.0) {
                int stopNodeIDNo = (int) Dispatch.call(item, "AttValue", "NodeNo").getDouble();
                Id<Node> stopNodeID = Id.createNodeId(this.exporterConfig.getNetworkMode() + "_" + stopNodeIDNo);
                stopNode = this.networkBuilder.createNode(stopNodeID, stopPointCoord);
                this.network.addNode(stopNode);
            }
            else if(fromStopIsOnLink == 1.0)    {
                int stopLinkFromNodeNo = (int) Dispatch.call(item, "AttValue", "FromNodeNo").getDouble();
                Id<Node> stopNodeID = Id.createNodeId(this.exporterConfig.getNetworkMode() + "_" + stopLinkFromNodeNo + "_"  + stopPointNo);
                stopNode = this.networkBuilder.createNode(stopNodeID, stopPointCoord);
                this.network.addNode(stopNode);
            }
            else    {
                log.error("something went wrong. stop must be either on node or on link.");
            }

            Id<Link> loopLinkID = Id.createLinkId(this.exporterConfig.getNetworkMode() + "_" + stopPointNo);
            Link loopLink = this.networkBuilder.createLink(loopLinkID, stopNode, stopNode);
            loopLink.setLength(0.0);
            loopLink.setFreespeed(10000);
            loopLink.setCapacity(10000);
            loopLink.setNumberOfLanes(10000);
            loopLink.setAllowedModes(Collections.singleton(this.exporterConfig.getNetworkMode()));
            this.network.addLink(loopLink);

            int stopAreaNo = (int) Dispatch.call(item, "AttValue", "StopArea\\No").getDouble();
            if(!this.stopAreasToStopPoints.containsKey(stopAreaNo))
                this.stopAreasToStopPoints.put(stopAreaNo, new HashSet<>());
            this.stopAreasToStopPoints.get(stopAreaNo).add(stopPointID);

            // create transitStopFacility
            TransitStopFacility st = this.scheduleBuilder.createTransitStopFacility(stopPointID, stopPointCoord, false);
            st.setName(stopPointName);
            st.setLinkId(loopLinkID);

            // custom stop attributes as identifiers
            for(ExportPTSupplyFromVisumConfigGroup.StopAttributeParams params: this.exporterConfig.getStopAttributeParams().values())    {
                String name = Dispatch.call(item, "AttValue", params.getAttributeValue()).toString();
                if(!name.isEmpty() && !name.equals("null"))    {
                    switch ( params.getDataType() ) {
                        case Type.STRING_CLASS:
                            st.getAttributes().putAttribute(params.getAttributeName(), name);
                            break;
                        case Type.DOUBLE_CLASS:
                            st.getAttributes().putAttribute(params.getAttributeName(), Double.parseDouble(name));
                            break;
                        case Type.INTEGER_CLASS:
                            st.getAttributes().putAttribute(params.getAttributeName(), (int) Double.parseDouble(name));
                            break;
                        default:
                            throw new IllegalArgumentException( params.getDataType() );
                    }
                }
            }
            this.schedule.addStopFacility(st);

            i++;
            Dispatch.call(stopPointIterator, "Next");
        }

        log.info("Finished LoadStopPoints and CreateLoopLinks");
        log.info("Added " + nrOfStopPoints + " Stop Points");
    }

    private void integrateMinTransferTimes(ActiveXComponent visum)  {
        List<Transfer> withinTransfers = loadWithinTransfers(visum);
        List<Transfer> betweenTransfers = loadBetweenTransfers(visum);
        integrateTransfers(withinTransfers);
        integrateTransfers(betweenTransfers);

        // clear memory
        this.stopAreasToStopPoints.clear();
    }

    private static List<Transfer> loadWithinTransfers(ActiveXComponent visum)  {
        log.info("loading within stop transfers.");
        Dispatch lists = Dispatch.get(visum, "Lists").toDispatch();
        Dispatch walkTimes = Dispatch.get(lists, "CreateStopTransferWalkTimeList").toDispatch();
        Dispatch.call(walkTimes, "AddColumn", "FromStopAreaNo");
        Dispatch.call(walkTimes, "AddColumn", "ToStopAreaNo");
        Dispatch.call(walkTimes, "AddColumn", "Time(F)", 3);

        String transferList = Dispatch.call(walkTimes, "SaveToArray").toString();
        String[] lines = transferList.split("\n");
        log.info("Number of transfers: " + lines.length);
        List<Transfer> transfers = new ArrayList<>();
        for(String line: lines) {
            String[] transfer = line.split(" ");
            if(Double.parseDouble(transfer[3]) > 0)
                transfers.add(new Transfer((int) Double.parseDouble(transfer[1]), (int) Double.parseDouble(transfer[2]),
                        Double.parseDouble(transfer[3])));
        }
        log.info("finished loading within stop transfers.");
        return transfers;
    }

    private static List<Transfer> loadBetweenTransfers(ActiveXComponent visum)  {
        log.info("loading \"Fusswege\".");
        Dispatch filters = Dispatch.get(visum, "Filters").toDispatch();
        Dispatch linkFilter = Dispatch.call(filters, "LinkFilter").toDispatch();
        Dispatch.call(linkFilter, "Init");
        Dispatch.call(linkFilter, "AddCondition", "OP_NONE", false, "TSysSet", 13, "F");
        Dispatch.put(linkFilter, "UseFilter", true);

        Dispatch net = Dispatch.get(visum, "Net").toDispatch();
        Dispatch links = Dispatch.get(net, "Links").toDispatch();
        Dispatch linkIterator = Dispatch.get(links, "Iterator").toDispatch();

        int nrOfLinks = Dispatch.call(links, "CountActive").getInt();
        log.info("Number of Fusswege: " + nrOfLinks);
        int i = 0;

        List<Transfer> transfers = new ArrayList<>();
        while (i < nrOfLinks) {
            Dispatch item = Dispatch.get(linkIterator, "Item").toDispatch();

            String fromStopAreasStr = Dispatch.call(item, "AttValue", "FROMNODE\\DISTINCT:STOPAREAS\\NO").toString();
            Set<String> fromStopAreas = CollectionUtils.stringToSet(fromStopAreasStr);
            String toStopAreasStr = Dispatch.call(item, "AttValue", "TONODE\\DISTINCT:STOPAREAS\\NO").toString();
            Set<String> toStopAreas = CollectionUtils.stringToSet(toStopAreasStr);
            double walkTime = Dispatch.call(item, "AttValue", "T_PUTSYS(F)").getDouble();
            if(walkTime > 0 && fromStopAreas.size() > 0 && toStopAreas.size() > 0) {
                for (String fromStopArea : fromStopAreas) {
                    for (String toStopArea : toStopAreas) {
                        transfers.add(new Transfer((int) Double.parseDouble(fromStopArea), (int) Double.parseDouble(toStopArea),
                                walkTime));
                    }
                }
            }
            i++;
            Dispatch.call(linkIterator, "Next");
        }

        Dispatch.put(linkFilter, "UseFilter", false);
        log.info("finished loading \"Fusswege\".");
        return transfers;
    }

    private void integrateTransfers(List<Transfer> transfers) {
        log.info("started integrating the transfers into the schedule");
        int countAreaTransfers = 0;
        int countStopTransfers = 0;
        MinimalTransferTimes mtt = this.schedule.getMinimalTransferTimes();
        for (Transfer transfer : transfers) {
            Set<Id<TransitStopFacility>> fromStopFacilities = this.stopAreasToStopPoints.get(transfer.fromStopArea);
            Set<Id<TransitStopFacility>> toStopFacilities = this.stopAreasToStopPoints.get(transfer.toStopArea);

            if (fromStopFacilities != null && toStopFacilities != null) {
                countAreaTransfers++;
                for (Id<TransitStopFacility> fromFacilityId : fromStopFacilities) {
                    for (Id<TransitStopFacility> toFacilityId : toStopFacilities) {
                        countStopTransfers++;
                        double oldValue = mtt.set(fromFacilityId, toFacilityId, transfer.transferTime);
                        if (!Double.isNaN(oldValue) && oldValue != transfer.transferTime) {
                            log.warn("Overwrite transfer time from " + fromFacilityId + " to " + toFacilityId + ". oldValue = " + oldValue + "  newValue = " + transfer.transferTime);
                        }
                    }
                }
            }
        }
        log.info("Used " + countAreaTransfers + " transfer relations between stop areas to generate " + countStopTransfers + " transfers between stop facilities.");
    }

    private static class Transfer {
        final int fromStopArea;
        final int toStopArea;
        final double transferTime;

        public Transfer(int fromStopArea, int toStopArea, double transferTime) {
            this.fromStopArea = fromStopArea;
            this.toStopArea = toStopArea;
            this.transferTime = transferTime;
        }
    }

    private void createVehicleTypes(Dispatch net) {
        log.info("Loading vehicle types...");
        Dispatch tSystems = Dispatch.get(net, "TSystems").toDispatch();
        Dispatch tSystemsIterator = Dispatch.get(tSystems, "Iterator").toDispatch();

        int nrOfTSystems = Dispatch.call(tSystems, "Count").getInt();
        int i = 0;

        while (i < nrOfTSystems) {
            Dispatch item = Dispatch.get(tSystemsIterator, "Item").toDispatch();
            String tSysCode = Dispatch.call(item, "AttValue", "Code").toString();
            String tSysName = Dispatch.call(item, "AttValue", "Name").toString();

            Id<VehicleType> vehicleTypeId = Id.create(tSysCode, VehicleType.class);
            // TODO e.g. for "Fernbusse", we need the possibility to set capacity constraint.
            VehicleType vehicleType = this.vehicleBuilder.createVehicleType(vehicleTypeId);
            vehicleType.setDescription(tSysName);
            vehicleType.setDoorOperationMode(VehicleType.DoorOperationMode.serial);
            VehicleCapacity vehicleCapacity = new VehicleCapacityImpl();
            vehicleCapacity.setStandingRoom(500);
            vehicleCapacity.setSeats(10000);
            vehicleType.setCapacity(vehicleCapacity);

            // the following parameters do not have any influence in a deterministic simulation engine
            vehicleType.setLength(10);
            vehicleType.setWidth(2);
            vehicleType.setPcuEquivalents(1);
            vehicleType.setMaximumVelocity(10000);
            this.vehicles.addVehicleType(vehicleType);

            i++;
            Dispatch.call(tSystemsIterator, "Next");
        }
    }

    private void loadTransitLines(Dispatch net)   {
        Dispatch timeProfiles = Dispatch.get(net, "TimeProfiles").toDispatch();
        Dispatch timeProfileIterator = Dispatch.get(timeProfiles, "Iterator").toDispatch();
        log.info("Loading transit routes started...");

        int nrOfTimeProfiles = Dispatch.call(timeProfiles, "CountActive").getInt();
        log.info("Number of active time profiles: " + nrOfTimeProfiles);
        int i = 0;

        //while (i < 100) { // for test purposes
        while (i < nrOfTimeProfiles) {
            if(!Dispatch.call(timeProfileIterator, "Active").getBoolean())   {
                Dispatch.call(timeProfileIterator, "Next");
                continue;
            }

            log.info("Processing Time Profile " + i + " of " + nrOfTimeProfiles);
            Dispatch item = Dispatch.get(timeProfileIterator, "Item").toDispatch();

            String lineName = Dispatch.call(item, "AttValue", "LineName").toString();
            Id<TransitLine> lineID = Id.create(lineName, TransitLine.class);
            if(!this.schedule.getTransitLines().containsKey(lineID)) {
                TransitLine line = this.scheduleBuilder.createTransitLine(lineID);
                this.schedule.addTransitLine(line);
            }

            // Fahrplanfahrten
            Dispatch vehicleJourneys = Dispatch.get(item, "VehJourneys").toDispatch();
            Dispatch vehicleJourneyIterator = Dispatch.get(vehicleJourneys, "Iterator").toDispatch();

            String mode;
            if(this.exporterConfig.getVehicleMode().equals("Datenherkunft"))
                mode = Dispatch.call(item, "AttValue", "LineRoute\\Line\\Datenherkunft").toString();
            else
                mode = this.exporterConfig.getVehicleMode();

            int nrOfVehicleJourneys = Dispatch.call(vehicleJourneys, "Count").getInt();
            int k = 0;

            while (k < nrOfVehicleJourneys) {
                Dispatch item_ = Dispatch.get(vehicleJourneyIterator, "Item").toDispatch();

                int routeName = (int) Dispatch.call(item, "AttValue", "ID").getDouble();
                int from_tp_index = (int) Dispatch.call(item_, "AttValue", "FromTProfItemIndex").getDouble();
                int to_tp_index = (int) Dispatch.call(item_, "AttValue", "ToTProfItemIndex").getDouble();
                Id<TransitRoute> routeID = Id.create(routeName + "_" + from_tp_index + "_" + to_tp_index, TransitRoute.class);
                TransitRoute route;

                if(!this.schedule.getTransitLines().get(lineID).getRoutes().containsKey(routeID)) {
                    Dispatch lineRoute = Dispatch.get(item_, "LineRoute").toDispatch();
                    Dispatch lineRouteItems = Dispatch.get(lineRoute, "LineRouteItems").toDispatch();
                    Dispatch lineRouteItemsIterator = Dispatch.get(lineRouteItems, "Iterator").toDispatch();

                    // Fahrzeitprofil-Verläufe
                    List<TransitRouteStop> transitRouteStops = new ArrayList<>();
                    List<Id<Link>> routeLinks = new ArrayList<>();
                    Id<Link> startLink = null;
                    Id<Link> endLink = null;
                    TransitStopFacility fromStop = null;
                    double postlength = 0.0;
                    double delta = 0.0;

                    Dispatch fzpVerlaufe = Dispatch.get(item, "TimeProfileItems").toDispatch();
                    Dispatch fzpVerlaufIterator = Dispatch.get(fzpVerlaufe, "Iterator").toDispatch();

                    int nrFZPVerlaufe = Dispatch.call(fzpVerlaufe, "Count").getInt();
                    int l = 0;
                    boolean isFirstRouteStop = true;

                    while (l < nrFZPVerlaufe) {
                        Dispatch item__ = Dispatch.get(fzpVerlaufIterator, "Item").toDispatch();

                        int stopPointNo = (int) Dispatch.call(item__, "AttValue", "LineRouteItem\\StopPointNo").getDouble();

                        int index = (int) Dispatch.call(item__, "AttValue", "Index").getDouble();
                        if(from_tp_index > index || to_tp_index < index)    {
                            l++;
                            Dispatch.call(fzpVerlaufIterator, "Next");
                            continue;
                        }
                        else if(from_tp_index == index) {
                            startLink = Id.createLinkId(this.exporterConfig.getNetworkMode() + "_" + stopPointNo);
                            delta = Dispatch.call(item__, "AttValue", "Dep").getDouble();
                        }
                        else if(to_tp_index == index) { endLink = Id.createLinkId(this.exporterConfig.getNetworkMode() + "_" + stopPointNo); }

                        Id<TransitStopFacility> stopID = Id.create(stopPointNo, TransitStopFacility.class);
                        TransitStopFacility stop = this.schedule.getFacilities().get(stopID);

                        double arrTime = Dispatch.call(item__, "AttValue", "Arr").getDouble();
                        double depTime = Dispatch.call(item__, "AttValue", "Dep").getDouble();
                        TransitRouteStop rst;
                        if(isFirstRouteStop) {
                            rst = this.scheduleBuilder.createTransitRouteStop(stop, Time.UNDEFINED_TIME, depTime - delta);
                            isFirstRouteStop = false;
                        }
                        else {
                            rst = this.scheduleBuilder.createTransitRouteStop(stop, arrTime - delta, depTime - delta);
                        }
                        rst.setAwaitDepartureTime(true);
                        transitRouteStops.add(rst);

                        if(fromStop != null) {
                            // routed network
                            if(this.exporterConfig.getLinesToRoute().contains(mode))   {
                                Dispatch lineRouteItem = Dispatch.get(lineRouteItemsIterator, "Item").toDispatch();
                                boolean startwriting = false;
                                boolean foundToStop = false;

                                while (!foundToStop) {
                                    boolean stopIsOnLink = false;

                                    double lineRouteStop = Double.MAX_VALUE;
                                    String lineRouteStopStr = Dispatch.call(lineRouteItem, "AttValue", "StopPointNo").toString();
                                    if(!lineRouteStopStr.equals("null")) {
                                        lineRouteStop = Double.valueOf(lineRouteStopStr);
                                        if(Double.valueOf(Dispatch.call(lineRouteItem, "AttValue", "StopPoint\\IsOnLink").toString()) == 1.0)
                                            stopIsOnLink = true;
                                    }

                                    boolean isToStop = String.valueOf((int) lineRouteStop).equals(stop.getId().toString());

                                    if(isToStop)   {
                                        if(stopIsOnLink) {
                                            // last link must be split into two pieces if the stop is on a link
                                            Id<TransitStopFacility> lineRouteStopId = Id.create((int) lineRouteStop, TransitStopFacility.class);
                                            Node betweenNode = this.network.getLinks().get(this.schedule.getFacilities().get(lineRouteStopId).getLinkId()).getFromNode();

                                            Id<Link> lastRouteLinkId = routeLinks.get(routeLinks.size() - 1);
                                            routeLinks.remove(routeLinks.size() - 1);
                                            String[] newLinkIdStr = lastRouteLinkId.toString().split("-");
                                            Node fromNode = this.network.getNodes().get(Id.createNodeId(newLinkIdStr[0]));

                                            Id<Link> newLinkID = Id.createLinkId(fromNode.getId().toString() + "-" + newLinkIdStr[1] + "-" + betweenNode.getId().toString());
                                            createLinkIfDoesNtExist(newLinkID, lineRouteItem, fromNode, betweenNode, false, false, mode);
                                            routeLinks.add(newLinkID);
                                        }
                                        routeLinks.add(stop.getLinkId());
                                        break;
                                    }

                                    boolean isFromStop = String.valueOf((int) lineRouteStop).equals(fromStop.getId().toString());
                                    if(isFromStop)
                                        startwriting = true;

                                    if(startwriting)    {
                                        int outLinkNo = (int) Dispatch.call(lineRouteItem, "AttValue", "OutLink\\No").getDouble();
                                        int fromNodeNo = (int) Dispatch.call(lineRouteItem, "AttValue", "OutLink\\FromNodeNo").getDouble();
                                        int toNodeNo = (int) Dispatch.call(lineRouteItem, "AttValue", "OutLink\\ToNodeNo").getDouble();

                                        Id<Node> fromNodeId = Id.createNodeId(this.exporterConfig.getNetworkMode() + "_" + fromNodeNo);
                                        Id<Node> toNodeId = Id.createNodeId(this.exporterConfig.getNetworkMode() + "_" + toNodeNo);

                                        Node fromNode = createAndGetNode(fromNodeId, lineRouteItem, true);
                                        Node toNode = createAndGetNode(toNodeId, lineRouteItem, false);

                                        if(!stopIsOnLink) {
                                            Id<Link> newLinkID = Id.createLinkId(fromNode.getId().toString() + "-" + outLinkNo + "-" + toNode.getId().toString());
                                            createLinkIfDoesNtExist(newLinkID, lineRouteItem, fromNode, toNode, true, false, mode);
                                            routeLinks.add(newLinkID);
                                        }
                                        else    {
                                            Id<TransitStopFacility> lineRouteStopId = Id.create((int) lineRouteStop, TransitStopFacility.class);
                                            Node betweenNode = this.network.getLinks().get(this.schedule.getFacilities().get(lineRouteStopId).getLinkId()).getFromNode();
                                            Id<Link> newLinkID;

                                            if(!isFromStop) {
                                                routeLinks.remove(routeLinks.size() - 1);
                                                newLinkID = Id.createLinkId(fromNode.getId().toString() + "-" + outLinkNo + "-" + betweenNode.getId().toString());
                                                createLinkIfDoesNtExist(newLinkID, lineRouteItem, fromNode, betweenNode, false, false, mode);
                                                routeLinks.add(newLinkID);
                                            }

                                            newLinkID = Id.createLinkId(betweenNode.getId().toString() + "-" + outLinkNo + "-" + toNode.getId().toString());
                                            createLinkIfDoesNtExist(newLinkID, lineRouteItem, betweenNode, toNode, false, true, mode);
                                            routeLinks.add(newLinkID);
                                        }
                                    }
                                    Dispatch.call(lineRouteItemsIterator, "Next");
                                    lineRouteItem = Dispatch.get(lineRouteItemsIterator, "Item").toDispatch();
                                }
                            }

                            // non-routed links (fly from stop to stop)
                            else {
                                Node fromNode = this.network.getLinks().get(fromStop.getLinkId()).getFromNode();
                                Node toNode = this.network.getLinks().get(stop.getLinkId()).getFromNode();
                                Id<Link> newLinkID = Id.createLinkId(fromNode.getId().toString() + "-" + toNode.getId().toString());
                                if (!this.network.getLinks().containsKey(newLinkID)) {
                                    Link newLink = this.networkBuilder.createLink(newLinkID, fromNode, toNode);
                                    newLink.setLength(postlength * 1000);
                                    newLink.setFreespeed(10000);
                                    newLink.setCapacity(10000);
                                    newLink.setNumberOfLanes(10000);
                                    newLink.setAllowedModes(Collections.singleton(mode));
                                    this.network.addLink(newLink);
                                }
                                // differentiate between links with the same from- and to-node but different length
                                else {
                                    boolean hasLinkWithSameLength = false;
                                    if (this.network.getLinks().get(newLinkID).getLength() != postlength * 1000) {
                                        int m = 1;
                                        Id<Link> linkID = Id.createLinkId(fromNode.getId().toString() + "-" + toNode.getId().toString() + "." + m);
                                        while (this.network.getLinks().containsKey(linkID)) {
                                            if (this.network.getLinks().get(linkID).getLength() == postlength * 1000) {
                                                hasLinkWithSameLength = true;
                                                break;
                                            }
                                            m++;
                                            linkID = Id.createLinkId(fromNode.getId().toString() + "-" + toNode.getId().toString() + "." + m);
                                        }
                                        if (!hasLinkWithSameLength) {
                                            Link link = this.networkBuilder.createLink(linkID, fromNode, toNode);
                                            link.setLength(postlength * 1000);
                                            link.setFreespeed(10000);
                                            link.setCapacity(10000);
                                            link.setNumberOfLanes(10000);
                                            link.setAllowedModes(Collections.singleton(mode));
                                            this.network.addLink(link);
                                            newLinkID = linkID;
                                        }
                                    }
                                }
                                routeLinks.add(newLinkID);
                                routeLinks.add(stop.getLinkId());
                            }
                        }
                        postlength = Double.valueOf(Dispatch.call(item__, "AttValue", "PostLength").toString());
                        fromStop = stop;

                        l++;
                        Dispatch.call(fzpVerlaufIterator, "Next");
                    }
                    routeLinks.remove(routeLinks.size() - 1);
                    NetworkRoute netRoute = new LinkNetworkRouteImpl(startLink, endLink);
                    netRoute.setLinkIds(startLink, routeLinks, endLink);

                    route = this.scheduleBuilder.createTransitRoute(routeID, netRoute, transitRouteStops, mode);

                    this.schedule.getTransitLines().get(lineID).addRoute(route);
                }
                else    {
                    route = this.schedule.getTransitLines().get(lineID).getRoutes().get(routeID);
                }

                int depName = (int) Dispatch.call(item_, "AttValue", "No").getDouble();
                Id<Departure> depID = Id.create(depName, Departure.class);
                double depTime = Dispatch.call(item_, "AttValue", "Dep").getDouble();
                Departure dep = this.scheduleBuilder.createDeparture(depID, depTime);

                Id<Vehicle> vehicleId = Id.createVehicleId(depID.toString());
                dep.setVehicleId(vehicleId);
                route.addDeparture(dep);

                // custom route identifiers
                for(ExportPTSupplyFromVisumConfigGroup.RouteAttributeParams params: this.exporterConfig.getRouteAttributeParams().values())    {
                    String name = Dispatch.call(item, "AttValue", params.getAttributeValue()).toString();
                    if(!name.isEmpty() && !name.equals("null"))    {
                        switch ( params.getDataType() ) {
                            case Type.STRING_CLASS:
                                route.getAttributes().putAttribute(params.getAttributeName(), name);
                                break;
                            case Type.DOUBLE_CLASS:
                                route.getAttributes().putAttribute(params.getAttributeName(), Double.parseDouble(name));
                                break;
                            case Type.INTEGER_CLASS:
                                route.getAttributes().putAttribute(params.getAttributeName(), (int) Double.parseDouble(name));
                                break;
                            default:
                                throw new IllegalArgumentException( params.getDataType() );
                        }
                    }
                }

                String vehicleType = Dispatch.call(item, "AttValue", "TSysCode").toString();
                Id<VehicleType> vehicleTypeId = Id.create(vehicleType, VehicleType.class);
                Vehicle vehicle = this.vehicleBuilder.createVehicle(vehicleId, this.vehicles.getVehicleTypes().get(vehicleTypeId));
                this.vehicles.addVehicle(vehicle);

                k++;
                Dispatch.call(vehicleJourneyIterator, "Next");
            }
            i++;
            Dispatch.call(timeProfileIterator, "Next");
        }
        log.info("Loading transit routes finished");
    }

    private Node createAndGetNode(Id<Node> nodeID, Dispatch visumNode, boolean isFromNode) {
        Node node;
        if(this.network.getNodes().containsKey(nodeID)) {
            node = this.network.getNodes().get(nodeID);
        } else {
            double xCoord;
            double yCoord;
            if(isFromNode) {
                xCoord = Double.valueOf(Dispatch.call(visumNode, "AttValue", "OutLink\\FromNode\\XCoord").toString());
                yCoord = Double.valueOf(Dispatch.call(visumNode, "AttValue", "OutLink\\FromNode\\YCoord").toString());
            }
            else {
                xCoord = Double.valueOf(Dispatch.call(visumNode, "AttValue", "OutLink\\ToNode\\XCoord").toString());
                yCoord = Double.valueOf(Dispatch.call(visumNode, "AttValue", "OutLink\\ToNode\\YCoord").toString());
            }
            node = this.networkBuilder.createNode(nodeID, new Coord(xCoord, yCoord));
            this.network.addNode(node);
        }
        return node;
    }

    private void createLinkIfDoesNtExist(Id<Link> linkID, Dispatch visumLink, Node fromNode, Node toNode, boolean isOnNode, boolean fromNodeIsBetweenNode, String ptMode)    {
        if (!this.network.getLinks().containsKey(linkID)) {
            Link link = this.networkBuilder.createLink(linkID, fromNode, toNode);
            String lengthStr = Dispatch.call(visumLink, "AttValue", "OutLink\\Length").toString();
            double length;
            if(!lengthStr.equals("null"))
                length = Double.valueOf(lengthStr);
            else
                length = Dispatch.call(visumLink, "AttValue", "InLink\\Length").getDouble();
            if(!isOnNode) {
                double fraction = Dispatch.call(visumLink, "AttValue", "StopPoint\\RelPos").getDouble();

                double fromNodeNo = Double.valueOf(fromNode.getId().toString().split("_")[1]);
                double toNodeNo = Double.valueOf(toNode.getId().toString().split("_")[1]);

                double fromNodeStopLink = Dispatch.call(visumLink, "AttValue", "StopPoint\\FromNodeNo").getDouble();
                if(fromNodeNo == fromNodeStopLink && !fromNodeIsBetweenNode)   {
                    length = length * fraction;
                }
                else if(toNodeNo == fromNodeNo && fromNodeIsBetweenNode)     {
                    length = length * fraction;
                }
                else {
                    length = length * (1 - fraction);
                }
            }
            link.setLength(length * 1000);
            link.setFreespeed(10000);
            link.setCapacity(10000);
            link.setNumberOfLanes(10000);
            link.setAllowedModes(Collections.singleton(ptMode));
            this.network.addLink(link);
        }
    }

    private void cleanStops()   {
        Set<Id<TransitStopFacility>> stopsToKeep = new HashSet<>();
        Set<Id<TransitStopFacility>> stopsToRemove = new HashSet<>();

        for(TransitLine line: this.schedule.getTransitLines().values())   {
            for(TransitRoute route: line.getRoutes().values())  {
                for(TransitRouteStop stops: route.getStops()) {
                    stopsToKeep.add(stops.getStopFacility().getId());
                }
            }
        }
        for(Id<TransitStopFacility> stopId: this.schedule.getFacilities().keySet()) {
            if(!stopsToKeep.contains(stopId))
                stopsToRemove.add(stopId);
        }
        log.info("Cleared " + stopsToRemove.size() + " unused stop facilities.");
        for(Id<TransitStopFacility> stopId: stopsToRemove)
            this.schedule.removeStopFacility(this.schedule.getFacilities().get(stopId));

        MinimalTransferTimes mtt =  this.schedule.getMinimalTransferTimes();
        MinimalTransferTimes.MinimalTransferTimesIterator itr = mtt.iterator();
        while(itr.hasNext()) {
            itr.next();
            if(!stopsToKeep.contains(itr.getFromStopId()) || !stopsToKeep.contains(itr.getToStopId()))
                mtt.remove(itr.getFromStopId(), itr.getToStopId());
        }

        stopsToKeep.clear();
        stopsToRemove.clear();
    }

    private void cleanNetwork() {
        Set<Id<Link>> linksToKeep = new HashSet<>();
        Set<Id<Link>> linksToRemove = new HashSet<>();

        for(TransitLine line: this.schedule.getTransitLines().values())   {
            for(TransitRoute route: line.getRoutes().values())  {
                linksToKeep.add(route.getRoute().getStartLinkId());
                linksToKeep.add(route.getRoute().getEndLinkId());
                for(Id<Link> linkId: route.getRoute().getLinkIds()) {
                    linksToKeep.add(linkId);
                }
            }
        }
        for(Id<Link> linkId: this.network.getLinks().keySet()) {
            if(!linksToKeep.contains(linkId))
                linksToRemove.add(linkId);
        }
        log.info("Cleared " + linksToRemove.size() + " unused links.");
        for(Id<Link> linkId: linksToRemove)
            this.network.removeLink(linkId);
        linksToKeep.clear();
        linksToRemove.clear();

        Set<Id<Node>> nodesToRemove = new HashSet<>();

        for(Node node: this.network.getNodes().values())  {
            if(node.getInLinks().size() == 0 && node.getOutLinks().size() == 0)
                nodesToRemove.add(node.getId());
        }
        log.info("Cleared " + nodesToRemove.size() + " unused nodes.");
        for(Id<Node> nodeId: nodesToRemove)
            this.network.removeNode(nodeId);
    }

    private void writeFiles()   {
        String outputPath = this.exporterConfig.getOutputPath();

        new NetworkWriter(this.network).write(new File(outputPath, NETWORK_OUT).getPath());
        new TransitScheduleWriter(this.schedule).writeFileV2(new File(outputPath, TRANSITSCHEDULE_OUT).getPath());
        new VehicleWriterV1(this.vehicles).writeFile(new File(outputPath, TRANSITVEHICLES_OUT).getPath());
    }
}