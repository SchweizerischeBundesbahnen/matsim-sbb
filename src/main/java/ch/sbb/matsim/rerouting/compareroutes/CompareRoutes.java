package ch.sbb.matsim.rerouting.compareroutes;

import ch.sbb.matsim.rerouting.compareroutes.MATSimRoute.Stops;
import java.io.BufferedReader;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class CompareRoutes {

    public static void main(String[] args) {

        Set<MATSimRoute> matSimRoutesList = new HashSet<>();

        try (BufferedReader reader = new BufferedReader(new FileReader("treeRoutes.csv"))) {
            List<String> header = List.of(reader.readLine().split(";"));
            String line;
            int index = 0;
            int count = 0;
            MATSimRoute matSimRoute = null;
            while ((line = reader.readLine()) != null) {
                String[] splitline = line.split(";");
                if (index == Integer.parseInt(splitline[header.indexOf("PATHINDEX")])) {
                    matSimRoute.addStop(new Stops(Integer.parseInt(splitline[header.indexOf("DEPTIME")]),
                        Integer.parseInt(splitline[header.indexOf("ARRTIME")]),
                        Integer.parseInt(splitline[header.indexOf("FROMSTOPPOINTNO")]),
                        Integer.parseInt(splitline[header.indexOf("TOSTOPPOINTNO")])));
                } else {
                    if (matSimRoute != null) {
                       if(!matSimRoutesList.add(matSimRoute)){
                           count++;
                       }
                    }
                    index = Integer.parseInt(splitline[header.indexOf("PATHINDEX")]);
                    matSimRoute = new MATSimRoute(new Stops(Integer.parseInt(splitline[header.indexOf("DEPTIME")]),
                        Integer.parseInt(splitline[header.indexOf("ARRTIME")]),
                        Integer.parseInt(splitline[header.indexOf("FROMSTOPPOINTNO")]),
                        Integer.parseInt(splitline[header.indexOf("TOSTOPPOINTNO")])));
                }
            }
            System.out.println(count + " routes existed");
            System.out.println(matSimRoutesList.size() + " unique routes");
        } catch (Exception e) {
            e.printStackTrace();
        }

        try (BufferedReader reader = new BufferedReader(new FileReader("modell_2028_DWV_putPathLegs_reduced.csv"))) {
            List<String> header = List.of(reader.readLine().split(";"));
            String line;
            int index = 0;
            int count = 0;
            MATSimRoute matSimRoute = null;
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
                        if(!matSimRoutesList.add(matSimRoute)){
                            count++;
                        }
                    }
                    index = Integer.parseInt(splitline[header.indexOf("PATHINDEX")]);
                    matSimRoute = new MATSimRoute(new Stops(Integer.parseInt(splitline[header.indexOf("DEPTIME")]),
                        Integer.parseInt(splitline[header.indexOf("ARRTIME")]),
                        Integer.parseInt(splitline[header.indexOf("FROMSTOPPOINTNO")]),
                        Integer.parseInt(splitline[header.indexOf("TOSTOPPOINTNO")])));
                }
            }
            System.out.println(count + " routes existed");
            System.out.println(matSimRoutesList.size() + " unique routes");
        } catch (Exception e) {
            e.printStackTrace();
        }

        System.out.println("Done");

    }

}
