/* *********************************************************************** *
 * project: org.matsim.*
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 * copyright       : (C) 2020 by the members listed in the COPYING,        *
 *                   LICENSE and WARRANTY file.                            *
 * email           : info at matsim dot org                                *
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 *   This program is free software; you can redistribute it and/or modify  *
 *   it under the terms of the GNU General Public License as published by  *
 *   the Free Software Foundation; either version 2 of the License, or     *
 *   (at your option) any later version.                                   *
 *   See also COPYING, LICENSE and WARRANTY file                           *
 *                                                                         *
 * *********************************************************************** */

package ch.sbb.matsim.analysis.tripsandlegsanalysis;

import ch.sbb.matsim.config.PostProcessingConfigGroup;
import ch.sbb.matsim.config.variables.SBBModes;
import ch.sbb.matsim.config.variables.SBBModes.PTSubModes;
import ch.sbb.matsim.config.variables.Variables;
import ch.sbb.matsim.csv.CSVWriter;
import ch.sbb.matsim.preparation.casestudies.MixExperiencedPlansFromSeveralSimulations;
import ch.sbb.matsim.zones.Zones;
import ch.sbb.matsim.zones.ZonesCollection;
import ch.sbb.matsim.zones.ZonesLoader;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.matsim.api.core.v01.*;
import org.matsim.api.core.v01.population.*;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.network.io.MatsimNetworkReader;
import org.matsim.core.population.PopulationUtils;
import org.matsim.core.population.io.PopulationReader;
import org.matsim.core.router.TripStructureUtils;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.core.scoring.ExperiencedPlansService;
import org.matsim.core.utils.collections.CollectionUtils;
import org.matsim.core.utils.misc.Time;
import org.matsim.facilities.ActivityFacility;
import org.matsim.facilities.MatsimFacilitiesReader;
import org.matsim.pt.routes.TransitPassengerRoute;
import org.matsim.pt.transitSchedule.api.TransitLine;
import org.matsim.pt.transitSchedule.api.TransitRoute;
import org.matsim.pt.transitSchedule.api.TransitSchedule;
import org.matsim.pt.transitSchedule.api.TransitScheduleReader;

import jakarta.inject.Inject;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

public class PutSurveyWriter {

    private final static Logger LOG = LogManager.getLogger(MixExperiencedPlansFromSeveralSimulations.class);

    private static final String GEM_SHAPE_ATTR = "mun_id";

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
    private static final String COL_SUBPOP = "SUBPOP";
    private static final String COL_ORIG_GEM = "ORIG_GEM";
    private static final String COL_DEST_GEM = "DEST_GEM";
    private static final String COL_ACCESS_TO_RAIL_MODE = "ACCESS_TO_RAIL_MODE";
    private static final String COL_EGRESS_FROM_RAIL_MODE = "EGRESS_FROM_RAIL_MODE";
    private static final String COL_ACCESS_TO_RAIL_DIST = "ACCESS_TO_RAIL_DIST";
    private static final String COL_EGRESS_FROM_RAIL_DIST = "EGRESS_FROM_RAIL_DIST";
    private static final String COL_PERS_ID = "PERSONID";
    private static final String COL_FROM_ACT = "FROM_ACT";
    private static final String COL_TO_ACT = "TO_ACT";
    private static final String COL_TOURID = "TOURID";
    private static final String COL_TRIPID = "TRIPID";
    private static final String COL_DIRECTION = "DIRECTION";
    private static final String COL_PURPOSE = "PURPOSE";
    private static final String[] COLUMNS = new String[]{COL_PATH_ID, COL_LEG_ID, COL_FROM_STOP, COL_TO_STOP, COL_VSYSCODE, COL_LINNAME, COL_LINROUTENAME, COL_RICHTUNGSCODE, COL_FZPROFILNAME,
            COL_TEILWEG_KENNUNG, COL_EINHSTNR, COL_EINHSTABFAHRTSTAG, COL_EINHSTABFAHRTSZEIT, COL_PFAHRT, COL_SUBPOP, COL_ORIG_GEM, COL_DEST_GEM, COL_ACCESS_TO_RAIL_MODE, COL_EGRESS_FROM_RAIL_MODE,
            COL_ACCESS_TO_RAIL_DIST, COL_EGRESS_FROM_RAIL_DIST, COL_PERS_ID, COL_TOURID, COL_TRIPID, COL_DIRECTION, COL_PURPOSE, COL_FROM_ACT, COL_TO_ACT};

    private static final String HEADER = "$VISION\n* VisumInst\n* 10.11.06\n*\n*\n* Tabelle: Versionsblock\n$VERSION:VERSNR;FILETYPE;LANGUAGE;UNIT\n4.00;Att;DEU;KM\n*\n*\n* Tabelle: ÖV-Teilwege\n";

