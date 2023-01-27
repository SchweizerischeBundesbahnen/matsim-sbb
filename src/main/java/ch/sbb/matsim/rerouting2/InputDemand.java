package ch.sbb.matsim.rerouting2;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import omx.OmxFile;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.facilities.ActivityFacilitiesFactory;
import org.matsim.facilities.ActivityFacility;
import org.matsim.facilities.FacilitiesWriter;
import org.matsim.pt.transitSchedule.api.TransitStopFacility;

public class InputDemand {

    private final OmxFile omxFile;
    private final Map<Integer, Coord> validPosistions;
    private final List<Integer> timeList = new ArrayList<>();
    private double missingDemand;

    InputDemand(String columNames, String nachfrageTag, Scenario scenario) {
        long startTime = System.nanoTime();
        this.omxFile = readOMXMatrciesDayDemand(nachfrageTag);
        Map<Integer, Coord> assignmentMap = readAssignment(columNames, scenario);
        //createFacilities(assignmentMap);
        this.validPosistions = searchValidStationsPosition((int[]) omxFile.getLookup("NO").getLookup(), assignmentMap);
        for (int i = 1; i < this.omxFile.getMatrixNames().size(); i++) {
            timeList.add(i);
        }
        System.out.println("Input demand: " + ((System.nanoTime() - startTime) / 1_000_000_000) + "s");
    }

    private void createFacilities(Map<Integer, Coord> assignmentMap) {
        Config config = ConfigUtils.createConfig();
        Scenario scenario = ScenarioUtils.createScenario(config);
        ActivityFacilitiesFactory f= scenario.getActivityFacilities().getFactory();
        for (Entry<Integer, Coord> entry : assignmentMap.entrySet()) {
            scenario.getActivityFacilities().addActivityFacility(f.createActivityFacility(Id.create(entry.getKey(), ActivityFacility.class), entry.getValue()));
        }
        new FacilitiesWriter(scenario.getActivityFacilities()).write("Z:/99_Playgrounds/MD/Umlegung/Input/facilities/facilities.xml");
    }

    private Map<Integer, Coord> searchValidStationsPosition(int[] lookup, Map<Integer, Coord> assignmentMap) {
        Map<Integer, Coord> validPostions = new HashMap<>();
        double missingDemand = 0;
        for (int i = 0; i < lookup.length; i++) {
            if (assignmentMap.containsKey(lookup[i])) {
                validPostions.put(i, assignmentMap.get(lookup[i]));
            } else {
                for (int time = 1; time < this.omxFile.getMatrixNames().size(); time++) {
                    double[][] matrix = (double[][]) omxFile.getMatrix(String.valueOf(time)).getData();
                    for (int y = 0; y < matrix.length; y++) {
                        missingDemand += matrix[i][y];
                        missingDemand += matrix[y][i];
                    }
                }
            }
        }
        System.out.println("Missing demand becaus auf invalid stations: " + missingDemand);
        this.missingDemand = missingDemand;
        return validPostions;
    }

    private Map<Integer, Coord> readAssignment(String file, Scenario scenario) {
        Map<Integer, Coord> assignmentMap = new HashMap<>();
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            List<String> header = List.of(reader.readLine().split(";"));
            String line;
            while ((line = reader.readLine()) != null) {
                String[] splitLine = line.split(";");
                if (scenario.getTransitSchedule().getFacilities().get(Id.create(splitLine[1], TransitStopFacility.class)) == null) {
                    continue;
                }
                Coord coord = assignmentMap.get(Integer.parseInt(splitLine[0]));
                if (coord == null) {
                    assignmentMap.put(Integer.parseInt(splitLine[0]), scenario.getTransitSchedule().getFacilities().get(Id.create(splitLine[1], TransitStopFacility.class)).getCoord());
                } else {
                    assignmentMap.put(Integer.parseInt(splitLine[0]),
                        new Coord((scenario.getTransitSchedule().getFacilities().get(Id.create(splitLine[1], TransitStopFacility.class)).getCoord().getX() + coord.getX()) / 2,
                            (scenario.getTransitSchedule().getFacilities().get(Id.create(splitLine[1], TransitStopFacility.class)).getCoord().getY() + coord.getY()) / 2));
                }
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return assignmentMap;
    }

    private OmxFile readOMXMatrciesDayDemand(String file) {
        long startTime = System.nanoTime();
        OmxFile omxFile = new OmxFile(file);
        omxFile.openReadOnly();
        omxFile.summary();
        System.out.println("Read omx file: " + ((System.nanoTime() - startTime) / 1_000_000_000) + "s");
        return omxFile;
    }

    public OmxFile getOmxFile() {
        return omxFile;
    }

    public List<Integer> getTimeList() {
        return timeList;
    }

    public Map<Integer, Coord> getValidPosistions() {
        return validPosistions;
    }

    public double getMissingDemand() {
        return missingDemand;
    }
}
