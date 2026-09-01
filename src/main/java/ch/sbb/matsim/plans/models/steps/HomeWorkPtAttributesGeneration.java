package ch.sbb.matsim.plans.models.steps;

import ch.sbb.matsim.plans.MOBiPlansRunner;
import ch.sbb.matsim.plans.constants.SynpopConstants;
import ch.sbb.matsim.plans.matrices.Matrices;
import ch.sbb.matsim.plans.matrices.MatrixNames;
import ch.sbb.matsim.zones.Zone;
import ch.sbb.matsim.zones.Zones;
import jakarta.inject.Inject;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.population.Person;

import java.util.Map;

public class HomeWorkPtAttributesGeneration implements ModelStep {

    private static final Logger logger = LogManager.getLogger(HomeWorkPtAttributesGeneration.class);

    private final Scenario scenario;
    private final Zones zones;
    private final Matrices matrices;

    @Inject
    public HomeWorkPtAttributesGeneration(Scenario scenario, Zones zones, Matrices matrices) {
        this.scenario = scenario;
        this.zones = zones;
        this.matrices = matrices;
    }

    @Override
    public String getName() {
        return MOBiPlansRunner.HOME_WORK_PT_ATTRIBUTES;
    }

    @Override
    public void run() {
        scenario.getPopulation().getPersons().values().parallelStream().forEach(this::assignPtAttributes);
        logger.info("Generated PT commute attributes for persons with work/school locations.");
    }

    private void assignPtAttributes(Person person) {
        Zone homeZone = resolveZone(person.getAttributes().getAttribute(SynpopConstants.ZONE));
        if (homeZone == null) {
            logger.error("Person {} has no home zone; needed for PT attribute assignment.", person.getId());
            return;
        }

        assignPtAttributesForDestination(
            person,
            homeZone,
            SynpopConstants.WORK_ZONE,
            SynpopConstants.PT_TRAVEL_TIME_WORK,
            SynpopConstants.PT_ACCESS_TIME_WORK,
            SynpopConstants.PT_TRANSFERS_WORK
        );

        assignPtAttributesForDestination(
            person,
            homeZone,
            SynpopConstants.SCHOOL_ZONE,
            SynpopConstants.PT_TRAVEL_TIME_SCHOOL,
            SynpopConstants.PT_ACCESS_TIME_SCHOOL,
            SynpopConstants.PT_TRANSFERS_SCHOOL
        );
    }

    private void assignPtAttributesForDestination(Person person, Zone homeZone, String destinationZoneAttribute,
        String travelTimeAttribute, String accessTimeAttribute, String transfersAttribute) {
        Object destinationZoneValue = readDestinationZoneValue(person, destinationZoneAttribute);
        if (destinationZoneValue == null) {
            logger.debug("Person {} has no '{}' attribute; skipping PT attributes [{}, {}, {}].",
                person.getId(), destinationZoneAttribute, travelTimeAttribute, accessTimeAttribute, transfersAttribute);
            return;
        }

        Zone destinationZone = resolveZone(destinationZoneValue);
        if (destinationZone == null) {
            logger.warn("Person {} has unresolved '{}' value '{}'; skipping PT attributes [{}, {}, {}].",
                person.getId(), destinationZoneAttribute, destinationZoneValue, travelTimeAttribute, accessTimeAttribute, transfersAttribute);
            return;
        }

        Map<String, Double> metrics = matrices.getTravelMetrics(homeZone.getId(), destinationZone.getId());
        // The available PT skim set currently exposes train and local/non-train components;
        // summing them is the closest equivalent to the requested train + bus PT travel time.
        double ptTravelTime = getMetric(metrics, MatrixNames.TravelMetricsMatrices.PT_TRAVEL_TIMES_TRAIN.name().toLowerCase())
            + getMetric(metrics, MatrixNames.TravelMetricsMatrices.PT_TRAVEL_TIMES_LOCAL.name().toLowerCase());
        if (ptTravelTime <= 0.0) {
            ptTravelTime = getMetric(metrics, MatrixNames.TravelMetricsMatrices.PT_TRAVEL_TIMES_TOTAL.name().toLowerCase());
        }

        person.getAttributes().putAttribute(travelTimeAttribute, ptTravelTime);
        person.getAttributes().putAttribute(accessTimeAttribute, getMetric(metrics, MatrixNames.TravelMetricsMatrices.PT_ACCESS_TIME.name().toLowerCase()));
        person.getAttributes().putAttribute(transfersAttribute, getMetric(metrics, MatrixNames.TravelMetricsMatrices.PT_TRANSFERS.name().toLowerCase()));
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
        // Fallback for configs writing zone ids as work_zone_no / school_zone_no.
        return person.getAttributes().getAttribute(destinationZoneAttribute + "_no");
    }

    private double getMetric(Map<String, Double> metrics, String matrixName) {
        return metrics.getOrDefault(matrixName, 0.0);
    }
}

