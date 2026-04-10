package ch.sbb.matsim.plans.models.steps;

import ch.sbb.matsim.analysis.skims.FloatMatrix;
import ch.sbb.matsim.config.variables.SBBActivities;
import ch.sbb.matsim.config.variables.SBBModes;
import ch.sbb.matsim.plans.MOBiPlansRunner;
import ch.sbb.matsim.plans.constants.SynpopConstants;
import ch.sbb.matsim.plans.constants.ZonalAttributes;
import ch.sbb.matsim.plans.matrices.Matrices;
import ch.sbb.matsim.plans.matrices.MatrixNames;
import ch.sbb.matsim.plans.models.config.MOBiPlansConfig;
import ch.sbb.matsim.plans.models.formulas.ModeImpedanceFormulaRepository;
import ch.sbb.matsim.plans.models.utils.ExpressionEvaluator;
import ch.sbb.matsim.plans.models.utils.StrataPredicateFactory;
import ch.sbb.matsim.plans.synpop.DecisionStrata;
import ch.sbb.matsim.plans.synpop.SynPopUtils;
import ch.sbb.matsim.plans.tours.Tour;
import ch.sbb.matsim.zones.Zone;
import jakarta.inject.Inject;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.population.*;

import java.util.*;
import java.util.concurrent.ForkJoinPool;

/*
   This is a re-implementation of the zone based initial mode choice model. It produces a starting solution for MATSim and is only based on zones.
 */
public class InitialModeChoiceModel implements ModelStep {

    public static final String CAR_NET_DISTANCE = "car_net_distance";
    private final Matrices matrices;
    private final Scenario scenario;
    private final MOBiPlansConfig moBiPlansConfig;
    private final ModeImpedanceFormulaRepository modeImpedanceFormulaRepository;
    private final StrataPredicateFactory strataPredicateFactory;
    private final ExpressionEvaluator expressionEvaluator; // NEW: Required for formulas
    private final FloatMatrix<Id<Zone>> networkDistances;
    private final PopulationFactory populationFactory;

    private final static Set<String> allModes = Set.of(SBBModes.BIKE, TransportMode.walk, SBBModes.RIDE, SBBModes.CAR, SBBModes.PT);
    private final static Set<String> modesWithoutDriving = Set.of(SBBModes.BIKE, TransportMode.walk, SBBModes.RIDE, SBBModes.PT);
    private final static Set<String> chainbasedModes = Set.of(SBBModes.CAR); // exclude bike here

    @Inject
    public InitialModeChoiceModel(Matrices matrices, Scenario scenario, MOBiPlansConfig moBiPlansConfig,
                                  ModeImpedanceFormulaRepository modeImpedanceFormulaRepository,
                                  StrataPredicateFactory strataPredicateFactory,
                                  ExpressionEvaluator expressionEvaluator) {
        this.matrices = matrices;
        this.scenario = scenario;
        this.moBiPlansConfig = moBiPlansConfig;
        this.modeImpedanceFormulaRepository = modeImpedanceFormulaRepository;
        this.strataPredicateFactory = strataPredicateFactory;
        this.expressionEvaluator = expressionEvaluator;
        this.networkDistances = matrices.getTravelMetricsMatrix(MatrixNames.TravelMetricsMatrices.CAR_NET_DISTANCE);
        this.populationFactory = scenario.getPopulation().getFactory();
    }

    @Override
    public String getName() {
        return MOBiPlansRunner.INITIAL_MODE_CHOICE;
    }

    @Override
    public void run() {
        ForkJoinPool customPool = new ForkJoinPool(moBiPlansConfig.models().threads());
        try {
            customPool.submit(() ->
                    scenario.getPopulation().getPersons().values().parallelStream()
                            .filter(person -> new PopulationSampler.ActivePersonPredicate().test(person)) // Only process active persons
                            .forEach(this::processPerson)
            ).get();
        } catch (Exception e) {
            throw new RuntimeException("Parallel execution failed", e);
        } finally {
            customPool.shutdown();
        }
    }

