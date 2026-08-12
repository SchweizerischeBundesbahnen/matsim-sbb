package ch.sbb.matsim.analysis.modalsplit;

import ch.sbb.matsim.analysis.tripsandlegsanalysis.RailTripsAnalyzer;
import ch.sbb.matsim.config.variables.SBBModes;
import ch.sbb.matsim.csv.CSVWriter;
import ch.sbb.matsim.zones.Zone;
import ch.sbb.matsim.zones.Zones;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.population.Leg;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.Plan;
import org.matsim.core.router.TripStructureUtils;
import org.matsim.pt.transitSchedule.api.TransitSchedule;
import org.matsim.pt.transitSchedule.api.TransitStopFacility;

import java.util.*;

import static ch.sbb.matsim.analysis.modalsplit.MSVariables.*;
import static ch.sbb.matsim.config.variables.SBBModes.PT;

/**
 * Counts boardings, alightings, FQ boardings/alightings, transfer types, and mode split at each
 * {@link TransitStopFacility}.
 *
 * <p>Replaces {@code ModalSplitStats#analyzeStopsStations} and
 * {@code ModalSplitStats#writeStopStationAnalysis}.
 */
public class StopBoardingCollector implements TripDataCollector {

    private final TransitSchedule transitSchedule;
    private final Zones zones;
    private final RailTripsAnalyzer railTripsAnalyzer;
    private final LongestMainModeIdentifier mainModeIdentifier = new LongestMainModeIdentifier();

    /** All modes that can appear at a stop (used as CSV column headers). */
    private final List<String> possibleOriginDestinationModesAtStop;

    /** Modes appearing in the transit schedule per stop, derived once at construction. */
    private final Map<Id<TransitStopFacility>, Set<String>> actualModesAtStop;

    /** One entry per stop facility, initialised from the schedule. */
    private final Map<Id<TransitStopFacility>, StopStation> stopStationsMap;

    public StopBoardingCollector(TransitSchedule transitSchedule,
                                 Zones zones,
                                 RailTripsAnalyzer railTripsAnalyzer,
                                 Set<String> feederModes) {
        this.transitSchedule = transitSchedule;
        this.zones = zones;
        this.railTripsAnalyzer = railTripsAnalyzer;

        Set<String> origDestSet = new HashSet<>(SBBModes.TRAIN_STATION_ORIGDEST_MODES);
        origDestSet.addAll(feederModes);
        this.possibleOriginDestinationModesAtStop = new ArrayList<>(origDestSet);

        this.actualModesAtStop = buildActualModesAtStop();
        this.stopStationsMap = buildStopStationMap();
    }

    // -------------------------------------------------------------------------
    // TripDataCollector
    // -------------------------------------------------------------------------

    @Override
    public void collect(Person person, Plan experiencedPlan) {
        for (TripStructureUtils.Trip trip : TripStructureUtils.getTrips(experiencedPlan)) {
            if (!mainModeIdentifier.identifyMainMode(trip.getTripElements()).equals(PT)) continue;

            boolean isFQ = false;
            try {
                isFQ = railTripsAnalyzer.getFQDistance(trip, true) > 0;
            } catch (Exception ignored) {
            }

            List<Leg> legs = trip.getLegsOnly();
            Leg legBefore = null;

            for (Leg leg : legs) {
                if (PtLegUtils.isPtLeg(leg)) {
                    TransitStopFacility startFacility = PtLegUtils.accessStop(leg, transitSchedule);
                    TransitStopFacility endFacility   = PtLegUtils.egressStop(leg, transitSchedule);
                    boolean isRailLeg = PtLegUtils.isRailLeg(leg);

                    // ---------- boarding at start facility ----------
                    StopStation startStation = stopStationsMap.get(startFacility.getId());
                    startStation.addEntered();
                    if (isRailLeg) startStation.setRailStation();
                    if (isFQ) startStation.addEnteredFQ();

                    recordEntryMode(startStation, startFacility, leg, legBefore);

                    // ---------- alighting at end facility ----------
                    StopStation endStation = stopStationsMap.get(endFacility.getId());
                    endStation.addExited();
                    if (isRailLeg) endStation.setRailStation();
                    if (isFQ) endStation.addExitedFQ();

                    Leg legAfter = PtLegUtils.nextNonWalkLeg(legs, legs.indexOf(leg));
                    recordExitMode(endStation, endFacility, leg, legAfter);
                }

                // Update legBefore: track all legs except walk legs
                if (!leg.getMode().contains(SBBModes.WALK_FOR_ANALYSIS)) {
                    legBefore = leg;
                }
            }
        }
    }

