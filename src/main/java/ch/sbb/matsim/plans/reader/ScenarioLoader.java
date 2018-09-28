package ch.sbb.matsim.plans.reader;

import ch.sbb.matsim.plans.abm.AbmData;
import ch.sbb.matsim.synpop.writer.MATSimWriter;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.Plan;
import org.matsim.api.core.v01.population.Population;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.population.io.PopulationReader;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.facilities.FacilitiesReaderMatsimV1;
import org.matsim.utils.objectattributes.ObjectAttributesXmlReader;

import java.util.HashSet;
import java.util.Set;

public class ScenarioLoader {

    public ScenarioLoader() {
    }

    public static Scenario prepareSynpopData(AbmData abmData, String pathSynpopOut) {
        Scenario scenario = ScenarioUtils.createScenario(ConfigUtils.createConfig());
        loadScenario(scenario, pathSynpopOut);

        Set<Id<Person>> personsToRemove = new HashSet<>();
        Population population = scenario.getPopulation();

        for(Person person: population.getPersons().values()) {
            if(abmData.getPersonIds().contains(person.getId())) {
                Plan plan = population.getFactory().createPlan();
                person.addPlan(plan);
                person.setSelectedPlan(plan);
            }
            else    {
                personsToRemove.add(person.getId());
                population.getPersonAttributes().removeAllAttributes(person.getId().toString());
            }
        }

        for(Id<Person> pid: personsToRemove)    {
            population.removePerson(pid);
        }
        return scenario;
    }

    private static void loadScenario(Scenario scenario, String path)  {
        // TODO: link filenames to synpop final declaration
        new PopulationReader(scenario).readFile(path + MATSimWriter.POPULATION);
        new ObjectAttributesXmlReader(scenario.getPopulation().getPersonAttributes()).readFile(path + "/person_attributes.xml.gz");
        // TODO: take original synpop output... then, PrepareFacilities will be obsolete
        new FacilitiesReaderMatsimV1(scenario).readFile(path + MATSimWriter.FACILITIES);
        // TODO: with MATSim 11, this should be removed
        new ObjectAttributesXmlReader(scenario.getActivityFacilities().getFacilityAttributes()).readFile(path + "/facility_attributes.xml.gz");
    }
}