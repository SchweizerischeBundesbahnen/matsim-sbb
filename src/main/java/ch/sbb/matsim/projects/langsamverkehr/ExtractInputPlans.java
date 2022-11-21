package ch.sbb.matsim.projects.langsamverkehr;

import ch.sbb.matsim.config.variables.SBBModes;
import ch.sbb.matsim.routing.SBBAnalysisMainModeIdentifier;
import java.util.ArrayList;
import java.util.List;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.population.Activity;
import org.matsim.api.core.v01.population.Leg;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.Plan;
import org.matsim.api.core.v01.population.PlanElement;
import org.matsim.api.core.v01.population.Population;
import org.matsim.api.core.v01.population.PopulationFactory;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.network.io.MatsimNetworkReader;
import org.matsim.core.population.io.PopulationReader;
import org.matsim.core.router.TripStructureUtils;
import org.matsim.core.router.TripStructureUtils.Trip;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.core.utils.geometry.CoordUtils;
import org.matsim.pt.transitSchedule.api.TransitSchedule;
import org.matsim.pt.transitSchedule.api.TransitScheduleReader;
import org.matsim.pt.transitSchedule.api.TransitStopFacility;

public class ExtractInputPlans {

    List<Id<Link>> stopFacilityLinkId = new ArrayList<>();

    Population extractedPopulation = null;

    TransitSchedule transitSchedule;
    int radius = 15000;
    Scenario scenario = ScenarioUtils.createScenario(ConfigUtils.createConfig());
    SBBAnalysisMainModeIdentifier mMI = new SBBAnalysisMainModeIdentifier();
    int splitPerson = 0;

    public static void main(String[] args) throws Exception {
        ExtractInputPlans extractInputPlans = new ExtractInputPlans();
        Scenario readScenario = ScenarioUtils.createScenario(ConfigUtils.createConfig());
        new PopulationReader(readScenario).readFile(args[0]);
        new TransitScheduleReader(readScenario).readFile(args[1]);
        new MatsimNetworkReader(readScenario.getNetwork()).readFile(args[2]);
        extractInputPlans.scenario = readScenario;
        extractInputPlans.run();
        CreateEntrancesAndExits createEntrancesAndExits = new CreateEntrancesAndExits(readScenario.getNetwork(), extractInputPlans.extractedPopulation,
            readScenario.getTransitSchedule(), args[3], args[4], args[5]);
        createEntrancesAndExits.readEntrancesAndExits();
        System.out.println("Done");
    }

    public void run() throws Exception {
        for (TransitStopFacility stopFacility : scenario.getTransitSchedule().getFacilities().values()) {
            if (stopFacility.getAttributes().getAttribute("03_Stop_Code") != null) {
                stopFacilityLinkId.add(stopFacility.getLinkId());
            }
        }

        Scenario scenario2 = ScenarioUtils.createScenario(ConfigUtils.createConfig());
        extractedPopulation = scenario2.getPopulation();
        PopulationFactory f = extractedPopulation.getFactory();

        for (Person person : scenario.getPopulation().getPersons().values()) {
            Plan plan = person.getSelectedPlan();
            for (TripStructureUtils.Trip trip : TripStructureUtils.getTrips(plan)) {
                nearStation(scenario, f, person, trip);
            }
            usesStation(f, person, plan);
            splitPerson = 0;
        }
    }

    private void usesStation(PopulationFactory f, Person person, Plan plan) {
        List<Integer> checkList = new ArrayList<>();
        for (PlanElement planElement : plan.getPlanElements()) {
            if (planElement instanceof Activity activity) {
                int index = plan.getPlanElements().indexOf(planElement);
                if (activity.getType().equals("pt interaction")) {
                    Leg legBefore = (Leg) plan.getPlanElements().get(index - 1);
                    Leg legAfter = (Leg) plan.getPlanElements().get(index + 1);
                    if (!legBefore.getMode().equals(SBBModes.PT) &&
                        !legBefore.getRoute().getStartLinkId().equals(legBefore.getRoute().getEndLinkId())  &&
                        !checkList.contains(index-1) &&
                        (stopFacilityLinkId.contains(legBefore.getRoute().getStartLinkId()) ^
                            stopFacilityLinkId.contains(legBefore.getRoute().getEndLinkId()))) {
                        Activity sA = (Activity) plan.getPlanElements().get(index - 2);
                        activity.setType("work");
                        sA.setEndTime(legBefore.getDepartureTime().seconds());
                        sA.setType("home");
                        legBefore.setMode(SBBModes.WALK_FOR_ANALYSIS);

                        Person nPerson = f.createPerson(Id.create(person.getId().toString() + "_" + splitPerson++, Person.class));
                        Plan nPlan = f.createPlan();

                        nPlan.addActivity(sA);
                        nPlan.addLeg(legBefore);
                        nPlan.addActivity(activity);

                        nPerson.addPlan(nPlan);
                        extractedPopulation.addPerson(nPerson);
                        checkList.add(index-1);
                    }
                    if (!legAfter.getMode().equals(SBBModes.PT) &&
                        !legAfter.getRoute().getStartLinkId().equals(legAfter.getRoute().getEndLinkId()) &&
                        (stopFacilityLinkId.contains(legAfter.getRoute().getStartLinkId()) ^
                            stopFacilityLinkId.contains(legAfter.getRoute().getEndLinkId()))) {
                        activity.setType("work");
                        activity.setEndTime(legAfter.getDepartureTime().seconds());
                        Activity eA = (Activity) plan.getPlanElements().get(index + 2);
                        eA.setType("home");
                        legAfter.setMode(SBBModes.WALK_FOR_ANALYSIS);

                        Person nPerson = f.createPerson(Id.create(person.getId().toString() + "_" + splitPerson++, Person.class));
                        Plan nPlan = f.createPlan();

                        nPlan.addActivity(activity);
                        nPlan.addLeg(legAfter);
                        nPlan.addActivity(eA);

                        nPerson.addPlan(nPlan);
                        extractedPopulation.addPerson(nPerson);
                        checkList.add(index+1);
                    }
                }
            }
        }
    }

    private void nearStation(Scenario scenario, PopulationFactory f, Person person, Trip trip) {
        if (mMI.identifyMainMode(trip.getTripElements()).equals(SBBModes.WALK_MAIN_MAINMODE)) {
            for (TransitStopFacility transitStopFacility : scenario.getTransitSchedule().getFacilities().values()) {
                if ((CoordUtils.calcEuclideanDistance(trip.getOriginActivity().getCoord(), transitStopFacility.getCoord()) < radius) ||
                    (CoordUtils.calcEuclideanDistance(trip.getDestinationActivity().getCoord(), transitStopFacility.getCoord()) < radius)) {

                    Person nPerson = f.createPerson(Id.create(person.getId().toString() + "_" + splitPerson++, Person.class));
                    Plan nPlan = f.createPlan();

                    Activity startA = trip.getOriginActivity();
                    startA.setType("home");
                    Activity endA = trip.getDestinationActivity();
                    endA.setType("work");
                    Leg leg = f.createLeg(SBBModes.WALK_FOR_ANALYSIS);

                    nPlan.addActivity(startA);
                    nPlan.addLeg(leg);
                    nPlan.addActivity(endA);

                    nPerson.addPlan(nPlan);
                    extractedPopulation.addPerson(nPerson);
                    return;
                }
            }
        }
    }
}