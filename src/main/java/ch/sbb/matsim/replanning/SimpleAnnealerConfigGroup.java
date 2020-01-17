package ch.sbb.matsim.replanning;

import ch.sbb.matsim.config.SBBBehaviorGroupsConfigGroup;
import org.matsim.core.config.ConfigGroup;
import org.matsim.core.config.ReflectiveConfigGroup;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * @author davig
 */

public class SimpleAnnealerConfigGroup extends ReflectiveConfigGroup {

    public static final String GROUP_NAME = "SimpleAnnealer";
    private boolean activateAnnealingModule = false;

    public enum annealOption {linear, geometric, exponential, msa, sigmoid, disabled}
    public enum annealParameterOption {globalInnovationRate, BrainExpBeta, PathSizeLogitBeta, learningRate,
        ReRoute, SubtourModeChoice, TimeAllocationMutator, SBBTimeMutatorReRoute}

    public SimpleAnnealerConfigGroup() {
        super(GROUP_NAME);
    }

    @StringGetter("activateAnnealingModule")
    public boolean isActivateAnnealingModule() {
        return activateAnnealingModule;
    }

    @StringSetter("activateAnnealingModule")
    public void setActivateAnnealingModule(boolean activateAnnealingModule) {
        this.activateAnnealingModule = activateAnnealingModule;
    }

    @Override
    public ConfigGroup createParameterSet(final String type) {
        if (AnnealingVariable.GROUP_NAME.equals(type)) {
            return new SBBBehaviorGroupsConfigGroup.BehaviorGroupParams();
        }
        throw new IllegalArgumentException(type);
    }

    @Override
    protected void checkParameterSet(final ConfigGroup module) {
        if (!AnnealingVariable.GROUP_NAME.equals(module.getName())) {
            throw new IllegalArgumentException(module.getName());
        }
        if (!(module instanceof AnnealingVariable)) {
            throw new RuntimeException("unexpected class for module " + module);
        }
    }

    @Override
    public void addParameterSet(final ConfigGroup set) {
        if (AnnealingVariable.GROUP_NAME.equals(set.getName())) {
            addAnnealingVariable((AnnealingVariable) set);
        }
        throw new IllegalArgumentException(set.getName());
    }

    public Map<annealParameterOption, AnnealingVariable> getAnnealingVariables() {
        final EnumMap<annealParameterOption, AnnealingVariable> map =
                new EnumMap<>(annealParameterOption.class);
        for (ConfigGroup pars : getParameterSets(AnnealingVariable.GROUP_NAME)) {
            final annealParameterOption name = ((AnnealingVariable) pars).getAnnealParameter();
            final AnnealingVariable old = map.put(name, (AnnealingVariable) pars);
            if (old != null) throw new IllegalStateException("several parameter sets for behavior group " + name);
        }
        return map;
    }

    public void addAnnealingVariable(final AnnealingVariable params) {
        final AnnealingVariable previous = this.getAnnealingVariables().get(params.getAnnealParameter());
        if (previous != null) {
            final boolean removed = removeParameterSet(previous);
            if (!removed) throw new RuntimeException("problem replacing behavior group params ");
        }
        super.addParameterSet(params);
    }

    public static class AnnealingVariable extends ReflectiveConfigGroup {

        public static final String GROUP_NAME = "AnnealingVariable";

        private String defaultSubpop = null;
        private Double startValue = null;
        private double endValue = 0.0001;
        private double shapeFactor = 0.9;
        private int halfLife = 100;
        private int iterationToFreezeAnnealingRates = Integer.MAX_VALUE;
        private annealOption annealType = annealOption.disabled;
        private annealParameterOption annealParameter = annealParameterOption.globalInnovationRate;

        public AnnealingVariable() {
            super(GROUP_NAME);
        }

        @StringGetter("startValue")
        public Double getStartValue() {
            return this.startValue;
        }

        @StringSetter("startValue")
        void setStartValue(Double startValue) {
            this.startValue = startValue;
        }

        @StringGetter("endValue")
        public double getEndValue() {
            return this.endValue;
        }

        @StringSetter("endValue")
        void setEndValue(double endValue) {
            this.endValue = endValue;
        }

        @StringGetter("annealType")
        public annealOption getAnnealType() {
            return this.annealType;
        }

        @StringSetter("annealType")
        void setAnnealType(String annealType) {
            this.annealType = annealOption.valueOf(annealType);
        }

        @StringGetter("defaultSubpopulation")
        public String getDefaultSubpopulation() {
            return this.defaultSubpop;
        }

        @StringSetter("defaultSubpopulation")
        void setDefaultSubpopulation(String defaultSubpop) {
            this.defaultSubpop = defaultSubpop;
        }

        @StringGetter("annealParameter")
        public annealParameterOption getAnnealParameter() {
            return this.annealParameter;
        }

        @StringSetter("annealParameter")
        void setAnnealParameter(String annealParameter) {
            this.annealParameter = annealParameterOption.valueOf(annealParameter);
        }

        @StringGetter("halfLife")
        public int getHalfLife() {
            return this.halfLife;
        }

        @StringSetter("halfLife")
        void setHalfLife(int halfLife) {
            this.halfLife = halfLife;
        }

        @StringGetter("shapeFactor")
        public double getShapeFactor() {
            return this.shapeFactor;
        }

        @StringSetter("shapeFactor")
        void setShapeFactor(double shapeFactor) {
            this.shapeFactor = shapeFactor;
        }

        @StringGetter("iterationToFreezeAnnealingRates")
        public int getIterationToFreezeAnnealingRates() {
            return this.iterationToFreezeAnnealingRates;
        }

        @StringSetter("iterationToFreezeAnnealingRates")
        void setIterationToFreezeAnnealingRates(int iterationToFreezeAnnealingRates) {
            this.iterationToFreezeAnnealingRates = iterationToFreezeAnnealingRates;
        }

        @Override
        public Map<String, String> getComments() {
            Map<String, String> map = super.getComments();
            map.put("iterationToFreezeAnnealingRates", "if using this, make sure to change other config parameters accordingly to avoid conflicts (e.g. innovationSwitchOff).");
            map.put("halfLife", "exponential: startValue / exp(it/halfLife)");
            map.put("shapeFactor", "sigmoid: 1/(1+e^(shapeFactor*(it - halfLife))); geometric: startValue * shapeFactor^it; msa: startValue / it^shapeFactor");
            map.put("annealType", "options: linear, exponential, geometric, msa, sigmoid and disabled (no annealing).");
            map.put("annealParameter", "list of config parameters that shall be annealed. Currently supported: globalInnovationRate, BrainExpBeta, PathSizeLogitBeta, learningRate. Default is globalInnovationRate");
            map.put("defaultSubpopulation", "subpopulation to have the global innovation rate adjusted. Not applicable when annealing with other parameters.");
            return map;
        }
    }

}