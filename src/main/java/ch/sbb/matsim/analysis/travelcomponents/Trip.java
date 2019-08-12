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
import java.util.List;
import java.util.NoSuchElementException;

public class Trip extends TravelComponent {

    private static double walkSpeed = new TransitRouterConfig(ConfigUtils.createConfig()).getBeelineWalkSpeed();

	private Activity fromAct;
	private Activity toAct;
    private List<TravelledLeg> legs = new ArrayList<>();
	private final Config config;

	Trip(Config config){
		super(config);
		this.config = config;
	}

	public TravelledLeg addLeg() {
		TravelledLeg leg = new TravelledLeg(this.config);
		this.legs.add(leg);
		return leg;
	}

	public String toString() {
		return String.format("TRIP: start: %6.0f end: %6.0f dur: %6.0f invehDist: %6.0f walkDist: %6.0f \n %s",
				getStartTime(), getEndTime(), getDuration(), getInVehDistance(), getWalkDistance(),
				legs.toString());
	}

	public double getInVehDistance() {
		if(getMainMode().equals("walk"))
			return 0;
		double distance = 0;
		for (TravelledLeg t : getLegs()) {
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
		for (TravelledLeg t : getLegs()) {
			time += t.getDuration();
		}
		return time;
	}

	public String getMainMode() {
		try {
			TravelledLeg longestLeg = null;
			if (getLegs().size() > 1) {
				for (int i = 1; i < getLegs().size(); i++) {
					TravelledLeg leg = getLegs().get(i);
					if (leg.getMode().equals(TransportMode.egress_walk) || leg.getMode().equals(TransportMode.access_walk)) {
					}
					else if (longestLeg == null) {
						longestLeg = leg;
					}
					else if (leg.getDistance() > longestLeg.getDistance()) {
						longestLeg = getLegs().get(i);
					}
				}
				return longestLeg.getMode();
			}
			else{
				return getFirstLeg().getMode();
			}

		} catch (NoSuchElementException e) {
			return "walk";

		}
	}

	public String getMainModeMikroZensus() {
		try {
			if (this.legs.size() > 1) {
				return "pt";
			}
			TravelledLeg firstLeg = getFirstLeg();
			if (firstLeg.getMode().equals("transit_walk"))
				return "walk";
			else
				return firstLeg.getMode();

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

	public TravelledLeg getFirstLeg() {
		return this.legs.get(0);
	}

	public TravelledLeg getLastLeg() {
		return this.legs.get(this.legs.size() - 1);
	}

	public List<TravelledLeg> getLegs() {
		return legs;
	}

	public Id getFirstBoardingStop() {
	    if (this.legs.isEmpty()) {
	        return null;
        }
		return this.getFirstLeg().getBoardingStop();
	}

	public Id getLastAlightingStop() {
	    if (this.legs.isEmpty()) {
	        return null;
        }
		return this.getFirstLeg().getAlightingStop();
	}

	public static void setWalkSpeed(double walkSpeed) {
		Trip.walkSpeed = walkSpeed;
	}
}