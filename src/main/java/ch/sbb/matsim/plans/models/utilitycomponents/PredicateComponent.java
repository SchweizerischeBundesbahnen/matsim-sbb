package ch.sbb.matsim.plans.models.utilitycomponents;

import ch.sbb.matsim.plans.models.resolver.AttributeResolver;
import ch.sbb.matsim.plans.models.resolver.NestedAttributeResolver;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.matsim.utils.objectattributes.attributable.Attributable;

import java.util.function.Predicate;

public class PredicateComponent implements UtilityComponent {

    private static final Logger log = LogManager.getLogger(ShadowUtility.class);

    private final Predicate<Object> condition;
    private final double beta;
    private final String attributePath;
    private final AttributeResolver resolver;

    public PredicateComponent(String attributePath, Predicate<Object> condition, double beta) {
        this(attributePath, condition, beta, new NestedAttributeResolver());
    }

    public PredicateComponent(String attributePath, Predicate<Object> condition, double beta, AttributeResolver resolver) {
        this.attributePath = attributePath;
        this.condition = condition;
        this.beta = beta;
        this.resolver = resolver;
    }

    @Override
    public double calculate(Attributable attributes) {
        Object attribute = resolver.resolve(attributes, attributePath);
        double result = condition.test(attribute) ? beta : 0.0;
//        log.debug("PredicateComponent: attributePath=" + attributePath + ", attribute=" + attribute + ", value=" + result);
        return result;
    }
}
