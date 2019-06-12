/*
 * Copyright (C) Schweizerische Bundesbahnen SBB, 2017.
 */

package ch.sbb.matsim.analysis;

import ch.sbb.matsim.analysis.VisumPuTSurvey.VisumPuTSurvey;
import ch.sbb.matsim.analysis.travelcomponents.Activity;
import ch.sbb.matsim.analysis.travelcomponents.Journey;
import ch.sbb.matsim.analysis.travelcomponents.TravellerChain;
import ch.sbb.matsim.analysis.travelcomponents.TravelledLeg;
import ch.sbb.matsim.config.PostProcessingConfigGroup;
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
import org.matsim.core.events.algorithms.EventWriter;
import org.matsim.core.gbl.MatsimRandom;
import org.matsim.core.utils.io.IOUtils;
import org.matsim.core.utils.misc.Counter;
import org.matsim.pt.PtConstants;
import org.matsim.pt.transitSchedule.api.Departure;
import org.matsim.pt.transitSchedule.api.TransitLine;
import org.matsim.pt.transitSchedule.api.TransitRoute;
import org.matsim.pt.transitSchedule.api.TransitSchedule;
import org.matsim.vehicles.Vehicle;

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
 *         Converts events into journeys, legs/stages, transfers and activities
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

    private String filename;

    private Map<Id<Person>, TravellerChain> chains = new HashMap<>();
    private Map<Id<Vehicle>, PTVehicle> ptVehicles = new HashMap<>();
    private HashSet<Id<Person>> transitDriverIds = new HashSet<>();
    private HashMap<Id<Vehicle>, Id<Person>> driverIdFromVehicleId = new HashMap<>();
    private int stuck = 0;
    private TransitSchedule transitSchedule;
    private final boolean isTransitScenario;
    private boolean writeVisumPuTSurvey = false;
    private LocateAct locateAct = null;
    private Config config;
    private Scenario scenario;


    public EventsToTravelDiaries(Scenario scenario, String filename) {
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
            this.setMapActToZone(ppConfig.getShapeFile(), ppConfig.getZoneAttribute());
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
                // end the preceding journey
                Journey journey = chain.getLastJourney();
//                journey.setDest(act.getCoord());
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
            Journey journey = chain.getLastJourney();
            journey.setEndTime(event.getTime());
//            journey.setDest(network.getLinks().get(event.getLinkId()).getCoord());
            journey.setEndTime(event.getTime());
            TravelledLeg leg = journey.getLastLeg();
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
            Journey journey;
            TravelledLeg leg;
            if (!chain.isInPT()) {
                journey = chain.addJourney();
//                journey.setOrig(network.getLinks().get(event.getLinkId()).getCoord());
                journey.setFromAct(chain.getLastActivity());
                journey.setStartTime(event.getTime());
                // journey.setMainmode(event.getLegMode());
            }
            journey = chain.getLastJourney();
            leg = journey.addLeg();
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
                if (chain.getJourneys().size() > 0)
                    chain.removeLastJourney();
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
                Journey journey = chain.getLastJourney();
                // first, handle the end of the wait
                // now, create a new leg
                vehicle.addPassenger(event.getPersonId());
                TravelledLeg leg = journey.getLastLeg();
                leg.setLine(vehicle.transitLineId);
                leg.setVehicleId(event.getVehicleId());
                leg.setMode(transitSchedule.getTransitLines()
                        .get(vehicle.transitLineId).getRoutes()
                        .get(vehicle.transitRouteId).getTransportMode());
                leg.setBoardingStop(vehicle.lastStop);
                // leg.setOrig(network.getLinks().get(event.getLinkId()).getCoord());
                // leg.setOrig(journey.getWaits().getLast().getCoord());
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
                TravelledLeg leg = chain.getLastJourney().getLastLeg();
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
                TravelledLeg leg = chain.getLastJourney().getLastLeg();
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
            Journey journey = chain.getLastJourney();
            TravelledLeg leg = journey.getLastLeg();
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
                TravelledLeg leg = chain.getLastJourney().getLastLeg();
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

    public void setMapActToZone(String shapefile, String attribute) {
        this.locateAct = new LocateAct(shapefile, attribute);
    }

    public void writeSimulationResultsToTabSeparated(String appendage) throws IOException {
        String actTableName;
        String journeyTableName;
        String legsTableName;

        if (appendage.matches("[a-zA-Z0-9]*[_]*")) {
            actTableName = appendage + "matsim_activities.txt";
            journeyTableName = appendage + "matsim_journeys.txt";
            legsTableName = appendage + "matsim_legs.txt";
        } else {
            if (appendage.matches("[a-zA-Z0-9]*"))
                appendage = "_" + appendage;
            actTableName = "matsim_activities" + appendage + ".txt";
            journeyTableName = "matsim_journeys" + appendage + ".txt";
            legsTableName = "matsim_legs" + appendage + ".txt";
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

        BufferedWriter legsWriter = IOUtils.getBufferedWriter(this.filename + legsTableName);
        legsWriter.write("leg_id\tjourney_id\tstart_time\tend_time\t" +
                "distance\tmode\tline\troute\tboarding_stop\t" +
                "alighting_stop\tdeparture_time\tdeparture_delay\tsample_selector\t" +
                 "from_x\tfrom_y\tto_x\tto_y\tprevious_leg_id\tnext_leg_id\n");

        // read a static field that increments with every inheriting object constructed
        Counter counter = new Counter("Output lines written: ");
        for (Entry<Id<Person>, TravellerChain> entry : chains.entrySet()) {
            String pax_id = entry.getKey().toString();
            TravellerChain chain = entry.getValue();
            for (Activity act : chain.getActs()) {
                try {
                    activityWriter.write(String.format(
                            "%d\t%s\t%s\t%s\t%d\t%d\t%f\t%f\t%f\t%s\n",
                            act.getElementId(), pax_id,
                            act.getFacility(), act.getType(),
                            (int) act.getStartTime(),
                            (int) act.getEndTime(),
                            act.getCoord().getX(),
                            act.getCoord().getY(),
                            MatsimRandom.getRandom().nextDouble(),
                            (this.locateAct != null) ? this.locateAct.getZoneAttribute(act.getCoord()) : ""));
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
                            0 /*journey.getAccessWalkDistance()*/,
                            0 /*(int) journey.getAccessWalkTime()*/,
                            0 /*(int) journey.getAccessWaitTime()*/,
                            journey.getFirstBoardingStop(),
                            0 /*journey.getEgressWalkDistance()*/,
                            0 /*(int) journey.getEgressWalkTime()*/,
                            journey.getLastAlightingStop(),
                            0 /*journey.getTransferWalkDistance()*/,
                            0 /*(int) journey.getTransferWalkTime()*/,
                            0 /*(int) journey.getTransferWaitTime()*/,
                            MatsimRandom.getRandom().nextDouble(),
                            chain.isStuck())
                    );
                    counter.incCounter();

                    int ind = 0;
                    for (TravelledLeg leg : journey.getLegs()) {

                        String previous_leg_id = null;
                        String next_leg_id = null;
                        if(ind > 0)
                            previous_leg_id = Integer.toString(journey.getLegs().get(ind - 1).getElementId());
                        if(ind < journey.getLegs().size() - 1)
                            next_leg_id = Integer.toString(journey.getLegs().get(ind + 1).getElementId());
                        ind++;

                        legsWriter.write(String.format(
                                "%d\t%d\t%d\t%d\t%.3f\t%s\t%s\t%s\t%s\t%s\t%d\t%d\t%f\t%f\t%f\t%f\t%f\t%s\t%s\n",
                                leg.getElementId(),
                                journey.getElementId(),
                                (int) leg.getStartTime(),
                                (int) leg.getEndTime(),
                                leg.getDistance(),
                                leg.getMode(), leg.getLine(),
                                leg.getRoute(), leg.getBoardingStop(),
                                leg.getAlightingStop(), (int) leg.getPtDepartureTime(), (int) leg.getDepartureDelay(),
                                MatsimRandom.getRandom().nextDouble(),
                                leg.getOrig().getX(),
                                leg.getOrig().getY(),
                                leg.getDest().getX(),
                                leg.getDest().getY(),
                                previous_leg_id,
                                next_leg_id));
                        counter.incCounter();
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
        legsWriter.close();
        counter.printCounter();
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