    @Override
    public void write(String outputBaseDir, String runId, double sampleSize) {
        List<String> headColumns = new ArrayList<>(List.of(
                runID, "HST_Nummer", "Stop_Nummer", "Code", "Stop_Code",
                "Name", "Stop_Name", "isRailStop", "modesAtStop",
                "X", "Y", "Zone",
                "Einstiege_Gesamt", "Ausstiege_Gesamt",
                "Einstiege_FQ_Gesamt", "Ausstiege_FQ_Gesamt",
                "Umstiege_Bahn_Bahn", "Umsteige_AHP_Bahn", "Umsteige_Bahn_AHP"));
        for (String m : possibleOriginDestinationModesAtStop) {
            headColumns.add("Zielaustieg_" + m);
            headColumns.add("Quelleinstieg_" + m);
        }

        try (CSVWriter writer = new CSVWriter("\uFEFF", headColumns.toArray(new String[0]),
                outputBaseDir + oNStopStationsCount)) {
            for (Map.Entry<Id<TransitStopFacility>, StopStation> e : stopStationsMap.entrySet()) {
                writeStationRow(writer, e.getKey(), e.getValue(), runId, sampleSize);
            }
        } catch (Exception ex) {
            //noinspection CallToPrintStackTrace
            ex.printStackTrace();
        }
    }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    private void recordEntryMode(StopStation station, TransitStopFacility facility,
                                  Leg currentLeg, Leg legBefore) {
        if (legBefore == null) {
            station.incrementEnteredForMode(SBBModes.WALK_FOR_ANALYSIS);
            return;
        }
        String prevMode = PtLegUtils.normalizePtMode(legBefore.getMode());
        station.incrementEnteredForMode(prevMode);

        if (!PtLegUtils.isPtLeg(legBefore)) return;

        // Transfer type detection
        TransitStopFacility prevEndFacility = PtLegUtils.egressStop(legBefore, transitSchedule);
        if (!prevEndFacility.getAttributes().getAttribute(STOP_NO)
                .equals(facility.getAttributes().getAttribute(STOP_NO))) return;

        if (prevEndFacility.equals(facility)) {
            if (PtLegUtils.isRailLeg(currentLeg) && PtLegUtils.isRailLeg(legBefore)) {
                station.addUmstiegeBahnBahn();
            }
        } else if (PtLegUtils.isRailLeg(currentLeg) && PtLegUtils.isRailLeg(legBefore)) {
            station.addUmsteigeAHPBahn();
        }
    }

    private void recordExitMode(StopStation station, TransitStopFacility facility,
                                 Leg currentLeg, Leg legAfter) {
        if (legAfter == null) {
            station.incrementExitedForMode(SBBModes.WALK_FOR_ANALYSIS);
            return;
        }
        String nextMode = PtLegUtils.normalizePtMode(legAfter.getMode());
        station.incrementExitedForMode(nextMode);

        if (!PtLegUtils.isPtLeg(legAfter)) return;

        // Transfer type detection
        TransitStopFacility nextStartFacility = PtLegUtils.accessStop(legAfter, transitSchedule);
        if (!nextStartFacility.getAttributes().getAttribute(STOP_NO)
                .equals(facility.getAttributes().getAttribute(STOP_NO))) return;

        if (nextStartFacility.equals(facility)) {
            if (PtLegUtils.isRailLeg(currentLeg) && PtLegUtils.isRailLeg(legAfter)) {
                station.addUmstiegeBahnBahn();
            }
        } else if (PtLegUtils.isRailLeg(currentLeg) && PtLegUtils.isRailLeg(legAfter)) {
            station.addUmsteigeBahnAHP();
        }
    }

