package ch.sbb.matsim.plans.models;

import ch.sbb.matsim.plans.models.selector.ChoiceSelector;
import ch.sbb.matsim.plans.models.selector.LogitSelector;
import ch.sbb.matsim.plans.models.utils.ChoiceModelCSVReader;
import org.matsim.utils.objectattributes.attributable.Attributable;

public class CarsPerHouseholdModel implements ChoiceModel {

    private final ChoiceSelector selector;

    public CarsPerHouseholdModel(String carsPerHouseholdModelDefinitionFile) {
        var calculatorsPerAlternative = ChoiceModelCSVReader.readChoiceModelFromCSV(carsPerHouseholdModelDefinitionFile);
        this.selector = new LogitSelector(calculatorsPerAlternative, 1.0);

    }

    @Override
    public Integer calculate(Attributable attributable) {
        return Integer.parseInt(selector.select(attributable));
    }
}
