package ch.sbb.matsim.plans.models.utilitycomponents;

import ch.sbb.matsim.plans.models.resolver.AttributeResolver;
import ch.sbb.matsim.plans.models.resolver.NestedAttributeResolver;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.matsim.utils.objectattributes.attributable.Attributable;

public class LogarithmicComponent implements UtilityComponent {

    private static final Logger log = LogManager.getLogger(LogarithmicComponent.class);

    private final String attributePath;
    private final double beta;
    private final AttributeResolver resolver;
    private final boolean nullable;

    public LogarithmicComponent(String attributePath, double beta, AttributeResolver resolver,boolean nullable) {
        this.attributePath = attributePath;
        this.beta = beta;
        this.resolver = resolver;
        this.nullable = nullable;
    }

    public LogarithmicComponent(String attributePath, double beta, boolean nullable) {
        this(attributePath, beta, new NestedAttributeResolver(), nullable);
    }

    @Override
    public double calculate(Attributable attributable) {
        Object attribute = resolver.resolve(attributable, attributePath);
        double value = ((Number) attribute).doubleValue();
        if (value < 0) {
            throw new IllegalArgumentException("Value must be greater than zero for logarithmic calculation.");
        }
        if (value == 0) {
//            log.debug("LogarithmicComponent: attributePath=" + attributePath + ", attribute=" + attribute + ", value=0.0");
            return 0.0;
        }
        double result = Math.log(value) * beta;
//        log.debug("LogarithmicComponent: attributePath=" + attributePath + ", attribute=" + attribute + ", value=" + result);
        return result;
    }
}
