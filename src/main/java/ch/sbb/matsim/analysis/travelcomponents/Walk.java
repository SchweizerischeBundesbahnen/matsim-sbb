/*
 * Copyright (C) Schweizerische Bundesbahnen SBB, 2017.
 */

package ch.sbb.matsim.analysis.travelcomponents;

import org.matsim.api.core.v01.Coord;
import org.matsim.core.config.Config;

public  class Walk extends TravelComponent {
	public Journey journey;
	private Coord orig;
	private Coord dest;
	private double distance;
	private boolean accessWalk = false;
	private boolean egressWalk = false;

	Walk(Config config){
		super(config);
	}

	public String toString() {
		return String.format(
				"\tWALK: start: %6.0f end: %6.0f distance: %6.0f \n",
				getStartTime(), getEndTime(), getDistance());
	}

	public boolean isEgressWalk() {
		return egressWalk;
	}

	public void setEgressWalk(boolean egressWalk) {
		this.egressWalk = egressWalk;
	}

	public boolean isAccessWalk() {
		return accessWalk;
	}

	public void setAccessWalk(boolean accessWalk) {
		this.accessWalk = accessWalk;
	}

	public Coord getOrig() {
		return orig;
	}

	public void setOrig(Coord orig) {
		this.orig = orig;
	}

	public Coord getDest() {
		return dest;
	}

	public void setDest(Coord dest) {
		this.dest = dest;
	}

	public double getDistance() {
		return distance;
	}

	public void setDistance(double distance) {
		this.distance = distance;
	}
}