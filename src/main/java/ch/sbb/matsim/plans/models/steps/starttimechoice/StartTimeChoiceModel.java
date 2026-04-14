package ch.sbb.matsim.plans.models.steps.starttimechoice;

import ch.sbb.matsim.config.variables.SBBActivities;
import ch.sbb.matsim.plans.MOBiPlansRunner;
import ch.sbb.matsim.plans.constants.SynpopConstants;
import ch.sbb.matsim.plans.models.config.MOBiPlansConfig;
import ch.sbb.matsim.plans.models.steps.InitialModeChoiceModel;
import ch.sbb.matsim.plans.models.steps.ModelStep;
import ch.sbb.matsim.plans.models.steps.PopulationSampler;
import ch.sbb.matsim.plans.synpop.SynPopUtils;
import ch.sbb.matsim.plans.tours.Tour;
import jakarta.inject.Inject;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.population.*;
import org.matsim.core.population.PopulationUtils;

import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ForkJoinPool;
import java.util.function.BiPredicate;

public class StartTimeChoiceModel implements ModelStep {
   private final Scenario scenario;
   private final MOBiPlansConfig mobiPlansConfig;
   private final Map<String, List<TimeSeriesRule>> rulesMap;

   @Inject
    public StartTimeChoiceModel(Scenario scenario, MOBiPlansConfig mobiPlansConfig) {
        this.scenario = scenario;
        this.mobiPlansConfig = mobiPlansConfig;
        this.rulesMap = StartTimeChoiceConfigParser.parse(Paths.get(mobiPlansConfig.data().modelBaseDir()).resolve(MOBiPlansConfig.MODEL_BASE_FOLDER_CHOICE_MODELS).resolve(MOBiPlansConfig.TIME_OF_DAY_CHOICE_CSV).toString());

    }

    @Override
    public String getName() {
        return MOBiPlansRunner.START_TIME_CHOICE;
    }

    @Override
    public void run() {
        try (ForkJoinPool customPool = new ForkJoinPool(mobiPlansConfig.models().threads())){
            customPool.submit(() ->
                    scenario.getPopulation().getPersons().values().parallelStream()
                            .filter(person -> new PopulationSampler.ActivePersonPredicate().test(person)) // Only process active persons
                            .forEach(this::processPerson)
            ).get();
        } catch (Exception e) {
            throw new RuntimeException("Parallel execution failed", e);
        }
    }

    private void processPerson(Person person) {
        Random random = SynPopUtils.getRandom(person.getId(), getName());
        @SuppressWarnings("unchecked") List<Tour> sortedTours = (List<Tour>) person.getAttributes().getAttribute(SynpopConstants.TOURS);
        if (sortedTours != null && sortedTours.isEmpty()) {
            sortToursByPriority(sortedTours);
            CandidateSchedule bestSchedule = findBestSchedule(person, sortedTours, random);
            applyScheduleToMATSimPlan(person, sortedTours, bestSchedule.mainActivityStartTimes());
        }
    }


    private CandidateSchedule findBestSchedule(Person person, List<Tour> sortedTours, Random random) {
        CandidateSchedule bestSchedule = null;
        CandidateSchedule bestInvalidSchedule = null; // Fallback if no 100% valid schedule is found

        for (int i = 0; i < 100; i++) {
            CandidateSchedule candidate = buildCandidateSchedule(person, sortedTours, rulesMap, random);

            if (candidate.isValid()) {
                if (bestSchedule == null) {
                    bestSchedule = candidate;
                } else {
                    // Simulated Annealing / Logit acceptance of the new schedule
                    // Python: binary_choice_prob = 1 / (1 + exp(-5 * (new_score - best_score)))
                    double logitProb = 1.0 / (1.0 + Math.exp(-5.0 * (candidate.score() - bestSchedule.score())));
                    if (random.nextDouble() < logitProb) {
                        bestSchedule = candidate;
                    }
                }
            } else {
                // Track invalid schedules just in case this agent has an impossible day
                if (bestInvalidSchedule == null || candidate.score() > bestInvalidSchedule.score()) {
                    bestInvalidSchedule = candidate;
                }
            }
        }

        return bestSchedule != null ? bestSchedule : bestInvalidSchedule;
    }

