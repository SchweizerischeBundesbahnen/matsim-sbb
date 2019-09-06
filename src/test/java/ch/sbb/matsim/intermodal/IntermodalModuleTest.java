package ch.sbb.matsim.intermodal;

import ch.sbb.matsim.config.SBBIntermodalConfigGroup;
import org.junit.Assert;
import org.junit.Test;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.population.io.PopulationReader;
import org.matsim.core.scenario.ScenarioUtils;

public class IntermodalModuleTest {

    @Test
    public void install() {
        Config config = ConfigUtils.createConfig();
        config.controler().setOutputDirectory("test/output/ch/sbb/matsim/intermodal/");
        SBBIntermodalConfigGroup intermodalConfigGroup = new SBBIntermodalConfigGroup();
        intermodalConfigGroup.setAttributesCSVPath("test/input/ch/sbb/matsim/intermodal/intermodalParams.csv");
        config.addModule(intermodalConfigGroup);
        Scenario scenario = ScenarioUtils.createScenario(config);
        new PopulationReader(scenario).readFile("test/input/scenarios/mobi20test/population.xml");
        new IntermodalModule(scenario);
        Assert.assertTrue(Boolean.parseBoolean((String) scenario.getPopulation().getPersons().get(Id.createPersonId("P_1072505")).getAttributes().getAttribute("hasBike")));

    }
}