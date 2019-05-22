package ch.sbb.matsim.scoring;

import ch.sbb.matsim.config.SBBBehaviorGroupsConfigGroup;
import ch.sbb.matsim.config.variables.SBBActivities;
import org.junit.Assert;
import org.junit.Test;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.population.*;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.config.groups.PlanCalcScoreConfigGroup;
import org.matsim.core.router.StageActivityTypes;
import org.matsim.core.router.StageActivityTypesImpl;
import org.matsim.core.router.TripStructureUtils;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.core.scoring.ScoringFunction;
import org.matsim.core.scoring.functions.ActivityUtilityParameters;
import org.matsim.core.utils.misc.Time;
import org.matsim.pt.PtConstants;

import java.util.List;

/**
 * @author mrieser
 */
public class SBBScoringFunctionTest {

    @Test
    public void testTransferScoring() {
        testTransferScoring("access_walk", "egress_walk");
    }

    @Test
    public void testTransferScoring_accessEgressJustTransit() {
        testTransferScoring("transit_walk", "transit_walk");
    }

    private void testTransferScoring(String accessMode, String egressMode) {
        Config config = ConfigUtils.createConfig();
        ScoringFixture.addRideInteractionScoring(config);
        Scenario scenario = ScenarioUtils.createScenario(config);

        PopulationFactory pf = scenario.getPopulation().getFactory();
        Person person1 = pf.createPerson(Id.create("1", Person.class));
        Activity homeAct1 = createActivity(pf, "home", 100, 100, Time.getUndefinedTime(), 8 * 3600);
        Leg accessLeg = createLeg(pf, accessMode, 8 * 3600, 8.1 * 3600);
        Activity ptInteraction1 = createActivity(pf, PtConstants.TRANSIT_ACTIVITY_TYPE, 500, 500, 8.1 * 3600, 8.1 * 3600);
        Leg ptLeg1 = createLeg(pf, "pt", 8.1 * 3600, 8.5 * 3600-60);
        Activity ptInteraction2 = createActivity(pf, PtConstants.TRANSIT_ACTIVITY_TYPE, 1500, 1500, 8.5 * 3600, 8.5 * 3600);
        Leg transferLeg = createLeg(pf, "transit_walk", 8.5 * 3600 - 60, 8.5 * 3600 + 60);
        Activity ptInteraction3 = createActivity(pf, PtConstants.TRANSIT_ACTIVITY_TYPE, 1500, 1500, 8.5 * 3600, 8.5 * 3600);
        Leg ptLeg2 = createLeg(pf, "pt", 8.5 * 3600 + 60, 8.9 * 3600);
        Activity ptInteraction4 = createActivity(pf, PtConstants.TRANSIT_ACTIVITY_TYPE, 500, 500, 8.9 * 3600, 8.9 * 3600);
        Leg egressLeg = createLeg(pf, egressMode, 8.9 * 3600, 9 * 3600);
        Activity homeAct2 = createActivity(pf, "home", 100, 100, 9 * 3600, Time.getUndefinedTime());

        Plan plan = pf.createPlan();
        plan.addActivity(homeAct1);
        plan.addLeg(accessLeg);
        plan.addActivity(ptInteraction1);
        plan.addLeg(ptLeg1);
        plan.addActivity(ptInteraction2);
        plan.addLeg(transferLeg);
        plan.addActivity(ptInteraction3);
        plan.addLeg(ptLeg2);
        plan.addActivity(ptInteraction4);
        plan.addLeg(egressLeg);
        plan.addActivity(homeAct2);
        person1.addPlan(plan);

        StageActivityTypes stageActivities = new StageActivityTypesImpl(PtConstants.TRANSIT_ACTIVITY_TYPE);
        List<TripStructureUtils.Trip> trips = TripStructureUtils.getTrips(plan, stageActivities);

        PlanCalcScoreConfigGroup.ActivityParams homeParams = new PlanCalcScoreConfigGroup.ActivityParams("home");
        homeParams.setTypicalDuration(12*3600);
        homeParams.setMinimalDuration(8*3600);
        config.planCalcScore().addActivityParams(homeParams);

        // set all leg and activity scoring to 0, so we can measure the transfers only
        config.planCalcScore().setPerforming_utils_hr(0.0);
        PlanCalcScoreConfigGroup.ModeParams ptScoring = new PlanCalcScoreConfigGroup.ModeParams("pt");
        ptScoring.setMarginalUtilityOfDistance(0.0);
        ptScoring.setMarginalUtilityOfTraveling(0.0);
        PlanCalcScoreConfigGroup.ModeParams walkScoring = new PlanCalcScoreConfigGroup.ModeParams("walk");
        walkScoring.setMarginalUtilityOfDistance(0.0);
        walkScoring.setMarginalUtilityOfTraveling(0.0);
        config.planCalcScore().addModeParams(ptScoring);
        config.planCalcScore().addModeParams(walkScoring);

        // now set our transfer scoring parameters
        config.planCalcScore().setUtilityOfLineSwitch(-3.0); // set this, but it should get ignored
        SBBBehaviorGroupsConfigGroup sbbParams = ConfigUtils.addOrGetModule(config, SBBBehaviorGroupsConfigGroup.class);
        sbbParams.setBaseTransferUtility(-1.0);
        sbbParams.setTransferUtilityPerTravelTime_utils_hr(-2.0);
        sbbParams.setMinimumTransferUtility(-2.0);
        sbbParams.setMaximumTransferUtility(-16.0);

        SBBScoringFunctionFactory factory = new SBBScoringFunctionFactory(scenario);
        ScoringFunction sf = factory.createNewScoringFunction(person1);
        sf.handleActivity(homeAct1);
        sf.handleLeg(accessLeg);
        sf.handleActivity(ptInteraction1);
        sf.handleLeg(ptLeg1);
        sf.handleActivity(ptInteraction2);
        sf.handleLeg(ptLeg2);
        sf.handleActivity(ptInteraction3);
        sf.handleLeg(egressLeg);
        sf.handleActivity(homeAct2);

        TripStructureUtils.Trip trip = trips.get(0);
        sf.handleTrip(trip);
        sf.finish();

        double score = sf.getScore();
        System.out.println(score);

        int numberOfTransfers = 1;
        double minTransferUtility = Math.min(sbbParams.getMinimumTransferUtility(), sbbParams.getMaximumTransferUtility());
        double maxTransferUtility = Math.max(sbbParams.getMinimumTransferUtility(), sbbParams.getMaximumTransferUtility());
        double singleTransferScore = Math.max(minTransferUtility, Math.min(maxTransferUtility, sbbParams.getBaseTransferUtility() + sbbParams.getTransferUtilityPerTravelTime_utils_hr() * 0.8));
        double expectedScore = numberOfTransfers * singleTransferScore;

        Assert.assertEquals(expectedScore, score, 1e-6);
    }

