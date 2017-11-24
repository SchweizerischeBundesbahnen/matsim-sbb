/*
 * Copyright (C) Schweizerische Bundesbahnen SBB, 2017.
 */

package ch.sbb.matsim.mobsim.qsim.pt;

import org.junit.Assert;
import org.junit.Test;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.events.Event;
import org.matsim.api.core.v01.events.PersonDepartureEvent;
import org.matsim.api.core.v01.events.TransitDriverStartsEvent;
import org.matsim.api.core.v01.population.Person;
import org.matsim.core.api.experimental.events.EventsManager;
import org.matsim.core.api.experimental.events.VehicleArrivesAtFacilityEvent;
import org.matsim.core.api.experimental.events.VehicleDepartsAtFacilityEvent;
import org.matsim.core.events.EventsUtils;
import org.matsim.core.mobsim.framework.MobsimAgent;
import org.matsim.core.mobsim.qsim.AbstractQSimPlugin;
import org.matsim.core.mobsim.qsim.ActivityEnginePlugin;
import org.matsim.core.mobsim.qsim.QSim;
import org.matsim.core.mobsim.qsim.QSimUtils;
import org.matsim.core.mobsim.qsim.pt.TransitDriverAgent;
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
        TransitDriverAgent driver = (TransitDriverAgent) agent;
        Assert.assertEquals(f.stopA, driver.getNextTransitStop());
        driver.handleTransitStop(f.stopA, 0);
        Assert.assertEquals(f.stopB, driver.getNextTransitStop());
        driver.handleTransitStop(f.stopB, 0);
        Assert.assertEquals(f.stopC, driver.getNextTransitStop());
        driver.handleTransitStop(f.stopC, 0);
        Assert.assertEquals(f.stopD, driver.getNextTransitStop());
        driver.handleTransitStop(f.stopD, 0);
        Assert.assertEquals(f.stopE, driver.getNextTransitStop());
        driver.handleTransitStop(f.stopE, 0);
        Assert.assertNull(driver.getNextTransitStop());
    }

    @Test
    public void testEvents_withoutPassengers_withoutLinks() {
        TestFixture f = new TestFixture();

        EventsManager eventsManager = EventsUtils.createEventsManager(f.config);
        List<AbstractQSimPlugin> plugins = new ArrayList<>();
        plugins.add(new ActivityEnginePlugin(f.config));
        plugins.add(new SBBTransitEnginePlugin(f.config));
        QSim qSim = QSimUtils.createQSim(f.scenario, eventsManager, plugins);

        Assert.assertEquals(SBBTransitQSimEngine.class, qSim.getTransitEngine().getClass());

        EventsCollector collector = new EventsCollector();
        eventsManager.addHandler(collector);
        qSim.run();
        List<Event> allEvents = collector.getEvents();

        for (Event event : allEvents) {
            System.out.println(event.toString());
        }

        Assert.assertEquals("wrong number of events.", 13, allEvents.size());
        assertEqualEvent(TransitDriverStartsEvent.class,      30000, allEvents.get(0));
        assertEqualEvent(PersonDepartureEvent.class,          30000, allEvents.get(1));
        assertEqualEvent(VehicleArrivesAtFacilityEvent.class, 30000, allEvents.get(2));
        assertEqualEvent(VehicleDepartsAtFacilityEvent.class, 30000, allEvents.get(3));
        assertEqualEvent(VehicleArrivesAtFacilityEvent.class, 30100, allEvents.get(4));
        assertEqualEvent(VehicleDepartsAtFacilityEvent.class, 30120, allEvents.get(5));
        assertEqualEvent(VehicleArrivesAtFacilityEvent.class, 30300, allEvents.get(6));
        assertEqualEvent(VehicleDepartsAtFacilityEvent.class, 30300, allEvents.get(7));
        assertEqualEvent(VehicleArrivesAtFacilityEvent.class, 30570, allEvents.get(8));
        assertEqualEvent(VehicleDepartsAtFacilityEvent.class, 30600, allEvents.get(9));
        assertEqualEvent(VehicleArrivesAtFacilityEvent.class, 30720, allEvents.get(10));
        assertEqualEvent(VehicleDepartsAtFacilityEvent.class, 30750, allEvents.get(11));
        assertEqualEvent(TransitDriverStartsEvent.class,      30750, allEvents.get(12));
    }

    private static void assertEqualEvent(Class<? extends Event> eventClass, double time, Event event) {
        Assert.assertTrue(event.getClass().isAssignableFrom(event.getClass()));
        Assert.assertEquals(time, event.getTime(), 1e-7);
    }
}
