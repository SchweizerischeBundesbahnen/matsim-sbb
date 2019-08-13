/*
 * Copyright (C) Schweizerische Bundesbahnen SBB, 2017.
 */

package ch.sbb.matsim.config;

import org.matsim.core.config.ConfigGroup;
import org.matsim.core.config.ReflectiveConfigGroup;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class SBBIntermodalConfigGroup extends ReflectiveConfigGroup {

    static public final String GROUP_NAME = "SBBIntermodal";

    private final List<SBBIntermodalModeParameterSet> modeParamSets = new ArrayList<>();

    @Override
    public ConfigGroup createParameterSet(String type) {
        if (SBBIntermodalModeParameterSet.TYPE.equals(type)) {
            return new SBBIntermodalModeParameterSet();
        }
        throw new IllegalArgumentException("Unsupported parameterset-type: " + type);
    }

    @Override
    public void addParameterSet(ConfigGroup set) {
        if (set instanceof SBBIntermodalModeParameterSet) {
            addModeParameters((SBBIntermodalModeParameterSet) set);
        } else {
          throw new IllegalArgumentException("Unsupported parameterset: " + set.getClass().getName());
        }
    }

    public void addModeParameters(SBBIntermodalModeParameterSet set) {
        this.modeParamSets.add(set);
        super.addParameterSet(set);
    }

    @Override
    public boolean removeParameterSet(ConfigGroup set) {
        this.modeParamSets.remove(set);
        return super.removeParameterSet(set);

    }

    public List<SBBIntermodalModeParameterSet> getModeParameterSets() {
        return this.modeParamSets;
    }

    public SBBIntermodalConfigGroup() {
        super(GROUP_NAME);
    }

    public static class SBBIntermodalModeParameterSet extends ReflectiveConfigGroup {
        static final String TYPE = "mode";

        static private final String PARAM_MODE = "mode";
        static private final String PARAM_WAITINGTIME = "waitingTime";
        static private final String PARAM_CONSTANT = "constant";
        static private final String PARAM_MUTT = "mutt";
        static private final String PARAM_DETOUR = "detourFactor";
        static private final String PARAM_NETWORK = "isOnNetwork";

        private String mode = "ride_feeder";
        private int waitingTime = 15 * 60;
        private double constant = 1.5;
        private double mutt = 0.003;
        private double detourFactor = 1.3;
        private boolean onNetwork = true;

        public SBBIntermodalModeParameterSet() {
            super(TYPE);
        }

        public SBBIntermodalModeParameterSet(String mode, int waitingTime, double constanst, double mutt, double detourFactor) {
            super(TYPE);
            this.mode = mode;
            this.waitingTime = waitingTime;
            this.constant = constanst;
            this.mutt = mutt;
            this.detourFactor = detourFactor;
        }

        @StringGetter(PARAM_MODE)
        public String getMode() {
            return this.mode;
        }

        @StringSetter(PARAM_MODE)
        public void setMode(String mode) {
            this.mode = mode;
        }

        @StringGetter(PARAM_WAITINGTIME)
        public int getWaitingTime() {
            return this.waitingTime;
        }

        @StringSetter(PARAM_WAITINGTIME)
        public void setWaitingTime(int waitingTime) {
            this.waitingTime = waitingTime;
        }


        @StringGetter(PARAM_CONSTANT)
        public double getConstant() {
            return this.constant;
        }

        @StringSetter(PARAM_CONSTANT)
        public void setConstant(double constant) {
            this.constant = constant;
        }


        @StringGetter(PARAM_DETOUR)
        public double getDetourFactor() {
            return this.detourFactor;
        }

        @StringSetter(PARAM_DETOUR)
        public void setDetourFactor(double detourFactor) {
            this.detourFactor = detourFactor;
        }


        @StringGetter(PARAM_MUTT)
        public double getMUTT() {
            return this.mutt;
        }

        @StringSetter(PARAM_MUTT)
        public void setMUTT(double mutt) {
            this.mutt = mutt;
        }


        @StringGetter(PARAM_NETWORK)
        public boolean isOnNetwork() {
            return this.onNetwork;
        }

        @StringSetter(PARAM_NETWORK)
        public void setOnNetwork(boolean onNetwork) {
            this.onNetwork = onNetwork;
        }

        @Override
        public Map<String, String> getComments() {
            Map<String, String> comments = super.getComments();
            comments.put(PARAM_MODE, "Mode to use as feeder");
            comments.put(PARAM_MUTT, "Marginal utility of travel time");
            comments.put(PARAM_DETOUR, "Factor to multiply the fastest travel time with as an estimation of potential detours to pick up other passengers.");
            comments.put(PARAM_NETWORK, "If true, the mode will be added as main-mode to be simulated on the road network.");
            return comments;
        }

    }
}
