package ch.sbb.matsim.projects.elm;

import ch.sbb.matsim.zones.Zone;
import java.util.ArrayList;
import java.util.List;
import org.matsim.api.core.v01.Coord;
import org.matsim.pt.transitSchedule.api.TransitStopFacility;

public class TrainStaiton {

    private List<TransitStopFacility> stops = new ArrayList<>();
    private final String stopNummer;
    private final String stopCode;
    private final Coord coord;

    private int velo = 0;

    public TrainStaiton(TransitStopFacility transitStopFacility) {
        this.stopNummer = transitStopFacility.getAttributes().getAttribute("02_Stop_No").toString();
        Object codeAttribute = transitStopFacility.getAttributes().getAttribute("03_Stop_Code");
        if (codeAttribute == null) {
            this.stopCode = "NA";
        } else {
            this.stopCode = codeAttribute.toString();
        }
        this.coord = transitStopFacility.getCoord();
    }

    public void addStop(TransitStopFacility transitStopFacility) {
        stops.add(transitStopFacility);
    }

    public void addVelo() {
        velo++;
    }

    public int getVelo() {
        return velo;
    }

    public Coord getCoord() {
        return coord;
    }

}
