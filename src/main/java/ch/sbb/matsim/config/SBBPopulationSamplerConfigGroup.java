/*
 * Copyright (C) Schweizerische Bundesbahnen SBB, 2017.
 */

package ch.sbb.matsim.config;

import org.matsim.core.config.ReflectiveConfigGroup;

public class SBBPopulationSamplerConfigGroup extends ReflectiveConfigGroup {

	static public final String GROUP_NAME = "SBBPopulationSamplerConfigGroup";

	private double fraction = 1.0;
	private boolean doSample = false;

	public SBBPopulationSamplerConfigGroup() {
		super(GROUP_NAME);
	}

	@StringGetter("doSample")
	public boolean getDoSample() {
		return doSample;
	}

	@StringSetter("doSample")
	void setDoSample(boolean doSample) {
		this.doSample = doSample;
	}

	@StringGetter("fraction")
	public double getFraction() {
		return fraction;
	}

	@StringSetter("fraction")
	void setFraction(double fraction) {
		this.fraction = fraction;
	}

}
