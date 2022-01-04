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

package ch.sbb.matsim.preparation.casestudies;

import ch.sbb.matsim.RunSBB;
import ch.sbb.matsim.analysis.zonebased.IntermodalAwareRouterModeIdentifier;
import ch.sbb.matsim.config.variables.Variables;
import ch.sbb.matsim.zones.ZonesLoader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.commons.lang3.mutable.MutableInt;
import org.apache.log4j.Logger;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.population.Person;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.config.ConfigWriter;
import org.matsim.core.config.groups.StrategyConfigGroup.StrategySettings;
import org.matsim.core.population.PersonUtils;
import org.matsim.core.population.PopulationUtils;
import org.matsim.core.population.algorithms.TripsToLegsAlgorithm;
import org.matsim.core.population.io.StreamingPopulationReader;
import org.matsim.core.population.io.StreamingPopulationWriter;
import org.matsim.core.replanning.strategies.DefaultPlanStrategiesModule.DefaultSelector;
import org.matsim.core.router.TripStructureUtils;
import org.matsim.core.router.TripStructureUtils.StageActivityHandling;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.facilities.ActivityFacility;
import org.matsim.facilities.MatsimFacilitiesReader;
import org.matsim.pt.routes.DefaultTransitPassengerRoute;
import org.matsim.pt.transitSchedule.api.TransitSchedule;
import org.matsim.pt.transitSchedule.api.TransitScheduleReader;

/**
 * A class to merge the routed plans of one finished simulation run with unrouted plans of another run. All agents performing at least one activity in either of the whitelisted zones will be drawn
 * from the unrouted plans, all other from the routed plans. Agents in the non-regular subpopulation (exogeneous plans) are not handled, but their routed plans are copied. Running via IDE and vclient
 * should be possible, as the population is read and written on the fly.
 */
public class MergeRoutedAndUnroutedPlans {

    private final static Logger LOG = Logger.getLogger(MergeRoutedAndUnroutedPlans.class);
    private final String unroutedPlans;
    private final String routedPlans;
    private final String unroutedPlansFacilities;
    private final String routedPlansFacilities;
    private final String whiteListZonesFiles;
    private final String zonesFile;
    private final String inputConfig;
    private final String outputConfig;
    private final String outputPlansFile;
    private final TransitSchedule schedule;

    private Set<Id<ActivityFacility>> facilityWhiteList = new HashSet<>();
    private Config config;

    /**
     * T
     *
     * @param unroutedPlans MATSim plans which will have full replanning
     * @param routedPlans MATSim plans where last selected plan is performed (can be left out by "-")
     * @param unroutedPlansFacilities Facilities belongig to the fully replanned scenario
     * @param routedPlansFacilities Facilities belongig to the  scenario without replanning (use "-" if not applicable)
     * @param whiteListZonesFiles A text file containing zone IDs (line by line) with zones. Any agent performing at least one activity here will be fully replanned.
     * @param zonesFile A mobi zones shape file
     * @param inputConfig The input config file
     * @param outputConfig The output config file
     * @param outputPlansFile The output plans file
     * @param transitSchedulePath TransitScheduleFile to check if routes exist.
     */
    public MergeRoutedAndUnroutedPlans(String unroutedPlans, String routedPlans, String unroutedPlansFacilities, String routedPlansFacilities, String whiteListZonesFiles, String zonesFile,
            String inputConfig, String outputConfig, String outputPlansFile, String transitSchedulePath) {
        this.unroutedPlans = unroutedPlans;
        this.routedPlans = routedPlans;
        this.unroutedPlansFacilities = unroutedPlansFacilities;
        this.routedPlansFacilities = routedPlansFacilities;
        this.whiteListZonesFiles = whiteListZonesFiles;
        this.zonesFile = zonesFile;

        this.inputConfig = inputConfig;
        this.outputConfig = outputConfig;
        this.outputPlansFile = outputPlansFile;
        Scenario scenario = ScenarioUtils.createScenario(ConfigUtils.createConfig());
        new TransitScheduleReader(scenario).readFile(transitSchedulePath);
        this.schedule = scenario.getTransitSchedule();
    }

    /**
     * @param args
     */
    public static void main(String[] args) {
        String unroutedPlans = args[0];
        String routedPlans = args[1];
        String unroutedPlansFacilities = args[2];
        String routedPlansFacilities = args[3];
        String whiteListZonesFiles = args[4];
        String zonesFile = args[5];
        String inputConfig = args[6];
        String outputConfig = args[7];
        String outputPlansFile = args[8];
        String schedule = args[9];
        new MergeRoutedAndUnroutedPlans(unroutedPlans, routedPlans, unroutedPlansFacilities, routedPlansFacilities, whiteListZonesFiles, zonesFile, inputConfig, outputConfig, outputPlansFile,
                schedule).run();

    }

    private void run() {
        adjustConfig();
        prepareRelevantFacilities();
        mergePlans();
    }

