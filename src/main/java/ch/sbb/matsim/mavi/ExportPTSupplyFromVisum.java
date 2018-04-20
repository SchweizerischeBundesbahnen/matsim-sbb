/*
 * Copyright (C) Schweizerische Bundesbahnen SBB, 2017.
 */

package ch.sbb.matsim.mavi;

import com.jacob.activeX.ActiveXComponent;
import com.jacob.com.Dispatch;
import com.jacob.com.Variant;
import org.apache.log4j.Logger;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.network.NetworkFactory;
import org.matsim.api.core.v01.network.Node;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.network.io.NetworkWriter;
import org.matsim.core.population.routes.NetworkRoute;
import org.matsim.core.population.routes.RouteUtils;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.core.utils.misc.Time;
import org.matsim.pt.transitSchedule.api.Departure;
import org.matsim.pt.transitSchedule.api.TransitLine;
import org.matsim.pt.transitSchedule.api.TransitRoute;
import org.matsim.pt.transitSchedule.api.TransitRouteStop;
import org.matsim.pt.transitSchedule.api.TransitSchedule;
import org.matsim.pt.transitSchedule.api.TransitScheduleFactory;
import org.matsim.pt.transitSchedule.api.TransitScheduleWriter;
import org.matsim.pt.transitSchedule.api.TransitStopFacility;
import org.matsim.utils.objectattributes.ObjectAttributesXmlWriter;
import org.matsim.vehicles.Vehicle;
import org.matsim.vehicles.VehicleCapacity;
import org.matsim.vehicles.VehicleCapacityImpl;
import org.matsim.vehicles.VehicleType;
import org.matsim.vehicles.VehicleWriterV1;
import org.matsim.vehicles.Vehicles;
import org.matsim.vehicles.VehiclesFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/***
 *
 * @author pmanser / SBB
 *
 * IMPORTANT:
 * Download the JACOB library version 1.18 and
 * set path to the library in the VM Options (e.g. -Djava.library.path="C:\Users\u225744\Downloads\jacob-1.18\jacob-1.18")
 *
 */

public class ExportPTSupplyFromVisum {

    public final static String ATT_DATENHERKUNFT = "01_Datenherkunft";
    public final static String ATT_STOP_NO = "02_Stop_No";
    public final static String ATT_STOP_CODE = "03_Stop_Code";
    public final static String ATT_STOP_NAME = "04_Stop_Name";
    public final static String ATT_STOP_DIDOK_HAFAS = "05_Stop_Didok_Hafas";
    public final static String ATT_STOP_AREA_NO = "06_Stop_Area_No";

    public final static String ATT_TRANSITLINE = "02_TransitLine";
    public final static String ATT_LINEROUTENAME = "03_LineRouteName";
    public final static String ATT_DIRECTIONCODE = "04_DirectionCode";
    public final static String ATT_FZPNAME = "05_Name";
    public final static String ATT_OPERATOR_NAME= "06_OperatorName";
    public final static String ATT_OPERATOR_NO= "07_OperatorNo";
    public final static String ATT_TSYSNAME= "08_TSysName";

    private final static Set<String> MODES_PT = Collections.singleton(TransportMode.pt);

    private final static Logger log = Logger.getLogger(ExportPTSupplyFromVisum.class);

    private Scenario scenario;
    private Network network;
    private TransitSchedule schedule;
    private Vehicles vehicles;

    private TransitScheduleFactory scheduleBuilder;
    private NetworkFactory networkBuilder;
    private VehiclesFactory vehicleBuilder;

    private static final String PATHTOVISUM = "\\\\V00925\\Simba\\20_Modelle\\80_MatSim\\10_Modelle_vonDritten\\40_NPVM2016\\OEVAngebot_NPVM2016_Patrick.ver";

    // at the moment, the NPVM has the three options "2015", "2016" and "2030"
    private static final int SIMBASUPPLY = 2016;

    // we should use detPt for now as we do not make any differentiation between modes
    private static final boolean USEDETPTASMODE = true;

