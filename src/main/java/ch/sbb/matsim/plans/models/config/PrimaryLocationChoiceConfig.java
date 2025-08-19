package ch.sbb.matsim.plans.models.config;

import ch.sbb.matsim.plans.matrices.MatrixNames;
import ch.sbb.matsim.plans.synpop.DecisionStrata;

import java.util.Set;

public class PrimaryLocationChoiceConfig {

    private final Set<PrimaryLocationChoiceConfigRecord> primaryLocationChoiceConfigs;

    /**
     * Constructor for PrimaryLocationChoiceConfig.
     *
     * @param primaryLocationChoiceConfigs Set of PrimaryLocationChoiceConfigRecord containing configurations for each activity type.
     */
    public PrimaryLocationChoiceConfig(Set<PrimaryLocationChoiceConfigRecord> primaryLocationChoiceConfigs) {
        this.primaryLocationChoiceConfigs = primaryLocationChoiceConfigs;

    }

    public Set<PrimaryLocationChoiceConfigRecord> getPrimaryLocationChoiceConfigs() {
        return primaryLocationChoiceConfigs;
    }

    /**
     * A configuration record that holds all the specific details for running the
     * primary location choice model for a single activity type (e.g., "work").
     *
     * @param activityName              The name of the activity (e.g., "work", "education").
     * @param relevantStrata            The set of DecisionStrata that apply to this activity.
     * @param attractionAttributeName   The name of the ZonalAttribute to use for attraction (e.g., "jobs_total").
     * @param personResultAttributeName The name of the PersonAttribute where the chosen zone ID will be stored.
     * @param ipfIterations             Number of IPF Iteration
     * @param kalifMatrixName           Name of the kalif correction matrix
     */
    record PrimaryLocationChoiceConfigRecord(
            String activityName,
            Set<DecisionStrata> relevantStrata,
            String attractionAttributeName,
            String personResultAttributeName,
            int ipfIterations,
            MatrixNames.KalifMatrices kalifMatrixName
    ) {
    }
}


