package ch.sbb.matsim.analysis.modalsplitstatstest;

import ch.sbb.matsim.RunSBB;
import ch.sbb.matsim.analysis.modalsplit.MSVariables;
import ch.sbb.matsim.analysis.modalsplit.ModalSplitStats;
import ch.sbb.matsim.config.PostProcessingConfigGroup;
import ch.sbb.matsim.config.variables.SBBModes;
import ch.sbb.matsim.config.variables.Variables;
import ch.sbb.matsim.zones.Zones;
import ch.sbb.matsim.zones.ZonesCollection;
import ch.sbb.matsim.zones.ZonesLoader;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.List;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.matsim.api.core.v01.DefaultActivityTypes;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.IdMap;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.population.Activity;
import org.matsim.api.core.v01.population.Leg;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.Plan;
import org.matsim.api.core.v01.population.Population;
import org.matsim.api.core.v01.population.PopulationFactory;
import org.matsim.api.core.v01.population.Route;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.network.io.MatsimNetworkReader;
import org.matsim.core.population.io.PopulationReader;
import org.matsim.core.population.routes.RouteUtils;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.facilities.ActivityFacility;
import org.matsim.pt.transitSchedule.api.TransitScheduleReader;

public class ModalSplitStatsTest {

    private ModalSplitStats modalSplitStats;
    private PopulationFactory populationFactory;
    private Population population;
    private String output;

    @Before
    public void init() {
        System.setProperty("matsim.preferLocalDtds", "true");
        Config config = ConfigUtils.createConfig();
        Scenario scenario = ScenarioUtils.createScenario(config);

        config.qsim().setEndTime(30 * 3600);
        config.controler().setRunId("ModalSplitStatsTest");

        new PopulationReader(scenario).readFile("test/input/scenarios/mobi31test/output_plans.xml");
        new TransitScheduleReader(scenario).readFile("test/input/scenarios/mobi31test/transitSchedule.xml.gz");
        new MatsimNetworkReader(scenario.getNetwork()).readFile("test/input/scenarios/mobi31test/network.xml.gz");

        Zones zones = ZonesLoader.loadZones("zones", "test/input/scenarios/mobi31test/zones/andermatt-zones.shp", "zone_id");
        ZonesCollection zonesCollection = new ZonesCollection();
        zonesCollection.addZones(zones);

        PostProcessingConfigGroup ppConfig = ConfigUtils.addOrGetModule(config, PostProcessingConfigGroup.class);
        ppConfig.setSimulationSampleSize(1);
        ppConfig.setZonesId("zones");

        this.populationFactory = scenario.getPopulation().getFactory();
        this.population = scenario.getPopulation();
        this.output = RunSBB.buildConfig("test/input/scenarios/mobi31test/config.xml").controler().getOutputDirectory();

        this.modalSplitStats = new ModalSplitStats(zonesCollection, config, scenario);
    }


