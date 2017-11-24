/*
 * Copyright (C) Schweizerische Bundesbahnen SBB, 2017.
 */

package ch.sbb.matsim.mobsim.qsim.pt;

import ch.sbb.matsim.config.SBBTransitConfigGroup;
import org.apache.log4j.Logger;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.events.PersonEntersVehicleEvent;
import org.matsim.api.core.v01.events.PersonLeavesVehicleEvent;
import org.matsim.api.core.v01.events.PersonStuckEvent;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.population.Leg;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.mobsim.framework.MobsimAgent;
import org.matsim.core.mobsim.framework.PassengerAgent;
import org.matsim.core.mobsim.qsim.InternalInterface;
import org.matsim.core.mobsim.qsim.QSim;
import org.matsim.core.mobsim.qsim.pt.AbstractTransitDriverAgent;
import org.matsim.core.mobsim.qsim.pt.PTPassengerAgent;
import org.matsim.core.mobsim.qsim.pt.SimpleTransitStopHandlerFactory;
import org.matsim.core.mobsim.qsim.pt.TransitDriverAgentFactory;
import org.matsim.core.mobsim.qsim.pt.TransitDriverAgentImpl;
import org.matsim.core.mobsim.qsim.pt.TransitQSimEngine;
import org.matsim.core.mobsim.qsim.pt.TransitQVehicle;
import org.matsim.core.mobsim.qsim.pt.TransitStopAgentTracker;
import org.matsim.core.mobsim.qsim.pt.TransitStopHandlerFactory;
import org.matsim.core.utils.misc.Time;
import org.matsim.pt.Umlauf;
import org.matsim.pt.UmlaufImpl;
import org.matsim.pt.UmlaufStueck;
import org.matsim.pt.transitSchedule.api.Departure;
import org.matsim.pt.transitSchedule.api.TransitLine;
import org.matsim.pt.transitSchedule.api.TransitRoute;
import org.matsim.pt.transitSchedule.api.TransitRouteStop;
import org.matsim.pt.transitSchedule.api.TransitSchedule;
import org.matsim.pt.transitSchedule.api.TransitStopFacility;
import org.matsim.vehicles.Vehicle;
import org.matsim.vehicles.Vehicles;

import javax.inject.Inject;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;

/**
 * @author mrieser / SBB
 */
public class SBBTransitQSimEngine extends TransitQSimEngine /*implements DepartureHandler, MobsimEngine, AgentSource*/ {

    private static final Logger log = Logger.getLogger(SBBTransitQSimEngine.class);

    private final SBBTransitConfigGroup config;
    private final QSim qSim;
    private final TransitStopAgentTracker agentTracker;
    private final TransitSchedule schedule;
    private InternalInterface internalInterface;
    private TransitDriverAgentFactory transitDriverFactory;
    private TransitStopHandlerFactory stopHandlerFactory = new SimpleTransitStopHandlerFactory();
    private PriorityQueue<TransitEvent> eventQueue = new PriorityQueue<>();

    @Inject
    public SBBTransitQSimEngine(QSim qSim) {
        super(qSim);
        this.qSim = qSim;
        this.config = ConfigUtils.addOrGetModule(qSim.getScenario().getConfig(), SBBTransitConfigGroup.GROUP_NAME, SBBTransitConfigGroup.class);
        this.schedule = qSim.getScenario().getTransitSchedule();
        this.agentTracker = new TransitStopAgentTracker(qSim.getEventsManager());
    }

    @Inject
    public void setTransitStopHandlerFactory(final TransitStopHandlerFactory stopHandlerFactory) {
        this.stopHandlerFactory = stopHandlerFactory;
    }

    @Override
    public void setInternalInterface(InternalInterface internalInterface) {
        this.internalInterface = internalInterface;
        this.transitDriverFactory = new SBBTransitDriverAgentFactory(internalInterface, this.agentTracker, this.config.getDeterministicServiceModes());
    }

    @Override
    public void insertAgentsIntoMobsim() {
        createVehiclesAndDrivers();
    }

    @Override
    public boolean handleDeparture(double time, MobsimAgent agent, Id<Link> linkId) {
        String mode = agent.getMode();
        if (this.config.getPassengerModes().contains(mode)) {
            handlePassengerDeparture(agent, linkId);
            return true;
        } else if (this.config.getDeterministicServiceModes().contains(mode)) {
            handleDeterministicDriverDeparture(agent);
            return true;
        } else if (this.config.getNetworkServiceModes().contains(mode)) {
            // this should actually be handled as a qsim mainMode, not sure how to
            // simplify the configuration for that and if networkServiceModes are
            // actually needed at all.
            // TODO
        }
        return false;
    }

