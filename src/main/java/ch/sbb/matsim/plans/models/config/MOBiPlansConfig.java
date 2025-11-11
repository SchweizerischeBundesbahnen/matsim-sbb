package ch.sbb.matsim.plans.models.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;

import java.io.File;
import java.io.IOException;
import java.util.List;



public record MOBiPlansConfig (
        DataConfig data,
        ModelsConfig models
){
    public static final String DRIVING_LICENSE_CSV = "driving_license.csv";
    public static final String EBIKE_45_CSV = "ebike45.csv";
    public static final String CARS_PER_HOUSEHOLD_CSV = "cars_per_household.csv";
    public static final String PT_SUBSCRIPTION_ADULTS_CSV = "pt_subscription_adults.csv";
    public static final String PT_SUBSCRIPTION_MINORS_CSV = "pt_subscription_minors.csv";

    /**
     * Reads and parses the master configuration from a YAML file.
     *
     * @param filePath The path to the master config.yaml file.
     * @return A fully populated, immutable MasterConfig object.
     * @throws IOException if there is an error reading or parsing the file.
     */
    public static MOBiPlansConfig readFromYaml(String filePath) throws IOException {
        ObjectMapper mapper = new YAMLMapper();
        return mapper.readValue(new File(filePath), MOBiPlansConfig.class);
    }


/**
 * A record holding all data source paths.
 * Field names must exactly match the keys in the YAML file's "data" section.
 */
public record DataConfig(
        List<String> synpop,
        String populationFile,
        String householdsFile,
        String zonesFile,
        String facilitiesFile,
        String carSkimsFile,
        String ptSkimsFile,
        String kalifMatricesFile,
        String modelBaseDir,
        String outputBaseDir
) {}

public record ModelsConfig(
        int threads,
        List<String> modelsToRun
) {}

}