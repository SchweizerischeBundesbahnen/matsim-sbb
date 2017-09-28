/*
 * Copyright (C) Schweizerische Bundesbahnen SBB, 2017.
 */

package ch.sbb.matsim.analysis.travelcomponents;

import org.matsim.core.config.Config;

import java.util.LinkedList;


public 	 class TravellerChain {
	// use linked lists so I can use the getlast method
	private Boolean stucked = false;
	private LinkedList<Activity> acts = new LinkedList<Activity>();
	private LinkedList<Journey> journeys = new LinkedList<Journey>();
	LinkedList<TravelComponent> planElements = new LinkedList<TravelComponent>();
	private Config config = null;

	public TravellerChain(Config config){
		this.config = config;
	}

	public Journey addJourney() {
		Journey journey = new Journey(this.config);
		getJourneys().add(journey);
		planElements.add(journey);
		return journey;
	}

	public Activity addActivity() {
		Activity activity = new Activity(this.config);
		getActs().add(activity);
		planElements.add(activity);
		return activity;
	}

	public Boolean getStucked() {
		return stucked;
	}
	public void setStucked(){
		this.stucked = true;
	}

	public LinkedList<Journey> getJourneys() {
		return journeys;
	}

	public void setJourneys(LinkedList<Journey> journeys) {
		this.journeys = journeys;
	}

	public LinkedList<Activity> getActs() {
		return acts;
	}

	public void setActs(LinkedList<Activity> acts) {
		this.acts = acts;
	}

	public boolean isInPT() {
		return inPT;
	}

	public void setInPT(boolean inPT) {
		this.inPT = inPT;
	}

	private boolean inPT = false;
	public boolean inCar;
	public boolean traveledVehicle;
	public boolean traveling=false;
	public boolean walking=false;
	private double linkEnterTime;

	public double getLinkEnterTime() {
		return linkEnterTime;
	}

	public void setLinkEnterTime(double linkEnterTime) {
		this.linkEnterTime = linkEnterTime;
	}

}
