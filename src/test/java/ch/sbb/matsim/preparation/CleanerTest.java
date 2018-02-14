/*
 * Copyright (C) Schweizerische Bundesbahnen SBB, 2018.
 */

package ch.sbb.matsim.preparation;

import org.apache.log4j.Logger;
import org.junit.Rule;
import org.junit.Test;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.population.Leg;
import org.matsim.api.core.v01.population.Plan;
import org.matsim.api.core.v01.population.PlanElement;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.population.io.PopulationReader;
import org.matsim.core.population.io.PopulationWriter;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.testcases.MatsimTestUtils;

import java.io.ByteArrayInputStream;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

/**
 *
 * @author pmanser / SBB
 */

public class CleanerTest {

    @Rule
    public MatsimTestUtils utils = new MatsimTestUtils();

    private static final Logger log = Logger.getLogger(CleanerTest.class);

    @Test
    public void testPlansRemover() {
        Fixture f = new Fixture();
        Cleaner cleaner = new Cleaner(f.scenario.getPopulation());

        assertEquals(2, f.scenario.getPopulation().getPersons().get(Id.createPersonId("1")).getPlans().size(), 0.0);
        cleaner.removeNonSelectedPlans();
        assertEquals(1, f.scenario.getPopulation().getPersons().get(Id.createPersonId("1")).getPlans().size(), 0.0);
    }

    @Test
    public void testCarAgentNoAccessTimes() {
        Fixture f = new Fixture();
        Cleaner cleaner = new Cleaner(f.scenario.getPopulation());

        Leg carLeg = (Leg) f.scenario.getPopulation().getPersons().get(Id.createPersonId("1")).getPlans().get(0).getPlanElements().get(1);

        assertEquals(carLeg.getMode(), TransportMode.car);
        assertEquals(carLeg.getRoute().getStartLinkId().toString(), "2");

        cleaner.clean(Arrays.asList(TransportMode.car), Arrays.asList("regular"));
        assertEquals(carLeg.getMode(), TransportMode.car);
        assertNotNull(carLeg.getRoute());

        cleaner.clean(Arrays.asList(TransportMode.pt), Arrays.asList("all"));
        assertEquals(carLeg.getMode(), TransportMode.car);
        assertNotNull(carLeg.getRoute());

        cleaner.clean(Arrays.asList(TransportMode.pt, TransportMode.car), Arrays.asList("all"));
        assertEquals(carLeg.getMode(), TransportMode.car);
        assertNull(carLeg.getRoute());
    }

    @Test
    public void testCarAgentWithAccessTimes() {
        Fixture f = new Fixture();
        Cleaner cleaner = new Cleaner(f.scenario.getPopulation());

        List<PlanElement> pe =  f.scenario.getPopulation().getPersons().get(Id.createPersonId("2")).getPlans().get(0).getPlanElements();
        assertEquals(7, pe.size(), 0);
        assertEquals(TransportMode.access_walk, ((Leg) pe.get(1)).getMode());

        cleaner.clean(Arrays.asList(TransportMode.pt), Arrays.asList("regular"));
        assertEquals(7, pe.size(), 0);
        assertEquals(TransportMode.access_walk, ((Leg) pe.get(1)).getMode());

        cleaner.clean(Arrays.asList(TransportMode.car), Arrays.asList("cb"));
        assertEquals(7, pe.size(), 0);
        assertEquals(TransportMode.access_walk, ((Leg) pe.get(1)).getMode());

        cleaner.clean(Arrays.asList(TransportMode.car), Arrays.asList("regular"));
        assertEquals(3, pe.size(), 0);
        assertEquals(TransportMode.car, ((Leg) pe.get(1)).getMode());
    }

    @Test
    public void testBikeAgentNoAccessTimes() {
        Fixture f = new Fixture();
        Cleaner cleaner = new Cleaner(f.scenario.getPopulation());

        Leg leg = (Leg) f.scenario.getPopulation().getPersons().get(Id.createPersonId("3")).getPlans().get(0).getPlanElements().get(1);

        assertEquals(leg.getMode(), TransportMode.bike);
        assertEquals(leg.getRoute().getStartLinkId().toString(), "2");

        cleaner.clean(Arrays.asList(TransportMode.bike), Arrays.asList("cb"));
        assertEquals(leg.getMode(), TransportMode.bike);
        assertNotNull(leg.getRoute());

        cleaner.clean(Arrays.asList(TransportMode.pt), Arrays.asList("all"));
        assertEquals(leg.getMode(), TransportMode.bike);
        assertNotNull(leg.getRoute());

        cleaner.clean(Arrays.asList(TransportMode.pt, TransportMode.bike), Arrays.asList("all"));
        assertEquals(leg.getMode(), TransportMode.bike);
        assertNull(leg.getRoute());
    }

