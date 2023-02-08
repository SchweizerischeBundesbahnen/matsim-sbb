package ch.sbb.matsim.rerouting2.compareroutes;

import java.util.ArrayList;
import java.util.List;

public class PartRoutes {

    List<Stops> stopList = new ArrayList<>();

    public PartRoutes(Stops stop) {
        stopList.add(stop);
    }

    public void addStop(Stops stop) {
        stopList.add(stop);
    }

    @Override
    public String toString() {
        StringBuilder stopsString = new StringBuilder();
        for (Stops stop : stopList) {
            stopsString.append("; ");
            stopsString.append(stop.toString());
        }
        return "StopListSize: " + stopList.size() + stopsString;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        PartRoutes that = (PartRoutes) o;
        return this.toString().equals(that.toString());
    }

    @Override
    public int hashCode() {
        int hash = stopList.size()+2;
        for (Stops stops : stopList) {
            hash = 31 * hash + stops.startPoint + stops.endPoint - stops.endTime - stops.startTime;
        }
        return hash;
    }

    static class Stops {

        int startTime;
        int endTime;
        int startPoint;
        int endPoint;

        Stops(int startTime, int endTime, int startPoint, int endPoint) {
            this.startTime = startTime;
            this.endTime = endTime;
            this.startPoint = startPoint;
            this.endPoint = endPoint;
        }

        @Override
        public String toString() {
            return "StartPoint: " + startPoint + "; EndPoint: " + endPoint + "; StartTime: " + startTime + "; EndTime: " + endTime;
        }

    }

}
