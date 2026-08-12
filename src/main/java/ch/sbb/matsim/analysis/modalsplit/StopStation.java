package ch.sbb.matsim.analysis.modalsplit;

import ch.sbb.matsim.zones.Zone;
import org.matsim.pt.transitSchedule.api.TransitStopFacility;

import java.util.HashMap;
import java.util.Map;

public class StopStation {

    private int entered = 0;
    private int exited = 0;
    private int enteredFQ = 0;
    private int exitedFQ = 0;
    private final Map<String, Integer> enteredByMode = new HashMap<>();
    private final Map<String, Integer> exitedByMode = new HashMap<>();
    private int umsteigeBahnBahn = 0;
    private int umsteigeAHPBahn = 0;
    private int umsteigeBahnAHP = 0;
    private boolean isRailStation = false;
    private final Zone zone;
    private final TransitStopFacility stop;

    public StopStation(TransitStopFacility stop, Zone zone) {
        this.stop = stop;
        this.zone = zone;
    }

    public void addEntered() { entered++; }
    public void addExited() { exited++; }
    public void addEnteredFQ() { enteredFQ++; }
    public void addExitedFQ() { exitedFQ++; }
    public void setRailStation() { isRailStation = true; }
    public void addUmstiegeBahnBahn() { umsteigeBahnBahn++; }
    public void addUmsteigeAHPBahn() { umsteigeAHPBahn++; }
    public void addUmsteigeBahnAHP() { umsteigeBahnAHP++; }

    public void incrementEnteredForMode(String mode) {
        enteredByMode.merge(mode, 1, Integer::sum);
    }

    public void incrementExitedForMode(String mode) {
        exitedByMode.merge(mode, 1, Integer::sum);
    }

    public int getEnteredForMode(String mode) {
        return enteredByMode.getOrDefault(mode, 0);
    }

    public int getExitedForMode(String mode) {
        return exitedByMode.getOrDefault(mode, 0);
    }

    public String getZoneId() {
        return zone != null ? zone.getId().toString() : "NA";
    }

    public boolean getIsRailStation() { return isRailStation; }
    public int getEntered() { return entered; }
    public int getExited() { return exited; }
    public int getEnteredFQ() { return enteredFQ; }
    public int getExitedFQ() { return exitedFQ; }
    public int getUmsteigeBahnBahn() { return umsteigeBahnBahn; }
    public int getUmsteigeAHPBahn() { return umsteigeAHPBahn; }
    public int getUmsteigeBahnAHP() { return umsteigeBahnAHP; }
    public TransitStopFacility getStop() { return stop; }
    public Zone getZone() { return zone; }
}
