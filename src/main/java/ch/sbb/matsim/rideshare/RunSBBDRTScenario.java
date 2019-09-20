package ch.sbb.matsim.rideshare;

import ch.sbb.matsim.RunSBB;
import ch.sbb.matsim.routing.pt.raptor.IntermodalAwareRouterModeIdentifier;
import org.apache.log4j.Logger;
import org.matsim.api.core.v01.Scenario;
import org.matsim.contrib.drt.run.*;
import org.matsim.contrib.dvrp.run.DvrpConfigGroup;
import org.matsim.contrib.dvrp.run.DvrpModule;
import org.matsim.contrib.dvrp.run.DvrpQSimComponents;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigGroup;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.controler.AbstractModule;
import org.matsim.core.controler.Controler;
import org.matsim.core.controler.OutputDirectoryHierarchy;
import org.matsim.core.router.MainModeIdentifier;
import org.matsim.core.scenario.ScenarioUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class RunSBBDRTScenario {

    private final static Logger log = Logger.getLogger(RunSBBDRTScenario.class);

    public static void main(String[] args) {
        System.setProperty("matsim.preferLocalDtds", "true");
        final String configFile = args[0];
        log.info(configFile);
        Config config = ConfigUtils.loadConfig(configFile, getSBBAndDrtConfigGroups());
        if (args.length > 1)
            config.controler().setOutputDirectory(args[1]);
        config.controler().setOverwriteFileSetting(OutputDirectoryHierarchy.OverwriteFileSetting.overwriteExistingFiles);
        prepareDrtConfig(config);
        Scenario scenario = DrtControlerCreator.createScenarioWithDrtRouteFactory(config);
        ScenarioUtils.loadScenario(scenario);
        RunSBB.addSBBDefaultScenarioModules(scenario);
        Controler controler = new Controler(scenario);
        RunSBB.addSBBDefaultControlerModules(controler);
        prepareDrtControler(controler);
        controler.run();
    }

    public static ConfigGroup[] getSBBAndDrtConfigGroups() {
        List<ConfigGroup> configGroupList = new ArrayList<>();
        configGroupList.addAll(Arrays.asList(RunSBB.sbbDefaultConfigGroups));
        configGroupList.add(new DrtConfigGroup());
        configGroupList.add(new DvrpConfigGroup());
        return configGroupList.toArray(new ConfigGroup[configGroupList.size()]);
    }

    public static void prepareDrtConfig(Config config) {
        DrtConfigGroup drtCfg = DrtConfigGroup.get(config);
        DrtConfigs.adjustDrtConfig(drtCfg, config.planCalcScore());
        config.addConfigConsistencyChecker(new DrtConfigConsistencyChecker());
        config.checkConsistency();
    }

    public static void prepareDrtControler(Controler controler) {
        controler.addOverridingModule(new DrtModule());
        controler.addOverridingModule(new DvrpModule());
        controler.configureQSimComponents(DvrpQSimComponents.activateModes(DrtConfigGroup.get(controler.getConfig()).getMode()));
        controler.addOverridingModule(new AbstractModule() {
            @Override
            public void install() {
                bind(MainModeIdentifier.class).to(IntermodalAwareRouterModeIdentifier.class);
            }
        });
    }


}
