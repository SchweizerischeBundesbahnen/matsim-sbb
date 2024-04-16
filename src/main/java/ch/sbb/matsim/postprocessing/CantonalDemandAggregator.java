package ch.sbb.matsim.postprocessing;

import ch.sbb.matsim.analysis.tripsandlegsanalysis.DemandAggregator;
import ch.sbb.matsim.analysis.tripsandlegsanalysis.RailTripsAnalyzer;
import ch.sbb.matsim.config.PostProcessingConfigGroup;
import ch.sbb.matsim.zones.ZonesCollection;
import ch.sbb.matsim.zones.ZonesLoader;
import org.matsim.api.core.v01.Scenario;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.network.io.MatsimNetworkReader;
import org.matsim.core.population.io.PopulationReader;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.pt.transitSchedule.api.TransitScheduleReader;

import java.util.stream.Collectors;

public class CantonalDemandAggregator {

    public static void main(String[] args) {
        String experiencedPlansFile = "\\\\wsbbrz0283\\mobi\\50_Ergebnisse\\MOBi_4.0\\2017\\sim\\3.3.2017.7.50pct\\output_slice0\\M332017.7.output_experienced_plans.xml.gz";
        String transitScheduleFile = "\\\\wsbbrz0283\\mobi\\50_Ergebnisse\\MOBi_4.0\\2017\\sim\\3.3.2017.7.50pct\\output_slice0\\M332017.7.output_transitSchedule.xml.gz";
        String zonesShapeFile = "\\\\wsbbrz0283\\mobi\\40_Projekte\\20230825_Grenzguertel\\plans\\v7\\mobi-zones.shp";
        String networkFile = "\\\\wsbbrz0283\\mobi\\50_Ergebnisse\\MOBi_4.0\\2017\\sim\\3.3.2017.7.50pct\\output_slice0\\M332017.7.output_network.xml.gz";
        String aggregationId = "amr_id";
        double scaleFactor = 0.25;
        String outputFile = "trip_demand_per_canton.csv";

        ZonesCollection zonesCollection = new ZonesCollection();
        zonesCollection.addZones(ZonesLoader.loadZones("zones", zonesShapeFile, "zone_id"));
        Scenario scenario = ScenarioUtils.createScenario(ConfigUtils.createConfig());
        new TransitScheduleReader(scenario).readFile(transitScheduleFile);
        new MatsimNetworkReader(scenario.getNetwork()).readFile(networkFile);
        new PopulationReader(scenario).readFile(experiencedPlansFile);
        PostProcessingConfigGroup ppcg = new PostProcessingConfigGroup();
        ppcg.setRailMatrixAggregate(aggregationId);
        ppcg.setZonesId("zones");
        ppcg.setSimulationSampleSize(scaleFactor);
        RailTripsAnalyzer railTripsAnalyzer = new RailTripsAnalyzer(scenario.getTransitSchedule(), scenario.getNetwork(), zonesCollection);
        DemandAggregator demandAggregator = new DemandAggregator(scenario, zonesCollection, ppcg, railTripsAnalyzer);
        demandAggregator.aggregateTripDemand(scaleFactor, scenario.getPopulation().getPersons().values().stream().map(person -> person.getSelectedPlan()).collect(Collectors.toList()));
        demandAggregator.writeTripDemand("amr_id", "amr_name", outputFile);


    }
}
