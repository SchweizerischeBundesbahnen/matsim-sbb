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
        System.out.println(controler.getConfig().controler().getOutputDirectory());
        PostProcessingConfigGroup ppConfig = (PostProcessingConfigGroup) scenario.getConfig().getModule(PostProcessingConfigGroup.GROUP_NAME);
        NetworkToVisumNetFile networkToVisum = new NetworkToVisumNetFile(scenario, controler.getConfig().controler().getOutputDirectory(), ppConfig);
        networkToVisum.write(controler.getConfig().controler().getOutputDirectory() + "net.net");
    }
}
