package ch.sbb.matsim.plans.models;

import ch.sbb.matsim.plans.models.utilitycomponents.*;
import ch.sbb.matsim.plans.synpop.SynpopConstants;
import org.matsim.api.core.v01.Scenario;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.population.io.PopulationReader;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.households.HouseholdsReaderV10;

import java.util.Collections;
import java.util.List;

public class DrivingLicenseModel {
    private final UtilityCalculator hasDrivingLicenseCalculator;
    private final UtilityCalculator noDrivingLicenseCalculator;



    public DrivingLicenseModel() {
        List<UtilityComponent> utilityComponentsHasLicense = new java.util.ArrayList<>();
        utilityComponentsHasLicense.add(new PiecewiseCalculator(SynpopConstants.AGE, new double[]{22, 26, 69, 89}, new double[]{0.398, 0.0301, 0.0201, -0.118, -0.19}));
        utilityComponentsHasLicense.add(new LinearCalculator(SynpopConstants.IS_SWISS, 0.635));
        utilityComponentsHasLicense.add(UtilityComponentFactory.fromCondition(SynpopConstants.LANGUAGE, "==", "2", 0.156));
        utilityComponentsHasLicense.add(UtilityComponentFactory.fromCondition(SynpopConstants.LEVEL_OF_EMPLOYMENT, ">=", "90", 1.54));
        utilityComponentsHasLicense.add(UtilityComponentFactory.fromCombinedCondition(SynpopConstants.LEVEL_OF_EMPLOYMENT, List.of("<", ">"),
                List.of("90", "0"),
                0.34));
        utilityComponentsHasLicense.add(UtilityComponentFactory.fromCondition("household.couple_type", "==", "1", 0.57));
        utilityComponentsHasLicense.add(UtilityComponentFactory.fromCondition("household.couple_type", "==", "2", 0.551));
        utilityComponentsHasLicense.add(new BoxCoxCalculator("zone.accsib_pt_scaled", -0.892, 0.1774));
        utilityComponentsHasLicense.add(new BoxCoxCalculator("zone.accsib_multi_scaled", -0.892, 0.1774));
        utilityComponentsHasLicense.add(new LinearCalculator("zone.pc_car", -0.785));
        utilityComponentsHasLicense.add(new LogarithmicCalculator("zone.pc_car", 0.373));
        utilityComponentsHasLicense.add(UtilityComponentFactory.fromCondition("zone.pc_car", "==", "0.0", -0.598));
        this.hasDrivingLicenseCalculator = new UtilityCalculator(
                utilityComponentsHasLicense);


        List<UtilityComponent> utilityComponentsHasNoLicense = Collections.emptyList();
        //all betas are zero, so no need to add any components
        this.noDrivingLicenseCalculator = new UtilityCalculator(utilityComponentsHasNoLicense);


    }

    public static void main(String[] args) {
        Scenario scenario = ScenarioUtils.createScenario(ConfigUtils.createConfig());
        new PopulationReader(scenario).readFile("C:\\devsbb\\tmp\\synpop_2022\\persons_2022.xml.gz");
        new HouseholdsReaderV10(scenario.getHouseholds()).readFile("C:\\devsbb\\tmp\\synpop_2022\\households_2022.xml.gz");

    }

}
