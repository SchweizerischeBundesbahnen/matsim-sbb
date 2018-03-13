package ch.sbb.matsim.utils;

import org.matsim.api.core.v01.Scenario;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.controler.Controler;
import org.matsim.core.scenario.ScenarioUtils;
import ch.sbb.matsim.analysis.NetworkToVisumNetFile;
import ch.sbb.matsim.config.PostProcessingConfigGroup;

public class RunNetworkToVisumNetFile {

    public static void main(String[] args) {
        final String configFile = args[0];
        final Config config = ConfigUtils.loadConfig(configFile, new PostProcessingConfigGroup());
        Scenario scenario = ScenarioUtils.loadScenario(config);
        Controler controler = new Controler(scenario);
        //System.out.println(controler.getConfig().controler().getOutputDirectory());
        PostProcessingConfigGroup ppConfig = ConfigUtils.addOrGetModule(scenario.getConfig(), PostProcessingConfigGroup.class);
        NetworkToVisumNetFile networkToVisum = new NetworkToVisumNetFile(scenario, ppConfig);
        networkToVisum.write(controler.getConfig().controler().getOutputDirectory() + "/");
    }
}
