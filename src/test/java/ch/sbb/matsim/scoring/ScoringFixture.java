/*
 * Copyright (C) Schweizerische Bundesbahnen SBB, 2017.
 */

package ch.sbb.matsim.scoring;

import ch.sbb.matsim.config.SBBBehaviorGroupsConfigGroup;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.Population;
import org.matsim.api.core.v01.population.PopulationFactory;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.core.scoring.functions.ScoringParameters;

public class ScoringFixture {

    Config config;
    SBBBehaviorGroupsConfigGroup sbbConfig;
    Scenario scenario;

    private final static String GROUP1 = "Abobesitz";
    private final static String ATTRIBUTEGROUP1 = "season_ticket";
    private final static String VALUEGROUP1 = "Generalabo";

    private final static String GROUP2 = "Raumtypen";
    private final static String ATTRIBUTEGROUP2 = "raumtyp";
    private final static String VALUESGROUP2 = "2,5,6";
    private final static int VALUEGROUP2 = 2;

    private final static String GROUP3 = "Alter";
    private final static String ATTRIBUTEGROUP3 = "alter";
    private final static String VALUEGROUP3 = "25";

    ScoringFixture()    {
        this.config = ConfigUtils.createConfig();
        this.config.planCalcScore().getModes().get(TransportMode.pt).setConstant(-1.0);
        this.config.planCalcScore().getModes().get(TransportMode.pt).setMarginalUtilityOfTraveling(1.14);
        this.config.planCalcScore().getModes().get(TransportMode.pt).setMarginalUtilityOfDistance(0.0);
        this.config.planCalcScore().getModes().get(TransportMode.pt).setMonetaryDistanceRate(-0.000300);
        this.scenario = ScenarioUtils.createScenario(this.config);
        this.sbbConfig = ConfigUtils.addOrGetModule(this.config, SBBBehaviorGroupsConfigGroup.class);
    }

    ScoringParameters buildDefaultScoringParams(Id<Person> personId)   {
        SBBCharyparNagelScoringParametersForPerson psf = new SBBCharyparNagelScoringParametersForPerson(this.scenario);
        ScoringParameters params = psf.getScoringParameters(this.scenario.getPopulation().getPersons().get(personId));
        return params;
    }

    void addCustomScoringParams()   {
        // add behavior group 1
        SBBBehaviorGroupsConfigGroup.BehaviorGroupParams bgp = new SBBBehaviorGroupsConfigGroup.BehaviorGroupParams();
        bgp.setBehaviorGroupName(GROUP1);
        bgp.setPersonAttribute(ATTRIBUTEGROUP1);

        SBBBehaviorGroupsConfigGroup.PersonGroupAttributeValues pgt = new SBBBehaviorGroupsConfigGroup.PersonGroupAttributeValues();
        pgt.setPersonGroupAttributeValues(VALUEGROUP1);
        bgp.addPersonGroupByAttribute(pgt);

        SBBBehaviorGroupsConfigGroup.ModeCorrection modeCorrection = new SBBBehaviorGroupsConfigGroup.ModeCorrection();
        modeCorrection.setMode(TransportMode.pt);
        modeCorrection.setConstant(1.0);
        modeCorrection.setMargUtilOfTime(0.26);
        modeCorrection.setDistanceRate(0.000300);
        bgp.getPersonGroupByAttribute(VALUEGROUP1).addModeCorrection(modeCorrection);

        this.sbbConfig.addBehaviorGroupParams(bgp);

        // add behavior group 2
        bgp = new SBBBehaviorGroupsConfigGroup.BehaviorGroupParams();
        bgp.setBehaviorGroupName(GROUP2);
        bgp.setPersonAttribute(ATTRIBUTEGROUP2);

        pgt = new SBBBehaviorGroupsConfigGroup.PersonGroupAttributeValues();
        pgt.setPersonGroupAttributeValues(VALUESGROUP2);
        bgp.addPersonGroupByAttribute(pgt);

        modeCorrection = new SBBBehaviorGroupsConfigGroup.ModeCorrection();
        modeCorrection.setMode(TransportMode.pt);
        modeCorrection.setConstant(-0.3);
        pgt.addModeCorrection(modeCorrection);

        this.sbbConfig.addBehaviorGroupParams(bgp);

        // add behavior group 3
        bgp = new SBBBehaviorGroupsConfigGroup.BehaviorGroupParams();
        bgp.setBehaviorGroupName(GROUP3);
        bgp.setPersonAttribute(ATTRIBUTEGROUP3);

        pgt = new SBBBehaviorGroupsConfigGroup.PersonGroupAttributeValues();
        pgt.setPersonGroupAttributeValues(VALUEGROUP3);
        bgp.addPersonGroupByAttribute(pgt);

        modeCorrection = new SBBBehaviorGroupsConfigGroup.ModeCorrection();
        modeCorrection.setMode(TransportMode.pt);
        modeCorrection.setConstant(0.5);
        bgp.getPersonGroupByAttribute(VALUEGROUP3).addModeCorrection(modeCorrection);

        this.sbbConfig.addBehaviorGroupParams(bgp);
    }

    void addPersonNoAttribute() {
        Population population = this.scenario.getPopulation();
        PopulationFactory pf = population.getFactory();
        Person person = pf.createPerson(Id.create(1, Person.class));
        this.scenario.getPopulation().addPerson(person);
    }

    void personOneAttribute() {
        Population population = this.scenario.getPopulation();
        PopulationFactory pf = population.getFactory();
        Person person = pf.createPerson(Id.create(2, Person.class));

        person.getAttributes().putAttribute(ATTRIBUTEGROUP1,VALUEGROUP1);
        this.scenario.getPopulation().addPerson(person);
    }

    void personTwoAttribute() {
        Population population = this.scenario.getPopulation();
        PopulationFactory pf = population.getFactory();
        Person person = pf.createPerson(Id.create(3, Person.class));

        person.getAttributes().putAttribute(ATTRIBUTEGROUP1,VALUEGROUP1);
        person.getAttributes().putAttribute(ATTRIBUTEGROUP2,VALUEGROUP2);
        this.scenario.getPopulation().addPerson(person);
    }

    void personThreeAttribute() {
        Population population = this.scenario.getPopulation();
        PopulationFactory pf = population.getFactory();
        Person person = pf.createPerson(Id.create(4, Person.class));

        person.getAttributes().putAttribute(ATTRIBUTEGROUP1,VALUEGROUP1);
        person.getAttributes().putAttribute(ATTRIBUTEGROUP2,VALUEGROUP2);
        person.getAttributes().putAttribute(ATTRIBUTEGROUP3,VALUEGROUP3);
        this.scenario.getPopulation().addPerson(person);
    }
}