    @Test
    public void testBikeAgentWithAccessTimes() {
        Fixture f = new Fixture();
        Cleaner cleaner = new Cleaner(f.scenario.getPopulation());

        List<PlanElement> pe =  f.scenario.getPopulation().getPersons().get(Id.createPersonId("4")).getPlans().get(0).getPlanElements();
        assertEquals(7, pe.size(), 0);
        assertEquals(TransportMode.access_walk, ((Leg) pe.get(1)).getMode());

        cleaner.clean(Arrays.asList(TransportMode.pt), Arrays.asList("regular"));
        assertEquals(7, pe.size(), 0);
        assertEquals(TransportMode.access_walk, ((Leg) pe.get(1)).getMode());

        cleaner.clean(Arrays.asList(TransportMode.car), Arrays.asList("cb"));
        assertEquals(7, pe.size(), 0);
        assertEquals(TransportMode.access_walk, ((Leg) pe.get(1)).getMode());

        cleaner.clean(Arrays.asList(TransportMode.bike), Arrays.asList("regular"));
        assertEquals(3, pe.size(), 0);
        assertEquals(TransportMode.bike, ((Leg) pe.get(1)).getMode());
    }

    @Test
    public void testPTAgentFirstLegTransitWalk() {
        Fixture f = new Fixture();
        Cleaner cleaner = new Cleaner(f.scenario.getPopulation());

        Leg leg = (Leg) f.scenario.getPopulation().getPersons().get(Id.createPersonId("5")).getPlans().get(0).getPlanElements().get(1);

        assertEquals(leg.getMode(), TransportMode.transit_walk);
        assertEquals(7, f.scenario.getPopulation().getPersons().get(Id.createPersonId("5")).getPlans().get(0).getPlanElements().size(), 0);
        assertEquals(leg.getRoute().getStartLinkId().toString(), "2");

        cleaner.clean(Arrays.asList(TransportMode.pt), Arrays.asList("cb"));
        assertEquals(leg.getMode(), TransportMode.transit_walk);
        assertNotNull(leg.getRoute());

        cleaner.clean(Arrays.asList(TransportMode.car), Arrays.asList("regular"));
        assertEquals(leg.getMode(), TransportMode.transit_walk);
        assertNotNull(leg.getRoute());

        cleaner.clean(Arrays.asList(TransportMode.pt, TransportMode.bike), Arrays.asList("all"));
        assertEquals(leg.getMode(), TransportMode.pt);
        assertEquals(3, f.scenario.getPopulation().getPersons().get(Id.createPersonId("5")).getPlans().get(0).getPlanElements().size(), 0);
        assertNull(leg.getRoute());
    }

    @Test
    public void testPTAgentFirstLegAccessWalk() {
        Fixture f = new Fixture();
        Cleaner cleaner = new Cleaner(f.scenario.getPopulation());

        List<PlanElement> pe =  f.scenario.getPopulation().getPersons().get(Id.createPersonId("6")).getPlans().get(0).getPlanElements();
        assertEquals(7, pe.size(), 0);
        assertEquals(TransportMode.access_walk, ((Leg) pe.get(1)).getMode());

        cleaner.clean(Arrays.asList(TransportMode.car), Arrays.asList("regular"));
        assertEquals(7, pe.size(), 0);
        assertEquals(TransportMode.access_walk, ((Leg) pe.get(1)).getMode());

        cleaner.clean(Arrays.asList(TransportMode.pt), Arrays.asList("cb"));
        assertEquals(7, pe.size(), 0);
        assertEquals(TransportMode.access_walk, ((Leg) pe.get(1)).getMode());

        cleaner.clean(Arrays.asList(TransportMode.pt), Arrays.asList("regular"));
        assertEquals(3, pe.size(), 0);
        assertEquals(TransportMode.pt, ((Leg) pe.get(1)).getMode());
    }

