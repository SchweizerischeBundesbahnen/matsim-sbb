package ch.sbb.matsim.plans.models.utilitycomponents;

import ch.sbb.matsim.plans.models.resolver.AttributeResolver;
import ch.sbb.matsim.plans.models.resolver.NestedAttributeResolver;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.matsim.utils.objectattributes.attributable.Attributable;

public class BoxCoxComponent implements UtilityComponent {

    private static final Logger log = LogManager.getLogger(BoxCoxComponent.class);

    private final String attributePath;
    private final double beta;
    private final double lambda;
    private final AttributeResolver resolver;

    public BoxCoxComponent(String attributePath, double beta, double lambda, AttributeResolver resolver) {
        this.attributePath = attributePath;
        this.beta = beta;
        this.lambda = lambda;
        this.resolver = resolver;
    }

    public BoxCoxComponent(String attributePath, double beta, double lambda) {
        this(attributePath, beta, lambda, new NestedAttributeResolver());
    }

    @Override
    public double calculate(Attributable attributable) {
        Object attribute = resolver.resolve(attributable, attributePath);
        double value = ((Number) attribute).doubleValue();
        double result;
        if (lambda == 0.0) {
            result = Math.log(value) * beta;
//            log.debug("BoxCoxComponent (log): attributePath=" + attributePath + ", attribute=" + attribute + ", value=" + result);
        } else {
            result = ((Math.pow(value, lambda) - 1) / lambda) * beta;
//            log.debug("BoxCoxComponent (classical): attributePath=" + attributePath + ", attribute=" + attribute + ", value=" + result);
        }


        return result;
    }

}
