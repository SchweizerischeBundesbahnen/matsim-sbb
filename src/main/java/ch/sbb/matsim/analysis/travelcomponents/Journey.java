/*
 * Copyright (C) Schweizerische Bundesbahnen SBB, 2017.
 */

package ch.sbb.matsim.analysis.travelcomponents;

import ch.sbb.matsim.config.variables.SBBActivities;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.pt.router.TransitRouterConfig;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.NoSuchElementException;

public class Journey extends TravelComponent {

    private static double walkSpeed = new TransitRouterConfig(ConfigUtils.createConfig()).getBeelineWalkSpeed();

	private Activity fromAct;
	private Activity toAct;
    private List<Trip> trips = new ArrayList<>();
	private List<TravelComponent> planElements = new ArrayList<>();
	private final Config config;

	Journey(Config config){
		super(config);
		this.config = config;
	}

	public Trip addTrip() {
		Trip trip = new Trip(this.config);
		getTrips().add(trip);
		planElements.add(trip);
		return trip;
	}

	public String toString() {
		return String.format("JOURNEY: start: %6.0f end: %6.0f dur: %6.0f invehDist: %6.0f walkDist: %6.0f \n %s",
				getStartTime(), getEndTime(), getDuration(), getInVehDistance(), getWalkDistance(),
				planElements.toString());
	}

	public double getInVehDistance() {
		if(getMainMode().equals("walk"))
			return 0;
		double distance = 0;
		for (Trip t : getTrips()) {
			distance += t.getDistance();
		}
		return distance;
	}

	private double getWalkDistance() {
		if(getMainMode().equals("walk"))
			return walkSpeed * getDuration();
		return 0;
	}

	public double getInVehTime() {
		if(getMainMode().equals("walk"))
			return 0;
		double time = 0;
		for (Trip t : getTrips()) {
			time += t.getDuration();
		}
		return time;
	}

	public String getMainMode() {
		try {
			Trip longestTrip = null;
			if (getTrips().size() > 1) {
				for (int i = 1; i < getTrips().size(); i++) {
					Trip trip = getTrips().get(i);
					if(trip.getMode().equals(TransportMode.egress_walk) || trip.getMode().equals(TransportMode.access_walk)){
					}
					else if(longestTrip == null){
						longestTrip = trip;
					}
					else if (trip.getDistance() > longestTrip.getDistance()) {
						longestTrip = getTrips().get(i);
					}
				}
				return longestTrip.getMode();
			}
			else{
				return getFirstTrip().getMode();
			}

		} catch (NoSuchElementException e) {
			return "walk";

		}
	}

	public String getMainModeMikroZensus() {
		try {
			Trip firstTrip = getFirstTrip();
			if (getTrips().size() > 1) {
				return "pt";
			}
			if(firstTrip.getMode().equals("transit_walk"))
				return "walk";
			else
				return firstTrip.getMode();

		} catch (NoSuchElementException e) {
			return "walk";
		}
	}

	public String getToActType()	{
		String typeLong = this.toAct.getType();
		String type = typeLong.split("_")[0];
		return SBBActivities.matsimActs2abmActs.get(type);
	}

	public double getDistance() {
		return getInVehDistance() + getWalkDistance();
	}

	public Activity getFromAct() {
		return fromAct;
	}

	public void setFromAct(Activity fromAct) {
		this.fromAct = fromAct;
	}

	public Activity getToAct() {
		return toAct;
	}

	public void setToAct(Activity toAct) {
		this.toAct = toAct;
	}

	public Trip getFirstTrip() {
		if (this.trips.isEmpty()) {
			return null;
		}
		return this.trips.get(0);
	}

	public Trip getLastTrip() {
		if (this.trips.isEmpty()) {
			return null;
		}
		return this.trips.get(this.trips.size() - 1);
	}

	public List<Trip> getTrips() {
		return trips;
	}

	public void setTrips(LinkedList<Trip> trips) {
		this.trips = trips;
	}

	public Id getFirstBoardingStop() {
		if (this.getTrips().size() > 0) {
			return this.getFirstTrip().getBoardingStop();
		}
		return null;
	}

	public Id getLastAlightingStop() {
		if (this.getTrips().size() > 0) {
			return this.getFirstTrip().getAlightingStop();
		}
		return null;
	}

	public static void setWalkSpeed(double walkSpeed) {
		Journey.walkSpeed = walkSpeed;
	}
}