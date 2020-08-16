/*
 * Copyright (C) Schweizerische Bundesbahnen SBB, 2017.
 */

package ch.sbb.matsim.analysis.travelcomponents;

import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import org.matsim.core.config.Config;

public class TravellerChain {

	private final Config config;
	private boolean isStuck = false;
	private List<Activity> acts = new ArrayList<>(5);
	private List<Trip> trips = new ArrayList<>(5);
	private boolean inPT = false;
	private List<String> visumTripIds;
	public TravellerChain(Config config) {
		this.config = config;
	}

	public Trip addTrip() {
		Trip trip = new Trip(this.config);
		String visumTripId = visumTripIds.size()>trips.size()?visumTripIds.get(trips.size()):"";
		trip.setVisumTripId(visumTripId);
		getTrips().add(trip);
		return trip;
	}

	public void setVisumTripIds(List<String> visumTripIds) {
		this.visumTripIds = visumTripIds;
	}

	public List<String> getVisumTripIds() {
		return visumTripIds;
	}

	public Activity addActivity() {
		Activity activity = new Activity(this.config);
		getActs().add(activity);
		return activity;
	}

	public boolean isStuck() {
		return isStuck;
	}

	public void setStuck() {
		this.isStuck = true;
	}

	public Trip getLastTrip() {
		if (this.trips.isEmpty()) {
			throw new NoSuchElementException();
		}
		return this.trips.get(this.trips.size() - 1);
	}

	public List<Trip> getTrips() {
		return trips;
	}

	public void removeLastTrip() {
		this.trips.remove(this.trips.size() - 1);
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

}
