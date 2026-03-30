package ch.sbb.matsim.plans.models.steps.csvmodels;

import ch.sbb.matsim.plans.models.selector.ChoiceSelector;
import ch.sbb.matsim.plans.models.selector.LogitSelector;
import ch.sbb.matsim.plans.models.steps.ChoiceModel;
import ch.sbb.matsim.plans.models.utils.ChoiceModelCSVReader;
import org.matsim.utils.objectattributes.attributable.Attributable;

public class IntegerSelectionModelFromCSV implements ChoiceModel {

    private final ChoiceSelector selector;

    /*        * @param csvDefinitionsFile the CSV file that defines the choice model. The file should have the following format:
     *                          Alternative,UtilityComponent1,UtilityComponent2,...
     * @param name a name for the model, used for logging and random seed generation. E.g. "driving_license_model".
     */

    public IntegerSelectionModelFromCSV(String csvDefinitionsFile, String name) {
        var calculatorsPerAlternative = ChoiceModelCSVReader.readChoiceModelFromCSV(csvDefinitionsFile);
        this.selector = new LogitSelector(calculatorsPerAlternative, 1.0, name);

    }


    @Override
    public Integer calculate(Attributable attributable) {
        return Integer.parseInt(this.selector.select(attributable));
    }
}
