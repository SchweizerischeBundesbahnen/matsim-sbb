package ch.sbb.matsim.plans.models.selector;

import ch.sbb.matsim.plans.models.utilitycomponents.UtilityCalculator;
import ch.sbb.matsim.plans.models.utils.ChoiceModelCSVReader;
import org.matsim.utils.objectattributes.attributable.Attributable;

public class LinearRegressionModel implements RegressionModel {

    private final UtilityCalculator utilityCalculator;

    public LinearRegressionModel(UtilityCalculator utilityCalculators) {
        this.utilityCalculator = utilityCalculators;
    }

    @Override
    public double calculate(Attributable attributable){
        return utilityCalculator.calculate(attributable);
    }

    public static LinearRegressionModel createFromCSV(String filepath){
        return new LinearRegressionModel(ChoiceModelCSVReader.readRegressionModelFromCSV(filepath));
    }
}
