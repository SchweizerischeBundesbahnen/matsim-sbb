package ch.sbb.matsim.analysis.VisumPuTSurvey;

import java.util.HashMap;
import java.util.Map;

import ch.sbb.matsim.mavi.ExportPTSupplyFromVisum;
import org.apache.log4j.Logger;
import org.matsim.api.core.v01.Id;
import org.matsim.core.utils.misc.Time;
import org.matsim.pt.transitSchedule.api.Departure;
import org.matsim.pt.transitSchedule.api.TransitLine;
import org.matsim.pt.transitSchedule.api.TransitRoute;
import org.matsim.pt.transitSchedule.api.TransitSchedule;

import ch.sbb.matsim.analysis.travelcomponents.Journey;
import ch.sbb.matsim.analysis.travelcomponents.TravellerChain;
import ch.sbb.matsim.analysis.travelcomponents.Trip;
import ch.sbb.matsim.csv.CSVWriter;

public class VisumPuTSurvey {

    private static final String FILENAME = "matsim_put_survey.att";

    private static final String COL_PATH_ID = "$OEVTEILWEG:DATENSATZNR";
    private static final String COL_LEG_ID = "TWEGIND";
    private static final String COL_FROM_STOP = "VONHSTNR";
    private static final String COL_TO_STOP = "NACHHSTNR";
    private static final String COL_VSYSCODE = "VSYSCODE";
    private static final String COL_LINNAME = "LINNAME";
    private static final String COL_LINROUTENAME = "LINROUTENAME";
    private static final String COL_RICHTUNGSCODE = "RICHTUNGSCODE";
    private static final String COL_FZPROFILNAME = "FZPNAME";
    private static final String COL_TEILWEG_KENNUNG = "TEILWEG-KENNUNG";
    private static final String COL_EINHSTNR = "EINHSTNR";
    private static final String COL_EINHSTABFAHRTSTAG = "EINHSTABFAHRTSTAG";
    private static final String COL_EINHSTABFAHRTSZEIT = "EINHSTABFAHRTSZEIT";
    private static final String COL_PFAHRT = "PFAHRT";
    private static final String[] COLUMNS = new String[] { COL_PATH_ID, COL_LEG_ID, COL_FROM_STOP, COL_TO_STOP, COL_VSYSCODE, COL_LINNAME, COL_LINROUTENAME, COL_RICHTUNGSCODE, COL_FZPROFILNAME,
            COL_TEILWEG_KENNUNG, COL_EINHSTNR, COL_EINHSTABFAHRTSTAG, COL_EINHSTABFAHRTSZEIT, COL_PFAHRT };

    final private String header = "$VISION\n* VisumInst\n* 10.11.06\n*\n*\n* Tabelle: Versionsblock\n$VERSION:VERSNR;FILETYPE;LANGUAGE;UNIT\n4.00;Att;DEU;KM\n*\n*\n* Tabelle: ÖV-Teilwege\n";
    private final static Logger log = Logger.getLogger(VisumPuTSurvey.class);
    private final CSVWriter writer = new CSVWriter(COLUMNS);

    final private Map<Id, TravellerChain> chains;

    final private Map<Id, PTVehicle> ptVehicles = new HashMap<>();
    final private TransitSchedule transitSchedule;
    private Double scaleFactor = 1.0;

    public VisumPuTSurvey(Map<Id, TravellerChain> chains, TransitSchedule transitSchedule, Double scaleFactor) {
        this.chains = chains;
        readVehicles(transitSchedule);
        this.transitSchedule = transitSchedule;
        this.scaleFactor = scaleFactor;
    }

    public CSVWriter getWriter() {
        return writer;
    }

    private void readVehicles(TransitSchedule transitSchedule) {
        for (TransitLine tL : transitSchedule.getTransitLines().values()) {
            for (TransitRoute tR : tL.getRoutes().values()) {
                for (Departure dep : tR.getDepartures().values()) {
                    Id<org.matsim.vehicles.Vehicle> vehicleId = dep.getVehicleId();
                    if (ptVehicles.containsKey(vehicleId)) {
                        log.error("vehicleId already in Map: " + vehicleId);
                    } else {
                        this.ptVehicles.put(vehicleId, new PTVehicle(tL, tR));
                    }
                }
            }
        }
    }

