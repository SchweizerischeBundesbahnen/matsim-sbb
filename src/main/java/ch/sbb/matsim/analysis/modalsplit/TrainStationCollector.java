package ch.sbb.matsim.analysis.modalsplit;

import ch.sbb.matsim.analysis.tripsandlegsanalysis.RailTripsAnalyzer;
import ch.sbb.matsim.config.variables.SBBModes;
import ch.sbb.matsim.csv.CSVWriter;
import ch.sbb.matsim.zones.Zone;
import ch.sbb.matsim.zones.Zones;
import org.matsim.api.core.v01.population.Leg;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.Plan;
import org.matsim.core.router.TripStructureUtils;
import org.matsim.pt.routes.TransitPassengerRoute;
import org.matsim.pt.transitSchedule.api.TransitSchedule;
import org.matsim.pt.transitSchedule.api.TransitStopFacility;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static ch.sbb.matsim.analysis.modalsplit.MSVariables.*;
import static ch.sbb.matsim.config.variables.SBBModes.PT;

/**
 * Counts rail-station-level passenger flows (origin, destination, transfer types)
 * aggregated by {@code STOP_NO} (i.e., the physical station, not individual stop facilities).
 *
 * <p>Replaces {@code ModalSplitStats#analyzeTrainsStations} and
 * {@code ModalSplitStats#writeTrainStationAnalysis}.
 */
public class TrainStationCollector implements TripDataCollector {

    private final TransitSchedule transitSchedule;
    private final RailTripsAnalyzer railTripsAnalyzer;
    private final LongestMainModeIdentifier mainModeIdentifier = new LongestMainModeIdentifier();

    /** One entry per STOP_NO (station cluster), initialised from the schedule. */
    private final Map<String, TrainStation> trainStationMap;
    private final Set<String> railStationIds;

    public TrainStationCollector(TransitSchedule transitSchedule,
                                  Zones zones,
                                  RailTripsAnalyzer railTripsAnalyzer) {
        this.transitSchedule = transitSchedule;
        this.railTripsAnalyzer = railTripsAnalyzer;
        this.trainStationMap = buildTrainStationMap(zones);
        this.railStationIds = RailStationUtils.buildRailStationIds(transitSchedule);
    }

    // -------------------------------------------------------------------------
    // TripDataCollector
    // -------------------------------------------------------------------------

    @Override
    public void collect(Person person, Plan experiencedPlan) {
        for (TripStructureUtils.Trip trip : TripStructureUtils.getTrips(experiencedPlan)) {
            if (!mainModeIdentifier.identifyMainMode(trip.getTripElements()).equals(PT)) continue;

            List<Leg> legs = trip.getLegsOnly();
            // legBefore starts as null (represents origin walk — isPtLeg(null) == false)
            Leg legBefore = null;

            for (Leg leg : legs) {
                if (PtLegUtils.isPtLeg(leg)) {
                    TransitStopFacility startFacility = PtLegUtils.accessStop(leg, transitSchedule);
                    TransitStopFacility endFacility   = PtLegUtils.egressStop(leg, transitSchedule);
                    String startStationId = stopNo(startFacility);
                    String endStationId   = stopNo(endFacility);

                    if (PtLegUtils.isRailLeg(leg)) {
                        trainStationMap.get(startStationId).addGesamtEinsteiger();
                        trainStationMap.get(endStationId).addGesamtAussteiger();
                    }

                    Leg legAfter = PtLegUtils.nextNonWalkLeg(legs, legs.indexOf(leg));

                    if (railTripsAnalyzer.hasFQRelevantLeg(
                            List.of((TransitPassengerRoute) leg.getRoute()))) {
                        classifyBoardingFQ(leg, legBefore, startStationId);
                        classifyAlightingFQ(leg, legAfter, endStationId);
                    } else {
                        classifyNonFQ(leg, legBefore, legAfter, startStationId, endStationId);
                    }
                }

                // Update legBefore: all legs except walk legs
                if (!leg.getMode().contains(SBBModes.WALK_FOR_ANALYSIS)) {
                    legBefore = leg;
                }
            }
        }
    }

