package ch.sbb.matsim.preparation.cutter;

import ch.sbb.matsim.RunSBB;
import ch.sbb.matsim.zones.ZonesLoader;
import org.apache.log4j.Logger;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigWriter;
import org.matsim.core.config.groups.PlanCalcScoreConfigGroup;
import org.matsim.core.config.groups.PlansCalcRouteConfigGroup;
import org.matsim.core.config.groups.StrategyConfigGroup;
import org.matsim.core.replanning.strategies.DefaultPlanStrategiesModule;

import java.io.IOException;

public class SBBScenarioCutter {

    /*
     *
     */
    public static void main(String[] args) throws IOException {

        String inputConfig;
        String newInputRelativeToNewConfig;
        String newConfig;
        String innerExtentShapeFile;
        String networkExtentShapeFile;
        String outerExtentShapeFile;
        String originalRunDirectory;

        String newRunId;
        String originalRunId;
        double newScenarioSampleSize;
        boolean parseEvents;

        if (args.length == 11) {
            Logger.getLogger(SBBScenarioCutter.class).info("Will use input files defined by args!");
            inputConfig = args[0];
            newConfig = args[1];
            newInputRelativeToNewConfig = args[2];

            innerExtentShapeFile = args[3];
            networkExtentShapeFile = args[4];
            outerExtentShapeFile = args[5];

            originalRunDirectory = args[6];
            originalRunId = args[7];

            newRunId = args[8];
            newScenarioSampleSize = Double.parseDouble(args[9]);
            parseEvents = Boolean.parseBoolean(args[10]);

        } else {

            Logger.getLogger(SBBScenarioCutter.class).info("Will use input files defined in code!");
            inputConfig = "\\\\k13536\\mobi\\40_Projekte\\20190805_ScenarioCutter\\20190815_thun_10pct\\config_scoring_parsed.xml";
            newConfig = "\\\\k13536\\mobi\\40_Projekte\\20190805_ScenarioCutter\\20190815_thun_10pct\\config_scoring_cut.xml";
            newInputRelativeToNewConfig = "input/";

            innerExtentShapeFile = "\\\\k13536\\mobi\\40_Projekte\\20190805_ScenarioCutter\\20190805_zones\\thun\\thun-agglo.shp";
            networkExtentShapeFile = "\\\\k13536\\mobi\\40_Projekte\\20190805_ScenarioCutter\\20190805_zones\\thun\\thun-network.shp";
            outerExtentShapeFile = "\\\\k13536\\mobi\\40_Projekte\\20190805_ScenarioCutter\\20190805_zones\\thun\\thun-umgebung.shp";

            originalRunDirectory = "\\\\k13536\\mobi\\50_Ergebnisse\\MOBi_2.0\\sim\\2.0.0_10pct_release\\output";
            originalRunId = "CH.10pct.2016";

            newRunId = "thun";
            newScenarioSampleSize = 1.0;
            parseEvents = false;
        }


        final String zonesIdAttribute = "ID";
        final String zonesId = "id";
        CutExtent inside = new ShapeExtent(ZonesLoader.loadZones
                (zonesId, innerExtentShapeFile, zonesIdAttribute));

        CutExtent outside = new ShapeExtent(ZonesLoader.loadZones
                (zonesId, outerExtentShapeFile, zonesIdAttribute));

        CutExtent network = new ShapeExtent(ZonesLoader.loadZones
                (zonesId, networkExtentShapeFile, zonesIdAttribute));

        Config config = RunSBB.buildConfig(inputConfig);

        String cutterOutputDirectory = config.getContext().getFile() + "/" + newInputRelativeToNewConfig;

        ScenarioCutter.run(originalRunDirectory, originalRunId, cutterOutputDirectory, newScenarioSampleSize, parseEvents, inside, outside, network);


        config.controler().setRunId(newRunId);
        adjustConfig(config, newInputRelativeToNewConfig);
        new ConfigWriter(config).write(newConfig);

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

        // fix cut plans (no innovation, scoring parameters do not matter)
        PlanCalcScoreConfigGroup.ActivityParams outsideParams = new PlanCalcScoreConfigGroup.ActivityParams(ScenarioCutter.OUTSIDE_ACT_TYPE);
        outsideParams.setTypicalDuration(3600);
        outsideParams.setScoringThisActivityAtAll(false);
        config.planCalcScore().addActivityParams(outsideParams);

        StrategyConfigGroup.StrategySettings outsideStrategy = new StrategyConfigGroup.StrategySettings();
        outsideStrategy.setStrategyName(DefaultPlanStrategiesModule.DefaultSelector.KeepLastSelected);
        outsideStrategy.setWeight(1.0);
        outsideStrategy.setSubpopulation(ScenarioCutter.OUTSIDE_AGENT_SUBPOP);
        config.strategy().addStrategySettings(outsideStrategy);

        //these values are not required, but neccessary for the simulation to run
        PlanCalcScoreConfigGroup.ModeParams outsideMode = new PlanCalcScoreConfigGroup.ModeParams(ScenarioCutter.OUTSIDE_LEG_MODE);
        outsideMode.setMarginalUtilityOfTraveling(0);
        outsideMode.setConstant(0);
        outsideMode.setMarginalUtilityOfDistance(0);
        outsideMode.setMonetaryDistanceRate(0);
        config.planCalcScore().addModeParams(outsideMode);

        PlansCalcRouteConfigGroup.ModeRoutingParams outsideRoutingParams = new PlansCalcRouteConfigGroup.ModeRoutingParams(ScenarioCutter.OUTSIDE_LEG_MODE);
        outsideRoutingParams.setBeelineDistanceFactor(1.3);
        outsideRoutingParams.setTeleportedModeSpeed(8.0);
        config.plansCalcRoute().addModeRoutingParams(outsideRoutingParams);

    }




}
