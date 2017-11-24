/*
 * Copyright (C) Schweizerische Bundesbahnen SBB, 2017.
 */

package ch.sbb.matsim.mobsim.qsim.pt;

import org.junit.Assert;
import org.junit.Test;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.events.ActivityEndEvent;
import org.matsim.api.core.v01.events.ActivityStartEvent;
import org.matsim.api.core.v01.events.Event;
import org.matsim.api.core.v01.events.PersonArrivalEvent;
import org.matsim.api.core.v01.events.PersonDepartureEvent;
import org.matsim.api.core.v01.events.PersonEntersVehicleEvent;
import org.matsim.api.core.v01.events.PersonLeavesVehicleEvent;
import org.matsim.api.core.v01.events.TransitDriverStartsEvent;
import org.matsim.api.core.v01.population.Person;
import org.matsim.core.api.experimental.events.AgentWaitingForPtEvent;
import org.matsim.core.api.experimental.events.EventsManager;
import org.matsim.core.api.experimental.events.VehicleArrivesAtFacilityEvent;
import org.matsim.core.api.experimental.events.VehicleDepartsAtFacilityEvent;
import org.matsim.core.events.EventsUtils;
import org.matsim.core.mobsim.framework.MobsimAgent;
import org.matsim.core.mobsim.qsim.AbstractQSimPlugin;
import org.matsim.core.mobsim.qsim.ActivityEnginePlugin;
import org.matsim.core.mobsim.qsim.PopulationPlugin;
import org.matsim.core.mobsim.qsim.QSim;
import org.matsim.core.mobsim.qsim.QSimUtils;
import org.matsim.core.utils.misc.Time;
import org.matsim.pt.transitSchedule.api.TransitRoute;
import org.matsim.pt.transitSchedule.api.TransitRouteStop;
import org.matsim.pt.transitSchedule.api.TransitStopFacility;
import org.matsim.testcases.utils.EventsCollector;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * @author mrieser / SBB
 */
public class SBBTransitQSimEngineTest {

    @Test
    public void testDriver() {
        TestFixture f = new TestFixture();

        EventsManager eventsManager = EventsUtils.createEventsManager(f.config);
        QSim qSim = QSimUtils.createDefaultQSim(f.scenario, eventsManager);
        SBBTransitQSimEngine trEngine = new SBBTransitQSimEngine(qSim);
        qSim.addMobsimEngine(trEngine);

        trEngine.insertAgentsIntoMobsim();

        Map<Id<Person>, MobsimAgent> agents = qSim.getAgents();
        Assert.assertEquals("Expected one driver as agent.", 1, agents.size());
        MobsimAgent agent = agents.values().iterator().next();
        Assert.assertTrue(agent instanceof SBBTransitDriverAgent);
        SBBTransitDriverAgent driver = (SBBTransitDriverAgent) agent;
        TransitRoute route = driver.getTransitRoute();
        List<TransitRouteStop> stops = route.getStops();
        double depTime = driver.getActivityEndTime();

        assertNextStop(driver, stops.get(0), depTime);
        assertNextStop(driver, stops.get(1), depTime);
        assertNextStop(driver, stops.get(2), depTime);
        assertNextStop(driver, stops.get(3), depTime);
        assertNextStop(driver, stops.get(4), depTime);

        Assert.assertNull(driver.getNextRouteStop());
    }

    private void assertNextStop(SBBTransitDriverAgent driver, TransitRouteStop stop, double routeDepTime) {
        double arrOffset = stop.getArrivalOffset();
        double depOffset = stop.getDepartureOffset();
        if (arrOffset == Time.UNDEFINED_TIME) arrOffset = depOffset;
        if (depOffset == Time.UNDEFINED_TIME) depOffset = arrOffset;
        TransitStopFacility f = stop.getStopFacility();

        Assert.assertEquals(stop, driver.getNextRouteStop());
        double stopTime = driver.handleTransitStop(f, routeDepTime + arrOffset);
        Assert.assertEquals(depOffset - arrOffset, stopTime, 1e-7);
        Assert.assertEquals(0.0, driver.handleTransitStop(f, routeDepTime + arrOffset + stopTime), 1e-7);
        driver.depart(f, routeDepTime + depOffset);
    }