    @Override
    public void onPrepareSim() {
        // nothing to do, all pre-processing is done in insertAgentsIntoMobsim
    }

    @Override
    public void doSimStep(double time) {
        TransitEvent event = this.eventQueue.peek();
        while (event != null && event.time <= time) {
            handleTransitEvent(this.eventQueue.poll());
            event = this.eventQueue.peek();
        }
    }

    @Override
    public void afterSim() {
        // check that all agents have arrived, generate stuck events otherwise
        double now = this.qSim.getSimTimer().getTimeOfDay();
        for (Map.Entry<Id<TransitStopFacility>, List<PTPassengerAgent>> agentsAtStop : this.agentTracker.getAgentsAtStop().entrySet()) {
            TransitStopFacility stop = this.schedule.getFacilities().get(agentsAtStop.getKey());
            for (PTPassengerAgent agent : agentsAtStop.getValue()) {
                this.qSim.getEventsManager().processEvent(new PersonStuckEvent(now, agent.getId(), stop.getLinkId(), agent.getMode()));
                this.qSim.getAgentCounter().decLiving();
                this.qSim.getAgentCounter().incLost();
            }
        }

        // check for agents still in a vehicle
        TransitEvent event;
        while ((event = this.eventQueue.poll()) != null) {
            Id<Link> nextStopLinkId = event.stop.getStopFacility().getLinkId();
            for (PassengerAgent agent : event.driver.getVehicle().getPassengers()) {
                this.qSim.getEventsManager().processEvent(new PersonStuckEvent(now, agent.getId(), nextStopLinkId, agent.getMode()));
                this.qSim.getAgentCounter().decLiving();
                this.qSim.getAgentCounter().incLost();
            }
        }
    }

    private void createVehiclesAndDrivers() {
        Scenario scenario = this.qSim.getScenario();
        TransitSchedule schedule = scenario.getTransitSchedule();
        Vehicles vehicles = scenario.getTransitVehicles();

        for (TransitLine line : schedule.getTransitLines().values()) {
            for (TransitRoute route : line.getRoutes().values()) {
                String mode = route.getTransportMode();
                boolean isDeterministic = this.config.getDeterministicServiceModes().contains(mode);
                for (Departure dep : route.getDepartures().values()) {
                    Vehicle veh = vehicles.getVehicles().get(dep.getVehicleId());
//                    if (isDeterministic) {
//                        createAndScheduleDeterministicDriver(veh, line, route, dep);
//                    } else {
                        Umlauf umlauf = createUmlauf(line, route, dep);
                        createAndScheduleNetworkDriver(veh, umlauf);
//                    }
                }
            }
        }
    }

//    private void createAndScheduleDeterministicDriver(Vehicle veh, TransitLine line, TransitRoute route, Departure dep) {
//        TransitQVehicle qVeh = new TransitQVehicle(veh);
//
//    }

    private void createAndScheduleNetworkDriver(Vehicle veh, Umlauf umlauf) {
        TransitQVehicle qVeh = new TransitQVehicle(veh);
        AbstractTransitDriverAgent driver = this.transitDriverFactory.createTransitDriver(umlauf);
        qVeh.setDriver(driver);
        qVeh.setStopHandler(this.stopHandlerFactory.createTransitStopHandler(veh));
        driver.setVehicle(qVeh);

        Leg firstLeg = (Leg) driver.getNextPlanElement();
        String firstMode = firstLeg.getMode(); // we assume that all legs should have the same mode for a vehicle
        boolean isDeterministic = this.config.getDeterministicServiceModes().contains(firstMode);
        if (!isDeterministic) {
            // insert vehicle into qSim
            Id<Link> startLinkId = firstLeg.getRoute().getStartLinkId();
            this.qSim.addParkedVehicle(qVeh, startLinkId);
        }
        this.qSim.insertAgentIntoMobsim(driver);
    }

    private Umlauf createUmlauf(TransitLine line, TransitRoute route, Departure departure) {
        Id<Umlauf> id = Id.create(line.getId().toString() + "_" + route.getId().toString() + "_" + departure.getId().toString(), Umlauf.class);
        UmlaufImpl umlauf = new UmlaufImpl(id);
        UmlaufStueck part = new UmlaufStueck(line, route, departure);
        umlauf.getUmlaufStuecke().add(part);
        return umlauf;
    }


