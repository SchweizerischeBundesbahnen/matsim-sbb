package ch.sbb.matsim.scoring;

import org.apache.log4j.Logger;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.population.Person;
import org.matsim.core.scoring.ScoringFunction;
import org.matsim.core.scoring.ScoringFunctionFactory;
import org.matsim.core.scoring.SumScoringFunction;
import org.matsim.core.scoring.functions.CharyparNagelAgentStuckScoring;
import org.matsim.core.scoring.functions.CharyparNagelMoneyScoring;
import org.matsim.core.scoring.functions.ScoringParameters;

import java.util.Set;


/**
 * @author jlie based on org.matsim.core.scoring.functions.RandomizedCharyparNagelScoringFunctionFactory
 * adding: the default ScoringFunctionFactory seems to be org.matsim.core.scoring.functions.CharyparNagelScoringFunctionFactory
 */
public class SBBScoringFunctionFactory implements ScoringFunctionFactory {

    private final static Logger log = Logger.getLogger(SBBScoringFunctionFactory.class);

    private final SBBCharyparNagelScoringParametersForPerson paramsForPerson;
    private final Scenario scenario;

    public SBBScoringFunctionFactory(Scenario scenario) {
        this.scenario = scenario;
        this.paramsForPerson = new SBBCharyparNagelScoringParametersForPerson(this.scenario);
        log.info("SBBScoringFunctionFactory initialized");
    }

    @Override
    public ScoringFunction createNewScoringFunction(Person person) {
        Set<String> ptModes = this.scenario.getConfig().transit().getTransitModes();
        final SBBScoringParameters sbbParams = this.paramsForPerson.getSBBScoringParameters(person);
        final ScoringParameters params = sbbParams.getMatsimScoringParameters();
        SumScoringFunction sumScoringFunction = new SumScoringFunction();
        sumScoringFunction.addScoringFunction(new SBBActivityScoring(params));
        sumScoringFunction.addScoringFunction(new SBBCharyparNagelLegScoring(params, this.scenario.getNetwork(), ptModes));
        sumScoringFunction.addScoringFunction(new CharyparNagelMoneyScoring(params));
        sumScoringFunction.addScoringFunction(new CharyparNagelAgentStuckScoring(params));
        sumScoringFunction.addScoringFunction(new SBBParkingCostScoring(sbbParams.getMarginalUtilityOfParkingPrice()));
        sumScoringFunction.addScoringFunction(new SBBTransferScoring(sbbParams, ptModes));
        return sumScoringFunction;
    }
}
