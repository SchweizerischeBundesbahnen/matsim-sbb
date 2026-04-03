package ch.sbb.matsim.plans.tours;

import org.matsim.api.core.v01.population.Activity;
import org.matsim.api.core.v01.population.PlanElement;
import org.matsim.core.population.PopulationUtils;

import java.util.ArrayList;
import java.util.List;
/*
    * A class representing a tour, which can be a primary tour, secondary tour, or subtour.
    * It contains the main activity, inbound and outbound stop activities, and subtour activities.
    * The object is extended by each model step and finally converted into a list of PlanElements.
 */
public class Tour {

    public enum TourType {PRIMARY,SECONDARY,SUBTOUR}
    private final TourType tourType;
    private final List<Activity> inboundStopActivities = new ArrayList<>();
    private final List<Activity> outboundStopActivities = new ArrayList<>();
    private final List<Activity> subtourActivities = new ArrayList<>();
    private final List <Activity> mainActivities;
    private String maintourMode;
    private String subtourMode;
    private List<PlanElement> tourElements;

    public Tour(TourType tourType, String mainActivityType) {
        this.tourType = tourType;
        this.mainActivities = new ArrayList<>();
        this.mainActivities.add(PopulationUtils.getFactory().createActivityFromCoord(mainActivityType,null));
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
        return mainActivities.get(0).getType();
    }

    public String getMaintourMode() {
        return maintourMode;
    }

    public void setMaintourMode(String maintourMode) {
        this.maintourMode = maintourMode;
    }

    public String getSubtourMode() {
        return subtourMode;
    }

    public void setSubtourMode(String subtourMode) {
        this.subtourMode = subtourMode;
    }

    public List<Activity> getMainActivities() {
        return mainActivities;
    }
    public void setTourElements(List<PlanElement> planElements) {

    }

    public List<PlanElement> getTourElements() {
        return tourElements;
    }

    @Override
    public String toString() {
        return "Tour{" +
                "tourType=" + tourType +
                ", mainActivityType='" + getMainActivityType() + '\'' +
                ", inboundStopActivities=" + inboundStopActivities +
                ", outboundStopActivities=" + outboundStopActivities +
                ", subtourActivities=" + subtourActivities +

                '}';
    }
}
