/*
 * Copyright (C) Schweizerische Bundesbahnen SBB, 2017.
 */

package playgrounds.mrieser.pt.routers.perftest;

import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.population.Activity;
import org.matsim.api.core.v01.population.Leg;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.Plan;
import org.matsim.api.core.v01.population.PlanElement;
import org.matsim.api.core.v01.population.Population;
import org.matsim.api.core.v01.population.Route;
import org.matsim.contrib.minibus.performance.raptor.Raptor;
import org.matsim.contrib.minibus.performance.raptor.RaptorDisutility;
import org.matsim.contrib.minibus.performance.raptor.TransitRouterQuadTree;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.population.io.PopulationReader;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.core.utils.geometry.CoordUtils;
import org.matsim.core.utils.misc.Time;
import org.matsim.facilities.Facility;
import org.matsim.pt.router.FakeFacility;
import org.matsim.pt.router.TransitRouter;
import org.matsim.pt.router.TransitRouterConfig;
import org.matsim.pt.router.TransitRouterImpl;
import org.matsim.pt.routes.ExperimentalTransitRoute;
import org.matsim.pt.transitSchedule.api.TransitSchedule;
import org.matsim.pt.transitSchedule.api.TransitScheduleReader;
import org.matsim.pt.utils.TransitScheduleValidator;

import java.io.BufferedWriter;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.util.List;

public class PerformanceTest {

    public static void main(String[] args) throws IOException {
        String testPopulation = "\\\\v00925\\Simba\\20_Modelle\\80_MatSim\\30_ModellCH\\04_TestSzenario\\02_simulation\\output\\TestSample.output_plans.xml.gz";
        String testSchedule = "\\\\v00925\\Simba\\20_Modelle\\80_MatSim\\30_ModellCH\\04_TestSzenario\\02_simulation\\output\\TestSample.output_transitSchedule.xml.gz";
//        String testNetwork = "\\\\v00925\\Simba\\20_Modelle\\80_MatSim\\30_ModellCH\\04_TestSzenario\\02_simulation\\output\\TestSample.output_network.xml.gz";
        int maxPlans = 100;

        System.setProperty("matsim.preferLocalDtds", "true");

        Scenario scenario = ScenarioUtils.createScenario(ConfigUtils.createConfig());

//        new MatsimNetworkReader(scenario.getNetwork()).readFile(testNetwork);
        new PopulationReader(scenario).readFile(testPopulation);
        new TransitScheduleReader(scenario).readFile(testSchedule);

//        TransitScheduleValidator.printResult(TransitScheduleValidator.validateAll(scenario.getTransitSchedule(), scenario.getNetwork()));

        TransitRouter[] routers = new TransitRouter[]{
                getDefaultRouter(scenario.getTransitSchedule()),
                getMinibusRaptor(scenario.getTransitSchedule())
        };

        long[] starts = new long[routers.length];
        long[] ends = new long[routers.length];
        int[] results = new int[routers.length];

        for (int i = 0; i < routers.length; i++) {
            TransitRouter router = routers[i];
            starts[i] = System.currentTimeMillis();
            results[i] = testRouter(scenario.getPopulation(), maxPlans, router);
            ends[i] = System.currentTimeMillis();
            double duration = (ends[i] - starts[i]) / 1000.0;
            System.out.println("### " + duration + " seconds   " + router.getClass().getName());
        }

        System.out.println();
        System.out.println("------------------------------------------------------");
        System.out.printf("%10s %10s %s %n", "Duration", "# Legs", "Router");
        for (int i = 0; i < routers.length; i++) {
            double duration = (ends[i] - starts[i]) / 1000.0;
            System.out.printf("%10.3f %10d %10s %n", duration, results[i], routers[i].getClass().getName());
        }
        System.out.println();
    }

    private static TransitRouter getDefaultRouter(TransitSchedule schedule) {
        long start = System.currentTimeMillis();
        Config config = ConfigUtils.createConfig();
        TransitRouterConfig ptRouterConfig = new TransitRouterConfig(config);
        TransitRouterImpl router = new TransitRouterImpl(ptRouterConfig, schedule);
        long end = System.currentTimeMillis();
        System.out.println("Preparing default router took " + (end - start)/1000.0 + " seconds.");
        return router;
    }

    private static TransitRouter getMinibusRaptor(TransitSchedule schedule) {
        long start = System.currentTimeMillis();
        Config config = ConfigUtils.createConfig();
        TransitRouterConfig ptRouterConfig = new TransitRouterConfig(config);
        double costPerBoarding = 0;
        double costPerMeterTraveled = 0;
        RaptorDisutility disutility = new RaptorDisutility(ptRouterConfig, costPerBoarding, costPerMeterTraveled);
        TransitRouterQuadTree transitQT = new TransitRouterQuadTree(disutility);
        transitQT.initializeFromSchedule(schedule, ptRouterConfig.getBeelineWalkConnectionDistance());
        Raptor raptor = new Raptor(transitQT, disutility, ptRouterConfig);
        long end = System.currentTimeMillis();
        System.out.println("Preparing raptor took " + (end - start)/1000.0 + " seconds.");
        return raptor;
    }

    private static int testRouter(Population population, int maxPlans, TransitRouter router) throws IOException {
        int planCounter = 0;
        int legCount = 0;
        try (Writer out = new BufferedWriter(new FileWriter(router.getClass().getSimpleName() + ".txt"))) {
            out.write("Person\tDepTime\tfromAct\tfromX\tfromY\ttoAct\ttoX\ttoY\tdistance\troute\n");
            for (Person person : population.getPersons().values()) {
                planCounter++;
                if (planCounter > maxPlans) {
                    return legCount;
                }
                Plan plan = person.getSelectedPlan();
                Activity prevAct = null;
                for (PlanElement pe : plan.getPlanElements()) {
                    if (pe instanceof Activity) {
                        Activity act = (Activity) pe;

                        if (prevAct != null) {
                            Facility from = new FakeFacility(prevAct.getCoord());
                            Facility to = new FakeFacility(act.getCoord());
                            double depTime = prevAct.getEndTime();
                            if (Double.isNaN(depTime)) {
                                depTime = prevAct.getStartTime() + prevAct.getMaximumDuration();
                            }
                            if (!Double.isNaN(depTime)) {
                                List<Leg> legs = router.calcRoute(from, to, depTime, person);
                                legCount += legs == null ? 0 : legs.size();
                                writeLegs(person, depTime, prevAct, act, legs, out);
                            }
                        }
                        prevAct = act;
                    }
                }
            }
        }
        return legCount;
    }

    private static void writeLegs(Person person, double deptime, Activity from, Activity to, List<Leg> legs, Writer out) throws IOException {
        int dist = (int) CoordUtils.calcEuclideanDistance(from.getCoord(), to.getCoord());
        out.write(person.getId() + "\t" + Time.writeTime(deptime) + "\t" + from.getType() + "\t" + from.getCoord().getX() + "\t" + from.getCoord().getY()  + "\t" + to.getType() + "\t" + to.getCoord().getX() + "\t" + to.getCoord().getY() + "\t" + dist + "\t");
        if (legs == null) {
            out.write("n/a");
        } else {
            for (Leg leg : legs) {
                Route route = leg.getRoute();
                if (route instanceof ExperimentalTransitRoute) {
                    ExperimentalTransitRoute trRoute = (ExperimentalTransitRoute) route;
                    out.write(trRoute.getLineId() + "/" + trRoute.getRouteId() + "  ");
                } else {
                    out.write(leg.getMode() + "  ");
                }
            }
        }
        out.write("\n");
    }
}
