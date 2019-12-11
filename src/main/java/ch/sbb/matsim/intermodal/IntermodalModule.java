package ch.sbb.matsim.intermodal;

import ch.sbb.matsim.config.SBBIntermodalConfigGroup;
import ch.sbb.matsim.config.SBBIntermodalConfigGroup.SBBIntermodalModeParameterSet;
import ch.sbb.matsim.config.variables.SBBModes;
import ch.sbb.matsim.csv.CSVReader;
import ch.sbb.matsim.intermodal.analysis.IntermodalControlerListener;
import ch.sbb.matsim.intermodal.analysis.IntermodalTransferTimeAnalyser;
import ch.sbb.matsim.routing.network.SBBNetworkRoutingModule;
import ch.sbb.matsim.routing.pt.raptor.RaptorIntermodalAccessEgress;
import org.apache.log4j.Logger;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.Population;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.controler.AbstractModule;

import java.io.IOException;
import java.net.URL;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;


public class IntermodalModule extends AbstractModule {
    private final static Logger log = Logger.getLogger(IntermodalModule.class);
    private SBBIntermodalConfigGroup configGroup;

    public IntermodalModule(Scenario scenario) {
        super(scenario.getConfig());
        this.configGroup = ConfigUtils.addOrGetModule(this.getConfig(), SBBIntermodalConfigGroup.class);
        this.prepare(scenario);
        URL csvPath = configGroup.getAttributesCSVPathURL(scenario.getConfig().getContext());
        if (csvPath != null) {
            this.preparePopulation(scenario.getPopulation(), csvPath);
        }

    }

    private void preparePopulation(Population population, URL csvPath) {
        try (CSVReader reader = new CSVReader(csvPath, ";")) {
            log.info(csvPath);

            Set<String> attributes = new HashSet<>(Arrays.asList(reader.getColumns()));

            Map<String, String> map;
            while ((map = reader.readLine()) != null) {
                Id<Person> personId = Id.createPersonId(map.get("personId"));
                Person person = population.getPersons().get(personId);
                for (String attribute : attributes) {
                    if (person.getAttributes().getAsMap().containsKey(attribute)) {
                        throw new RuntimeException("Attribute " + attribute + " already exists. Overwriting by CSV should not be intended.");
                    }
                    person.getAttributes().putAttribute(attribute, map.get(attribute));
                }
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

    }

    private void prepare(Scenario scenario) {
        for (SBBIntermodalModeParameterSet mode : this.configGroup.getModeParameterSets()) {
            if (mode.isSimulatedOnNetwork()) {
                SBBNetworkRoutingModule.addNetworkMode(scenario.getNetwork(), mode.getMode(), SBBModes.CAR);
                Set<String> mainModes = new HashSet<>(scenario.getConfig().qsim().getMainModes());
                mainModes.add(mode.getMode());
                scenario.getConfig().qsim().setMainModes(mainModes);
            }
        }
    }

    @Override
    public void install() {
        for (SBBIntermodalModeParameterSet mode : this.configGroup.getModeParameterSets()) {
            if (mode.isRoutedOnNetwork() && !mode.getMode().equals(TransportMode.car)) {
                addTravelTimeBinding(mode.getMode()).to(networkTravelTime());
                addTravelDisutilityFactoryBinding(mode.getMode()).to(carTravelDisutilityFactoryKey());
            }
        }
        bind(IntermodalTransferTimeAnalyser.class).asEagerSingleton();
        addControlerListenerBinding().to(IntermodalControlerListener.class).asEagerSingleton();
        bind(RaptorIntermodalAccessEgress.class).to(SBBRaptorIntermodalAccessEgress.class).asEagerSingleton();
    }

}


