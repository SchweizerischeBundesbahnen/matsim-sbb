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
import ch.sbb.matsim.config.variables.Variables;
import ch.sbb.matsim.intermodal.IntermodalAwareRouterModeIdentifier;
import ch.sbb.matsim.zones.Zones;
import ch.sbb.matsim.zones.ZonesLoader;
import org.apache.commons.lang3.mutable.MutableInt;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.matsim.api.core.v01.Coord;
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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * A class to merge the routed plans of one finished simulation run with unrouted plans of another run. All agents performing at least one activity in either of the whitelisted zones will be drawn
 * from the unrouted plans, all other from the routed plans. Agents in the non-regular subpopulation (exogeneous plans) are not handled, but their routed plans are copied. Running via IDE and vclient
 * should be possible, as the population is read and written on the fly.
 */
public class MergeRoutedAndUnroutedPlans {

    private final static Logger LOG = LogManager.getLogger(MergeRoutedAndUnroutedPlans.class);
    private final String unroutedPlans;
    private final String routedPlans;
    private final String unroutedPlansFacilities;
    private final String routedPlansFacilities;
    private final String whiteListZonesFiles;
    private final String zonesFile;
    private final String inputConfig;
    private final String outputConfig;
    private final String outputPlansFile;
    private final TransitSchedule scheduleVar;

    private final String varPlans;

    private final String varFacilities;

    private final String varOutputPlansFile;

    private final Set<Id<Person>> varPersons = new HashSet<>();

    private Set<Id<ActivityFacility>> facilityWhiteList;

    private final Set<Id<Person>> allPersons = new HashSet<>();

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
     * @param varPlans MATSim Plans from mobi-plans run with modified skims (var)
     * @param varFacilities MATSim Facilities from mobi-plans run with modified skims
     * @param varOutputPlansFile The output file for var plans
     */
    public MergeRoutedAndUnroutedPlans(String unroutedPlans, String routedPlans, String unroutedPlansFacilities, String routedPlansFacilities, String whiteListZonesFiles, String zonesFile,
            String inputConfig, String outputConfig, String outputPlansFile, String transitSchedulePath, String varPlans, String varFacilities, String varOutputPlansFile) {
        this.unroutedPlans = unroutedPlans;
        this.routedPlans = routedPlans;
        this.unroutedPlansFacilities = unroutedPlansFacilities;
        this.routedPlansFacilities = routedPlansFacilities;
        this.whiteListZonesFiles = whiteListZonesFiles;
        this.zonesFile = zonesFile;

        this.inputConfig = inputConfig;
        this.outputConfig = outputConfig;
        this.outputPlansFile = outputPlansFile;
        this.varPlans = varPlans;
        this.varFacilities = varFacilities;
        this.varOutputPlansFile = varOutputPlansFile;
        Scenario scenario = ScenarioUtils.createScenario(ConfigUtils.createConfig());
        new TransitScheduleReader(scenario).readFile(transitSchedulePath);
        this.scheduleVar = scenario.getTransitSchedule();
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
        String varPlans = "-";
        String varFacilites = "-";
        String varOutputPlansFile = "-";
        if (args.length > 10) {
            varPlans = args[10];
            varFacilites = args[11];
            varOutputPlansFile = args[12];
        }
        new MergeRoutedAndUnroutedPlans(unroutedPlans, routedPlans, unroutedPlansFacilities, routedPlansFacilities, whiteListZonesFiles, zonesFile,
                inputConfig, outputConfig, outputPlansFile,
                schedule, varPlans, varFacilites, varOutputPlansFile).run();

    }

    public static Set<String> readWhiteListZones(String whiteListZonesFiles) {
        try {
            Stream<String> lines = Files.lines(Path.of(whiteListZonesFiles));
            Set<String> whitelistZones = lines.collect(Collectors.toSet());
            lines.close();
            return whitelistZones;
        } catch (IOException e) {
            throw new RuntimeException("Whitelist Zone file could not be read: " + whiteListZonesFiles);
        }
    }

    private void mergePlans() {
        LOG.info("varPersons before mergePlans " + varPersons.size());
        LOG.info("allPersons before mergePlans " + allPersons.size());
        StreamingPopulationWriter spw = new StreamingPopulationWriter();
        spw.startStreaming(outputPlansFile);
        StreamingPopulationReader unroutedReader = new StreamingPopulationReader(ScenarioUtils.createScenario(ConfigUtils.createConfig()));
        unroutedReader.addAlgorithm(person -> {
            if (PopulationUtils.getSubpopulation(person).equals(Variables.REGULAR)) {
                boolean include = TripStructureUtils.getActivities(person.getSelectedPlan(), StageActivityHandling.ExcludeStageActivities)
                        .stream()
                        .anyMatch(activity -> this.facilityWhiteList.contains(activity.getFacilityId()));
                String source = "var";
                if (include) {
                    source = "ref";
                }
                include = include | varPersons.contains(person.getId());
                if (include) {
                    spw.run(person);
                    allPersons.add(person.getId());
                }

            }
        });
        unroutedReader.readFile(unroutedPlans);

        appendRoutedPlans(spw);

        spw.closeStreaming();
        LOG.info("varPersons after mergePlans " + varPersons.size());
        LOG.info("allPersons after mergePlans " + allPersons.size());
    }

