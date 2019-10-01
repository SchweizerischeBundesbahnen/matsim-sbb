/*
 * Copyright (C) Schweizerische Bundesbahnen SBB, 2017.
 */

package ch.sbb.matsim.analysis.travelcomponents;

import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.core.config.Config;

public class TravelledLeg extends TravelComponent {
    private String mode;
    private Id line;
    private Id route;
    private Coord orig;
    private Coord dest;
    private Id boardingStop;
    private Id alightingStop;
    private Id vehicleId;
    private double distance;
    private double ptDepartureTime;
    private double ptDepartureDelay;
    private boolean departureTimeIsSet = false;
    private boolean isAccess = false;
    private boolean isEgress = false;

    TravelledLeg(Config config) {
        super(config);
    }

    public String toString() {
        return String
                .format("\tLEG: mode: %s start: %6.0f end: %6.0f distance: %6.0f \n",
                        getMode(), getStartTime(), getEndTime(), getDistance());
    }

    public Id getVehicleId() {
        return vehicleId;
    }

    public void setVehicleId(Id vehicleId) {
        this.vehicleId = vehicleId;
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

    public void setPtDepartureTime(double time) {
        if (!this.departureTimeIsSet) {
            this.ptDepartureTime = time;
            this.departureTimeIsSet = true;
        }
    }

    public void setDepartureDelay(double delay) {
        if (!this.departureTimeIsSet) {
            this.ptDepartureDelay = delay;
            this.departureTimeIsSet = true;
        }
    }

    public double getDepartureDelay() {
        return this.ptDepartureDelay;
    }

    public double getPtDepartureTime() {
        return this.ptDepartureTime;
    }

    public void incrementDistance(double linkLength) {
        this.distance += linkLength;

    }

    public void setIsAccess(String accessMode) {
        this.isAccess = this.getMode().equals(accessMode);
    }

    public void setIsEgress(String egressMode) {
        this.isEgress = this.getMode().equals(egressMode);
    }


    public boolean isAccessLeg() {
        return this.isAccess;
    }

    public boolean isEgressLeg() {
        return this.isEgress;
    }

    private boolean isPtLeg() {
        return (this.mode.equals("detPt") || this.mode.equals("pt"));
    }

    public boolean isRailLeg() {
        if (this.isPtLeg()) {
            return (this.line.toString().substring(0, 5).equals("S2016"));
        }
        return false;
    }
}
