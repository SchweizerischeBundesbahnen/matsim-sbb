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
import ch.sbb.matsim.csv.CSVWriter;
import ch.sbb.matsim.zones.Zone;
import ch.sbb.matsim.zones.Zones;
import ch.sbb.matsim.zones.ZonesCollection;
import java.io.IOException;

import jakarta.inject.Inject;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.IdMap;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.population.Activity;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.Plan;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.router.TripStructureUtils;
import org.matsim.core.router.TripStructureUtils.StageActivityHandling;
import org.matsim.core.scoring.ExperiencedPlansService;

public class ActivityWriter {

    public static final String ACTIVITY_ID = "activity_id";
    public static final String PERSON_ID = "person_id";
    public static final String FACILITY_ID = "facility_id";
    public static final String START_TIME = "start_time";
    public static final String END_TIME = "end_time";
    public static final String X = "x";
    public static final String Y = "y";
    public static final String ZONE = "zone";
    private final Network network;
    private final int simEndTime;
    private final Zones zones;

    @Inject
    private ExperiencedPlansService experiencedPlansService;

    @Inject
    public ActivityWriter(Scenario scenario, ZonesCollection zonesCollection) {
        this.network = scenario.getNetwork();
        this.simEndTime = scenario.getConfig().hermes().getEndTime();
        this.zones = zonesCollection.getZones(ConfigUtils.addOrGetModule(scenario.getConfig(), PostProcessingConfigGroup.class).getZonesId());

    }

    //activity_id;person_id;facility_id;type;start_time;end_time;x;y;zone
    public void writeActivities(String filename) {
        writeActivities(filename, experiencedPlansService.getExperiencedPlans());
    }

    public void writeActivities(String filename, IdMap<Person, Plan> experiencedPlans) {
        try (CSVWriter csvWriter = new CSVWriter(null, new String[]{ACTIVITY_ID, PERSON_ID, FACILITY_ID, START_TIME, END_TIME, X, Y, ZONE}, filename)) {
            int i = 0;
            for (var e : experiencedPlans.entrySet()) {
                for (Activity a : TripStructureUtils.getActivities(e.getValue(), StageActivityHandling.ExcludeStageActivities)) {
                    csvWriter.set(ACTIVITY_ID, Integer.toString(i++));
                    csvWriter.set(PERSON_ID, e.getKey().toString());
                    csvWriter.set(FACILITY_ID, String.valueOf(a.getFacilityId()));
                    csvWriter.set(START_TIME, Integer.toString(a.getStartTime().isDefined() ? (int) a.getStartTime().seconds() : 0));
                    csvWriter.set(END_TIME, Integer.toString(a.getEndTime().isDefined() ? (int) a.getEndTime().seconds() : simEndTime));
                    Coord c = a.getCoord();
                    if (c == null) {
                        c = network.getLinks().get(a.getLinkId()).getToNode().getCoord();
                    }
                    csvWriter.set(X, String.valueOf(c.getX()));
                    csvWriter.set(Y, String.valueOf(c.getY()));
                    Zone z = zones.findZone(c);
                    String zoneId = z == null ? "" : z.getId().toString();
                    csvWriter.set(ZONE, zoneId);
                    csvWriter.writeRow();
                }
            }

        } catch (IOException e) {
            e.printStackTrace();
        }

    }

}
