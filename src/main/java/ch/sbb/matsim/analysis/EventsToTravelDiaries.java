/*
 * Copyright (C) Schweizerische Bundesbahnen SBB, 2017.
 */

package ch.sbb.matsim.analysis;

import ch.sbb.matsim.analysis.VisumPuTSurvey.VisumPuTSurvey;
import ch.sbb.matsim.analysis.travelcomponents.Activity;
import ch.sbb.matsim.analysis.travelcomponents.Journey;
import ch.sbb.matsim.analysis.travelcomponents.Transfer;
import ch.sbb.matsim.analysis.travelcomponents.TravellerChain;
import ch.sbb.matsim.analysis.travelcomponents.Trip;
import ch.sbb.matsim.config.PostProcessingConfigGroup;
import ch.sbb.matsim.zones.Zone;
import ch.sbb.matsim.zones.Zones;
import ch.sbb.matsim.zones.ZonesCollection;
import ch.sbb.matsim.zones.ZonesQueryCache;
import org.apache.log4j.Logger;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.events.ActivityEndEvent;
import org.matsim.api.core.v01.events.ActivityStartEvent;
import org.matsim.api.core.v01.events.LinkEnterEvent;
import org.matsim.api.core.v01.events.LinkLeaveEvent;
import org.matsim.api.core.v01.events.PersonArrivalEvent;
import org.matsim.api.core.v01.events.PersonDepartureEvent;
import org.matsim.api.core.v01.events.PersonEntersVehicleEvent;
import org.matsim.api.core.v01.events.PersonLeavesVehicleEvent;
import org.matsim.api.core.v01.events.PersonStuckEvent;
import org.matsim.api.core.v01.events.TransitDriverStartsEvent;
import org.matsim.api.core.v01.events.handler.ActivityEndEventHandler;
import org.matsim.api.core.v01.events.handler.ActivityStartEventHandler;
import org.matsim.api.core.v01.events.handler.LinkEnterEventHandler;
import org.matsim.api.core.v01.events.handler.LinkLeaveEventHandler;
import org.matsim.api.core.v01.events.handler.PersonArrivalEventHandler;
import org.matsim.api.core.v01.events.handler.PersonDepartureEventHandler;
import org.matsim.api.core.v01.events.handler.PersonEntersVehicleEventHandler;
import org.matsim.api.core.v01.events.handler.PersonLeavesVehicleEventHandler;
import org.matsim.api.core.v01.events.handler.PersonStuckEventHandler;
import org.matsim.api.core.v01.events.handler.TransitDriverStartsEventHandler;
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
import org.matsim.core.events.algorithms.EventWriter;
import org.matsim.core.gbl.MatsimRandom;
import org.matsim.core.utils.io.IOUtils;
import org.matsim.core.utils.misc.Counter;
import org.matsim.pt.PtConstants;
import org.matsim.pt.transitSchedule.api.Departure;
import org.matsim.pt.transitSchedule.api.TransitLine;
import org.matsim.pt.transitSchedule.api.TransitRoute;
import org.matsim.pt.transitSchedule.api.TransitSchedule;

import java.io.BufferedWriter;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

/**
 * @author pieterfourie, sergioo
 *         <p>
 *         Converts events into journeys, trips/stages, transfers and activities
 *         tables. Originally designed for transit scenarios with full transit
 *         simulation, but should work with most teleported modes
 *         </p>
 */

