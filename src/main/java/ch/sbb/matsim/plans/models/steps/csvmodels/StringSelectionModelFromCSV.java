package ch.sbb.matsim.plans.models.steps.csvmodels;

import ch.sbb.matsim.plans.models.selector.ChoiceSelector;
import ch.sbb.matsim.plans.models.selector.LogitSelector;
import ch.sbb.matsim.plans.models.steps.ChoiceModel;
import ch.sbb.matsim.plans.models.utils.ChoiceModelCSVReader;
import org.matsim.utils.objectattributes.attributable.Attributable;

public class StringSelectionModelFromCSV implements ChoiceModel {

    private final ChoiceSelector selector;

    public StringSelectionModelFromCSV(String ptSubscriptionModelDefinitionFile) {
        var calculatorsPerAlternative = ChoiceModelCSVReader.readChoiceModelFromCSV(ptSubscriptionModelDefinitionFile);
        this.selector = new LogitSelector(calculatorsPerAlternative, 1.0, getClass().getName());

    }


    @Override
    public String calculate(Attributable attributable) {
        return this.selector.select(attributable).toUpperCase();
    }

}
