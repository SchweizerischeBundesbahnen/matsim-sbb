/*
 * Copyright (C) Schweizerische Bundesbahnen SBB, 2018.
 */

package ch.sbb.matsim.routing.pt.raptor;

import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.population.Activity;
import org.matsim.api.core.v01.population.Leg;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.Plan;
import org.matsim.api.core.v01.population.PlanElement;
import org.matsim.api.core.v01.population.Route;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.population.io.StreamingPopulationReader;
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

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.List;

/**
 * @author mrieser / SBB
 */
public class PerformanceAnalysis {

    public static void main(String[] args) throws IOException {
// TestSzenario Sion
//        String testPopulation = "\\\\v00925\\Simba\\20_Modelle\\80_MatSim\\30_ModellCH\\04_TestSzenario\\02_simulation\\output\\TestSample.output_plans.xml.gz";
//        String testSchedule = "\\\\v00925\\Simba\\20_Modelle\\80_MatSim\\30_ModellCH\\04_TestSzenario\\02_simulation\\output\\TestSample.output_transitSchedule.xml.gz";
//        String testNetwork = "\\\\v00925\\Simba\\20_Modelle\\80_MatSim\\30_ModellCH\\04_TestSzenario\\02_simulation\\output\\TestSample.output_network.xml.gz";

// CNB 1.4
//        String testPopulation = "D:\\devsbb\\mrieser\\data\\runs\\matsim-runs\\prepared\\attributeMerged\\population.xml.gz";
//        String testSchedule = "D:\\devsbb\\mrieser\\data\\runs\\matsim-runs\\prepared\\cut\\transitSchedule.xml.gz";

// CH 2016
        String testPopulation = "D:\\devsbb\\mrieser\\data\\raptorPerfTest\\population.xml.gz";
        String testSchedule = "D:\\devsbb\\mrieser\\data\\raptorPerfTest\\transitSchedule.xml.gz";

        int maxPlans = 2000;
        int warmupRounds = 3;
        int measurementRounds = 5;

        System.setProperty("matsim.preferLocalDtds", "true");

        Scenario scenario = ScenarioUtils.createScenario(ConfigUtils.createConfig());

//        new MatsimNetworkReader(scenario.getNetwork()).readFile(testNetwork);
        List<Person> persons = new ArrayList<>();
        StreamingPopulationReader popReader = new StreamingPopulationReader(scenario);
        popReader.addAlgorithm(person -> {
            if (persons.size() < maxPlans) {
                // only keep the selected plan
                List<Plan> allPlans = new ArrayList<>(person.getPlans());
                for (Plan plan : allPlans) {
                    if (plan != person.getSelectedPlan()) {
                        person.removePlan(plan);
                    }
                }
                persons.add(person);
            }
        });
        popReader.readFile(testPopulation);
        new TransitScheduleReader(scenario).readFile(testSchedule);

//        TransitScheduleValidator.printResult(TransitScheduleValidator.validateAll(scenario.getTransitSchedule(), scenario.getNetwork()));

        TransitRouter[] routers = new TransitRouter[]{
//                getDefaultRouter(scenario.getTransitSchedule()),
//                getMinibusRaptor(scenario.getTransitSchedule()),
                getSwissRailRaptor(scenario.getTransitSchedule())
        };

        double[][] durations = new double[routers.length][measurementRounds];
        int[][] results = new int[routers.length][measurementRounds];

        for (int round = 0; round < warmupRounds; round++) {
            System.out.println("WARM UP -- round " + round);
            for (int i = 0; i < routers.length; i++) {
                TransitRouter router = routers[i];
                long start = System.currentTimeMillis();
                int result = testRouter(persons, router);
                long end = System.currentTimeMillis();
                double duration = (end - start) / 1000.0;
                System.out.printf("### %10.3f %10d %10s %n", duration, result, router.getClass().getName());
            }

        }

        for (int round = 0; round < measurementRounds; round++) {
            System.out.println("ROUND " + round);
            for (int i = 0; i < routers.length; i++) {
                TransitRouter router = routers[i];
                long start = System.currentTimeMillis();
                results[i][round] = testRouter(persons, router);
                long end = System.currentTimeMillis();
                durations[i][round] = (end - start) / 1000.0;
                System.out.printf("### %10.3f %10d %10s %n", durations[i][round], results[i][round], router.getClass().getName());
            }
        }

        System.out.println();
        System.out.println("------------------------------------------------------");
        System.out.printf("%10s %10s %s %n", "avg Duration", "avg # Legs", "Router");
        for (int i = 0; i < routers.length; i++) {
            double sum = sum(durations[i]);
            double result = sum(results[i]);
            System.out.printf("%10.3f %10.1f %10s %n", sum/measurementRounds, result/measurementRounds, routers[i].getClass().getName());
        }
        System.out.println();
    }

    private static double sum(double[] values) {
        double sum = 0;
        for (double v : values) {
            sum += v;
        }
        return sum;
    }

    private static int sum(int[] values) {
        int sum = 0;
        for (int v : values) {
            sum += v;
        }
        return sum;
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

//    private static TransitRouter getMinibusRaptor(TransitSchedule schedule) {
//        long start = System.currentTimeMillis();
//        Config config = ConfigUtils.createConfig();
//        TransitRouterConfig ptRouterConfig = new TransitRouterConfig(config);
//        double costPerBoarding = 0;
//        double costPerMeterTraveled = 0;
//        RaptorDisutility disutility = new RaptorDisutility(ptRouterConfig, costPerBoarding, costPerMeterTraveled);
//        TransitRouterQuadTree transitQT = new TransitRouterQuadTree(disutility);
//        transitQT.initializeFromSchedule(schedule, ptRouterConfig.getBeelineWalkConnectionDistance());
//        Raptor raptor = new Raptor(transitQT, disutility, ptRouterConfig);
//        long end = System.currentTimeMillis();
//        System.out.println("Preparing raptor took " + (end - start)/1000.0 + " seconds.");
//        return raptor;
//    }

    private static TransitRouter getSwissRailRaptor(TransitSchedule schedule) {
        long start = System.currentTimeMillis();
        Config config = ConfigUtils.createConfig();

        RaptorConfig raptorConfig = RaptorUtils.createRaptorConfig(config);
        SwissRailRaptorData data = SwissRailRaptorData.create(schedule, raptorConfig);
        SwissRailRaptor raptor = new SwissRailRaptor(data);
        long end = System.currentTimeMillis();
        System.out.println("Preparing SwissRailRaptor took " + (end - start)/1000.0 + " seconds.");
        return raptor;
    }

    private static int testRouter(List<Person> persons, TransitRouter router) throws IOException {
        int legCount = 0;
        try (Writer out = new BufferedWriter(new FileWriter(router.getClass().getSimpleName() + ".txt"))) {
            out.write("Person\tDepTime\tfromAct\tfromX\tfromY\ttoAct\ttoX\ttoY\tdistance\troute\n");
            for (Person person : persons) {
                Plan plan = person.getSelectedPlan();
                Activity prevAct = null;
                for (PlanElement pe : plan.getPlanElements()) {
                    if (pe instanceof Activity) {
                        Activity act = (Activity) pe;

                        if (prevAct != null) {
                            Facility from = new FakeFacility(prevAct.getCoord());
                            Facility to = new FakeFacility(act.getCoord());
                            double depTime = prevAct.getEndTime();
                            if (!Double.isFinite(depTime)) {
                                depTime = prevAct.getStartTime() + prevAct.getMaximumDuration();
                            }
                            if (Double.isFinite(depTime)) {
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
