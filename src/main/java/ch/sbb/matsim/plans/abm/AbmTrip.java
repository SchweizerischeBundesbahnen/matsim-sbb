package ch.sbb.matsim.plans.abm;

import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.facilities.ActivityFacility;

public class AbmTrip {

    private String oAct;
    private String dAct;
    private String mode;
    private int deptime;
    private int arrtime;

    private Id<ActivityFacility> origFacilityId;
    private Id<ActivityFacility> destFacilityId;

    private final Coord coordOrig;
    private final Coord coordDest;


    public Coord getCoordOrig() {
        return coordOrig;
    }

    public Coord getCoordDest() {
        return coordDest;
    }

    public AbmTrip(Id<ActivityFacility> origFacilityId, Id<ActivityFacility> destFacilityId, String oAct, String dAct, String mode, int deptime, int arrtime, Coord coordOrig, Coord coordDest) {
        this.oAct = oAct;
        this.dAct = dAct;
        this.mode = mode;
        this.deptime = deptime;
        this.arrtime = arrtime;

        this.origFacilityId = origFacilityId;
        this.destFacilityId = destFacilityId;

        this.coordDest = coordDest;
        this.coordOrig = coordOrig;

    }

    public Id<ActivityFacility> getOrigFacilityId() {
        return origFacilityId;
    }

    public Id<ActivityFacility> getDestFacilityId() {
        return destFacilityId;
    }

    public String getMode() {
        return this.mode;
    }

    public int getArrtime() {
        return arrtime;
    }


    public int getDepTime() {
        return this.deptime;
    }

    public String getDestAct() {
        return this.dAct;
    }

    public String getoAct() {
        return oAct;
    }

}
