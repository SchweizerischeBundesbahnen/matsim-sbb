/*
 * Copyright (C) Schweizerische Bundesbahnen SBB, 2017.
 */

package ch.sbb.matsim.scoring;

import static org.junit.Assert.assertEquals;

import ch.sbb.matsim.config.variables.SBBModes;
import org.apache.log4j.Logger;
import org.junit.Rule;
import org.junit.Test;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.population.Person;
import org.matsim.core.config.groups.PlanCalcScoreConfigGroup;
import org.matsim.core.scoring.functions.ScoringParameters;
import org.matsim.testcases.MatsimTestUtils;

public class SBBScoringParametersForPersonTest {

	private static final Logger log = Logger.getLogger(SBBScoringParametersForPersonTest.class);
	@Rule public MatsimTestUtils utils = new MatsimTestUtils();

	@Test
	public void testDefaultScoringParams() {
		ScoringFixture f = new ScoringFixture();

		PlanCalcScoreConfigGroup.ModeParams defaultModeParams = f.config.planCalcScore().getModes().get(SBBModes.PT);
		assertEquals(-1.0, defaultModeParams.getConstant(), 0.0);
		assertEquals(0.0, defaultModeParams.getMarginalUtilityOfDistance(), 0.0);
		assertEquals(1.14, defaultModeParams.getMarginalUtilityOfTraveling(), 0.0);
		assertEquals(-0.000300, defaultModeParams.getMonetaryDistanceRate(), 0.0);

		f.addPersonNoAttribute();
		ScoringParameters params = f.buildDefaultScoringParams(Id.create(1, Person.class));
		assertEquals(-1.0, params.modeParams.get(SBBModes.PT).constant, 0.0);
		assertEquals(0.0, params.modeParams.get(SBBModes.PT).marginalUtilityOfDistance_m, 0.0);
		assertEquals(1.14 / 3600, params.modeParams.get(SBBModes.PT).marginalUtilityOfTraveling_s, 0.0);
		assertEquals(-0.000300, params.modeParams.get(SBBModes.PT).monetaryDistanceCostRate, 0.0);
	}

	@Test
	public void testCustomScoringParamsOneAttribute() {
		ScoringFixture f = new ScoringFixture();
		f.addCustomScoringParams();

		f.addPersonNoAttribute();
		ScoringParameters params = f.buildDefaultScoringParams(Id.create(1, Person.class));
		assertEquals(-1.0, params.modeParams.get(SBBModes.PT).constant, 0.0);
		assertEquals(0.0, params.modeParams.get(SBBModes.PT).marginalUtilityOfDistance_m, 0.0);
		assertEquals(1.14 / 3600, params.modeParams.get(SBBModes.PT).marginalUtilityOfTraveling_s, 0.0);
		assertEquals(-0.000300, params.modeParams.get(SBBModes.PT).monetaryDistanceCostRate, 0.0);

		f.personOneAttribute();
		params = f.buildDefaultScoringParams(Id.create(2, Person.class));
		assertEquals(0.0, params.modeParams.get(SBBModes.PT).constant, 0.0);
		assertEquals(0.0, params.modeParams.get(SBBModes.PT).marginalUtilityOfDistance_m, 0.0);
		assertEquals(1.4 / 3600, params.modeParams.get(SBBModes.PT).marginalUtilityOfTraveling_s, 0.0);
		assertEquals(0.0, params.modeParams.get(SBBModes.PT).monetaryDistanceCostRate, 0.0);
	}

