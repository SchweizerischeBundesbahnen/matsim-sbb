package ch.sbb.matsim.plans.models.steps.csvmodels;

import ch.sbb.matsim.plans.models.selector.ChoiceSelector;
import ch.sbb.matsim.plans.models.selector.LogitSelector;
import ch.sbb.matsim.plans.models.steps.ChoiceModel;
import ch.sbb.matsim.plans.models.utils.ChoiceModelCSVReader;
import org.matsim.utils.objectattributes.attributable.Attributable;

public class IntegerSelectionModelFromCSV implements ChoiceModel {

    private final ChoiceSelector selector;

    public IntegerSelectionModelFromCSV(String ptSubscriptionModelDefinitionFile) {
        var calculatorsPerAlternative = ChoiceModelCSVReader.readChoiceModelFromCSV(ptSubscriptionModelDefinitionFile);
        this.selector = new LogitSelector(calculatorsPerAlternative, 1.0, getClass().getName());

    }


    @Override
    public Integer calculate(Attributable attributable) {
        return Integer.parseInt(this.selector.select(attributable));
    }
}
