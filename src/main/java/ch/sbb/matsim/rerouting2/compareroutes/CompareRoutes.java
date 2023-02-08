package ch.sbb.matsim.rerouting2.compareroutes;

import ch.sbb.matsim.rerouting2.compareroutes.PartRoutes.Stops;
import java.io.BufferedReader;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class CompareRoutes {

    public static void main(String[] args) {

        Set<String> matSimRoutesSet = new HashSet<>();
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

        try (BufferedReader reader = new BufferedReader(new FileReader("treeRoutes.csv"))) {
            String line;
            while ((line = reader.readLine()) != null) {
                matSimRoutesSet.add(line);
                matSimRoutesList.add(line);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        Set<String> visumRoutesSet = new HashSet<>();
        List<String> visumRoutesList = new ArrayList<>();

        try (BufferedReader reader = new BufferedReader(new FileReader("PathRoutesVisum.csv"))) {
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
        }

        int matsimRouteFoundInVisum = 0;
        int matsimRouteNotFoundInVisum = 0;
        int visumRouteFoundInMatsim = 0;
        int visumRouteNotFoundInMatsim = 0;
        for (String string : matSimRoutesSet) {
            if (visumRoutesSet.contains(string)) {
                matsimRouteFoundInVisum++;
            } else {
                matsimRouteNotFoundInVisum++;
            }
        }


        for (String string : visumRoutesSet) {
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
        System.out.println("matsimRouteNotFoundInVisum: " + matsimRouteNotFoundInVisum);
        System.out.println("visumRouteFoundInMatsim: " + visumRouteFoundInMatsim);
        System.out.println("visumRouteNotFoundInMatsim: " + visumRouteNotFoundInMatsim);


        int caFound = 0;
        int caNotFound = 0;
        System.out.println("----------------------------------");
        for (String string : visumRoutesSet) {
            String[] splitVisum = string.split(";");
            for (String string2 : matSimRoutesSet) {
                String[] splitMatsim = string2.split(";");
                if (string.length() != string2.length()) {
                    caNotFound++;
                    continue;
                }
                boolean found = false;
                for (int lenght = 0; lenght < splitVisum.length/5; lenght++) {
                    String part1 = splitVisum[5 * lenght];
                    String part2 = splitVisum[1 + 5 * lenght];
                    String part3 = splitVisum[2 + 5 * lenght];
                    int part4 = Integer.parseInt(splitVisum[3 + 5 * lenght]);
                    int part5 = Integer.parseInt(splitVisum[4 + 5 * lenght]);
                    String part11 = splitMatsim[5 * lenght];
                    String part22 = splitMatsim[1 + 5 * lenght];
                    String part33 = splitMatsim[2 + 5 * lenght];
                    int part44 = Integer.parseInt(splitMatsim[3 + 5 * lenght]);
                    int part55 = Integer.parseInt(splitMatsim[4 + 5 * lenght]);
                    if (!part1.equals(part11)) {
                        break;
                    }
                    if (!part2.equals(part22)) {
                        break;
                    }
                    if (!part3.equals(part33)) {
                        break;
                    }
                    if (!(part44 - 60 <=  part4 && part4 <= part44 + 60)) {
                        break;
                    }
                    if (!(part55 - 60 <=  part5 && part5 <= part55 + 60)) {
                        break;
                    }
                    if (lenght == splitVisum.length/5 - 1) {
                        found = true;
                    }
                }
                if (found) {
                    caFound++;
                } else {
                    caNotFound++;
                }
            }
        }
        System.out.println("caFound: " + caFound);
        System.out.println("caNotFound: " + caNotFound);
        /*try (BufferedReader reader = new BufferedReader(new FileReader("PathRoutesVisum.csv"))) {
            List<String> header = List.of(reader.readLine().split(";"));
            String line;
            int index = 0;
            int count = 0;
            PartRoutes matSimRoute = null;
            while ((line = reader.readLine()) != null) {
                String[] splitline = line.split(";");
                if (splitline[header.indexOf("ARRTIME")].equals("") ||
                    splitline[header.indexOf("DEPTIME")].equals("") ||
                    splitline[header.indexOf("FROMSTOPPOINTNO")].equals("") ||
                    splitline[header.indexOf("TOSTOPPOINTNO")].equals("")) {
                count++;
                continue;
                }
                if (index == Integer.parseInt(splitline[header.indexOf("PATHINDEX")])) {
                    matSimRoute.addStop(new Stops(Integer.parseInt(splitline[header.indexOf("DEPTIME")]),
                        Integer.parseInt(splitline[header.indexOf("ARRTIME")]),
                        Integer.parseInt(splitline[header.indexOf("FROMSTOPPOINTNO")]),
                        Integer.parseInt(splitline[header.indexOf("TOSTOPPOINTNO")])));
                } else {
                    if (matSimRoute != null) {
                        if(!matSimRoutesList2.add(matSimRoute)){
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
            System.out.println(matSimRoutesList2.size() + " unique routes");
        } catch (Exception e) {
            e.printStackTrace();
        }
        */


    }

}
