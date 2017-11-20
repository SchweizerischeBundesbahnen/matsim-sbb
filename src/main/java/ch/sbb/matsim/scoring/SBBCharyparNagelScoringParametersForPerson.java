package ch.sbb.matsim.scoring;

import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.log4j.Logger;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.Population;
import org.matsim.core.config.groups.PlanCalcScoreConfigGroup;
import org.matsim.core.config.groups.PlansConfigGroup;
import org.matsim.core.config.groups.ScenarioConfigGroup;
import org.matsim.core.scoring.functions.ActivityUtilityParameters;
import org.matsim.core.scoring.functions.ScoringParameters;
import org.matsim.core.scoring.functions.ScoringParametersForPerson;
import org.matsim.core.scoring.functions.ModeUtilityParameters;
import org.matsim.pt.PtConstants;
import org.matsim.pt.config.TransitConfigGroup;
import org.matsim.utils.objectattributes.ObjectAttributes;

import ch.sbb.matsim.preparation.RaumtypPerPerson;
import javafx.util.Pair;

/**
 * @author jlie based on org.matsim.core.scoring.functions.RandomizedCharyparNagelScoringParameters
 *         adding: the default CharyparNagelScoringParametersForPerson seems to be org.matsim.core.scoring.functions.SubpopulationCharyparNagelScoringParameters
 */

public class SBBCharyparNagelScoringParametersForPerson implements ScoringParametersForPerson {

    private final PlanCalcScoreConfigGroup config;
    private final ScenarioConfigGroup scConfig;
    private final Map<Person, ScoringParameters> paramsPerPerson = new LinkedHashMap<>();
    private final ObjectAttributes personAttributes;
    private final String subpopulationAttributeName;
    private final TransitConfigGroup transitConfigGroup;
    Map<String, PlanCalcScoreConfigGroup.ModeParams> paramsPerMode;
    Map<Pair<String, String>, Double> modeConstCorrectionPerModeAndRaumtyp;
    private Logger log = Logger.getLogger(SBBCharyparNagelScoringParametersForPerson.class);
    private static List<String> MODES_WITH_CONST_CORRECTION = Arrays.asList(new String[] {
            TransportMode.walk, TransportMode.bike, TransportMode.car, TransportMode.pt, TransportMode.ride});

    public SBBCharyparNagelScoringParametersForPerson(Scenario scenario) {
        this(scenario.getConfig().plans(),
                scenario.getConfig().planCalcScore(),
                scenario.getConfig().scenario(),
                scenario.getPopulation(),
                scenario.getConfig().transit());
    }

    SBBCharyparNagelScoringParametersForPerson(
            PlansConfigGroup plansConfigGroup,
            PlanCalcScoreConfigGroup planCalcScoreConfigGroup,
            ScenarioConfigGroup scenarioConfigGroup,
            Population population,
            TransitConfigGroup transitConfigGroup) {
        this.config = planCalcScoreConfigGroup;
        this.scConfig = scenarioConfigGroup;
        this.personAttributes = population.getPersonAttributes();
        this.subpopulationAttributeName = plansConfigGroup.getSubpopulationAttributeName();
        this.transitConfigGroup = transitConfigGroup;
        this.paramsPerMode = new HashMap<>();
        for (Map.Entry<String, PlanCalcScoreConfigGroup.ModeParams> aModeEntry : this.config.getModes().entrySet()) {
            String mode = aModeEntry.getKey();
            PlanCalcScoreConfigGroup.ModeParams modeParams = aModeEntry.getValue();
            String[] splitted = mode.split("_");
            if (splitted.length == 2) {
                if (splitted[0].equals(TransportMode.pt)) {
                    this.paramsPerMode.put(splitted[1], modeParams);
                } else if (mode.equals(TransportMode.transit_walk)) {
                    this.paramsPerMode.put(mode, modeParams);
                } else {
                    throw new IllegalArgumentException("mode separated by exactly one _, but first argument is not pt " + mode);
                }
            } else if (splitted.length == 1) {
                this.paramsPerMode.put(mode, modeParams);
            } else
                throw new IllegalArgumentException("not well SBB-formatted mode " + mode);
        }
        this.modeConstCorrectionPerModeAndRaumtyp = new HashMap<>();
        this.modeConstCorrectionPerModeAndRaumtyp.put(new Pair<>(TransportMode.walk, "1"), 0.0);
        this.modeConstCorrectionPerModeAndRaumtyp.put(new Pair<>(TransportMode.walk, "2"), -0.24);
        this.modeConstCorrectionPerModeAndRaumtyp.put(new Pair<>(TransportMode.walk, "3"), -0.06);
        this.modeConstCorrectionPerModeAndRaumtyp.put(new Pair<>(TransportMode.walk, "4"), 0.0);
        this.modeConstCorrectionPerModeAndRaumtyp.put(new Pair<>(TransportMode.bike, "1"), 0.0);
        this.modeConstCorrectionPerModeAndRaumtyp.put(new Pair<>(TransportMode.bike, "2"), -0.43);
        this.modeConstCorrectionPerModeAndRaumtyp.put(new Pair<>(TransportMode.bike, "3"), -0.40);
        this.modeConstCorrectionPerModeAndRaumtyp.put(new Pair<>(TransportMode.bike, "4"), 0.0);
        this.modeConstCorrectionPerModeAndRaumtyp.put(new Pair<>(TransportMode.car, "1"), 0.0);
        this.modeConstCorrectionPerModeAndRaumtyp.put(new Pair<>(TransportMode.car, "2"), 0.0);
        this.modeConstCorrectionPerModeAndRaumtyp.put(new Pair<>(TransportMode.car, "3"), 0.0);
        this.modeConstCorrectionPerModeAndRaumtyp.put(new Pair<>(TransportMode.car, "4"), 0.0);
        this.modeConstCorrectionPerModeAndRaumtyp.put(new Pair<>(TransportMode.pt, "1"), 0.0);
        this.modeConstCorrectionPerModeAndRaumtyp.put(new Pair<>(TransportMode.pt, "2"), -0.24);
        this.modeConstCorrectionPerModeAndRaumtyp.put(new Pair<>(TransportMode.pt, "3"), -0.30);
        this.modeConstCorrectionPerModeAndRaumtyp.put(new Pair<>(TransportMode.pt, "4"), 0.0);
        this.modeConstCorrectionPerModeAndRaumtyp.put(new Pair<>(TransportMode.ride, "1"), 0.0);
        this.modeConstCorrectionPerModeAndRaumtyp.put(new Pair<>(TransportMode.ride, "2"), 0.0);
        this.modeConstCorrectionPerModeAndRaumtyp.put(new Pair<>(TransportMode.ride, "3"), 0.0);
        this.modeConstCorrectionPerModeAndRaumtyp.put(new Pair<>(TransportMode.ride, "4"), 0.0);
        for (Pair<String, String> pair: this.modeConstCorrectionPerModeAndRaumtyp.keySet()) {
            if (!MODES_WITH_CONST_CORRECTION.contains(pair.getKey())) {
                throw new IllegalStateException("mode " + pair.getKey() +
                        " with const-correction ist not contained in " + MODES_WITH_CONST_CORRECTION);
            }
        }
    }

