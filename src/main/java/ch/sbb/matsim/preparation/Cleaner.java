/*
 * Copyright (C) Schweizerische Bundesbahnen SBB, 2017.
 */

package ch.sbb.matsim.preparation;

import ch.sbb.matsim.config.variables.SBBModes;
import org.apache.log4j.Logger;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.population.*;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.population.PopulationUtils;
import org.matsim.core.population.io.PopulationReader;
import org.matsim.core.population.io.PopulationWriter;
import org.matsim.core.scenario.ScenarioUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class Cleaner {
    private static final Logger log =Logger.getLogger(Cleaner.class);
    private Population population;

    public Cleaner(Population population) {
        this.population = population;
    }

    public void removeNonSelectedPlans() {
        log.info("Cleaner starts removing all non-selected plans.");
        for(Person p : this.population.getPersons().values())  {
            Plan selectedPlan = p.getSelectedPlan();
            List<Plan> plansToRemove = new ArrayList<>();

            for(Plan plan: p.getPlans())    {
                if(!plan.equals(selectedPlan))
                    plansToRemove.add(plan);
            }

            for(Plan plan: plansToRemove)
                p.removePlan(plan);
        }
        log.info("Cleaner removed all non-selected plans.");
    }

    public void clean(List<String> modesToClean, List<String> subpopulationsToClean){
        for(String mode: modesToClean)
            log.info("Cleaner cleanes routes for mode: " + mode);
        for(String subpop: subpopulationsToClean)
            log.info("Cleaner cleans routes for subpop: " + subpop);

        for(Person p: population.getPersons().values()){
            if (!subpopulationsToClean.contains(p.getAttributes().getAttribute("subpopulation")) &&
                    !subpopulationsToClean.contains("all"))
                continue;

            for(Plan plan: p.getPlans()){
                List<PlanElement> planElements = plan.getPlanElements();
                int i = 0;
                String mode = null;
                for(int n = planElements.size(); i < n; ++i) {
                    PlanElement pe = planElements.get(i);
                    if (pe instanceof Activity) {
                        Activity act = (Activity)pe;
                        if(act.getType().contains("interaction"))
                            mode = act.getType().split(" ")[0];
                        else
                            mode = null;

                        if (mode != null && modesToClean.contains(mode)) {
                            PopulationUtils.removeActivity(plan, i);
                            n -= 2;
                            i -= 2;
                        }
                    } else if (pe instanceof Leg) {
                        Leg leg = (Leg)pe;
                        if(mode != null && modesToClean.contains(mode))    {
                            leg.setMode(mode);
                            leg.setRoute(null);
                        }
                        if (leg.getMode().equals(SBBModes.PT_FALLBACK_MODE) && modesToClean.contains(SBBModes.PT)) {
                            leg.setMode(SBBModes.PT);
                            leg.setRoute(null);
                        }
                        if(modesToClean.contains(leg.getMode())) {
                            leg.setRoute(null);
                        }
                    }
                }
            }
        }
        log.info("Finished cleaning the routes for all specified modes and subpopulations.");
    }

    public static void main(final String[] args) {
        final String planFile = args[0];
        final String attributeFile = args[1];
        final String outputPlanFile = args[2];
        // the cleaner will remove all routes of the given modes. It supports pt,car, ride, bike, walk with and without access times.
        final String modesToCleanStr = args[3];
        // list of subpopulation names to clean. "all" means that the routes of all subpopulations will be removed.
        final String subpopulationsToCleanStr = args[4];

        List<String> modesToClean = Arrays.asList(modesToCleanStr.split(","));
        List<String> subpopulationsToClean = Arrays.asList(subpopulationsToCleanStr.split(","));

        Scenario scenario = ScenarioUtils.createScenario(ConfigUtils.createConfig());
        new PopulationReader(scenario).readFile(planFile);

        Cleaner cleaner = new Cleaner(scenario.getPopulation());
        cleaner.removeNonSelectedPlans();
        cleaner.clean(modesToClean, subpopulationsToClean);

        new PopulationWriter(scenario.getPopulation()).write(outputPlanFile);
    }
}

