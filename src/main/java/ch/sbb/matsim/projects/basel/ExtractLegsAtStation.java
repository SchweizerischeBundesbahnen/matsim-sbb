package ch.sbb.matsim.projects.basel;

import ch.sbb.matsim.config.variables.SBBModes;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.population.*;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.gbl.MatsimRandom;
import org.matsim.core.population.io.PopulationReader;
import org.matsim.core.population.io.PopulationWriter;
import org.matsim.core.scenario.ScenarioUtils;

import java.util.List;
import java.util.Random;

public class ExtractLegsAtStation {

    public static final String STATION_ACT = "station";
    public static final String PT_INTERACTION = "pt interaction";
    public static final String ACTIVITY = "activity";
    public static final String TRANSFER = "transfer";
    //VIA does not like visualizing interaction activities
    public static final double TIMEVARIATION = 300;

    public static void main(String[] args) {
        String inputPlans = "\\\\wsbbrz0283\\mobi\\40_Projekte\\20230201_Biel_2040\\sim\\pedsim\\output-plans-biel.xml.gz";
        String outputPlans = "\\\\wsbbrz0283\\mobi\\40_Projekte\\20230201_Biel_2040\\sim\\pedsim\\biel-legs.xml.gz";

        List<Id<Link>> stopFacilityIds = List.of(Id.createLinkId("pt_1279"), Id.createLinkId("pt_3654"));

        extractLegs(inputPlans, outputPlans, stopFacilityIds);
    }

    public static void extractLegs(String inputPlans, String outputPlans, List<Id<Link>> stopFacilityIds) {
        Random r = MatsimRandom.getRandom();
        Scenario scenario = ScenarioUtils.createScenario(ConfigUtils.createConfig());
        Scenario scenarioOut = ScenarioUtils.createScenario(ConfigUtils.createConfig());
        new PopulationReader(scenario).readFile(inputPlans);
        Population popOut = scenarioOut.getPopulation();
        PopulationFactory f = popOut.getFactory();
        for (Person p : scenario.getPopulation().getPersons().values()) {
            Plan plan = p.getSelectedPlan();

            Activity beforeAct = null;
            Activity afterAct = null;
            Leg relevantLeg = null;
            boolean egress = false;
            for (PlanElement planElement : plan.getPlanElements()) {
                if (planElement instanceof Activity) {
                    if (relevantLeg != null) {
                        afterAct = (Activity) planElement;
                        break;
                    }
                    beforeAct = (Activity) planElement;
                } else if (planElement instanceof Leg) {
                    Leg leg = (Leg) planElement;
                    if (leg.getMode().equals(SBBModes.ACCESS_EGRESS_WALK)) {
                        if (stopFacilityIds.contains(leg.getRoute().getStartLinkId())) {
                            //within station transfer
                            if (stopFacilityIds.contains(leg.getRoute().getEndLinkId())) continue;
                            relevantLeg = f.createLeg(SBBModes.WALK_MAIN_MAINMODE);
                            relevantLeg.setDepartureTime(leg.getDepartureTime().seconds());
                            egress = true;

                        } else if (stopFacilityIds.contains(leg.getRoute().getEndLinkId())) {
                            relevantLeg = f.createLeg(SBBModes.WALK_MAIN_MAINMODE);
                            relevantLeg.setDepartureTime(leg.getDepartureTime().seconds());

                        }
                    }
                }
            }
            Person newPerson = f.createPerson(p.getId());
            popOut.addPerson(newPerson);
            Plan newPlan = f.createPlan();
            newPerson.addPlan(newPlan);
            if (relevantLeg != null) {
                double timeVariation = 2 * TIMEVARIATION * (r.nextDouble() - 1.0);
                if (egress) {
                    Activity start = f.createActivityFromCoord(STATION_ACT, beforeAct.getCoord());
                    start.setEndTime(relevantLeg.getDepartureTime().seconds() + timeVariation);
                    newPlan.addActivity(start);
                    relevantLeg.setDepartureTime(start.getEndTime().seconds());
                    newPlan.addLeg(relevantLeg);
                    String endActivityType = afterAct.getType().equals(PT_INTERACTION) ? TRANSFER : ACTIVITY;
                    Activity end = f.createActivityFromCoord(endActivityType, afterAct.getCoord());
                    newPlan.addActivity(end);
                } else {
                    String startActivityType = beforeAct.getType().equals(PT_INTERACTION) ? TRANSFER : ACTIVITY;
                    Activity start = f.createActivityFromCoord(startActivityType, beforeAct.getCoord());
                    start.setEndTime(relevantLeg.getDepartureTime().seconds() + timeVariation);
                    relevantLeg.setDepartureTime(start.getEndTime().seconds());
                    newPlan.addActivity(start);
                    newPlan.addLeg(relevantLeg);
                    Activity end = f.createActivityFromCoord(STATION_ACT, afterAct.getCoord());
                    newPlan.addActivity(end);
                }
            }

        }
        new PopulationWriter(scenarioOut.getPopulation()).write(outputPlans);
    }
}
