package ch.sbb.matsim.plans.models.config;

import ch.sbb.matsim.plans.matrices.MatrixNames;
import ch.sbb.matsim.plans.models.steps.demandstrata.DemandStrata;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the PrimaryLocationChoiceConfig class, focusing on its ability
 * to correctly read and parse a YAML configuration file.
 */
// Use PER_CLASS lifecycle to allow @BeforeAll on a non-static method
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PrimaryLocationChoiceConfigTest {

    // @TempDir creates a temporary directory for our test files and cleans it up automatically.
    @TempDir
    Path tempDir;

    private File testYamlFile;
    private PrimaryLocationChoiceConfig loadedConfig;

    /**
     * Sets up the test by creating a representative YAML file in the temporary directory
     * and loading it once for all tests. This is more efficient than reading the file in every test.
     */
    @BeforeEach
    void setup() throws IOException {
        testYamlFile = tempDir.resolve("test_config.yaml").toFile();

        // This YAML string is a perfect representation of our configuration needs.
        // It includes two activities, multiple strata, and both presence and absence of the 'matrixFactor'.
        String yamlContent = """
                primaryLocationChoiceConfigs:
                  - activityName: "work"
                    personResultAttributeName: "work_zone_no"
                    strataConfigs:
                      # This strata has NO matrixFactor, so it should default to 1.0
                      test_strata_without_matrix_factor:
                        attractionAttributeName: "jobs_endo"
                        kalifMatrixName: "KALIF_WORK"
                        ipfIterations: 10
                      # This strata has a specific matrixFactor
                      test_strata_with_matrix_factor:
                        attractionAttributeName: "jobs_exo"
                        kalifMatrixName: "KALIF_WORK_CB"
                        matrixFactor: 0.75
                        ipfIterations: 1
                  - activityName: "education"
                    personResultAttributeName: "school_zone_no"
                    strataConfigs:
                      test_strata_school:
                        attractionAttributeName: "schl_enr_1"
                        kalifMatrixName: "KALIF_EDUCATION_PRIM"
                        ipfIterations: 5
                """;

        Files.writeString(testYamlFile.toPath(), yamlContent);

        // Load the configuration once for use in all test methods
        loadedConfig = PrimaryLocationChoiceConfig.readFromYamlFile(testYamlFile.getAbsolutePath());
    }

    @Test
    void test_readFromYaml_loadsCorrectNumberOfActivities() {
        assertNotNull(loadedConfig, "Config should not be null after loading.");
        assertEquals(2, loadedConfig.getPrimaryLocationChoiceConfigs().size(),
                "Should have loaded exactly 2 activity configurations (work and education).");
    }

    @Test
    void test_readFromYaml_parsesWorkActivityCorrectly() {
        // Find the "work" configuration in the set
        Optional<PrimaryLocationChoiceConfig.ActivityConfigRecord> workConfigOpt = loadedConfig.getPrimaryLocationChoiceConfigs().stream()
                .filter(c -> "work".equals(c.activityName()))
                .findFirst();

        assertTrue(workConfigOpt.isPresent(), "A configuration for 'work' activity should exist.");
        PrimaryLocationChoiceConfig.ActivityConfigRecord workConfig = workConfigOpt.get();

        // Assert basic properties
        assertEquals("work_zone_no", workConfig.personResultAttributeName());
        assertEquals(2, workConfig.strataConfigs().size(), "Work config should contain 2 strata configurations.");

        // Assert details of a specific strata
        PrimaryLocationChoiceConfig.StrataConfigRecord noAboCarStrata = workConfig.strataConfigs().get("test_strata_without_matrix_factor");
        assertNotNull(noAboCarStrata, "Strata config for test_strata should exist.");
        assertEquals("jobs_endo", noAboCarStrata.attractionAttributeName());
        assertEquals(MatrixNames.KalifMatrices.KALIF_WORK, noAboCarStrata.kalifMatrixName());
        assertEquals(10, noAboCarStrata.ipfIterations());
    }

    @Test
    void test_getMatrixFactor_returnsDefaultWhenNull() {
        // Get the specific strata config that we know has no matrixFactor defined in the YAML
        PrimaryLocationChoiceConfig.StrataConfigRecord strataConfig = loadedConfig.getPrimaryLocationChoiceConfigs().stream()
                .filter(c -> "work".equals(c.activityName())).findFirst().get()
                .strataConfigs().get("test_strata_without_matrix_factor");

        // The 'matrixFactor' field itself should be null
        assertNull(strataConfig.matrixFactor(), "The raw matrixFactor field should be null when omitted from YAML.");

        // The convenience getter should return the default value
        assertEquals(1.0, strataConfig.getMatrixFactor(),
                "getMatrixFactor() should return the default value of 1.0 when the field is not specified.");
    }

    @Test
    void test_getMatrixFactor_returnsSpecifiedValue() {
        // Get the specific strata config that we know has a matrixFactor defined
        PrimaryLocationChoiceConfig.StrataConfigRecord strataConfig = loadedConfig.getPrimaryLocationChoiceConfigs().stream()
                .filter(c -> "work".equals(c.activityName())).findFirst().get()
                .strataConfigs().get("test_strata_with_matrix_factor");

        // The convenience getter should return the specified value
        assertEquals(0.75, strataConfig.getMatrixFactor(),
                "getMatrixFactor() should return the value specified in the YAML.");
    }

    @Test
    void test_readFromYaml_throwsExceptionForMissingFile() {
        String nonExistentFilePath = tempDir.resolve("non_existent_file.yaml").toString();

        // Assert that an Exception is thrown when the file does not exist
        assertThrows(Exception.class, () -> {
            PrimaryLocationChoiceConfig.readFromYamlFile(nonExistentFilePath);
        }, "Should throw an IOException for a file that does not exist.");
    }

    @Test
    void test_readFromYaml_throwsExceptionForInvalidEnum() throws IOException {
        File badEnumFile = tempDir.resolve("bad_enum.yaml").toFile();
        // This YAML contains an invalid value for the KalifMatrices enum
        String yamlContent = """
                primaryLocationChoiceConfigs:
                  - activityName: "work"
                    personResultAttributeName: "work_zone_no"
                    strataConfigs:
                      test_strata:
                        attractionAttributeName: "jobs_endo"
                        kalifMatrixName: "THIS_IS_NOT_A_VALID_ENUM_VALUE"
                        ipfIterations: 10
                """;
        Files.writeString(badEnumFile.toPath(), yamlContent);

        // Jackson will throw an exception (typically a subtype of IOException) when it fails to map the string to an enum
        assertThrows(Exception.class, () -> {
            PrimaryLocationChoiceConfig.readFromYamlFile(badEnumFile.getAbsolutePath());
        }, "Should throw an exception when a string cannot be mapped to an enum constant.");
    }
}