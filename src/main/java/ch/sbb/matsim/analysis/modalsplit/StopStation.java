package ch.sbb.matsim.analysis.modalsplit;

import ch.sbb.matsim.config.variables.SBBModes;
import ch.sbb.matsim.zones.Zone;
import java.util.List;
import org.matsim.pt.transitSchedule.api.TransitStopFacility;

public class StopStation {

    private static final List<String> modes = SBBModes.TRAIN_STATION_MODES;
    private int entered = 0;
    private int exited = 0;
    private final int[] enteredMode = new int[modes.size()];
    private final int[] exitedMode = new int[modes.size()];
    private int umsteigeBahnBahn = 0;
    private int umsteigeAHPBahn = 0;
    private int umsteigeBahnAHP = 0;
    private final Zone zone;
    private final TransitStopFacility stop;
    public StopStation(TransitStopFacility trainStation, Zone zone) {
        this.stop = trainStation;
        this.zone = zone;
    }

    public String getZoneId(){
        if (zone == null) {
            return "NA";
        }
        return zone.getId().toString();
    }

    public void addUmstiegeBahnBahn() {
        umsteigeBahnBahn++;
    }
    public void addUmsteigeAHPBahn() {
        umsteigeAHPBahn++;
    }
    public void addUmsteigeBahnAHP() {
        umsteigeBahnAHP++;
    }


    public void addEntred() {
        entered++;
    }

    public void addExited() {
        exited++;
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

    public TransitStopFacility getStop() {
        return stop;
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

    public int getUmsteigeBahnBahn() {
        return umsteigeBahnBahn;
    }

    public int getUmsteigeAHPBahn() {
        return umsteigeAHPBahn;
    }

    public int getUmsteigeBahnAHP() {
        return umsteigeBahnAHP;
    }
}
