package ch.sbb.matsim.config;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.matsim.core.config.ReflectiveConfigGroup;

import java.util.Map;


public class SBBCapacityDependentRoutingConfigGroup extends ReflectiveConfigGroup {

    static public final String GROUP_NAME = "SBBCapacityDependentRouting";

    static private final String PARAM_USESERVICEQUALITY = "useServiceQuality";
    static private final String PARAM_USESERVICEQUALITY_DESC = "use capacity dependent routing yes/no";
    static private final String PARAM_MINIMUMCOSTFACTOR = "minimumCostFactor";
    static private final String PARAM_MINIMUMCOSTFACTOR_DESC = "minimal cost factor for capacity dependent routing";
    static private final String PARAM_MAXIMUMCOSTFACTOR = "maximumCostFactor";
    static private final String PARAM_MAXIMUMCOSTFACTOR_DESC = "maximal cost factor for capacity dependent routing";
    static private final String PARAM_LOWERCAPACITYLIMIT = "lowerCapacityLimit";
    static private final String PARAM_LOWERCAPACITYLIMIT_DESC = "limit of capacity below which cost factor is < 1.0 ";
    static private final String PARAM_HIGHERCAPACITYLIMIT = "higherCapacityLimit";
    static private final String PARAM_HIGHERCAPACITYLIMIT_DESC = "limit of capacity above which cost factor is > 1.0";

    private static Logger logger = LogManager.getLogger(SBBCapacityDependentRoutingConfigGroup.class);

    private boolean useServiceQuality = false;
    private double minimumCostFactor = 0.8;
    private double maximumCostFactor = 1.2;
    private double lowerCapacityLimit = 0.0;
    private double higherCapacityLimit = 0.7;

    public SBBCapacityDependentRoutingConfigGroup() {
        super(GROUP_NAME);
    }

    @StringGetter(PARAM_USESERVICEQUALITY)
    public boolean getUseServiceQuality() {
        return this.useServiceQuality;
    }

    @StringSetter(PARAM_USESERVICEQUALITY)
    public void setUseServiceQuality(boolean useServiceQuality) {
        this.useServiceQuality = useServiceQuality;
    }

    @StringGetter(PARAM_MINIMUMCOSTFACTOR)
    public double getMinimumCostFactor() {
        return this.minimumCostFactor;
    }

    @StringSetter(PARAM_MINIMUMCOSTFACTOR)
    public void setMinimumCostFactor(double minimumCostFactor) {
        this.minimumCostFactor = minimumCostFactor;
    }

    @StringGetter(PARAM_MAXIMUMCOSTFACTOR)
    public double getMaximumCostFactor() {
        return this.maximumCostFactor;
    }

    @StringSetter(PARAM_MAXIMUMCOSTFACTOR)
    public void setMaximumCostFactor(double maximumCostFactor) {
        this.maximumCostFactor = maximumCostFactor;
    }

    @StringGetter(PARAM_LOWERCAPACITYLIMIT)
    public double getLowerCapacityLimit() {
        return this.lowerCapacityLimit;
    }

    @StringSetter(PARAM_LOWERCAPACITYLIMIT)
    public void setLowerCapacityLimit(double lowerCapacityLimit) {
        this.lowerCapacityLimit = lowerCapacityLimit;
    }

    @StringGetter(PARAM_HIGHERCAPACITYLIMIT)
    public double getHighercapacitylimit() {
        return this.higherCapacityLimit;
    }

    @StringSetter(PARAM_HIGHERCAPACITYLIMIT)
    public void setHighercapacitylimit(double higherCapacityLimit) {
        this.higherCapacityLimit = higherCapacityLimit;
    }

    @Override
    public Map<String, String> getComments() {
        Map<String, String> comments = super.getComments();
        comments.put(PARAM_USESERVICEQUALITY, PARAM_USESERVICEQUALITY_DESC);
        comments.put(PARAM_MINIMUMCOSTFACTOR, PARAM_MINIMUMCOSTFACTOR_DESC);
        comments.put(PARAM_MAXIMUMCOSTFACTOR, PARAM_MAXIMUMCOSTFACTOR_DESC);
        comments.put(PARAM_LOWERCAPACITYLIMIT, PARAM_LOWERCAPACITYLIMIT_DESC);
        comments.put(PARAM_HIGHERCAPACITYLIMIT, PARAM_HIGHERCAPACITYLIMIT_DESC);
        return (comments);

    }

}