    @Test
    public void testPTAgentTransitWalkOnly() {
        Fixture f = new Fixture();
        Cleaner cleaner = new Cleaner(f.scenario.getPopulation());

        Leg leg = (Leg) f.scenario.getPopulation().getPersons().get(Id.createPersonId("7")).getPlans().get(0).getPlanElements().get(1);

        assertEquals(leg.getMode(), TransportMode.transit_walk);
        assertEquals(3, f.scenario.getPopulation().getPersons().get(Id.createPersonId("7")).getPlans().get(0).getPlanElements().size(), 0);
        assertEquals(leg.getRoute().getStartLinkId().toString(), "2");

        cleaner.clean(Arrays.asList(TransportMode.pt), Arrays.asList("cb"));
        assertEquals(leg.getMode(), TransportMode.transit_walk);
        assertNotNull(leg.getRoute());

        cleaner.clean(Arrays.asList(TransportMode.car), Arrays.asList("freight"));
        assertEquals(leg.getMode(), TransportMode.transit_walk);
        assertNotNull(leg.getRoute());

        cleaner.clean(Arrays.asList(TransportMode.pt, TransportMode.bike), Arrays.asList("freight"));
        assertEquals(leg.getMode(), TransportMode.pt);
        assertEquals(3, f.scenario.getPopulation().getPersons().get(Id.createPersonId("7")).getPlans().get(0).getPlanElements().size(), 0);
        assertNull(leg.getRoute());
    }

    @Test
    public void testComplexPTTrip() {
        Fixture f = new Fixture();
        Cleaner cleaner = new Cleaner(f.scenario.getPopulation());

        Leg oldLeg = (Leg) f.scenario.getPopulation().getPersons().get(Id.createPersonId("8")).getPlans().get(0).getPlanElements().get(1);

        assertEquals(oldLeg.getMode(), TransportMode.access_walk);
        assertEquals(13, f.scenario.getPopulation().getPersons().get(Id.createPersonId("8")).getPlans().get(0).getPlanElements().size(), 0);
        assertEquals(oldLeg.getRoute().getStartLinkId().toString(), "2");

        Leg leg = (Leg) f.scenario.getPopulation().getPersons().get(Id.createPersonId("8")).getPlans().get(0).getPlanElements().get(3);
        assertEquals(leg.getMode(), TransportMode.pt);

        cleaner.clean(Arrays.asList(TransportMode.pt), Arrays.asList("cb"));
        assertEquals(leg.getMode(), TransportMode.pt);
        assertNotNull(leg.getRoute());

        cleaner.clean(Arrays.asList(TransportMode.car), Arrays.asList("regular"));
        assertEquals(leg.getMode(), TransportMode.pt);
        assertNotNull(leg.getRoute());

        cleaner.clean(Arrays.asList(TransportMode.pt, TransportMode.bike), Arrays.asList("regular"));
        assertEquals(leg.getMode(), TransportMode.pt);
        log.info(f.scenario.getPopulation().getPersons().get(Id.createPersonId("8")).getPlans().get(0).getPlanElements().get(1));

        Plan plan = f.scenario.getPopulation().getPersons().get(Id.createPersonId("8")).getPlans().get(0);

        assertEquals(3, f.scenario.getPopulation().getPersons().get(Id.createPersonId("8")).getPlans().get(0).getPlanElements().size(), 0);
        assertNull(oldLeg.getRoute());
    }

    private static class Fixture {
        public final Scenario scenario;

