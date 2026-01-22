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

    public BooleanSelectionModelFromCSV(String modeldefinitionFile) {
        var calculatorsPerAlternative = ChoiceModelCSVReader.readChoiceModelFromCSV(modeldefinitionFile);
        this.selector = new LogitSelector(calculatorsPerAlternative, 1.0, getClass().getName());
    }

    @Override
    public Boolean calculate(Attributable person) {
        return Boolean.parseBoolean(selector.select(person));
    }

}

