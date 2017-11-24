/*
 * Copyright (C) Schweizerische Bundesbahnen SBB, 2017.
 */

package ch.sbb.matsim.mobsim.qsim.pt;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.events.PersonEntersVehicleEvent;
import org.matsim.api.core.v01.events.PersonLeavesVehicleEvent;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.population.Person;
import org.matsim.core.api.experimental.events.BoardingDeniedEvent;
import org.matsim.core.api.experimental.events.EventsManager;
import org.matsim.core.mobsim.framework.MobsimAgent;
import org.matsim.core.mobsim.framework.MobsimDriverAgent;
import org.matsim.core.mobsim.framework.PassengerAgent;
import org.matsim.core.mobsim.qsim.InternalInterface;
import org.matsim.core.mobsim.qsim.interfaces.MobsimVehicle;
import org.matsim.core.mobsim.qsim.pt.PTPassengerAgent;
import org.matsim.core.mobsim.qsim.pt.PassengerAccessEgress;
import org.matsim.core.mobsim.qsim.pt.TransitStopAgentTracker;
import org.matsim.core.mobsim.qsim.pt.TransitVehicle;
import org.matsim.pt.transitSchedule.api.TransitLine;
import org.matsim.pt.transitSchedule.api.TransitRoute;
import org.matsim.pt.transitSchedule.api.TransitRouteStop;
import org.matsim.pt.transitSchedule.api.TransitStopFacility;
import org.matsim.vehicles.Vehicle;

import java.util.ArrayList;
import java.util.List;

/**
 * @author mrieser / SBB
 */
public class SBBPassengerAccessEgress implements PassengerAccessEgress {

    private final InternalInterface internalInterface;
    private final TransitStopAgentTracker agentTracker;
    private final EventsManager eventsManager;
    private final boolean isGeneratingDeniedBoardingEvents;
    private final List<PTPassengerAgent> deniedBoarding;

    SBBPassengerAccessEgress(InternalInterface internalInterface, TransitStopAgentTracker agentTracker, Scenario scenario, EventsManager eventsManager) {
        this.internalInterface = internalInterface;
        this.agentTracker = agentTracker;
        this.eventsManager = eventsManager;
        this.isGeneratingDeniedBoardingEvents = scenario.getConfig().vspExperimental().isGeneratingBoardingDeniedEvents() ;
        this.deniedBoarding = this.isGeneratingDeniedBoardingEvents ? new ArrayList<>() : null;
    }

    void handlePassengers(TransitStopFacility stop, TransitVehicle vehicle, TransitLine line, TransitRoute route, List<TransitRouteStop> upcomingStops, double now) {
        List<PTPassengerAgent> leavingPassengers = findPassengersLeaving(vehicle, stop);
        for (PTPassengerAgent passenger : leavingPassengers) {
            handlePassengerLeaving(passenger, vehicle, passenger.getDestinationLinkId(), now);
        }

        int freeCapacity = vehicle.getPassengerCapacity() -  vehicle.getPassengers().size();

        List<PTPassengerAgent> boardingPassengers = findPassengersBoarding(route, line, vehicle, stop, upcomingStops, freeCapacity, now);
        for (PTPassengerAgent passenger : boardingPassengers) {
            boolean entered = handlePassengerEntering(passenger, vehicle, passenger.getDesiredAccessStopId(), now);
            if (!entered && this.isGeneratingDeniedBoardingEvents) {
                this.deniedBoarding.add(passenger);
            }
        }
        if (this.isGeneratingDeniedBoardingEvents && !this.deniedBoarding.isEmpty()) {
            for (PTPassengerAgent passenger : this.deniedBoarding) {
                fireBoardingDeniedEvents(vehicle, now);
            }
        }
    }

    @Override
    public boolean handlePassengerLeaving(PTPassengerAgent passenger, MobsimVehicle vehicle, Id<Link> toLinkId, double time) {
        boolean removed = vehicle.removePassenger(passenger);
        if (removed) {
            this.eventsManager.processEvent(new PersonLeavesVehicleEvent(time, passenger.getId(), vehicle.getVehicle().getId()));
            MobsimAgent agent = (MobsimAgent) passenger;
            agent.notifyArrivalOnLinkByNonNetworkMode(toLinkId);
            agent.endLegAndComputeNextState(time);
            this.internalInterface.arrangeNextAgentState(agent);
        }
        return removed;
    }

    @Override
    public boolean handlePassengerEntering(PTPassengerAgent passenger, MobsimVehicle vehicle,  Id<TransitStopFacility> fromStopFacilityId, double time) {
        boolean entered = vehicle.addPassenger(passenger);
        if (entered) {
            this.agentTracker.removeAgentFromStop(passenger, fromStopFacilityId);
            Id<Person> agentId = passenger.getId();
            Id<Link> linkId = passenger.getCurrentLinkId();
            this.internalInterface.unregisterAdditionalAgentOnLink(agentId, linkId);
            MobsimDriverAgent agent = (MobsimDriverAgent) passenger;
            this.eventsManager.processEvent(new PersonEntersVehicleEvent(time, agent.getId(), vehicle.getVehicle().getId()));
        }
        return entered;
    }

    private ArrayList<PTPassengerAgent> findPassengersLeaving(TransitVehicle vehicle,
                                                              final TransitStopFacility stop) {
        ArrayList<PTPassengerAgent> passengersLeaving = new ArrayList<>();
        for (PassengerAgent passenger : vehicle.getPassengers()) {
            if (((PTPassengerAgent) passenger).getExitAtStop(stop)) {
                passengersLeaving.add((PTPassengerAgent) passenger);
            }
        }
        return passengersLeaving;
    }

    private List<PTPassengerAgent> findPassengersBoarding(TransitRoute transitRoute, TransitLine transitLine, TransitVehicle vehicle,
                                                          final TransitStopFacility stop, List<TransitRouteStop> stopsToCome, int freeCapacity, double now) {
        ArrayList<PTPassengerAgent> passengersEntering = new ArrayList<>();
        if (this.isGeneratingDeniedBoardingEvents) {
            for (PTPassengerAgent agent : this.agentTracker.getAgentsAtStop(stop.getId())) {
                if (agent.getEnterTransitRoute(transitLine, transitRoute, stopsToCome, vehicle)) {
                    if (freeCapacity >= 1) {
                        passengersEntering.add(agent);
                        freeCapacity--;
                    } else {
                        this.deniedBoarding.add(agent);
                    }
                }
            }
        } else {
            for (PTPassengerAgent agent : this.agentTracker.getAgentsAtStop(stop.getId())) {
                if (freeCapacity == 0) {
                    break;
                }
                if (agent.getEnterTransitRoute(transitLine, transitRoute, stopsToCome, vehicle)) {
                    passengersEntering.add(agent);
                    freeCapacity--;
                }
            }
        }
        return passengersEntering;
    }

    private void fireBoardingDeniedEvents(TransitVehicle vehicle, double now){
        Id<Vehicle> vehicleId = vehicle.getId();
        for (PTPassengerAgent agent : this.deniedBoarding) {
            this.eventsManager.processEvent(new BoardingDeniedEvent(now, agent.getId(), vehicleId));
        }
        this.deniedBoarding.clear();
    }

}
