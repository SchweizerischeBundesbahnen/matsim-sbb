package ch.sbb.matsim.preparation.cutter;

import ch.sbb.matsim.RunSBB;
import org.matsim.api.core.v01.Scenario;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigWriter;
import org.matsim.core.config.groups.PlanCalcScoreConfigGroup;
import org.matsim.core.config.groups.PlansCalcRouteConfigGroup;
import org.matsim.core.config.groups.StrategyConfigGroup;
import org.matsim.core.replanning.strategies.DefaultPlanStrategiesModule;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.utils.objectattributes.ObjectAttributesXmlReader;
import org.matsim.utils.objectattributes.ObjectAttributesXmlWriter;

import java.io.File;

public class PrepareCutScenario {

    public static void main(String[] args) {
        String inputConfig = "\\\\k13536\\mobi\\40_Projekte\\20190805_ScenarioCutter\\20190812_thun_10pct\\config_scoring_parsed.xml";
        String inbase = "input/";
        String outputConfig = "\\\\k13536\\mobi\\40_Projekte\\20190805_ScenarioCutter\\20190812_thun_10pct\\config_scoring_cut.xml";

        Config config = RunSBB.buildConfig(inputConfig);
        adjustConfig(config, inbase);
        adjustPopulationAttributes(config);



        new ConfigWriter(config).write(outputConfig);


    }

    private static void adjustConfig(Config config, String inbase) {

        config.facilities().setInputFile(inbase + "facilities.xml.gz");

        config.network().setInputFile(inbase + "network.xml.gz");
        config.network().setChangeEventsInputFile(inbase + "networkChangeEvents.xml.gz");
        config.network().setTimeVariantNetwork(true);

        config.plans().setInputFile(inbase + "population.xml.gz");
        config.plans().setInputPersonAttributeFile(inbase + "personAttributes.xml.gz");

        config.transit().setTransitScheduleFile(inbase + "schedule.xml.gz");
        config.transit().setVehiclesFile(inbase + "transitVehicles.xml.gz");

        config.vehicles().setVehiclesFile(inbase + "vehicles.xml.gz");

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

    private static void adjustPopulationAttributes(Config config) {
        Scenario scenario = ScenarioUtils.createScenario(config);
        BetterPopulationReader.readSelectedPlansOnly(scenario, new File(config.plans().getInputFileURL(config.getContext()).getFile()));
        new ObjectAttributesXmlReader(scenario.getPopulation().getPersonAttributes()).readURL(config.plans().getInputPersonAttributeFileURL(config.getContext()));
        scenario.getPopulation().getPersons().values().stream()
                .filter(ScenarioCutter.isCut())
                .forEach(person ->
                        scenario.getPopulation().getPersonAttributes()
                                .putAttribute(person.getId().toString(), config.plans().getSubpopulationAttributeName(), ScenarioCutter.OUTSIDE_AGENT_SUBPOP));

        new ObjectAttributesXmlWriter(scenario.getPopulation().getPersonAttributes()).writeFile(config.plans().getInputPersonAttributeFileURL(config.getContext()).getFile());

    }
}
