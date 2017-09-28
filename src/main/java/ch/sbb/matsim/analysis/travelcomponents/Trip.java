/*
 * Copyright (C) Schweizerische Bundesbahnen SBB, 2017.
 */

package ch.sbb.matsim.analysis.travelcomponents;

import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.core.config.Config;

public 	 class Trip extends TravelComponent {
	Journey journey;
	private String mode;
	private Id line;
	private Id route;
	private Coord orig;
	private Coord dest;
	private Id boardingStop;
	private Id alightingStop;
	private double distance;
	private double PtDepartureTime;
	private double PtDepartureDelay;
	private boolean departureTimeIsSet = false;

	Trip(Config config){
		super(config);
	}

	public String toString() {
		return String
				.format("\tTRIP: mode: %s start: %6.0f end: %6.0f distance: %6.0f \n",
						getMode(), getStartTime(), getEndTime(), getDistance());
	}

	public Id getLine() {
		return line;
	}

	public void setLine(Id line) {
		this.line = line;
	}

	public Id getRoute() {
		return route;
	}

	public void setRoute(Id route) {
		this.route = route;
	}

	public Id getBoardingStop() {
		return boardingStop;
	}

	public void setBoardingStop(Id boardingStop) {
		this.boardingStop = boardingStop;
	}

	public String getMode() {
		return mode;
	}

	public void setMode(String mode) {
		this.mode = mode.trim();
	}

	public double getDistance() {
		return distance;
	}

	public void setDistance(double distance) {
		this.distance = distance;
	}

	public Id getAlightingStop() {
		return alightingStop;
	}

	public void setAlightingStop(Id alightingStop) {
		this.alightingStop = alightingStop;
	}

	public Coord getDest() {
		return dest;
	}

	public void setDest(Coord dest) {
		this.dest = dest;
	}

	public Coord getOrig() {
		return orig;
	}

	public void setOrig(Coord orig) {
		this.orig = orig;
	}

	public void setPtDepartureTime(double time){
		if (!this.departureTimeIsSet){
			this.PtDepartureTime = time;
			this.departureTimeIsSet = true;
		}
	}

	public void setDepartureDelay(double delay){
		if (!this.departureTimeIsSet){
			this.PtDepartureDelay = delay;
			this.departureTimeIsSet = true;
		}
	}

	public double getDepartureDelay(){
		return this.PtDepartureDelay;
	}

	public double getPtDepartureTime(){
		return this.PtDepartureTime;
	}

	public void incrementDistance(double linkLength) {
		this.distance += linkLength;
		
	}

	public void incrementTime(double linkTime) {
		this.setEndTime(this.getEndTime()+linkTime);
		
	}
}
