package ch.sbb.matsim.plans.models;

import ch.sbb.matsim.config.variables.Variables;
import ch.sbb.matsim.plans.MOBiPlansRunner;
import ch.sbb.matsim.plans.constants.SynpopConstants;
import ch.sbb.matsim.plans.models.config.MOBiPlansConfig;
import ch.sbb.matsim.plans.modules.DefaultMOBiPlansModule;
import ch.sbb.matsim.plans.synpop.SynPopUtils;
import ch.sbb.matsim.plans.synpop.loading.SynpopLoader;
import ch.sbb.matsim.zones.Zone;
import ch.sbb.matsim.zones.Zones;
import ch.sbb.matsim.zones.ZonesLoader;
import com.google.inject.Guice;
import com.google.inject.Injector;
import org.matsim.api.core.v01.Scenario;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.population.io.PopulationReader;
import org.matsim.core.population.io.PopulationWriter;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.households.HouseholdsReaderV10;
import org.matsim.households.HouseholdsWriterV10;

import java.io.File;
import java.io.IOException;
import java.util.stream.Collectors;

public class GenerateSubModel {
     static void main(String[] args) throws IOException {
         MOBiPlansConfig config = MOBiPlansConfig.readFromYaml("/Users/joschka.bischoff/tmp/mobi_plans_config.yaml");

         Scenario scenario = ScenarioUtils.createScenario(ConfigUtils.createConfig());
         Zones zones = ZonesLoader.loadZones("zones", "/Users/joschka.bischoff/tmp/synpop2022/35_kalif_secondary.mobi-zones.shp");
         scenario.addScenarioElement(Variables.ZONES, zones);
         SynpopLoader loader = new SynpopLoader(config, scenario);
         loader.run();
        String aggId = "kt_name";
        String aggregate = "AI";
        var hhNotInAI = scenario.getHouseholds().getHouseholds().values()
                .stream().filter(household ->
                {
                    Zone zone = (Zone) household.getAttributes().getAttribute(SynpopConstants.ZONE);
                    return !zone.getAttribute(aggId).toString().equals(aggregate);
                })
                .map(household -> household.getId())
                .collect(Collectors.toSet());
        System.out.println("Number of households" + scenario.getHouseholds().getHouseholds().size());
        System.out.println("Number of households not in AI: " + hhNotInAI.size());
        hhNotInAI.forEach(householdId -> scenario.getHouseholds().getHouseholds().remove(householdId));
        System.out.println("Number of households in AI: " + scenario.getHouseholds().getHouseholds().size());
        var personsNotInAI = scenario.getPopulation().getPersons().values()
                .stream().filter(person ->
                {
                    Zone zone = (Zone) person.getAttributes().getAttribute(SynpopConstants.ZONE);
                    return !zone.getAttribute(aggId).toString().equals(aggregate);
                })
                .map(person -> person.getId())
                .collect(Collectors.toSet());
        System.out.println("Number of persons: " + scenario.getPopulation().getPersons().size());
        System.out.println("Number of persons not in AI: " + personsNotInAI.size());
        personsNotInAI.forEach(personId -> scenario.getPopulation().removePerson(personId));
        System.out.println("Number of persons in AI: " + scenario.getPopulation().getPersons().size());
        new PopulationWriter(scenario.getPopulation()).write("/Users/joschka.bischoff/tmp/synpop2022/AI/persons_2022_" + aggregate + ".xml.gz");
        new HouseholdsWriterV10(scenario.getHouseholds()).writeFile("/Users/joschka.bischoff/tmp/synpop2022/AI/households_2022_" + aggregate + ".xml.gz");


    }
}
