package ch.sbb.matsim.plans.models;

import ch.sbb.matsim.config.variables.Variables;
import ch.sbb.matsim.plans.synpop.SynPopUtils;
import ch.sbb.matsim.plans.synpop.SynpopConstants;
import ch.sbb.matsim.zones.Zone;
import ch.sbb.matsim.zones.Zones;
import ch.sbb.matsim.zones.ZonesLoader;
import org.matsim.api.core.v01.Scenario;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.population.io.PopulationReader;
import org.matsim.core.population.io.PopulationWriter;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.households.HouseholdsReaderV10;
import org.matsim.households.HouseholdsWriterV10;

import java.util.stream.Collectors;

public class GenerateSubModel {
    public static void main(String[] args) {
        Scenario scenario = ScenarioUtils.createScenario(ConfigUtils.createConfig());
        Zones zones = ZonesLoader.loadZones("zones", "C:\\devsbb\\tmp\\synpop_2022\\35_kalif_secondary.mobi-zones.shp");
        scenario.addScenarioElement(Variables.ZONES, zones);
        new PopulationReader(scenario).readFile("C:\\devsbb\\tmp\\synpop_2022\\persons_2022.xml.gz");
        new HouseholdsReaderV10(scenario.getHouseholds()).readFile("C:\\devsbb\\tmp\\synpop_2022\\households_2022.xml.gz");
        SynPopUtils.linkHouseholdsAndPopulation(scenario);
        SynPopUtils.linkZonesToHouseHoldsAndPersons(scenario);
        String aggId = "mun_name";
        String aggregate = "Bern";
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
        new PopulationWriter(scenario.getPopulation()).write("C:\\devsbb\\tmp\\synpop_2022\\persons_2022_" + aggregate + ".xml.gz");
        new HouseholdsWriterV10(scenario.getHouseholds()).writeFile("C:\\devsbb\\tmp\\synpop_2022\\households_2022_" + aggregate + ".xml.gz");


    }
}
