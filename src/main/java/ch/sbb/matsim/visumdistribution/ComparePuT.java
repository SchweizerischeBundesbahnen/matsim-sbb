package ch.sbb.matsim.visumdistribution;

import java.io.BufferedReader;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * compare the links from visum and the srr also considers the demand on these, the solution is currently implemented very practical
 */
public class ComparePuT {

    public static void main(String[] args) {

        String visum = "Z:/99_Playgrounds/MD/Umlegung/visum/PathRoutesVisumLines.csv";
        String matsim = "Z:/99_Playgrounds/MD/Umlegung/alle/treeRoutesvLines.csv";

        Set<String> matSimRoutesSet = new HashSet<>();
        Set<String> visumRoutesSet = new HashSet<>();
        List<String> matSimRoutesList = new ArrayList<>();
        List<String> visumRoutesList = new ArrayList<>();

        Map<String, Double> matsimMap = new HashMap<>();
        Map<String, Double> visumMap = new HashMap<>();
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
                            newLine.append(tmp).append(";");
                        } else {
                            newLine.append(splitLine[i]).append(";");
                        }
                    }
                }
                if (newLine.length() == 0) {
                    continue;
                }
                //newLine.deleteCharAt(newLine.length() - 1);
                if (matSimRoutesSet.add(newLine.toString())) {
                    matsimMap.put(newLine.toString(), Double.parseDouble(splitLine[splitLine.length - 1]));
                } else {
                    double tmp = matsimMap.get(newLine.toString());
                    matsimMap.put(newLine.toString(), Double.parseDouble(splitLine[splitLine.length - 1]) + tmp);
                }
                matSimRoutesList.add(newLine.toString());
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

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

        System.out.println("matsim list: " + matSimRoutesList.size());
        System.out.println("visum list: " + visumRoutesList.size());
        System.out.println("matsim set: " + matSimRoutesSet.size());
        System.out.println("visum set: " + visumRoutesSet.size());

        System.out.println("----------------------------------");

        int beide = 0;
        int error = 0;
        int umsteige0 = 0;
        int umsteige1 = 0;
        int umsteige2 = 0;
        int umsteige3 = 0;
        int umsteige4 = 0;
        double demandGefunden = 0;
        double demandNichtGefunden = 0;
        double demand0 = 0;
        double demand1 = 0;
        double demand2 = 0;
        double demand3 = 0;
        double demand4 = 0;
        double demandMatsimTotal = 0;

        for (String line : visumRoutesSet) {
            if (!matSimRoutesSet.contains(line)) {
                demandNichtGefunden += visumMap.get(line);
                if (line.startsWith("2;")) {
                    error++;
                }
                if (line.contains(";5;")) {
                    umsteige4++;
                } else if (line.contains(";4;")) {
                    umsteige3++;
                } else if (line.contains(";3;")) {
                    umsteige2++;
                } else if (line.contains(";2;")) {
                    umsteige1++;
                } else  {
                    umsteige0++;
                }
            } else {
                demandGefunden += visumMap.get(line);
                beide++;
                demandMatsimTotal += matsimMap.get(line);
                if (line.startsWith("2;")) {
                    error++;
                }
                if (line.contains(";5;")) {
                    demand4 += matsimMap.get(line);
                } else if (line.contains(";4;")) {
                    demand3 += matsimMap.get(line);
                } else if (line.contains(";3;")) {
                    demand2 += matsimMap.get(line);
                } else if (line.contains(";2;")) {
                    demand1 += matsimMap.get(line);
                } else  {
                    demand0 += matsimMap.get(line);
                }
            }
        }

        System.out.println("beide: " + beide);
        System.out.println("Nachfrage gefunden : " + demandGefunden);
        System.out.println("Nachfrage nicht gefunden: " + demandNichtGefunden);
        System.out.println("----------------------------------");
        System.out.println("nicht gefunden error: " + error);
        System.out.println("nicht gefunden Umsteige 0: " + umsteige0);
        System.out.println("nicht gefunden Umsteige 1: " + umsteige1);
        System.out.println("nicht gefunden Umsteige 2: " + umsteige2);
        System.out.println("nicht gefunden Umsteige 3: " + umsteige3);
        System.out.println("nicht gefunden Umsteige 4: " + umsteige4);
        System.out.println("----------------------------------");
        System.out.println("matsim gefunden Demand total: " + demandMatsimTotal);
        System.out.println("matsim gefunden Demand 0: " + demand0);
        System.out.println("matsim gefunden Demand 1: " + demand1);
        System.out.println("matsim gefunden Demand 2: " + demand2);
        System.out.println("matsim gefunden Demand 3: " + demand3);
        System.out.println("matsim gefunden Demand >=4: " + demand4);
        System.out.println("----------------------------------");
    }
}