	@Test
	public void testCustomScoringParamsTwoAttributes() {
		ScoringFixture f = new ScoringFixture();
		f.addCustomScoringParams();

		f.addPersonNoAttribute();
		ScoringParameters params = f.buildDefaultScoringParams(Id.create(1, Person.class));
		assertEquals(-1.0, params.modeParams.get(SBBModes.PT).constant, 0.0);
		assertEquals(0.0, params.modeParams.get(SBBModes.PT).marginalUtilityOfDistance_m, 0.0);
		assertEquals(1.14 / 3600, params.modeParams.get(SBBModes.PT).marginalUtilityOfTraveling_s, 0.0);
		assertEquals(-0.000300, params.modeParams.get(SBBModes.PT).monetaryDistanceCostRate, 0.0);

		f.personOneAttribute();
		params = f.buildDefaultScoringParams(Id.create(2, Person.class));
		assertEquals(0.0, params.modeParams.get(SBBModes.PT).constant, 0.0);
		assertEquals(0.0, params.modeParams.get(SBBModes.PT).marginalUtilityOfDistance_m, 0.0);
		assertEquals(1.4 / 3600, params.modeParams.get(SBBModes.PT).marginalUtilityOfTraveling_s, 0.0);
		assertEquals(0.0, params.modeParams.get(SBBModes.PT).monetaryDistanceCostRate, 0.0);

		f.personTwoAttribute();
		params = f.buildDefaultScoringParams(Id.create(3, Person.class));
		assertEquals(-0.3, params.modeParams.get(SBBModes.PT).constant, 0.0);
		assertEquals(0.0, params.modeParams.get(SBBModes.PT).marginalUtilityOfDistance_m, 0.0);
		assertEquals(1.4 / 3600, params.modeParams.get(SBBModes.PT).marginalUtilityOfTraveling_s, 0.0);
		assertEquals(0.0, params.modeParams.get(SBBModes.PT).monetaryDistanceCostRate, 0.0);
	}

	@Test
	public void testCustomScoringParamsThreeAttributes() {
		ScoringFixture f = new ScoringFixture();
		f.addCustomScoringParams();

		f.addPersonNoAttribute();
		ScoringParameters params = f.buildDefaultScoringParams(Id.create(1, Person.class));
		assertEquals(-1.0, params.modeParams.get(SBBModes.PT).constant, 0.0);
		assertEquals(0.0, params.modeParams.get(SBBModes.PT).marginalUtilityOfDistance_m, 0.0);
		assertEquals(1.14 / 3600, params.modeParams.get(SBBModes.PT).marginalUtilityOfTraveling_s, 0.0);
		assertEquals(-0.000300, params.modeParams.get(SBBModes.PT).monetaryDistanceCostRate, 0.0);

		f.personOneAttribute();
		params = f.buildDefaultScoringParams(Id.create(2, Person.class));
		assertEquals(0.0, params.modeParams.get(SBBModes.PT).constant, 0.0);
		assertEquals(0.0, params.modeParams.get(SBBModes.PT).marginalUtilityOfDistance_m, 0.0);
		assertEquals(1.4 / 3600, params.modeParams.get(SBBModes.PT).marginalUtilityOfTraveling_s, 0.0);
		assertEquals(0.0, params.modeParams.get(SBBModes.PT).monetaryDistanceCostRate, 0.0);

		f.personTwoAttribute();
		params = f.buildDefaultScoringParams(Id.create(3, Person.class));
		assertEquals(-0.3, params.modeParams.get(SBBModes.PT).constant, 0.0);
		assertEquals(0.0, params.modeParams.get(SBBModes.PT).marginalUtilityOfDistance_m, 0.0);
		assertEquals(1.4 / 3600, params.modeParams.get(SBBModes.PT).marginalUtilityOfTraveling_s, 0.0);
		assertEquals(0.0, params.modeParams.get(SBBModes.PT).monetaryDistanceCostRate, 0.0);

		f.personThreeAttribute();
		params = f.buildDefaultScoringParams(Id.create(4, Person.class));
		assertEquals(0.2, params.modeParams.get(SBBModes.PT).constant, 0.0);
		assertEquals(0.0, params.modeParams.get(SBBModes.PT).marginalUtilityOfDistance_m, 0.0);
		assertEquals(1.4 / 3600, params.modeParams.get(SBBModes.PT).marginalUtilityOfTraveling_s, 0.0);
		assertEquals(0.0, params.modeParams.get(SBBModes.PT).monetaryDistanceCostRate, 0.0);
	}
}
