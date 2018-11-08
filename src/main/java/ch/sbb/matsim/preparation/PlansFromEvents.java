/*
 * Copyright (C) Schweizerische Bundesbahnen SBB, 2017.
 */

package ch.sbb.matsim.preparation;

import org.apache.log4j.Logger;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.events.ActivityEndEvent;
import org.matsim.api.core.v01.events.ActivityStartEvent;
import org.matsim.api.core.v01.events.LinkEnterEvent;
import org.matsim.api.core.v01.events.PersonArrivalEvent;
import org.matsim.api.core.v01.events.PersonDepartureEvent;
import org.matsim.api.core.v01.events.PersonEntersVehicleEvent;
import org.matsim.api.core.v01.events.PersonLeavesVehicleEvent;
import org.matsim.api.core.v01.events.TransitDriverStartsEvent;
import org.matsim.api.core.v01.events.VehicleEntersTrafficEvent;
import org.matsim.api.core.v01.events.VehicleLeavesTrafficEvent;
import org.matsim.api.core.v01.events.handler.ActivityEndEventHandler;
import org.matsim.api.core.v01.events.handler.ActivityStartEventHandler;
import org.matsim.api.core.v01.events.handler.LinkEnterEventHandler;
import org.matsim.api.core.v01.events.handler.PersonArrivalEventHandler;
import org.matsim.api.core.v01.events.handler.PersonDepartureEventHandler;
import org.matsim.api.core.v01.events.handler.PersonEntersVehicleEventHandler;
import org.matsim.api.core.v01.events.handler.PersonLeavesVehicleEventHandler;
import org.matsim.api.core.v01.events.handler.TransitDriverStartsEventHandler;
import org.matsim.api.core.v01.events.handler.VehicleEntersTrafficEventHandler;
import org.matsim.api.core.v01.events.handler.VehicleLeavesTrafficEventHandler;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.population.Activity;
import org.matsim.api.core.v01.population.Leg;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.Plan;
import org.matsim.api.core.v01.population.PlanElement;
import org.matsim.api.core.v01.population.Population;
import org.matsim.api.core.v01.population.Route;
import org.matsim.core.api.experimental.events.EventsManager;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.events.EventsManagerImpl;
import org.matsim.core.events.MatsimEventsReader;
import org.matsim.core.network.io.MatsimNetworkReader;
import org.matsim.core.population.PopulationUtils;
import org.matsim.core.population.io.PopulationWriter;
import org.matsim.core.population.routes.RouteUtils;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.vehicles.Vehicle;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

