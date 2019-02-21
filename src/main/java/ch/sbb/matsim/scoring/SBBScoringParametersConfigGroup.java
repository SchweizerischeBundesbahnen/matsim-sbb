package ch.sbb.matsim.scoring;

import org.matsim.core.config.ConfigGroup;
import org.matsim.core.config.ReflectiveConfigGroup;

import java.util.HashMap;
import java.util.Map;

public class SBBScoringParametersConfigGroup extends ReflectiveConfigGroup  {

    public static final String GROUP_NAME = "SBBScoringParameters";

    private final Map<String, ScoringParametersSet> paramsPerSubpopulation = new HashMap<>();

    public SBBScoringParametersConfigGroup() {
        super(GROUP_NAME);
        this.paramsPerSubpopulation.put(null, new ScoringParametersSet()); // set defaults
    }

    @Override
    public ConfigGroup createParameterSet(String type) {

        if (ScoringParametersSet.TYPE.equals(type)) {
            return new ScoringParametersSet();
        }
        throw new IllegalArgumentException(type);
    }

    @Override
    public void addParameterSet(final ConfigGroup set) {
        if (set instanceof ScoringParametersSet) {
            addScoringParameters((ScoringParametersSet) set);
            return;
        }
        throw new IllegalArgumentException("Cannot add parameter set of type " + set.getClass().getName());
    }

    public void addScoringParameters(ScoringParametersSet set) {
        this.paramsPerSubpopulation.put(set.subpopulation, set);
    }

    public ScoringParametersSet getScoringParameters(String subpopulation) {
        return this.paramsPerSubpopulation.get(subpopulation);
    }

    public static class ScoringParametersSet extends ReflectiveConfigGroup {

        public static final String TYPE = "scoringParameters";

        public static final String SUBPOPULATION = "subpopulation";
        public static final String MARGINAL_UTILITY_OF_PARKING_PRICE = "marginalUtilityOfParkingPrice";
        public static final String TRANSFER_TRAVELTIME_TO_COST_FACTOR_PER_HOUR = "transferTravelTimeToCostFactor";

        private String subpopulation = null;
        private double marginalUtilityOfParkingPrice = 0.0;
        private double transferTravelTimeToCostFactor_utilsPerHour = 0;

        public ScoringParametersSet() {
            super(TYPE);
        }

        @Override
        public Map<String, String> getComments() {
            Map<String, String> map = super.getComments();
            map.put(SUBPOPULATION, "The attribute value to identify the subpopulation. Use 'null' for the default subpopulation or for no subpopulations at all.");
            map.put(MARGINAL_UTILITY_OF_PARKING_PRICE, "[utils/money]");
            map.put(TRANSFER_TRAVELTIME_TO_COST_FACTOR_PER_HOUR, "[utils/hour] transfer penalty in utils, depending on the total transit travel time.");
            return map;
        }

        @StringSetter(SUBPOPULATION)
        public void setSubpopulation(String subpopulation) {
            if (this.subpopulation != null) {
                throw new IllegalStateException("Cannot change subpopulation, as it is already set and it is used for indexing.");
            }
            this.subpopulation = subpopulation;
        }

        @StringGetter(SUBPOPULATION)
        public String getSubpopulation() {
            return this.subpopulation;
        }

        @StringSetter(MARGINAL_UTILITY_OF_PARKING_PRICE)
        public void setMarginalUtilityOfParkingPrice(String marginalUtilityOfParkingPrice) {
            this.marginalUtilityOfParkingPrice = Double.parseDouble(marginalUtilityOfParkingPrice);
        }

        @StringGetter(MARGINAL_UTILITY_OF_PARKING_PRICE)
        public String getMarginalUtilityOfParkingPrice_asString() {
            return Double.toString(this.marginalUtilityOfParkingPrice);
        }

        public void setMarginalUtilityOfParkingPrice(double marginalUtilityOfParkingPrice) {
            this.marginalUtilityOfParkingPrice = marginalUtilityOfParkingPrice;
        }

        public double getMarginalUtilityOfParkingPrice() {
            return this.marginalUtilityOfParkingPrice;
        }

        @StringGetter(TRANSFER_TRAVELTIME_TO_COST_FACTOR_PER_HOUR)
        public void setTransferTravelTimeToCostFactor(String factor) {
            this.transferTravelTimeToCostFactor_utilsPerHour = Double.parseDouble(factor);
        }

        @StringGetter(TRANSFER_TRAVELTIME_TO_COST_FACTOR_PER_HOUR)
        public String getTransferTravelTimeToCostFactor_asString() {
            return Double.toString(this.transferTravelTimeToCostFactor_utilsPerHour);
        }

        public void setTransferTraveltimeToCostFactor_utils_hr(double factor) {
            this.transferTravelTimeToCostFactor_utilsPerHour = factor;
        }

        public double getTransferTravelTimeToCostFactor_utils_hr() {
            return this.transferTravelTimeToCostFactor_utilsPerHour;
        }
    }

}