    // names of the output files
    private static final String NETWORKFILE = "NPVM_Output/transitNetwork" + SIMBASUPPLY + ".xml.gz";
    private static final String SCHEDULEFILE = "NPVM_Output/transitSchedule" + SIMBASUPPLY + ".xml.gz";
    private static final String VEHICLEFILE = "NPVM_Output/transitVehicles" + SIMBASUPPLY + ".xml.gz";
    private static final String STOPATTRIBUTES = "NPVM_Output/stopAttributes" + SIMBASUPPLY + ".xml.gz";
    private static final String ROUTEATTRIBUTES = "NPVM_Output/routeAttributes" + SIMBASUPPLY + ".xml.gz";

    public static void main(String[] args) { new ExportPTSupplyFromVisum(); }

    private ExportPTSupplyFromVisum() {
        ActiveXComponent visum = new ActiveXComponent("Visum.Visum.16");
        log.info("VISUM Client gestartet.");
        try {
            run(visum);
        } catch (Exception e) {
            log.error(e.getMessage(), e);
        } finally {
            visum.safeRelease();
        }
    }

    private void run(ActiveXComponent visum) {
        loadVersion(visum);
        createMATSimScenario();

        setFilter(visum);

        Dispatch net = Dispatch.get(visum, "Net").toDispatch();
        loadStopPoints(net);
        writeStopAttributes();
        createVehicleTypes(net);
        loadTransitLines(net);
        cleanStops();

        writeFiles();
    }

    private void loadVersion(ActiveXComponent visum) {
        log.info("LoadVersion started...");
        log.info("Start VISUM Client mit Version " + PATHTOVISUM);
        Dispatch.call(visum, "LoadVersion", new Object[] { new Variant(PATHTOVISUM) });
        log.info("LoadVersion finished");
    }

    private void createMATSimScenario()   {
        this.scenario = ScenarioUtils.createScenario(ConfigUtils.createConfig());
        log.info("MATSim scenario created");
        this.scheduleBuilder = scenario.getTransitSchedule().getFactory();
        log.info("Schedule builder initialized");
        this.vehicleBuilder = scenario.getTransitVehicles().getFactory();
        log.info("Vehicle builder initialized");
        this.networkBuilder = scenario.getNetwork().getFactory();
        log.info("Network builder initialized");

        this.network = this.scenario.getNetwork();
        this.schedule = this.scenario.getTransitSchedule();
        this.vehicles = this.scenario.getTransitVehicles();
    }

    private void setFilter(ActiveXComponent visum)    {
        Dispatch filters = Dispatch.get(visum, "Filters").toDispatch();
        Dispatch filter = Dispatch.call(filters, "LineGroupFilter").toDispatch();
        Dispatch tpFilter = Dispatch.call(filter, "TimeProfileFilter").toDispatch();
        Dispatch.call(tpFilter, "Init");
        switch ( SIMBASUPPLY ) {
            case 2015:
                Dispatch.call(tpFilter, "AddCondition", "OP_NONE",false,"LineRoute\\Line\\Datenherkunft",9,"Hafas_2015");
                Dispatch.call(tpFilter, "AddCondition", "OP_OR",false,"LineRoute\\Line\\Datenherkunft",9,"SBB_Simba.CH_2015");
                break;
            case 2016:
                Dispatch.call(tpFilter, "AddCondition", "OP_NONE",false,"LineRoute\\Line\\Datenherkunft",9,"Hafas_2015");
                Dispatch.call(tpFilter, "AddCondition", "OP_OR",false,"LineRoute\\Line\\Datenherkunft",9,"SBB_Simba.CH_2016");
                break;
            case 2030:
                Dispatch.call(tpFilter, "AddCondition", "OP_NONE",false,"LineRoute\\Line\\Datenherkunft",9,"Hafas_2015");
                Dispatch.call(tpFilter, "AddCondition", "OP_OR",false,"LineRoute\\Line\\Datenherkunft",9,"SBB_Simba.CH_Prognose");
                break;
            default:
                throw new IllegalArgumentException( "The model does not contain the SIMBA version " + SIMBASUPPLY );
        }
        Dispatch.put(filter, "UseFilterForTimeProfiles", true);
    }

