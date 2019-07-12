/*
 * Copyright (C) Schweizerische Bundesbahnen SBB, 2017.
 */

package ch.sbb.matsim.config;

import org.matsim.core.config.ReflectiveConfigGroup;

import java.util.Map;

/**
 *
 */
public class SBBIntermodalConfigGroup extends ReflectiveConfigGroup {

    static public final String GROUP_NAME = "SBBIntermodal";

    static private final String PARAM_MODE = "rideMode";
    static private final String PARAM_WAITINGTIME = "waitingTime";
    static private final String PARAM_CONSTANT = "constant";
    static private final String PARAM_MUTT = "mutt";
    static private final String PARAM_DETOUR = "detourFactor";

    private String mode = "ride_feeder";
    private int waitingTime = 15 * 60;
    private double constant = 1.5;
    private double mutt = 0.003;
    private double detourFactor = 1.3;

    public SBBIntermodalConfigGroup() {
        super(GROUP_NAME);
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
    public int getWaitingtime() {
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


    @Override
    public Map<String, String> getComments() {
        Map<String, String> comments = super.getComments();
        comments.put(PARAM_MODE, "Mode to use as feeder");
        return comments;
    }
}
