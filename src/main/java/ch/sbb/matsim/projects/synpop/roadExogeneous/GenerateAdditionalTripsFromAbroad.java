package ch.sbb.matsim.projects.synpop.roadExogeneous;

import ch.sbb.matsim.config.variables.SBBModes;
import ch.sbb.matsim.config.variables.Variables;
import ch.sbb.matsim.csv.CSVWriter;
import ch.sbb.matsim.projects.synpop.OMXODParser;
import ch.sbb.matsim.routing.SBBAnalysisMainModeIdentifier;
import ch.sbb.matsim.zones.Zone;
import ch.sbb.matsim.zones.Zones;
import ch.sbb.matsim.zones.ZonesLoader;
import org.apache.commons.lang3.mutable.MutableInt;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.population.Population;
import org.matsim.api.core.v01.population.PopulationWriter;
import org.matsim.contrib.common.util.WeightedRandomSelection;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.gbl.MatsimRandom;
import org.matsim.core.population.io.PopulationReader;
import org.matsim.core.router.MainModeIdentifier;
import org.matsim.core.router.TripStructureUtils;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.facilities.MatsimFacilitiesReader;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/*
    This scripts takes:
    - the cross-border endogenous demand ("Grenzgaenger")
    - the cross-border demand from the NPVM
    - If two zones show insufficient or no endogenous demand, additional single-trip agents are generated

 */
public class GenerateAdditionalTripsFromAbroad {
    public static final String MATRIX_NAME = "7";
    private final OMXODParser omxodParser;
    private final Population population;
    private final List<String> relevantModes;
    private final Zones zones;
    private final MainModeIdentifier mainModeIdentifier;
    private final int sampleFactor;
    private final Random random;
    private final Logger logger = LogManager.getLogger(GenerateAdditionalTripsFromAbroad.class);
    private final Map<String, WeightedRandomSelection<Id<Zone>>> weightedZonesPerAggregate = new HashMap<>();
    private final Map<String, Map<String, MutableInt>> endogenousDemand = new HashMap<>();
    private final Map<String, Map<String, Double>> missingDemand = new HashMap<>();

    GenerateAdditionalTripsFromAbroad(OMXODParser omxodParser, Population population, List<String> relevantModes, Zones zones, int sampleFactor, Random random) {
        this.omxodParser = omxodParser;
        this.population = population;
        this.relevantModes = relevantModes;
        this.zones = zones;
        this.mainModeIdentifier = new SBBAnalysisMainModeIdentifier();
        this.sampleFactor = sampleFactor;
        this.random = random;
        prepareZonalDistributions();
        analyzeEndogenousDemand();


    }

    public static void main(String[] args) {
        String inputPopulationFile = "\\\\wsbbrz0283\\mobi\\40_Projekte\\20230825_Grenzguertel\\plans\\v7\\plans.xml.gz";
        String inputFacilitiesFile = "\\\\wsbbrz0283\\mobi\\40_Projekte\\20230825_Grenzguertel\\plans\\v7\\facilities.xml.gz";
        String inputZonesFile = "\\\\wsbbrz0283\\mobi\\40_Projekte\\20230825_Grenzguertel\\plans\\v7\\mobi-zones.shp";
        String npvmMatrixFile = "\\\\wsbbrz0283\\mobi\\40_Projekte\\20240207_MOBi_5.0\\plans_exogeneous\\MIV international\\input\\NPVM_2017_7_QZD.omx";
        String outputMissingDemandStatsFile = "\\\\wsbbrz0283\\mobi\\40_Projekte\\20240207_MOBi_5.0\\plans_exogeneous\\MIV international\\output\\missingDemand_amr.csv";
        String foreignConnectorsLocationFile = "\\\\wsbbrz0283\\mobi\\40_Projekte\\20240207_MOBi_5.0\\plans_exogeneous\\MIV international\\input\\Anbindungen_Pseudozonen_Ausland.csv";
        String timeDistributionFile = "\\\\wsbbrz0283\\mobi\\40_Projekte\\20240207_MOBi_5.0\\plans_exogeneous\\MIV international\\input\\2016_Ganglinie.csv";
        String outputPopulationFile = "\\\\wsbbrz0283\\mobi\\40_Projekte\\20240207_MOBi_5.0\\plans_exogeneous\\MIV international\\output\\cb_road.xml.gz";
        int sampleFactor = 1;


        Random random = MatsimRandom.getRandom();
        Scenario scenario = ScenarioUtils.createScenario(ConfigUtils.createConfig());
        new PopulationReader(scenario).readFile(inputPopulationFile);
        new MatsimFacilitiesReader(scenario).readFile(inputFacilitiesFile);
        var relevantModes = List.of(SBBModes.RIDE, SBBModes.CAR);
        OMXODParser parser = new OMXODParser();
        parser.openMatrix(npvmMatrixFile);
        Zones zs = ZonesLoader.loadZones("z", inputZonesFile);
        GenerateAdditionalTripsFromAbroad generator = new GenerateAdditionalTripsFromAbroad(parser, scenario.getPopulation(), relevantModes, zs, sampleFactor, random);
        generator.calculateMissingDemand();
        generator.writeMissingDemandReport(generator.getMissingDemand(), outputMissingDemandStatsFile);
        var coordinateSelector = SingleTripAgentCreator.createCoordinateSelector(scenario, zs, random, foreignConnectorsLocationFile);
        var timeDistribution = SingleTripAgentCreator.readTimeDistribution(timeDistributionFile, random);
        Scenario outputScenario = ScenarioUtils.createScenario(ConfigUtils.createConfig());

        SingleTripAgentCreator creator = new SingleTripAgentCreator(outputScenario.getPopulation(), timeDistribution, coordinateSelector, "cbHome", "cbHome", null, random);
        creator.generateAgents(generator.disaggregateMissingDemand(), SBBModes.CAR, Variables.CB_ROAD, "");
        new PopulationWriter(outputScenario.getPopulation()).write(outputPopulationFile);


    }