        public Fixture()  {
            Config config = ConfigUtils.createConfig();
            this.scenario = ScenarioUtils.createScenario(config);

            String plansXml =
                    "<?xml version=\"1.0\" ?>" +
                    "<!DOCTYPE population SYSTEM \"http://www.matsim.org/files/dtd/population_v6.dtd\">" +
                    "<population>" +

                    "<person id=\"1\">" +
                    "  <plan selected=\"yes\">" +
                    "     <activity type=\"h\" x=\"1000\" y=\"1000\" link=\"2\" max_dur=\"05:45\" end_time=\"05:45\" />" +
                    "     <leg mode=\"car\" dep_time=\"07:23:01\" trav_time=\"00:02:44\"> " +
                    "       <route type=\"links\" start_link=\"2\" end_link=\"3\" trav_time=\"00:01:02\" distance=\"598.249397023311\">2 3</route>" +
                    "     </leg>" +
                    "     <activity type=\"w\" x=\"10000\" y=\"0\" link=\"3\" start_time=\"06:00\" end_time=\"12:00\" />" +
                    "  </plan>" +
                    "  <plan selected=\"no\">" +
                    "     <activity type=\"h\" x=\"1000\" y=\"1000\" link=\"2\" max_dur=\"05:45\" end_time=\"05:45\" />" +
                    "     <leg mode=\"car\" dep_time=\"07:23:01\" trav_time=\"00:02:44\"> " +
                    "       <route type=\"links\" start_link=\"2\" end_link=\"3\" trav_time=\"00:01:02\" distance=\"598.249397023311\">2 3</route>" +
                    "     </leg>" +
                    "     <activity type=\"w\" x=\"10000\" y=\"0\" link=\"3\" start_time=\"06:00\" end_time=\"12:00\" />" +
                    "  </plan>" +
                    "</person>" +

                    "<person id=\"2\">" +
                    "  <plan>" +
                    "     <activity type=\"h\" x=\"1000\" y=\"1000\" link=\"2\"  max_dur=\"05:45\" end_time=\"05:45\" />" +
                    "     <leg mode=\"access_walk\" dep_time=\"07:22:31\" trav_time=\"00:00:30\">" +
                    "        <route type=\"generic\" start_link=\"2\" end_link=\"2\"></route>"+
                    "     </leg>" +
                    "        <activity type=\"car interaction\" link=\"2\" x=\"572118.1840014302\" y=\"225206.70029124807\" max_dur=\"00:00:00\" >" +
                    "        </activity>" +
                    "     <leg mode=\"car\" dep_time=\"07:23:01\" trav_time=\"00:02:44\">" +
                    "        <route type=\"links\" start_link=\"2\" end_link=\"3\">2 3</route>" +
                    "     </leg>" +
                    "        <activity type=\"car interaction\" link=\"2\" x=\"572118.1840014302\" y=\"225206.70029124807\" max_dur=\"00:00:00\" >" +
                    "        </activity>" +
                    "     <leg mode=\"egress_walk\" dep_time=\"07:22:31\" trav_time=\"00:00:30\">" +
                    "        <route type=\"generic\" start_link=\"3\" end_link=\"3\"></route>"+
                    "     </leg>" +
                    "     <activity type=\"w\" x=\"10000\" y=\"0\" link=\"3\" start_time=\"05:45\" end_time=\"12:00\" />" +
                    "  </plan>" +
                    "</person>" +

                    "<person id=\"3\">" +
                    "  <plan selected=\"yes\">" +
                    "     <activity type=\"h\" x=\"1000\" y=\"1000\" link=\"2\" max_dur=\"05:45\" end_time=\"05:45\" />" +
                    "     <leg mode=\"bike\" dep_time=\"07:23:01\" trav_time=\"00:02:44\"> " +
                    "       <route type=\"generic\" start_link=\"2\" end_link=\"3\" trav_time=\"00:08:49\" distance=\"1910.9354410863807\"></route>" +
                    "     </leg>" +
                    "     <activity type=\"w\" x=\"10000\" y=\"0\" link=\"3\" start_time=\"06:00\" end_time=\"12:00\" />" +
                    "  </plan>" +
                    "</person>" +

                    "<person id=\"4\">" +
                    "  <plan>" +
                    "     <activity type=\"h\" x=\"1000\" y=\"1000\" link=\"2\"  max_dur=\"05:45\" end_time=\"05:45\" />" +
                    "     <leg mode=\"access_walk\" dep_time=\"07:22:31\" trav_time=\"00:00:30\">" +
                    "        <route type=\"generic\" start_link=\"2\" end_link=\"2\"></route>"+
                    "     </leg>" +
                    "        <activity type=\"bike interaction\" link=\"2\" x=\"572118.1840014302\" y=\"225206.70029124807\" max_dur=\"00:00:00\" >" +
                    "        </activity>" +
                    "     <leg mode=\"bike\" dep_time=\"07:23:01\" trav_time=\"00:02:44\">" +
                    "        <route start_link=\"2\" end_link=\"3\">2 3</route>" +
                    "     </leg>" +
                    "        <activity type=\"bike interaction\" link=\"2\" x=\"572118.1840014302\" y=\"225206.70029124807\" max_dur=\"00:00:00\" >" +
                    "        </activity>" +
                    "     <leg mode=\"egress_walk\" dep_time=\"07:22:31\" trav_time=\"00:00:30\">" +
                    "        <route type=\"generic\" start_link=\"3\" end_link=\"3\"></route>"+
                    "     </leg>" +
                    "     <activity type=\"w\" x=\"10000\" y=\"0\" link=\"3\" start_time=\"05:45\" end_time=\"12:00\" />" +
                    "  </plan>" +
                    "</person>" +

                    "<person id=\"5\">" +
                    "  <plan>" +
                    "     <activity type=\"h\" x=\"1000\" y=\"1000\" link=\"2\"  max_dur=\"05:45\" end_time=\"05:45\" />" +
                    "     <leg mode=\"transit_walk\" dep_time=\"07:22:31\" trav_time=\"00:00:30\">" +
                    "        <route type=\"generic\" start_link=\"2\" end_link=\"2\"></route>"+
                    "     </leg>" +
                    "        <activity type=\"pt interaction\" link=\"2\" x=\"572118.1840014302\" y=\"225206.70029124807\" max_dur=\"00:00:00\" >" +
                    "        </activity>" +
                    "     <leg mode=\"pt\" dep_time=\"07:23:01\" trav_time=\"00:02:44\">" +
                    "        <route type=\"experimentalPt1\" start_link=\"pt_485797221\" end_link=\"pt_485718011\" trav_time=\"00:28:01\" distance=\"8308.056653632644\">PT1===485797221===360_000792===24086_1_15===485718011</route>" +
                    "     </leg>" +
                    "        <activity type=\"pt interaction\" link=\"2\" x=\"572118.1840014302\" y=\"225206.70029124807\" max_dur=\"00:00:00\" >" +
                    "        </activity>" +
                    "     <leg mode=\"transit_walk\" dep_time=\"07:22:31\" trav_time=\"00:00:30\">" +
                    "        <route type=\"generic\" start_link=\"3\" end_link=\"3\"></route>"+
                    "     </leg>" +
                    "     <activity type=\"w\" x=\"10000\" y=\"0\" link=\"3\" start_time=\"05:45\" end_time=\"12:00\" />" +
                    "  </plan>" +
                    "</person>" +

                    "<person id=\"6\">" +
                    "  <plan>" +
                    "     <activity type=\"h\" x=\"1000\" y=\"1000\" link=\"2\"  max_dur=\"05:45\" end_time=\"05:45\" />" +
                    "     <leg mode=\"access_walk\" dep_time=\"07:22:31\" trav_time=\"00:00:30\">" +
                    "        <route type=\"generic\" start_link=\"2\" end_link=\"2\"></route>"+
                    "     </leg>" +
                    "        <activity type=\"pt interaction\" link=\"2\" x=\"572118.1840014302\" y=\"225206.70029124807\" max_dur=\"00:00:00\" >" +
                    "        </activity>" +
                    "     <leg mode=\"pt\" dep_time=\"07:23:01\" trav_time=\"00:02:44\">" +
                    "        <route type=\"experimentalPt1\" start_link=\"pt_485797221\" end_link=\"pt_485718011\" trav_time=\"00:28:01\" distance=\"8308.056653632644\">PT1===485797221===360_000792===24086_1_15===485718011</route>" +
                    "     </leg>" +
                    "        <activity type=\"pt interaction\" link=\"2\" x=\"572118.1840014302\" y=\"225206.70029124807\" max_dur=\"00:00:00\" >" +
                    "        </activity>" +
                    "     <leg mode=\"egress_walk\" dep_time=\"07:22:31\" trav_time=\"00:00:30\">" +
                    "        <route type=\"generic\" start_link=\"3\" end_link=\"3\"></route>"+
                    "     </leg>" +
                    "     <activity type=\"w\" x=\"10000\" y=\"0\" link=\"3\" start_time=\"05:45\" end_time=\"12:00\" />" +
                    "  </plan>" +
                    "</person>" +

                    "<person id=\"7\">" +
                    "  <plan>" +
                    "     <activity type=\"h\" x=\"1000\" y=\"1000\" link=\"2\"  max_dur=\"05:45\" end_time=\"05:45\" />" +
                    "     <leg mode=\"transit_walk\" dep_time=\"07:22:31\" trav_time=\"00:00:30\">" +
                    "        <route type=\"generic\" start_link=\"2\" end_link=\"3\"></route>"+
                    "     </leg>" +
                    "     <activity type=\"w\" x=\"10000\" y=\"0\" link=\"3\" start_time=\"05:45\" end_time=\"12:00\" />" +
                    "  </plan>" +
                    "</person>" +

                    "<person id=\"8\">" +
                    "  <plan>" +
                    "     <activity type=\"h\" x=\"1000\" y=\"1000\" link=\"2\"  max_dur=\"05:45\" end_time=\"05:45\" />" +
                    "     <leg mode=\"access_walk\" dep_time=\"07:22:31\" trav_time=\"00:00:30\">" +
                    "        <route type=\"generic\" start_link=\"2\" end_link=\"2\"></route>"+
                    "     </leg>" +
                    "        <activity type=\"pt interaction\" link=\"2\" x=\"572118.1840014302\" y=\"225206.70029124807\" max_dur=\"00:00:00\" >" +
                    "        </activity>" +
                    "     <leg mode=\"pt\" dep_time=\"07:23:01\" trav_time=\"00:02:44\">" +
                    "        <route type=\"experimentalPt1\" start_link=\"pt_485797221\" end_link=\"pt_485718011\" trav_time=\"00:28:01\" distance=\"8308.056653632644\">PT1===485797221===360_000792===24086_1_15===485718011</route>" +
                    "     </leg>" +
                    "        <activity type=\"pt interaction\" link=\"2\" x=\"572118.1840014302\" y=\"225206.70029124807\" max_dur=\"00:00:00\" >" +
                    "        </activity>" +
                    "     <leg mode=\"transit_walk\" dep_time=\"07:22:31\" trav_time=\"00:00:30\">" +
                    "        <route type=\"generic\" start_link=\"2\" end_link=\"3\"></route>"+
                    "     </leg>" +
                    "        <activity type=\"pt interaction\" link=\"2\" x=\"572118.1840014302\" y=\"225206.70029124807\" max_dur=\"00:00:00\" >" +
                    "        </activity>" +
                    "     <leg mode=\"pt\" dep_time=\"07:23:01\" trav_time=\"00:02:44\">" +
                    "        <route type=\"experimentalPt1\" start_link=\"pt_485797221\" end_link=\"pt_485718011\" trav_time=\"00:28:01\" distance=\"8308.056653632644\">PT1===485797221===360_000792===24086_1_15===485718011</route>" +
                    "     </leg>" +
                    "        <activity type=\"pt interaction\" link=\"2\" x=\"572118.1840014302\" y=\"225206.70029124807\" max_dur=\"00:00:00\" >" +
                    "        </activity>" +
                    "     <leg mode=\"pt\" dep_time=\"07:23:01\" trav_time=\"00:02:44\">" +
                    "        <route type=\"experimentalPt1\" start_link=\"pt_485797221\" end_link=\"pt_485718011\" trav_time=\"00:28:01\" distance=\"8308.056653632644\">PT1===485797221===360_000792===24086_1_15===485718011</route>" +
                    "     </leg>" +
                    "        <activity type=\"pt interaction\" link=\"2\" x=\"572118.1840014302\" y=\"225206.70029124807\" max_dur=\"00:00:00\" >" +
                    "        </activity>" +
                    "     <leg mode=\"egress_walk\" dep_time=\"07:22:31\" trav_time=\"00:00:30\">" +
                    "        <route type=\"generic\" start_link=\"3\" end_link=\"3\"></route>"+
                    "     </leg>" +
                    "     <activity type=\"w\" x=\"10000\" y=\"0\" link=\"3\" start_time=\"05:45\" end_time=\"12:00\" />" +
                    "  </plan>" +
                    "</person>" +

                    "</population>";

            new PopulationReader(scenario).parse(new ByteArrayInputStream(plansXml.getBytes()));

            scenario.getPopulation().getPersonAttributes().putAttribute("1", "subpopulation", "cb");
            scenario.getPopulation().getPersonAttributes().putAttribute("2", "subpopulation", "regular");
            scenario.getPopulation().getPersonAttributes().putAttribute("3", "subpopulation", "regular");
            scenario.getPopulation().getPersonAttributes().putAttribute("4", "subpopulation", "regular");
            // no attribute for person 5 -> "all" works
            scenario.getPopulation().getPersonAttributes().putAttribute("6", "subpopulation", "regular");
            scenario.getPopulation().getPersonAttributes().putAttribute("7", "subpopulation", "freight");
            scenario.getPopulation().getPersonAttributes().putAttribute("8", "subpopulation", "regular");
        }
    }
}