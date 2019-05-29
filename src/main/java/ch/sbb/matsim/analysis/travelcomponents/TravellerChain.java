/*
 * Copyright (C) Schweizerische Bundesbahnen SBB, 2017.
 */

package ch.sbb.matsim.analysis.travelcomponents;

import org.matsim.core.config.Config;

import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;


public class TravellerChain {
	private Boolean stucked = false;
	private List<Activity> acts = new ArrayList<>(5);
	private List<Journey> journeys = new ArrayList<>(5);
	private List<TravelComponent> planElements = new ArrayList<>(5);
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

	public Journey getLastJourney() {
		if (this.journeys.isEmpty()) {
			throw new NoSuchElementException();
		}
		return this.journeys.get(this.journeys.size() - 1);
	}

	public List<Journey> getJourneys() {
		return journeys;
	}

	public void removeLastJourney() {
		this.journeys.remove(this.journeys.size() - 1);
	}

	public Activity getLastActivity() {
		if (this.acts.isEmpty()) {
			throw new NoSuchElementException();
		}
		return this.acts.get(this.acts.size() - 1);
	}

	public List<Activity> getActs() {
		return acts;
	}

	public boolean isInPT() {
		return inPT;
	}

	public void setInPT(boolean inPT) {
		this.inPT = inPT;
	}

	private boolean inPT = false;
	public boolean traveledVehicle;
	public boolean traveling=false;
	public boolean walking=false;

}