public class PlansFromEvents implements PersonArrivalEventHandler, PersonDepartureEventHandler,
        ActivityStartEventHandler, ActivityEndEventHandler, TransitDriverStartsEventHandler,
        PersonEntersVehicleEventHandler, PersonLeavesVehicleEventHandler,
        VehicleEntersTrafficEventHandler, VehicleLeavesTrafficEventHandler,
        LinkEnterEventHandler {

    private HashSet<Id> transitDrivers = new HashSet<>();
    private Population population;
    private Network network;
    private Map<Id<Vehicle>, List<Person>> personsPerVehicleIds = new HashMap<>();
    private Map<Person, LinkedList<Id<Link>>> actLinkIdsPerPerson = new HashMap<>();
    private Map<Person, Double> actDistancePerPerson = new HashMap<>();

    private List<String> toIgnore = Arrays.asList("vehicle_interaction", "vehicle_parking", "egress_walk", "access_walk");

    private final static Logger log = Logger.getLogger(PlansFromEvents.class);

    public PlansFromEvents(Network network){
        this.population = PopulationUtils.createPopulation(ConfigUtils.createConfig());
        this.network = network;
    }

    // Methods
    @Override
    public void reset(int iteration) {
        population.getPersons().clear();
        transitDrivers.clear();
    }

    @Override
    public void handleEvent(PersonArrivalEvent event) {
//        if(!transitDrivers.contains(event.getPersonId())){
//            Person person = population.getPersons().get(event.getPersonId());
//        }
    }

    @Override
    public void handleEvent(PersonDepartureEvent event) {
        Person person = createPersonIfNecessary(event.getPersonId());
        if(person != null) {
            //Leg leg = getLastLeg(person.getSelectedPlan());
            if(this.toIgnore.contains(event.getLegMode())){
                return;
            }
            Leg leg = population.getFactory().createLeg(event.getLegMode());
            person.getSelectedPlan().addLeg(leg);
        }
    }

    @Override
    public void handleEvent(ActivityStartEvent event) {
        Person person = createPersonIfNecessary(event.getPersonId());
        if(person != null) {
            if(this.toIgnore.contains(event.getActType())){
                return;
            }
            Link link = this.network.getLinks().get(event.getLinkId());
            Activity activity = population.getFactory().createActivityFromCoord(event.getActType(), link.getCoord());
            activity.setStartTime(event.getTime());
            activity.setLinkId(event.getLinkId());
            person.getSelectedPlan().addActivity(activity);
        }
    }

    @Override
    public void handleEvent(ActivityEndEvent event) {
        Person person = createPersonIfNecessary(event.getPersonId());
        if(person != null){
            if(this.toIgnore.contains(event.getActType())){
                return;
            }

            Activity activity = getLastActivity(person.getSelectedPlan());
            if(activity==null || !activity.getType().equals(event.getActType())){
                Link link = this.network.getLinks().get(event.getLinkId());
                activity = population.getFactory().createActivityFromCoord(event.getActType(), link.getCoord());
                activity.setEndTime(event.getTime());
                activity.setStartTime(0.0); // must be the first event starting at 00:00:00 which does not fire a ActivityStartEvent
                activity.setLinkId(event.getLinkId());
                person.getSelectedPlan().addActivity(activity);
            }
            else{
                activity.setEndTime(event.getTime());
            }
        }
    }

    private Person createPersonIfNecessary(Id<Person> personId){
        if(transitDrivers.contains(personId)){
            return null;
        }
        Person person = this.population.getPersons().get(personId);
        if (person == null) {
            person = population.getFactory().createPerson(personId);
            population.addPerson(person);
            Plan plan = population.getFactory().createPlan();
            person.addPlan(plan);
            person.setSelectedPlan(plan);
        }
        return person;
    }


    private Leg getLastLeg(Plan plan){
        int size = getLegs(plan).size();
        if(size==0){
            return null;
        }
        else{
            return getLegs(plan).get(size-1);
        }
    }

    private Activity getLastActivity(Plan plan){
        int size = getActivities(plan).size();
        if(size==0){
            return null;
        }
        else{
            return getActivities(plan).get(size-1);
        }
    }

    private List<Leg> getLegs(Plan plan){
        List<Leg> list = new ArrayList<>();
        for(PlanElement pe: plan.getPlanElements()){
            if(pe instanceof Leg){
                list.add((Leg) pe);
            }
        }
        return list;
    }

    private List<Activity> getActivities(Plan plan){
        List<Activity> list = new ArrayList<>();
        for(PlanElement pe: plan.getPlanElements()){
            if(pe instanceof Activity){
                list.add((Activity) pe);
            }
        }
        return list;
    }

    @Override
    public void handleEvent(TransitDriverStartsEvent event) {
        population.getPersons().remove(event.getDriverId());
        transitDrivers.add(event.getDriverId());
    }

    @Override
    public void handleEvent(PersonEntersVehicleEvent event) {
        List<Person> personsInVehicle = personsPerVehicleIds.get(event.getVehicleId());
        if (personsInVehicle == null) {
            personsInVehicle = new ArrayList<>();
            personsPerVehicleIds.put(event.getVehicleId(), personsInVehicle);
        }
        Person person = createPersonIfNecessary(event.getPersonId());
        if (person != null) {
            personsInVehicle.add(person);
            actLinkIdsPerPerson.put(person, new LinkedList<Id<Link>>());
            actDistancePerPerson.put(person, 0.0);
            getLastLeg(person.getSelectedPlan()).setDepartureTime(event.getTime());
        }
    }

    @Override
    public void handleEvent(PersonLeavesVehicleEvent event) {
        Person person = createPersonIfNecessary(event.getPersonId());
        if (person != null) {
            List<Person> personsInVehicle = personsPerVehicleIds.get(event.getVehicleId());
            if (personsInVehicle == null) {
                throw new IllegalStateException("there must be at least one person in the vehicle");
            }
            else {
                if (!(personsInVehicle.remove(person))) throw new IllegalStateException("Person must be in vehicle " + person.getId());
            }
            Leg leg = getLastLeg(person.getSelectedPlan());
            leg.setTravelTime(event.getTime() - leg.getDepartureTime());
            if (leg.getMode().equals(TransportMode.car)) {
                // at the moment only for car-routes
                List<Id<Link>> actLinks = actLinkIdsPerPerson.get(person);
                if (actLinks.size() > 2) {
                    // there should be at least one proper link in the route
                    Id<Link> firstLinkId = actLinks.remove(0);
                    Id<Link> lastLinkId = actLinks.remove(actLinks.size() - 1);
                    Route route = RouteUtils.createLinkNetworkRouteImpl(firstLinkId, actLinks, lastLinkId);
                    route.setDistance(actDistancePerPerson.get(person));
                    route.setTravelTime(leg.getTravelTime());
                    leg.setRoute(route);
                }
                else log.info("Mode of leg is car, but there are no proper links defined for person " + person.getId());
            }
            if (actLinkIdsPerPerson.remove(person) == null) throw new IllegalStateException("Person must be in vehicle " + person.getId());
            if (actDistancePerPerson.remove(person) == null) throw new IllegalStateException("Distance for person must be defined " + person.getId());
        }
    }

    @Override
    public void handleEvent(VehicleEntersTrafficEvent event) {
        handleNewLink(event.getVehicleId(), event.getLinkId(), event.getRelativePositionOnLink());
    }

    @Override
    public void handleEvent(VehicleLeavesTrafficEvent event) {
        // link was already added with full distance as LinkEnterEvent, just adjust distance
        for (Person person: personsPerVehicleIds.get(event.getVehicleId())) {
            Double actDistance = actDistancePerPerson.get(person);
            actDistance -= event.getRelativePositionOnLink() * network.getLinks().get(event.getLinkId()).getLength();
            actDistancePerPerson.put(person, actDistance);
        }
    }

    @Override
    public void handleEvent(LinkEnterEvent event) {
        handleNewLink(event.getVehicleId(), event.getLinkId(), 0.0);
    }

    private void handleNewLink(Id<Vehicle> vehicleId, Id<Link> linkId, double relPos) {
        for (Person person: personsPerVehicleIds.get(vehicleId)) {
            List<Id<Link>> actLinks = actLinkIdsPerPerson.get(person);
            if (actLinks == null) throw new IllegalStateException("link-list for person " + person.getId() + " must be defined");
            actLinks.add(linkId);
            Double actDistance = actDistancePerPerson.get(person);
            if (actDistance == null) throw new IllegalStateException("distance por person " + person.getId() + " must be defined");
            actDistance += (1.0 - relPos) * network.getLinks().get(linkId).getLength();
            actDistancePerPerson.put(person, actDistance);
        }
    }

    public static void main(String[] args) {
        String eventsFileName = args[0];
        String networkFile = args[1];
        String planFile = args[2];

        EventsManager events = new EventsManagerImpl();

        Config config = ConfigUtils.createConfig();
        Scenario scenario = ScenarioUtils.createScenario(config);

        new MatsimNetworkReader(scenario.getNetwork()).readFile(networkFile);
        PlansFromEvents plansHandler = new PlansFromEvents(scenario.getNetwork());

        events.addHandler(plansHandler);
        new MatsimEventsReader(events).readFile(eventsFileName);
        Cleaner cleaner = new Cleaner(plansHandler.population);
        cleaner.clean(Arrays.asList(TransportMode.pt), Arrays.asList("all"));
        new PopulationWriter(plansHandler.population).write(planFile);
    }
}