    private void processPerson(Person person) {
        Zone homeZone = (Zone) person.getAttributes().getAttribute(SynpopConstants.ZONE);
        Random random = SynPopUtils.getRandom(person.getId(), getName()); // Deterministic random

        @SuppressWarnings("unchecked")
        List<Tour> tours = (List<Tour>) person.getAttributes().getAttribute(SynpopConstants.TOURS);
        if (tours == null) return;

        for (Tour tour : tours) {
            // Flatten each tour into a chain of relations
            List<LegInTour> relationsWithActivityType = flattenTourIntoLegs(tour, homeZone);

            // Find longest leg for main tour
            LegInTour longestDistanceLeg = relationsWithActivityType.stream()
                    .filter(leg -> !leg.partOfSubtour())
                    .max(Comparator.comparingDouble(LegInTour::networkDistance))
                    .orElseThrow();

            // Determine available modes (Safe boolean check)
            boolean carAvail = Boolean.TRUE.equals(person.getAttributes().getAttribute(SynpopConstants.CAR_AVAILABLE));
            Set<String> availableModes = carAvail ? allModes : modesWithoutDriving;

            String mainTourMode = chooseMode(longestDistanceLeg, person, availableModes, random);
            tour.setMaintourMode(mainTourMode);

            if (!tour.getSubtourActivities().isEmpty()){
                LegInTour longestSubtourLeg = relationsWithActivityType.stream()
                        .filter(LegInTour::partOfSubtour)
                        .max(Comparator.comparingDouble(LegInTour::networkDistance))
                        .orElseThrow();

                // Subtours cannot use chain-based modes UNLESS they brought that vehicle on the main tour
                Set<String> availableSubtourModes = new HashSet<>(availableModes);
                availableSubtourModes.removeAll(chainbasedModes);
                if (chainbasedModes.contains(mainTourMode)){
                    availableSubtourModes.add(mainTourMode);
                }

                String subtourMode = chooseMode(longestSubtourLeg, person, availableSubtourModes, random);
                tour.setSubtourMode(subtourMode);


            }
         generateTourElements(tour,relationsWithActivityType);

        }
    }

    void generateTourElements(Tour tour, List<LegInTour> relationsWithActivityType) {
        List<PlanElement> planElements = new ArrayList<>();
        for (LegInTour legInTour : relationsWithActivityType) {
            String mode = legInTour.partOfSubtour ? tour.getSubtourMode() : tour.getMaintourMode();
            Leg leg = populationFactory.createLeg(mode);
            setTravelTimeAndDistanceForMode(legInTour.fromZone,legInTour.toZone, mode, leg);
            planElements.add(legInTour.nextActivity);
        }
            tour.setTourElements(planElements);
    }



    private void setTravelTimeAndDistanceForMode(Zone fromZone, Zone toZone, String mode, Leg leg) {
        // Fetch travel metrics for the O-D pair
        Map<String, Double> metrics = matrices.getTravelMetrics(fromZone.getId(), toZone.getId());
        double distanceMeters = getMetric(metrics, MatrixNames.TravelMetricsMatrices.CAR_NET_DISTANCE.name().toLowerCase(), 0.0);
        leg.getAttributes().putAttribute(CAR_NET_DISTANCE, distanceMeters);
        //add the distance as a leg attribute,
        // it is needed in the Start Time Choice model. Not using the route here, as no actual route has been calculated.

        // Base Walk travel time (Python speed: 4.7 km/h. Conversion to m/s is 4.7 / 3.6)
        double walkTtSeconds = distanceMeters / (4.7 / 3.6);

        double travelTime = switch (mode) {
            case SBBModes.WALK_MAIN_MAINMODE:
            case "walk":
                yield walkTtSeconds;

            case SBBModes.BIKE:
                // Base Bike travel time (Python speed: 13.0 km/h. Conversion to m/s is 13.0 / 3.6)
                yield distanceMeters / (13.0 / 3.6);

            case SBBModes.CAR:
                double carTt = getMetric(metrics, MatrixNames.TravelMetricsMatrices.CAR_TRAVEL_TIMES.name().toLowerCase(), 0.0);
                double atCarOrigin = getZoneDouble(fromZone, ZonalAttributes.AT_CAR, 0.0);
                double atCarDest = getZoneDouble(toZone, ZonalAttributes.AT_CAR, 0.0);
                double pcCar = getZoneDouble(toZone, ZonalAttributes.PC_CAR, 0.0);

                // Parking penalty remains exactly as we established earlier
                yield carTt + atCarOrigin + atCarDest + (60.0 * pcCar * 2.0 * 0.047 / 0.125);

            case SBBModes.RIDE:
                double rideTt = getMetric(metrics, MatrixNames.TravelMetricsMatrices.CAR_TRAVEL_TIMES.name().toLowerCase(), 0.0);
                // Adjust "at_ride" and "pc_ride" if you have them as constants in ZonalAttributes!
                double atRideOrigin = getZoneDouble(fromZone, "at_ride", 0.0);
                double atRideDest = getZoneDouble(toZone, "at_ride", 0.0);
                double pcRide = getZoneDouble(toZone, "pc_ride", 0.0);

                yield rideTt + atRideOrigin + atRideDest + (60.0 * pcRide * 2.0 * 0.047 / 0.125);

            case SBBModes.PT:
                // 5 minute transfer penalty translates to 300 seconds
                double ptTt = getMetric(metrics, MatrixNames.TravelMetricsMatrices.PT_TRAVEL_TIMES_TOTAL.name().toLowerCase(), 0.0)
                        + getMetric(metrics, MatrixNames.TravelMetricsMatrices.PT_ACCESS_TIME.name().toLowerCase(), 0.0)
                        + getMetric(metrics, MatrixNames.TravelMetricsMatrices.PT_EGRESS_TIME.name().toLowerCase(), 0.0)
                        + getMetric(metrics, MatrixNames.TravelMetricsMatrices.PT_TRANSFERS.name().toLowerCase(), 0.0) * 300.0;

                // Python logic: "np.where(tt_mat_pt < tt_mat_walk, tt_mat_pt, tt_mat_walk)"
                // Meaning, if walking is faster than waiting for the train, the agent just walks!
                yield Math.min(ptTt, walkTtSeconds);

            default:
                yield walkTtSeconds; // Safe fallback
        };

        leg.setTravelTime(travelTime);
    }

