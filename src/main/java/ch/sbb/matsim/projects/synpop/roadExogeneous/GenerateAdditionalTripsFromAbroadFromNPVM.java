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
import org.matsim.core.population.PopulationUtils;
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

/**
 * Generates additional cross-border road single-trip agents based on the NPVM demand matrix.
 * <p>
 * The class compares two sources of cross-border ("Grenzgaenger") road demand:
 * <ul>
 *     <li>the <b>endogenous</b> demand already present in the MATSim population
 *         (regular subpopulation, {@code car}/{@code ride} trips), aggregated per zone group;</li>
 *     <li>the <b>exogenous</b> demand taken from the NPVM origin-destination matrix
 *         ({@link #MATRIX_NAME}).</li>
 * </ul>
 * Where the NPVM demand exceeds the endogenous demand between two zone aggregates, the difference
 * ("missing demand") is disaggregated onto individual zones (weighted by population) and materialised
 * as single-trip agents via {@link SingleTripAgentCreator}.
 */
public class GenerateAdditionalTripsFromAbroadFromNPVM {
    /** Name of the NPVM OD matrix inside the OMX file that holds the relevant cross-border road demand. */
    public static final String MATRIX_NAME = "7";
    /**
     * Highest zone id that is still considered domestic (Switzerland). Zones with an id strictly greater
     * than this value are treated as foreign; a relation is only relevant for cross-border demand if at
     * least one of its endpoints is a foreign zone.
     */
    private static final int MAX_DOMESTIC_ZONE_ID = 730101001;
    private final OMXODParser omxodParser;
    private final Population population;
    private final List<String> relevantModes;
    private final Zones zones;
    private final MainModeIdentifier mainModeIdentifier;
    private final int sampleFactor;
    private final Random random;
    private final Logger logger = LogManager.getLogger(GenerateAdditionalTripsFromAbroadFromNPVM.class);
    private final Map<String, WeightedRandomSelection<Id<Zone>>> weightedZonesPerAggregate = new HashMap<>();
    private final Map<String, Map<String, MutableInt>> endogenousDemand = new HashMap<>();
    private final Map<String, Map<String, Double>> missingDemand = new HashMap<>();
    private final double growthFactor;

    /**
     * @param omxodParser   parser holding the opened NPVM OD matrix
     * @param population     MATSim population used to derive the endogenous cross-border demand
     * @param relevantModes  modes counted as road demand (typically {@code car} and {@code ride})
     * @param zones          zone system used for spatial lookups and aggregation
     * @param sampleFactor   scaling factor of the population sample (1 = 100% sample)
     * @param random         random source (shared with the weighted zone selections)
     * @param growthFactor   multiplicative factor applied to the missing demand (scenario growth)
     */
    GenerateAdditionalTripsFromAbroadFromNPVM(OMXODParser omxodParser, Population population, List<String> relevantModes, Zones zones, int sampleFactor, Random random, double growthFactor) {
        this.growthFactor = growthFactor;
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
        String inputPopulationFile = "D:\\mobi\\40_Projekte\\20260513_MOBi_5.2\\2023\\plans\\117_skims\\output\\117_skims.plans.xml.zst";
        String inputFacilitiesFile = "D:\\mobi\\40_Projekte\\20260513_MOBi_5.2\\2023\\plans\\117_skims\\output\\117_skims.facilities.xml.zst";
        String inputZonesFile = "D:\\mobi\\40_Projekte\\20260513_MOBi_5.2\\2023\\plans\\117_skims\\output\\117_skims.mobi_zones.gpkg";
        String npvmMatrixFile = "D:\\mobi\\40_Projekte\\20260513_MOBi_5.2\\2023\\exogenous\\freight\\7QZD_LI_LW_LZ_NPVM2023.omx";
        String outputMissingDemandStatsFile = "D:\\mobi\\40_Projekte\\20260513_MOBi_5.2\\2023\\exogenous\\cb_roadmissingDemand_amr.csv";
        String foreignConnectorsLocationFile = "D:\\mobi\\50_Ergebnisse\\MOBi_5.0\\plans_exogeneous\\MIV international\\input\\Anbindungen_Pseudozonen_Ausland.csv";
        String timeDistributionFile = "D:\\mobi\\40_Projekte\\20260513_MOBi_5.2\\2023\\exogenous\\freight\\Ganglinie_angepasst.csv";
        String outputPopulationFile = "D:\\mobi\\40_Projekte\\20260513_MOBi_5.2\\2023\\exogenous\\cb_road.xml.gz";
        int sampleFactor = 1;
        double growthFactor = 1.00;


        Random random = MatsimRandom.getRandom();
        Scenario scenario = ScenarioUtils.createScenario(ConfigUtils.createConfig());
        new PopulationReader(scenario).readFile(inputPopulationFile);
        new MatsimFacilitiesReader(scenario).readFile(inputFacilitiesFile);
        var relevantModes = List.of(SBBModes.RIDE, SBBModes.CAR);
        OMXODParser parser = new OMXODParser();
        parser.openMatrix(npvmMatrixFile);
        Zones zs = ZonesLoader.loadZones("z", inputZonesFile);
        GenerateAdditionalTripsFromAbroadFromNPVM generator = new GenerateAdditionalTripsFromAbroadFromNPVM(parser, scenario.getPopulation(), relevantModes, zs, sampleFactor, random, growthFactor);
        generator.calculateMissingDemand();
        generator.writeMissingDemandReport(generator.getMissingDemand(), outputMissingDemandStatsFile);
        var coordinateSelector = SingleTripAgentCreator.createCoordinateSelector(scenario, zs, random, foreignConnectorsLocationFile);
        var timeDistribution = SingleTripAgentCreator.readTimeDistribution(timeDistributionFile, random);
        Scenario outputScenario = ScenarioUtils.createScenario(ConfigUtils.createConfig());

        SingleTripAgentCreator creator = new SingleTripAgentCreator(outputScenario.getPopulation(), timeDistribution, coordinateSelector, "cbHome", "cbHome", null, random);
        creator.generateAgents(generator.disaggregateMissingDemand(), SBBModes.CAR, Variables.CB_ROAD, "");
        new PopulationWriter(outputScenario.getPopulation()).write(outputPopulationFile);


    }