    private CandidateSchedule buildCandidateSchedule(Person person, List<Tour> sortedTours, Map<String, List<TimeSeriesRule>> rulesMap, Random random) {
        DailyCalendar calendar = new DailyCalendar();
        int[] chosenStartTimes = new int[sortedTours.size()];
        double totalDayScore = 0.0;
        boolean allToursFit = true;

        for (int i = 0; i < sortedTours.size(); i++) {
            Tour tour = sortedTours.get(i);

            // 1. Calculate duration parts (Before Main Act, Main Act, After Main Act)
            double durBeforeMainActivityStart = calculateDurationBeforeMain(tour);
            double durAfterMainActivityStart = estimateTotalTourDuration(tour) - durBeforeMainActivityStart;

            // 2. Find the matching TimeSeriesRule for this activity and person
            TimeSeriesRule rule = findMatchingRule(person, tour, rulesMap);
            if (rule == null) {
                allToursFit = false; break;
            }

            // 3. Find intersections between Free Windows and Time Bins
            List<ValidIntersection> intersections = new ArrayList<>();
            double totalWeight = 0.0;

            for (DailyCalendar.TimeWindow window : calendar.getFreeWindows()) {
                // Apply the 15-minute (900s) buffer from the Python code
                int constrainedBegin = window.startSec() + (int) durBeforeMainActivityStart + 900;
                int constrainedEnd = window.endSec() - (int) durAfterMainActivityStart - 900;

                if (constrainedEnd > constrainedBegin) {
                    for (TimeBin bin : rule.timeBins()) {
                        int overlapBegin = Math.max(constrainedBegin, bin.startTimeSec());
                        int overlapEnd = Math.min(constrainedEnd, bin.endTimeSec());
                        int overlapDuration = overlapEnd - overlapBegin;

                        if (overlapDuration > 0 && bin.probability() > 0) {
                            double weight = bin.probability() * overlapDuration; // Weight by overlap size
                            intersections.add(new ValidIntersection(overlapBegin, overlapEnd, bin, weight));
                            totalWeight += weight;
                        }
                    }
                }
            }

            if (intersections.isEmpty() || totalWeight <= 0.0) {
                // The tour physically cannot fit into the remaining free time!
                allToursFit = false;
                break;
            }

            // 4. Roulette Wheel Draw for the specific Intersection
            double r = random.nextDouble() * totalWeight;
            double cumulative = 0.0;
            ValidIntersection chosenIntersection = intersections.getLast();

            for (ValidIntersection intersection : intersections) {
                cumulative += intersection.weight;
                if (r <= cumulative) {
                    chosenIntersection = intersection;
                    break;
                }
            }

            // 5. Pick an exact second within the chosen overlapping window
            int exactMainActStart = chosenIntersection.overlapBegin +
                    (int) (random.nextDouble() * (chosenIntersection.overlapEnd - chosenIntersection.overlapBegin));

            chosenStartTimes[i] = exactMainActStart;

            // 6. Block the calendar! (Block = Leave Home to Return Home)
            int tourLeaveHomeTime = exactMainActStart - (int) durBeforeMainActivityStart;
            int tourReturnHomeTime = exactMainActStart + (int) durAfterMainActivityStart;
            calendar.blockTime(tourLeaveHomeTime, tourReturnHomeTime);

            // 7. Calculate the Score for this choice (Python translation)
            double binShare = chosenIntersection.bin.probability();
            double binWidth = chosenIntersection.bin.getDurationSec();
            // prob_post_choice = (3600 / bin_width) * bin_share * expBeta
            double probPostChoice = (3600.0 / binWidth) * binShare / 100.0;
            double score = Math.exp(probPostChoice * rule.expBeta());
            totalDayScore += score;
        }

        return new CandidateSchedule(totalDayScore, chosenStartTimes, allToursFit);
    }

    private double calculateDurationBeforeMain(Tour tour) {
        double durationBefore = 0.0;
        Activity mainActivity = tour.getMainActivities().getFirst(); // The exact object reference

        for (var pe : tour.getTourElements()) {
            // Stop exactly when we hit the physical Main Activity object
            if (pe == mainActivity) {
                break;
            }

            if (pe instanceof Activity act) {
                durationBefore += act.getMaximumDuration().orElse(0.0);
            } else if (pe instanceof Leg leg) {
                durationBefore += leg.getTravelTime().orElse(0.0);
            }
        }
        return durationBefore;
    }

