package ch.sbb.matsim.plans.models.steps;

import ch.sbb.matsim.plans.MOBiPlansRunner;
import jakarta.inject.Inject;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.population.Activity;
import org.matsim.api.core.v01.population.Plan;
import org.matsim.api.core.v01.population.PlanElement;
import org.matsim.core.utils.geometry.CoordUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class AddAdditionalTripVariables implements ModelStep {

    private final Scenario scenario;

    @Inject
    public AddAdditionalTripVariables(Scenario scenario) {
        this.scenario = scenario;
    }

    @Override
    public String getName() {
        return MOBiPlansRunner.ADDITIONAL_VARIABLES;
    }

    @Override
    public void run() {
        scenario.getPopulation().getPersons().values().forEach(person -> {
            Plan plan = person.getSelectedPlan();
            if (plan != null) {
                // 1. Extract all activities sequentially
                List<Activity> activities = new ArrayList<>();
                for (PlanElement planElement : plan.getPlanElements()) {
                    if (planElement instanceof Activity activity) {
                        activities.add(activity);
                    }
                }

                if (activities.size() < 2) return;

                // 2. Identify Main Tours (Home to Home segments)
                List<MainTour> mainTours = new ArrayList<>();
                int currentStart = 0;
                for (int i = 1; i < activities.size(); i++) {
                    if (isHome(activities.get(i))) {
                        mainTours.add(new MainTour(currentStart, i));
                        currentStart = i; // Next tour starts where the current one ends
                    }
                }
                // Handle remaining activities as an open tour
                if (currentStart < activities.size() - 1) {
                    mainTours.add(new MainTour(currentStart, activities.size() - 1));
                }

                // Determine Main Tour purposes
                for (MainTour mt : mainTours) {
                    boolean hasWork = false;
                    boolean hasBusiness = false;
                    for (int i = mt.start; i <= mt.end; i++) {
                        String type = activities.get(i).getType();
                        if (type != null) {
                            type = type.toLowerCase();
                            if (type.contains("work")) hasWork = true;
                            if (type.contains("business")) hasBusiness = true;
                        }
                    }
                    if (hasWork) mt.purpose = "Pe";
                    else if (hasBusiness) mt.purpose = "Ge";
                    else mt.purpose = "Fr";
                }

                // 3. Identify Subtours (work-xxx-work or educ-xxx-educ)
                List<Subtour> subtours = new ArrayList<>();
                for (int i = 0; i < activities.size(); i++) {
                    String typeI = getAnchorType(activities.get(i));
                    if (typeI == null) continue;

                    for (int j = i + 1; j < activities.size(); j++) {
                        // A subtour cannot cross or include a home activity. Break immediately.
                        if (isHome(activities.get(j))) {
                            break;
                        }

                        String typeJ = getAnchorType(activities.get(j));
                        if (typeI.equals(typeJ)) {
                            // Only form a subtour if there is actually at least one activity in between
                            if (j > i + 1) {
                                subtours.add(new Subtour(i, j));
                            }
                            break; // Stop at the nearest matching anchor to form the subtour bounds
                        }
                    }
                }
                // Smallest subtours first to handle nesting correctly (e.g. educ-shop-educ inside a larger work-xxx-work subtour)
                subtours.sort(Comparator.comparingInt(s -> (s.end - s.start)));

                // 4. Process each trip (transition from activity t to t+1)
                for (int t = 0; t < activities.size() - 1; t++) {
                    Activity currentAct = activities.get(t);
                    Activity nextAct = activities.get(t + 1);

                    // Determine if the current trip is inside a subtour or main tour
                    Subtour activeSubtour = null;
                    for (Subtour st : subtours) {
                        if (t >= st.start && t < st.end) {
                            activeSubtour = st;
                            break;
                        }
                    }

                    Activity anchorAct;
                    String tripPurpose;

                    if (activeSubtour != null) {
                        anchorAct = activities.get(activeSubtour.start);
                        tripPurpose = "Fr"; // Subtours are always strictly 'Fr'
                    } else {
                        MainTour activeMain = null;
                        for (MainTour mt : mainTours) {
                            if (t >= mt.start && t < mt.end) {
                                activeMain = mt;
                                break;
                            }
                        }
                        if (activeMain != null) {
                            // Find the home anchor for the main tour
                            if (isHome(activities.get(activeMain.start))) {
                                anchorAct = activities.get(activeMain.start);
                            } else if (isHome(activities.get(activeMain.end))) {
                                anchorAct = activities.get(activeMain.end);
                            } else {
                                anchorAct = activities.get(activeMain.start);
                            }
                            tripPurpose = activeMain.purpose;
                        } else {
                            anchorAct = activities.get(0);
                            tripPurpose = "Fr";
                        }
                    }

                    // 5. Compare distances to define direction state
                    String direction = "outbound";
                    if (anchorAct.getCoord() != null && currentAct.getCoord() != null && nextAct.getCoord() != null) {
                        double distCurrent = CoordUtils.calcEuclideanDistance(anchorAct.getCoord(), currentAct.getCoord());
                        double distNext = CoordUtils.calcEuclideanDistance(anchorAct.getCoord(), nextAct.getCoord());

                        // If the next leg's destination is closer to the anchor, it's inbound
                        if (distNext < distCurrent) {
                            direction = "inbound";
                        }
                    }

                    // 6. Set calculated variables
                    currentAct.getAttributes().putAttribute("next_trip_direction", direction);
                    currentAct.getAttributes().putAttribute("next_trip_purpose", tripPurpose);
                }

                // Clean-up: The very last activity of a plan has no "next_trip".
                Activity lastAct = activities.get(activities.size() - 1);
                lastAct.getAttributes().removeAttribute("next_trip_direction");
                lastAct.getAttributes().removeAttribute("next_trip_purpose");
            }
        });
    }

    private boolean isHome(Activity act) {
        return act.getType() != null && act.getType().toLowerCase().contains("home");
    }

    private String getAnchorType(Activity act) {
        if (act.getType() == null) return null;
        String type = act.getType().toLowerCase();
        if (type.contains("work")) return "work";
        if (type.contains("educ")) return "educ";
        return null;
    }

    private static class MainTour {
        final int start;
        final int end;
        String purpose;

        MainTour(int start, int end) {
            this.start = start;
            this.end = end;
        }
    }

    private static class Subtour {
        final int start;
        final int end;

        Subtour(int start, int end) {
            this.start = start;
            this.end = end;
        }
    }
}