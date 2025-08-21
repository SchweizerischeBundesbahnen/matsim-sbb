package ch.sbb.matsim.plans.models.config;

import ch.sbb.matsim.plans.matrices.MatrixNames;
import ch.sbb.matsim.plans.synpop.DecisionStrata;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.Set;

public class PrimaryLocationChoiceConfig {

    private final Set<ActivityConfigRecord> primaryLocationChoiceConfigs;

    public PrimaryLocationChoiceConfig(Set<ActivityConfigRecord> primaryLocationChoiceConfigs) {
        this.primaryLocationChoiceConfigs = primaryLocationChoiceConfigs;
    }

    public static PrimaryLocationChoiceConfig readFromYamlFile(String filePath) throws IOException {
        ObjectMapper mapper = new YAMLMapper();
        return mapper.readValue(new File(filePath), PrimaryLocationChoiceConfig.class);
    }

    public Set<ActivityConfigRecord> getPrimaryLocationChoiceConfigs() {
        return primaryLocationChoiceConfigs;
    }

    public record ActivityConfigRecord(
            String activityName,
            String personResultAttributeName,
            Map<DecisionStrata, StrataConfigRecord> strataConfigs
    ) {
    }

    /**
     * A detailed configuration record holding all parameters specific to a SINGLE DecisionStrata.
     * The matrixFactor is a Double object to allow it to be null when omitted from the YAML.
     */
    public record StrataConfigRecord(
            String attractionAttributeName,
            MatrixNames.KalifMatrices kalifMatrixName,
            int ipfIterations,
            Double matrixFactor
    ) {
        /**
         * A convenience getter that provides a default value for the matrix factor.
         * If matrixFactor is not specified in the YAML, it will be null, and we return 1.0.
         * Otherwise, we return the specified value.
         *
         * @return The matrix factor, defaulting to 1.0.
         */
        public double getMatrixFactor() {
            return matrixFactor == null ? 1.0 : matrixFactor;
        }
    }
}