    // --- Helper Methods (if not already in your class) ---

    private double getMetric(Map<String, Double> metrics, String key, double defaultValue) {
        return metrics != null ? metrics.getOrDefault(key, defaultValue) : defaultValue;
    }

    private double getZoneDouble(Zone zone, String attributeName, double defaultValue) {
        Object val = zone.getAttributes().getAttribute(attributeName);
        if (val instanceof Number) return ((Number) val).doubleValue();
        if (val instanceof String) {
            try { return Double.parseDouble((String) val); } catch (NumberFormatException ignored) {}
        }
        return defaultValue;
    }

    private String chooseMode(LegInTour longestDistanceLeg, Person person, Set<String> availableModes, Random random) {
        String activityType = longestDistanceLeg.nextActivity().getType();

        DecisionStrata strata = getStrataForPersonAndActivity(person, activityType);
        Map<String, String> modeFormulas = modeImpedanceFormulaRepository.getModeFormulas(activityType, strata);

        if (modeFormulas == null || modeFormulas.isEmpty()) {
            throw new IllegalStateException("No mode formulas found for activity '" + activityType + "' and strata '" + strata.getStrataName() + "'");
        }

        // Fetch the travel metrics for this specific O-D pair
        Map<String, Double> travelMetrics = matrices.getTravelAndAccessibilityMetrics(
                longestDistanceLeg.fromZone(), longestDistanceLeg.toZone());

        Map<String, Double> utilities = new HashMap<>();
        double maxUtility = Double.NEGATIVE_INFINITY;

        // 1. Evaluate the utility for every available mode
        for (String mode : availableModes) {
            String formula = modeFormulas.get(mode);

            // Skip modes that are either unavailable in the CSV ("-9999") or missing entirely
            if (formula == null || formula.trim().equals("-9999")) {
                continue;
            }

            try {
                // Evaluate the math expression
                double u = expressionEvaluator.evaluate(formula, travelMetrics);

                if (Double.isNaN(u)) {
                    utilities.put(mode, Double.NEGATIVE_INFINITY);
                } else {
                    utilities.put(mode, u);
                    if (u > maxUtility) {
                        maxUtility = u;
                    }
                }
            } catch (Exception e) {
                // If a formula fails to parse (e.g., missing variable), mark the mode as impossible
                utilities.put(mode, Double.NEGATIVE_INFINITY);
            }
        }

        // Fast-fail if no modes are mathematically possible
        if (maxUtility == Double.NEGATIVE_INFINITY || utilities.isEmpty()) {
            throw new IllegalStateException("No valid modes available for person " + person.getId() + " on tour to " + activityType);
        }

        // 2. Exponentiate and normalize into probabilities (using LogSumExp trick for numeric stability)
        double sumExp = 0.0;
        Map<String, Double> probabilities = new HashMap<>();

        for (Map.Entry<String, Double> entry : utilities.entrySet()) {
            double u = entry.getValue();
            if (u > Double.NEGATIVE_INFINITY) {
                double p = Math.exp(u - maxUtility);
                probabilities.put(entry.getKey(), p);
                sumExp += p;
            }
        }

        // 3. Roulette wheel selection
        double r = random.nextDouble() * sumExp;
        double cumulative = 0.0;

        for (Map.Entry<String, Double> entry : probabilities.entrySet()) {
            cumulative += entry.getValue();
            if (r <= cumulative) {
                return entry.getKey();
            }
        }

        // Fallback (Should mathematically never happen unless probabilities sum to 0)
        return probabilities.keySet().iterator().next();

    }

