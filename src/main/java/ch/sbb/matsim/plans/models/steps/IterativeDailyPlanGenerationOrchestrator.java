package ch.sbb.matsim.plans.models.steps;

import ch.sbb.matsim.plans.MOBiPlansRunner;
import jakarta.inject.Inject;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.matsim.api.core.v01.Scenario;

public class IterativeDailyPlanGenerationOrchestrator implements ModelStep {

    public static final String NEEDS_DAILY_REPLANNING = "needs_daily_replanning";
    private final TourBuildingModel tourBuildingModel;
    private final CombinedDestinationAndDurationChoiceModel destDurModel;
    private final Scenario scenario;
    private static final Logger log = LogManager.getLogger(IterativeDailyPlanGenerationOrchestrator.class);
    private static final double[] BUD_TRAVEL = {12.0, 5.0, 4.0, 3.0};
    private static final double[] BUD_PERF = {14.0, 12.0, 11.0, 10.0};
    private static final double[] BUD_OOH = {13.5, 14.0, 15.0, 16.5};

    @Inject
    public IterativeDailyPlanGenerationOrchestrator(TourBuildingModel tourBuildingModel, CombinedDestinationAndDurationChoiceModel destDurModel, Scenario scenario) {
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

            // 2. Dest/Dur Choice (Checks budgets based on 'replanning_iteration')
            // If they pass, it sets needs_daily_replanning = false.
            // If they fail, it increments replanning_iteration.
            destDurModel.runIteration(BUD_PERF[iter],BUD_TRAVEL[iter],BUD_OOH[iter] );
        }

        long failedAgents = scenario.getPopulation().getPersons().values().stream()
                .filter(p -> Boolean.TRUE.equals(p.getAttributes().getAttribute(NEEDS_DAILY_REPLANNING)))
                .count();
        if (failedAgents > 0) {
            log.warn("{} agents failed to find a valid daily plan after {} iterations. Keeping their best fallback plan.", failedAgents, maxIterations);
        }
        destDurModel.cleanup();



    }



    @Override
    public String getName() {
        return MOBiPlansRunner.ITERATTIVE_DAILY_PLAN_GENERATION;
    }
}