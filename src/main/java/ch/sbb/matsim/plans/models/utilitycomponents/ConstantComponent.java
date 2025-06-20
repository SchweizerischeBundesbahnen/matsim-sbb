package ch.sbb.matsim.plans.models.utilitycomponents;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.matsim.utils.objectattributes.attributable.Attributable;


public class ConstantComponent implements UtilityComponent {

    private static final Logger log = LogManager.getLogger(ConstantComponent.class);
    private final double constant;

    public ConstantComponent(double constant) {
        this.constant = constant;
    }

    @Override
    public double calculate(Attributable attributable) {
//        log.debug("ConstantComponent: value=" + constant);
        return constant;
    }
}
