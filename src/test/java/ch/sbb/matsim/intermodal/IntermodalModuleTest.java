package ch.sbb.matsim.intermodal;

import ch.sbb.matsim.config.SBBIntermodalConfiggroup;
import ch.sbb.matsim.preparation.casestudies.AddIntermodalAttributes;
import org.junit.Assert;
import org.junit.Test;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigGroup;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.population.io.PopulationReader;
import org.matsim.core.scenario.ScenarioUtils;

public class IntermodalModuleTest {

	@Test(expected = RuntimeException.class)
	public void install() {
        Config config = ConfigUtils.createConfig();
        config.controler().setOutputDirectory("test/output/ch/sbb/matsim/intermodal/");
        SBBIntermodalConfiggroup intermodalConfigGroup = new SBBIntermodalConfiggroup();
        config.addModule(intermodalConfigGroup);
        Scenario scenario = ScenarioUtils.createScenario(config);
        new PopulationReader(scenario).readFile("test/input/scenarios/mobi20test/population.xml");
        IntermodalModule.prepareIntermodalScenario(scenario);
        AddIntermodalAttributes.preparePopulation(scenario.getPopulation(), ConfigGroup.getInputFileURL(config.getContext(), "test/input/ch/sbb/matsim/intermodal/intermodalParams.csv"));
        Assert.assertTrue(Boolean.parseBoolean((String) scenario.getPopulation().getPersons().get(Id.createPersonId("P_1072505")).getAttributes().getAttribute("hasBike")));
        // a second call should throw an exception
        IntermodalModule.prepareIntermodalScenario(scenario);

    }
}