    private void handlePassengerDeparture(MobsimAgent agent, Id<Link> linkId) {
        PTPassengerAgent passenger = (PTPassengerAgent) agent;
        // this puts the agent into the transit stop.
        Id<TransitStopFacility> accessStopId = passenger.getDesiredAccessStopId();
        if (accessStopId == null) {
            // looks like this agent has a bad transit route, likely no
            // route could be calculated for it
            log.error("pt-agent doesn't know to what transit stop to go to. Removing agent from simulation. Agent " + passenger.getId().toString());
            this.qSim.getAgentCounter().decLiving();
            this.qSim.getAgentCounter().incLost();
            return;
        }
        TransitStopFacility stop = this.schedule.getFacilities().get(accessStopId);
        if (stop.getLinkId() == null || stop.getLinkId().equals(linkId)) {
            double now = this.qSim.getSimTimer().getTimeOfDay();
            this.agentTracker.addAgentToStop(now, passenger, stop.getId());
            this.internalInterface.registerAdditionalAgentOnLink(agent);
        } else {
            throw new TransitQSimEngine.TransitAgentTriesToTeleportException("Agent " + passenger.getId() + " tries to enter a transit stop at link "+stop.getLinkId()+" but really is at "+linkId+"!");
        }

    }

    private void handleDeterministicDriverDeparture(MobsimAgent agent) {
        TransitDriverAgentImpl driver = (TransitDriverAgentImpl) agent;
        Iterator<TransitRouteStop> stopIter = driver.getTransitRoute().getStops().iterator();
        TransitRouteStop firstStop = stopIter.next();
        double firstDepartureTime = driver.getDeparture().getDepartureTime() + firstStop.getDepartureOffset();
        this.qSim.getEventsManager().processEvent(new PersonEntersVehicleEvent(firstDepartureTime, driver.getId(), driver.getVehicle().getId()));
        TransitEvent event = new TransitEvent(firstDepartureTime, TransitEventType.ArrivalAtStop, driver, stopIter, firstStop);
        this.eventQueue.add(event);
    }

    private void handleTransitEvent(TransitEvent event) {
        switch (event.type) {
            case ArrivalAtStop: handleArrivalAtStop(event); break;
            case DepartureAtStop: handleDepartureAtStop(event); break;
            default: throw new RuntimeException("Unsupported TransitEvent type.");
        }
    }

    private void handleArrivalAtStop(TransitEvent event) {
        double stopTime;
        do {
            stopTime = event.driver.handleTransitStop(event.stop.getStopFacility(), event.time);
        } while (stopTime > 0);
        double depOffset = event.stop.getDepartureOffset();
        if (depOffset == Time.UNDEFINED_TIME) {
            depOffset = event.stop.getArrivalOffset() + 30;
        }
        double depTime = event.driver.getDeparture().getDepartureTime() + depOffset;
        TransitEvent depEvent = new TransitEvent(depTime, TransitEventType.DepartureAtStop, event.driver, event.stopIter, event.stop);
        this.eventQueue.add(depEvent);
    }

    private void handleDepartureAtStop(TransitEvent event) {
        // TODO the departure event was already sent when handleTransitStop return 0.0, and thus too early
        // so we just need to update our internal data structures and schedule a new event
        if (event.stopIter.hasNext()) {
            TransitRouteStop nextStop = event.stopIter.next();
            double arrOffset = nextStop.getArrivalOffset();
            if (arrOffset == Time.UNDEFINED_TIME) {
                arrOffset = nextStop.getDepartureOffset();
            }
            double arrTime = event.driver.getDeparture().getDepartureTime() + arrOffset;
            TransitEvent arrEvent = new TransitEvent(arrTime, TransitEventType.ArrivalAtStop, event.driver, event.stopIter, nextStop);
            this.eventQueue.add(arrEvent);
        } else {
            this.qSim.getEventsManager().processEvent(new PersonLeavesVehicleEvent(event.time, event.driver.getId(), event.driver.getVehicle().getId()));
            event.driver.endLegAndComputeNextState(event.time);
            this.internalInterface.arrangeNextAgentState(event.driver);
        }
    }

    private enum TransitEventType { ArrivalAtStop, DepartureAtStop }

    private static class TransitEvent implements Comparable<TransitEvent> {
        double time;
        TransitEventType type;
        TransitDriverAgentImpl driver;
        Iterator<TransitRouteStop> stopIter;
        TransitRouteStop stop;

        TransitEvent(double time, TransitEventType type, TransitDriverAgentImpl driver, Iterator<TransitRouteStop> stopIter, TransitRouteStop stop) {
            this.time = time;
            this.type = type;
            this.driver = driver;
            this.stopIter = stopIter;
            this.stop = stop;
        }

        @Override
        public int compareTo(TransitEvent o) {
            int result = Double.compare(this.time, o.time);
            if (result == 0) {
                if (this.type == o.type) {
                    result = this.driver.getId().compareTo(o.driver.getId());
                } else {
                    // arrivals should come before departures
                    result = this.type == TransitEventType.ArrivalAtStop ? -1 : +1;
                }
            }
            return result;
        }
    }
}
