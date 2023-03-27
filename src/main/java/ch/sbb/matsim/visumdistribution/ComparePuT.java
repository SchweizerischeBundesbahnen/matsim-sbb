package ch.sbb.matsim.visumdistribution;

import java.io.BufferedReader;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.commons.lang3.StringUtils;

public class ComparePuT {

    public static void main(String[] args) {

        String visum = "Z:/99_Playgrounds/MD/Umlegung2/visum/PathRoutesVisumLinesV2.csv";
        //String matsim = "Z:/99_Playgrounds/MD/Umlegung2/treeRoutesvLines.csv";
        String matsim = "treeRoutesvLines.csv";

        Set<String> matSimRoutesSet = new HashSet<>();
        Set<String> visumRoutesSet = new HashSet<>();
        List<String> matSimRoutesList = new ArrayList<>();
        List<String> visumRoutesList = new ArrayList<>();

        Map<String, Double> matsimMap = new HashMap<>();
        Map<String, Double> visumMap = new HashMap<>();
        int nulll = 0;
        int[] umsteigeVisum = new int[20];
        int[] umsteigeMatsim = new int[20];
        int[] umsteigebeides = new int[20];
        double[] demandVisum = new double[20];
        double[] demandMatsim = new double[20];
        double[] demandbeides = new double[20];
        List<Integer> skip = List.of(3, 9, 15, 21, 27, 33, 39, 45, 51, 57, 63, 69, 75, 81, 87, 93, 99);
        List<Integer> time = List.of(5, 11, 17, 23, 29, 35, 41, 47, 53, 59, 65, 71, 77, 83, 89, 95, 101);
        try (BufferedReader reader = new BufferedReader(new FileReader(matsim))) {
            String line;
            while ((line = reader.readLine()) != null) {
                StringBuilder newLine = new StringBuilder();
                String[] splitLine = line.split(";");
                for (int i = 0; i < splitLine.length - 1; i++) {
                    if (!skip.contains(i)) {
                        if (time.contains(i)) {
                            int tmp = Integer.parseInt(splitLine[i]);
                            tmp = tmp % 86400;
                            newLine.append(tmp).append(";");
                        } else {
                            newLine.append(splitLine[i]).append(";");
                        }
                    }
                }
                if (newLine.length() == 0) {
                    nulll++;
                    continue;
                }
                //newLine.deleteCharAt(newLine.length() - 1);
                if (matSimRoutesSet.add(newLine.toString())) {
                    matsimMap.put(newLine.toString(), Double.parseDouble(splitLine[splitLine.length - 1]));
                    int um = (StringUtils.countMatches(newLine, ";") / 5)-1;
                    double tmp2 = demandMatsim[um];
                    demandMatsim[um] = tmp2 + Double.parseDouble(splitLine[splitLine.length - 1]);
                    int tmp = umsteigeMatsim[um];
                    umsteigeMatsim[um] = tmp + 1;
                } else {
                    double tmp = matsimMap.get(newLine.toString());
                    matsimMap.put(newLine.toString(), Double.parseDouble(splitLine[splitLine.length - 1]) + tmp);
                }
                matSimRoutesList.add(newLine.toString());
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        System.out.println("matsim list " + matSimRoutesList.size());
        System.out.println("matsim set " + matSimRoutesSet.size());

        try (BufferedReader reader = new BufferedReader(new FileReader(visum))) {
            List<String> header = List.of(reader.readLine().split(";"));
            String line;
            int index = 0;
            StringBuilder addLine = new StringBuilder();
            double demandtmp = 0;
            boolean first = true;
            while ((line = reader.readLine()) != null) {
                String[] splitline = line.split(";");
                if (index == Integer.parseInt(splitline[header.indexOf("PATHINDEX")])) {
                    addLine.append(splitline[header.indexOf("PATHLEGINDEX")]).append(";")
                        .append(splitline[header.indexOf("FROMSTOPPOINTNO")]).append(";")
                        .append(splitline[header.indexOf("TOSTOPPOINTNO")]).append(";")
                        .append(splitline[header.indexOf("LINEROUTENAME")]).append(";")
                        .append(splitline[header.indexOf("VEHJOURNEY_DEP")]).append(";");
                    demandtmp = Double.parseDouble(splitline[header.indexOf("ODTRIPS")]);
                } else {
                    if (first) {
                        first = false;
                    } else {
                        //addLine.deleteCharAt(addLine.length() - 1);
                        if (visumRoutesSet.add(addLine.toString())) {
                            visumMap.put(addLine.toString(), demandtmp);
                            int um = (StringUtils.countMatches(addLine, ";") / 5)-1;
                            int tmp = umsteigeVisum[um];
                            umsteigeVisum[um] = tmp + 1;
                            double tmp2 = demandVisum[um];
                            demandVisum[um] = tmp2 + Double.parseDouble(splitline[splitline.length - 1]);
                        } else {
                            double tmp = visumMap.get(addLine.toString());
                            visumMap.put(addLine.toString(), demandtmp + tmp);
                        }
                        visumRoutesList.add(addLine.toString());
                        var test = false;
                        var tmpLine = addLine.toString().split(";");
                        int last = 1;
                        for (int x = 0; x < (tmpLine.length+1)/5; x++) {
                            if (Integer.parseInt(tmpLine[x*5]) != last) {
                                test = true;
                            }
                            last++;
                        }
                        if (test) {
                            visumRoutesSet.remove(addLine.toString());
                            //visumMap.remove(addLine.toString());
                        }
                        addLine = new StringBuilder();

                    }
                    index = Integer.parseInt(splitline[header.indexOf("PATHINDEX")]);
                    addLine.append(splitline[header.indexOf("PATHLEGINDEX")]).append(";")
                        .append(splitline[header.indexOf("FROMSTOPPOINTNO")]).append(";")
                        .append(splitline[header.indexOf("TOSTOPPOINTNO")]).append(";")
                        .append(splitline[header.indexOf("LINEROUTENAME")]).append(";")
                        .append(splitline[header.indexOf("VEHJOURNEY_DEP")]).append(";");
                    demandtmp = Double.parseDouble(splitline[header.indexOf("ODTRIPS")]);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        System.out.println("visum list " + visumRoutesList.size());
        System.out.println("visum set " + visumRoutesSet.size());

        Set<String> beideSet = new HashSet<>();
        List<String> beideList = new ArrayList<>();
        int matsimRouteFoundInVisum = 0;
        int matsimRouteNotFoundInVisum = 0;
        for (String string : matSimRoutesSet) {
            if (visumRoutesSet.contains(string)) {
                int um2 = (StringUtils.countMatches(string, ";") / 5) -1;
                double tmp2 = demandbeides[um2];
                demandbeides[um2] = tmp2 + visumMap.get(string);
                matsimRouteFoundInVisum++;
                beideSet.add(string);
                beideList.add(string);
                int um1 = (StringUtils.countMatches(string, ";") / 5) -1;
                int tmp1 = umsteigebeides[um1];
                umsteigebeides[um1] = tmp1 + 1;
            } else {
                matsimRouteNotFoundInVisum++;
            }
        }

        System.out.println("matsimRouteFoundInVisum: " + matsimRouteFoundInVisum);
        System.out.println("matsimRouteNotFoundInVisum: " + matsimRouteNotFoundInVisum);

        System.out.println("beide list " + beideList.size());
        System.out.println("beide set " + beideSet.size());
        System.out.println("----------------------------------");
        System.out.println("Visum: " + Arrays.toString(umsteigeVisum));
        System.out.println("Matsim: " + Arrays.toString(umsteigeMatsim));
        System.out.println("beides: " + Arrays.toString(umsteigebeides));
        System.out.println("----------------------------------");

        for (String string : visumRoutesSet) {
            if (matSimRoutesSet.contains(string)) {

            } else {
                if (string.split(";").length < 6) {
                    //System.out.println(string);
                }
            }
        }

        double demand = 0;
        double totaldemand = 0;

        for (double tmp : matsimMap.values()) {
            totaldemand += tmp;
        }



        System.out.println("Visum: " + Arrays.toString(demandVisum));
        System.out.println("Matsim: " + Arrays.toString(demandMatsim));
        System.out.println("beides: " + Arrays.toString(demandbeides));
        System.out.println("----------------------------------");
        System.out.println("demandFoundInVisum: " + demand);
        System.out.println("totaldemandFoundInMATSim: " + totaldemand);

        demand = 0;
        double max = 0;
        String maxC = "";

        for (var entry : visumMap.entrySet()) {
            var start = entry.getKey().split(";")[1];
            var end = entry.getKey().split(";")[entry.getKey().split(";").length - 3];
            if (start.equals("1311") && end.equals("2194")) {
                demand += entry.getValue();
            }
            if (entry.getValue() > max && !matsimMap.containsKey(entry.getKey())) {
                maxC = entry.getKey();
                max = entry.getValue();
            }
        }
        System.out.println(demand);
        System.out.println(maxC);
        System.out.println(max);
        System.out.println("nulll " + nulll);
        System.out.println("Done");

    }
}