    private void loadStopPoints(Dispatch net) {
        log.info("LoadStopPoints and CreateLoopLinks started...");
        Dispatch stopPoints = Dispatch.get(net, "StopPoints").toDispatch();
        Dispatch stopPointIterator = Dispatch.get(stopPoints, "Iterator").toDispatch();

        int nrOfStopPoints = Integer.valueOf(Dispatch.call(stopPoints, "Count").toString());
        int i = 0;

        while (i < nrOfStopPoints) {
            Dispatch item = Dispatch.get(stopPointIterator, "Item").toDispatch();

            // get the stop characteristics
            double stopPointNo = Double.valueOf(Dispatch.call(item, "AttValue", "No").toString());
            Id<TransitStopFacility> stopPointID = Id.create((int) stopPointNo, TransitStopFacility.class);
            double xCoord = Double.valueOf(Dispatch.call(item, "AttValue", "XCoord").toString());
            double yCoord = Double.valueOf(Dispatch.call(item, "AttValue", "YCoord").toString());
            Coord stopPointCoord = new Coord(xCoord, yCoord);
            String stopPointName = Dispatch.call(item, "AttValue", "Name").toString();

            // create stop node and loop link
            Id<Node> stopNodeID = Id.createNodeId("pt_" + ((int) stopPointNo));
            Node stopNode = this.networkBuilder.createNode(stopNodeID, stopPointCoord);
            this.network.addNode(stopNode);

            Id<Link> loopLinkID = Id.createLinkId("pt_" + ((int) stopPointNo));
            Link loopLink = this.networkBuilder.createLink(loopLinkID, stopNode, stopNode);
            loopLink.setLength(0.0);
            loopLink.setFreespeed(10000);
            loopLink.setCapacity(10000);
            loopLink.setNumberOfLanes(10000);
            loopLink.setAllowedModes(MODES_PT);
            this.network.addLink(loopLink);

            // create transitStopFacility
            TransitStopFacility st = this.scheduleBuilder.createTransitStopFacility(stopPointID, stopPointCoord, false);
            st.setName(stopPointName);
            st.setLinkId(loopLinkID);

            // custom stop attributes as identifiers
            String datenHerkunft = Dispatch.call(item, "AttValue", "Datenherkunft").toString();
            this.schedule.getTransitStopsAttributes().putAttribute(stopPointID.toString(),ATT_DATENHERKUNFT, datenHerkunft);
            double stopNo = Double.valueOf(Dispatch.call(item, "AttValue", "StopArea\\Stop\\No").toString());
            this.schedule.getTransitStopsAttributes().putAttribute(stopPointID.toString(),ATT_STOP_NO, (int) stopNo);
            String stopCode = Dispatch.call(item, "AttValue", "StopArea\\Stop\\Code").toString();
            if(!stopCode.isEmpty())
                this.schedule.getTransitStopsAttributes().putAttribute(stopPointID.toString(),ATT_STOP_CODE, stopCode);
            String stopName = Dispatch.call(item, "AttValue", "StopArea\\Stop\\Name").toString();
            if(!stopName.isEmpty())
                this.schedule.getTransitStopsAttributes().putAttribute(stopPointID.toString(),ATT_STOP_NAME, stopName);
            if(!Dispatch.call(item, "AttValue", "DIDOKNR_HAFAS").toString().equals("null")) {
                double stopDidokHafas = Double.valueOf(Dispatch.call(item, "AttValue", "DIDOKNR_HAFAS").toString());
                this.schedule.getTransitStopsAttributes().putAttribute(stopPointID.toString(), ATT_STOP_DIDOK_HAFAS, (int) stopDidokHafas);
            }
            double stopAreaNo = Double.valueOf(Dispatch.call(item, "AttValue", "StopAreaNo").toString());
            this.schedule.getTransitStopsAttributes().putAttribute(stopPointID.toString(),ATT_STOP_AREA_NO, (int) stopAreaNo);

            this.schedule.addStopFacility(st);

            i++;
            Dispatch.call(stopPointIterator, "Next");
        }

        log.info("Finished LoadStopPoints and CreateLoopLinks");
        log.info("Added " + nrOfStopPoints + " Stop Points");
    }

