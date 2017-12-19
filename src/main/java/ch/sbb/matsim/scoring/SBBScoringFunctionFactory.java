package ch.sbb.matsim.scoring;

import org.apache.log4j.Logger;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.population.Person;
import org.matsim.core.scoring.ScoringFunction;
import org.matsim.core.scoring.ScoringFunctionFactory;
import org.matsim.core.scoring.SumScoringFunction;
import org.matsim.core.scoring.functions.CharyparNagelActivityScoring;
import org.matsim.core.scoring.functions.CharyparNagelAgentStuckScoring;
import org.matsim.core.scoring.functions.CharyparNagelLegScoring;
import org.matsim.core.scoring.functions.CharyparNagelMoneyScoring;
import org.matsim.core.scoring.functions.ScoringParameters;
import org.matsim.core.scoring.functions.ScoringParametersForPerson;


/**
 * @author jlie based on org.matsim.core.scoring.functions.RandomizedCharyparNagelScoringFunctionFactory
 * adding: the default ScoringFunctionFactory seems to be org.matsim.core.scoring.functions.CharyparNagelScoringFunctionFactory
 */
public class SBBScoringFunctionFactory implements ScoringFunctionFactory {

    private final static Logger log = Logger.getLogger(SBBScoringFunctionFactory.class);

    private final ScoringParametersForPerson paramsForPerson;
    private final Scenario scenario;

    public SBBScoringFunctionFactory(Scenario scenario) {
        this.scenario = scenario;
        this.paramsForPerson = new SBBCharyparNagelScoringParametersForPerson(this.scenario);
        log.info("SBBScoringFunctionFactory initialized");
    }

    @Override
    public ScoringFunction createNewScoringFunction(Person person) {
        final ScoringParameters params = paramsForPerson.getScoringParameters(person);
        SumScoringFunction sumScoringFunction = new SumScoringFunction();
        sumScoringFunction.addScoringFunction(new CharyparNagelActivityScoring(params));
        sumScoringFunction.addScoringFunction(new CharyparNagelLegScoring(params, this.scenario.getNetwork()));
        sumScoringFunction.addScoringFunction(new CharyparNagelMoneyScoring(params));
        sumScoringFunction.addScoringFunction(new CharyparNagelAgentStuckScoring(params));
        return sumScoringFunction;
    }
}
