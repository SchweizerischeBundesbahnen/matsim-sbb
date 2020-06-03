package ch.sbb.matsim.mavi.pt;

import ch.sbb.matsim.csv.CSVWriter;
import ch.sbb.matsim.mavi.visum.Visum;
import org.apache.log4j.Logger;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.network.NetworkFactory;
import org.matsim.api.core.v01.network.Node;
import org.matsim.core.population.routes.NetworkRoute;
import org.matsim.core.population.routes.RouteUtils;
import org.matsim.core.utils.misc.Time;
import org.matsim.pt.transitSchedule.api.Departure;
import org.matsim.pt.transitSchedule.api.TransitLine;
import org.matsim.pt.transitSchedule.api.TransitRoute;
import org.matsim.pt.transitSchedule.api.TransitRouteStop;
import org.matsim.pt.transitSchedule.api.TransitSchedule;
import org.matsim.pt.transitSchedule.api.TransitScheduleFactory;
import org.matsim.pt.transitSchedule.api.TransitStopFacility;
import org.matsim.utils.objectattributes.attributable.Attributes;
import org.matsim.vehicles.Vehicle;
import org.matsim.vehicles.VehicleCapacity;
import org.matsim.vehicles.VehicleType;
import org.matsim.vehicles.VehicleType.DoorOperationMode;
import org.matsim.vehicles.VehicleUtils;
import org.matsim.vehicles.Vehicles;
import org.matsim.vehicles.VehiclesFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.IntStream;

public class TimeProfileExporter {

    private static final Logger log = Logger.getLogger(TimeProfileExporter.class);

    private final NetworkFactory networkBuilder;
    private final TransitScheduleFactory scheduleBuilder;
    private final Vehicles vehicles;
    private final VehiclesFactory vehicleBuilder;
    private Network network;
    private TransitSchedule schedule;
    public Map<Id<Link>, String> linkToVisumSequence = new HashMap<>();

    public TimeProfileExporter(Scenario scenario)   {
        this.network = scenario.getNetwork();
        this.schedule = scenario.getTransitSchedule();
        this.vehicles = scenario.getVehicles();
        this.networkBuilder = this.network.getFactory();
        this.scheduleBuilder = this.schedule.getFactory();
        this.vehicleBuilder = scenario.getVehicles().getFactory();
    }

    private static HashMap<Integer, TimeProfile> loadTimeProfileInfos(Visum visum, VisumPtExporterConfigGroup config) {
        HashMap<Integer, TimeProfile> timeProfileMap = new HashMap<>();

        // time profiles
        Visum.ComObject timeProfiles = visum.getNetObject("TimeProfiles");
        int nrOfTimeProfiles = timeProfiles.countActive();
        String[][] timeProfileAttributes = Visum.getArrayFromAttributeList(nrOfTimeProfiles, timeProfiles,
                "ID", "LineName", "LineRoute\\Line\\Datenherkunft", "TSysCode",
                "LineRoute\\Line\\TSys\\TSys_MOBi");
        String[][] customAttributes = Visum.getArrayFromAttributeList(nrOfTimeProfiles, timeProfiles,
                config.getRouteAttributeParams().values().stream().
                        map(VisumPtExporterConfigGroup.RouteAttributeParams::getAttributeValue).
                        toArray(String[]::new));

        for (int tp = 0; tp < nrOfTimeProfiles; tp++) {
            timeProfileMap.put((int) Double.parseDouble(timeProfileAttributes[tp][0]),
                    new TimeProfile(timeProfileAttributes[tp][1],
                            timeProfileAttributes[tp][2],
                            timeProfileAttributes[tp][3],
                            timeProfileAttributes[tp][4],
                            customAttributes[tp]));
        }

        // vehicles journeys
        Visum.ComObject vehJourneys = visum.getNetObject("VehicleJourneys");
        int nrOfVehJourneys = vehJourneys.countActive();
        String[][] vehJourneyAttributes = Visum.getArrayFromAttributeList(nrOfVehJourneys, vehJourneys,
                "TimeProfile\\ID", "No", "FromTProfItemIndex", "ToTProfItemIndex", "Dep", "VehCapacity", "StandingRoom");
        for (int vj = 0; vj < nrOfVehJourneys; vj++) {
            TimeProfile tp = timeProfileMap.get((int) Double.parseDouble(vehJourneyAttributes[vj][0]));
            if (tp == null)
                log.info((int) Double.parseDouble(vehJourneyAttributes[vj][0]));
            tp.addVehicleJourney(new VehicleJourney((int) Double.parseDouble(vehJourneyAttributes[vj][1]),
                    (int) Double.parseDouble(vehJourneyAttributes[vj][2]),
                    (int) Double.parseDouble(vehJourneyAttributes[vj][3]),
                    Double.parseDouble(vehJourneyAttributes[vj][4]),
                    (int) Double.parseDouble(vehJourneyAttributes[vj][5].isEmpty() ? "-1" : vehJourneyAttributes[vj][5]),
                    (int) Double.parseDouble(vehJourneyAttributes[vj][6].isEmpty() ? "-1" : vehJourneyAttributes[vj][6])));
        }

        // time profile items
        Visum.ComObject timeProfileItems = visum.getNetObject("TimeProfileItems");
        int nrOfTimeProfileItems = timeProfileItems.countActive();
        String[][] timeProfileItemAttributes = Visum.getArrayFromAttributeList(nrOfTimeProfileItems, timeProfileItems,
                "TimeProfile\\ID", "Index", "LineRouteItem\\StopPointNo", "Dep", "Arr", "PostLength",
                "Concatenate:UsedLineRouteItems\\OutLink\\No");
        for (int tpi = 0; tpi < nrOfTimeProfileItems; tpi++) {
            String[] linkSeq = timeProfileItemAttributes[tpi][6].split(",");
            List<String> linkSeqList = new ArrayList<>();
            linkSeqList.add(linkSeq[0]);
            for (int i = 1; i < linkSeq.length; i++) {
                if (!linkSeq[i].equals(linkSeq[i - 1])) {
                    linkSeqList.add(linkSeq[i]);
                }
            }
            TimeProfile tp = timeProfileMap.get((int) Double.parseDouble(timeProfileItemAttributes[tpi][0]));
            tp.addTimeProfileItem(new TimeProfileItem((int) Double.parseDouble(timeProfileItemAttributes[tpi][1]),
                    (int) Double.parseDouble(timeProfileItemAttributes[tpi][2]),
                    Double.parseDouble(timeProfileItemAttributes[tpi][3]),
                    Double.parseDouble(timeProfileItemAttributes[tpi][4]),
                    Double.parseDouble(timeProfileItemAttributes[tpi][5]),
                    String.join(", ", linkSeqList)));
        }

        return timeProfileMap;
    }

