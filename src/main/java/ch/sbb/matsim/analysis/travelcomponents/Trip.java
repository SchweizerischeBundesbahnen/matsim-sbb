/*
 * Copyright (C) Schweizerische Bundesbahnen SBB, 2017.
 */

package ch.sbb.matsim.analysis.travelcomponents;

import ch.sbb.matsim.config.variables.SBBActivities;
import ch.sbb.matsim.config.variables.SBBModes;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.core.config.Config;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class Trip extends TravelComponent {

    private double walkSpeed;
    private Activity fromAct;
    private Activity toAct;
    private TravelledLeg firstRailLeg;
    private TravelledLeg lastRailLeg;
    private List<TravelledLeg> legs = new ArrayList<>();
    private final Config config;

    Trip(Config config) {
        super(config);
        this.config = config;
        this.walkSpeed = config.plansCalcRoute().getModeRoutingParams().get( TransportMode.walk ).getTeleportedModeSpeed();
    }

    public TravelledLeg addLeg() {
        TravelledLeg leg = new TravelledLeg(this.config);
        this.legs.add(leg);
        return leg;
    }

    public List<TravelledLeg> getLegs() {
        return this.legs;
    }

    public TravelledLeg getFirstLeg() {
        return this.legs.get(0);
    }

    public TravelledLeg getLastLeg() {
        return this.legs.get(this.legs.size() - 1);
    }

    public String toString() {
        return String.format("TRIP: start: %6.0f end: %6.0f dur: %6.0f invehDist: %6.0f walkDist: %6.0f \n %s",
                getStartTime(), getEndTime(), getDuration(), getInVehDistance(), getWalkDistance(),
                legs.toString());
    }

    public double getInVehDistance() {
        if (getMainMode().equals(SBBModes.WALK))
            return 0;
        return this.legs.stream().mapToDouble(TravelledLeg::getDistance).sum();
    }

    private double getWalkDistance() {
        if (getMainMode().equals(SBBModes.WALK))
            return walkSpeed * getDuration();
        return 0;
    }

    public double getDistance() {
        return getInVehDistance() + getWalkDistance();
    }

    public double getInVehTime() {
        if (getMainMode().equals(SBBModes.WALK))
            return 0;
        return this.legs.stream().mapToDouble(TravelledLeg::getDuration).sum();
    }

    public String getMainMode() {
        // get main mode according to hierarchical order
        TravelledLeg leg = Collections.min(this.legs, Comparator.comparing(TravelledLeg::getModeHierarchy));
        if (leg.getModeHierarchy() != SBBModes.DEFAULT_MODE_HIERARCHY) {
            if (leg.isPtLeg()) {
                return SBBModes.PT;
            }
            String mainMode = leg.getMode();
            if (mainMode.equals(SBBModes.PT_FALLBACK_MODE)) {
                return SBBModes.WALK;
            }
            return mainMode;
        }
        else    {
            // fallback solution -> get main mode according to longest distance
            return Collections.max(this.legs, Comparator.comparing(TravelledLeg::getDistance)).getMode();
        }
    }

    public void setFromAct(Activity fromAct) {
        this.fromAct = fromAct;
    }

    public Activity getFromAct() {
        return fromAct;
    }

    public void setToAct(Activity toAct) {
        this.toAct = toAct;
    }

    public Activity getToAct() {
        return toAct;
    }

    public String getToActType() {
        String typeLong = this.toAct.getType();
        String type = typeLong.split("_")[0];
        return SBBActivities.matsimActs2abmActs.get(type);
    }

    public List<TravelledLeg> getAccessLegs() {
        List<TravelledLeg> accessLegs = new ArrayList<>();
        if(!isRailJourney())    {
            return accessLegs;
        }
        for (TravelledLeg leg : this.legs) {
            if (leg.isRailLeg()) {
                this.firstRailLeg = leg;
                break;
            }
            if (leg.getDistance() > 0) {
                accessLegs.add(leg);
            }
        }
        return accessLegs;
    }

    public List<TravelledLeg> getEgressLegs() {
        List<TravelledLeg> egressLegs = new ArrayList<>();
        TravelledLeg leg;
        if(!isRailJourney())    {
            return egressLegs;
        }
        for (int i = this.legs.size() - 1; i >= 0; i--) {
            leg = this.legs.get(i);
            if (leg.isRailLeg()) {
                this.lastRailLeg = leg;
                break;
            }
            if (leg.getDistance() > 0) {
                egressLegs.add(0, leg);
            }
        }
        return egressLegs;
    }

    public boolean isRailJourney() {
        return this.legs.stream().anyMatch(TravelledLeg::isRailLeg);
    }

    public String getAccessToRailMode(List<TravelledLeg> accessLegs) {
        if (accessLegs == null || accessLegs.isEmpty()) {
            return "";
        } else if (accessLegs.size() > 1) {
            return accessLegs.get(1).getMode();
        } else {
            return accessLegs.get(0).getMode();
        }
    }

    public String getEgressFromRailMode(List<TravelledLeg> egressLegs) {
        if (egressLegs == null || egressLegs.isEmpty()) {
            return "";
        } else if (egressLegs.size() > 1) {
            return egressLegs.get(egressLegs.size() - 2).getMode();
        } else {
            return egressLegs.get(egressLegs.size() - 1).getMode();
        }
    }

    public double getAccessToRailDist(List<TravelledLeg> accessLegs) {
        if (accessLegs == null) {
            return 0;
        }
        return accessLegs.stream().mapToDouble(TravelledLeg::getDistance).sum();
    }

    public double getEgressFromRailDist(List<TravelledLeg> egressLegs) {
        if (egressLegs == null) {
            return 0;
        }
        return egressLegs.stream().mapToDouble(TravelledLeg::getDistance).sum();
    }

    public Id getFirstRailBoardingStop() {
        if (this.firstRailLeg == null) {
            return null;
        }
        return this.firstRailLeg.getBoardingStop();
    }

    public Id getLastRailAlightingStop() {
        if (this.lastRailLeg == null) {
            return null;
        }
        return this.lastRailLeg.getAlightingStop();
    }
}