    private DecisionStrata getStrataForPersonAndActivity(Person person, String type) {
        return modeImpedanceFormulaRepository.getStratasPerActivityType().get(type).stream()
                .filter(strata -> strataPredicateFactory.getPredicate(strata).test(person))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No strata found for person " + person.getId() + " and activity type " + type));
    }

    private List<LegInTour> flattenTourIntoLegs(Tour tour, Zone homeZone) {
        List<LegInTour> relationsWithActivityType = new ArrayList<>();

        Zone fromZone = homeZone;
        Zone toZone;

        for (Activity activity : tour.getOutboundStopActivities()) {
            toZone = (Zone) activity.getAttributes().getAttribute(CombinedDestinationAndDurationChoiceModel.ASSIGNED_ZONE);
            float distance = networkDistances.get(fromZone.getId(), toZone.getId());
            relationsWithActivityType.add(new LegInTour(fromZone, toZone, distance, false, activity));
            fromZone = toZone;
        }

        Activity mainActivity0 = tour.getMainActivities().get(0);
        toZone = (Zone) mainActivity0.getAttributes().getAttribute(CombinedDestinationAndDurationChoiceModel.ASSIGNED_ZONE);
        float distanceToMainActivity = networkDistances.get(fromZone.getId(), toZone.getId());
        relationsWithActivityType.add(new LegInTour(fromZone, toZone, distanceToMainActivity, false, mainActivity0));
        fromZone = toZone;

        if (!tour.getSubtourActivities().isEmpty()){
            for (Activity activity : tour.getSubtourActivities()) {
                toZone = (Zone) activity.getAttributes().getAttribute(CombinedDestinationAndDurationChoiceModel.ASSIGNED_ZONE);
                float distance = networkDistances.get(fromZone.getId(), toZone.getId());
                relationsWithActivityType.add(new LegInTour(fromZone, toZone, distance, true, activity));
                fromZone = toZone;
            }

            // Back to main activity
            Activity mainActivity1 = tour.getMainActivities().get(1);
            toZone = (Zone) mainActivity1.getAttributes().getAttribute(CombinedDestinationAndDurationChoiceModel.ASSIGNED_ZONE);
            float distanceToMainActivity2 = networkDistances.get(fromZone.getId(), toZone.getId());
            relationsWithActivityType.add(new LegInTour(fromZone, toZone, distanceToMainActivity2, true, mainActivity1));
            fromZone = toZone;
        }

        for (Activity activity : tour.getInboundStopActivities()) {
            toZone = (Zone) activity.getAttributes().getAttribute(CombinedDestinationAndDurationChoiceModel.ASSIGNED_ZONE);
            float distance = networkDistances.get(fromZone.getId(), toZone.getId());
            relationsWithActivityType.add(new LegInTour(fromZone, toZone, distance, false, activity));
            fromZone = toZone;
        }

        // Finally back home
        float distanceHome = networkDistances.get(fromZone.getId(), homeZone.getId());
        relationsWithActivityType.add(new LegInTour(fromZone, homeZone, distanceHome, false, populationFactory.createActivityFromCoord(SBBActivities.home, null)));

        return relationsWithActivityType;
    }

    record LegInTour(Zone fromZone, Zone toZone, double networkDistance, boolean partOfSubtour, Activity nextActivity){}
}