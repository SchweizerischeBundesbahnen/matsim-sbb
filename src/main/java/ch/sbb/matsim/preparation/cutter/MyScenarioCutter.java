package ch.sbb.matsim.preparation.cutter;

import ch.sbb.matsim.RunSBB;
import ch.sbb.matsim.zones.ZonesLoader;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigWriter;
import org.matsim.core.config.groups.PlanCalcScoreConfigGroup;
import org.matsim.core.config.groups.PlansCalcRouteConfigGroup;
import org.matsim.core.config.groups.StrategyConfigGroup;
import org.matsim.core.replanning.strategies.DefaultPlanStrategiesModule;

import java.io.IOException;

public class MyScenarioCutter {
    public static void main(String[] args) throws IOException {
        String inputConfig = "\\\\k13536\\mobi\\40_Projekte\\20190805_ScenarioCutter\\20190812_thun_10pct\\config_scoring_parsed.xml";
        String inbase = "input/";
        String outputConfig = "\\\\k13536\\mobi\\40_Projekte\\20190805_ScenarioCutter\\20190812_thun_10pct\\config_scoring_cut.xml";

        CutExtent inside = new ShapeExtent(ZonesLoader.loadZones
                ("id", "\\\\k13536\\mobi\\40_Projekte\\20190805_ScenarioCutter\\20190805_zones\\thun\\thun-agglo.shp", "ID"));
        CutExtent outside = new ShapeExtent(ZonesLoader.loadZones
                ("id", "\\\\k13536\\mobi\\40_Projekte\\20190805_ScenarioCutter\\20190805_zones\\thun\\thun-umgebung.shp", "ID"));
        CutExtent network = new ShapeExtent(ZonesLoader.loadZones
                ("id", "\\\\k13536\\mobi\\40_Projekte\\20190805_ScenarioCutter\\20190805_zones\\thun\\thun-network.shp", "ID"));

        ScenarioCutter.run("\\\\k13536\\mobi\\50_Ergebnisse\\MOBi_2.0\\sim\\2.0.0_10pct_release\\output", "CH.10pct.2016", "\\\\k13536\\mobi\\40_Projekte\\20190805_ScenarioCutter\\20190812_thun_10pct\\input\\", 1.0, true, inside, outside, network);

        Config config = RunSBB.buildConfig(inputConfig);
        adjustConfig(config, inbase);
        new ConfigWriter(config).write(outputConfig);

    }

    public static void adjustConfig(Config config, String inbase) {

        config.facilities().setInputFile(inbase + "/facilities.xml.gz");

        config.network().setInputFile(inbase + "/network.xml.gz");
        config.network().setChangeEventsInputFile(inbase + "/networkChangeEvents.xml.gz");
        config.network().setTimeVariantNetwork(true);

        config.plans().setInputFile(inbase + "/population.xml.gz");
        config.plans().setInputPersonAttributeFile(inbase + "/personAttributes.xml.gz");

        config.transit().setTransitScheduleFile(inbase + "/schedule.xml.gz");
        config.transit().setVehiclesFile(inbase + "/transitVehicles.xml.gz");

        config.vehicles().setVehiclesFile(inbase + "/vehicles.xml.gz");

        config.counts().setInputFile(null);

        PlanCalcScoreConfigGroup.ActivityParams outsideParams = new PlanCalcScoreConfigGroup.ActivityParams(ScenarioCutter.OUTSIDE_ACT_TYPE);
        outsideParams.setTypicalDuration(3600);
        outsideParams.setScoringThisActivityAtAll(false);
        config.planCalcScore().addActivityParams(outsideParams);

        PlanCalcScoreConfigGroup.ModeParams outsideMode = new PlanCalcScoreConfigGroup.ModeParams(ScenarioCutter.OUTSIDE_LEG_MODE);
        outsideMode.setMarginalUtilityOfTraveling(0);
        outsideMode.setConstant(0);
        outsideMode.setMarginalUtilityOfDistance(0);
        outsideMode.setMonetaryDistanceRate(0);
        config.planCalcScore().addModeParams(outsideMode);

        PlansCalcRouteConfigGroup.ModeRoutingParams outsideRoutingParams = new PlansCalcRouteConfigGroup.ModeRoutingParams(ScenarioCutter.OUTSIDE_LEG_MODE);
        outsideRoutingParams.setBeelineDistanceFactor(1.3);
        outsideRoutingParams.setTeleportedModeFreespeedFactor(2.0);
        config.plansCalcRoute().addModeRoutingParams(outsideRoutingParams);

        StrategyConfigGroup.StrategySettings outsideStrategy = new StrategyConfigGroup.StrategySettings();
        outsideStrategy.setStrategyName(DefaultPlanStrategiesModule.DefaultSelector.KeepLastSelected);
        outsideStrategy.setWeight(1.0);
        outsideStrategy.setSubpopulation(ScenarioCutter.OUTSIDE_AGENT_SUBPOP);
        config.strategy().addStrategySettings(outsideStrategy);
    }




}