    /**
     * Calculates the cumulative travel distance of all legs leading up to the Main Activity.
     */
    private double calculateDistanceBeforeMain(Tour tour) {
        double cumulativeDistance = 0.0;
        Activity mainActivity = tour.getMainActivities().getFirst();

        for (var pe : tour.getTourElements()) {
            if (pe == mainActivity) {
                break;
            }
            if (pe instanceof Leg leg) {
                Object distanceAttribute = leg.getAttributes().getAttribute(InitialModeChoiceModel.CAR_NET_DISTANCE);
                if (distanceAttribute != null) {
                    cumulativeDistance += (double) distanceAttribute;
                }
            }
        }
        return cumulativeDistance;
    }

    // Helper Record for the intersection math
    private record ValidIntersection(int overlapBegin, int overlapEnd, TimeBin bin, double weight) {}

    // Helper to find the matching rule evaluating the incoming trip distance
    private TimeSeriesRule findMatchingRule(Person person, Tour tour, Map<String, List<TimeSeriesRule>> rulesMap) {
        List<TimeSeriesRule> rules = rulesMap.get(tour.getMainActivityType());
        if (rules == null || rules.isEmpty()) return null;

        // The distance of the trip leading TO the main activity
        double incomingDistance = calculateDistanceBeforeMain(tour);

        for (TimeSeriesRule rule : rules) {
            if (rule.condition().test(person, incomingDistance)) {
                return rule;
            }
        }
        return null;
    }
    private void applyScheduleToMATSimPlan(Person person, List<Tour> tours, int[] mainActStartTimes) {
        PopulationFactory factory = PopulationUtils.getFactory();
        Plan plan = factory.createPlan();

        // 1. Pair the tours with their calculated DEPARTURE times and sort chronologically
        record ScheduledTour(Tour tour, double departureTime) {}
        List<ScheduledTour> scheduledTours = new ArrayList<>();

        for (int i = 0; i < tours.size(); i++) {
            Tour tour = tours.get(i);
            double durBeforeMain = calculateDurationBeforeMain(tour);
            double departureTime = mainActStartTimes[i] - durBeforeMain;
            scheduledTours.add(new ScheduledTour(tour, departureTime));
        }

        scheduledTours.sort(Comparator.comparingDouble(ScheduledTour::departureTime));

        // 2. Build the continuous 24-hour MATSim Plan
        double currentTime = 0.0;

        // Initial Morning Home Activity (Since Tour 1 starts with a Leg)
        Activity currentHomeActivity = factory.createActivityFromCoord(SBBActivities.home,null);
        plan.addActivity(currentHomeActivity);

        for (ScheduledTour st : scheduledTours) {
            Tour tour = st.tour();
            List<PlanElement> tourElements = tour.getTourElements();

            // Safety fallback: Prevent overlapping tours if the schedule was tightly squished
            double actualDepartureTime = Math.max(currentTime, st.departureTime());

            // Close the previous Home activity exactly when this tour starts
            currentHomeActivity.setEndTime(actualDepartureTime);
            currentTime = actualDepartureTime;

            // Roll forward through the Tour Elements
            for (int i = 0; i < tourElements.size(); i++) {
                PlanElement pe = tourElements.get(i);

                if (pe instanceof Leg leg) {
                    double travelTime = leg.getTravelTime().orElse(0.0);
                    currentTime += travelTime;

                    plan.addLeg(leg);

                } else if (pe instanceof Activity act) {
                    act.setStartTime(currentTime);

                    // Is this the very last activity of the tour (the Return Home activity)?
                    if (i == tourElements.size() - 1) {
                        // Keep it open to bridge the gap to the next tour
                        currentHomeActivity = act;
                    } else {
                        // Standard intermediate activity/stop
                        double duration = act.getMaximumDuration().orElse(0.0);
                        currentTime += duration;
                        act.setEndTime(currentTime);
                    }

                    // Erase duration so MATSim strictly follows the clock times
                    act.setMaximumDurationUndefined();
                    plan.addActivity(act);
                }
            }
        }

        // 3. Close the final Evening Home Activity
        // (The last tour's returning Home activity remains open until the end of the simulation)
        currentHomeActivity.setEndTimeUndefined();

        // 4. Attach the finalized plan to the Person
        person.addPlan(plan);
        person.setSelectedPlan(plan);
    }