    private Activity createActivity(PopulationFactory pf, String type, double x, double y, double startTime, double endTime) {
        Activity act = pf.createActivityFromCoord(type, new Coord(x, y));
        act.setStartTime(startTime);
        act.setEndTime(endTime);
        return act;
    }

    private Leg createLeg(PopulationFactory pf, String mode, double departureTime, double arrivalTime) {
        Leg leg = pf.createLeg(mode);
        leg.setDepartureTime(departureTime);
        leg.setTravelTime(arrivalTime - departureTime);
        return leg;
    }

    @Test
    public void testMemoryOptimization() {
        /* this test checks that the scoring function only stores activity parameters for each person that are actually
         * used, and that, if possible, objects are re-used. */
        SBBBehaviorGroupsConfigGroup sbbBehaviour = new SBBBehaviorGroupsConfigGroup();
        Config config = ConfigUtils.createConfig(sbbBehaviour);
        ScoringFixture.addRideInteractionScoring(config);
        PlanCalcScoreConfigGroup.ScoringParameterSet params = config.planCalcScore().getOrCreateScoringParameters(null);
        params.addActivityParams(createActivityParams("home", 8*3600, 12*3600));
        params.addActivityParams(createActivityParams("work", 3*3600, 8*3600));
        params.addActivityParams(createActivityParams("edu", 3*3600, 6*3600));
        params.addActivityParams(createActivityParams("shop", 1*3600, 2*3600));
        params.addActivityParams(createActivityParams("leisure", 1*3600, 2*3600));
        params.addActivityParams(createActivityParams("other", 1*3600, 3*3600));

        Scenario scenario = ScenarioUtils.createScenario(config);
        Population population = scenario.getPopulation();
        PopulationFactory pf = population.getFactory();

        Person w1, w2, e3;
        population.addPerson(w1 = createWorkPerson(pf, "w1"));
        population.addPerson(w2 = createWorkPerson(pf, "w2"));
        population.addPerson(e3 = createEduPerson(pf, "e3"));

        int interactionActtypeCount = SBBActivities.stageActivityTypeList.size();

        SBBCharyparNagelScoringParametersForPerson spfp = new SBBCharyparNagelScoringParametersForPerson(config.plans(), config.planCalcScore(), config.scenario(), population, config.transit(), sbbBehaviour);

        SBBScoringParameters sp1 = spfp.getSBBScoringParameters(w1);
        ActivityUtilityParameters home1 = sp1.getMatsimScoringParameters().utilParams.get("home");
        ActivityUtilityParameters work1 = sp1.getMatsimScoringParameters().utilParams.get("work");
        ActivityUtilityParameters edu1 = sp1.getMatsimScoringParameters().utilParams.get("edu");
        Assert.assertEquals("ScoringFunction should only contain parameters for activity types used by this agent.", 2 + interactionActtypeCount, sp1.getMatsimScoringParameters().utilParams.size());
        Assert.assertNotNull("ScoringFunction should contain parameters for activity 'home'.", home1);
        Assert.assertNotNull("ScoringFunction should contain parameters for activity 'work'.", work1);
        Assert.assertNull("ScoringFunction should not contain parameters for activity 'edu'.", edu1);

        SBBScoringParameters sp2 = spfp.getSBBScoringParameters(w2);
        ActivityUtilityParameters home2 = sp2.getMatsimScoringParameters().utilParams.get("home");
        ActivityUtilityParameters work2 = sp2.getMatsimScoringParameters().utilParams.get("work");
        ActivityUtilityParameters edu2 = sp2.getMatsimScoringParameters().utilParams.get("edu");
        Assert.assertEquals("ScoringFunction should only contain parameters for activity types used by this agent.", 2 + interactionActtypeCount, sp2.getMatsimScoringParameters().utilParams.size());
        Assert.assertNotNull("ScoringFunction should contain parameters for activity 'home'.", home2);
        Assert.assertNotNull("ScoringFunction should contain parameters for activity 'work'.", work2);
        Assert.assertNull("ScoringFunction should not contain parameters for activity 'edu'.", edu2);

        SBBScoringParameters sp3 = spfp.getSBBScoringParameters(e3);
        ActivityUtilityParameters home3 = sp3.getMatsimScoringParameters().utilParams.get("home");
        ActivityUtilityParameters work3 = sp3.getMatsimScoringParameters().utilParams.get("work");
        ActivityUtilityParameters edu3 = sp3.getMatsimScoringParameters().utilParams.get("edu");
        Assert.assertEquals("ScoringFunction should only contain parameters for activity types used by this agent.", 2 + interactionActtypeCount, sp3.getMatsimScoringParameters().utilParams.size());
        Assert.assertNotNull("ScoringFunction should contain parameters for activity 'home'.", home3);
        Assert.assertNull("ScoringFunction should not contain parameters for activity 'work'.", work3);
        Assert.assertNotNull("ScoringFunction should contain parameters for activity 'edu'.", edu3);

        Assert.assertSame("parameters for home should be the same object.", home1, home2);
        Assert.assertSame("parameters for home should be the same object.", home1, home2);
        Assert.assertSame("parameters for home should be the same object.", work1, work2);
    }

