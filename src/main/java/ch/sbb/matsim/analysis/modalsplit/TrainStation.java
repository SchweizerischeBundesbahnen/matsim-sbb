package ch.sbb.matsim.analysis.modalsplit;

import ch.sbb.matsim.zones.Zone;
import org.matsim.pt.transitSchedule.api.TransitStopFacility;

public class TrainStation {

    private final Zone zone;
    private final String hstNummer;
    private final String stopCode;
    private final String stationName;
    private int zielAussteiger = 0;
    private int quellEinsteiger = 0;
    private int gesamtEinsteiger = 0;
    private int gesamtAussteiger = 0;
    private int umsteigerTyp5a = 0;
    private int umsteigerSimbaSimba = 0;
    private int umsteigerSimbaAndere = 0;
    private int umsteigerAndereSimba = 0;
    private int umsteigerAndereAndere = 0;
    private int umsteigerTyp5b = 0;

    public TrainStation(TransitStopFacility transitStopFacility, Zone zone) {
        this.hstNummer = transitStopFacility.getAttributes().getAttribute("02_Stop_No").toString();
        Object nameAttribute = transitStopFacility.getAttributes().getAttribute("04_Stop_Name");
        this.stationName = nameAttribute==null?transitStopFacility.getName():nameAttribute.toString();
        Object codeAttribute = transitStopFacility.getAttributes().getAttribute("03_Stop_Code");
        if (codeAttribute == null) {
            this.stopCode = "NA";
        } else {
            this.stopCode = codeAttribute.toString();
        }
        this.zone = zone;
    }

    public void addZielAussteiger() {
        zielAussteiger++;
    }
    public void addQuellEinsteiger() {
        quellEinsteiger++;
    }

    public void addGesamtEinsteiger() {
        gesamtEinsteiger++;
    }

    public void addGesamtAussteiger() {
        gesamtAussteiger++;
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

    public String getHstNummer() {
        return hstNummer;
    }
    public String getStopCode() {
        return stopCode;
    }

    public String getStationName() {
        return stationName;
    }

    public int getZielAussteiger() {
        return zielAussteiger;
    }

    public int getQuellEinsteiger() {
        return quellEinsteiger;
    }

    public int getGesamtEinsteiger() {
        return gesamtEinsteiger;
    }

    public int getGesamtAussteiger() {
        return gesamtAussteiger;
    }

    public int getSummeEinAussteiger() {
        return gesamtEinsteiger + gesamtAussteiger;
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
