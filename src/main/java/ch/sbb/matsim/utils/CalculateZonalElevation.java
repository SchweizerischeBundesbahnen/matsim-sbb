package ch.sbb.matsim.utils;

import ch.sbb.matsim.csv.CSVWriter;
import ch.sbb.matsim.zones.Zone;
import ch.sbb.matsim.zones.Zones;
import ch.sbb.matsim.zones.ZonesLoader;
import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.Network;
import org.matsim.core.network.NetworkUtils;
import org.matsim.core.network.io.MatsimNetworkReader;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class CalculateZonalElevation {
    public static void main(String[] args) {
        String networkFile = args[0];
        String zonesFile = args[1];
        String outputFile = args[2];
        Map<Id<Zone>, DescriptiveStatistics> nodeElevationPerZone = new HashMap<>();

        Network network = NetworkUtils.createNetwork();
        new MatsimNetworkReader(network).readFile(networkFile);
        if (!network.getNodes().values().stream().allMatch(node -> node.getCoord().hasZ())){
            throw new RuntimeException("Network is not three-dimensional");
        }
        Zones zones = ZonesLoader.loadZones("zones",zonesFile);
        network.getNodes().values().stream().map(node -> node.getCoord()).forEach(coord -> {
            Zone zone = null;
            try {zone = zones.findZone(coord.getX(), coord.getY());}
            catch (Exception e){}
            if (zone!=null){
                nodeElevationPerZone.computeIfAbsent(zone.getId(),a->new DescriptiveStatistics()).addValue(coord.getZ());
            }
        });

        String zone_id = "zone_id";
        String min_elevation = "min_elevation";
        String max_elevation = "max_elevation";
        String mean_elevation = "mean_elevation";
        String median_elevation = "median_elevation";
        try (CSVWriter writer = new CSVWriter(null,new String[]{zone_id,min_elevation,max_elevation,mean_elevation,median_elevation},outputFile)){
            for (var entry : nodeElevationPerZone.entrySet()){
                writer.set(zone_id,entry.getKey().toString());
                writer.set(min_elevation, String.valueOf(entry.getValue().getMin()));
                writer.set(max_elevation, String.valueOf(entry.getValue().getMax()));
                writer.set(mean_elevation, String.valueOf(entry.getValue().getMean()));
                writer.set(median_elevation, String.valueOf(entry.getValue().getPercentile(.5)));
                writer.writeRow();
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }


    }
}
