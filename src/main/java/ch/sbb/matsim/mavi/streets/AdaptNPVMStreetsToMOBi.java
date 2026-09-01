package ch.sbb.matsim.mavi.streets;

import ch.sbb.matsim.config.variables.SBBModes;
import ch.sbb.matsim.csv.CSVReader;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.core.network.NetworkUtils;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class AdaptNPVMStreetsToMOBi {

    private AdaptNPVMStreetsToMOBi() {}

    public static void adjustSpeedsAndCapacities(String typeLookUpFile, Network network ){
     System.out.println("Adjusting speeds and capacities based on: " + typeLookUpFile);
     Map<String, Double> speedFactors = new HashMap<>();
     Map<String, Double> capacityFactors = new HashMap<>();
     try (CSVReader reader = new CSVReader(typeLookUpFile, ";")){
        var line = reader.readLine();
         while (line != null) {
             double speedFactor = Double.parseDouble(line.get("factor_v0"));
             double capacityFactor = Double.parseDouble(line.get("factor_cap"));
             String[] types = line.get("type_no").split(",");
             for (String type : types) {
                 speedFactors.put(type, speedFactor);
                 capacityFactors.put(type, capacityFactor);
             }

             line = reader.readLine();
         }
      } catch (IOException e) {
        throw new RuntimeException(e);
    }
     for (Link link : network.getLinks().values()) {
         if (link.getAllowedModes().contains(SBBModes.CAR)){
         String t = NetworkUtils.getType(link);
            if (t != null) {
                Double speedFactor = speedFactors.get(t);
                Double capacityFactor = capacityFactors.get(t);
                if (speedFactor != null) {
                    double newFreespeed = link.getFreespeed() * speedFactor;
                    link.setFreespeed(newFreespeed);
                }
                if (capacityFactor != null) {
                    double newCapacity = link.getCapacity() * capacityFactor;
                    link.setCapacity(newCapacity);
                }
            }
     }
     }


    }

}
