package ch.sbb.matsim.plans.models;

import ch.sbb.matsim.plans.models.selector.ChoiceSelector;
import ch.sbb.matsim.plans.models.selector.LogitSelector;
import ch.sbb.matsim.plans.models.utils.ChoiceModelCSVReader;
import org.matsim.utils.objectattributes.attributable.Attributable;

public class DrivingLicenseModelFromCSV implements ChoiceModel {

    private final ChoiceSelector selector;

    public DrivingLicenseModelFromCSV(String drivingLicenseModelDefinitionFile) {
        var calculatorsPerAlternative = ChoiceModelCSVReader.readChoiceModelFromCSV(drivingLicenseModelDefinitionFile);
        this.selector = new LogitSelector(calculatorsPerAlternative, 1.0);
    }

    @Override
    public Boolean calculate(Attributable person) {
        return Boolean.parseBoolean(selector.select(person));
    }

}

