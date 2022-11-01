package ch.sbb.matsim.analysis.modalsplit;

import ch.sbb.matsim.config.variables.SBBModes;
import ch.sbb.matsim.zones.Zone;
import java.util.List;
import org.matsim.pt.transitSchedule.api.TransitStopFacility;

public class TrainStation {

    private static final List<String> modes = SBBModes.TRAIN_STATION_MODES;
    private int enteredAndExited = 0;
    private int entered = 0;
    private int exited = 0;
    private final int[] enteredMode = new int[modes.size()];
    private final int[] exitedMode = new int[modes.size()];
    private int zustiege = 0;
    private int wegstiege = 0;
    private int umsteige = 0;
    private final Zone zone;
    private final TransitStopFacility station;
    public TrainStation(TransitStopFacility trainStation, Zone zone) {
        this.station = trainStation;
        this.zone = zone;
    }

    public String getZoneId(){
        if (zone == null) {
            return "NA";
        }
        return zone.getId().toString();
    }

    public void addUmstiege() {
        umsteige++;
    }

    public void addZustiege() {
        zustiege++;
    }

    public void addWegstiege() {
        wegstiege++;
    }

    public void addEntred() {
        enteredAndExited++;
        entered++;
    }

    public void addExited() {
        enteredAndExited++;
        exited++;
    }

    public int getEnteredAndExited() {
        return enteredAndExited;
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

    public TransitStopFacility getStation() {
        return station;
    }

    public static List<String> getModes() {
        return modes;
    }

    public int[] getEnteredMode() {
        return enteredMode;
    }

    public int[] getExitedMode() {
        return exitedMode;
    }

    public int getZustiege() {
        return zustiege;
    }

    public int getWegstiege() {
        return wegstiege;
    }

    public int getUmsteige() {
        return umsteige;
    }

}
