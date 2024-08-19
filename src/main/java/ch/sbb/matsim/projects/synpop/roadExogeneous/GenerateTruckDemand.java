package ch.sbb.matsim.projects.synpop.roadExogeneous;

import ch.sbb.matsim.config.variables.SBBModes;
import ch.sbb.matsim.projects.synpop.OMXODParser;
import ch.sbb.matsim.projects.synpop.SingleTripAgentToCSVConverter;
import ch.sbb.matsim.zones.Zone;
import ch.sbb.matsim.zones.Zones;
import ch.sbb.matsim.zones.ZonesLoader;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.PopulationWriter;
import org.matsim.contrib.common.util.WeightedRandomSelection;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.gbl.MatsimRandom;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.facilities.MatsimFacilitiesReader;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

public class GenerateTruckDemand {
    private final String matrixNo;
    private final OMXODParser parser;
    private final Zones zones;
    private final Random random;
    private final Map<String, WeightedRandomSelection<Id<Zone>>> zoneInAMRSelector = new HashMap<>();
    private final Map<String, Map<String, Double>> aggregatedDemand = new HashMap<>();
    private final Map<Id<Zone>, Map<Id<Zone>, Double>> disaggregatedDemand = new HashMap<>();
    private final Logger logger = LogManager.getLogger(getClass());
    private final double growthRate;

    public GenerateTruckDemand(String matrixNo, OMXODParser parser, Zones zones, Random random, double growthRate) {
        logger.info("Matrix " + matrixNo);
        this.matrixNo = matrixNo;
        this.parser = parser;
        this.zones = zones;
        this.random = random;
        this.growthRate = growthRate;
        prepareZoneAggregates();
    }

    public static void main(String[] args) throws IOException {
        String inputZonesFile = "\\\\wsbbrz0283\\mobi\\40_Projekte\\20230825_Grenzguertel\\plans\\v7\\mobi-zones.shp";
        String npvmMatrixFile = "\\\\wsbbrz0283\\mobi\\40_Projekte\\20240207_MOBi_5.0\\plans_exogeneous\\GV\\input\\NPVM2017_LI_LZ_LW.omx";
        String foreignConnectorsLocationFile = "\\\\wsbbrz0283\\mobi\\40_Projekte\\20240207_MOBi_5.0\\plans_exogeneous\\MIV international\\input\\Anbindungen_Pseudozonen_Ausland.csv";
        String timeDistributionFile = "\\\\wsbbrz0283\\mobi\\40_Projekte\\20240207_MOBi_5.0\\plans_exogeneous\\MIV international\\input\\2016_Ganglinie.csv";
        String inputFacilitiesFile = "\\\\wsbbrz0283\\mobi\\40_Projekte\\20230825_Grenzguertel\\plans\\v7\\facilities.xml.gz";
        String outputPopulationFile = "c:\\devsbb\\freight.xml.gz";
        String outputPopulationCSVFile = "c:\\devsbb\\freight_road.csv.gz";

        Random random = MatsimRandom.getRandom();
        Map<String, String> matricesToModeMap = Map.of("10", "LI", "11", "LW", "12", "LZ");
        Map<String, Double> growthRates = Map.of("10", 1.16, "11", 1.045, "12", 1.045);
        Zones zones = ZonesLoader.loadZones("zones", inputZonesFile);
        Scenario scenario = ScenarioUtils.createScenario(ConfigUtils.createConfig());
        new MatsimFacilitiesReader(scenario).readFile(inputFacilitiesFile);
        var coordinateSelector = SingleTripAgentCreator.createCoordinateSelector(scenario, zones, random, foreignConnectorsLocationFile);
        var timeDistribution = SingleTripAgentCreator.readTimeDistribution(timeDistributionFile, random);
        SingleTripAgentCreator singleTripAgentCreator = new SingleTripAgentCreator(scenario.getPopulation(), timeDistribution, coordinateSelector, "freight", "freight", null, random);
        OMXODParser parser = new OMXODParser();
        parser.openMatrix(npvmMatrixFile);
        for (var demandSegment : matricesToModeMap.entrySet()) {
            GenerateTruckDemand generateTruckDemand = new GenerateTruckDemand(demandSegment.getKey(), parser, zones, random, growthRates.get(demandSegment.getKey()));
            generateTruckDemand.aggregateDemand();
            generateTruckDemand.disaggregateDemand();
            singleTripAgentCreator.generateAgents(generateTruckDemand.getDisaggregatedDemand(), SBBModes.CAR, "freight_road", demandSegment.getValue());
        }
        for (Person p : scenario.getPopulation().getPersons().values()) {
            String vehicleType = "";
            if (p.getId().toString().contains("LZ")) {
                vehicleType = "LZ";
            }
            if (p.getId().toString().contains("LW")) {
                vehicleType = "LW";

            }
            if (p.getId().toString().contains("LI")) {
                vehicleType = "LI";

            }
            p.getAttributes().putAttribute("vehicleType", vehicleType);

        }

        new PopulationWriter(scenario.getPopulation()).write(outputPopulationFile);
        SingleTripAgentToCSVConverter.writeSingleTripsAsCSV(outputPopulationCSVFile, scenario.getPopulation());

    }