    private void writeStationRow(CSVWriter writer, Id<TransitStopFacility> stopId,
                                  StopStation station, String runId, double sampleSize) {
        TransitStopFacility facility = station.getStop();

        writer.set(runID, runId);
        writer.set("HST_Nummer",  facility.getAttributes().getAttribute(STOP_NO).toString());
        writer.set("Stop_Nummer", facility.getId().toString());
        writer.set("isRailStop",  station.getIsRailStation() ? "1" : "0");

        Object codeAttr = facility.getAttributes().getAttribute(STOP_CODE);
        writer.set("Code", codeAttr != null ? codeAttr.toString() : ModalSplitStats.NO_CODE);

        Object stopCodeAttr = facility.getAttributes().getAttribute(STOP_AREA_CODE);
        writer.set("Stop_Code", stopCodeAttr != null ? stopCodeAttr.toString() : ModalSplitStats.NO_CODE);

        TransitStopFacility scheduled = transitSchedule.getFacilities().get(
                Id.create(stopId, TransitStopFacility.class));
        String name = scheduled != null ? scheduled.getName() : null;
        writer.set("Name", name != null ? name : "");

        Object stpNameAttr = facility.getAttributes().getAttribute(STOP_AREA_NAME);
        writer.set("Stop_Name", stpNameAttr != null ? stpNameAttr.toString() : "");

        writer.set("X", Double.toString(facility.getCoord().getX()));
        writer.set("Y", Double.toString(facility.getCoord().getY()));
        writer.set("Zone", station.getZoneId());

        writer.set("Einstiege_Gesamt",    Integer.toString((int) (station.getEntered()   / sampleSize)));
        writer.set("Ausstiege_Gesamt",    Integer.toString((int) (station.getExited()    / sampleSize)));
        writer.set("Einstiege_FQ_Gesamt", Integer.toString((int) (station.getEnteredFQ() / sampleSize)));
        writer.set("Ausstiege_FQ_Gesamt", Integer.toString((int) (station.getExitedFQ()  / sampleSize)));

        for (String m : possibleOriginDestinationModesAtStop) {
            writer.set("Quelleinstieg_" + m, Integer.toString((int) (station.getEnteredForMode(m) / sampleSize)));
            writer.set("Zielaustieg_" + m,   Integer.toString((int) (station.getExitedForMode(m)  / sampleSize)));
        }

        writer.set("Umstiege_Bahn_Bahn",  Integer.toString((int) (station.getUmsteigeBahnBahn() / sampleSize)));
        writer.set("Umsteige_AHP_Bahn",   Integer.toString((int) (station.getUmsteigeAHPBahn()  / sampleSize)));
        writer.set("Umsteige_Bahn_AHP",   Integer.toString((int) (station.getUmsteigeBahnAHP()  / sampleSize)));

        Set<String> modesSet = actualModesAtStop.get(Id.create(stopId, TransitStopFacility.class));
        writer.set("modesAtStop", modesSet != null ? String.join(",", modesSet) : "");

        writer.writeRow();
    }

    private Map<Id<TransitStopFacility>, Set<String>> buildActualModesAtStop() {
        Map<Id<TransitStopFacility>, Set<String>> result = new HashMap<>();
        transitSchedule.getTransitLines().values().stream()
                .flatMap(l -> l.getRoutes().values().stream())
                .forEach(r -> r.getStops().stream()
                        .map(s -> s.getStopFacility().getId())
                        .forEach(id -> result.computeIfAbsent(id,
                                        _ -> new HashSet<>())
                                .add(r.getTransportMode())));
        return result;
    }

    private Map<Id<TransitStopFacility>, StopStation> buildStopStationMap() {
        Map<Id<TransitStopFacility>, StopStation> result = new HashMap<>();
        for (TransitStopFacility f : transitSchedule.getFacilities().values()) {
            Zone zone = zones.findZone(f.getCoord());
            result.put(f.getId(), new StopStation(f, zone));
        }
        return result;
    }
}