    /**
     * Distributes the aggregate "missing demand" onto concrete origin/destination zones.
     * <p>
     * The (scaled) number of trips per aggregate relation is turned into integer trips; a fractional
     * remainder is realised stochastically (e.g. 2.3 trips -> 2 trips plus a 30% chance of a third).
     * For each trip an origin and destination zone are drawn from the population-weighted distribution
     * of the respective aggregate. Non-positive missing demand is implicitly ignored because the loop
     * body never executes for {@code trips <= 0}.
     *
     * @return nested map origin zone -> destination zone -> number of generated trips
     */
    private Map<Id<Zone>, Map<Id<Zone>, Double>> disaggregateMissingDemand() {
        Map<Id<Zone>, Map<Id<Zone>, Double>> disaggregatedMissingDemand = new HashMap<>();
        for (var fromAgg : this.missingDemand.entrySet()) {
            for (var toAgg : fromAgg.getValue().entrySet()) {
                Double trips = toAgg.getValue() * growthFactor;
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

    /**
     * Writes an aggregate origin/destination report of the (signed) missing demand to a CSV file.
     * Only relations with an absolute value greater than 1 are written.
     *
     * @param demandAggregation aggregate origin -> destination -> demand value
     * @param fileName          target CSV file
     */
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

    /**
     * Builds, per zone aggregate, a population-weighted random selection of its member zones. This is
     * later used to draw concrete origin/destination zones when disaggregating the missing demand.
     */
    private void prepareZonalDistributions() {
        List<Id<Zone>> allZoneIds = omxodParser.getAllZoneIdsInLookup();
        for (var zoneId : allZoneIds) {
            String aggregate = SingleTripAgentCreator.getAggregateZone(zones, zoneId);
            Zone zone = zones.getZone(zoneId);
            double weight = Math.max(zone != null ? ((Number) zone.getAttribute("pop_total")).longValue() : 1.0, 1.0);
            weightedZonesPerAggregate.computeIfAbsent(aggregate, a -> new WeightedRandomSelection<>(random)).add(zoneId, weight);
        }
    }

    /**
     * Computes, per aggregate origin/destination relation, the demand that the NPVM matrix contains on
     * top of the already existing endogenous demand ({@code missing = npvm - endogenous}). Only relations
     * touching at least one foreign zone (id &gt; {@link #MAX_DOMESTIC_ZONE_ID}) are considered.
     */
    public void calculateMissingDemand() {
        logger.info("Calculating missing demand.");
        List<Id<Zone>> allZoneIds = omxodParser.getAllZoneIdsInLookup();
        for (var fromZoneId : allZoneIds) {
            String fromAggregate = SingleTripAgentCreator.getAggregateZone(zones, fromZoneId);
            for (var toZoneId : allZoneIds) {
                if (Integer.parseInt(fromZoneId.toString()) > MAX_DOMESTIC_ZONE_ID || Integer.parseInt(toZoneId.toString()) > MAX_DOMESTIC_ZONE_ID) {
                    String toAggregate = SingleTripAgentCreator.getAggregateZone(zones, toZoneId);
                    double npvmValue = omxodParser.getMatrixValue(fromZoneId, toZoneId, MATRIX_NAME);
                    double endogenousValue = endogenousDemand.getOrDefault(fromAggregate, new HashMap<>()).getOrDefault(toAggregate, new MutableInt()).doubleValue();
                    double diff = npvmValue - endogenousValue;
                    missingDemand.computeIfAbsent(fromAggregate, a -> new HashMap<>()).put(toAggregate, diff);
                }
            }
        }
        logger.info("Done.");
    }


    /**
     * Aggregates the endogenous cross-border road demand from the regular subpopulation. For every
     * relevant trip the origin/destination zones are looked up, mapped to their aggregates and counted
     * (scaled by {@link #sampleFactor}).
     */
    private void analyzeEndogenousDemand() {
        logger.info("Analyzing endogenous demand.");

        population.getPersons()
                .values()
                .stream()
                .filter(person -> PopulationUtils.getSubpopulation(person).equals(Variables.REGULAR))
                .flatMap(person -> TripStructureUtils.getTrips(person.getSelectedPlan()).stream())
                .filter(trip -> relevantModes.contains(mainModeIdentifier.identifyMainMode(trip.getTripElements())))
                .forEach(trip -> {
                    Zone fromZone = zones.findZone(trip.getOriginActivity().getCoord());
                    if (fromZone != null) {
                        String fromAgg = SingleTripAgentCreator.getAggregateZone(zones, fromZone.getId());
                        Zone toZone = zones.findZone(trip.getDestinationActivity().getCoord());
                        if (toZone != null) {
                            String toAgg = SingleTripAgentCreator.getAggregateZone(zones, toZone.getId());
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
