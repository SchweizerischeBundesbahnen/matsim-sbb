package ch.sbb.matsim.rerouting;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import omx.OmxFile;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.pt.transitSchedule.api.TransitStopFacility;

public class InputDemand {
    private final OmxFile omxFile;
    private final Map<Integer, Coord> validPosistions;
    private final List<Integer> timeList = new ArrayList<>();

    InputDemand(String columNames, String nachfrageTag, Scenario scenario) {
        this.omxFile = readOMXMatrciesDayDemand(nachfrageTag);
        Map<Integer, Coord> assignmentMap = readAssignment(columNames, scenario);
        this.validPosistions = searchValidStationsPosition((int[]) omxFile.getLookup("NO").getLookup(), assignmentMap);
        for (int i = 1; i < this.omxFile.getMatrixNames().size(); i++) {
            timeList.add(i);
        }
    }

    private Map<Integer, Coord> searchValidStationsPosition(int[] lookup, Map<Integer, Coord> assignmentMap) {
        Map<Integer, Coord> validPostions = new HashMap<>();
        for (int i = 0; i < lookup.length; i++) {
            if (assignmentMap.containsKey(lookup[i])) {
                validPostions.put(i, assignmentMap.get(lookup[i]));
            }
        }
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
        OmxFile omxFile = new OmxFile(file);
        omxFile.openReadOnly();
        omxFile.summary();
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
}
