package ch.sbb.matsim.plans.models.utilitycomponents;

import ch.sbb.matsim.plans.models.resolver.AttributeResolver;
import ch.sbb.matsim.plans.models.resolver.NestedAttributeResolver;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.matsim.utils.objectattributes.attributable.Attributable;

import java.util.List;
import java.util.function.Predicate;

public class PredicateComponent implements UtilityComponent {

    private static final Logger log = LogManager.getLogger(ShadowUtility.class);

    private final List<Predicate<Object>> conditions;
    private final List<String> attributes;
    private final double beta;
    private final AttributeResolver resolver;

    public PredicateComponent(List<String> attributes, List<Predicate<Object>> conditions, double beta) {
        this(attributes, conditions, beta, new NestedAttributeResolver());
    }

    public PredicateComponent(List<String> attributes, List<Predicate<Object>> conditions, double beta, AttributeResolver resolver) {

        this.conditions = conditions;
        this.attributes = attributes;
        this.beta = beta;
        this.resolver = resolver;
        if (attributes.size() != conditions.size()) {
            throw new IllegalArgumentException("Attributes and conditions must have the same size.");
        }
    }

    @Override
    public double calculate(Attributable attributable) {
        for (int i = 0; i < conditions.size(); i++) {
            Object value = resolver.resolve(attributable, this.attributes.get(i));
            if (!conditions.get(i).test(value)) {
                return 0.0;
            }
        }
//        log.debug("PredicateComponent: All conditions met "+attributes+" returning beta value: " + beta);
        return beta;
    }
}
