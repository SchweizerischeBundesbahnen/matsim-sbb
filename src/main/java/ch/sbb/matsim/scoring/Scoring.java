/*
 * Copyright (C) Schweizerische Bundesbahnen SBB, 2017.
 */
package ch.sbb.matsim.scoring;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.population.Activity;
import org.matsim.api.core.v01.population.Person;
import org.matsim.core.config.groups.PlanCalcScoreConfigGroup;
import org.matsim.core.config.groups.ScenarioConfigGroup;
import org.matsim.core.router.StageActivityTypes;
import org.matsim.core.router.TripStructureUtils;
import org.matsim.core.scoring.ScoringFunction;
import org.matsim.core.scoring.ScoringFunctionFactory;
import org.matsim.core.scoring.SumScoringFunction;
import org.matsim.core.scoring.functions.ActivityUtilityParameters;
import org.matsim.core.scoring.functions.CharyparNagelLegScoring;
import org.matsim.core.scoring.functions.CharyparNagelScoringParameters;
import org.matsim.utils.objectattributes.ObjectAttributes;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class Scoring implements  ScoringFunctionFactory{
    private final Scenario scenario;
    private final StageActivityTypes blackList;
    private final Map<Id, CharyparNagelScoringParameters> individualParameters = new HashMap<>();

    public Scoring(final Scenario scenario, final StageActivityTypes typesNotToScore) {
        this.scenario = scenario;
        this.blackList = typesNotToScore;
    }

    @Override
    public ScoringFunction createNewScoringFunction(Person person) {
        final SumScoringFunction scoringFunctionAccumulator = new SumScoringFunction();

        final PlanCalcScoreConfigGroup config = scenario.getConfig().planCalcScore();
        final ObjectAttributes personAttributes = scenario.getPopulation().getPersonAttributes();

        final CharyparNagelScoringParameters params = createParams(person, config, scenario.getConfig().scenario(), personAttributes);
        scoringFunctionAccumulator.addScoringFunction(new CharyparNagelLegScoring(params, scenario.getNetwork()));
        return scoringFunctionAccumulator;
    }


    private CharyparNagelScoringParameters createParams(
            final Person person,
            final PlanCalcScoreConfigGroup config,
            final ScenarioConfigGroup scenarioConfig,
            final ObjectAttributes personAttributes) {
        if (individualParameters.containsKey(person.getId())) {
            return individualParameters.get(person.getId());
        }

        final CharyparNagelScoringParameters.Builder builder = new CharyparNagelScoringParameters.Builder(config, config.getScoringParameters(null), scenarioConfig);
        final Set<String> handledTypes = new HashSet<>();
        for (Activity act : TripStructureUtils.getActivities(person.getSelectedPlan(), blackList)) {
            // XXX works only if no variation of type of activities between plans
            if (!handledTypes.add(act.getType()))
                continue; // parameters already gotten

            final String id = person.getId().toString();

            // I am not so pleased with this, as wrong parameters may silently be
            // used (for instance if individual preferences are ill-specified).
            // This should become nicer once we have a better format for specifying
            // utility parameters in the config.
            final ActivityUtilityParameters.Builder typeBuilder = new ActivityUtilityParameters.Builder(
                    config.getActivityParams(act.getType()) != null ? config.getActivityParams(act.getType()) : new PlanCalcScoreConfigGroup.ActivityParams(act.getType()));

            final Double earliestEndTime = (Double) personAttributes.getAttribute(
                    id,
                    "earliestEndTime_" + act.getType());
            if (earliestEndTime != null) {
                typeBuilder.setScoreAtAll(true);
                typeBuilder.setEarliestEndTime(earliestEndTime);
            }

            final Double latestStartTime = (Double) personAttributes.getAttribute(
                    id,
                    "latestStartTime_" + act.getType());
            if (latestStartTime != null) {
                typeBuilder.setScoreAtAll(true);
                typeBuilder.setLatestStartTime(latestStartTime);
            }

            final Double minimalDuration = (Double) personAttributes.getAttribute(
                    id,
                    "minimalDuration_" + act.getType());
            if (minimalDuration != null) {
                typeBuilder.setScoreAtAll(true);
                typeBuilder.setMinimalDuration(minimalDuration);
            }

            final Double typicalDuration = (Double) personAttributes.getAttribute(
                    id,
                    "typicalDuration_" + act.getType());
            if (typicalDuration != null) {
                typeBuilder.setScoreAtAll(true);
                typeBuilder.setTypicalDuration_s(typicalDuration);
            }

            builder.setActivityParameters(
                    act.getType(),
                    typeBuilder);
        }

        final CharyparNagelScoringParameters params = builder.build();
        individualParameters.put(person.getId(), params);
        return params;
    }

}
