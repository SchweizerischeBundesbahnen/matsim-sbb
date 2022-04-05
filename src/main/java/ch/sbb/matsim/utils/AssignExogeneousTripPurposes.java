package ch.sbb.matsim.utils;

import ch.sbb.matsim.config.variables.Variables;
import ch.sbb.matsim.zones.Zone;
import ch.sbb.matsim.zones.Zones;
import ch.sbb.matsim.zones.ZonesLoader;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.population.Activity;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.Plan;
import org.matsim.api.core.v01.population.Population;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.gbl.MatsimRandom;
import org.matsim.core.population.io.PopulationReader;
import org.matsim.core.population.io.PopulationWriter;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.core.utils.geometry.CoordUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class AssignExogeneousTripPurposes {

    public static final String BUSINESS = "Ge";
    public static final String LEISURE = "Fr";
    public static final String COMMUTE = "Pe";
    public static final String FREIGHT = "Cargo";

    public static void main(String[] args) throws IOException {
        Zones zones = ZonesLoader.loadZones("zone_id", args[0], Variables.ZONE_ID);
        Path start = Paths.get(args[1]);
        try (Stream<Path> stream = Files.walk(start, Integer.MAX_VALUE)) {
            List<String> exofiles = stream
                    .map(String::valueOf)
                    .sorted()
                    .filter(s -> s.endsWith(".xml.gz"))
                    .collect(Collectors.toList());
            exofiles.forEach(System.out::println);
            exofiles.parallelStream().forEach(s -> {
                Random r = MatsimRandom.getLocalInstance();
                Scenario scenario = ScenarioUtils.createScenario(ConfigUtils.createConfig());
                new PopulationReader(scenario).readFile(s);
                if (s.contains(Variables.AIRPORT_ROAD) || s.contains(Variables.AIRPORT_RAIL)) {
                    assignAirportTripPurpose(scenario.getPopulation(), r);
                } else if (s.contains(Variables.CB_RAIL) || s.contains(Variables.CB_ROAD)) {
                    assignCBPurpose(scenario.getPopulation(), r, zones);
                } else if (s.contains(Variables.FREIGHT_ROAD)) {
                    assignFreightPurpose(scenario.getPopulation());
                } else if (s.contains(Variables.TOURISM_RAIL)) {
                    assignTouristPurpose(scenario.getPopulation());
                }
                new PopulationWriter(scenario.getPopulation()).write(s);
            });


        }


    }


    public static void assignCBPurpose(Population population, Random random, Zones zones) {
        for (Person person : population.getPersons().values()) {

            Plan plan = person.getSelectedPlan();
            if (plan.getPlanElements().size() != 3) {
                throw new RuntimeException("Plans have more than one trip. Probably not desired.");
            }
            Activity origin = (Activity) plan.getPlanElements().get(0);
            Activity destination = (Activity) plan.getPlanElements().get(2);
            Zone originZone = zones.findZone(origin.getCoord());
            Zone destinationZone = zones.findZone(destination.getCoord());

            double departureTime = origin.getEndTime().seconds();
            double beelineDistance = CoordUtils.calcEuclideanDistance(origin.getCoord(), destination.getCoord());
            String purpose;
            double r = random.nextDouble();
            if (beelineDistance > 75_000) {
                if (r <= 0.225) {
                    purpose = BUSINESS;
                } else if (r <= 0.415) {
                    purpose = COMMUTE;
                } else {
                    purpose = LEISURE;
                }
            } else {

                if (isSwissZone(originZone)) {
                    if (departureTime > 15.0 * 3600 && departureTime < 19.0 * 3600) {
                        if (r <= 0.6) {
                            purpose = COMMUTE;
                        } else if (r <= 0.8) {
                            purpose = LEISURE;
                        } else {
                            purpose = BUSINESS;
                        }
                    } else {
                        if (r <= 0.19) {
                            purpose = COMMUTE;
                        } else if (r <= 0.78) {
                            purpose = LEISURE;
                        } else {
                            purpose = BUSINESS;
                        }
                    }
                } else if (isSwissZone(destinationZone)) {

                    if (departureTime < 9.5 * 3600) {
                        if (r <= 0.6) {
                            purpose = COMMUTE;
                        } else if (r <= 0.8) {
                            purpose = LEISURE;
                        } else {
                            purpose = BUSINESS;
                        }
                    } else {
                        if (r <= 0.15) {
                            purpose = COMMUTE;
                        } else if (r <= 0.75) {
                            purpose = LEISURE;
                        } else {
                            purpose = BUSINESS;
                        }
                    }
                } else {
                    //Transit, Konstanz
                    if (r <= 0.15) {
                        purpose = COMMUTE;
                    } else if (r <= 0.78) {
                        purpose = LEISURE;
                    } else {
                        purpose = BUSINESS;
                    }

                }
            }
            assignTripPurpose(plan, purpose);

        }
    }

    private static boolean isSwissZone(Zone zone) {
        if (zone != null) {
            return Integer.parseInt(zone.getId().toString()) < 700000000;
        }
        return false;
    }

    public static void assignFreightPurpose(Population population) {
        for (Person person : population.getPersons().values()) {
            assignTripPurpose(person.getSelectedPlan(), FREIGHT);
        }
    }

    public static void assignTouristPurpose(Population population) {
        for (Person person : population.getPersons().values()) {
            assignTripPurpose(person.getSelectedPlan(), LEISURE);
        }
    }

    public static void assignAirportTripPurpose(Population population, Random random) {
        for (Person person : population.getPersons().values()) {
            double r = random.nextDouble();
            String purpose;
            //data source: ZRH airport, 2019
            if (r <= 0.26) {
                purpose = BUSINESS;
            } else if (r <= 0.36) {
                purpose = COMMUTE;
            } else {
                purpose = LEISURE;
            }
            assignTripPurpose(person.getSelectedPlan(), purpose);
        }

    }

    private static void assignTripPurpose(Plan selectedPlan, String purpose) {
        int planelements = selectedPlan.getPlanElements().size();
        for (int i = 0; i < planelements - 1; i++) {
            var planElement = selectedPlan.getPlanElements().get(i);
            if (planElement instanceof Activity) {
                Activity activity = (Activity) planElement;
                activity.getAttributes().putAttribute(Variables.MOBiTripAttributes.PURPOSE, purpose);
            }
        }
    }
}
