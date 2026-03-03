package ch.sbb.matsim.plans.models.steps.csvmodels;

import ch.sbb.matsim.plans.models.selector.ChoiceSelector;
import ch.sbb.matsim.plans.models.selector.LogitSelector;
import ch.sbb.matsim.plans.models.steps.ChoiceModel;
import ch.sbb.matsim.plans.models.utils.ChoiceModelCSVReader;
import org.matsim.utils.objectattributes.attributable.Attributable;

/**
 * A choice model that selects a boolean value based on a CSV definition file. E.g, driving license model or e-bike model.
 */
public class BooleanSelectionModelFromCSV implements ChoiceModel {

    private final ChoiceSelector selector;

     /* @param csvDefinitionFile the CSV file that defines the choice model. The file should have the following format:
     *                          Alternative,UtilityComponent1,UtilityComponent2,...
     * @param name a name for the model, used for logging and random seed generation. E.g. "driving_license_model".
     */
    public BooleanSelectionModelFromCSV(String csvDefinitionFile, String name) {
        var calculatorsPerAlternative = ChoiceModelCSVReader.readChoiceModelFromCSV(csvDefinitionFile);
        this.selector = new LogitSelector(calculatorsPerAlternative, 1.0, name);
    }

    @Override
    public Boolean calculate(Attributable person) {
        return Boolean.parseBoolean(selector.select(person));
    }

}