    private void writeStopAttributes()  {
        log.info("Writing out the stop attributes file and cleaning the scenario");
        new ObjectAttributesXmlWriter(this.schedule.getTransitStopsAttributes()).writeFile(STOPATTRIBUTES);

        // remove stop attributes file because of memory issues
        for(Id<TransitStopFacility> stopID: this.schedule.getFacilities().keySet())   {
            this.schedule.getTransitStopsAttributes().removeAllAttributes(stopID.toString());
        }
        log.info("Finished the scenario cleaning");
    }

    private void createVehicleTypes(Dispatch net) {
        log.info("Loading vehicle types...");
        Dispatch tSystems = Dispatch.get(net, "TSystems").toDispatch();
        Dispatch tSystemsIterator = Dispatch.get(tSystems, "Iterator").toDispatch();

        int nrOfTSystems = Integer.valueOf(Dispatch.call(tSystems, "Count").toString());
        int i = 0;

        while (i < nrOfTSystems) {
            Dispatch item = Dispatch.get(tSystemsIterator, "Item").toDispatch();

            String tSysCode = Dispatch.call(item, "AttValue", "Code").toString();
            Id<VehicleType> vehicleTypeId = Id.create(tSysCode, VehicleType.class);

            // TODO we need much more sophisticated values based on reference data
            VehicleType vehicleType = this.vehicleBuilder.createVehicleType(vehicleTypeId);
            String tSysName = Dispatch.call(item, "AttValue", "Name").toString();
            vehicleType.setDescription(tSysName);
            vehicleType.setDoorOperationMode(VehicleType.DoorOperationMode.serial);
            VehicleCapacity vehicleCapacity = new VehicleCapacityImpl();
            vehicleCapacity.setStandingRoom(500);
            vehicleCapacity.setSeats(2000);
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
        // Fahrzeitprofile
        Dispatch timeProfiles = Dispatch.get(net, "TimeProfiles").toDispatch();
        log.info("Loading transit routes started...");
        Dispatch timeProfileIterator = Dispatch.get(timeProfiles, "Iterator").toDispatch();

        int nrOfTimeProfiles = Integer.valueOf(Dispatch.call(timeProfiles, "CountActive").toString());
        log.info("Number of active time profiles: " + nrOfTimeProfiles);
        int i = 0;

        //while (i < 150) { // for test purposes
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
            if(USEDETPTASMODE)
                mode = "detPt";
            else
                mode = Dispatch.call(item, "AttValue", "TSysCode").toString();

            int nrOfVehicleJourneys = Integer.valueOf(Dispatch.call(vehicleJourneys, "Count").toString());
            int k = 0;

            while (k < nrOfVehicleJourneys) {
                Dispatch item_ = Dispatch.get(vehicleJourneyIterator, "Item").toDispatch();

                double routeName = Double.valueOf(Dispatch.call(item, "AttValue", "ID").toString());
                double from_tp_index = Double.valueOf(Dispatch.call(item_, "AttValue", "FromTProfItemIndex").toString());
                double to_tp_index = Double.valueOf(Dispatch.call(item_, "AttValue", "ToTProfItemIndex").toString());
                Id<TransitRoute> routeID = Id.create(((int) routeName) + "_" + ((int) from_tp_index)
                        + "_" + ((int) to_tp_index), TransitRoute.class);
                TransitRoute route;

                if(!this.schedule.getTransitLines().get(lineID).getRoutes().containsKey(routeID)) {

                    // custom route identifiers
                    String datenHerkunft = Dispatch.call(item, "AttValue", "LineRoute\\Line\\Datenherkunft").toString();
                    this.schedule.getTransitLinesAttributes().putAttribute(routeID.toString(), ATT_DATENHERKUNFT, datenHerkunft);
                    this.schedule.getTransitLinesAttributes().putAttribute(routeID.toString(), ATT_TRANSITLINE, lineName);
                    String lineRouteName = Dispatch.call(item, "AttValue", "LineRouteName").toString();
                    this.schedule.getTransitLinesAttributes().putAttribute(routeID.toString(), ATT_LINEROUTENAME, lineRouteName);
                    String richtungsCode = Dispatch.call(item, "AttValue", "DirectionCode").toString();
                    this.schedule.getTransitLinesAttributes().putAttribute(routeID.toString(), ATT_DIRECTIONCODE, richtungsCode);
                    String name = Dispatch.call(item, "AttValue", "Name").toString();
                    this.schedule.getTransitLinesAttributes().putAttribute(routeID.toString(), ATT_FZPNAME, name);
                    String operatorName = Dispatch.call(item, "AttValue", "LineRoute\\Line\\Operator\\Name").toString();
                    this.schedule.getTransitLinesAttributes().putAttribute(routeID.toString(), ATT_OPERATOR_NAME, operatorName);
                    if(!Dispatch.call(item, "AttValue", "LineRoute\\Line\\Operator\\No").toString().equals("null")) {
                        double operatorNo = Double.valueOf(Dispatch.call(item, "AttValue", "LineRoute\\Line\\Operator\\No").toString());
                        this.schedule.getTransitLinesAttributes().putAttribute(routeID.toString(), ATT_OPERATOR_NO, (int) operatorNo);
                    }
                    String tSysName = Dispatch.call(item, "AttValue", "LineRoute\\Line\\TSysName").toString();
                    this.schedule.getTransitLinesAttributes().putAttribute(routeID.toString(), ATT_TSYSNAME, tSysName);

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

                    int nrFZPVerlaufe = Integer.valueOf(Dispatch.call(fzpVerlaufe, "Count").toString());
                    int l = 0;
                    boolean isFirstRouteStop = true;

                    while (l < nrFZPVerlaufe) {
                        Dispatch item__ = Dispatch.get(fzpVerlaufIterator, "Item").toDispatch();

                        double stopPointNo = Double.valueOf(Dispatch.call(item__, "AttValue", "LineRouteItem\\StopPointNo").toString());

                        double index = Double.valueOf(Dispatch.call(item__, "AttValue", "Index").toString());
                        if(from_tp_index > index || to_tp_index < index)    {
                            l++;
                            Dispatch.call(fzpVerlaufIterator, "Next");
                            continue;
                        }
                        else if(from_tp_index == index) {
                            startLink = Id.createLinkId("pt_" + ((int) stopPointNo));
                            delta = Double.valueOf(Dispatch.call(item__, "AttValue", "Dep").toString());
                        }
                        else if(to_tp_index == index) { endLink = Id.createLinkId("pt_" + ((int) stopPointNo)); }

                        Id<TransitStopFacility> stopID = Id.create((int) stopPointNo, TransitStopFacility.class);
                        TransitStopFacility stop = this.schedule.getFacilities().get(stopID);

                        double arrTime = Double.valueOf(Dispatch.call(item__, "AttValue", "Arr").toString());
                        double depTime = Double.valueOf(Dispatch.call(item__, "AttValue", "Dep").toString());
                        TransitRouteStop rst;
                        if(isFirstRouteStop) {
                            rst = this.scheduleBuilder.createTransitRouteStop(stop, Time.getUndefinedTime(), depTime - delta);
                            isFirstRouteStop = false;
                        }
                        else {
                            rst = this.scheduleBuilder.createTransitRouteStop(stop, arrTime - delta, depTime - delta);
                        }
                        rst.setAwaitDepartureTime(true);
                        transitRouteStops.add(rst);

                        if(fromStop != null) {
                            Node fromNode = this.network.getLinks().get(fromStop.getLinkId()).getFromNode();
                            Node toNode = this.network.getLinks().get(stop.getLinkId()).getFromNode();
                            Id<Link> newLinkID = Id.createLinkId(fromNode.getId().toString() + "-" + toNode.getId().toString());
                            if(!this.network.getLinks().containsKey(newLinkID)) {
                                Link newLink = this.networkBuilder.createLink(newLinkID, fromNode, toNode);
                                newLink.setLength(postlength * 1000);
                                newLink.setFreespeed(10000);
                                newLink.setCapacity(10000);
                                newLink.setNumberOfLanes(10000);
                                newLink.setAllowedModes(MODES_PT);
                                this.network.addLink(newLink);
                            }
                            // differentiate between links with the same from- and to-node but different length
                            else    {
                                boolean hasLinkWithSameLength = false;
                                if(this.network.getLinks().get(newLinkID).getLength() != postlength * 1000) {
                                    int m = 1;
                                    Id<Link> linkID = Id.createLinkId(fromNode.getId().toString() + "-" + toNode.getId().toString() + "." + m);
                                    while (this.network.getLinks().containsKey(linkID)) {
                                        if(this.network.getLinks().get(linkID).getLength() == postlength * 1000) {
                                            hasLinkWithSameLength = true;
                                            break;
                                        }
                                        m++;
                                        linkID = Id.createLinkId(fromNode.getId().toString() + "-" + toNode.getId().toString() + "." + m);
                                    }
                                    if(!hasLinkWithSameLength)  {
                                        Link link = this.networkBuilder.createLink(linkID, fromNode, toNode);
                                        link.setLength(postlength * 1000);
                                        link.setFreespeed(10000);
                                        link.setCapacity(10000);
                                        link.setNumberOfLanes(10000);
                                        link.setAllowedModes(MODES_PT);
                                        this.network.addLink(link);
                                        newLinkID = linkID;
                                    }
                                }
                            }
                            routeLinks.add(newLinkID);
                            routeLinks.add(stop.getLinkId());
                        }
                        postlength = Double.valueOf(Dispatch.call(item__, "AttValue", "PostLength").toString());
                        fromStop = stop;

                        l++;
                        Dispatch.call(fzpVerlaufIterator, "Next");
                    }
                    routeLinks.remove(routeLinks.size() - 1);
                    NetworkRoute netRoute = RouteUtils.createLinkNetworkRouteImpl(startLink, routeLinks, endLink);

                    route = this.scheduleBuilder.createTransitRoute(routeID, netRoute, transitRouteStops, mode);

                    this.schedule.getTransitLines().get(lineID).addRoute(route);
                }
                else    {
                    route = this.schedule.getTransitLines().get(lineID).getRoutes().get(routeID);
                }

                double depName = Double.valueOf(Dispatch.call(item_, "AttValue", "No").toString());
                Id<Departure> depID = Id.create((int) depName, Departure.class);
                double depTime = Double.valueOf(Dispatch.call(item_, "AttValue", "Dep").toString());
                Departure dep = this.scheduleBuilder.createDeparture(depID, depTime);

                Id<Vehicle> vehicleId = Id.createVehicleId(depID.toString());
                dep.setVehicleId(vehicleId);
                route.addDeparture(dep);

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

        for(Id<TransitStopFacility> stopId: stopsToRemove)  {
            this.schedule.removeStopFacility(this.schedule.getFacilities().get(stopId));

            Id<Node> stopNodeID = Id.createNodeId("pt_" + stopId.toString());
            Id<Link> loopLinkID = Id.createLinkId("pt_" + stopId.toString());
            this.network.removeNode(stopNodeID);
            this.network.removeLink(loopLinkID);
        }
    }

    private void writeFiles()   {
        new NetworkWriter(this.network).write(NETWORKFILE);
        new TransitScheduleWriter(this.schedule).writeFile(SCHEDULEFILE);
        new VehicleWriterV1(this.vehicles).writeFile(VEHICLEFILE);
        new ObjectAttributesXmlWriter(this.schedule.getTransitLinesAttributes()).writeFile(ROUTEATTRIBUTES);
    }
}
