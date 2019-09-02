package ch.sbb.matsim.intermodal;

import ch.sbb.matsim.RunSBB;
import ch.sbb.matsim.config.SBBIntermodalConfigGroup;
import ch.sbb.matsim.config.SBBIntermodalConfigGroup.SBBIntermodalModeParameterSet;
import ch.sbb.matsim.csv.CSVReader;
import ch.sbb.matsim.routing.pt.raptor.RaptorIntermodalAccessEgress;
import ch.sbb.matsim.synpop.reader.Synpop2MATSim;
import org.apache.log4j.Logger;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.Population;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.controler.AbstractModule;
import org.matsim.core.scenario.ScenarioUtils;

import java.io.IOException;
import java.util.*;


public class IntermodalModule extends AbstractModule {
    private final static Logger log = Logger.getLogger(IntermodalModule.class);
    private SBBIntermodalConfigGroup configGroup;

    public IntermodalModule(Scenario scenario) {
        super(scenario.getConfig());
        this.configGroup = ConfigUtils.addOrGetModule(this.getConfig(), SBBIntermodalConfigGroup.class);
        this.prepare(scenario);
        this.preparePopulation(scenario.getPopulation(), csvPath);

    }

    private void preparePopulation(Population population, String csvPath) {
        try (CSVReader reader = new CSVReader(csvPath, ";")) {
            log.info(csvPath);

            //removing business_id from columns and read activities
            Set<String> attributes = new HashSet<String>(Arrays.asList(reader.getColumns()));

            Map<String, String> map;
            while ((map = reader.readLine()) != null) {
                Id<Person> personId = Id.createPersonId(map.get("personId"));
                Person person = population.getPersons().get(personId);
                for (String attribute : attributes) {
                    person.getAttributes().putAttribute(attribute, map.get(attribute));
                }
            }
        } catch (IOException e) {
            log.warn(e);
        }


    }

    private void prepare(Scenario scenario) {
        for (SBBIntermodalModeParameterSet mode : this.configGroup.getModeParameterSets()) {
            if (mode.isOnNetwork()) {
                Intermodal.prepareNetwork(scenario.getNetwork(), mode.getMode());
                Set<String> mainModes = new HashSet<>(scenario.getConfig().qsim().getMainModes());
                mainModes.add(mode.getMode());
                scenario.getConfig().qsim().setMainModes(mainModes);
            }
        }
    }

    @Override
    public void install() {
        for (SBBIntermodalModeParameterSet mode : this.configGroup.getModeParameterSets()) {
            if (mode.isOnNetwork()) {
                addTravelTimeBinding(mode.getMode()).to(networkTravelTime());
                addTravelDisutilityFactoryBinding(mode.getMode()).to(carTravelDisutilityFactoryKey());
            }
        }

        bind(RaptorIntermodalAccessEgress.class).toInstance(new SBBRaptorIntermodalAccessEgress(this.configGroup.getModeParameterSets()));
    }

}