    private Map<Id<Zone>, Map<Id<Zone>, Double>> disaggregateMissingDemand() {
        Map<Id<Zone>, Map<Id<Zone>, Double>> disaggregatedMissingDemand = new HashMap<>();
        for (var fromAgg : this.missingDemand.entrySet()) {
            for (var toAgg : fromAgg.getValue().entrySet()) {
                Double trips = toAgg.getValue();
                for (double i = 0.0; i < trips; i++) {
                    double leftoverTrips = trips - i;
                    if (leftoverTrips < 1.0) {
                        if (random.nextDouble() > leftoverTrips) continue;
                    }
                    Id<Zone> fromZone = weightedZonesPerAggregate.get(fromAgg.getKey()).select();
                    Id<Zone> toZone = weightedZonesPerAggregate.get(toAgg.getKey()).select();
                    Map<Id<Zone>, Double> fromZoneDemand = disaggregatedMissingDemand.computeIfAbsent(fromZone, a -> new HashMap<>());
                    double tripsOnRelation = fromZoneDemand.getOrDefault(toZone, 0.0) + 1.0;
                    fromZoneDemand.put(toZone, tripsOnRelation);

                }
            }
        }
        return disaggregatedMissingDemand;
    }

    private void writeMissingDemandReport(Map<String, Map<String, Double>> demandAggregation, String fileName) {
        try (CSVWriter writer = new CSVWriter(List.of("from", "to", "value"), fileName)) {
            for (var e : demandAggregation.entrySet()) {
                for (var tos : e.getValue().entrySet()) {
                    if (Math.abs(tos.getValue().doubleValue()) > 1) {
                        writer.set("from", e.getKey());
                        writer.set("to", tos.getKey());
                        writer.set("value", tos.getValue().toString());
                        writer.writeRow();
                    }
                }
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void prepareZonalDistributions() {
        List<Id<Zone>> allZoneIds = omxodParser.getAllZoneIdsInLookup();
        for (var zoneId : allZoneIds) {
            String aggregate = SingleTripAgentCreator.getAggregateZone(zones, zoneId);
            Zone zone = zones.getZone(zoneId);
            double weight = Math.max(zone != null ? (Integer) zone.getAttribute("pop_total") : 1.0, 1.0);
            weightedZonesPerAggregate.computeIfAbsent(aggregate, a -> new WeightedRandomSelection<>(random)).add(zoneId, weight);
        }
    }

    public void calculateMissingDemand() {
        logger.info("Calculating missing demand.");
        List<Id<Zone>> allZoneIds = omxodParser.getAllZoneIdsInLookup();
        for (var fromZoneId : allZoneIds) {
            String fromAggregate = SingleTripAgentCreator.getAggregateZone(zones, fromZoneId);
            for (var toZoneId : allZoneIds) {
                String toAggregate = SingleTripAgentCreator.getAggregateZone(zones, toZoneId);
                double npvmValue = omxodParser.getMatrixValue(fromZoneId, toZoneId, MATRIX_NAME);
                double endogenousValue = endogenousDemand.getOrDefault(fromAggregate, new HashMap<>()).getOrDefault(toAggregate, new MutableInt()).doubleValue();
                double diff = npvmValue - endogenousValue;
                missingDemand.computeIfAbsent(fromAggregate, a -> new HashMap<>()).put(toAggregate, diff);
            }
        }
        logger.info("Done.");
    }


    private void analyzeEndogenousDemand() {
        logger.info("Analyzing endogenous demand.");

        population.getPersons()
                .values()
                .stream()
                .flatMap(person -> TripStructureUtils.getTrips(person.getSelectedPlan()).stream())
                .filter(trip -> relevantModes.contains(mainModeIdentifier.identifyMainMode(trip.getTripElements())))
                .forEach(trip -> {
                    Zone fromZone = zones.findZone(trip.getOriginActivity().getCoord());
                    if (fromZone != null) {
                        String fromAgg = SingleTripAgentCreator.getAggregateZone(zones, fromZone.getId());
                        Zone toZone = zones.findZone(trip.getDestinationActivity().getCoord());
                        String toAgg = SingleTripAgentCreator.getAggregateZone(zones, toZone.getId());
                        if (toZone != null) {
                            endogenousDemand.computeIfAbsent(fromAgg, a -> new HashMap<>()).computeIfAbsent(toAgg, a -> new MutableInt()).add(sampleFactor);
                        }
                    }
                });
        logger.info("Done.");

    }

    public Map<String, Map<String, Double>> getMissingDemand() {
        return missingDemand;
    }
}
