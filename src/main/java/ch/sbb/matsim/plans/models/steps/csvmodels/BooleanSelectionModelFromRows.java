package ch.sbb.matsim.plans.models.steps.csvmodels;

import ch.sbb.matsim.plans.models.selector.ChoiceSelector;
import ch.sbb.matsim.plans.models.selector.LogitSelector;
import ch.sbb.matsim.plans.models.steps.ChoiceModel;
import ch.sbb.matsim.plans.models.utils.ChoiceModelCSVReader;
import org.matsim.utils.objectattributes.attributable.Attributable;

import java.util.ArrayList;
import java.util.Map;

public class BooleanSelectionModelFromRows implements ChoiceModel {

    private final ChoiceSelector selector;

    /* @param csvDefinitionFile the CSV file that defines the choice model. The file should have the following format:
     *                          Alternative,UtilityComponent1,UtilityComponent2,...
     * @param name a name for the model, used for logging and random seed generation. E.g. "driving_license_model".
     */
    public BooleanSelectionModelFromRows(ArrayList<Map<String, String>> rows, String name) {
        var calculatorsPerAlternative = ChoiceModelCSVReader.getChoiceModelFromRows(rows);
        this.selector = new LogitSelector(calculatorsPerAlternative, name);
    }

    @Override
    public Boolean calculate(Attributable person) {
        return Boolean.parseBoolean(selector.select(person));
    }

    @Override
    public double getProbabilityForAlternative(Attributable attributable, String alternative) {
        return selector.getProbabilities(attributable).getOrDefault(alternative, 0.0);
    }

}