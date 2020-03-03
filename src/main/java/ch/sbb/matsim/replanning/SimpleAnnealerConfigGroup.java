package ch.sbb.matsim.replanning;

import org.matsim.core.config.ConfigGroup;
import org.matsim.core.config.ReflectiveConfigGroup;

import java.util.EnumMap;
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
            return new AnnealingVariable();
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
        if (!AnnealingVariable.GROUP_NAME.equals(set.getName())) {
            throw new IllegalArgumentException(set.getName());
        }
        addAnnealingVariable((AnnealingVariable) set);
    }

    public Map<annealParameterOption, AnnealingVariable> getAnnealingVariables() {
        final EnumMap<annealParameterOption, AnnealingVariable> map =
                new EnumMap<>(annealParameterOption.class);
        for (ConfigGroup pars : getParameterSets(AnnealingVariable.GROUP_NAME)) {
            final annealParameterOption name = ((AnnealingVariable) pars).getAnnealParameter();
            final AnnealingVariable old = map.put(name, (AnnealingVariable) pars);
            if (old != null) throw new IllegalStateException("several parameter sets for variable " + name);
        }
        return map;
    }

    public void addAnnealingVariable(final AnnealingVariable params) {
        final AnnealingVariable previous = this.getAnnealingVariables().get(params.getAnnealParameter());
        if (previous != null) {
            final boolean removed = removeParameterSet(previous);
            if (!removed) throw new RuntimeException("problem replacing annealing variable");
        }
        super.addParameterSet(params);
    }

    public static class AnnealingVariable extends ReflectiveConfigGroup {

        public static final String GROUP_NAME = "AnnealingVariable";

        private String defaultSubpop = null;
        private Double startValue = null;
        private double endValue = 0.0001;
        private double shapeFactor = 0.9;
        private double halfLife = 100.0;
        private annealOption annealType = annealOption.disabled;
        private annealParameterOption annealParameter = annealParameterOption.globalInnovationRate;

        public AnnealingVariable() {
            super(GROUP_NAME);
        }

        private static final String START_VALUE = "startValue";
        @StringGetter(START_VALUE)
        public Double getStartValue() {
            return this.startValue;
        }

        @StringSetter(START_VALUE)
        public void setStartValue(Double startValue) {
            this.startValue = startValue;
        }

        private static final String END_VALUE = "endValue";
        @StringGetter(END_VALUE)
        public double getEndValue() {
            return this.endValue;
        }

        @StringSetter(END_VALUE)
        public void setEndValue(double endValue) {
            this.endValue = endValue;
        }

        private static final String ANNEAL_TYPE = "annealType";
        @StringGetter(ANNEAL_TYPE)
        public annealOption getAnnealType() {
            return this.annealType;
        }

        @StringSetter(ANNEAL_TYPE)
        public void setAnnealType(String annealType) {
            this.annealType = annealOption.valueOf(annealType);
        }

        public void setAnnealType(annealOption annealType) {
            this.annealType = annealType;
        }

        private static final String DEFAULT_SUBPOP = "defaultSubpopulation";
        @StringGetter(DEFAULT_SUBPOP)
        public String getDefaultSubpopulation() {
            return this.defaultSubpop;
        }

        @StringSetter(DEFAULT_SUBPOP)
        public void setDefaultSubpopulation(String defaultSubpop) {
            this.defaultSubpop = defaultSubpop;
        }

        private static final String ANNEAL_PARAM = "annealParameter";
        @StringGetter(ANNEAL_PARAM)
        public annealParameterOption getAnnealParameter() {
            return this.annealParameter;
        }

        @StringSetter(ANNEAL_PARAM)
        public void setAnnealParameter(String annealParameter) {
            this.annealParameter = annealParameterOption.valueOf(annealParameter);
        }

        public void setAnnealParameter(annealParameterOption annealParameter) {
            this.annealParameter = annealParameter;
        }

        private static final String HALFLIFE = "halfLife";
        @StringGetter(HALFLIFE)
        public double getHalfLife() {
            return this.halfLife;
        }

        @StringSetter(HALFLIFE)
        public void setHalfLife(double halfLife) {
            this.halfLife = halfLife;
        }

        private static final String SHAPE_FACTOR = "shapeFactor";
        @StringGetter(SHAPE_FACTOR)
        public double getShapeFactor() {
            return this.shapeFactor;
        }

        @StringSetter(SHAPE_FACTOR)
        public void setShapeFactor(double shapeFactor) {
            this.shapeFactor = shapeFactor;
        }

        @Override
        public Map<String, String> getComments() {
            Map<String, String> map = super.getComments();
            map.put(HALFLIFE, "this parameter enters the exponential and sigmoid formulas. May be an iteration or a share, i.e. 0.5 for halfLife at 50% of iterations. Exponential: startValue / exp(it/halfLife)");
            map.put(SHAPE_FACTOR, "sigmoid: 1/(1+e^(shapeFactor*(it - halfLife))); geometric: startValue * shapeFactor^it; msa: startValue / it^shapeFactor");
            map.put(ANNEAL_TYPE, "options: linear, exponential, geometric, msa, sigmoid and disabled (no annealing).");
            map.put(ANNEAL_PARAM, "list of config parameters that shall be annealed. Currently supported: globalInnovationRate, BrainExpBeta, PathSizeLogitBeta, learningRate. Default is globalInnovationRate");
            map.put(DEFAULT_SUBPOP, "subpopulation to have the global innovation rate adjusted. Not applicable when annealing with other parameters.");
            map.put(START_VALUE, "start value for annealing.");
            map.put(END_VALUE, "final annealing value. When the annealing function reaches this value, further results remain constant.");
            return map;
        }
    }

}