    @Test
    public void testEvents_withoutPassengers_withoutLinks() {
        TestFixture f = new TestFixture();

        EventsManager eventsManager = EventsUtils.createEventsManager(f.config);
        List<AbstractQSimPlugin> plugins = new ArrayList<>();
        plugins.add(new ActivityEnginePlugin(f.config));
        plugins.add(new SBBTransitEnginePlugin(f.config));

        // to compare to original TransitQSimEngine, use the following two instead of the SBBTransitEnginePlugin
//        plugins.add(new TransitEnginePlugin(f.config));
//        plugins.add(new QNetsimEnginePlugin(f.config));

        QSim qSim = QSimUtils.createQSim(f.scenario, eventsManager, plugins);

        Assert.assertEquals(SBBTransitQSimEngine.class, qSim.getTransitEngine().getClass());

        EventsCollector collector = new EventsCollector();
        eventsManager.addHandler(collector);
        qSim.run();
        List<Event> allEvents = collector.getEvents();

        for (Event event : allEvents) {
            System.out.println(event.toString());
        }

        Assert.assertEquals("wrong number of events.", 15, allEvents.size());
        assertEqualEvent(TransitDriverStartsEvent.class,      30000, allEvents.get(0));
        assertEqualEvent(PersonDepartureEvent.class,          30000, allEvents.get(1));
        assertEqualEvent(PersonEntersVehicleEvent.class,      30000, allEvents.get(2));
        assertEqualEvent(VehicleArrivesAtFacilityEvent.class, 30000, allEvents.get(3));
        assertEqualEvent(VehicleDepartsAtFacilityEvent.class, 30000, allEvents.get(4));
        assertEqualEvent(VehicleArrivesAtFacilityEvent.class, 30100, allEvents.get(5));
        assertEqualEvent(VehicleDepartsAtFacilityEvent.class, 30120, allEvents.get(6));
        assertEqualEvent(VehicleArrivesAtFacilityEvent.class, 30300, allEvents.get(7));
        assertEqualEvent(VehicleDepartsAtFacilityEvent.class, 30300, allEvents.get(8));
        assertEqualEvent(VehicleArrivesAtFacilityEvent.class, 30570, allEvents.get(9));
        assertEqualEvent(VehicleDepartsAtFacilityEvent.class, 30600, allEvents.get(10));
        assertEqualEvent(VehicleArrivesAtFacilityEvent.class, 30720, allEvents.get(11));
        assertEqualEvent(VehicleDepartsAtFacilityEvent.class, 30750, allEvents.get(12));
        assertEqualEvent(PersonLeavesVehicleEvent.class,      30750, allEvents.get(13));
        assertEqualEvent(PersonArrivalEvent.class,            30750, allEvents.get(14));
    }

    @Test
    public void testEvents_withPassengers_withoutLinks() {
        TestFixture f = new TestFixture();
        f.addSingleTransitDemand();

        EventsManager eventsManager = EventsUtils.createEventsManager(f.config);
        List<AbstractQSimPlugin> plugins = new ArrayList<>();
        plugins.add(new ActivityEnginePlugin(f.config));
        plugins.add(new PopulationPlugin(f.config));
        plugins.add(new SBBTransitEnginePlugin(f.config));

        // to compare to original TransitQSimEngine, use the following two instead of the SBBTransitEnginePlugin
//        plugins.add(new TransitEnginePlugin(f.config));
//        plugins.add(new QNetsimEnginePlugin(f.config));

        QSim qSim = QSimUtils.createQSim(f.scenario, eventsManager, plugins);

        Assert.assertEquals(SBBTransitQSimEngine.class, qSim.getTransitEngine().getClass());

        EventsCollector collector = new EventsCollector();
        eventsManager.addHandler(collector);
        qSim.run();
        List<Event> allEvents = collector.getEvents();

        for (Event event : allEvents) {
            System.out.println(event.toString());
        }

        Assert.assertEquals("wrong number of events.", 22, allEvents.size());
        assertEqualEvent(ActivityEndEvent.class,              29500, allEvents.get(0));
        assertEqualEvent(PersonDepartureEvent.class,          29500, allEvents.get(1));
        assertEqualEvent(AgentWaitingForPtEvent.class,        29500, allEvents.get(2));
        assertEqualEvent(TransitDriverStartsEvent.class,      30000, allEvents.get(3));
        assertEqualEvent(PersonDepartureEvent.class,          30000, allEvents.get(4));
        assertEqualEvent(PersonEntersVehicleEvent.class,      30000, allEvents.get(5));
        assertEqualEvent(VehicleArrivesAtFacilityEvent.class, 30000, allEvents.get(6));
        assertEqualEvent(VehicleDepartsAtFacilityEvent.class, 30000, allEvents.get(7));
        assertEqualEvent(VehicleArrivesAtFacilityEvent.class, 30100, allEvents.get(8));
        assertEqualEvent(PersonEntersVehicleEvent.class,      30100, allEvents.get(9));
        assertEqualEvent(VehicleDepartsAtFacilityEvent.class, 30120, allEvents.get(10));
        assertEqualEvent(VehicleArrivesAtFacilityEvent.class, 30300, allEvents.get(11));
        assertEqualEvent(VehicleDepartsAtFacilityEvent.class, 30300, allEvents.get(12));
        assertEqualEvent(VehicleArrivesAtFacilityEvent.class, 30570, allEvents.get(13));
        assertEqualEvent(PersonLeavesVehicleEvent.class,      30570, allEvents.get(14));
        assertEqualEvent(PersonArrivalEvent.class,            30570, allEvents.get(15));
        assertEqualEvent(ActivityStartEvent.class,            30570, allEvents.get(16));
        assertEqualEvent(VehicleDepartsAtFacilityEvent.class, 30600, allEvents.get(17));
        assertEqualEvent(VehicleArrivesAtFacilityEvent.class, 30720, allEvents.get(18));
        assertEqualEvent(VehicleDepartsAtFacilityEvent.class, 30750, allEvents.get(19));
        assertEqualEvent(PersonLeavesVehicleEvent.class,      30750, allEvents.get(20));
        assertEqualEvent(PersonArrivalEvent.class,            30750, allEvents.get(21));
    }

    private static void assertEqualEvent(Class<? extends Event> eventClass, double time, Event event) {
        Assert.assertTrue(event.getClass().isAssignableFrom(event.getClass()));
        Assert.assertEquals(time, event.getTime(), 1e-7);
    }
}
