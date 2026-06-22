package ch.sbb.matsim.plans.models.utils;

import ch.sbb.matsim.analysis.tripsandlegsanalysis.DemandAggregator;
import ch.sbb.matsim.zones.Zones;
import ch.sbb.matsim.zones.ZonesLoader;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.population.Person;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.population.io.PopulationReader;
import org.matsim.core.scenario.ScenarioUtils;

import java.util.ArrayList;
import java.util.TreeSet;
import java.util.stream.Collectors;

public class WriteDataRelationAsMatrix {
    public static void main(String[] args) {
        String inputPopulationFile = args[0];
        String inputZonesFile = args[1];
        String outputMatrixFile = args[2];
        String fromAttribute = args[3];
        String toAttribute = args[4];
        String aggregationAttribute = args[5];
        Zones zones = ZonesLoader.loadZones("zones", inputZonesFile);
        Scenario scenario = ScenarioUtils.createScenario(ConfigUtils.createConfig());
        new PopulationReader(scenario).readFile(inputPopulationFile);
        computeAndWriteMatrix(zones, aggregationAttribute, scenario, fromAttribute, toAttribute, outputMatrixFile);


    }

    public static void computeAndWriteMatrix(Zones zones, String aggregationAttribute, Scenario scenario, String fromAttribute, String toAttribute, String outputMatrixFile) {
        var zoneToAggregateMapping = zones.getZones().stream().collect(Collectors.toMap(zone -> zone.getId().toString(), zone -> zone.getAttribute(aggregationAttribute).toString()));
        var sortedZones = new TreeSet<>(new DemandAggregator.StringNumberComparator());
        sortedZones.addAll(zoneToAggregateMapping.values());
        ArrayList<String> aggregateZones = new ArrayList<>(sortedZones);
        float[][] matrix = new float[aggregateZones.size()][aggregateZones.size()];
        for (Person person : scenario.getPopulation().getPersons().values()){
            String fromValue = String.valueOf(person.getAttributes().getAttribute(fromAttribute));
            String toValue = String.valueOf(person.getAttributes().getAttribute(toAttribute));
            String fromAggregate = zoneToAggregateMapping.get(fromValue);
            String toAggregate = zoneToAggregateMapping.get(toValue);
            if (fromAggregate != null && toAggregate != null) {
                int fromIndex = aggregateZones.indexOf(fromAggregate);
                int toIndex = aggregateZones.indexOf(toAggregate);
                matrix[fromIndex][toIndex] += 1.0;

            }
        }
        DemandAggregator.writeMatrix(matrix, outputMatrixFile, aggregateZones);
    }
}
