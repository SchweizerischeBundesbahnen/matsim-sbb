package ch.sbb.matsim.rerouting2.compareroutes;

import ch.sbb.matsim.rerouting2.compareroutes.PartRoutes.Stops;
import java.io.BufferedReader;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.commons.collections4.SetValuedMap;
import org.apache.commons.lang3.StringUtils;
import scala.collection.Factory.StringFactory;

public class CompareRoutes {

    public static void main(String[] args) {

        Set<String> matSimRoutesSet = new HashSet<>();
        Map<String, Double> matSimRoutesMap = new HashMap<>();
        List<String> matSimRoutesList = new ArrayList<>();

        /*try (BufferedReader reader = new BufferedReader(new FileReader("treeRoutes.csv"))) {
            List<String> header = List.of(reader.readLine().split(";"));
            String line;
            int index = 0;
            int count = 0;
            PartRoutes matSimRoute = null;
            while ((line = reader.readLine()) != null) {
                String[] splitline = line.split(";");
                if (index == Integer.parseInt(splitline[header.indexOf("PATHINDEX")])) {
                    matSimRoute.addStop(new Stops(Integer.parseInt(splitline[header.indexOf("DEPTIME")]),
                        Integer.parseInt(splitline[header.indexOf("ARRTIME")]),
                        Integer.parseInt(splitline[header.indexOf("FROMSTOPPOINTNO")]),
                        Integer.parseInt(splitline[header.indexOf("TOSTOPPOINTNO")])));
                } else {
                    if (matSimRoute != null) {
                       if(!matSimRoutesList1.add(matSimRoute)){
                           count++;
                       }
                    }
                    index = Integer.parseInt(splitline[header.indexOf("PATHINDEX")]);
                    matSimRoute = new PartRoutes(new Stops(Integer.parseInt(splitline[header.indexOf("DEPTIME")]),
                        Integer.parseInt(splitline[header.indexOf("ARRTIME")]),
                        Integer.parseInt(splitline[header.indexOf("FROMSTOPPOINTNO")]),
                        Integer.parseInt(splitline[header.indexOf("TOSTOPPOINTNO")])));
                }
            }
            System.out.println(count + " routes existed");
            System.out.println(matSimRoutesList1.size() + " unique routes");
        } catch (Exception e) {
            e.printStackTrace();
        }
         */

        try (BufferedReader reader = new BufferedReader(new FileReader("treeRoutesv2.csv"))) {
            String line;
            double demand = 0;
            while ((line = reader.readLine()) != null) {
                String[] splitLine = line.split(";");
                //String stringDemand = splitLine[splitLine.length-1];
                //demand = Double.parseDouble(stringDemand);
                line = line.substring(0, line.length() - 1);
                matSimRoutesSet.add(line);
                if (matSimRoutesMap.containsKey(line)) {
                    double tmp = matSimRoutesMap.get(line);
                    demand += tmp;
                    matSimRoutesMap.put(line, demand);
                } else {
                    matSimRoutesMap.put(line, demand);
                }
                matSimRoutesList.add(line);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        Set<String> visumRoutesSet = new HashSet<>();
        Map<String, Double> visumRoutesMap = new HashMap<>();
        List<String> visumRoutesList = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new FileReader("PathRoutesVisumDep.csv"))) {
            List<String> header = List.of(reader.readLine().split(";"));
            String line;
            int index = 0;
            String addLine = "";
            double demand = 0;
            double count = 0;
            boolean first = true;
            while ((line = reader.readLine()) != null) {
                String[] splitline = line.split(";");
                if (index == Integer.parseInt(splitline[header.indexOf("PATHINDEX")])) {
                    addLine = addLine +";" + splitline[header.indexOf("PATHLEGINDEX")] + ";" +
                        splitline[header.indexOf("FROMSTOPPOINTNO")] + ";" +
                        splitline[header.indexOf("TOSTOPPOINTNO")] + ";" +
                        splitline[header.indexOf("VEHJOURNEY-DEP")];
                    //demand += Double.parseDouble(splitline[header.indexOf("ODTRIPS")]);
                } else {
                    if (first) {
                        first = false;
                    } else {
                        visumRoutesSet.add(addLine);
                        visumRoutesList.add(addLine);
                        if (visumRoutesMap.containsKey(addLine)) {
                            double tmp = visumRoutesMap.get(addLine);
                            demand += tmp;
                            visumRoutesMap.put(addLine, demand);
                            count++;
                        } else {
                            visumRoutesMap.put(addLine, demand);
                        }
                        addLine = "";
                        demand = 0;
                    }
                    index = Integer.parseInt(splitline[header.indexOf("PATHINDEX")]);
                    addLine = addLine + splitline[header.indexOf("PATHLEGINDEX")] + ";" +
                        splitline[header.indexOf("FROMSTOPPOINTNO")] + ";" +
                        splitline[header.indexOf("TOSTOPPOINTNO")] + ";" +
                        splitline[header.indexOf("VEHJOURNEY-DEP")];
                    demand += Double.parseDouble(splitline[header.indexOf("ODTRIPS")]);
                }
            }
            System.out.println(count);
            System.out.println(visumRoutesList.size());
        } catch (Exception e) {
            e.printStackTrace();
        }
        /*try (BufferedReader reader = new BufferedReader(new FileReader("PathRoutesVisumDep.csv"))) {
            List<String> header = List.of(reader.readLine().split(";"));
            String line;
            int index = 0;
            String addLine = "";
            boolean first = true;
            while ((line = reader.readLine()) != null) {
                String[] splitline = line.split(";");
                if (index == Integer.parseInt(splitline[header.indexOf("PATHINDEX")])) {
                    addLine = addLine +";" + splitline[header.indexOf("PATHLEGINDEX")] + ";" +
                        splitline[header.indexOf("FROMSTOPPOINTNO")] + ";" +
                        splitline[header.indexOf("TOSTOPPOINTNO")] + ";" +
                        splitline[header.indexOf("DEPTIME")] + ";" +
                        splitline[header.indexOf("ARRTIME")];
                } else {
                    if (first) {
                        first = false;
                    } else {
                        visumRoutesSet.add(addLine);
                        visumRoutesList.add(addLine);
                        addLine = "";
                    }
                    index = Integer.parseInt(splitline[header.indexOf("PATHINDEX")]);
                    addLine = addLine + splitline[header.indexOf("PATHLEGINDEX")] + ";" +
                        splitline[header.indexOf("FROMSTOPPOINTNO")] + ";" +
                        splitline[header.indexOf("TOSTOPPOINTNO")] + ";" +
                        splitline[header.indexOf("DEPTIME")] + ";" +
                        splitline[header.indexOf("ARRTIME")];
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }*/

        int matsimRouteFoundInVisum = 0;
        int matsimRouteNotFoundInVisum = 0;
        int visumRouteFoundInMatsim = 0;
        int visumRouteNotFoundInMatsim = 0;
        double foundDemand = 0;
        double notFoundDemand = 0;
        double totaldemand = 0;

        int[] umsteigeVisum = new int[20];
        int[] umsteigeMatsim = new int[20];
        int[] umsteigebeides = new int[20];

        for (String string : matSimRoutesSet) {
            int um = StringUtils.countMatches(string, ";")/4;
            int tmp = umsteigeMatsim[um];
            umsteigeMatsim[um] = tmp+1;
            if (um == 17) {
                System.out.println(string);
            }
            totaldemand += matSimRoutesMap.get(string);
            if (visumRoutesSet.contains(string)) {
                matsimRouteFoundInVisum++;
                foundDemand += visumRoutesMap.get(string);
                notFoundDemand += matSimRoutesMap.get(string);
                int um1 = StringUtils.countMatches(string, ";")/4;
                int tmp1 = umsteigebeides[um1];
                umsteigebeides[um1] = tmp1+1;
            } else {
                matsimRouteNotFoundInVisum++;
            }
        }


        for (String string : visumRoutesSet) {
            int um = StringUtils.countMatches(string, ";")/4;
            int tmp = umsteigeVisum[um];
            umsteigeVisum[um] = tmp+1;
            //totaldemand += visumRoutesMap.get(string);
            if (matSimRoutesSet.contains(string)) {
                visumRouteFoundInMatsim++;
            } else {
                visumRouteNotFoundInMatsim++;
                //System.out.println(string);
            }
        }
        System.out.println("----------------------------------");
        System.out.println("Unique routes in MATSim: " + matSimRoutesSet.size());
        System.out.println("Unique routes in Visum: " + visumRoutesSet.size());
        System.out.println("matsimRouteFoundInVisum: " + matsimRouteFoundInVisum);
        System.out.println("foundDemand: " + foundDemand);
        System.out.println("matsimRouteNotFoundInVisum: " + matsimRouteNotFoundInVisum);
        System.out.println("matsim: " + notFoundDemand);
        System.out.println("total matsim: " + totaldemand);
        System.out.println("visumRouteFoundInMatsim: " + visumRouteFoundInMatsim);
        System.out.println("visumRouteNotFoundInMatsim: " + visumRouteNotFoundInMatsim);


        System.out.println("----------------------------------");
        System.out.println("Visum: " + Arrays.toString(umsteigeVisum));
        System.out.println("Matsim: " + Arrays.toString(umsteigeMatsim));
        System.out.println("beides: " + Arrays.toString(umsteigebeides));
        System.out.println("----------------------------------");



    }

}
