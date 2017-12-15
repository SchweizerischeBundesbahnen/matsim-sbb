/*
 * Copyright (C) Schweizerische Bundesbahnen SBB, 2017.
 */

package ch.sbb.matsim.preparation;

import org.apache.log4j.Logger;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.Plan;
import org.matsim.api.core.v01.population.Population;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.population.io.PopulationReader;
import org.matsim.core.population.io.PopulationWriter;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.pt.router.TransitActsRemover;

public class Cleaner {
    private static final Logger log =Logger.getLogger(Cleaner.class);
    private Population population;

    public Cleaner(Population population) {
        this.population = population;
    }

    public void clean(){
        for(Person p: population.getPersons().values()){
            for(Plan plan: p.getPlans()){
                new TransitActsRemover().run(plan);
            }
        }
    }

    public static void main(final String[] args) {
        final Config config = ConfigUtils.createConfig();
        final String planFile = args[0];
        final String outputPlanFile = args[1];
        config.plans().setInputFile(planFile);

        Scenario scenario = ScenarioUtils.createScenario(config);
        new PopulationReader(scenario).readFile(config.plans().getInputFile());

        Cleaner cleaner = new Cleaner(scenario.getPopulation());
        cleaner.clean();

        new PopulationWriter(scenario.getPopulation()).write(outputPlanFile);
    }

}