    public void write(String path) {
        log.info("write Visum PuT Survey File");

        for (Map.Entry<Id, TravellerChain> entry : chains.entrySet()) {
            String pax_id = entry.getKey().toString();
            TravellerChain chain = entry.getValue();
            for (Journey journey : chain.getJourneys()) {
                Integer i = 1;
                for (Trip trip : journey.getTrips()) {
                    if (this.ptVehicles.containsKey(trip.getVehicleId())) {

                        HashMap<String, String> aRow = writer.addRow();
                        aRow.put(COL_PATH_ID, Integer.toString(journey.getElementId()));
                        aRow.put(COL_LEG_ID, Integer.toString(i));
                        String boarding = this.transitSchedule.getTransitStopsAttributes().getAttribute(trip.getBoardingStop().toString(), ExportPTSupplyFromVisum.ATT_STOP_NO).toString();
                        aRow.put(COL_FROM_STOP, boarding);
                        aRow.put(COL_TO_STOP, this.transitSchedule.getTransitStopsAttributes().getAttribute(trip.getAlightingStop().toString(), ExportPTSupplyFromVisum.ATT_STOP_NO).toString());

                        Id vId = trip.getVehicleId();

                        PTVehicle vehicle = this.ptVehicles.get(vId);
                        String direction = this.transitSchedule.getTransitLinesAttributes().getAttribute(vehicle.getRouteId().toString(), ExportPTSupplyFromVisum.ATT_DIRECTIONCODE).toString();
                        String vsys = this.transitSchedule.getTransitLinesAttributes().getAttribute(vehicle.getRouteId().toString(), ExportPTSupplyFromVisum.ATT_TSYSNAME).toString();
                        String line = this.transitSchedule.getTransitLinesAttributes().getAttribute(vehicle.getRouteId().toString(), ExportPTSupplyFromVisum.ATT_TRANSITLINE).toString();
                        String lineroute = this.transitSchedule.getTransitLinesAttributes().getAttribute(vehicle.getRouteId().toString(), ExportPTSupplyFromVisum.ATT_LINEROUTENAME).toString();
                        String fzp = this.transitSchedule.getTransitLinesAttributes().getAttribute(vehicle.getRouteId().toString(), ExportPTSupplyFromVisum.ATT_FZPNAME).toString();

                        aRow.put(COL_VSYSCODE, vsys);
                        aRow.put(COL_LINNAME, line);
                        aRow.put(COL_LINROUTENAME, lineroute);
                        aRow.put(COL_RICHTUNGSCODE, direction);
                        aRow.put(COL_FZPROFILNAME, fzp);

                        String kennung = "E";
                        if (i > 1) {
                            kennung = "N";
                        }

                        aRow.put(COL_TEILWEG_KENNUNG, kennung);
                        aRow.put(COL_EINHSTNR, boarding);

                        int day = (int) Math.ceil(trip.getStartTime() / (24 * 60 * 60.0));
                        assert day > 0;

                        aRow.put(COL_EINHSTABFAHRTSTAG, Integer.toString(day));
                        aRow.put(COL_EINHSTABFAHRTSZEIT, Time.writeTime(trip.getStartTime()));

                        Double pfahrt = 1.0 * scaleFactor;
                        aRow.put(COL_PFAHRT, Integer.toString(pfahrt.intValue()));

                        i++;
                    }
                }
            }
        }

        final String filepath = path + FILENAME;
        log.info(path + FILENAME);

        writer.setHeader(header);
        writer.write(filepath);

    }

    private class PTVehicle {
        TransitLine transitLine;
        TransitRoute transitRoute;

        private PTVehicle(TransitLine tl, TransitRoute tr) {
            transitLine = tl;
            transitRoute = tr;
        }

        private Id getRouteId() {
            return transitRoute.getId();
        }
    }


}