    private PlanCalcScoreConfigGroup.ActivityParams createActivityParams(String type, double minDuration, double typicalDuration) {
        PlanCalcScoreConfigGroup.ActivityParams params = new PlanCalcScoreConfigGroup.ActivityParams(type);
        params.setMinimalDuration(minDuration);
        params.setTypicalDuration(typicalDuration);
        return params;
    }

    private Person createWorkPerson(PopulationFactory pf, String id) {
        return createPerson(pf, id, "work");
    }

    private Person createEduPerson(PopulationFactory pf, String id) {
        return createPerson(pf, id, "edu");
    }

    private Person createPerson(PopulationFactory pf, String id, String primActType) {
        Person p = pf.createPerson(Id.create(id, Person.class));

        Activity h1 = pf.createActivityFromCoord("home", new Coord(0, 0));
        h1.setEndTime(7.5*3600);
        Activity pa = pf.createActivityFromCoord(primActType, new Coord(1000, 1000));
        pa.setEndTime(15*3600);
        Activity h2 = pf.createActivityFromCoord("home", h1.getCoord());

        Plan plan = pf.createPlan();
        plan.addActivity(h1);
        plan.addLeg(pf.createLeg("walk"));
        plan.addActivity(pa);
        plan.addLeg(pf.createLeg("walk"));
        plan.addActivity(h2);

        p.addPlan(plan);

        return p;
    }
}