    @Override
    public ScoringParameters getScoringParameters(Person person) {
        if (!this.paramsPerPerson.containsKey(person)) {
            final String subpopulation = (String) personAttributes.getAttribute(person.getId().toString(), subpopulationAttributeName);
            final String aboType = (String) personAttributes.getAttribute(person.getId().toString(), "season_ticket"); // TODO define "season_ticket" as static string?
            final String raumtyp = (String) personAttributes.getAttribute(person.getId().toString(), RaumtypPerPerson.RAUMTYP);
            PlanCalcScoreConfigGroup.ModeParams modeParams = paramsPerMode.get(aboType);
            if (modeParams == null) {
                log.info("no mode parameters defined for season-ticket " + aboType);
                modeParams = paramsPerMode.get(TransportMode.pt);
                if (modeParams == null) {
                    throw new IllegalStateException("mode parameters for pt must be defined");
                }
            }
            modeParams.setConstant(modeParams.getConstant() + modeConstCorrectionPerModeAndRaumtyp.get(new Pair<>(TransportMode.pt, raumtyp)));
            ScoringParameters.Builder builder = new ScoringParameters.Builder(
                    this.config, this.config.getScoringParameters(subpopulation),
                    scConfig);
            if (transitConfigGroup.isUseTransit()) {
                // jlie (17.08.2017): this is from org.matsim.core.scoring.functions.SubpopulationCharyparNagelScoringParameters
                // without this MATSim does not know "pt interaction" and throws an IllegalArgumentException in CharyparNagelActivityScoring
                // yyyy this should go away somehow. :-)
                PlanCalcScoreConfigGroup.ActivityParams transitActivityParams = new PlanCalcScoreConfigGroup.ActivityParams(PtConstants.TRANSIT_ACTIVITY_TYPE);
                transitActivityParams.setTypicalDuration(120.0);
                transitActivityParams.setOpeningTime(0.) ;
                transitActivityParams.setClosingTime(0.) ;
                ActivityUtilityParameters.Builder modeParamsBuilder = new ActivityUtilityParameters.Builder(transitActivityParams);
                modeParamsBuilder.setScoreAtAll(false);
                builder.setActivityParameters(PtConstants.TRANSIT_ACTIVITY_TYPE, modeParamsBuilder);
            }
            builder.setModeParameters(TransportMode.pt, new ModeUtilityParameters.Builder(modeParams));

            for (String mode: MODES_WITH_CONST_CORRECTION) {
                if (!mode.equals(TransportMode.pt)) {
                    builder.getModeParameters(mode).setConstant(paramsPerMode.get(mode).getConstant() +
                            modeConstCorrectionPerModeAndRaumtyp.get(new Pair<>(mode, raumtyp)));
                }
            }
            CharyparNagelScoringParameters params = builder.build();
            this.paramsPerPerson.put(person, params);
        }
        return this.paramsPerPerson.get(person);
    }
}