    private void mergePlans() {
        TripsToLegsAlgorithm tripsToLegsAlgorithm = new TripsToLegsAlgorithm(new IntermodalAwareRouterModeIdentifier(config));
        StreamingPopulationWriter spw = new StreamingPopulationWriter();
        spw.startStreaming(outputPlansFile);
        Set<Id<Person>> allPersons = new HashSet<>();
        StreamingPopulationReader unroutedReader = new StreamingPopulationReader(ScenarioUtils.createScenario(ConfigUtils.createConfig()));
        unroutedReader.addAlgorithm(person -> {
            if (PopulationUtils.getSubpopulation(person).equals(Variables.REGULAR)) {
                boolean include = TripStructureUtils.getActivities(person.getSelectedPlan(), StageActivityHandling.ExcludeStageActivities)
                        .stream()
                        .anyMatch(activity -> this.facilityWhiteList.contains(activity.getFacilityId()));
                if (include) {
                    spw.run(person);
                    allPersons.add(person.getId());
                }

            }
        });
        unroutedReader.readFile(unroutedPlans);
        MutableInt ttl = new MutableInt();
        StreamingPopulationReader routedReader = new StreamingPopulationReader(ScenarioUtils.createScenario(ConfigUtils.createConfig()));
        routedReader.addAlgorithm(person -> {
            if (!allPersons.contains(person.getId())) {
                boolean include;
                if (PopulationUtils.getSubpopulation(person).equals(Variables.REGULAR)) {
                    include = !(TripStructureUtils.getActivities(person.getSelectedPlan(), StageActivityHandling.ExcludeStageActivities)
                            .stream()
                            .anyMatch(activity
                                    -> this.facilityWhiteList.contains(activity.getFacilityId())));
                    if (include) {
                        PopulationUtils.putSubpopulation(person, Variables.NO_REPLANNING);

                    }
                } else {
                    include = true;
                }
                if (include) {
                    PersonUtils.removeUnselectedPlans(person);
                    var ptroutes = TripStructureUtils.getLegs(person.getSelectedPlan()).stream().filter(leg -> leg.getRoute().getRouteType().equals(DefaultTransitPassengerRoute.ROUTE_TYPE))
                            .map(leg -> (DefaultTransitPassengerRoute) leg.getRoute()).collect(Collectors.toSet());
                    for (DefaultTransitPassengerRoute r : ptroutes) {
                        var transitLine = schedule.getTransitLines().get(r.getLineId());
                        boolean hasRoute = false;
                        boolean hasLine = false;
                        if (transitLine != null) {
                            hasRoute = transitLine.getRoutes().containsKey(r.getRouteId());
                            hasLine = true;
                        }
                        if (!hasLine || !hasRoute) {
                            if (PopulationUtils.getSubpopulation(person).equals(Variables.NO_REPLANNING)) {
                                PopulationUtils.putSubpopulation(person, Variables.REGULAR);
                            }
                            tripsToLegsAlgorithm.run(person.getSelectedPlan());
                            ttl.increment();

                        }
                    }

                    allPersons.add(person.getId());
                    spw.run(person);
                }
            }

        });
        if (!"-".equals(routedPlans)) {
            routedReader.readFile(routedPlans);
        }

        spw.closeStreaming();
        LOG.info(ttl.intValue());
    }

    private void prepareRelevantFacilities() {

        try {
            Set<String> whitelistZones = Files.lines(Path.of(whiteListZonesFiles)).collect(Collectors.toSet());
            var zones = ZonesLoader.loadZones("zones", zonesFile, Variables.ZONE_ID);
            List<String> facilityFiles = List.of(routedPlansFacilities, unroutedPlansFacilities);
            for (String f : facilityFiles) {
                if (!"-".equals(f)) {
                    LOG.info("Handling zone matching for facilities file " + f);
                    Scenario scenario = ScenarioUtils.createScenario(ConfigUtils.createConfig());
                    new MatsimFacilitiesReader(scenario).readFile(f);
                    for (ActivityFacility facility : scenario.getActivityFacilities().getFacilities().values()) {
                        var zone = zones.findZone(facility.getCoord());
                        if (zone != null) {
                            if (whitelistZones.contains(zone.getId().toString())) {
                                this.facilityWhiteList.add(facility.getId());
                            }
                        }
                    }
                    LOG.info("done.");
                }
            }
            LOG.info("Whitelist contains " + facilityWhiteList.size() + " facilities in boundary.");

        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    private void adjustConfig() {

        this.config = ConfigUtils.loadConfig(this.inputConfig, RunSBB.getSbbDefaultConfigGroups());
        config.strategy().getStrategySettings().stream().filter(s -> Variables.EXOGENEOUS_DEMAND.contains(s.getSubpopulation())).forEach(s -> s.setWeight(0.0));
        List<String> subpops = new ArrayList<>();
        subpops.add(Variables.NO_REPLANNING);
        subpops.addAll(Variables.EXOGENEOUS_DEMAND);
        for (var s : subpops) {
            StrategySettings norep = new StrategySettings();
            norep.setWeight(1.0);
            norep.setSubpopulation(s);
            norep.setStrategyName(DefaultSelector.KeepLastSelected);
            config.strategy().addStrategySettings(norep);
        }
        new ConfigWriter(config).write(outputConfig);
        LOG.info("wrote new config to " + outputConfig);
    }

}