    @Override
    public void write(String outputBaseDir, String runId, double sampleSize) {
        final String[] columns = {
                runID, "HST_Nummer", "Code", "Zone",
                "Gesamt_Einsteiger", "Gesamt_Aussteiger", "Summe_EinAussteiger",
                "Ziel_Aussteiger", "Quell_Einsteiger",
                "Umsteiger_Typ_5a", "Umsteiger_Typ_5b",
                "Umsteiger_Simba_Simba", "Umsteiger_Simba_Andere",
                "Umsteiger_Andere_Simba", "Umsteiger_Andere_Andere"
        };

        try (CSVWriter writer = new CSVWriter("\uFEFF", columns,
                outputBaseDir + oNTrainStrationsCount)) {
            for (TrainStation station : trainStationMap.values()) {
                if (!railStationIds.contains(station.getHstNummer())) {
                    continue;
                }

                int gesamtEinsteiger = (int) (station.getGesamtEinsteiger() / sampleSize);
                int gesamtAussteiger = (int) (station.getGesamtAussteiger() / sampleSize);
                int summeEinAussteiger = (int) (station.getSummeEinAussteiger() / sampleSize);

                writer.set(runID,              runId);
                writer.set("HST_Nummer",       station.getHstNummer());
                writer.set("Code",             station.getStopCode());
                writer.set("Zone",             station.getZoneId());
                writer.set("Gesamt_Einsteiger", Integer.toString(gesamtEinsteiger));
                writer.set("Gesamt_Aussteiger", Integer.toString(gesamtAussteiger));
                writer.set("Summe_EinAussteiger", Integer.toString(summeEinAussteiger));
                writer.set("Ziel_Aussteiger",  Integer.toString((int) (station.getZielAussteiger()       / sampleSize)));
                writer.set("Quell_Einsteiger", Integer.toString((int) (station.getQuellEinsteiger()      / sampleSize)));
                writer.set("Umsteiger_Typ_5a", Integer.toString((int) (station.getUmsteigerTyp5a()       / sampleSize)));
                writer.set("Umsteiger_Typ_5b", Integer.toString((int) (station.getUmsteigerTyp5b()       / sampleSize)));
                // divided by 2 because each transfer is seen from both legs
                writer.set("Umsteiger_Simba_Simba",   Integer.toString((int) (station.getUmsteigerSimbaSimba()   / sampleSize / 2)));
                writer.set("Umsteiger_Simba_Andere",  Integer.toString((int) (station.getUmsteigerSimbaAndere()  / sampleSize / 2)));
                writer.set("Umsteiger_Andere_Simba",  Integer.toString((int) (station.getUmsteigerAndereSimba()  / sampleSize / 2)));
                writer.set("Umsteiger_Andere_Andere", Integer.toString((int) (station.getUmsteigerAndereAndere() / sampleSize / 2)));
                writer.writeRow();
            }
        } catch (Exception ex) {
            //noinspection CallToPrintStackTrace
            ex.printStackTrace();
        }
    }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    private void classifyBoardingFQ(Leg leg, Leg legBefore, String startStationId) {
        if (PtLegUtils.isPtLeg(legBefore) && PtLegUtils.isRailLeg(leg)) {
            // Transfer onto a FQ-relevant rail leg
            String prevStationId = stopNo(PtLegUtils.egressStop(legBefore, transitSchedule));
            if (!prevStationId.equals(startStationId)) {
                trainStationMap.get(startStationId).addUmsteigerTyp5b();
            } else if (railTripsAnalyzer.hasFQRelevantLeg(
                    List.of((TransitPassengerRoute) legBefore.getRoute()))) {
                trainStationMap.get(startStationId).addUmsteigerSimbaSimba();
            } else {
                trainStationMap.get(startStationId).addUmsteigerAndereSimba();
            }
        } else {
            trainStationMap.get(startStationId).addQuellEinsteiger();
        }
    }

    private void classifyAlightingFQ(Leg leg, Leg legAfter, String endStationId) {
        if (PtLegUtils.isPtLeg(legAfter)
                && PtLegUtils.isRailLeg(legAfter) && PtLegUtils.isRailLeg(leg)) {
            String nextStationId = stopNo(PtLegUtils.accessStop(legAfter, transitSchedule));
            if (!nextStationId.equals(endStationId)) {
                trainStationMap.get(endStationId).addUmsteigerTyp5a();
            } else if (railTripsAnalyzer.hasFQRelevantLeg(
                    List.of((TransitPassengerRoute) legAfter.getRoute()))) {
                trainStationMap.get(endStationId).addUmsteigerSimbaSimba();
            } else {
                trainStationMap.get(endStationId).addUmsteigerSimbaAndere();
            }
        } else {
            trainStationMap.get(endStationId).addZielAussteiger();
        }
    }

    private void classifyNonFQ(Leg leg, Leg legBefore, Leg legAfter,
                                String startStationId, String endStationId) {
        // legBefore rail → current rail transfer (not FQ) → Andere_Andere
        if (PtLegUtils.isRailLeg(legBefore) && PtLegUtils.isRailLeg(leg)) {
            trainStationMap.get(startStationId).addUmsteigerAndereAndere();
        }
        // current rail → legAfter rail transfer (not FQ) → Andere_Andere
        // Fix: original code had legAfter.equals(PTSubModes.RAIL) which compared Leg to String (always false)
        if (PtLegUtils.isRailLeg(legAfter) && PtLegUtils.isRailLeg(leg)) {
            trainStationMap.get(endStationId).addUmsteigerAndereAndere();
        }
    }

    private String stopNo(TransitStopFacility facility) {
        return facility.getAttributes().getAttribute(STOP_NO).toString();
    }

    private Map<String, TrainStation> buildTrainStationMap(Zones zones) {
        Map<String, TrainStation> result = new HashMap<>();
        for (TransitStopFacility f : transitSchedule.getFacilities().values()) {
            String id = stopNo(f);
            Zone zone = zones.findZone(f.getCoord());
            result.computeIfAbsent(id, _ -> new TrainStation(f, zone)).addStop(f);
        }
        return result;
    }

}

