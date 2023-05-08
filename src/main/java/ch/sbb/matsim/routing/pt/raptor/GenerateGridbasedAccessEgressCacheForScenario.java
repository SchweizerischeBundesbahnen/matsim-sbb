package ch.sbb.matsim.routing.pt.raptor;

import ch.sbb.matsim.RunSBB;
import org.matsim.api.core.v01.Scenario;
import org.matsim.core.config.Config;
import org.matsim.core.scenario.ScenarioUtils;

public class GenerateGridbasedAccessEgressCacheForScenario {

    public static void main(String[] args) {
        Config config = RunSBB.buildConfig(args[0]);

        Scenario scenario = ScenarioUtils.loadScenario(config);
        RunSBB.addSBBDefaultScenarioModules(scenario);
        GridbasedAccessEgressCache cache = new GridbasedAccessEgressCache(scenario);
        cache.calculateGridTraveltimesViaTree();
        cache.writeCache(args[1]);

    }
}
