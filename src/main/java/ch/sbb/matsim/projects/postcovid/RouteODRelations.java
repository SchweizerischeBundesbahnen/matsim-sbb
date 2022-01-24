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

package ch.sbb.matsim.projects.postcovid;

import static ch.sbb.matsim.analysis.tripsandlegsanalysis.PutSurveyWriter.DIRECTION_CODE;
import static ch.sbb.matsim.analysis.tripsandlegsanalysis.PutSurveyWriter.FZPNAME;
import static ch.sbb.matsim.analysis.tripsandlegsanalysis.PutSurveyWriter.LINEROUTENAME;
import static ch.sbb.matsim.analysis.tripsandlegsanalysis.PutSurveyWriter.STOP_NO;
import static ch.sbb.matsim.analysis.tripsandlegsanalysis.PutSurveyWriter.TRANSITLINE;
import static ch.sbb.matsim.analysis.tripsandlegsanalysis.PutSurveyWriter.TSYS_CODE;
import static ch.sbb.matsim.analysis.tripsandlegsanalysis.PutSurveyWriter.getDayIndex;
import static ch.sbb.matsim.analysis.tripsandlegsanalysis.PutSurveyWriter.getTime;

import ch.sbb.matsim.analysis.tripsandlegsanalysis.PutSurveyWriter;
import ch.sbb.matsim.analysis.tripsandlegsanalysis.PutSurveyWriter.PutSurveyEntry;
import ch.sbb.matsim.config.variables.SBBModes;
import ch.sbb.matsim.routing.pt.raptor.DefaultRaptorInVehicleCostCalculator;
import ch.sbb.matsim.routing.pt.raptor.DefaultRaptorParametersForPerson;
import ch.sbb.matsim.routing.pt.raptor.DefaultRaptorStopFinder;
import ch.sbb.matsim.routing.pt.raptor.DefaultRaptorTransferCostCalculator;
import ch.sbb.matsim.routing.pt.raptor.RaptorStaticConfig;
import ch.sbb.matsim.routing.pt.raptor.RaptorUtils;
import ch.sbb.matsim.routing.pt.raptor.SwissRailRaptor;
import ch.sbb.matsim.routing.pt.raptor.SwissRailRaptorData;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.population.Leg;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.network.io.MatsimNetworkReader;
import org.matsim.core.router.DefaultRoutingRequest;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.facilities.ActivityFacility;
import org.matsim.pt.routes.TransitPassengerRoute;
import org.matsim.pt.transitSchedule.api.TransitLine;
import org.matsim.pt.transitSchedule.api.TransitRoute;
import org.matsim.pt.transitSchedule.api.TransitScheduleReader;
import org.matsim.pt.transitSchedule.api.TransitStopFacility;

public class RouteODRelations {

    public static void main(String[] args) throws IOException {
        String transitScheduleFile = "\\\\wsbbrz0283\\mobi\\50_Ergebnisse\\MOBi_3.1.LFP\\2030\\pt\\bav_ak25\\output\\transitSchedule.xml.gz";
        String networkFile = "\\\\wsbbrz0283\\mobi\\50_Ergebnisse\\MOBi_3.1.LFP\\2030\\pt\\bav_ak25\\output\\transitNetwork.xml.gz";
        String demandFile = "\\\\wsbbrz0283\\mobi\\40_Projekte\\20210408_PostCovid_Szen\\visum\\final_results\\august_results\\results_scenario_12pctHO-0pctPtVTTS-14pctF-v2.csv";
        String surveyFile = "\\\\wsbbrz0283\\mobi\\40_Projekte\\20210408_PostCovid_Szen\\visum\\final_results\\august_results\\results_scenario_12pctHO-0pctPtVTTS-14pctF-v2-put-survey.csv";
        Set<DemandRelation> relations = Files.lines(Path.of(demandFile)).map(s -> {
            var rel = s.split(";");
            return new DemandRelation(Id.create(rel[0], TransitStopFacility.class), Id.create(rel[1], TransitStopFacility.class), Double.parseDouble(rel[2]));
        }).collect(
                Collectors.toSet());
        Scenario scenario = ScenarioUtils.createScenario(ConfigUtils.createConfig());
        new TransitScheduleReader(scenario).readFile(transitScheduleFile);
        new MatsimNetworkReader(scenario.getNetwork()).readFile(networkFile);

        RaptorStaticConfig raptorConfig = RaptorUtils.createStaticConfig(scenario.getConfig());
        SwissRailRaptorData raptorData = SwissRailRaptorData.create(scenario.getTransitSchedule(), scenario.getTransitVehicles(), raptorConfig, scenario.getNetwork(), null);
        var raptor = new SwissRailRaptor(raptorData, new DefaultRaptorParametersForPerson(scenario.getConfig()), null, new DefaultRaptorStopFinder(scenario.getConfig(), null, null),
                new DefaultRaptorInVehicleCostCalculator(), new DefaultRaptorTransferCostCalculator());
        var schedule = scenario.getTransitSchedule();
        AtomicInteger i = new AtomicInteger(1);
        List<List<PutSurveyEntry>> entries = new ArrayList<>();
        for (var relation : relations) {

            var startFacility = scenario.getActivityFacilities().getFactory()
                    .createActivityFacility(Id.create("l", ActivityFacility.class), scenario.getTransitSchedule().getFacilities().get(relation.fromId).getCoord());
            var endFacility = scenario.getActivityFacilities().getFactory()
                    .createActivityFacility(Id.create("e", ActivityFacility.class), scenario.getTransitSchedule().getFacilities().get(relation.toId).getCoord());
            if (startFacility.getCoord() == null) {
                System.out.println(relation.fromId + " stop not found");
            }
            if (endFacility.getCoord() == null) {
                System.out.println(relation.toId + " stop not found");
            }
            var route = raptor.calcRoute(DefaultRoutingRequest.withoutAttributes(startFacility, endFacility, 8 * 3600, null));
            String path_id = Integer.toString(i.incrementAndGet());
            AtomicInteger leg_id = new AtomicInteger(1);
            List<PutSurveyEntry> putSurveyEntries = new ArrayList<>();
            if (route == null) {
                System.out.println("no route found from " + relation.fromId + " to " + relation.toId);
                continue;
            }
            List<Leg> ptlegs = route.stream().filter(l -> l instanceof Leg).map(l -> (Leg) l).filter(leg -> leg.getMode().equals(SBBModes.PT)).collect(Collectors.toList());
            for (Leg leg : ptlegs) {
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

                String teilweg_kennung = leg_id.getAndIncrement() > 1 ? "N" : "E";
                String einhstnr = from_stop;
                String einhstabfahrtstag = getDayIndex(r.getBoardingTime().seconds());
                String einhstabfahrtszeit = getTime(r.getBoardingTime().seconds());
                putSurveyEntries.add(new PutSurveyEntry(path_id, String.valueOf(leg_id), from_stop, to_stop, vsyscode, linname, linroutename, richtungscode,
                        fzprofilname, teilweg_kennung, einhstnr, einhstabfahrtstag, einhstabfahrtszeit, relation.demand, "regular", "", ""));
            }
            if (!putSurveyEntries.isEmpty()) {
                entries.add(putSurveyEntries);
            }
        }

        PutSurveyWriter.writePutSurvey(surveyFile, entries);
    }

    static class DemandRelation {

        Id<TransitStopFacility> fromId;
        Id<TransitStopFacility> toId;
        double demand;

        public DemandRelation(Id<TransitStopFacility> fromId, Id<TransitStopFacility> toId, double demand) {
            this.fromId = fromId;
            this.toId = toId;
            this.demand = demand;
        }
    }
}
