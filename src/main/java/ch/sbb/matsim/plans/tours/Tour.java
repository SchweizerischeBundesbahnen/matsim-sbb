package ch.sbb.matsim.plans.tours;

import org.matsim.api.core.v01.population.Activity;

import java.util.ArrayList;
import java.util.List;

public class Tour {
public enum TourType {PRIMARY,SECONDARY,SUBTOUR}
    protected final TourType tourType;
    protected  String mainActivityType;
    protected final List<Activity> inboundStopActivities = new ArrayList<>();
    protected final List<Activity> outboundStopActivities = new ArrayList<>();
    protected final List<Activity> subtourActivities = new ArrayList<>();


    public Tour(TourType tourType, String mainActivityType) {
        this.tourType = tourType;
        this.mainActivityType = mainActivityType;
    }

    public TourType getTourType() {
        return tourType;
    }

    public List<Activity> getInboundStopActivities() {
        return inboundStopActivities;
    }

    public List<Activity> getOutboundStopActivities() {
        return outboundStopActivities;
    }

    public List<Activity> getSubtourActivities() {
        return subtourActivities;
    }

    public String getMainActivityType() {
        return mainActivityType;
    }

    public void setMainActivityType(String mainActivityType) {
        this.mainActivityType = mainActivityType;
    }

    @Override
    public String toString() {
        return "Tour{" +
                "tourType=" + tourType +
                ", mainActivityType='" + mainActivityType + '\'' +
                ", inboundStopActivities=" + inboundStopActivities +
                ", outboundStopActivities=" + outboundStopActivities +
                ", subtourActivities=" + subtourActivities +
                '}';
    }
}
