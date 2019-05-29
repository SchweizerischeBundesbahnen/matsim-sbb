/*
 * Copyright (C) Schweizerische Bundesbahnen SBB, 2017.
 */

package ch.sbb.matsim.analysis.travelcomponents;

import ch.sbb.matsim.config.variables.SBBActivities;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.core.config.Config;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.NoSuchElementException;

public class Journey extends TravelComponent {
	private String mainmode = null;
	private String mainmode_mz = null;
	private Activity fromAct;
	private Activity toAct;
	private boolean carJourney = false;
	private boolean teleportJourney = false;
	private List<Trip> trips = new ArrayList<>();
	private List<Transfer> transfers = new ArrayList<>();
	private List<TravelComponent> planElements = new ArrayList<>();
	private final Config config;

	Journey(Config config){
		super(config);
		this.config = config;
	}

	public Trip addTrip() {
		Trip trip = new Trip(this.config);
		trip.journey = this;
		getTrips().add(trip);
		planElements.add(trip);
		return trip;
	}

	private Transfer possibleTransfer;
	private double carDistance;

	public String toString() {
		return String.format("JOURNEY: start: %6.0f end: %6.0f dur: %6.0f invehDist: %6.0f walkDist: %6.0f \n %s",
				getStartTime(), getEndTime(), getDuration(), getInVehDistance(), getWalkDistance(),
				planElements.toString());
	}

	public double getInVehDistance() {
		if(getMainMode().equals("walk"))
			return 0;
		if (!isCarJourney()) {
			double distance = 0;
			for (Trip t : getTrips()) {
				distance += t.getDistance();
			}
			return distance;
		}
		return carDistance;
	}

	double getWalkDistance() {
		if(getMainMode().equals("walk"))
			return walkSpeed * getDuration();
		if (!isCarJourney()) {
			double distance = 0;
			return distance;
		}
		return 0;
	}

	public double getInVehTime() {
		if(getMainMode().equals("walk"))
			return 0;
		if (!isCarJourney()) {
			double time = 0;
			for (Trip t : getTrips()) {
				time += t.getDuration();
			}
			return time;
		}
		return getDuration();
	}

	public String getMainMode() {
		if (!(mainmode == null)) {
			return mainmode;
		}
		if (isCarJourney()) {
			return "car";
		}
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
		if (!(mainmode_mz == null)) {
			return mainmode_mz;
		}
		if (isCarJourney()) {
			return "car";
		}
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

	public double getAccessWalkDistance() {
		return 0;
	}

	public double getAccessWalkTime() {
		return 0;
	}

	public double getAccessWaitTime() {
		return 0;
	}

	public double getEgressWalkDistance() {
		return 0;

	}

	public double getEgressWalkTime() {
		return 0;
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

	public boolean isCarJourney() {
		return carJourney;
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

	public List<Transfer> getTransfers() {
		return transfers;
	}

	public void setTransfers(LinkedList<Transfer> transfers) {
		this.transfers = transfers;
	}

	public void setMainmode(String mainmode) {
		this.mainmode = mainmode;
	}

	public double getTransferWalkDistance() {
		if (!isCarJourney()) {
			double walkDistance = 0;
			for (Transfer t : this.getTransfers()) {
				walkDistance += t.getWalkDistance();
			}
			return walkDistance;
		}
		return 0;
	}

	public double getTransferWalkTime() {
		if (!isCarJourney()) {
			double walkTime = 0;
			for (Transfer t : this.getTransfers()) {
				walkTime += t.getWalkTime();
			}
			return walkTime;
		}
		return 0;
	}

	public double getTransferWaitTime() {
		if (!isCarJourney()) {
			double waitTime = 0;
			for (Transfer t : this.getTransfers()) {
				waitTime += t.getWaitTime();
			}
			return waitTime;
		}
		return 0;
	}

	public Id getFirstBoardingStop() {
		if (!isCarJourney() && this.getTrips().size() > 0) {
			return this.getFirstTrip().getBoardingStop();
		}
		return null;
	}

	public Id getLastAlightingStop() {
		if (!isCarJourney() && this.getTrips().size() > 0) {
			return this.getFirstTrip().getAlightingStop();
		}
		return null;
	}

	public boolean isTeleportJourney() {
		return teleportJourney;
	}

	public static void setWalkSpeed(double walkSpeed) {
		Journey.walkSpeed = walkSpeed;
	}
}