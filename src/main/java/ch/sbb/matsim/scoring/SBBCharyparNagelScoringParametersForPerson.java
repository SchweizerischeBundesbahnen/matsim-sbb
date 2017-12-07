package ch.sbb.matsim.scoring;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import ch.sbb.matsim.csv.CSVReader;
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

    private Logger log = Logger.getLogger(SBBCharyparNagelScoringParametersForPerson.class);

    private final PlanCalcScoreConfigGroup config;
    private final ScenarioConfigGroup scConfig;
    private final Map<Person, ScoringParameters> paramsPerPerson = new LinkedHashMap<>();
    private final ObjectAttributes personAttributes;
    private final String subpopulationAttributeName;
    private final TransitConfigGroup transitConfigGroup;

    private final static String ATTRIBUTEABOTYPE = "season_ticket";

    // map mit verhaltenshomogene Gruppe -> Typ, Mode, Param -> Korrektur

    Map<Pair<String, String>, Double> modeConstCorrectionPerModeAndRaumtyp;
    Map<Pair<String, String>, Double> ptAboCorrection;

    private static List<String> PT_ABO_TYPES = Arrays.asList(new String[] {"none", "Halbtaxabo", "Generalabo"});
    private static List<String> MODES_WITH_CONST_CORRECTION = Arrays.asList(new String[] {
            TransportMode.walk, TransportMode.transit_walk, TransportMode.bike, TransportMode.car, TransportMode.pt, TransportMode.ride });

    public SBBCharyparNagelScoringParametersForPerson(Scenario scenario) {
        this(scenario.getConfig().plans(),
                scenario.getConfig().planCalcScore(),
                scenario.getConfig().scenario(),
                scenario.getPopulation(),
                scenario.getConfig().transit());
    }

    SBBCharyparNagelScoringParametersForPerson  (
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

        // TODO: Nur einmal alles zusammen einlesen ->
        readPTAboCorrection();
        readModeConstCorrectionPerModeAndRaumtyp();
    }

    @Override
    public ScoringParameters getScoringParameters(Person person) {
        if (!this.paramsPerPerson.containsKey(person)) {
            final String subpopulation = (String) personAttributes.getAttribute(person.getId().toString(), subpopulationAttributeName);
            final String aboType = (String) personAttributes.getAttribute(person.getId().toString(), ATTRIBUTEABOTYPE);
            final String raumtyp = (String) personAttributes.getAttribute(person.getId().toString(), RaumtypPerPerson.RAUMTYP);

            ScoringParameters.Builder builder = new ScoringParameters.Builder(
                    this.config, this.config.getScoringParameters(subpopulation),
                    scConfig);

            if (transitConfigGroup.isUseTransit()) {
                // jlie (17.08.2017): this is from org.matsim.core.scoring.functions.SubpopulationCharyparNagelScoringParameters
                // without this MATSim does not know "pt interaction" and throws an IllegalArgumentException in CharyparNagelActivityScoring
                // yyyy this should go away somehow. :-)
                PlanCalcScoreConfigGroup.ActivityParams transitActivityParams = new PlanCalcScoreConfigGroup.ActivityParams(PtConstants.TRANSIT_ACTIVITY_TYPE);
                transitActivityParams.setTypicalDuration(120.0);
                transitActivityParams.setOpeningTime(0.);
                transitActivityParams.setClosingTime(0.);
                ActivityUtilityParameters.Builder modeParamsBuilder = new ActivityUtilityParameters.Builder(transitActivityParams);
                modeParamsBuilder.setScoreAtAll(false);
                builder.setActivityParameters(PtConstants.TRANSIT_ACTIVITY_TYPE, modeParamsBuilder);
            }

            // TODO: make this code more flexible
            for(String mode: this.config.getModes().keySet())   {
                final PlanCalcScoreConfigGroup.ModeParams defaultModeParams = this.config.getModes().get(mode);
                final ModeUtilityParameters.Builder modeParameteresBuilder = new ModeUtilityParameters.Builder(defaultModeParams);

                // Korrektur nach Abotyp kombiniert mit Raumtyp
                if(mode.equals(TransportMode.pt))   {
                    modeParameteresBuilder.setConstant(ptAboCorrection.get(new Pair<>("constant", aboType)) +
                            modeConstCorrectionPerModeAndRaumtyp.get(new Pair<>(mode, raumtyp)));
                    modeParameteresBuilder.setMarginalUtilityOfDistance_m(ptAboCorrection.get(new Pair<>("marginalUtilityOfDistance_util_m", aboType)));
                    modeParameteresBuilder.setMarginalUtilityOfTraveling_s(ptAboCorrection.get(new Pair<>("marginalUtilityOfTraveling_util_hr", aboType)) / 3600);
                    modeParameteresBuilder.setMonetaryDistanceRate(ptAboCorrection.get(new Pair<>("monetaryDistanceRate", aboType)));
                }
                // Korrektur nach Raumtyp
                else if(MODES_WITH_CONST_CORRECTION.contains(mode))  {
                    modeParameteresBuilder.setConstant(defaultModeParams.getConstant() +
                            modeConstCorrectionPerModeAndRaumtyp.get(new Pair<>(mode, raumtyp)));
                }
                builder.setModeParameters(mode, modeParameteresBuilder);
            }
            ScoringParameters params = builder.build();
            this.paramsPerPerson.put(person, params);
        }
        return this.paramsPerPerson.get(person);
    }

    private void readPTAboCorrection()  {
        this.ptAboCorrection = new HashMap<>();

        // TODO: this is still not a proper solution
        String[] cols = new String[]{"param", "none", "Generalabo", "Halbtaxabo"};
        CSVReader reader = new CSVReader(cols);
        reader.read("KorrekturPTAbo.csv", ";");
        Iterator<HashMap<String, String>> iterator = reader.data.iterator();
        iterator.next(); // header line
        while (iterator.hasNext()) {
            HashMap<String, String> aRow = iterator.next();
            for(String aboType: PT_ABO_TYPES)
                this.ptAboCorrection.put(new Pair<>(aRow.get("param"), aboType), Double.valueOf(aRow.get(aboType)));
        }
    }

    private void readModeConstCorrectionPerModeAndRaumtyp() {
        this.modeConstCorrectionPerModeAndRaumtyp = new HashMap<>();

        // TODO: first step towards more flexible code, but still not very nice
        String[] cols = new String[]{"mode", "type1", "type2", "type3", "type4"};
        CSVReader reader = new CSVReader(cols);
        reader.read("KorrekturKonstantenRaumtypen.csv", ";");
        Iterator<HashMap<String, String>> iterator = reader.data.iterator();
        iterator.next(); // header line
        while (iterator.hasNext()) {
            HashMap<String, String> aRow = iterator.next();
            this.modeConstCorrectionPerModeAndRaumtyp.put(new Pair<>(aRow.get("mode"), "1"), Double.valueOf(aRow.get("type1")));
            this.modeConstCorrectionPerModeAndRaumtyp.put(new Pair<>(aRow.get("mode"), "2"), Double.valueOf(aRow.get("type2")));
            this.modeConstCorrectionPerModeAndRaumtyp.put(new Pair<>(aRow.get("mode"), "3"), Double.valueOf(aRow.get("type3")));
            this.modeConstCorrectionPerModeAndRaumtyp.put(new Pair<>(aRow.get("mode"), "4"), Double.valueOf(aRow.get("type4")));
        }
        for (Pair<String, String> pair : this.modeConstCorrectionPerModeAndRaumtyp.keySet()) {
            if (!MODES_WITH_CONST_CORRECTION.contains(pair.getKey())) {
                throw new IllegalStateException("mode " + pair.getKey() +
                        " with const-correction ist not contained in " + MODES_WITH_CONST_CORRECTION);
            }
        }
    }
}
