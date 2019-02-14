/*
 * Copyright (C) Schweizerische Bundesbahnen SBB, 2017.
 */

package ch.sbb.matsim;


import ch.sbb.matsim.analysis.SBBPostProcessingOutputHandler;
import ch.sbb.matsim.config.*;
import ch.sbb.matsim.config.variables.SBBActivities;
import ch.sbb.matsim.config.variables.Variables;
import ch.sbb.matsim.mobsim.qsim.SBBTransitModule;
import ch.sbb.matsim.mobsim.qsim.pt.SBBTransitEngineQSimModule;
import ch.sbb.matsim.preparation.PopulationSampler.SBBPopulationSampler;
import ch.sbb.matsim.replanning.SBBTimeAllocationMutatorReRoute;
import ch.sbb.matsim.routing.access.AccessEgress;
import ch.sbb.matsim.routing.pt.raptor.SwissRailRaptorModule;
import ch.sbb.matsim.scoring.SBBScoringFunctionFactory;
import com.google.inject.Provides;
import org.apache.log4j.Logger;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.population.Activity;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.Plan;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.controler.AbstractModule;
import org.matsim.core.controler.Controler;
import org.matsim.core.mobsim.qsim.components.QSimComponentsConfig;
import org.matsim.core.mobsim.qsim.components.StandardQSimComponentConfigurator;
import org.matsim.core.router.TripStructureUtils;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.core.scoring.ScoringFunctionFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * @author denism
 *
 */
public class RunSBB {

    private final static Logger log = Logger.getLogger(RunSBB.class);

    public static void main(String[] args) {
        System.setProperty("matsim.preferLocalDtds", "true");

        final String configFile = args[0];
        log.info(configFile);
        final Config config = buildConfig(configFile);

        if(args.length > 1)
            config.controler().setOutputDirectory(args[1]);

        Scenario scenario = ScenarioUtils.loadScenario(config);
        createInitialEndTimeAttribute(scenario);

        Controler controler = new Controler(scenario);

        SBBPopulationSamplerConfigGroup samplerConfig = ConfigUtils.addOrGetModule(scenario.getConfig(), SBBPopulationSamplerConfigGroup.class);
        if(samplerConfig.getDoSample()){
            SBBPopulationSampler sbbPopulationSampler = new SBBPopulationSampler();
            sbbPopulationSampler.sample(scenario.getPopulation(), samplerConfig.getFraction());
        }

        ScoringFunctionFactory scoringFunctionFactory = new SBBScoringFunctionFactory(scenario);
        controler.setScoringFunctionFactory(scoringFunctionFactory);

        controler.addOverridingModule(new AbstractModule() {
            @Override
            public void install() {
                this.addControlerListenerBinding().to(SBBPostProcessingOutputHandler.class);
            }
        });

        controler.addOverridingModule(new AbstractModule() {
            @Override
            public void install() {
                addPlanStrategyBinding("SBBTimeMutation_ReRoute").toProvider(SBBTimeAllocationMutatorReRoute.class);

                addTravelTimeBinding("ride").to(networkTravelTime());
                addTravelDisutilityFactoryBinding("ride").to(carTravelDisutilityFactoryKey());

                install(new SBBTransitModule());
                install(new SwissRailRaptorModule());
            }

            @Provides
            QSimComponentsConfig provideQSimComponentsConfig() {
                QSimComponentsConfig components = new QSimComponentsConfig();
                new StandardQSimComponentConfigurator(config).configure(components);
                SBBTransitEngineQSimModule.configure(components);
                return components;
            }
        });

        new AccessEgress(controler).installAccessTime();

        controler.run();
    }

    public static Config buildConfig(String filepath) {
        return ConfigUtils.loadConfig(filepath, new PostProcessingConfigGroup(), new SBBTransitConfigGroup(),
                new SBBBehaviorGroupsConfigGroup(),new SBBPopulationSamplerConfigGroup(), new SwissRailRaptorConfigGroup());
    }

    public static void createInitialEndTimeAttribute(Scenario scenario) {
        for(Person p: scenario.getPopulation().getPersons().values())   {
            if(p.getAttributes().getAttribute(Variables.INIT_END_TIMES) != null)
                continue;

            if(p.getPlans().size() > 1) {
                log.info("Person " + p.getId().toString() + " has more than one plan. Taking selected plan...");
            }
            Plan plan = p.getSelectedPlan();
            List<Activity> activities = TripStructureUtils.getActivities(plan, SBBActivities.stageActivitiesTypes);
            List<String> endTimeList = new ArrayList<>();
            int i = 0;

            for(Activity act: activities)   {
                if(i == activities.size() - 1) break;
                endTimeList.add(Double.toString(act.getEndTime()));
                i += 1;
            }

            p.getAttributes().putAttribute(Variables.INIT_END_TIMES, String.join("_", endTimeList) );
        }
    }
}
