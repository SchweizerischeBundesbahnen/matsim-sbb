package ch.sbb.matsim.analysis.modalsplit;

import ch.sbb.matsim.zones.Zone;
import org.matsim.facilities.Facility;

public class TrainStation {

    private int demand = 0;
    private int entered = 0;
    private int exited = 0;

    private Zone zone;
    private Facility station;
    public TrainStation(Facility trainStation, Zone zone) {
        this.station = trainStation;
        this.zone = zone;
    }

    public String getZoneId(){
        if (zone == null) {
            return "NA";
        }
        return zone.getId().toString();
    }

    public void addEntred() {
        demand++;
        entered++;
    }

    public void addExited() {
        demand++;
        exited++;
    }

    public int getDemand() {
        return demand;
    }

    public int getEntered() {
        return entered;
    }

    public int getExited() {
        return exited;
    }

    public Zone getZone() {
        return zone;
    }

    public Facility getStation() {
        return station;
    }
}
