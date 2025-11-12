package ch.sbb.matsim.plans.synpop.loading;

import ch.sbb.matsim.plans.MOBiPlansRunner;
import ch.sbb.matsim.plans.models.ModelStep;
import ch.sbb.matsim.plans.synpop.loading.arrow.BusinessesReader;
import ch.sbb.matsim.plans.synpop.loading.arrow.HouseholdsReader;
import ch.sbb.matsim.plans.synpop.SynPopUtils;
import ch.sbb.matsim.plans.synpop.loading.arrow.SynpopReader;
import ch.sbb.matsim.plans.synpop.loading.csv.BusinessesCsvReader;
import ch.sbb.matsim.plans.synpop.loading.csv.HouseholdsCsvReader;
import ch.sbb.matsim.plans.synpop.loading.csv.SynpopCsvReader;
import com.google.inject.Inject;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.matsim.api.core.v01.Scenario;
import ch.sbb.matsim.plans.models.config.MOBiPlansConfig;
import org.matsim.core.population.io.PopulationReader;
import org.matsim.facilities.MatsimFacilitiesReader;
import org.matsim.households.HouseholdsReaderV10;

import java.io.File;
import java.util.List;

public class SynpopLoader implements ModelStep {
    private final static Logger logger = LogManager.getLogger(SynpopLoader.class);
    private final MOBiPlansConfig config;
    private final Scenario scenario;
    private final String name = MOBiPlansRunner.LOAD_SYNPOP;
    @Inject
    public SynpopLoader(MOBiPlansConfig config, Scenario scenario) {
        this.config = config;
        this.scenario = scenario;
    }

    @Override
    public void run() {
        // Access the list of synpop directories from the config
        List<String> synpopDirs = config.data().synpop();
        if (synpopDirs != null && !synpopDirs.isEmpty()) {
            logger.info("Reading synthetic population from configured directories via ARROW files");
            readFromDirectories(synpopDirs);
        }
        else {
            logger.info("No synthetic population directories configured. Loading population from standard MATSim XML files.");
            readFromMatsimFiles(config.data().populationFile(), config.data().householdsFile(), config.data().facilitiesFile());
        }

        SynPopUtils.linkHouseholdsAndPopulation(scenario);
        SynPopUtils.linkZonesToHouseHoldsAndPersons(scenario);
        SynPopUtils.calculateHouseHoldCompositions(scenario);
        logger.info("Finished loading. Total persons: " + scenario.getPopulation().getPersons().size());
    }

    private void readFromMatsimFiles(String populationFile, String householdsFile, String facilitiesFile) {
        if (populationFile == null || householdsFile == null || facilitiesFile == null) {
            throw new IllegalArgumentException("Population, households, and facilities file paths must be provided in the configuration.");
        }
        File popFile = new File(populationFile);
        if (!popFile.exists()) {
            throw new IllegalArgumentException("Population file does not exist: " + populationFile);
        }
        File householdsFilePath = new File(householdsFile);
        if (!householdsFilePath.exists()) {
            throw new IllegalArgumentException("Households file does not exist: " + householdsFile);
            }
        File facFile = new File(facilitiesFile);
        if (!facFile.exists()) {
            throw new IllegalArgumentException("Facilities file does not exist: " + facilitiesFile);
        }
        new PopulationReader(scenario).readFile(populationFile);
        new HouseholdsReaderV10(scenario.getHouseholds()).readFile(householdsFile);
        new MatsimFacilitiesReader(scenario).readFile(facilitiesFile);
    }

    private void readFromDirectories(List<String> synpopDirs) {
        for (String dir : synpopDirs) {
            File directory = new File(dir);
            if (!directory.exists() || !directory.isDirectory()) {
                logger.warn("Synpop directory does not exist or is not a directory: " + dir);
                continue;
            }

            File[] arrowFiles = directory.listFiles((d, name) -> name.toLowerCase().endsWith(".arrow") || name.toLowerCase().endsWith(".feather"));
            if (arrowFiles != null) {
                for (File arrowFile : arrowFiles) {
                    if (arrowFile.getAbsolutePath().toLowerCase().contains("households")) {
                        logger.info("Reading households from ARROW file: " + arrowFile.getAbsolutePath());
                        HouseholdsReader.readHouseholdFile(arrowFile, scenario.getHouseholds());
                    } else if (arrowFile.getAbsolutePath().toLowerCase().contains("persons")) {
                        logger.info("Reading persons from ARROW file: " + arrowFile.getAbsolutePath());
                        SynpopReader.readSynpopFile(arrowFile, scenario.getPopulation());
                    } else if (arrowFile.getAbsolutePath().toLowerCase().contains("businesses")) {
                        logger.info("Reading Businesses from ARROW file: " + arrowFile.getAbsolutePath());
                        BusinessesReader.readBusinessesFile(arrowFile, scenario.getActivityFacilities());
                    }
                }
            } else {
                logger.info("No ARROW files found in directory: " + dir);
            }
            File[] csvFiles = directory.listFiles((d, name) -> name.toLowerCase().endsWith(".csv"));
            if (csvFiles != null) {
                for (File csvFile : csvFiles) {
                    if (csvFile.getAbsolutePath().toLowerCase().contains("households")) {
                        logger.info("Reading households from CSV file: " + csvFile.getAbsolutePath());
                        HouseholdsCsvReader.readHouseholdCsvFile(csvFile, scenario.getHouseholds());
                    } else if (csvFile.getAbsolutePath().toLowerCase().contains("persons")) {
                        logger.info("Reading persons from CSV file: " + csvFile.getAbsolutePath());
                        SynpopCsvReader.readSynpopCsvFile(csvFile, scenario.getPopulation());
                    } else if (csvFile.getAbsolutePath().toLowerCase().contains("businesses")) {
                        logger.info("Reading Businesses from CSV file: " + csvFile.getAbsolutePath());
                        BusinessesCsvReader.readBusinessesCsvFile(csvFile, scenario.getActivityFacilities());
                    }
                }
            } else {
                logger.info("No CSV files found in directory: " + dir);
            }

            if ((arrowFiles == null || arrowFiles.length == 0) && (csvFiles == null || csvFiles.length == 0)) {
                logger.warn("No ARROW or CSV files found in directory: " + dir);
            }
        }
    }

    @Override
    public String getName() {
        return name;
    }
}