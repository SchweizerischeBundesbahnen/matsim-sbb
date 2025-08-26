package ch.sbb.matsim.plans.models;

import ch.sbb.matsim.config.variables.Variables;
import ch.sbb.matsim.plans.matrices.DefaultMatrices;
import ch.sbb.matsim.plans.matrices.Matrices;
import ch.sbb.matsim.plans.matrices.MatrixReaders;
import ch.sbb.matsim.plans.models.config.PrimaryLocationChoiceConfig;
import ch.sbb.matsim.plans.models.formulas.FormulaRepository;
import ch.sbb.matsim.plans.synpop.SynPopUtils;
import ch.sbb.matsim.zones.ZoneGeoPackageWriter;
import ch.sbb.matsim.zones.Zones;
import ch.sbb.matsim.zones.ZonesLoader;
import org.apache.logging.log4j.LogManager;
import org.matsim.api.core.v01.Scenario;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.population.io.PopulationReader;
import org.matsim.core.population.io.PopulationWriter;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.households.HouseholdsReaderV10;

import java.io.File;
import java.io.IOException;

public class RunPrimaryLocationChoiceModel {

    public static void main(String[] args) throws IOException {
        var logger = LogManager.getLogger(RunPrimaryLocationChoiceModel.class);

        String inputFolder = "Y:\\40_Projekte\\20250408_mobi_plans_6.0\\test_input_synpop\\synpop_with_mobility_tools\\";
        String outputFolder = inputFolder + "..\\synpop_with_longterm_location_choice\\";
        new File(outputFolder).mkdirs();
        //        String suffix = ""; //whole population
        //        String suffix = "_Bern"; //City of Bern
        String suffix = "_AI"; //Appenzell Inner Rhodes
        String modelFolder = "\\\\wsbbrz0283\\mobi\\40_Projekte\\20250408_mobi_plans_6.0\\choice_models\\";

        Scenario scenario = ScenarioUtils.createScenario(ConfigUtils.createConfig());
        new PopulationReader(scenario).readFile(inputFolder + "persons_2022" + suffix + "_with_mobilitytools.xml.gz");
        new HouseholdsReaderV10(scenario.getHouseholds()).readFile(inputFolder + "households_2022" + suffix + "_with_mobilitytools.xml.gz");
        Zones zones = ZonesLoader.loadZones("zones", inputFolder + "zones_2022" + suffix + "_with_mobilitytools.gpkg");
        scenario.addScenarioElement(Variables.ZONES, zones);

        SynPopUtils.linkHouseholdsAndPopulation(scenario);
        SynPopUtils.linkZonesToHouseHoldsAndPersons(scenario);
        SynPopUtils.calculateHouseHoldCompositions(scenario);

        FormulaRepository formulaRepository = new FormulaRepository("\\\\wsbbrz0283\\mobi\\40_Projekte\\20250408_mobi_plans_6.0\\choice_models\\mode_impedances_per_activity.csv");
        scenario.addScenarioElement("modeImpedanceFormulas", formulaRepository);
        PrimaryLocationChoiceConfig primaryLocationChoiceConfig = PrimaryLocationChoiceConfig.readFromYamlFile("\\\\wsbbrz0283\\mobi\\40_Projekte\\20250408_mobi_plans_6.0\\choice_models\\primary_location_choice_config.yaml");
        scenario.addScenarioElement("primaryLocationChoiceConfig", primaryLocationChoiceConfig);

        Matrices matrices = new DefaultMatrices();
        //reading matrices from hard disk is much faster than from network drive
        MatrixReaders.readAndProcessCarSkims("C:\\devsbb\\tmp\\MIV-V101\\car_skims.omx", matrices);
        MatrixReaders.readPTSkims("C:\\devsbb\\tmp\\NPVM2023\\pt_skims.omx", matrices);
        MatrixReaders.readKalifMatrices("\\\\wsbbrz0283\\mobi\\40_Projekte\\20241104_MOBi_5.1\\kalif\\kalif_2023_mobi_51.omx", matrices);
        scenario.addScenarioElement(Variables.MATRICES, matrices);
        logger.info("Starting primary location choice model...");
        PrimaryLocationChoiceModel plcModel = new PrimaryLocationChoiceModel(scenario);
        plcModel.run();
        logger.info("Primary location choice model completed.");
        SynPopUtils.aggregatePrimaryLocationChoicePerZone(scenario);
        new PopulationWriter(scenario.getPopulation()).write(outputFolder + "persons_2022" + suffix + "_with_longterm_location_choice.xml.gz");
        ZoneGeoPackageWriter.writeGeoPackage(zones, outputFolder + "zones_2022" + suffix + "_with_longterm_location_choice.gpkg");
    }
}
