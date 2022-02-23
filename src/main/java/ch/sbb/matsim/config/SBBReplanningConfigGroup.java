package ch.sbb.matsim.config;

import org.matsim.core.config.ReflectiveConfigGroup;

public class SBBReplanningConfigGroup extends ReflectiveConfigGroup {
    static public final String GROUP_NAME = "SBBReplanning";
    private static final String desc_maximumWalkTourDistance_m = "maximumWalkTourDistance_m";
    private static final String desc_maximumBikeTourDistance_m = "maximumBikeTourDistance_m";
    private static final String desc_minimumTimeMutationStep_s = "minimumTimeMutationStep_s";
    private int maximumWalkTourDistance_m = 12500;
    private int maximumBikeTourDistance_m = 45000;
    private int minimumTimeMutationStep_s = 120;

    public SBBReplanningConfigGroup() {
        super(GROUP_NAME);
    }

    @StringGetter(desc_maximumWalkTourDistance_m)
    public int getMaximumWalkTourDistance_m() {
        return maximumWalkTourDistance_m;
    }

    @StringSetter(desc_maximumWalkTourDistance_m)
    public void setMaximumWalkTourDistance_m(int maximumWalkTourDistance_m) {
        this.maximumWalkTourDistance_m = maximumWalkTourDistance_m;
    }

    @StringGetter(desc_maximumBikeTourDistance_m)
    public int getMaximumBikeTourDistance_m() {
        return maximumBikeTourDistance_m;
    }

    @StringSetter(desc_maximumBikeTourDistance_m)
    public void setMaximumBikeTourDistance_m(int maximumBikeTourDistance_m) {
        this.maximumBikeTourDistance_m = maximumBikeTourDistance_m;
    }

    @StringGetter(desc_minimumTimeMutationStep_s)
    public int getMinimumTimeMutationStep_s() {
        return minimumTimeMutationStep_s;
    }

    @StringSetter(desc_minimumTimeMutationStep_s)
    public void setMinimumTimeMutationStep_s(int minimumTimeMutationStep_s) {
        this.minimumTimeMutationStep_s = minimumTimeMutationStep_s;
    }
}
