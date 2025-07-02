package ch.sbb.matsim.plans.models.utilitycomponents;

import ch.sbb.matsim.plans.models.resolver.AttributeResolver;
import ch.sbb.matsim.plans.models.resolver.NestedAttributeResolver;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.matsim.api.core.v01.Identifiable;
import org.matsim.utils.objectattributes.attributable.Attributable;

public class LinearComponent implements UtilityComponent {

    private static final Logger log = LogManager.getLogger(LinearComponent.class);

    private final String attributePath;
    private final double beta;
    private final AttributeResolver resolver;

    public LinearComponent(String attributePath, double beta, AttributeResolver resolver) {
        this.attributePath = attributePath;
        this.beta = beta;
        this.resolver = resolver;
    }

    public LinearComponent(String attributePath, double beta) {
        this(attributePath, beta, new NestedAttributeResolver());
    }

    @Override
    public double calculate(Attributable attributable) {
        Object attribute = resolver.resolve(attributable, attributePath);
        if (attribute == null) {
            throw new RuntimeException("Attribute '" + attributePath + "' is null for " + ((Identifiable) attributable).getId() + ". ");
        }
        if (attribute instanceof Boolean b) {
            if (b) {
//                log.debug("LinearComponent: attributePath=" + attributePath + ", attribute=" + attribute + ", value=" + beta);
                return beta; // Boolean true contributes beta
            } else {
//                log.debug("LinearComponent: attributePath=" + attributePath + ", attribute=" + attribute + ", value=0.0");
                return 0.0; // Boolean false contributes nothing
            }
        }
        double value = ((Number) attribute).doubleValue();
        double result = value * beta;
//        log.debug("LinearComponent: attributePath=" + attributePath + ", attribute=" + attribute + ", value=" + result);
        return result;
    }
}