    public void writeLinkSequence(String outputfolder) {
        try (CSVWriter writer = new CSVWriter("", new String[]{"matsim_link", "link_sequence_visum"},
                outputfolder + "/link_sequences.csv")) {
            for (Id<Link> linkId : this.linkToVisumSequence.keySet()) {
                writer.set("matsim_link", linkId.toString());
                writer.set("link_sequence_visum", this.linkToVisumSequence.get(linkId));
                writer.writeRow();
            }
        } catch (IOException e) {
            log.error("Could not write file. " + e.getMessage(), e);
        }
    }

    private Link createLink(Id<Link> linkId, Node fromNode, Node toNode, String mode, double length) {
        Link link = this.networkBuilder.createLink(linkId, fromNode, toNode);
        link.setLength(length * 1000);
        link.setFreespeed(10000);
        link.setCapacity(10000);
        link.setNumberOfLanes(10000);
        link.setAllowedModes(Collections.singleton(mode));
        this.network.addLink(link);
        return link;
    }

    public void createTransitLines(Visum visum, VisumPtExporterConfigGroup config)   {
        log.info("Loading all informations about transit lines...");
        HashMap<Integer, TimeProfile> timeProfileMap = loadTimeProfileInfos(visum, config);
        log.info("finished loading all informations for transit lines...");

        for (Map.Entry<Integer, TimeProfile> entrySet: timeProfileMap.entrySet())   {
            int tpId = entrySet.getKey();
            TimeProfile tp = entrySet.getValue();

            String lineName = tp.lineName;
            Id<TransitLine> lineID = Id.create(lineName, TransitLine.class);
            if(!this.schedule.getTransitLines().containsKey(lineID)) {
                TransitLine line = this.scheduleBuilder.createTransitLine(lineID);
                this.schedule.addTransitLine(line);
            }

            String mode = tp.tSysMOBi.toLowerCase();
            tp.vehicleJourneys.forEach(vj -> {
                int routeName = tpId;
                int from_tp_index = vj.fromTProfItemIndex;
                int to_tp_index = vj.toTProfItemIndex;
                Id<TransitRoute> routeID = Id.create(routeName + "_" + from_tp_index + "_" + to_tp_index, TransitRoute.class);
                TransitRoute route;

                if(!this.schedule.getTransitLines().get(lineID).getRoutes().containsKey(routeID)) {
                    // Fahrzeitprofil-Verläufe
                    List<TransitRouteStop> transitRouteStops = new ArrayList<>();
                    List<Id<Link>> routeLinks = new ArrayList<>();
                    Id<Link> startLink = null;
                    Id<Link> endLink = null;
                    TransitStopFacility fromStop = null;
                    String prevLinkSeq = null;
                    double postlength = 0.0;
                    double delta = 0.0;
                    boolean isFirstRouteStop = true;

                    for(TimeProfileItem tpi: tp.timeProfileItems)   {
                        int stopPointNo = tpi.stopPoint;

                        int index = tpi.index;
                        if(from_tp_index > index || to_tp_index < index)    {
                            continue;
                        }
                        else if(from_tp_index == index) {
                            startLink = Id.createLinkId(config.getNetworkMode() + "_" + stopPointNo);
                            delta = tpi.dep;
                        }
                        else if(to_tp_index == index) { endLink = Id.createLinkId(config.getNetworkMode() + "_" + stopPointNo); }

                        Id<TransitStopFacility> stopID = Id.create(stopPointNo, TransitStopFacility.class);
                        TransitStopFacility stop = this.schedule.getFacilities().get(stopID);

                        double arrTime = tpi.arr;
                        double depTime = tpi.dep;
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
                            // non-routed links (fly from stop to stop)
                            Node fromNode = this.network.getLinks().get(fromStop.getLinkId()).getFromNode();
                            Node toNode = this.network.getLinks().get(stop.getLinkId()).getFromNode();
                            Id<Link> newLinkID = Id.createLinkId(fromNode.getId().toString() + "-" + toNode.getId().toString());
                            if (!this.network.getLinks().containsKey(newLinkID)) {
                                createLink(newLinkID, fromNode, toNode, mode, postlength);
                                this.linkToVisumSequence.put(newLinkID, prevLinkSeq);
                            }
                            // differentiate between links with the same from- and to-node but different length
                            else {
                                boolean hasSameLinkSequence = false;
                                if(!this.linkToVisumSequence.get(newLinkID).equals(prevLinkSeq)) {
                                    int m = 1;
                                    Id<Link> linkID = Id.createLinkId(fromNode.getId().toString() + "-" + toNode.getId().toString() + "." + m);
                                    while (this.network.getLinks().containsKey(linkID)) {
                                        if (this.linkToVisumSequence.get(linkID).equals(prevLinkSeq)) {
                                            hasSameLinkSequence = true;
                                            newLinkID = linkID;
                                            Link link = this.network.getLinks().get(newLinkID);
                                            Set<String> allowedModesOld = link.getAllowedModes();
                                            if (!allowedModesOld.contains(mode)) {
                                                Set<String> allowedModesNew = new HashSet<>(allowedModesOld);
                                                allowedModesNew.add(mode);
                                                link.setAllowedModes(allowedModesNew);
                                            }
                                            break;
                                        }
                                        m++;
                                        linkID = Id.createLinkId(fromNode.getId().toString() + "-" + toNode.getId().toString() + "." + m);

                                    }
                                    if (!hasSameLinkSequence) {
                                        createLink(linkID, fromNode, toNode, mode, postlength);
                                        this.linkToVisumSequence.put(linkID, prevLinkSeq);
                                        newLinkID = linkID;
                                    }
                                }
                            }
                            routeLinks.add(newLinkID);
                            routeLinks.add(stop.getLinkId());
                        }
                        postlength = tpi.length;
                        fromStop = stop;
                        prevLinkSeq = tpi.linkSequence;
                    }
                    routeLinks.remove(routeLinks.size() - 1);
                    NetworkRoute netRoute = RouteUtils.createLinkNetworkRouteImpl(startLink, endLink);
                    netRoute.setLinkIds(startLink, routeLinks, endLink);

                    route = this.scheduleBuilder.createTransitRoute(routeID, netRoute, transitRouteStops, mode);

                    this.schedule.getTransitLines().get(lineID).addRoute(route);
                }
                else    {
                    route = this.schedule.getTransitLines().get(lineID).getRoutes().get(routeID);
                }

                int depName = vj.no;
                Id<Departure> depID = Id.create(depName, Departure.class);
                double depTime = vj.dep;
                Departure dep = this.scheduleBuilder.createDeparture(depID, depTime);

                Id<Vehicle> vehicleId = Id.createVehicleId(depID.toString());
                dep.setVehicleId(vehicleId);
                route.addDeparture(dep);

                String[] values = tp.customAttributes;
                List<VisumPtExporterConfigGroup.RouteAttributeParams> custAttNames = new ArrayList<>(config.getRouteAttributeParams().values());
                IntStream.range(0, values.length).forEach(j -> addAttribute(route.getAttributes(), custAttNames.get(j).getAttributeName(),
                        values[j], custAttNames.get(j).getDataType()));

                VehicleType vehType = getVehicleType(tp.tSysCode, vj.vehCapacity, vj.standingRoom);
                Vehicle vehicle = this.vehicleBuilder.createVehicle(vehicleId, vehType);
                this.vehicles.addVehicle(vehicle);
            });
        }
        log.info("Loading transit routes finished");
    }

    private VehicleType getVehicleType(String tSysCode, int capacity, int standingRoom) {
        Id<VehicleType> vehicleTypeId = Id.create(tSysCode + "_" + capacity + "_" + standingRoom, VehicleType.class);
        VehicleType vehType = this.vehicles.getVehicleTypes().get(vehicleTypeId);
        if (vehType == null) {
            vehType = this.vehicleBuilder.createVehicleType(vehicleTypeId);
            vehType.setDescription(tSysCode);
            VehicleUtils.setDoorOperationMode(vehType, DoorOperationMode.serial);
            VehicleCapacity vehicleCapacity = vehType.getCapacity();
            if (capacity < 0) {
                vehicleCapacity.setSeats(150); // default in case of missing value
            } else {
                vehicleCapacity.setSeats(capacity);
            }
            if (standingRoom < 0) {
                vehicleCapacity.setStandingRoom(50); // default in case of missing value
            } else {
                vehicleCapacity.setStandingRoom(standingRoom);
            }
            if (capacity == 0 && standingRoom == 0) {
                log.warn("There exists a vehicle type with capacity and standingRoom both = 0. tSysCode = " + tSysCode);
            }

            // the following parameters do not have any influence in a deterministic simulation engine
            vehType.setLength(10);
            vehType.setWidth(2);
            vehType.setPcuEquivalents(1);
            vehType.setMaximumVelocity(10000);
            this.vehicles.addVehicleType(vehType);
        }
        return vehType;
    }

    private static void addAttribute(Attributes attributes, String name, String value, String dataType)  {
        if(!value.isEmpty() && !value.equals("null"))    {
            switch ( dataType ) {
                case "java.lang.String":
                    attributes.putAttribute(name, value);
                    break;
                case "java.lang.Double":
                    attributes.putAttribute(name, Double.parseDouble(value));
                    break;
                case "java.lang.Integer":
                    attributes.putAttribute(name, (int) Double.parseDouble(value));
                    break;
                default:
                    throw new IllegalArgumentException( dataType );
            }
        }
    }

    private static class TimeProfile {
        final String lineName;
        final String datenHerkunft;
        final String tSysCode;
        final String tSysMOBi;
        final ArrayList<VehicleJourney> vehicleJourneys;
        final ArrayList<TimeProfileItem> timeProfileItems;
        final String[] customAttributes;

        public TimeProfile(String lineName, String datenHerkunft, String tSysCode, String tSysMOBi, String[] customAttributes) {
            this.lineName = lineName;
            this.datenHerkunft = datenHerkunft;
            this.tSysCode = tSysCode;
            this.tSysMOBi = tSysMOBi;
            this.customAttributes = customAttributes;
            this.vehicleJourneys = new ArrayList<>();
            this.timeProfileItems = new ArrayList<>();
        }

        public void addVehicleJourney(VehicleJourney vj)    {
            this.vehicleJourneys.add(vj);
        }

        public void addTimeProfileItem(TimeProfileItem tpi) {
            this.timeProfileItems.add(tpi);
        }
    }

    private static class VehicleJourney {
        final int no;
        final int fromTProfItemIndex;
        final int toTProfItemIndex;
        final double dep;
        final int vehCapacity;
        final int standingRoom;

        public VehicleJourney(int no, int fromTProfItemIndex, int toTProfItemIndex, double dep, int vehCapacity, int standingRoom) {
            this.no = no;
            this.fromTProfItemIndex = fromTProfItemIndex;
            this.toTProfItemIndex = toTProfItemIndex;
            this.dep = dep;
            this.vehCapacity = vehCapacity;
            this.standingRoom = standingRoom;
        }
    }

    private static class TimeProfileItem {
        final int index;
        final int stopPoint;
        final double dep;
        final double arr;
        final double length;
        final String linkSequence;

        public TimeProfileItem(int index, int stopPoint, double dep, double arr, double length, String linkSequence) {
            this.index = index;
            this.stopPoint = stopPoint;
            this.dep = dep;
            this.arr = arr;
            this.length = length;
            this.linkSequence = linkSequence;
        }
    }
}