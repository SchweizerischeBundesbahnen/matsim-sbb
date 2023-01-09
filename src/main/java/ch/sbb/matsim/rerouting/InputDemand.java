package ch.sbb.matsim.rerouting;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import omx.OmxFile;

public class InputDemand {

    private final List<Integer> codeList;
    private final OmxFile omxFile;
    private final int lastPosition;
    private final int lastDemandSegment;
    private int time = 1;
    private int xPosition = 0;
    private int yPosition = 0;

    InputDemand(String columNames, String nachfrageTag) {
        this.codeList = readColumNames(columNames);
        this.omxFile = readOMXMatrciesDayDemand(nachfrageTag);
        this.lastPosition = this.codeList.size();
        this.lastDemandSegment = this.omxFile.getMatrixNames().size();
    }

    private static List<Integer> readColumNames(String file) {
        List<Integer> codeList = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            List<String> header = List.of(reader.readLine().split(";"));
            String line;
            while ((line = reader.readLine()) != null) {
                codeList.add(Integer.parseInt(line.split(";")[header.indexOf("id")]));
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return codeList;
    }

    private static OmxFile readOMXMatrciesDayDemand(String file) {
        OmxFile omxFile = new OmxFile(file);
        omxFile.openReadOnly();
        omxFile.summary();
        return omxFile;
    }

    public int getLastPosition() {
        return lastPosition;
    }

    public int getLastDemandSegment() {
        return lastDemandSegment;
    }

    public List<Integer> getCodeList() {
        return codeList;
    }

    public OmxFile getOmxFile() {
        return omxFile;
    }

    public int getTime() {
        return time;
    }

    public int getXPosition() {
        return xPosition;
    }

    public int getYPosition() {
        return yPosition;
    }

    public void increaseTime() {
        this.time++;
    }

    public void increaseXPosition() {
        this.xPosition++;
    }

    public void increaseYPosition() {
        this.yPosition++;
    }

    public void resetXPosition() {
        this.xPosition = 0;
    }

    public void resetYPosition() {
        this.yPosition = 0;
    }
}
