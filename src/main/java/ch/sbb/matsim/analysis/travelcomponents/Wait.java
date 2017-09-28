/*
 * Copyright (C) Schweizerische Bundesbahnen SBB, 2017.
 */

package ch.sbb.matsim.analysis.travelcomponents;

import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.core.config.Config;

public 	 class Wait extends TravelComponent {
	public Journey journey;
	private Coord coord;
	private boolean accessWait = false;
	private Id stopId;

	Wait(Config config){
		super(config);
	}

	public String toString() {
		return String.format(
				"\tWAIT: start: %6.0f end: %6.0f dur: %6.0f \n", getStartTime(),
				getEndTime(), getEndTime() - getStartTime());
	}

	public boolean isAccessWait() {
		return accessWait;
	}

	public void setAccessWait(boolean accessWait) {
		this.accessWait = accessWait;
	}

	public Coord getCoord() {
		return coord;
	}

	public void setCoord(Coord coord) {
		this.coord = coord;
	}

	public Id getStopId() {
		return stopId;
	}

	public void setStopId(Id stopId) {
		this.stopId = stopId;
	}
}