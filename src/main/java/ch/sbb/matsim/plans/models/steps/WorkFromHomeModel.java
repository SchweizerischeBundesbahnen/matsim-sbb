package ch.sbb.matsim.plans.models.steps;

import ch.sbb.matsim.plans.MOBiPlansRunner;
import ch.sbb.matsim.plans.constants.SynpopConstants;
import ch.sbb.matsim.plans.models.config.MOBiPlansConfig;
import ch.sbb.matsim.plans.matrices.Matrices;
import ch.sbb.matsim.plans.matrices.MatrixNames;
import ch.sbb.matsim.plans.models.steps.csvmodels.ASCFittingToTargets;
import ch.sbb.matsim.plans.models.steps.csvmodels.BooleanSelectionModelFromCSV;
import ch.sbb.matsim.plans.models.utils.AttributeUtils;
import ch.sbb.matsim.zones.Zone;
import ch.sbb.matsim.zones.Zones;
import jakarta.inject.Inject;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.population.Person;
import org.matsim.facilities.ActivityFacility;
import org.matsim.households.Household;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class WorkFromHomeModel implements ModelStep {


    private final Scenario scenario;
    private final MOBiPlansConfig config;
    private final Zones zones;
    private final Matrices matrices;
    private final BooleanSelectionModelFromCSV workFromHomeModel;
    private final BooleanSelectionModelFromCSV workFlexibleModel;
    private final static Logger logger = LogManager.getLogger(WorkFromHomeModel.class);

    @Inject
    public WorkFromHomeModel(Scenario scenario, MOBiPlansConfig config, Zones zones, Matrices matrices) {
        this.scenario = scenario;
        this.config = config;
        this.zones = zones;
        this.matrices = matrices;
        Path modelFolder = Paths.get(config.data().modelBaseDir()).resolve(MOBiPlansConfig.MODEL_BASE_FOLDER_CHOICE_MODELS);
        this.workFromHomeModel = new BooleanSelectionModelFromCSV(modelFolder.resolve(MOBiPlansConfig.WORK_FROM_HOME_MODEL_CSV).toString(), getName());
        this.workFlexibleModel = new BooleanSelectionModelFromCSV(modelFolder.resolve(MOBiPlansConfig.FLEXIBLE_WORKING_HOURS_CSV).toString(), getName());

    }


    @Override
    public String getName() {
        return MOBiPlansRunner.WORK_FROM_HOME;
    }

    @Override
    public void run() {
        scenario.getPopulation().getPersons().values().parallelStream().forEach(this::assignPrimaryLocationAttributes);
        scenario.getPopulation().getPersons().values().forEach(this::initializeWorkAttributes);
        List<Person> personsToConsider = new ArrayList<>();
        scenario.getPopulation().getPersons().values().stream().filter(p -> AttributeUtils.getAsInteger(p, SynpopConstants.LEVEL_OF_EMPLOYMENT, 0) > 0).filter(p -> p.getAttributes().getAttribute(SynpopConstants.WORK_FACILITY) != null).forEach(person -> {
            person.getAttributes().putAttribute(SynpopConstants.ORIGINAL_WORK_ZONE, person.getAttributes().getAttribute(SynpopConstants.WORK_ZONE));
            person.getAttributes().putAttribute(SynpopConstants.ORIGINAL_WORK_FACILITY, person.getAttributes().getAttribute(SynpopConstants.WORK_FACILITY));
            updateWorkZoneNo(person, person.getAttributes().getAttribute(SynpopConstants.WORK_ZONE));
            personsToConsider.add(person);
        });
        int totalWorkForce = personsToConsider.size();
        int workingFromHome = (int) Math.floor(totalWorkForce * config.models().wfh_rate());
        int flexibleWorkingHours = (int) Math.floor(totalWorkForce * config.models().work_flexible_rate());
        logger.info("Total workforce: " + totalWorkForce);
        logger.info("Target working from home population: " + workingFromHome);
        logger.info("Target flexible working hours population: " + flexibleWorkingHours);
        ASCFittingToTargets.fitAndAssignTarget(personsToConsider, workingFromHome, workFromHomeModel, SynpopConstants.IS_WORKING_FROM_HOME, "true");
        ASCFittingToTargets.fitAndAssignTarget(personsToConsider, flexibleWorkingHours, workFlexibleModel, SynpopConstants.HAS_FLEXIBLE_WORK_HOURS, "true");
        personsToConsider.stream().filter(person -> Boolean.TRUE.equals(person.getAttributes().getAttribute(SynpopConstants.IS_WORKING_FROM_HOME))).forEach(person -> {
            Object homeZone = person.getAttributes().getAttribute(SynpopConstants.ZONE);
            person.getAttributes().putAttribute(SynpopConstants.WORK_ZONE, homeZone);
            updateWorkZoneNo(person, homeZone);
            Household household = (Household) person.getAttributes().getAttribute(SynpopConstants.HOUSEHOLD);
            var homeFacility = ((ActivityFacility) household.getAttributes().getAttribute(SynpopConstants.FACILITY));
            person.getAttributes().putAttribute(SynpopConstants.WORK_FACILITY, homeFacility);
        });
        long wfhPopulation = personsToConsider.stream().filter(person -> Boolean.TRUE.equals(person.getAttributes().getAttribute(SynpopConstants.IS_WORKING_FROM_HOME))).count();
        long flexibleWorkingHoursPopulation = personsToConsider.stream().filter(person -> Boolean.TRUE.equals(person.getAttributes().getAttribute(SynpopConstants.HAS_FLEXIBLE_WORK_HOURS))).count();
        logger.info("Generated PT/distance attributes for persons with work/school locations.");
        logger.info("Actual working from home population: " + wfhPopulation);
        logger.info("Actual flexible working hours population: " + flexibleWorkingHoursPopulation);
    }

    private void assignPrimaryLocationAttributes(Person person) {
        Zone homeZone = resolveZone(person.getAttributes().getAttribute(SynpopConstants.ZONE));
        if (homeZone == null) {
            logger.error("Person {} has no home zone; needed for commute attribute assignment.", person.getId());
            return;
        }
        assignPrimaryLocationAttributesForDestination(
                person,
                homeZone,
                SynpopConstants.WORK_ZONE,
                SynpopConstants.PT_TRAVEL_TIME_WORK,
                SynpopConstants.PT_ACCESS_TIME_WORK,
                SynpopConstants.PT_TRANSFERS_WORK,
                SynpopConstants.NET_DIST_WORK
        );
        assignPrimaryLocationAttributesForDestination(
                person,
                homeZone,
                SynpopConstants.SCHOOL_ZONE,
                SynpopConstants.PT_TRAVEL_TIME_SCHOOL,
                SynpopConstants.PT_ACCESS_TIME_SCHOOL,
                SynpopConstants.PT_TRANSFERS_SCHOOL,
                SynpopConstants.NET_DIST_SCHOOL
        );
    }

    private void assignPrimaryLocationAttributesForDestination(Person person, Zone homeZone, String destinationZoneAttribute,
                                                               String travelTimeAttribute, String accessTimeAttribute,
                                                               String transfersAttribute, String distanceAttribute) {
        Object destinationZoneValue = readDestinationZoneValue(person, destinationZoneAttribute);
        if (destinationZoneValue == null) {
            return;
        }
        Zone destinationZone = resolveZone(destinationZoneValue);
        if (destinationZone == null) {
            logger.warn("Person {} has unresolved '{}' value '{}'; skipping commute attributes.",
                    person.getId(), destinationZoneAttribute, destinationZoneValue);
            return;
        }
        Map<String, Double> metrics = matrices.getTravelMetrics(homeZone.getId(), destinationZone.getId());
        if (metrics == null) {
            logger.warn("Missing travel metrics from zone {} to zone {} for person {}.",
                    homeZone.getId(), destinationZone.getId(), person.getId());
            return;
        }

        double ptTravelTime = getMetric(metrics, MatrixNames.TravelMetricsMatrices.PT_TRAVEL_TIMES_TRAIN.name().toLowerCase())
                + getMetric(metrics, MatrixNames.TravelMetricsMatrices.PT_TRAVEL_TIMES_LOCAL.name().toLowerCase());
        if (ptTravelTime <= 0.0) {
            ptTravelTime = getMetric(metrics, MatrixNames.TravelMetricsMatrices.PT_TRAVEL_TIMES_TOTAL.name().toLowerCase());
        }

        person.getAttributes().putAttribute(travelTimeAttribute, ptTravelTime);
        person.getAttributes().putAttribute(accessTimeAttribute, getMetric(metrics, MatrixNames.TravelMetricsMatrices.PT_ACCESS_TIME.name().toLowerCase()));
        person.getAttributes().putAttribute(transfersAttribute, getMetric(metrics, MatrixNames.TravelMetricsMatrices.PT_TRANSFERS.name().toLowerCase()));
        person.getAttributes().putAttribute(distanceAttribute, getMetric(metrics, MatrixNames.TravelMetricsMatrices.CAR_NET_DISTANCE.name().toLowerCase()));

        if (SynpopConstants.WORK_ZONE.equals(destinationZoneAttribute)) {
            updateWorkZoneNo(person, destinationZone);
        }
    }

    private void initializeWorkAttributes(Person person) {
        if (person.getAttributes().getAttribute(SynpopConstants.IS_WORKING_FROM_HOME) == null) {
            person.getAttributes().putAttribute(SynpopConstants.IS_WORKING_FROM_HOME, false);
        }
        if (person.getAttributes().getAttribute(SynpopConstants.HAS_FLEXIBLE_WORK_HOURS) == null) {
            person.getAttributes().putAttribute(SynpopConstants.HAS_FLEXIBLE_WORK_HOURS, false);
        }
        updateWorkZoneNo(person, person.getAttributes().getAttribute(SynpopConstants.WORK_ZONE));
    }

    private void updateWorkZoneNo(Person person, Object zoneValue) {
        String workZoneNo = toZoneId(zoneValue);
        if (workZoneNo != null) {
            person.getAttributes().putAttribute(SynpopConstants.WORK_ZONE_NO, workZoneNo);
        }
    }

    private String toZoneId(Object zoneValue) {
        if (zoneValue == null) {
            return null;
        }
        if (zoneValue instanceof Zone zone) {
            return zone.getId().toString();
        }
        return zoneValue.toString();
    }

    private Zone resolveZone(Object zoneValue) {
        if (zoneValue == null) {
            return null;
        }
        if (zoneValue instanceof Zone zone) {
            return zone;
        }
        return zones.getZone(Id.create(zoneValue.toString(), Zone.class));
    }

    private Object readDestinationZoneValue(Person person, String destinationZoneAttribute) {
        Object destinationZoneValue = person.getAttributes().getAttribute(destinationZoneAttribute);
        if (destinationZoneValue != null) {
            return destinationZoneValue;
        }
        return person.getAttributes().getAttribute(destinationZoneAttribute + "_no");
    }

    private double getMetric(Map<String, Double> metrics, String matrixName) {
        return metrics.getOrDefault(matrixName, 0.0);
    }

}

