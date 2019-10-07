/*
 * Copyright (C) Schweizerische Bundesbahnen SBB, 2017.
 */

package ch.sbb.matsim.analysis;

import ch.sbb.matsim.analysis.VisumPuTSurvey.VisumPuTSurvey;
import ch.sbb.matsim.analysis.travelcomponents.Activity;
import ch.sbb.matsim.analysis.travelcomponents.TravelledLeg;
import ch.sbb.matsim.analysis.travelcomponents.TravellerChain;
import ch.sbb.matsim.analysis.travelcomponents.Trip;
import ch.sbb.matsim.config.PostProcessingConfigGroup;
import ch.sbb.matsim.csv.CSVWriter;
import ch.sbb.matsim.zones.Zone;
import ch.sbb.matsim.zones.Zones;
import ch.sbb.matsim.zones.ZonesCollection;
import ch.sbb.matsim.zones.ZonesQueryCache;
import org.apache.log4j.Logger;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.events.*;
import org.matsim.api.core.v01.events.handler.*;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.population.Person;
import org.matsim.core.api.experimental.events.TeleportationArrivalEvent;
import org.matsim.core.api.experimental.events.VehicleArrivesAtFacilityEvent;
import org.matsim.core.api.experimental.events.VehicleDepartsAtFacilityEvent;
import org.matsim.core.api.experimental.events.handler.TeleportationArrivalEventHandler;
import org.matsim.core.api.experimental.events.handler.VehicleArrivesAtFacilityEventHandler;
import org.matsim.core.api.experimental.events.handler.VehicleDepartsAtFacilityEventHandler;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.gbl.MatsimRandom;
import org.matsim.core.utils.misc.Counter;
import org.matsim.pt.PtConstants;
import org.matsim.pt.transitSchedule.api.Departure;
import org.matsim.pt.transitSchedule.api.TransitLine;
import org.matsim.pt.transitSchedule.api.TransitRoute;
import org.matsim.pt.transitSchedule.api.TransitSchedule;
import org.matsim.vehicles.Vehicle;

import java.io.IOException;
import java.util.*;
import java.util.Map.Entry;

/**
 * @author pieterfourie, sergioo
 * <p>
 * Converts events into trips, legs/stages, transfers and activities
 * tables. Originally designed for transit scenarios with full transit
 * simulation, but should work with most teleported modes
 * </p>
 */

