package ch.sbb.matsim.plans.models.steps;

import ch.sbb.matsim.config.variables.SBBActivities;
import ch.sbb.matsim.plans.MOBiPlansRunner;
import ch.sbb.matsim.plans.models.config.MOBiPlansConfig;
import ch.sbb.matsim.plans.tours.Tour;
import jakarta.inject.Inject;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.population.Activity;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.Plan;
import org.matsim.api.core.v01.population.PopulationFactory;
import org.matsim.core.population.PopulationUtils;

import java.util.List;

public class IterativeDailyPlanGenerationOrchestrator implements ModelStep {

    public static final String NEEDS_DAILY_REPLANNING = "needs_daily_replanning";
    private final MOBiPlansConfig mobiPlansConfig;
    private final TourBuildingModel tourBuildingModel;
    private final CombinedDestinationAndDurationChoiceModel destDurModel;
    private final Scenario scenario;
    private static final Logger log = LogManager.getLogger(IterativeDailyPlanGenerationOrchestrator.class);
    private static final double[] BUD_TRAVEL = {12.0, 5.0, 4.0, 3.0};
    private static final double[] BUD_PERF = {14.0, 12.0, 11.0, 10.0};
    private static final double[] BUD_OOH = {13.5, 14.0, 15.0, 16.5};

    @Inject
    public IterativeDailyPlanGenerationOrchestrator(MOBiPlansConfig moBiPlansConfig, TourBuildingModel tourBuildingModel, CombinedDestinationAndDurationChoiceModel destDurModel, Scenario scenario) {
        this.mobiPlansConfig = moBiPlansConfig;
        this.tourBuildingModel = tourBuildingModel;
            this.destDurModel = destDurModel;
            this.scenario = scenario;
        }

    @Override
    public void run() {
        scenario.getPopulation().getPersons().values().forEach(p -> {
            p.getAttributes().putAttribute(NEEDS_DAILY_REPLANNING, true);
        });

        int maxIterations = 4;
        destDurModel.prepare();
        for (int iter = 0; iter < maxIterations; iter++) {
            long remainingAgents = scenario.getPopulation()
                    .getPersons()
                    .values()
                    .stream()
                    .filter(p -> Boolean.TRUE.equals(p.getAttributes().getAttribute(NEEDS_DAILY_REPLANNING)))
                    .count();
            if (remainingAgents == 0) {
                log.info("All agents successfully planned at iteration {}. Breaking loop.", iter);
                break;
            }
            
            log.info("--- Starting Daily Plan Iteration {} for {} agents ---", iter + 1, remainingAgents);

            // 1. Tour Building (Only processes agents with needs_daily_replanning == true)
            tourBuildingModel.runIteration();
            log.info("Finished Tour Building for iteration {}. Starting Destination and Duration Choice.", iter + 1);
            // 2. Dest/Dur Choice (Checks budgets based on 'replanning_iteration')
            // If they pass, it sets needs_daily_replanning = false.
            // If they fail, it increments replanning_iteration.
            destDurModel.runIteration(BUD_PERF[iter],BUD_TRAVEL[iter],BUD_OOH[iter] );
        }
        if (this.mobiPlansConfig.models().write_analysis()){
            tourBuildingModel.writeTourDetails(mobiPlansConfig.data().outputBaseDir()+"/tour_building_model_output.csv.gz");
            tourBuildingModel.writeTourStats(mobiPlansConfig.data().outputBaseDir()+"/tour_building_model_stats.csv.gz");
            tourBuildingModel.printTourStats();
        }

        long failedAgents = scenario.getPopulation().getPersons().values().stream()
                .filter(p -> Boolean.TRUE.equals(p.getAttributes().getAttribute(NEEDS_DAILY_REPLANNING)))
                .count();
        if (failedAgents > 0) {
            log.warn("{} agents failed to find a valid daily plan after {} iterations. Keeping their best fallback plan.", failedAgents, maxIterations);
        }
        destDurModel.cleanup();
        PopulationFactory populationFactory = PopulationUtils.getFactory();

        for (Person person : scenario.getPopulation().getPersons().values()) {
            person.getAttributes().removeAttribute(NEEDS_DAILY_REPLANNING);
            Plan plan = populationFactory.createPlan();
            person.addPlan(plan);
            var tours = (List<Tour>) person.getAttributes().getAttribute("tours");
            if (tours!=null) {

                for (Tour tour : tours) {
                    plan.addActivity(populationFactory.createActivityFromCoord(SBBActivities.home, null));
                    for (Activity activity : tour.getOutboundStopActivities()) {
                        plan.addActivity(activity);
                    }
                    plan.addActivity(tour.getMainActivities().get(0));

                    if (tour.getSubtourActivities().size() > 0) {
                        for (Activity subtourActivity : tour.getSubtourActivities()) {
                            plan.addActivity(subtourActivity);
                        }
                        plan.addActivity(tour.getMainActivities().get(1));
                    }
                    for (Activity activity : tour.getInboundStopActivities()) {
                        plan.addActivity(activity);
                    }
                }
                plan.addActivity(populationFactory.createActivityFromCoord(SBBActivities.home, null));

            }
        }


    }



    @Override
    public String getName() {
        return MOBiPlansRunner.ITERATTIVE_DAILY_PLAN_GENERATION;
    }
}