package ch.sbb.matsim.plans.models.steps;

import ch.sbb.matsim.analysis.skims.FloatMatrix;
import ch.sbb.matsim.config.variables.SBBActivities;
import ch.sbb.matsim.config.variables.SBBModes;
import ch.sbb.matsim.plans.MOBiPlansRunner;
import ch.sbb.matsim.plans.constants.SynpopConstants;
import ch.sbb.matsim.plans.matrices.Matrices;
import ch.sbb.matsim.plans.matrices.MatrixNames;
import ch.sbb.matsim.plans.models.config.MOBiPlansConfig;
import ch.sbb.matsim.plans.models.formulas.ModeImpedanceFormulaRepository;
import ch.sbb.matsim.plans.models.utils.StrataPredicateFactory;
import ch.sbb.matsim.plans.tours.Tour;
import ch.sbb.matsim.zones.Zone;
import jakarta.inject.Inject;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.population.Activity;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.PopulationFactory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ForkJoinPool;
/*
   This is a re-implementation of the zone based initial mode choice model. It produces a starting solution for MATSim and is only based on zones.

 */
public class InitialModeChoiceModel implements ModelStep{

    private final Matrices matrices;
    private final Scenario scenario;
    private final MOBiPlansConfig moBiPlansConfig;
    private final ModeImpedanceFormulaRepository modeImpedanceFormulaRepository;
    private final StrataPredicateFactory strataPredicateFactory;
    private final FloatMatrix<Id<Zone>> networkDistances;
    private final PopulationFactory populationFactory;
    private final static Set<String> allModes = Set.of(SBBModes.BIKE,SBBModes.WALK_MAIN_MAINMODE,SBBModes.RIDE,SBBModes.CAR,SBBModes.PT);
    private final static Set<String> modesWithoutDriving = Set.of(SBBModes.BIKE,SBBModes.WALK_MAIN_MAINMODE,SBBModes.RIDE,SBBModes.PT);


    @Inject
    public InitialModeChoiceModel(Matrices matrices, Scenario scenario, MOBiPlansConfig moBiPlansConfig, ModeImpedanceFormulaRepository modeImpedanceFormulaRepository, StrataPredicateFactory strataPredicateFactory) {
        this.matrices = matrices;
        this.scenario = scenario;
        this.moBiPlansConfig = moBiPlansConfig;
        this.modeImpedanceFormulaRepository = modeImpedanceFormulaRepository;
        this.strataPredicateFactory = strataPredicateFactory;
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
                            .forEach(person -> processPerson(person))
            ).get();
        } catch (Exception e) {
            throw new RuntimeException("Parallel execution failed", e);
        } finally {
            customPool.shutdown();
        }

    }

    private void processPerson(Person person) {

        Zone homeZone = (Zone) person.getAttributes().getAttribute(SynpopConstants.ZONE);
        //flatten each tour into a chain of relations
        for (Tour tour : (List<Tour>)person.getAttributes().getAttribute(SynpopConstants.TOURS)) {
            List<LegInTour> relationsWithActivityType = flattenTourIntoLegs(tour, homeZone);
            LegInTour longestDistanceLeg = relationsWithActivityType.stream()
                    .filter(legInTour -> !legInTour.partOfSubtour())
                    .max(Comparator.comparingDouble(LegInTour::networkDistance))
                    .orElseThrow();

            //find mode for main tour

            if (tour.getSubtourActivities().size()>0){
                LegInTour longestSubtourLeg =
                    relationsWithActivityType.stream()
                            .filter(legInTour -> legInTour.partOfSubtour())
                            .max(Comparator.comparingDouble(LegInTour::networkDistance))
                            .orElseThrow();

            }
        //create real legs and matsim activities per tour in right order. add them to the tour object, not the plan
        }

    }

    private List<LegInTour> flattenTourIntoLegs(Tour tour, Zone homeZone) {
        List<LegInTour> relationsWithActivityType = new ArrayList<>();

        Zone fromZone = homeZone;
        Zone toZone;
        for (Activity activity : tour.getOutboundStopActivities()) {
            toZone = (Zone) activity.getAttributes().getAttribute(CombinedDestinationAndDurationChoiceModel.ASSIGNED_ZONE);
            float distance = networkDistances.get(fromZone.getId(), toZone.getId());
            relationsWithActivityType.add(new LegInTour(fromZone, toZone, distance, false,activity));
            fromZone = toZone;
        }
        Activity mainActivity0 = tour.getMainActivities().get(0);
        toZone = (Zone) mainActivity0.getAttributes().getAttribute(CombinedDestinationAndDurationChoiceModel.ASSIGNED_ZONE);
        float distanceToMainActivity = networkDistances.get(fromZone.getId(),toZone.getId());
        relationsWithActivityType.add(new LegInTour(fromZone,toZone, distanceToMainActivity,false,mainActivity0));
        fromZone = toZone;
        if (tour.getSubtourActivities().size()>0){
            for (Activity activity : tour.getSubtourActivities()) {
                toZone = (Zone) activity.getAttributes().getAttribute(CombinedDestinationAndDurationChoiceModel.ASSIGNED_ZONE);
                float distance = networkDistances.get(fromZone.getId(), toZone.getId());
                relationsWithActivityType.add(new LegInTour(fromZone, toZone, distance, true,activity));
                fromZone = toZone;
            }
            //back to main activity
            Activity mainActivity1 = tour.getMainActivities().get(1);
            toZone = (Zone) mainActivity1.getAttributes().getAttribute(CombinedDestinationAndDurationChoiceModel.ASSIGNED_ZONE);
            distanceToMainActivity = networkDistances.get(fromZone.getId(),toZone.getId());
            relationsWithActivityType.add(new LegInTour(fromZone,toZone, distanceToMainActivity,true,mainActivity1));
            fromZone = toZone;

        }
        for (Activity activity : tour.getInboundStopActivities()) {
            toZone = (Zone) activity.getAttributes().getAttribute(CombinedDestinationAndDurationChoiceModel.ASSIGNED_ZONE);
            float distance = networkDistances.get(fromZone.getId(), toZone.getId());
            relationsWithActivityType.add(new LegInTour(fromZone, toZone, distance, false,activity));
            fromZone = toZone;
        }
        float distanceHome = networkDistances.get(fromZone.getId(), homeZone.getId());
        relationsWithActivityType.add(new LegInTour(fromZone,homeZone,distanceHome,false,populationFactory.createActivityFromCoord(SBBActivities.home,null)));
        //finally back home

        return relationsWithActivityType;
    }

    record LegInTour(Zone fromZone, Zone toZone, double networkDistance, boolean partOfSubtour, Activity nextActivity){}
}
