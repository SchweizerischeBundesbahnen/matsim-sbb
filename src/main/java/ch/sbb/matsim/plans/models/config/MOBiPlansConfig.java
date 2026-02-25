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
    public static final String TOUR_FREQUENCY_WORK_MODEL_CSV = "tour_frequency_work.csv";
    public static final String TOUR_FREQUENCY_EDUCATION_MODEL_CSV = "tour_frequency_education.csv";
    public static final String TOUR_FREQUENCY_OTHER_MODEL_CSV = "tour_frequency_other.csv";
    public static final String TOUR_FREQUENCY_BUSINESS_MODEL_CSV = "tour_frequency_business.csv";
    public static final String EBIKE_45_CSV = "ebike45.csv";
    public static final String CARS_PER_HOUSEHOLD_CSV = "cars_per_household.csv";
    public static final String PT_SUBSCRIPTION_ADULTS_CSV = "pt_subscription_adults.csv";
    public static final String PT_SUBSCRIPTION_MINORS_CSV = "pt_subscription_minors.csv";
    public static final String MODE_IMPEDANCE_FORMULAS = "mode_impedance_formulas.csv";
    public static final String PRIMARY_LOCATION_CHOICE_CONFIG_YAML = "primary_location_choice_config.yaml";
    public static final String NUMBER_OF_STOPS_INBOUND_WORK_CSV = "stop_freq_inb_w.csv";
    public static final String NUMBER_OF_STOPS_OUTBOUND_WORK_CSV = "stop_freq_outb_w.csv";
    public static final String NUMBER_OF_STOPS_INBOUND_EDUCATION_CSV = "stop_freq_inb_e.csv";
    public static final String NUMBER_OF_STOPS_OUTBOUND_EDUCATION_CSV = "stop_freq_outb_e.csv";
    public static final String NUMBER_OF_STOPS_BUSINESS_CSV = "stop_freq_b.csv";
    public static final String NUMBER_OF_STOPS_OTHER_CSV = "stop_freq_o.csv";
    public static final String SUBTOUR_FREQUENCY_WORK_CSV = "subtour_frequency_work.csv";
    public static final String SUBTOUR_FREQUENCY_EDUCATION_CSV = "subtour_frequency_education.csv";
    public static final String SECONDARY_ACTIVITY_TYPE_ASSIGNMENT_CSV = "secondary_activity_type_likelihoods.csv";

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
        String outputBaseDir,
        String sff_attributes,
        String school_enr_apprentices,
        String foreign_attractions,
        String zone_informations

) {
}
    public record ModelsConfig(
            int threads,
            boolean write_analysis,
            String run_id,
            Object run
    ) {
    }

}