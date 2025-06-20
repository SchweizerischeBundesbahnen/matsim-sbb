package ch.sbb.matsim.plans.models.utilitycomponents;

import ch.sbb.matsim.plans.models.resolver.AttributeResolver;
import ch.sbb.matsim.plans.models.resolver.NestedAttributeResolver;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.matsim.utils.objectattributes.attributable.Attributable;

import java.util.Arrays;

public class PiecewiseComponent implements UtilityComponent {

    private static final Logger log = LogManager.getLogger(PiecewiseComponent.class);

    private final double[] thresholds;
    private final double[] betas;
    private final String attributePath;
    private final AttributeResolver resolver;


    public PiecewiseComponent(String attributePath, double[] thresholds, double[] betas, AttributeResolver resolver) {
        this.attributePath = attributePath;
        this.thresholds = thresholds;
        this.betas = betas;
        this.resolver = resolver;
        if (thresholds.length != betas.length - 1) {
            throw new IllegalArgumentException("Thresholds and betas must have length n and n+1 respectively" +
                    " (thresholds: " + Arrays.toString(thresholds) + ", betas: " + Arrays.toString(betas) + ")");
        }
    }

    public PiecewiseComponent(String attributePath, double[] thresholds, double[] betas) {
        this(attributePath, thresholds, betas, new NestedAttributeResolver());
    }


    public double calculate(Attributable attributable) {
        Object attribute = resolver.resolve(attributable, attributePath);
        double value = ((Number) attribute).doubleValue();
        double returnValue = 0.0;
        for (int i = 0; i < betas.length; i++) {
            if (i == 0) {
                returnValue += betas[i] * Math.min(value, thresholds[i]);
            } else if (i < betas.length - 1) {
                returnValue += betas[i] * (Math.max(0, Math.min(value - thresholds[i - 1],
                        thresholds[i] - thresholds[i - 1])));
            } else {
                returnValue += betas[i] * Math.max(0, value - thresholds[i - 1]);
            }
        }
//        log.debug("PiecewiseComponent: attributePath=" + attributePath + ", attribute=" + attribute + ", value=" + returnValue);
        return returnValue;
    }

}