    @Test
    public void skipHomeOfficeTest() {

        IdMap<Person, Plan> experiencedPlans = new IdMap<>(Person.class, 1);

        Person person = populationFactory.createPerson(Id.createPersonId("mobi-test1"));
        Plan plan = populationFactory.createPlan();

        Activity startActivity = populationFactory.createActivityFromActivityFacilityId(DefaultActivityTypes.home, Id.create(1, ActivityFacility.class));
        startActivity.setEndTime(8 * 3600);

        Activity endActivity = populationFactory.createActivityFromActivityFacilityId(DefaultActivityTypes.work, Id.create(1, ActivityFacility.class));
        endActivity.setStartTime(9 * 3600);

        Leg leg = populationFactory.createLeg(SBBModes.WALK_MAIN_MAINMODE);
        Route route = RouteUtils.createGenericRouteImpl(Id.createLinkId(1), Id.createLinkId(2));
        route.setDistance(10000);
        leg.setRoute(route);

        plan.addActivity(startActivity);
        plan.addLeg(leg);
        plan.addActivity(endActivity);

        experiencedPlans.put(person.getId(), plan);

        modalSplitStats.analyzeAndWriteStats(output, experiencedPlans);

        try (BufferedReader reader = new BufferedReader(new FileReader(output + "SBB_modal_split_PF.csv"))) {

            List<String> header = List.of(reader.readLine().split(";"));
            String line;
            while ((line = reader.readLine()) != null) {
                Assert.assertEquals(0, Long.parseLong(line.split(";")[header.indexOf("all")]));
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    public void distanceClassesTest() {
        IdMap<Person, Plan> experiencedPlans = new IdMap<>(Person.class, 1);

        Person person = populationFactory.createPerson(Id.createPersonId("mobi-test1"));
        Plan plan = populationFactory.createPlan();

        Activity activity1 = populationFactory.createActivityFromActivityFacilityId(DefaultActivityTypes.home, Id.create(1, ActivityFacility.class));
        activity1.setEndTime(8 * 3600);

        Activity activity2 = populationFactory.createActivityFromActivityFacilityId(DefaultActivityTypes.work, Id.create(2, ActivityFacility.class));
        activity2.setStartTime(9 * 3600);
        activity2.setEndTime(10 * 3600);

        Activity activity3 = populationFactory.createActivityFromActivityFacilityId(DefaultActivityTypes.work, Id.create(3, ActivityFacility.class));
        activity3.setStartTime(11 * 3600);
        activity3.setEndTime(12 * 3600);

        Activity activity4 = populationFactory.createActivityFromActivityFacilityId(DefaultActivityTypes.work, Id.create(4, ActivityFacility.class));
        activity4.setStartTime(13 * 3600);
        activity4.setEndTime(14 * 3600);

        Leg leg1 = populationFactory.createLeg(SBBModes.WALK_MAIN_MAINMODE);
        Route route1 = RouteUtils.createGenericRouteImpl(Id.createLinkId(1), Id.createLinkId(2));
        route1.setDistance(1500);
        leg1.setRoute(route1);

        Leg leg2 = populationFactory.createLeg(SBBModes.WALK_MAIN_MAINMODE);
        Route route2 = RouteUtils.createGenericRouteImpl(Id.createLinkId(1), Id.createLinkId(2));
        route2.setDistance(1500);
        leg2.setRoute(route2);

        Leg leg3 = populationFactory.createLeg(SBBModes.CAR);
        Route route3 = RouteUtils.createGenericRouteImpl(Id.createLinkId(1), Id.createLinkId(2));
        route3.setDistance(2500);
        leg3.setRoute(route3);

        plan.addActivity(activity1);
        plan.addLeg(leg1);
        plan.addActivity(activity2);
        plan.addLeg(leg2);
        plan.addActivity(activity3);
        plan.addLeg(leg3);
        plan.addActivity(activity4);

        experiencedPlans.put(person.getId(), plan);

        modalSplitStats.analyzeAndWriteStats(output, experiencedPlans);

        try (BufferedReader reader = new BufferedReader(new FileReader(output + "SBB_distance_classes.csv"))) {

            List<String> header = List.of(reader.readLine().split(";"));
            String line;
            while ((line = reader.readLine()) != null) {
                var tmpLine = line.split(";");

                if (tmpLine[header.indexOf(MSVariables.subpopulation)].equals(Variables.REGULAR)) {
                    if (tmpLine[header.indexOf(MSVariables.mode)].equals(SBBModes.CAR)) {
                        Assert.assertEquals(0, Long.parseLong(tmpLine[header.indexOf(MSVariables.distanceClassesLable.get(2))]));
                    } else if (tmpLine[header.indexOf(MSVariables.mode)].equals(SBBModes.WALK_FOR_ANALYSIS)) {
                        Assert.assertEquals(0, Long.parseLong(tmpLine[header.indexOf(MSVariables.distanceClassesLable.get(1))]));
                    } else {
                        Assert.assertEquals(0, Long.parseLong(tmpLine[header.indexOf(MSVariables.distanceClassesLable.get(3))]));
                    }
                }
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    public void middleTimeDistribution() {
        IdMap<Person, Plan> experiencedPlans = new IdMap<>(Person.class, 1);

        Person person = populationFactory.createPerson(Id.createPersonId("mobi-test1"));
        Plan plan = populationFactory.createPlan();

        Activity activity1 = populationFactory.createActivityFromActivityFacilityId(DefaultActivityTypes.home, Id.create(1, ActivityFacility.class));
        activity1.setEndTime(8.2 * 3600);

        Activity activity2 = populationFactory.createActivityFromActivityFacilityId(DefaultActivityTypes.work, Id.create(2, ActivityFacility.class));
        activity2.setStartTime(9.2 * 3600);
        activity2.setEndTime(10.2 * 3600);

        Activity activity3 = populationFactory.createActivityFromActivityFacilityId(DefaultActivityTypes.work, Id.create(3, ActivityFacility.class));
        activity3.setStartTime(11.2 * 3600);
        activity3.setEndTime(12.2 * 3600);

        Activity activity4 = populationFactory.createActivityFromActivityFacilityId(DefaultActivityTypes.work, Id.create(4, ActivityFacility.class));
        activity4.setStartTime(13.2 * 3600);
        activity4.setEndTime(14.2 * 3600);

        Leg leg1 = populationFactory.createLeg(SBBModes.WALK_MAIN_MAINMODE);
        Route route1 = RouteUtils.createGenericRouteImpl(Id.createLinkId(1), Id.createLinkId(2));
        route1.setDistance(1500);
        leg1.setRoute(route1);

        Leg leg2 = populationFactory.createLeg(SBBModes.WALK_MAIN_MAINMODE);
        Route route2 = RouteUtils.createGenericRouteImpl(Id.createLinkId(1), Id.createLinkId(2));
        route2.setDistance(1500);
        leg2.setRoute(route2);

        Leg leg3 = populationFactory.createLeg(SBBModes.CAR);
        Route route3 = RouteUtils.createGenericRouteImpl(Id.createLinkId(1), Id.createLinkId(2));
        route3.setDistance(2500);
        leg3.setRoute(route3);

        plan.addActivity(activity1);
        plan.addLeg(leg1);
        plan.addActivity(activity2);
        plan.addLeg(leg2);
        plan.addActivity(activity3);
        plan.addLeg(leg3);
        plan.addActivity(activity4);

        experiencedPlans.put(person.getId(), plan);

        modalSplitStats.analyzeAndWriteStats(output, experiencedPlans);

        try (BufferedReader reader = new BufferedReader(new FileReader(output + "SBB_middle_time_distribution.csv"))) {

            List<String> header = List.of(reader.readLine().split(";"));
            String line;
            while ((line = reader.readLine()) != null) {
                var tmpLine = line.split(";");
                if (tmpLine[header.indexOf(MSVariables.subpopulation)].equals(Variables.REGULAR)) {
                    if (Integer.parseInt(tmpLine[header.indexOf("time")]) == (int) ((activity1.getEndTime().seconds() + activity2.getStartTime().seconds())/2) - (((activity1.getEndTime().seconds() + activity2.getStartTime().seconds())/2) % MSVariables.timeSplit)) {
                        Assert.assertEquals(1, Long.parseLong(tmpLine[header.indexOf(MSVariables.all)]));
                        Assert.assertEquals(1, Long.parseLong(tmpLine[header.indexOf(MSVariables.walk)]));
                        Assert.assertEquals(1, Long.parseLong(tmpLine[header.indexOf(MSVariables.work)]));
                    } else if (Integer.parseInt(tmpLine[header.indexOf("time")]) == (int) ((activity2.getEndTime().seconds() + activity3.getStartTime().seconds())/2) - (((activity1.getEndTime().seconds() + activity2.getStartTime().seconds())/2) % MSVariables.timeSplit)) {
                        Assert.assertEquals(1, Long.parseLong(tmpLine[header.indexOf(MSVariables.all)]));
                        Assert.assertEquals(1, Long.parseLong(tmpLine[header.indexOf(MSVariables.walk)]));
                        Assert.assertEquals(1, Long.parseLong(tmpLine[header.indexOf(MSVariables.work)]));
                    } else if (Integer.parseInt(tmpLine[header.indexOf("time")]) == (int) ((activity3.getEndTime().seconds() + activity4.getStartTime().seconds())/2) - (((activity1.getEndTime().seconds() + activity2.getStartTime().seconds())/2)% MSVariables.timeSplit)) {
                        Assert.assertEquals(1, Long.parseLong(tmpLine[header.indexOf(MSVariables.all)]));
                        Assert.assertEquals(1, Long.parseLong(tmpLine[header.indexOf(MSVariables.car)]));
                        Assert.assertEquals(1, Long.parseLong(tmpLine[header.indexOf(MSVariables.work)]));
                    } else {
                        Assert.assertEquals(0, Long.parseLong(tmpLine[header.indexOf(MSVariables.all)]));
                    }
                }
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    public void travelTimeDistribution() {
        IdMap<Person, Plan> experiencedPlans = new IdMap<>(Person.class, 1);

        Person person = populationFactory.createPerson(Id.createPersonId("mobi-test1"));
        Plan plan = populationFactory.createPlan();

        Activity activity1 = populationFactory.createActivityFromActivityFacilityId(DefaultActivityTypes.home, Id.create(1, ActivityFacility.class));
        activity1.setEndTime(8 * 3600);

        Activity activity2 = populationFactory.createActivityFromActivityFacilityId(DefaultActivityTypes.work, Id.create(2, ActivityFacility.class));
        activity2.setStartTime(9 * 3600);
        activity2.setEndTime(10 * 3600);

        Activity activity3 = populationFactory.createActivityFromActivityFacilityId(DefaultActivityTypes.work, Id.create(3, ActivityFacility.class));
        activity3.setStartTime(12 * 3600);
        activity3.setEndTime(13 * 3600);

        Activity activity4 = populationFactory.createActivityFromActivityFacilityId(DefaultActivityTypes.work, Id.create(4, ActivityFacility.class));
        activity4.setStartTime(17 * 3600);
        activity4.setEndTime(20 * 3600);

        Leg leg1 = populationFactory.createLeg(SBBModes.WALK_MAIN_MAINMODE);
        Route route1 = RouteUtils.createGenericRouteImpl(Id.createLinkId(1), Id.createLinkId(2));
        route1.setDistance(1500);
        leg1.setRoute(route1);

        Leg leg2 = populationFactory.createLeg(SBBModes.WALK_MAIN_MAINMODE);
        Route route2 = RouteUtils.createGenericRouteImpl(Id.createLinkId(1), Id.createLinkId(2));
        route2.setDistance(1500);
        leg2.setRoute(route2);

        Leg leg3 = populationFactory.createLeg(SBBModes.CAR);
        Route route3 = RouteUtils.createGenericRouteImpl(Id.createLinkId(1), Id.createLinkId(2));
        route3.setDistance(2500);
        leg3.setRoute(route3);

        plan.addActivity(activity1);
        plan.addLeg(leg1);
        plan.addActivity(activity2);
        plan.addLeg(leg2);
        plan.addActivity(activity3);
        plan.addLeg(leg3);
        plan.addActivity(activity4);

        experiencedPlans.put(person.getId(), plan);

        modalSplitStats.analyzeAndWriteStats(output, experiencedPlans);

        try (BufferedReader reader = new BufferedReader(new FileReader(output + "SBB_travel_time_distribution.csv"))) {

            List<String> header = List.of(reader.readLine().split(";"));
            String line;
            while ((line = reader.readLine()) != null) {
                var tmpLine = line.split(";");
                if (tmpLine[header.indexOf(MSVariables.subpopulation)].equals(Variables.REGULAR)) {
                    if (tmpLine[header.indexOf("time")].equals(">18000")) {
                        continue;
                    }
                    if (Integer.parseInt(tmpLine[header.indexOf("time")]) == (int) activity2.getStartTime().seconds() - activity1.getEndTime().seconds()) {
                        Assert.assertEquals(1, Long.parseLong(tmpLine[header.indexOf(MSVariables.all)]));
                        Assert.assertEquals(1, Long.parseLong(tmpLine[header.indexOf(MSVariables.walk)]));
                        Assert.assertEquals(1, Long.parseLong(tmpLine[header.indexOf(MSVariables.work)]));
                    } else if (Integer.parseInt(tmpLine[header.indexOf("time")]) == (int) activity3.getStartTime().seconds() - activity2.getEndTime().seconds()) {
                        Assert.assertEquals(1, Long.parseLong(tmpLine[header.indexOf(MSVariables.all)]));
                        Assert.assertEquals(1, Long.parseLong(tmpLine[header.indexOf(MSVariables.walk)]));
                        Assert.assertEquals(1, Long.parseLong(tmpLine[header.indexOf(MSVariables.work)]));
                    } else if (Integer.parseInt(tmpLine[header.indexOf("time")]) == (int) activity4.getStartTime().seconds() - activity3.getEndTime().seconds()) {
                        Assert.assertEquals(1, Long.parseLong(tmpLine[header.indexOf(MSVariables.all)]));
                        Assert.assertEquals(1, Long.parseLong(tmpLine[header.indexOf(MSVariables.car)]));
                        Assert.assertEquals(1, Long.parseLong(tmpLine[header.indexOf(MSVariables.work)]));
                    } else {
                        Assert.assertEquals(0, Long.parseLong(tmpLine[header.indexOf(MSVariables.all)]));
                    }
                }
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    public void modalSplitPFTest() {
        IdMap<Person, Plan> experiencedPlans = new IdMap<>(Person.class, 1);

        Person person = populationFactory.createPerson(Id.createPersonId("mobi-test1"));
        Plan plan = populationFactory.createPlan();

        Activity activity1 = populationFactory.createActivityFromActivityFacilityId(DefaultActivityTypes.home, Id.create(1, ActivityFacility.class));
        activity1.setEndTime(8 * 3600);

        Activity activity2 = populationFactory.createActivityFromActivityFacilityId(DefaultActivityTypes.work, Id.create(2, ActivityFacility.class));
        activity2.setStartTime(9 * 3600);
        activity2.setEndTime(10 * 3600);

        Activity activity3 = populationFactory.createActivityFromActivityFacilityId(DefaultActivityTypes.work, Id.create(3, ActivityFacility.class));
        activity3.setStartTime(11 * 3600);
        activity3.setEndTime(12 * 3600);

        Activity activity4 = populationFactory.createActivityFromActivityFacilityId(DefaultActivityTypes.work, Id.create(4, ActivityFacility.class));
        activity4.setStartTime(13 * 3600);
        activity4.setEndTime(14 * 3600);

        Leg leg1 = populationFactory.createLeg(SBBModes.BIKE);
        Route route1 = RouteUtils.createGenericRouteImpl(Id.createLinkId(1), Id.createLinkId(2));
        route1.setDistance(1500);
        leg1.setRoute(route1);

        Leg leg2 = populationFactory.createLeg(SBBModes.BIKE);
        Route route2 = RouteUtils.createGenericRouteImpl(Id.createLinkId(1), Id.createLinkId(2));
        route2.setDistance(1500);
        leg2.setRoute(route2);

        Leg leg3 = populationFactory.createLeg(SBBModes.RIDE);
        Route route3 = RouteUtils.createGenericRouteImpl(Id.createLinkId(1), Id.createLinkId(2));
        route3.setDistance(2500);
        leg3.setRoute(route3);

        plan.addActivity(activity1);
        plan.addLeg(leg1);
        plan.addActivity(activity2);
        plan.addLeg(leg2);
        plan.addActivity(activity3);
        plan.addLeg(leg3);
        plan.addActivity(activity4);

        experiencedPlans.put(person.getId(), plan);

        modalSplitStats.analyzeAndWriteStats(output, experiencedPlans);

        try (BufferedReader reader = new BufferedReader(new FileReader(output + "SBB_modal_split_PF.csv"))) {

            List<String> header = List.of(reader.readLine().split(";"));
            String line;
            while ((line = reader.readLine()) != null) {
                var tmpLine = line.split(";");

                if (tmpLine[header.indexOf(MSVariables.subpopulation)].equals(Variables.REGULAR)) {
                    if (tmpLine[header.indexOf(MSVariables.mode)].equals(SBBModes.RIDE)) {
                        Assert.assertEquals(1, Long.parseLong(tmpLine[header.indexOf(MSVariables.all)]));
                        Assert.assertEquals(1, Long.parseLong(tmpLine[header.indexOf(MSVariables.carAvailableFalse)]));
                        Assert.assertEquals(1, Long.parseLong(tmpLine[header.indexOf(MSVariables.ptSubNone)]));
                        Assert.assertEquals(1, Long.parseLong(tmpLine[header.indexOf(MSVariables.nocarNone)]));
                        Assert.assertEquals(1, Long.parseLong(tmpLine[header.indexOf(MSVariables.secondary)]));
                        Assert.assertEquals(1, Long.parseLong(tmpLine[header.indexOf(MSVariables.employment0)]));
                        Assert.assertEquals(1, Long.parseLong(tmpLine[header.indexOf(MSVariables.ageCat17)]));
                        Assert.assertEquals(1, Long.parseLong(tmpLine[header.indexOf(MSVariables.work)]));
                    } else if (tmpLine[header.indexOf(MSVariables.mode)].equals(SBBModes.BIKE)) {
                        Assert.assertEquals(2, Long.parseLong(tmpLine[header.indexOf(MSVariables.all)]));
                        Assert.assertEquals(2, Long.parseLong(tmpLine[header.indexOf(MSVariables.carAvailableFalse)]));
                        Assert.assertEquals(2, Long.parseLong(tmpLine[header.indexOf(MSVariables.ptSubNone)]));
                        Assert.assertEquals(2, Long.parseLong(tmpLine[header.indexOf(MSVariables.nocarNone)]));
                        Assert.assertEquals(2, Long.parseLong(tmpLine[header.indexOf(MSVariables.secondary)]));
                        Assert.assertEquals(2, Long.parseLong(tmpLine[header.indexOf(MSVariables.employment0)]));
                        Assert.assertEquals(2, Long.parseLong(tmpLine[header.indexOf(MSVariables.ageCat17)]));
                        Assert.assertEquals(2, Long.parseLong(tmpLine[header.indexOf(MSVariables.work)]));
                    } else {
                        Assert.assertEquals(0, Long.parseLong(tmpLine[header.indexOf(MSVariables.all)]));
                        Assert.assertEquals(0, Long.parseLong(tmpLine[header.indexOf(MSVariables.carAvailableFalse)]));
                        Assert.assertEquals(0, Long.parseLong(tmpLine[header.indexOf(MSVariables.ptSubNone)]));
                        Assert.assertEquals(0, Long.parseLong(tmpLine[header.indexOf(MSVariables.nocarNone)]));
                        Assert.assertEquals(0, Long.parseLong(tmpLine[header.indexOf(MSVariables.secondary)]));
                        Assert.assertEquals(0, Long.parseLong(tmpLine[header.indexOf(MSVariables.employment0)]));
                        Assert.assertEquals(0, Long.parseLong(tmpLine[header.indexOf(MSVariables.ageCat17)]));
                        Assert.assertEquals(0, Long.parseLong(tmpLine[header.indexOf(MSVariables.work)]));
                    }
                }
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    public void modalSplitPKMTest() {
        IdMap<Person, Plan> experiencedPlans = new IdMap<>(Person.class, 1);

        Person person = populationFactory.createPerson(Id.createPersonId("mobi-test1"));
        Plan plan = populationFactory.createPlan();

        Activity activity1 = populationFactory.createActivityFromActivityFacilityId(DefaultActivityTypes.home, Id.create(1, ActivityFacility.class));
        activity1.setEndTime(8 * 3600);

        Activity activity2 = populationFactory.createActivityFromActivityFacilityId(DefaultActivityTypes.work, Id.create(2, ActivityFacility.class));
        activity2.setStartTime(9 * 3600);
        activity2.setEndTime(10 * 3600);

        Activity activity3 = populationFactory.createActivityFromActivityFacilityId(DefaultActivityTypes.work, Id.create(3, ActivityFacility.class));
        activity3.setStartTime(11 * 3600);
        activity3.setEndTime(12 * 3600);

        Activity activity4 = populationFactory.createActivityFromActivityFacilityId(DefaultActivityTypes.work, Id.create(4, ActivityFacility.class));
        activity4.setStartTime(13 * 3600);
        activity4.setEndTime(14 * 3600);

        Leg leg1 = populationFactory.createLeg(SBBModes.BIKE);
        Route route1 = RouteUtils.createGenericRouteImpl(Id.createLinkId(1), Id.createLinkId(2));
        route1.setDistance(2000);
        leg1.setRoute(route1);

        Leg leg2 = populationFactory.createLeg(SBBModes.BIKE);
        Route route2 = RouteUtils.createGenericRouteImpl(Id.createLinkId(1), Id.createLinkId(2));
        route2.setDistance(2000);
        leg2.setRoute(route2);

        Leg leg3 = populationFactory.createLeg(SBBModes.RIDE);
        Route route3 = RouteUtils.createGenericRouteImpl(Id.createLinkId(1), Id.createLinkId(2));
        route3.setDistance(3000);
        leg3.setRoute(route3);

        plan.addActivity(activity1);
        plan.addLeg(leg1);
        plan.addActivity(activity2);
        plan.addLeg(leg2);
        plan.addActivity(activity3);
        plan.addLeg(leg3);
        plan.addActivity(activity4);

        experiencedPlans.put(person.getId(), plan);

        modalSplitStats.analyzeAndWriteStats(output, experiencedPlans);

        try (BufferedReader reader = new BufferedReader(new FileReader(output + "SBB_modal_split_PKM.csv"))) {

            List<String> header = List.of(reader.readLine().split(";"));
            String line;
            while ((line = reader.readLine()) != null) {
                var tmpLine = line.split(";");

                if (tmpLine[header.indexOf(MSVariables.subpopulation)].equals(Variables.REGULAR)) {
                    if (tmpLine[header.indexOf(MSVariables.mode)].equals(SBBModes.RIDE)) {
                        Assert.assertEquals(3, Long.parseLong(tmpLine[header.indexOf(MSVariables.all)]));
                        Assert.assertEquals(3, Long.parseLong(tmpLine[header.indexOf(MSVariables.carAvailableFalse)]));
                        Assert.assertEquals(3, Long.parseLong(tmpLine[header.indexOf(MSVariables.ptSubNone)]));
                        Assert.assertEquals(3, Long.parseLong(tmpLine[header.indexOf(MSVariables.nocarNone)]));
                        Assert.assertEquals(3, Long.parseLong(tmpLine[header.indexOf(MSVariables.secondary)]));
                        Assert.assertEquals(3, Long.parseLong(tmpLine[header.indexOf(MSVariables.employment0)]));
                        Assert.assertEquals(3, Long.parseLong(tmpLine[header.indexOf(MSVariables.ageCat17)]));
                        Assert.assertEquals(3, Long.parseLong(tmpLine[header.indexOf(MSVariables.work)]));
                    } else if (tmpLine[header.indexOf(MSVariables.mode)].equals(SBBModes.BIKE)) {
                        Assert.assertEquals(4, Long.parseLong(tmpLine[header.indexOf(MSVariables.all)]));
                        Assert.assertEquals(4, Long.parseLong(tmpLine[header.indexOf(MSVariables.carAvailableFalse)]));
                        Assert.assertEquals(4, Long.parseLong(tmpLine[header.indexOf(MSVariables.ptSubNone)]));
                        Assert.assertEquals(4, Long.parseLong(tmpLine[header.indexOf(MSVariables.nocarNone)]));
                        Assert.assertEquals(4, Long.parseLong(tmpLine[header.indexOf(MSVariables.secondary)]));
                        Assert.assertEquals(4, Long.parseLong(tmpLine[header.indexOf(MSVariables.employment0)]));
                        Assert.assertEquals(4, Long.parseLong(tmpLine[header.indexOf(MSVariables.ageCat17)]));
                        Assert.assertEquals(4, Long.parseLong(tmpLine[header.indexOf(MSVariables.work)]));
                    } else {
                        Assert.assertEquals(0, Long.parseLong(tmpLine[header.indexOf(MSVariables.all)]));
                        Assert.assertEquals(0, Long.parseLong(tmpLine[header.indexOf(MSVariables.carAvailableFalse)]));
                        Assert.assertEquals(0, Long.parseLong(tmpLine[header.indexOf(MSVariables.ptSubNone)]));
                        Assert.assertEquals(0, Long.parseLong(tmpLine[header.indexOf(MSVariables.nocarNone)]));
                        Assert.assertEquals(0, Long.parseLong(tmpLine[header.indexOf(MSVariables.secondary)]));
                        Assert.assertEquals(0, Long.parseLong(tmpLine[header.indexOf(MSVariables.employment0)]));
                        Assert.assertEquals(0, Long.parseLong(tmpLine[header.indexOf(MSVariables.ageCat17)]));
                        Assert.assertEquals(0, Long.parseLong(tmpLine[header.indexOf(MSVariables.work)]));
                    }
                }
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    public void ptTest() {

        IdMap<Person, Plan> experiencedPlans = new IdMap<>(Person.class, population.getPersons().size());
        population.getPersons().values().forEach(p -> experiencedPlans.put(p.getId(), p.getSelectedPlan()));

        modalSplitStats.analyzeAndWriteStats(output, experiencedPlans);

        try (BufferedReader reader = new BufferedReader(new FileReader(output + "SBB_changes_count.csv"))) {

            List<String> header = List.of(reader.readLine().split(";"));
            String line;
            while ((line = reader.readLine()) != null) {
                var tmpLine = line.split(";");

                if (tmpLine[header.indexOf(MSVariables.subpopulation)].equals(Variables.REGULAR)) {
                    if (tmpLine[header.indexOf("Umsteigetyp")].equals("changesTrain")) {
                        Assert.assertEquals(1, Long.parseLong(tmpLine[header.indexOf("0")]));
                        Assert.assertEquals(1, Long.parseLong(tmpLine[header.indexOf("1")]));
                    }
                    if (tmpLine[header.indexOf("Umsteigetyp")].equals("changesOEV")) {
                        Assert.assertEquals(0, Long.parseLong(tmpLine[header.indexOf("0")]));
                        Assert.assertEquals(1, Long.parseLong(tmpLine[header.indexOf("1")]));
                    }
                    if (tmpLine[header.indexOf("Umsteigetyp")].equals("changesOPNV")) {
                        Assert.assertEquals(0, Long.parseLong(tmpLine[header.indexOf("0")]));
                        Assert.assertEquals(0, Long.parseLong(tmpLine[header.indexOf("1")]));
                    }
                }
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        try (BufferedReader reader = new BufferedReader(new FileReader(output + "SBB_train_stations_count.csv"))) {

            List<String> header = List.of(reader.readLine().split(";"));
            String line;
            while ((line = reader.readLine()) != null) {
                var tmpLine = line.split(";");

                if (tmpLine[header.indexOf("HST_Nummer")].equals("3289")) {
                    Assert.assertEquals(1, Long.parseLong(tmpLine[header.indexOf("Ziel_Aussteiger")]));
                    Assert.assertEquals(2, Long.parseLong(tmpLine[header.indexOf("Quell_Einsteiger")]));
                    Assert.assertEquals(1, Long.parseLong(tmpLine[header.indexOf("Ziel_Aussteiger")]));
                }
                if (tmpLine[header.indexOf("HST_Nummer")].equals("1800")) {
                    Assert.assertEquals(2, Long.parseLong(tmpLine[header.indexOf("Ziel_Aussteiger")]));
                    Assert.assertEquals(1, Long.parseLong(tmpLine[header.indexOf("Umsteiger_Simba_Simba")]));
                }
                if (tmpLine[header.indexOf("HST_Nummer")].equals("2194")) {
                    Assert.assertEquals(1, Long.parseLong(tmpLine[header.indexOf("Quell_Einsteiger")]));
                }
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        try (BufferedReader reader = new BufferedReader(new FileReader(output + "SBB_stop_stations_count.csv"))) {

            List<String> header = List.of(reader.readLine().split(";"));
            String line;
            while ((line = reader.readLine()) != null) {
                var tmpLine = line.split(";");

                if (tmpLine[header.indexOf("Stop_Nummer")].equals("3289")) {
                    Assert.assertEquals(2, Long.parseLong(tmpLine[header.indexOf("Einstiege_Gesamt")]));
                    Assert.assertEquals(1, Long.parseLong(tmpLine[header.indexOf("Ausstiege_Gesamt")]));
                    Assert.assertEquals(1, Long.parseLong(tmpLine[header.indexOf("Zielaustieg_walk")]));
                    Assert.assertEquals(2, Long.parseLong(tmpLine[header.indexOf("Quelleinstieg_walk")]));
                }
                if (tmpLine[header.indexOf("Stop_Nummer")].equals("1800")) {
                    Assert.assertEquals(1, Long.parseLong(tmpLine[header.indexOf("Einstiege_Gesamt")]));
                    Assert.assertEquals(3, Long.parseLong(tmpLine[header.indexOf("Ausstiege_Gesamt")]));
                    Assert.assertEquals(1, Long.parseLong(tmpLine[header.indexOf("Zielaustieg_walk")]));
                    Assert.assertEquals(1, Long.parseLong(tmpLine[header.indexOf("Zielaustieg_bus")]));
                    Assert.assertEquals(0, Long.parseLong(tmpLine[header.indexOf("Quelleinstieg_bus")]));
                    Assert.assertEquals(0, Long.parseLong(tmpLine[header.indexOf("Zielaustieg_tram")]));
                    Assert.assertEquals(0, Long.parseLong(tmpLine[header.indexOf("Quelleinstieg_tram")]));
                    Assert.assertEquals(0, Long.parseLong(tmpLine[header.indexOf("Einstiege_FQ_Gesamt")]));
                }
                if (tmpLine[header.indexOf("Stop_Nummer")].equals("2194")) {
                    Assert.assertEquals(1, Long.parseLong(tmpLine[header.indexOf("Einstiege_Gesamt")]));
                    Assert.assertEquals(1, Long.parseLong(tmpLine[header.indexOf("Quelleinstieg_walk")]));
                }
                if (tmpLine[header.indexOf("Stop_Nummer")].equals("485773991")) {
                    Assert.assertEquals(1, Long.parseLong(tmpLine[header.indexOf("Einstiege_Gesamt")]));
                    Assert.assertEquals(0, Long.parseLong(tmpLine[header.indexOf("Quelleinstieg_tram")]));
                    Assert.assertEquals(0, Long.parseLong(tmpLine[header.indexOf("Quelleinstieg_bus")]));
                }
                if (tmpLine[header.indexOf("Stop_Nummer")].equals("485774092")) {
                    Assert.assertEquals(1, Long.parseLong(tmpLine[header.indexOf("Ausstiege_Gesamt")]));
                    Assert.assertEquals(1, Long.parseLong(tmpLine[header.indexOf("Zielaustieg_walk")]));
                }
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

}