    /**
     * Sorts the person's tours by Priority (Edu > Work > Business > Other),
     * and then by Total Duration (Longest first) as a tie-breaker.
     */
    private void sortToursByPriority(List<Tour> tours) {
        tours.sort((t1, t2) -> {
            int p1 = getActivityPriority(t1.getMainActivityType());
            int p2 = getActivityPriority(t2.getMainActivityType());

            if (p1 != p2) {
                return Integer.compare(p2, p1); // Descending order (10 comes first)
            }

            // Tie-breaker: Longest tour goes first
            double dur1 = estimateTotalTourDuration(t1);
            double dur2 = estimateTotalTourDuration(t2);
            return Double.compare(dur2, dur1); // Descending order
        });
    }

    private int getActivityPriority(String activityType) {
        if (SBBActivities.education.equals(activityType)) return 10;
        if (SBBActivities.work.equals(activityType)) return 9;
        if (SBBActivities.business.equals(activityType)) return 8;
        return 7; // leisure, shopping, accompany, education_continued, other
    }

    /**
     * Sums up the duration of all activities and legs in the tour to use as a sizing tie-breaker.
     * (We use the maximumDuration that was cleanly assigned in the previous model step!)
     */
    private double estimateTotalTourDuration(Tour tour) {
        return tour.getTourElements().stream().mapToDouble(pe -> {
            if (pe instanceof Activity act) {
                return act.getMaximumDuration().orElse(0.0);
            } else if (pe instanceof Leg leg) {
                return leg.getTravelTime().orElse(0.0);
            }
            return 0;
        }).sum();


    }
    // Tracks the assigned start time for the Main Activity of each tour
    public record CandidateSchedule(double score, int[] mainActivityStartTimes, boolean isValid) {}

    public record TimeBin(int startTimeSec, int endTimeSec, double probability) {
        public int getDurationSec() {
            return endTimeSec - startTimeSec;
        }
    }

    public record TimeSeriesRule(
            String activityType,
            String attributeExpression,
            BiPredicate<Person, Double> condition,
            double expBeta,
            List<TimeBin> timeBins
    ) {}

    /**
     * A helper class that acts as the agent's 24-hour schedule.
     * It tracks free time windows and knows how to split them when an activity is scheduled.
     */
    public static class DailyCalendar {

        public record TimeWindow(int startSec, int endSec) {
            public int getDuration() { return endSec - startSec; }
        }

        private final List<TimeWindow> freeWindows = new ArrayList<>();

        public DailyCalendar() {
            // Initialize with a completely free day (0h to 25h, as per the Python code)
            freeWindows.add(new TimeWindow(0, 25 * 3600));
        }

        public List<TimeWindow> getFreeWindows() {
            return freeWindows;
        }


        /**
         * Blocks out a chunk of time, splitting or removing free windows as necessary.
         */
        public void blockTime(int blockStart, int blockEnd) {
            List<TimeWindow> newWindows = new ArrayList<>();

            for (TimeWindow window : freeWindows) {
                // 1. Block is completely outside this window -> keep window intact
                if (blockStart >= window.endSec || blockEnd <= window.startSec) {
                    newWindows.add(window);
                }
                // 2. Block is entirely inside this window -> split window in two!
                else if (blockStart > window.startSec && blockEnd < window.endSec) {
                    newWindows.add(new TimeWindow(window.startSec, blockStart));
                    newWindows.add(new TimeWindow(blockEnd, window.endSec));
                }
                // 3. Block completely surrounds this window -> window is destroyed (do not add to new list)
                else //noinspection StatementWithEmptyBody
                    if (blockStart <= window.startSec && blockEnd >= window.endSec) {
                    // Do nothing
                }
                // 4. Block overlaps the right side of the window
                else if (blockStart > window.startSec) {
                    newWindows.add(new TimeWindow(window.startSec, blockStart));
                }
                // 5. Block overlaps the left side of the window
                else {
                    newWindows.add(new TimeWindow(blockEnd, window.endSec));
                }
            }

            // Replace the old list and ensure it stays chronologically sorted
            freeWindows.clear();
            freeWindows.addAll(newWindows);
            freeWindows.sort(Comparator.comparingInt(TimeWindow::startSec));
        }
    }
}