public class EventsToTravelDiaries implements
        TransitDriverStartsEventHandler, PersonEntersVehicleEventHandler,
        PersonLeavesVehicleEventHandler, PersonDepartureEventHandler,
        PersonArrivalEventHandler, ActivityStartEventHandler,
        ActivityEndEventHandler, PersonStuckEventHandler,
        LinkEnterEventHandler, LinkLeaveEventHandler,
        TeleportationArrivalEventHandler, VehicleArrivesAtFacilityEventHandler,
        VehicleDepartsAtFacilityEventHandler,
        EventWriter {

    private static final Logger log = Logger.getLogger(EventsToTravelDiaries.class);

    private final Network network;
    // Attributes

    private String filename;

    private Map<Id, TravellerChain> chains = new HashMap<>();
    private Map<Id, PTVehicle> ptVehicles = new HashMap<>();
    private HashSet<Id> transitDriverIds = new HashSet<>();
    private HashMap<Id, Id> driverIdFromVehicleId = new HashMap<>();
    private int stuck = 0;
    private TransitSchedule transitSchedule;
    private boolean isTransitScenario = false;
    private boolean writeVisumPuTSurvey = false;
    private Zones zones = null;
    private String zoneAttribute = null;
    private Config config;
    private Scenario scenario;


    public EventsToTravelDiaries(Scenario scenario, String filename, ZonesCollection allZones) {
        this.filename = filename;
        this.scenario = scenario;

        this.network = scenario.getNetwork();
        isTransitScenario = scenario.getConfig().transit().isUseTransit();

        if (isTransitScenario) {
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
                        this.ptVehicles.put(vehicleId, new PTVehicle(tL.getId(), tR.getId(), vehicleId));
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
                Activity act = chain.getActs().getLast();
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
                chain.traveling = false;
                Activity act = chain.addActivity();
                act.setCoord(network.getLinks().get(event.getLinkId()).getCoord());
                act.setFacility(event.getFacilityId());
                act.setStartTime(event.getTime());
                act.setType(event.getActType());
                // end the preceding journey
                Journey journey = chain.getJourneys().getLast();
                journey.setDest(act.getCoord());
                journey.setEndTime(event.getTime());
                journey.setToAct(act);
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
            Journey journey = chain.getJourneys().getLast();
            journey.setEndTime(event.getTime());
            journey.setDest(network.getLinks().get(event.getLinkId()).getCoord());
            journey.setEndTime(event.getTime());
            Trip trip = journey.getTrips().getLast();
            trip.setEndTime(event.getTime());
            trip.setDest(network.getLinks().get(event.getLinkId()).getCoord());
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
            Journey journey;
            Trip trip;
            if (!chain.isInPT()) {
                journey = chain.addJourney();
                journey.setOrig(network.getLinks().get(event.getLinkId()).getCoord());
                journey.setFromAct(chain.getActs().getLast());
                journey.setStartTime(event.getTime());
                // journey.setMainmode(event.getLegMode());
            }
            journey = chain.getJourneys().getLast();
            trip = journey.addTrip();
            trip.setOrig(network.getLinks().get(event.getLinkId()).getCoord());
            trip.setMode(event.getLegMode());
            trip.setStartTime(event.getTime());

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
                chain.setStucked();
                if (chain.getJourneys().size() > 0)
                    chain.getJourneys().removeLast();
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
            if (ptVehicles.containsKey(event.getVehicleId())) {
                TravellerChain chain = chains.get(event.getPersonId());
                Journey journey = chain.getJourneys().getLast();
                // first, handle the end of the wait
                // now, create a new trip
                PTVehicle vehicle = ptVehicles.get(event.getVehicleId());
                vehicle.addPassenger(event.getPersonId());
                Trip trip = journey.getTrips().getLast();
                trip.setLine(vehicle.transitLineId);
                trip.setVehicleId(event.getVehicleId());
                trip.setMode(transitSchedule.getTransitLines()
                        .get(vehicle.transitLineId).getRoutes()
                        .get(vehicle.transitRouteId).getTransportMode());
                trip.setBoardingStop(vehicle.lastStop);
                // trip.setOrig(network.getLinks().get(event.getLinkId()).getCoord());
                // trip.setOrig(journey.getWaits().getLast().getCoord());
                trip.setRoute(ptVehicles.get(event.getVehicleId()).transitRouteId);
                trip.setStartTime(event.getTime());
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
            if (ptVehicles.containsKey(event.getVehicleId())) {
                TravellerChain chain = chains.get(event.getPersonId());
                chain.traveledVehicle = true;
                PTVehicle vehicle = ptVehicles.get(event.getVehicleId());
                double stageDistance = vehicle.removePassenger(event.getPersonId());
                Trip trip = chain.getJourneys().getLast().getTrips().getLast();
                trip.setDistance(stageDistance);
                trip.setAlightingStop(vehicle.lastStop);
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
            if (ptVehicles.containsKey(event.getVehicleId())) {
                PTVehicle ptVehicle = ptVehicles.get(event.getVehicleId());
                ptVehicle.in = true;
                ptVehicle.setLinkEnterTime(event.getTime());
            } else {
                chains.get(driverIdFromVehicleId.get(event.getVehicleId())).setLinkEnterTime(event.getTime());
            }

        } catch (Exception e) {
            log.error("Exception while handling event " + event.toString(), e);
        }

    }

    @Override
    public void handleEvent(LinkLeaveEvent event) {
        try {
            if (ptVehicles.containsKey(event.getVehicleId())) {
                PTVehicle vehicle = ptVehicles.get(event.getVehicleId());
                if (vehicle.in)
                    vehicle.in = false;
                vehicle.incDistance(network.getLinks().get(event.getLinkId()).getLength());

            } else {
                TravellerChain chain = chains.get(driverIdFromVehicleId.get(event.getVehicleId()));
                Trip trip = chain.getJourneys().getLast().getTrips().getLast();
                trip.incrementDistance(network.getLinks().get(event.getLinkId()).getLength());
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
                    new PTVehicle(event.getTransitLineId(), event.getTransitRouteId(),
                            event.getVehicleId()));
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
            Journey journey = chain.getJourneys().getLast();
            Trip trip = journey.getTrips().getLast();
            trip.setDistance((int) event.getDistance());
            if (chain.traveledVehicle)
                chain.traveledVehicle = false;
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
                Trip trip = chain.getJourneys().getLast().getTrips().getLast();
                trip.setPtDepartureTime(event.getTime());
                trip.setDepartureDelay(event.getDelay());
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

    public void writeSimulationResultsToTabSeparated(String appendage) throws IOException {
        String actTableName;
        String journeyTableName;
        String transferTableName;
        String tripTableName;

        if (appendage.matches("[a-zA-Z0-9]*[_]*")) {
            actTableName = appendage + "matsim_activities.txt";
            journeyTableName = appendage + "matsim_journeys.txt";
            transferTableName = appendage + "matsim_transfers.txt";
            tripTableName = appendage + "matsim_trips.txt";
        } else {
            if (appendage.matches("[a-zA-Z0-9]*"))
                appendage = "_" + appendage;
            actTableName = "matsim_activities" + appendage + ".txt";
            journeyTableName = "matsim_journeys" + appendage + ".txt";
            transferTableName = "matsim_transfers" + appendage + ".txt";
            tripTableName = "matsim_trips" + appendage + ".txt";
        }
        BufferedWriter activityWriter = IOUtils.getBufferedWriter(this.filename + actTableName);

        activityWriter.write("activity_id\tperson_id\tfacility_id\ttype\t" +
                "start_time\tend_time\tx\ty\tsample_selector\tzone\n");

        BufferedWriter journeyWriter = IOUtils.getBufferedWriter(this.filename + journeyTableName);
        journeyWriter.write("journey_id\tperson_id\tstart_time\t" +
                "end_time\tdistance\tmain_mode\tmain_mode_mikrozensus\tfrom_act\tto_act\tto_act_type\t" +
                "in_vehicle_distance\tin_vehicle_time\t" +
                "access_walk_distance\taccess_walk_time\taccess_wait_time\t" +
                "first_boarding_stop\tegress_walk_distance\t" +
                "egress_walk_time\tlast_alighting_stop\t" +
                "transfer_walk_distance\ttransfer_walk_time\t" +
                "transfer_wait_time\tsample_selector\tstucked\n");

        BufferedWriter tripWriter = IOUtils.getBufferedWriter(this.filename + tripTableName);
        tripWriter.write("trip_id\tjourney_id\tstart_time\tend_time\t" +
                "distance\tmode\tline\troute\tboarding_stop\t" +
                "alighting_stop\tdeparture_time\tdeparture_delay\tsample_selector\t" +
                 "from_x\tfrom_y\tto_x\tto_y\tprevious_trip_id\tnext_trip_id\n");

        BufferedWriter transferWriter = IOUtils.getBufferedWriter(this.filename + transferTableName);
        transferWriter.write("transfer_id\tjourney_id\tstart_time\t" +
                "end_time\tfrom_trip\tto_trip\twalk_distance\t" +
                "walk_time\twait_time\tsample_selector\n");

        // read a static field that increments with every inheriting object constructed
        Counter counter = new Counter("Output lines written: ");
        for (Entry<Id, TravellerChain> entry : chains.entrySet()) {
            String pax_id = entry.getKey().toString();
            TravellerChain chain = entry.getValue();
            for (Activity act : chain.getActs()) {
                try {
                    Zone z = (this.zones == null) ? null : this.zones.findZone(act.getCoord().getX(), act.getCoord().getY());
                    Object attrVal = (z == null) ? null : z.getAttribute(this.zoneAttribute);
                    activityWriter.write(String.format(
                            "%d\t%s\t%s\t%s\t%d\t%d\t%f\t%f\t%f\t%s\n",
                            act.getElementId(), pax_id,
                            act.getFacility(), act.getType(),
                            (int) act.getStartTime(),
                            (int) act.getEndTime(),
                            act.getCoord().getX(),
                            act.getCoord().getY(),
                            MatsimRandom.getRandom().nextDouble(),
                            (attrVal == null) ? "" : attrVal.toString()));
                } catch (Exception e) {
                    log.error("Couldn't print activity chain!", e);
                }
            }
            for (Journey journey : chain.getJourneys()) {
                try {
                    journeyWriter.write(String.format(
                            "%d\t%s\t%d\t%d\t%.3f\t%s\t%s\t%d\t%d\t%s\t%.3f\t%d\t%.3f\t%d\t%d\t%s\t%.3f\t%d\t%s\t%.3f\t%d\t%d\t%f\t%b\n",
                            journey.getElementId(),
                            pax_id,
                            (int) journey.getStartTime(),
                            (int) journey.getEndTime(),
                            journey.getDistance(),
                            journey.getMainMode(),
                            journey.getMainModeMikroZensus(),
                            journey.getFromAct().getElementId(),
                            journey.getToAct().getElementId(),
                            journey.getToActType(),
                            journey.getInVehDistance(),
                            (int) journey.getInVehTime(),
                            journey.getAccessWalkDistance(),
                            (int) journey.getAccessWalkTime(),
                            (int) journey.getAccessWaitTime(),
                            journey.getFirstBoardingStop(),
                            journey.getEgressWalkDistance(),
                            (int) journey.getEgressWalkTime(),
                            journey.getLastAlightingStop(),
                            journey.getTransferWalkDistance(),
                            (int) journey.getTransferWalkTime(),
                            (int) journey.getTransferWaitTime(),
                            MatsimRandom.getRandom().nextDouble(),
                            chain.getStucked())
                    );
                    counter.incCounter();

                    // comment (PManser): in my opinion, isCarJourney() does not mean anything
                    if (!(journey.isCarJourney() || journey.isTeleportJourney())) {
                        int ind = 0;
                        for (Trip trip : journey.getTrips()) {

                            String previous_trip_id = null;
                            String next_trip_id = null;
                            if(ind > 0)
                                previous_trip_id = Integer.toString(journey.getTrips().get(ind - 1).getElementId());
                            if(ind < journey.getTrips().size() - 1)
                                next_trip_id = Integer.toString(journey.getTrips().get(ind + 1).getElementId());
                            ind++;

                            tripWriter.write(String.format(
                                    "%d\t%d\t%d\t%d\t%.3f\t%s\t%s\t%s\t%s\t%s\t%d\t%d\t%f\t%f\t%f\t%f\t%f\t%s\t%s\n",
                                    trip.getElementId(),
                                    journey.getElementId(),
                                    (int) trip.getStartTime(),
                                    (int) trip.getEndTime(),
                                    trip.getDistance(),
                                    trip.getMode(), trip.getLine(),
                                    trip.getRoute(), trip.getBoardingStop(),
                                    trip.getAlightingStop(), (int) trip.getPtDepartureTime(), (int) trip.getDepartureDelay(),
                                    MatsimRandom.getRandom().nextDouble(),
                                    trip.getOrig().getX(),
                                    trip.getOrig().getY(),
                                    trip.getDest().getX(),
                                    trip.getDest().getY(),
                                    previous_trip_id,
                                    next_trip_id));
                            counter.incCounter();
                        }
                        for (Transfer transfer : journey.getTransfers()) {
                            transferWriter.write(String.format(
                                    "%d\t%d\t%d\t%d\t%d\t%d\t%.3f\t%d\t%d\t%f\n",
                                    transfer.getElementId(),
                                    journey.getElementId(),
                                    (int) transfer.getStartTime(),
                                    (int) transfer.getEndTime(),
                                    transfer.getFromTrip()
                                            .getElementId(),
                                    transfer.getToTrip()
                                            .getElementId(),

                                    transfer.getWalkDistance(),
                                    (int) transfer.getWalkTime(),
                                    (int) transfer.getWaitTime(),
                                    MatsimRandom.getRandom().nextDouble()

                            ));
                            counter.incCounter();
                        }
                    } else {
                        for (Trip trip : journey.getTrips()) {

                            tripWriter.write(String.format(
                                    "%d\t%d\t%d\t%d\t%.3f\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%f\n",
                                    trip.getElementId(),
                                    journey.getElementId(),
                                    (int) trip.getStartTime(),
                                    (int) trip.getEndTime(),
                                    trip.getDistance(),
                                    trip.getMode(), "", "", "", "", "", "",
                                    MatsimRandom.getRandom().nextDouble()

                            ));
                            counter.incCounter();
                        }
                    }
                } catch (NullPointerException e) {
                    setStuck(getStuck() + 1);
                }
            }

        }

        if (this.writeVisumPuTSurvey) {
            Double scaleFactor = 1.0 / this.config.qsim().getFlowCapFactor();
            VisumPuTSurvey visumPuTSurvey = new VisumPuTSurvey(this.getChains(), this.scenario, scaleFactor);
            visumPuTSurvey.write(this.filename);
        }

        activityWriter.close();
        journeyWriter.close();
        tripWriter.close();
        transferWriter.close();
        counter.printCounter();
    }

    public int getStuck() {
        return stuck;
    }

    public Map<Id, TravellerChain> getChains() {
        return chains;
    }

    void setStuck(int stuck) {
        this.stuck = stuck;
    }

    @Override
    public void closeFile() {
        try {
            this.writeSimulationResultsToTabSeparated("");
        } catch (IOException e) {
            log.error("Could not write data.", e);
        }
    }

    // Private classes
    private class PTVehicle {

        // Attributes
        private final Id transitLineId;
        private final Id transitRouteId;
        private final Id vehicleId;
        private final Map<Id, Double> passengers = new HashMap<>();
        boolean in = false;
        Id lastStop;
        private double distance;
        private double linkEnterTime = 0.0;

        // Constructors
        public PTVehicle(Id transitLineId, Id transitRouteId, Id vehicleId) {
            this.transitLineId = transitLineId;
            this.transitRouteId = transitRouteId;
            this.vehicleId = vehicleId;
        }

        // Methods
        public void incDistance(double linkDistance) {
            distance += linkDistance;
        }

        public Set<Id> getPassengersId() {
            return passengers.keySet();
        }

        public void addPassenger(Id passengerId) {
            passengers.put(passengerId, distance);
        }

        public double removePassenger(Id passengerId) {
            return distance - passengers.remove(passengerId);
        }

        public double getLinkEnterTime() {
            return linkEnterTime;
        }

        public void setLinkEnterTime(double linkEnterTime) {
            this.linkEnterTime = linkEnterTime;
        }

    }

}