    private void appendRoutedPlans(StreamingPopulationWriter spw) {
        TripsToLegsAlgorithm tripsToLegsAlgorithm = new TripsToLegsAlgorithm(new IntermodalAwareRouterModeIdentifier(config));
        MutableInt ttl = new MutableInt();
        StreamingPopulationReader routedReader = new StreamingPopulationReader(ScenarioUtils.createScenario(ConfigUtils.createConfig()));
        routedReader.addAlgorithm(person -> {
            if (!this.allPersons.contains(person.getId())) {
                boolean include;
                if (PopulationUtils.getSubpopulation(person).equals(Variables.REGULAR)) {
                    include = TripStructureUtils.getActivities(person.getSelectedPlan(), StageActivityHandling.ExcludeStageActivities)
                            .stream()
                            .noneMatch(activity
                                    -> this.facilityWhiteList.contains(activity.getFacilityId()));
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
                        var transitLine = scheduleVar.getTransitLines().get(r.getLineId());
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

                    this.allPersons.add(person.getId());
                    spw.run(person);
                }
            }

        });
        if (!"-".equals(routedPlans)) {
            routedReader.readFile(routedPlans);
        }

    }

    public static Set<Id<ActivityFacility>> prepareRelevantFacilities(Set<String> whitelistZones, Zones zones, List<String> facilityFiles) {

        Set<Id<ActivityFacility>> facilityWhiteList = new HashSet<>();
        for (String f : facilityFiles) {
            if (!"-".equals(f)) {
                LOG.info("Handling zone matching for facilities file " + f);
                Scenario scenario = ScenarioUtils.createScenario(ConfigUtils.createConfig());
                new MatsimFacilitiesReader(scenario).readFile(f);
                Set<Id<ActivityFacility>> whitelist = scenario.getActivityFacilities().getFacilities().values().parallelStream().filter(
                        facility -> isCoordinWhiteListZone(whitelistZones, zones, facility.getCoord())
                ).map(activityFacility -> activityFacility.getId()).collect(Collectors.toSet());
                facilityWhiteList.addAll(whitelist);

                LOG.info("done.");
            }
        }
        LOG.info("Whitelist contains " + facilityWhiteList.size() + " facilities in boundary.");
        return facilityWhiteList;

    }

    public static boolean isCoordinWhiteListZone(Set<String> whitelistZones, Zones zones, Coord coord) {
        var zone = zones.findZone(coord);
        if (zone != null) {
            return whitelistZones.contains(zone.getId().toString());
        }
        return false;
    }

    private void run() {
        adjustConfig();
        var zones = ZonesLoader.loadZones("zones", zonesFile, Variables.ZONE_ID);
        List<String> facilityFiles = List.of(routedPlansFacilities, unroutedPlansFacilities);
        this.facilityWhiteList = prepareRelevantFacilities(readWhiteListZones(whiteListZonesFiles), zones, facilityFiles);
        prepareVarPersons();
        mergePlans();
        if (!varPlans.equals("-")) {
            mergeVarPlans();
        }
    }

    private void prepareVarPersons() {
        /* if there are plans for a variant, take all persons that have activities in the relevant zones either in ref or in var */

        if (varPlans != "-") {
            StreamingPopulationReader varPlansReader = new StreamingPopulationReader(ScenarioUtils.createScenario(ConfigUtils.createConfig()));
            varPlansReader.addAlgorithm(person -> {
                if (PopulationUtils.getSubpopulation(person).equals(Variables.REGULAR)) {
                    boolean include = TripStructureUtils.getActivities(person.getSelectedPlan(), StageActivityHandling.ExcludeStageActivities)
                            .stream()
                            .anyMatch(activity -> this.facilityWhiteList.contains(activity.getFacilityId()));
                    if (include) {
                        varPersons.add(person.getId());
                    }

                }
            });
            varPlansReader.readFile(varPlans);
        }
        LOG.info("varPersons after prepareVarPersons " + varPersons.size());

    }

    private void mergeVarPlans()  {
        StreamingPopulationWriter spw = new StreamingPopulationWriter();
        spw.startStreaming(this.varOutputPlansFile);
        StreamingPopulationReader vpr = new StreamingPopulationReader(ScenarioUtils.createScenario(ConfigUtils.createConfig()));
        vpr.addAlgorithm(person -> {
            if (PopulationUtils.getSubpopulation(person).equals(Variables.REGULAR)) {
                boolean include = (allPersons.contains(person.getId()) | varPersons.contains(person.getId()));
                if (include) {
                    spw.run(person);
                }
            }
        });
        vpr.readFile(varPlans);

        appendRoutedPlans(spw);

        spw.closeStreaming();
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