    private void prepareZoneAggregates() {
        logger.info("preparing zone aggregates");
        for (var zoneId : parser.getAllZoneIdsInLookup()) {
            double weight = Math.max(Arrays.stream(parser.getMatrixRow(zoneId, matrixNo)).sum(), 0.000001);
            String aggregateZoneId = SingleTripAgentCreator.getAggregateZone(zones, zoneId);
            zoneInAMRSelector.computeIfAbsent(aggregateZoneId, a -> new WeightedRandomSelection<>(random)).add(zoneId, weight);
        }
        logger.info("done");
    }

    private void aggregateDemand() {
        logger.info("aggregating demand");
        for (var fromZoneId : parser.getAllZoneIdsInLookup()) {
            String fromAggregate = SingleTripAgentCreator.getAggregateZone(zones, fromZoneId);
            var fromAggregateMap = aggregatedDemand.computeIfAbsent(fromAggregate, a -> new HashMap<>());
            for (var toZoneId : parser.getAllZoneIdsInLookup()) {
                double value = parser.getMatrixValue(fromZoneId, toZoneId, matrixNo);
                if (value > 0.0) {
                    value = growthRate * value;
                    String toAggregate = SingleTripAgentCreator.getAggregateZone(zones, fromZoneId);
                    double aggregateValue = fromAggregateMap.getOrDefault(toAggregate, 0.0) + value;
                    fromAggregateMap.put(toAggregate, aggregateValue);
                }
            }
        }
        logger.info("done");

    }

    private void disaggregateDemand() {
        logger.info("disaggregating demand");
        for (var fromAggregateEntry : this.aggregatedDemand.entrySet()) {
            String fromAggregate = fromAggregateEntry.getKey();
            for (var toAggregateEntry : fromAggregateEntry.getValue().entrySet()) {
                String toAggregate = toAggregateEntry.getKey();
                Double value = toAggregateEntry.getValue();
                for (double i = 0.0; i < value; i++) {
                    double valueToWrite = value - i > 1.0 ? 1.0 : value - i;
                    var fromZoneId = zoneInAMRSelector.get(fromAggregate).select();
                    var toZoneId = zoneInAMRSelector.get(toAggregate).select();
                    Map<Id<Zone>, Double> fromZoneMap = disaggregatedDemand.computeIfAbsent(fromZoneId, a -> new HashMap<>());
                    double newValue = fromZoneMap.getOrDefault(toZoneId, 0.0) + valueToWrite;
                    fromZoneMap.put(toZoneId, newValue);
                }
            }
        }
        logger.info("done");

    }

    public Map<Id<Zone>, Map<Id<Zone>, Double>> getDisaggregatedDemand() {
        return disaggregatedDemand;
    }

}
