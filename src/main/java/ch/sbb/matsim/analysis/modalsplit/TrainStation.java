package ch.sbb.matsim.analysis.modalsplit;

import ch.sbb.matsim.zones.Zone;
import java.util.ArrayList;
import java.util.List;
import org.matsim.pt.transitSchedule.api.TransitStopFacility;

public class TrainStation {

    private List<TransitStopFacility> stops = new ArrayList<>();
    private final Zone zone;
    private final String station;

    private int zielAussteiger = 0;
    private int quellEinsteiger = 0;
    private int umsteigerTyp5a = 0;
    private int umsteigerSimbaSimba = 0;
    private int umsteigerSimbaAndere = 0;
    private int umsteigerAndereSimba = 0;
    private int umsteigerAndereAndere = 0;
    private int umsteigerTyp5b = 0;

    public TrainStation(String code, Zone zone) {
        this.station = code;
        this.zone = zone;
    }

    public void addStop(TransitStopFacility transitStopFacility) {
        stops.add(transitStopFacility);
    }

    public void addZielAussteiger() {
        zielAussteiger++;
    }
    public void addQuellEinsteiger() {
        quellEinsteiger++;
    }

    public void addUmsteigerTyp5a() {
        umsteigerTyp5a++;
    }

    public void addUmsteigerSimbaSimba() {
        umsteigerSimbaSimba++;
    }

    public void addUmsteigerSimbaAndere() {
        umsteigerSimbaAndere++;
    }

    public void addUmsteigerTyp5b() {
        umsteigerTyp5b++;
    }

    public void addUmsteigerAndereSimba() {
        umsteigerAndereSimba++;
    }

    public void addUmsteigerAndereAndere() {
        umsteigerAndereAndere++;
    }

    public String getZoneId() {
        if (zone == null) {
            return "NA";
        }
        return zone.getId().toString();
    }

    public String getStation() {
        return station;
    }

    public int getZielAussteiger() {
        return zielAussteiger;
    }

    public int getQuellEinsteiger() {
        return quellEinsteiger;
    }

    public int getUmsteigerTyp5a() {
        return umsteigerTyp5a;
    }

    public int getUmsteigerSimbaSimba() {
        return umsteigerSimbaSimba;
    }

    public int getUmsteigerSimbaAndere() {
        return umsteigerSimbaAndere;
    }

    public int getUmsteigerAndereSimba() {
        return umsteigerAndereSimba;
    }

    public int getUmsteigerAndereAndere() {
        return umsteigerAndereAndere;
    }

    public int getUmsteigerTyp5b() {
        return umsteigerTyp5b;
    }
}
