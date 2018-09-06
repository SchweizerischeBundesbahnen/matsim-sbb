package ch.sbb.matsim.plans.reader;

import ch.sbb.matsim.plans.abm.AbmData;
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

    private final Scenario scenario;
    private final String path;

    public ScenarioLoader(String pathSynpopOut) {
        this.path = pathSynpopOut;
        this.scenario = ScenarioUtils.createScenario(ConfigUtils.createConfig());
        loadScenario();
    }

    private void loadScenario()  {
        // TODO: link filenames to synpop final declaration
        new PopulationReader(this.scenario).readFile(this.path + "/persons.xml.gz");
        new ObjectAttributesXmlReader(this.scenario.getPopulation().getPersonAttributes()).readFile(this.path + "/person_attributes.xml.gz");
        // TODO: take original synpop output... then, PrepareFacilities will be obsolete
        new FacilitiesReaderMatsimV1(this.scenario).readFile(this.path + "/facilities_adj.xml.gz");
        new ObjectAttributesXmlReader(this.scenario.getActivityFacilities().getFacilityAttributes()).readFile(this.path + "/facility_attributes.xml.gz");
    }

    public Scenario prepareSynpopData(AbmData abmData) {
        Set<Id<Person>> personsToRemove = new HashSet<>();
        Population population = this.scenario.getPopulation();

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
        return this.scenario;
    }
}