public class EventsToTravelDiaries implements
        TransitDriverStartsEventHandler, PersonEntersVehicleEventHandler,
        PersonLeavesVehicleEventHandler, PersonDepartureEventHandler,
        PersonArrivalEventHandler, ActivityStartEventHandler,
        ActivityEndEventHandler, PersonStuckEventHandler,
        LinkEnterEventHandler, LinkLeaveEventHandler,
        TeleportationArrivalEventHandler, VehicleArrivesAtFacilityEventHandler,
        VehicleDepartsAtFacilityEventHandler,
        EventsAnalysis {

    private static final Logger log = Logger.getLogger(EventsToTravelDiaries.class);

    private final Network network;

    private String filename;

    private Map<Id<Person>, TravellerChain> chains = new HashMap<>();
    private Map<Id<Vehicle>, PTVehicle> ptVehicles = new HashMap<>();
    private HashSet<Id<Person>> transitDriverIds = new HashSet<>();
    private HashMap<Id<Vehicle>, Id<Person>> driverIdFromVehicleId = new HashMap<>();
    private int stuck = 0;
    private TransitSchedule transitSchedule;
    private final boolean isTransitScenario;
    private boolean writeVisumPuTSurvey = false;
    private Zones zones = null;
    private String zoneAttribute = null;
    private Config config;
    private Scenario scenario;


    public EventsToTravelDiaries(Scenario scenario, String filename, ZonesCollection allZones) {
        this.filename = filename;
        this.scenario = scenario;

        this.network = scenario.getNetwork();
        this.isTransitScenario = scenario.getConfig().transit().isUseTransit();

        if (this.isTransitScenario) {
            this.transitSchedule = scenario.getTransitSchedule();
            readVehiclesFromSchedule();
        }
        this.config = scenario.getConfig();
        PostProcessingConfigGroup ppConfig = ConfigUtils.addOrGetModule(this.config, PostProcessingConfigGroup.class);

        if (ppConfig.getMapActivitiesToZone()) {
            Zones zones = allZones.getZones(ppConfig.getZonesId());
            this.setMapActToZone(zones, ppConfig.getZoneAttribute());
        }

        if (ppConfig.getWriteVisumPuTSurvey()) {
            this.writeVisumPuTSurvey = true;
        }
    }

    private void readVehiclesFromSchedule() {
        for (TransitLine tL : this.transitSchedule.getTransitLines().values()) {
            for (TransitRoute tR : tL.getRoutes().values()) {
                for (Departure dep : tR.getDepartures().values()) {
                    Id<org.matsim.vehicles.Vehicle> vehicleId = dep.getVehicleId();
                    if (ptVehicles.containsKey(vehicleId)) {
                        log.error("vehicleId already in Map!");
                    } else {
                        this.ptVehicles.put(vehicleId, new PTVehicle(tL.getId(), tR.getId()));
                    }
                }
            }
        }
    }

    private boolean isTransitDriver(Id<Person> personId) {
        return isTransitScenario && transitDriverIds.contains(personId);
    }

    @Override
    public void handleEvent(ActivityEndEvent event) {
        try {
            if (isTransitDriver(event.getPersonId())) {
                return;
            }
            TravellerChain chain = chains.get(event.getPersonId());
            if (chain == null) {
                chain = new TravellerChain(this.config);
                chains.put(event.getPersonId(), chain);
                Activity act = chain.addActivity();
                act.setCoord(network.getLinks().get(event.getLinkId()).getCoord());
                act.setEndTime(event.getTime());
                act.setFacility(event.getFacilityId());
                act.setStartTime(0.0);
                act.setType(event.getActType());

            } else if (!chain.isInPT()) {
                Activity act = chain.getLastActivity();
                act.setEndTime(event.getTime());
            }
        } catch (Exception e) {
            log.error("Exception while handling event " + event.toString(), e);
        }
    }

    @Override
    public void handleEvent(ActivityStartEvent event) {
        try {
            if (isTransitDriver(event.getPersonId())) {
                return;
            }
            TravellerChain chain = chains.get(event.getPersonId());
            if (event.getActType().equals(PtConstants.TRANSIT_ACTIVITY_TYPE) || event.getActType().contains("interaction")) {
                chain.setInPT(true);

            } else {
                chain.setInPT(false);
                Activity act = chain.addActivity();
                act.setCoord(network.getLinks().get(event.getLinkId()).getCoord());
                act.setFacility(event.getFacilityId());
                act.setStartTime(event.getTime());
                act.setType(event.getActType());
                // end the preceding trip
                Trip trip = chain.getLastTrip();
//                trip.setDest(act.getCoord());
                trip.setEndTime(event.getTime());
                trip.setToAct(act);
            }
        } catch (Exception e) {
            log.error("Exception while handling event " + event.toString(), e);
        }
    }

    @Override
    public void handleEvent(PersonArrivalEvent event) {
        try {
            if (isTransitDriver(event.getPersonId())) {
                return;
            }
            TravellerChain chain = chains.get(event.getPersonId());
            Trip trip = chain.getLastTrip();
            trip.setEndTime(event.getTime());
//            trip.setDest(network.getLinks().get(event.getLinkId()).getCoord());
            trip.setEndTime(event.getTime());
            TravelledLeg leg = trip.getLastLeg();
            leg.setEndTime(event.getTime());
            leg.setDest(network.getLinks().get(event.getLinkId()).getCoord());
        } catch (Exception e) {
            log.error("Exception while handling event " + event.toString(), e);
        }
    }

    @Override
    public void handleEvent(PersonDepartureEvent event) {
        try {
            if (isTransitDriver(event.getPersonId())) {
                return;
            }
            TravellerChain chain = chains.get(event.getPersonId());
            Trip trip;
            TravelledLeg leg;
            if (!chain.isInPT()) {
                trip = chain.addTrip();
//                trip.setOrig(network.getLinks().get(event.getLinkId()).getCoord());
                trip.setFromAct(chain.getLastActivity());
                trip.setStartTime(event.getTime());
                // trip.setMainmode(event.getLegMode());
            }
            trip = chain.getLastTrip();
            leg = trip.addLeg();
            leg.setOrig(network.getLinks().get(event.getLinkId()).getCoord());
            leg.setMode(event.getLegMode());
            leg.setStartTime(event.getTime());

        } catch (Exception e) {
            log.error("Exception while handling event " + event.toString(), e);
        }
    }

    @Override
    public void handleEvent(PersonStuckEvent event) {
        try {
            if (!isTransitDriver(event.getPersonId())) {
                TravellerChain chain = chains.get(event.getPersonId());
                setStuck(getStuck() + 1);
                chain.setStuck();
                if (chain.getTrips().size() > 0)
                    chain.removeLastTrip();
            }
        } catch (Exception e) {
            log.error("Exception while handling event " + event.toString(), e);
        }
    }

    @Override
    public void handleEvent(PersonEntersVehicleEvent event) {
        try {
            if (isTransitDriver(event.getPersonId()))
                return;
            PTVehicle vehicle = ptVehicles.get(event.getVehicleId());
            if (vehicle != null) {
                TravellerChain chain = chains.get(event.getPersonId());
                Trip trip = chain.getLastTrip();
                // first, handle the end of the wait
                // now, create a new leg
                vehicle.addPassenger(event.getPersonId());
                TravelledLeg leg = trip.getLastLeg();
                leg.setLine(vehicle.transitLineId);
                leg.setVehicleId(event.getVehicleId());
                leg.setMode(transitSchedule.getTransitLines()
                        .get(vehicle.transitLineId).getRoutes()
                        .get(vehicle.transitRouteId).getTransportMode());
                leg.setBoardingStop(vehicle.lastStop);
                // leg.setOrig(network.getLinks().get(event.getLinkId()).getCoord());
                // leg.setOrig(trip.getWaits().getLast().getCoord());
                leg.setRoute(vehicle.transitRouteId);
                leg.setStartTime(event.getTime());
                // check for the end of a transfer
            } else {
                // add the person to the map that keeps track of who drives what
                driverIdFromVehicleId.put(event.getVehicleId(), event.getPersonId());
            }
        } catch (Exception e) {
            log.error("Exception while handling event " + event.toString(), e);
        }
    }

    @Override
    public void handleEvent(PersonLeavesVehicleEvent event) {
        if (isTransitDriver(event.getPersonId()))
            return;
        try {
            PTVehicle vehicle = ptVehicles.get(event.getVehicleId());
            if (vehicle != null) {
                TravellerChain chain = chains.get(event.getPersonId());
                double stageDistance = vehicle.removePassenger(event.getPersonId());
                TravelledLeg leg = chain.getLastTrip().getLastLeg();
                leg.setDistance(stageDistance);
                leg.setAlightingStop(vehicle.lastStop);
            } else {
                driverIdFromVehicleId.remove(event.getVehicleId());
            }

        } catch (Exception e) {
            log.error("Exception while handling event " + event.toString(), e);
        }
    }

    @Override
    public void handleEvent(LinkEnterEvent event) {
        try {
            PTVehicle ptVehicle = ptVehicles.get(event.getVehicleId());
            if (ptVehicle != null) {
                ptVehicle.in = true;
//                ptVehicle.setLinkEnterTime(event.getTime());
/*            } else {
                chains.get(driverIdFromVehicleId.get(event.getVehicleId())).setLinkEnterTime(event.getTime());*/
            }
        } catch (Exception e) {
            log.error("Exception while handling event " + event.toString(), e);
        }

    }

    @Override
    public void handleEvent(LinkLeaveEvent event) {
        try {
            PTVehicle vehicle = ptVehicles.get(event.getVehicleId());
            if (vehicle != null) {
                if (vehicle.in)
                    vehicle.in = false;
                vehicle.incDistance(network.getLinks().get(event.getLinkId()).getLength());
            } else {
                TravellerChain chain = chains.get(driverIdFromVehicleId.get(event.getVehicleId()));
                TravelledLeg leg = chain.getLastTrip().getLastLeg();
                leg.incrementDistance(network.getLinks().get(event.getLinkId()).getLength());
            }
        } catch (Exception e) {
            log.error("Exception while handling event " + event.toString(), e);
        }
    }

    @Override
    public void handleEvent(TransitDriverStartsEvent event) {
        try {
            ptVehicles.put(
                    event.getVehicleId(),
                    new PTVehicle(event.getTransitLineId(), event.getTransitRouteId()));
            transitDriverIds.add(event.getDriverId());
        } catch (Exception e) {
            log.error("Exception while handling event " + event.toString(), e);
        }
    }

    @Override
    public void handleEvent(TeleportationArrivalEvent event) {
        try {
            if (isTransitDriver(event.getPersonId()))
                return;
            TravellerChain chain = chains.get(event.getPersonId());
            Trip trip = chain.getLastTrip();
            TravelledLeg leg = trip.getLastLeg();
            leg.setDistance((int) event.getDistance());
        } catch (Exception e) {
            log.error("Exception while handling event " + event.toString(), e);
        }
    }

    @Override
    public void handleEvent(VehicleDepartsAtFacilityEvent event) {
        try {
            PTVehicle pt_vehicle = ptVehicles.get(event.getVehicleId());
            for (Id passenger_id : pt_vehicle.getPassengersId()) {
                TravellerChain chain = chains.get(passenger_id);
                TravelledLeg leg = chain.getLastTrip().getLastLeg();
                leg.setPtDepartureTime(event.getTime());
                leg.setDepartureDelay(event.getDelay());
            }

        } catch (Exception e) {
            log.error("Exception while handling event " + event.toString(), e);
        }
    }

    @Override
    public void handleEvent(VehicleArrivesAtFacilityEvent event) {
        try {
            PTVehicle ptVehicle = ptVehicles.get(event.getVehicleId());
            ptVehicle.lastStop = event.getFacilityId();
        } catch (Exception e) {
            log.error("Exception while handling event " + event.toString(), e);
        }
    }

    // Methods
    @Override
    public void reset(int iteration) {
        chains = new HashMap<>();
        ptVehicles = new HashMap<>();
        transitDriverIds = new HashSet<>();
        driverIdFromVehicleId = new HashMap<>();
    }

    public void setMapActToZone(Zones zones, String attribute) {
        this.zones = new ZonesQueryCache(zones);
        this.zoneAttribute = attribute;
    }

    public void writeSimulationResultsToCsv(String appendage) throws IOException {
        String actTableName;
        String tripsTableName;
        String legsTableName;

        if (appendage.matches("[a-zA-Z0-9]*[_]*")) {
            actTableName = appendage + "matsim_activities.csv.gz";
            tripsTableName = appendage + "matsim_trips.csv.gz";
            legsTableName = appendage + "matsim_legs.csv.gz";
        } else {
            if (appendage.matches("[a-zA-Z0-9]*"))
                appendage = "_" + appendage;
            actTableName = "matsim_activities" + appendage + ".csv.gz";
            tripsTableName = "matsim_trips" + appendage + ".csv.gz";
            legsTableName = "matsim_legs" + appendage + ".csv.gz";
        }

        String[] actsData = new String[]{"activity_id", "person_id", "facility_id", "type", "start_time", "end_time", "x", "y", "sample_selector", "zone"};
        CSVWriter activityWriter = new CSVWriter(null, actsData, this.filename + actTableName);

        String[] tripsData = new String[]{"trip_id", "person_id", "start_time", "end_time", "distance", "main_mode", "main_mode_mikrozensus",
                "from_act", "to_act", "to_act_type", "in_vehicle_distance", "in_vehicle_time", "first_boarding_stop", "last_alighting_stop",
                "sample_selector", "got_stuck", "access_mode", "egress_mode", "access_dist", "egress_dist"};
        CSVWriter tripsWriter = new CSVWriter(null, tripsData, this.filename + tripsTableName);

        String[] legsData = new String[]{"leg_id", "trip_id", "start_time", "end_time", "distance", "mode", "line", "route",
                "boarding_stop", "alighting_stop", "departure_time", "departure_delay", "sample_selector", "from_x", "from_y",
                "to_x", "to_y", "previous_leg_id", "next_leg_id", "is_access", "is_egress"};
        CSVWriter legsWriter = new CSVWriter(null, legsData, this.filename + legsTableName);

        // read a static field that increments with every inheriting object constructed
        Counter counter = new Counter("Output lines written: ");
        for (Entry<Id<Person>, TravellerChain> entry : chains.entrySet()) {
            String pax_id = entry.getKey().toString();
            TravellerChain chain = entry.getValue();
            for (Activity act : chain.getActs()) {
                try {
                    Zone z = (this.zones == null) ? null : this.zones.findZone(act.getCoord().getX(), act.getCoord().getY());
                    Object attrVal = (z == null) ? null : z.getAttribute(this.zoneAttribute);
                    activityWriter.set("activity_id", Integer.toString(act.getElementId()));
                    activityWriter.set("person_id", pax_id);
                    activityWriter.set("facility_id", id2string(act.getFacility()));
                    activityWriter.set("type", act.getType());
                    activityWriter.set("start_time", Integer.toString((int) act.getStartTime()));
                    activityWriter.set("end_time", Integer.toString((int) act.getEndTime()));
                    activityWriter.set("x", Double.toString(act.getCoord().getX()));
                    activityWriter.set("y", Double.toString(act.getCoord().getY()));
                    activityWriter.set("sample_selector", Double.toString(MatsimRandom.getRandom().nextDouble()));
                    activityWriter.set("zone", (attrVal == null) ? "" : attrVal.toString());
                    activityWriter.writeRow();
                } catch (Exception e) {
                    log.error("Couldn't write activity chain!", e);
                }
            }

            ArrayList<TravelledLeg> accessLegs;
            ArrayList<TravelledLeg> egressLegs;
            String accessMode;
            String egressMode;
            boolean isRailJourney;

            for (Trip trip : chain.getTrips()) {
                try {
                    tripsWriter.set("trip_id", Integer.toString(trip.getElementId()));
                    tripsWriter.set("person_id", pax_id);
                    tripsWriter.set("start_time", Integer.toString((int) trip.getStartTime()));
                    tripsWriter.set("end_time", Integer.toString((int) trip.getEndTime()));
                    tripsWriter.set("distance", Double.toString(trip.getDistance()));
                    tripsWriter.set("main_mode", trip.getMainMode());
                    tripsWriter.set("main_mode_mikrozensus", trip.getMainModeMikroZensus());
                    tripsWriter.set("from_act", Integer.toString(trip.getFromAct().getElementId()));
                    tripsWriter.set("to_act", Integer.toString(trip.getToAct().getElementId()));
                    tripsWriter.set("to_act_type", (trip.getToActType() == null) ? "" : trip.getToActType());
                    tripsWriter.set("in_vehicle_distance", Double.toString(trip.getInVehDistance()));
                    tripsWriter.set("in_vehicle_time", Integer.toString((int) trip.getInVehTime()));
                    tripsWriter.set("first_boarding_stop", id2string(trip.getFirstBoardingStop()));
                    tripsWriter.set("last_alighting_stop", id2string(trip.getLastAlightingStop()));
                    tripsWriter.set("sample_selector", Double.toString(MatsimRandom.getRandom().nextDouble()));
                    tripsWriter.set("got_stuck", Boolean.toString(chain.isStuck()));
                    isRailJourney = trip.isRailJourney();

                    accessMode = "";
                    egressMode = "";

                    if (isRailJourney) {
                        accessLegs = trip.getAccessLegs();
                        egressLegs = trip.getEgressLegs();

                        accessMode = trip.getAccessMode(accessLegs);
                        egressMode = trip.getEgressMode(egressLegs);

                        for (TravelledLeg leg : accessLegs) {
                            leg.setIsAccess(accessMode);
                        }
                        for (TravelledLeg leg : egressLegs) {
                            leg.setIsEgress(egressMode);
                        }
                        if (accessMode.equals("access_walk") || accessMode.equals("transit_walk")) {
                            accessMode = "walk";
                        }

                        if (egressMode.equals("egress_walk") || egressMode.equals("transit_walk")) {
                            egressMode = "walk";
                        }
                    }
                    tripsWriter.set("access_mode", accessMode);
                    tripsWriter.set("egress_mode", egressMode);
                    tripsWriter.set("access_dist", String.valueOf(trip.getAccessDist(accessLegs)));
                    tripsWriter.set("egress_dist", String.valueOf(trip.getEgressDist(egressLegs)));

                    tripsWriter.writeRow();
                    counter.incCounter();

                    int ind = 0;
                    int size = trip.getLegs().size() - 1;
                    for (TravelledLeg leg : trip.getLegs()) {

                        String previous_leg_id = null;
                        String next_leg_id = null;
                        if (ind > 0)
                            previous_leg_id = Integer.toString(trip.getLegs().get(ind - 1).getElementId());
                        if (ind < size)
                            next_leg_id = Integer.toString(trip.getLegs().get(ind + 1).getElementId());
                        ind++;

                        legsWriter.set("leg_id", Integer.toString(leg.getElementId()));
                        legsWriter.set("trip_id", Integer.toString(trip.getElementId()));
                        legsWriter.set("start_time", Integer.toString((int) leg.getStartTime()));
                        legsWriter.set("end_time", Integer.toString((int) leg.getEndTime()));
                        legsWriter.set("distance", Double.toString(leg.getDistance()));
                        legsWriter.set("mode", leg.getMode());
                        legsWriter.set("line", id2string(leg.getLine()));
                        legsWriter.set("route", id2string(leg.getRoute()));
                        legsWriter.set("boarding_stop", id2string(leg.getBoardingStop()));
                        legsWriter.set("alighting_stop", id2string(leg.getAlightingStop()));
                        legsWriter.set("departure_time", Integer.toString((int) leg.getPtDepartureTime()));
                        legsWriter.set("departure_delay", Integer.toString((int) leg.getDepartureDelay()));
                        legsWriter.set("sample_selector", Double.toString(MatsimRandom.getRandom().nextDouble()));
                        legsWriter.set("from_x", Double.toString(leg.getOrig().getX()));
                        legsWriter.set("from_y", Double.toString(leg.getOrig().getY()));
                        legsWriter.set("to_x", Double.toString(leg.getDest().getX()));
                        legsWriter.set("to_y", Double.toString(leg.getDest().getY()));
                        legsWriter.set("previous_leg_id", (previous_leg_id == null) ? "" : previous_leg_id);
                        legsWriter.set("next_leg_id", (next_leg_id == null) ? "" : next_leg_id);
                        legsWriter.set("is_access", (leg.isAccessLeg()) ? "1" : "0");
                        legsWriter.set("is_egress", (leg.isEgressLeg()) ? "1" : "0");
                        legsWriter.writeRow();
                        counter.incCounter();
                    }
                } catch (NullPointerException e) {
                    setStuck(getStuck() + 1);
                }
            }

        }

        if (this.writeVisumPuTSurvey) {
            Double scaleFactor = 1.0 / this.config.qsim().getFlowCapFactor();
            VisumPuTSurvey visumPuTSurvey = new VisumPuTSurvey(this.getChains(), this.scenario, this.zones, scaleFactor);
            visumPuTSurvey.write(this.filename);
        }

        activityWriter.close();
        tripsWriter.close();
        legsWriter.close();
        counter.printCounter();
    }

    private static String id2string(Id<?> id) {
        if (id == null) {
            return "";
        }
        return id.toString();
    }

    public int getStuck() {
        return stuck;
    }

    public Map<Id<Person>, TravellerChain> getChains() {
        return chains;
    }

    void setStuck(int stuck) {
        this.stuck = stuck;
    }

    @Override
    public void writeResults() {
        try {
            this.writeSimulationResultsToCsv("");
        } catch (IOException e) {
            log.error("Could not write data.", e);
        }
    }

    // Private classes
    private class PTVehicle {

        // Attributes
        private final Id transitLineId;
        private final Id transitRouteId;
        private final Map<Id, Double> passengers = new HashMap<>();
        boolean in = false;
        Id lastStop;
        private double distance;

        // Constructors
        PTVehicle(Id transitLineId, Id transitRouteId) {
            this.transitLineId = transitLineId;
            this.transitRouteId = transitRouteId;
        }

        // Methods
        void incDistance(double linkDistance) {
            distance += linkDistance;
        }

        Set<Id> getPassengersId() {
            return passengers.keySet();
        }

        void addPassenger(Id passengerId) {
            passengers.put(passengerId, distance);
        }

        double removePassenger(Id passengerId) {
            return distance - passengers.remove(passengerId);
        }
    }

}
