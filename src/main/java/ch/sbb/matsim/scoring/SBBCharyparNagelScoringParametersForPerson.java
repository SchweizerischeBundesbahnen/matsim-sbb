package ch.sbb.matsim.scoring;

import ch.sbb.matsim.config.SBBBehaviorGroupsConfigGroup;
import org.apache.log4j.Logger;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.Population;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.config.groups.PlanCalcScoreConfigGroup;
import org.matsim.core.config.groups.PlansConfigGroup;
import org.matsim.core.config.groups.ScenarioConfigGroup;
import org.matsim.core.scoring.functions.ModeUtilityParameters;
import org.matsim.core.scoring.functions.ScoringParameters;
import org.matsim.core.scoring.functions.ScoringParametersForPerson;
import org.matsim.pt.config.TransitConfigGroup;
import org.matsim.utils.objectattributes.ObjectAttributes;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * @author jlie/pmanser / SBB
 * based on org.matsim.core.scoring.functions.RandomizedCharyparNagelScoringParameters
 *
 * extended the code to allow customized personal scoring parameters depending on different behaviorally
 * homogeneous groups.
 *
 */

public class SBBCharyparNagelScoringParametersForPerson implements ScoringParametersForPerson {

    private final static Logger log = Logger.getLogger(SBBCharyparNagelScoringParametersForPerson.class);

    private final PlanCalcScoreConfigGroup config;
    private final ScenarioConfigGroup scConfig;
    private final Map<Person, ScoringParameters> paramsPerPerson = new LinkedHashMap<>();
    private final ObjectAttributes personAttributes;
    private final String subpopulationAttributeName;
    private final TransitConfigGroup transitConfigGroup;
    private final SBBBehaviorGroupsConfigGroup behaviorGroupsConfigGroup;

    public SBBCharyparNagelScoringParametersForPerson(Scenario scenario) {
        this(scenario.getConfig().plans(),
                scenario.getConfig().planCalcScore(),
                scenario.getConfig().scenario(),
                scenario.getPopulation(),
                scenario.getConfig().transit(),
                ConfigUtils.addOrGetModule(scenario.getConfig(), SBBBehaviorGroupsConfigGroup.class));
    }

    SBBCharyparNagelScoringParametersForPerson  (
            PlansConfigGroup plansConfigGroup,
            PlanCalcScoreConfigGroup planCalcScoreConfigGroup,
            ScenarioConfigGroup scenarioConfigGroup,
            Population population,
            TransitConfigGroup transitConfigGroup,
            SBBBehaviorGroupsConfigGroup behaviorGroupsConfigGroup) {
        this.config = planCalcScoreConfigGroup;
        this.scConfig = scenarioConfigGroup;
        this.personAttributes = population.getPersonAttributes();
        this.subpopulationAttributeName = plansConfigGroup.getSubpopulationAttributeName();
        this.transitConfigGroup = transitConfigGroup;
        this.behaviorGroupsConfigGroup = behaviorGroupsConfigGroup;
    }

    @Override
    public ScoringParameters getScoringParameters(Person person) {
        if (!this.paramsPerPerson.containsKey(person)) {
            final String subpopulation = (String) personAttributes.getAttribute(person.getId().toString(), subpopulationAttributeName);

            ScoringParameters.Builder builder = new ScoringParameters.Builder(
                    this.config, this.config.getScoringParameters(subpopulation),
                    scConfig);

            // building the customized scoring parameters for each person depending on his behavior group
            for(String mode: this.config.getModes().keySet()) {
                final PlanCalcScoreConfigGroup.ModeParams defaultModeParams = this.config.getModes().get(mode);
                final ModeUtilityParameters.Builder modeParameteresBuilder = new ModeUtilityParameters.Builder(defaultModeParams);

                double constant = defaultModeParams.getConstant();
                double margUtilTime = defaultModeParams.getMarginalUtilityOfTraveling();
                double margUtilDistance = defaultModeParams.getMarginalUtilityOfDistance();
                double monDistRate = defaultModeParams.getMonetaryDistanceRate();

                for (SBBBehaviorGroupsConfigGroup.BehaviorGroupParams bgp : behaviorGroupsConfigGroup.getBehaviorGroupParams().values()) {
                    Object personAttributeObj = person.getAttributes().getAttribute(bgp.getPersonAttribute());
                    if (personAttributeObj == null) continue;

                    String personAttribute;
                    if(personAttributeObj instanceof Integer)
                        personAttribute = Integer.toString((int) personAttributeObj);
                    else if(personAttributeObj instanceof Double)
                        personAttribute = Double.toString((double) personAttributeObj);
                    else
                        personAttribute = (String) personAttributeObj;

                    SBBBehaviorGroupsConfigGroup.PersonGroupValues pgt = bgp.getPersonGroupByAttribute(personAttribute);
                    if(pgt == null) continue;

                    SBBBehaviorGroupsConfigGroup.ModeCorrection modeCorrection = pgt.getModeCorrectionsForMode(mode);
                    if(modeCorrection == null)  continue;

                    constant += modeCorrection.getConstant();
                    margUtilTime += modeCorrection.getMargUtilOfTime();
                    margUtilDistance += modeCorrection.getMargUtilOfDistance();
                    monDistRate += modeCorrection.getDistanceRate();
                }

                modeParameteresBuilder.setConstant(constant);
                modeParameteresBuilder.setMarginalUtilityOfDistance_m(margUtilDistance);
                modeParameteresBuilder.setMarginalUtilityOfTraveling_s(margUtilTime / 3600);
                modeParameteresBuilder.setMonetaryDistanceRate(monDistRate);
                builder.setModeParameters(mode, modeParameteresBuilder);
            }
            ScoringParameters params = builder.build();
            this.paramsPerPerson.put(person, params);
        }
        return this.paramsPerPerson.get(person);
    }
}
