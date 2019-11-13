/*
 * Copyright (C) Schweizerische Bundesbahnen SBB, 2017.
 */

package ch.sbb.matsim.config;

import org.apache.log4j.Logger;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigGroup;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.config.ReflectiveConfigGroup;

import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class SBBIntermodalConfigGroup extends ReflectiveConfigGroup {

    static public final String GROUP_NAME = "SBBIntermodal";

    static private final String PARAM_CSV_PATH = "interModalAccessEgressCSV";
    static private final String PARAM_CSV_PATH_DESC = "If set, access&egress availability parameters will be read " +
            "from CSV file and added to Person Attributes. Null by default.";

    private String attributesCSVPath = null;

    private final List<SBBIntermodalModeParameterSet> modeParamSets = new ArrayList<>();
    private static Logger logger = Logger.getLogger(SBBIntermodalConfigGroup.class);

    @Override
    public ConfigGroup createParameterSet(String type) {
        if (SBBIntermodalModeParameterSet.TYPE.equals(type)) {
            return new SBBIntermodalModeParameterSet();
        }
        throw new IllegalArgumentException("Unsupported parameterset-type: " + type);
    }

    @StringGetter(PARAM_CSV_PATH)
    public String getAttributesCSVPath() {
        return attributesCSVPath;
    }

    public URL getAttributesCSVPathURL(URL context) {
        return attributesCSVPath != null ? ConfigGroup.getInputFileURL(context, attributesCSVPath) : null;
    }

    @StringSetter(PARAM_CSV_PATH)
    public void setAttributesCSVPath(String attributesCSVPath) {
        this.attributesCSVPath = attributesCSVPath;
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

    @Override
    public Map<String, String> getComments() {
        Map<String, String> comments = super.getComments();
        comments.put(PARAM_CSV_PATH, PARAM_CSV_PATH_DESC);
        return (comments);

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
        public static final String PARAM_MODE_DESC = "Mode to use as feeder";

        static private final String PARAM_WAITINGTIME = "waitingTime";
        public static final String PARAM_MUTT_DESC = "Marginal Utility of travel time (per hour)";

        static private final String PARAM_CONSTANT = "constant";
        public static final String PARAM_FACTOR_DESC = "Factor to multiply the fastest travel time with as an estimation of potential detours to pick up other passengers.";

        static private final String PARAM_MUTT = "mutt";
        public static final String PARAM_NETWORKMODE_DESC = "If true, the mode will be added as main-mode to be simulated on the road network.";

        static private final String PARAM_DETOUR = "detourFactor";
        static private final String PARAM_WAITINGTIME_DESC = "Additional waiting time.";

        static private final String PARAM_NETWORK = "isOnNetwork";
        static private final String PARAM_CONSTANT_DESC = "ASC for feeder mode";


        private String mode = "ride_feeder";
        private int waitingTime = 15 * 60;
        private double constant = -1.5;
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

        public double getMUTT_perSecond() {
            return (mutt / 3600.0);
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
            comments.put(PARAM_MODE, PARAM_MODE_DESC);
            comments.put(PARAM_MUTT, PARAM_MUTT_DESC);
            comments.put(PARAM_DETOUR, PARAM_FACTOR_DESC);
            comments.put(PARAM_NETWORK, PARAM_NETWORKMODE_DESC);
            comments.put(PARAM_CONSTANT, PARAM_CONSTANT_DESC);
            comments.put(PARAM_WAITINGTIME, PARAM_WAITINGTIME_DESC);
            return comments;
        }

        @Override
        protected void checkConsistency(Config config) {
            super.checkConsistency(config);
            SwissRailRaptorConfigGroup railRaptorConfigGroup = ConfigUtils.addOrGetModule(config, SwissRailRaptorConfigGroup.class);

            if (constant > 0) {
                logger.warn("Constant for intermodal mode " + getMode() + "is > 0. This might be an unwanted utility!");
            }
            if (getMUTT() > 0) {
                logger.warn("Marginal Utility of Travel time (per hour) for itnermodal " + getMode() + "is > 0. This might be an unwanted utility!");
            }
            Set<String> modesInRaptorConfig = railRaptorConfigGroup.getIntermodalAccessEgressParameterSets()
                    .stream()
                    .map(p -> p.getMode())
                    .collect(Collectors.toSet());
            if (!modesInRaptorConfig.contains(mode)) {
                throw new RuntimeException("Mode " + mode + "is defined in SBBIntermodalConfigGroup, but not in SwissRailRaptorConfigGroup. " +
                        "This will most likely be unwanted.");
            }
        }
    }
}
