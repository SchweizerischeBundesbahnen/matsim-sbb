/*
 * Copyright (C) Schweizerische Bundesbahnen SBB, 2017.
 */

package ch.sbb.matsim.analysis.travelcomponents;

import java.util.concurrent.atomic.AtomicInteger;
import org.matsim.core.config.Config;

public class TravelComponent {

	public double getDuration() {
		return getEndTime() - getStartTime();
	}

	private static AtomicInteger id = new AtomicInteger(0); // for enumeration

	private double startTime;
	private double endTime;

	private int elementId;

	public TravelComponent(Config config) {
		elementId = id.incrementAndGet();
		endTime = config.qsim().getEndTime().orElse(30 * 3600);

	}

	public int getElementId() {
		return elementId;
	}

	public double getStartTime() {
		return startTime;
	}

	public void setStartTime(double startTime) {
		this.startTime = startTime;
	}

	public double getEndTime() {
		return endTime;
	}

	public void setEndTime(double endTime) {
		this.endTime = endTime;
	}


	





	

	 


}
