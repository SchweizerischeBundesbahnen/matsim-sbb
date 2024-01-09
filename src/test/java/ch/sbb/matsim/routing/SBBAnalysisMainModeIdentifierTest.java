package ch.sbb.matsim.routing;

import ch.sbb.matsim.config.variables.SBBModes;
import org.junit.Assert;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.population.Activity;
import org.matsim.api.core.v01.population.Leg;
import org.matsim.api.core.v01.population.PlanElement;
import org.matsim.api.core.v01.population.PopulationFactory;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.router.MainModeIdentifier;
import org.matsim.core.scenario.ScenarioUtils;

import java.util.List;

public class SBBAnalysisMainModeIdentifierTest {

    @Test
    public void testMainMode() {
        PopulationFactory pf = ScenarioUtils.createScenario(ConfigUtils.createConfig()).getPopulation().getFactory();
        Leg l1 = pf.createLeg(SBBModes.PT);
        Leg l2 = pf.createLeg(SBBModes.ACCESS_EGRESS_WALK);
        Leg l3 = pf.createLeg(SBBModes.CAR);
        Leg l4 = pf.createLeg(SBBModes.RIDEFEEDER);
        Leg l5 = pf.createLeg(SBBModes.WALK_MAIN_MAINMODE);
        Activity a = pf.createActivityFromCoord("test", new Coord(0, 0));
        MainModeIdentifier mainModeIdentifier = new SBBAnalysisMainModeIdentifier();
        List<PlanElement> ptTrip = List.of(l1, a, l2);
        Assert.assertEquals(mainModeIdentifier.identifyMainMode(ptTrip), SBBModes.PT);


        List<PlanElement> carTrip = List.of(l3, a, l2);
        Assert.assertEquals(mainModeIdentifier.identifyMainMode(carTrip), SBBModes.CAR);

        List<PlanElement> walkTrip = List.of(l5, a, l2);
        Assert.assertEquals(mainModeIdentifier.identifyMainMode(walkTrip), SBBModes.WALK_MAIN_MAINMODE);

        List<PlanElement> feederTrip = List.of(l4, a, l2);
        Assert.assertEquals(mainModeIdentifier.identifyMainMode(feederTrip), SBBModes.PT);

    }

    public void testUnidentifiableMainMode() {
        Assertions.assertThrows(NullPointerException.class, () -> {
            PopulationFactory pf = ScenarioUtils.createScenario(ConfigUtils.createConfig()).getPopulation().getFactory();
            Leg l1 = pf.createLeg(TransportMode.airplane);
            List<PlanElement> feederTrip = List.of(l1);
            MainModeIdentifier mainModeIdentifier = new SBBAnalysisMainModeIdentifier();
            mainModeIdentifier.identifyMainMode(feederTrip);
        });

    }

}