    public static final String STOP_NO = "02_Stop_No";
    public static final String TSYS_CODE = "09_TSysCode";
    public static final String DIRECTION_CODE = "04_DirectionCode";
    public static final String TRANSITLINE = "02_TransitLine";
    public static final String LINEROUTENAME = "03_LineRouteName";
    public static final String FZPNAME = "05_Name";
    private static IdMap<Person, LinkedList<Variables.MOBiTripAttributes>> tripIds;
    private final Zones zones;
    private final double scaleFactor;
    private final TransitSchedule schedule;
    private final Scenario scenario;

    @Inject
    ExperiencedPlansService experiencedPlansService;

    @Inject
    public PutSurveyWriter(Scenario scenario, ZonesCollection zonesCollection, final PostProcessingConfigGroup ppConfig) {
        this.schedule = scenario.getTransitSchedule();
        zones = zonesCollection.getZones(ppConfig.getZonesId());
        scaleFactor = 1.0 / ppConfig.getSimulationSampleSize();
        this.scenario = scenario;

    }

    public static void writePutSurvey(String filename, List<List<PutSurveyEntry>> entries) {

        try (CSVWriter writer = new CSVWriter(HEADER, COLUMNS, filename)) {
            entries.stream().flatMap(Collection::stream).forEach(e -> {
                writer.set(COL_PATH_ID, e.path_id);
                writer.set(COL_LEG_ID, e.leg_id);
                writer.set(COL_FROM_STOP, e.from_stop);
                writer.set(COL_TO_STOP, e.to_stop);
                writer.set(COL_VSYSCODE, e.vsyscode);
                writer.set(COL_LINNAME, e.linname);
                writer.set(COL_LINROUTENAME, e.linroutename);
                writer.set(COL_RICHTUNGSCODE, e.richtungscode);
                writer.set(COL_FZPROFILNAME, e.fzprofilname);
                writer.set(COL_TEILWEG_KENNUNG, e.teilweg_kennung);
                writer.set(COL_EINHSTNR, e.einhstnr);
                writer.set(COL_EINHSTABFAHRTSTAG, e.einhstabfahrtstag);
                writer.set(COL_EINHSTABFAHRTSZEIT, e.einhstabfahrtszeit);
                writer.set(COL_PFAHRT, Double.toString(e.pfahrt));
                writer.set(COL_SUBPOP, e.subpop);
                writer.set(COL_ORIG_GEM, e.orig_gem);
                writer.set(COL_DEST_GEM, e.dest_gem);
                writer.set(COL_ACCESS_TO_RAIL_MODE, e.access_to_rail_mode);
                writer.set(COL_EGRESS_FROM_RAIL_MODE, e.egress_from_rail_mode);
                writer.set(COL_ACCESS_TO_RAIL_DIST, Integer.toString(e.access_to_rail_dist));
                writer.set(COL_EGRESS_FROM_RAIL_DIST, Integer.toString(e.egress_from_rail_dist));
                writer.set(COL_TO_ACT, e.to_act);
                writer.set(COL_FROM_ACT, e.from_act);
                writer.set(COL_PERS_ID, e.personId);
                writer.set(COL_TOURID, e.tourId);
                writer.set(COL_TRIPID, e.tripId);
                writer.set(COL_DIRECTION, e.direction);
                writer.set(COL_PURPOSE, e.purpose);
                writer.writeRow();
            });
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    public static String getDayIndex(double time) {
        int day = (int) Math.ceil(time / (24 * 60 * 60.0));
        assert day > 0;
        return Integer.toString(day);
    }

    public static String getTime(double time) {
        double sec = time % (24 * 60 * 60);
        return Time.writeTime(sec);
    }

    private Coord findCoord(Activity originActivity) {
        Id<ActivityFacility> facId = originActivity.getFacilityId();
        if (facId != null) {
            if (this.scenario.getActivityFacilities().getFacilities().get(facId) != null) {
                return this.scenario.getActivityFacilities().getFacilities().get(facId).getCoord();
            }
        }
        if (originActivity.getCoord() != null) {
            return originActivity.getCoord();
        } else if (originActivity.getLinkId() != null) {
            return scenario.getNetwork().getLinks().get(originActivity.getLinkId()).getToNode().getCoord();
        } else {
            throw new RuntimeException(" No coordinate found for activity " + originActivity);
        }
    }

    public static void main(String[] args) {
        String experiencedPlansFile = args[0];
        String zonesFile = args[1];
        String transitScheduleFile = args[2];
        String facilityFile = args[3];
        String networkFile = args[4];
        String outputFile = args[5];

        double samplesize = Double.parseDouble(args[6]);
        Zones zones = ZonesLoader.loadZones("z", zonesFile, "zone_id");
        ZonesCollection collection = new ZonesCollection();
        collection.addZones(zones);
        PostProcessingConfigGroup ppc = new PostProcessingConfigGroup();
        ppc.setSimulationSampleSize(samplesize);
        ppc.setZonesId("z");
        final Scenario scenario = ScenarioUtils.createScenario(ConfigUtils.createConfig());
        new MatsimNetworkReader(scenario.getNetwork()).readFile(networkFile);
        new MatsimFacilitiesReader(scenario).readFile(facilityFile);
        new TransitScheduleReader(scenario).readFile(transitScheduleFile);
        new PopulationReader(scenario).readFile(experiencedPlansFile);

        PutSurveyWriter putSurveyWriter = new PutSurveyWriter(scenario, collection, ppc);
        putSurveyWriter.collectAndWritePUTSurvey(outputFile, scenario.getPopulation().getPersons().values().stream().collect(Collectors.toMap(Identifiable::getId, HasPlansAndId::getSelectedPlan)));

    }

    public void collectAndWritePUTSurvey(String filename) {
        collectAndWritePUTSurvey(filename, experiencedPlansService.getExperiencedPlans());
    }

    public void collectAndWritePUTSurvey(String filename, Map<Id<Person>, Plan> experiencedPlans) {
        tripIds = Variables.MOBiTripAttributes.extractTripAttributes(scenario.getPopulation());

        AtomicInteger teilwegNr = new AtomicInteger();
        List<List<PutSurveyEntry>> entries = experiencedPlans.entrySet().parallelStream()
                .map(e -> TripStructureUtils
                        .getTrips(e.getValue()).stream()
                        .flatMap(trip -> {
                            Person person = this.scenario.getPopulation().getPersons().get(e.getKey());
                            String tourId = "";
                            String tripId = "";
                            String direction = "";
                            String purpose = "";
                            if (person != null) {
                                var visumTripIds = tripIds.get(person.getId());
                                if (tripIds != null) {
                                    Variables.MOBiTripAttributes tripAttributes = visumTripIds.poll();
                                    if (tripAttributes != null) {
                                        tourId = tripAttributes.getTourId();
                                        tripId = tripAttributes.getTripId();
                                        direction = tripAttributes.getTripDirection();
                                        purpose = tripAttributes.getTripPurpose();
                                    }
                                }
                            }

                            List<PutSurveyEntry> tripEntry = new ArrayList<>();

                            if (trip.getLegsOnly().stream().anyMatch(l -> l.getMode().equals(SBBModes.PT) )) {
                                String path_id = String.valueOf(teilwegNr.incrementAndGet());
                                Set<String> railAccessModes = new HashSet<>();
                                Set<String> railEgresssModes = new HashSet<>();
                                PutSurveyEntry firstRailLeg = null;
                                PutSurveyEntry lastRailLeg = null;

                                int leg_id = 1;
                                String from_act = trip.getOriginActivity().getType().split("_")[0];
                                String to_act = trip.getDestinationActivity().getType().split("_")[0];

                                String subpop = "null";
                                if (person != null) {
                                    subpop = String.valueOf(PopulationUtils.getSubpopulation(person));
                                }

                                var origzone = zones.findZone(findCoord(trip.getOriginActivity()));
                                var destzone = zones.findZone(findCoord(trip.getDestinationActivity()));

                                String orig_gem = origzone != null ? origzone.getAttribute(GEM_SHAPE_ATTR).toString() : Variables.DEFAULT_OUTSIDE_ZONE;
                                String dest_gem = destzone != null ? destzone.getAttribute(GEM_SHAPE_ATTR).toString() : Variables.DEFAULT_OUTSIDE_ZONE;
                                double railAccessDist = 0.;
                                double railEgressDist = 0.;
                                for (Leg leg : trip.getLegsOnly()) {
                                    boolean isRail = false;
                                    if (leg.getRoute() instanceof TransitPassengerRoute) {
                                        TransitPassengerRoute r = (TransitPassengerRoute) leg.getRoute();
                                        TransitLine line = schedule.getTransitLines().get(r.getLineId());
                                        TransitRoute transitRoute = line.getRoutes().get(r.getRouteId());
                                        String from_stop = String.valueOf(schedule.getFacilities().get(r.getAccessStopId()).getAttributes().getAttribute(STOP_NO));
                                        String to_stop = String.valueOf(schedule.getFacilities().get(r.getEgressStopId()).getAttributes().getAttribute(STOP_NO));
                                        String vsyscode = String.valueOf(transitRoute.getAttributes().getAttribute(TSYS_CODE));
                                        String linname = String.valueOf(transitRoute.getAttributes().getAttribute(TRANSITLINE));
                                        String linroutename = String.valueOf(transitRoute.getAttributes().getAttribute(LINEROUTENAME));
                                        String richtungscode = String.valueOf(transitRoute.getAttributes().getAttribute(DIRECTION_CODE));

                                        String fzprofilname = String.valueOf(transitRoute.getAttributes().getAttribute(FZPNAME));

                                        String teilweg_kennung = leg_id > 1 ? "N" : "E";
                                        String einhstabfahrtstag = getDayIndex(r.getBoardingTime().seconds());
                                        String einhstabfahrtszeit = getTime(r.getBoardingTime().seconds());

                                        PutSurveyEntry putSurveyEntry = new PutSurveyEntry(path_id, String.valueOf(leg_id), from_stop, to_stop, vsyscode, linname, linroutename, richtungscode,
                                                fzprofilname, teilweg_kennung, from_stop, einhstabfahrtstag, einhstabfahrtszeit, scaleFactor, subpop, orig_gem, dest_gem);
                                        putSurveyEntry.from_act = from_act;
                                        putSurveyEntry.to_act = to_act;
                                        putSurveyEntry.personId = e.getKey().toString();
                                        putSurveyEntry.tripId = tripId;
                                        putSurveyEntry.tourId = tourId;
                                        putSurveyEntry.purpose = purpose;
                                        putSurveyEntry.direction = direction;
                                        tripEntry.add(putSurveyEntry);
                                        if (transitRoute.getTransportMode().equals(PTSubModes.RAIL)) {
                                            isRail = true;
                                            lastRailLeg = putSurveyEntry;
                                            railEgressDist = 0.;
                                            railEgresssModes.clear();
                                            if (firstRailLeg == null) {
                                                firstRailLeg = putSurveyEntry;
                                            }
                                        }

                                        leg_id++;
                                    }

                                    if (firstRailLeg == null) {
                                        railAccessModes.add(leg.getMode());
                                        railAccessDist += leg.getRoute().getDistance();

                                    }
                                    if (lastRailLeg != null && !isRail) {
                                        railEgresssModes.add(leg.getMode());
                                        railEgressDist += leg.getRoute().getDistance();
                                    }
                                }
                                if (!railAccessModes.isEmpty() && firstRailLeg != null) {
                                    if (railAccessModes.size() > 1) {
                                        railAccessModes.remove(SBBModes.ACCESS_EGRESS_WALK);
                                    }
                                    firstRailLeg.access_to_rail_dist = (int) railAccessDist;
                                    firstRailLeg.access_to_rail_mode = CollectionUtils.setToString(railAccessModes);
                                }
                                if (!railEgresssModes.isEmpty()) {
                                    if (railEgresssModes.size() > 1) {
                                        railEgresssModes.remove(SBBModes.ACCESS_EGRESS_WALK);
                                    }
                                    lastRailLeg.egress_from_rail_dist = (int) railEgressDist;
                                    lastRailLeg.egress_from_rail_mode = CollectionUtils.setToString(railEgresssModes);
                                }
                            }
                            return tripEntry.stream();

                        }).collect(Collectors.toList()))
                .collect(Collectors.toList());
        writePutSurvey(filename, entries);
    }

    public static class PutSurveyEntry {

        private final String path_id;
        private final String leg_id;
        private final String from_stop;
        private final String to_stop;
        private final String vsyscode;
        private final String linname;
        private final String linroutename;
        private final String richtungscode;
        private final String fzprofilname;
        private final String teilweg_kennung;
        private final String einhstnr;
        private final String einhstabfahrtstag;
        private final String einhstabfahrtszeit;
        private final double pfahrt;
        private final String subpop;
        private final String orig_gem;
        private final String dest_gem;
        private String access_to_rail_mode = "";
        private String egress_from_rail_mode = "";
        private int access_to_rail_dist = 0;
        private int egress_from_rail_dist = 0;
        private String personId = "";
        private String tourId = "";
        private String tripId = "";
        private String direction = "";
        private String purpose = "";
        private String from_act = "";
        private String to_act = "";

        public PutSurveyEntry(String path_id, String leg_id, String from_stop, String to_stop, String vsyscode, String linname, String linroutename, String richtungscode, String fzprofilname,
                              String teilweg_kennung, String einhstnr, String einhstabfahrtstag, String einhstabfahrtszeit, double pfahrt, String subpop, String orig_gem, String dest_gem) {
            this.path_id = path_id;
            this.leg_id = leg_id;
            this.from_stop = from_stop;
            this.to_stop = to_stop;
            this.vsyscode = vsyscode;
            this.linname = linname;
            this.linroutename = linroutename;
            this.richtungscode = richtungscode;
            this.fzprofilname = fzprofilname;
            this.teilweg_kennung = teilweg_kennung;
            this.einhstnr = einhstnr;
            this.einhstabfahrtstag = einhstabfahrtstag;
            this.einhstabfahrtszeit = einhstabfahrtszeit;
            this.pfahrt = pfahrt;
            this.subpop = subpop;
            this.orig_gem = orig_gem;
            this.dest_gem = dest_gem;

        }
    }
}
