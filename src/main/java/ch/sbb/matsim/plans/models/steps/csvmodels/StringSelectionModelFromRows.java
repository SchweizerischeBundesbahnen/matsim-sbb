package ch.sbb.matsim.plans.models.steps.csvmodels;

import ch.sbb.matsim.plans.models.selector.BinaryLinearProbabilitySelector;
import ch.sbb.matsim.plans.models.selector.ChoiceSelector;
import ch.sbb.matsim.plans.models.steps.ChoiceModel;
import ch.sbb.matsim.plans.models.utils.ChoiceModelCSVReader;
import org.matsim.utils.objectattributes.attributable.Attributable;

import java.util.ArrayList;
import java.util.Map;

public class StringSelectionModelFromRows implements ChoiceModel {

    private final ChoiceSelector selector;

    /* @param csvDefinitionFile the CSV file that defines the choice model. The file should have the following format:
     *                          Alternative,UtilityComponent1,UtilityComponent2,...
     * @param name a name for the model, used for logging and random seed generation. E.g. "driving_license_model".
     */
    public StringSelectionModelFromRows(ArrayList<Map<String, String>> rows, String name) {
        var calculatorsPerAlternative = ChoiceModelCSVReader.getChoiceModelFromRows(rows);
        this.selector = new BinaryLinearProbabilitySelector(calculatorsPerAlternative, name);
    }

    @Override
    public String calculate(Attributable person) {
        return selector.select(person);
    }

    @Override
    public double getProbabilityForAlternative(Attributable attributable, String alternative) {
        return selector.getProbabilities(attributable).getOrDefault(alternative, 0.0);
    }

}