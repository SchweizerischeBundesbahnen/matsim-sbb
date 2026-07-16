package ch.sbb.matsim.plans.synpop;

import ch.sbb.matsim.config.variables.Variables;
import ch.sbb.matsim.plans.constants.SynpopConstants;
import ch.sbb.matsim.plans.constants.ZonalAttributes;
import ch.sbb.matsim.plans.models.utils.AttributeUtils;
import ch.sbb.matsim.zones.Zone;
import ch.sbb.matsim.zones.Zones;
import com.google.common.hash.HashFunction;
import com.google.common.hash.Hashing;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.population.Person;
import org.matsim.facilities.ActivityFacilitiesFactory;
import org.matsim.facilities.ActivityFacility;
import org.matsim.households.Household;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

public class SynPopUtils {

    private static final Logger log = LogManager.getLogger(SynPopUtils.class);

    public static void setAttribute(Person person, String attributeName, Object attribute) {
        person.getAttributes().putAttribute(attributeName, attribute);
    }

    public static void linkHouseholdsAndPopulation(Scenario scenario) {
        for (Person person : scenario.getPopulation().getPersons().values()) {
            var householdId = Id.create((String) person.getAttributes().getAttribute(SynpopConstants.HOUSEHOLD_ID), Household.class);
            var household = scenario.getHouseholds().getHouseholds().get(householdId);
            person.getAttributes().putAttribute(SynpopConstants.HOUSEHOLD, household);
            if (!household.getMemberIds().contains(person.getId())) {
                household.addMemberId(person.getId());
            }
            person.getAttributes().putAttribute(Variables.ZONE_ID, household.getAttributes().getAttribute(Variables.ZONE_ID));
        }
    }

    public static void linkZonesToHouseHoldsAndPersons(Scenario scenario) {
        Zones zones = (Zones) scenario.getScenarioElement(Variables.ZONES);
        for (Household household : scenario.getHouseholds().getHouseholds().values()) {
            var zoneId = Id.create((String) household.getAttributes().getAttribute(SynpopConstants.ZONE_ID), Zone.class);
            var zone = zones.getZone(zoneId);
            household.getAttributes().putAttribute(SynpopConstants.ZONE, zone);
        }
        for (Person person : scenario.getPopulation().getPersons().values()) {
            var zoneId = Id.create((String) person.getAttributes().getAttribute(SynpopConstants.ZONE_ID), Zone.class);
            var zone = zones.getZone(zoneId);
            person.getAttributes().putAttribute(SynpopConstants.ZONE, zone);
        }
    }

    public static void calculateHouseHoldCompositions(Scenario scenario) {
        log.info("Calculating household compositions...");
        for (Household household : scenario.getHouseholds().getHouseholds().values()) {
            int numberOfAdults = 0;
            int numberOfAdultsAbove24 = 0;
            int numberOfChildren = 0;
            int numberOfChildrenUnder24 = 0;
            int minLanguage = Integer.MAX_VALUE;
            for (Id<Person> memberId : household.getMemberIds()) {
                Person person = scenario.getPopulation().getPersons().get(memberId);
                Integer language = (Integer) person.getAttributes().getAttribute(SynpopConstants.LANGUAGE);
                if (language != null && language < minLanguage) {
                    minLanguage = language;
                    // this is the definition that we have used so far, but it is not clear if this is the best one
                    // if there is one person speaking german (language 1) and one person speaking french (language 2),
                    // then german wins the household.
                }
                if (person.getAttributes().getAttribute(SynpopConstants.AGE) != null) {
                    int age = (int) person.getAttributes().getAttribute(SynpopConstants.AGE);
                    if (age >= 18) {
                        numberOfAdults++;
                    } else {
                        numberOfChildren++;
                    }
                    if (age >= 24) {
                        numberOfAdultsAbove24++;
                    } else {
                        numberOfChildrenUnder24++;
                    }
                }
            }
            //default: no couple
            int coupleType = 0;
            if (numberOfAdultsAbove24 >= 2 && numberOfAdultsAbove24 <= 3) {
                if (numberOfChildrenUnder24 > 0) {
                    coupleType = 1; // couple with children
                } else {
                    coupleType = 2; // couple without children
                }
            }
            boolean singleParent = numberOfAdults == 1 && numberOfChildren > 0;
            // not more than 9 children and 11 adults per household according to the MTMC
            household.getAttributes().putAttribute(SynpopConstants.NB_OF_KIDS, Math.min(numberOfChildren, 9));
            household.getAttributes().putAttribute(SynpopConstants.NB_OF_ADULTS, Math.min(numberOfAdults, 11));
            household.getAttributes().putAttribute(SynpopConstants.HAS_CHILD_IN_HH, numberOfChildren > 0);
            household.getAttributes().putAttribute(SynpopConstants.COUPLE_TYPE, coupleType);
            household.getAttributes().putAttribute(SynpopConstants.SINGLE_PARENT, singleParent);
            household.getAttributes().putAttribute(SynpopConstants.SIZE, household.getMemberIds().size());
            household.getAttributes().putAttribute(SynpopConstants.LANGUAGE, minLanguage);

            if (numberOfChildren > 0) {
                for (Id<Person> memberId : household.getMemberIds()) {
                    Person person = scenario.getPopulation().getPersons().get(memberId);
                    person.getAttributes().putAttribute(SynpopConstants.HAS_CHILD_IN_HH, true);
                }
            }

        }
        scenario.getPopulation().getPersons().values().stream().filter(person -> person.getAttributes().getAttribute(SynpopConstants.HAS_CHILD_IN_HH) == null).forEach(person -> person.getAttributes().putAttribute(SynpopConstants.HAS_CHILD_IN_HH, false));
        log.info("Done. Calculating household compositions.");
    }

    public static <T> Random getRandom(Id<T> identifiable, String modelName) {
        HashFunction hashFunc = Hashing.murmur3_128();
        long seed = hashFunc.hashUnencodedChars(identifiable.toString() + modelName).asLong();
        return new Random(seed);

    }

    public static <T> Random getRandom(Id<T> identifiable) {
        return getRandom(identifiable, "");

    }

    public static void calculateDrivinglicensesPerHousehold(Scenario scenario) {
        log.info("Calculating driving licenses per household...");
        for (Household household : scenario.getHouseholds().getHouseholds().values()) {
            int drivingLicenseCount = 0;
            for (Id<Person> memberId : household.getMemberIds()) {
                Person person = scenario.getPopulation().getPersons().get(memberId);
                if (person.getAttributes().getAttribute(SynpopConstants.HAS_DRIVING_LICENSE) != null) {
                    Boolean hasDrivingLicense = (Boolean) person.getAttributes().getAttribute(SynpopConstants.HAS_DRIVING_LICENSE);
                    if (hasDrivingLicense) {
                        drivingLicenseCount++;
                    }
                }
            }
            household.getAttributes().putAttribute(SynpopConstants.NB_OF_DRIVING_LICENSES, drivingLicenseCount);
            Integer numberOfAdults = (Integer) household.getAttributes().getAttribute(SynpopConstants.NB_OF_ADULTS);
            double drivingLicensesPerAdult = (double) Math.min(drivingLicenseCount, numberOfAdults) / Math.max(numberOfAdults, 1);
            household.getAttributes().putAttribute(SynpopConstants.NB_OF_DRIVING_LICENSES_PER_ADULT, drivingLicensesPerAdult);
        }
        log.info("Done. Calculating driving licenses per household.");
    }

    public static void setCarAvailableForPopulation(Scenario scenario) {
        log.info("Setting car availability for population...");
        List<Person> students = new ArrayList<>();
        List<Person> studentsInHouseholdsWithCars = new ArrayList<>();
        for (Person person : scenario.getPopulation().getPersons().values()) {
            if (AttributeUtils.getAsInteger(person, SynpopConstants.CURRENT_EDU, -1) == 4) {
                students.add(person);
                Integer numberOfCars = (Integer) ((Household) person.getAttributes().getAttribute(SynpopConstants.HOUSEHOLD)).getAttributes().getAttribute(SynpopConstants.NB_OF_CARS);
                if (numberOfCars != null && numberOfCars > 0) {
                    studentsInHouseholdsWithCars.add(person);
                }
            }
        }
        studentsInHouseholdsWithCars.sort(Comparator.comparingDouble(person -> SynPopUtils.getRandom(person.getId(), "car_availability_students").nextDouble()));

        int targetStudentCarAvailability = (int) Math.round(students.size() * 0.20);
        int selectedStudents = Math.min(targetStudentCarAvailability, studentsInHouseholdsWithCars.size());
        Set<Id<Person>> studentsWithCarAvailability = new HashSet<>();
        for (int i = 0; i < selectedStudents; i++) {
            studentsWithCarAvailability.add(studentsInHouseholdsWithCars.get(i).getId());
        }

        if (selectedStudents < targetStudentCarAvailability) {
            log.warn("Could not reach 20% student car availability because only {} students are in households with cars (target: {}).", selectedStudents, targetStudentCarAvailability);
        }

        for (Person person : scenario.getPopulation().getPersons().values()) {
            if (AttributeUtils.getAsInteger(person, SynpopConstants.CURRENT_EDU, -1) == 4) {
                person.getAttributes().putAttribute(SynpopConstants.CAR_AVAILABLE, studentsWithCarAvailability.contains(person.getId()));
                continue;
            }

            Boolean hasDrivingLicense = AttributeUtils.hasDrivingLicense(person);
            if (hasDrivingLicense) {
                Integer numberOfCars = (Integer) ((Household) person.getAttributes().getAttribute(SynpopConstants.HOUSEHOLD)).getAttributes().getAttribute(SynpopConstants.NB_OF_CARS);
                if (numberOfCars != null && numberOfCars > 0) {
                    person.getAttributes().putAttribute(SynpopConstants.CAR_AVAILABLE, true);
                } else {
                    person.getAttributes().putAttribute(SynpopConstants.CAR_AVAILABLE, false);
                }
            } else {
                person.getAttributes().putAttribute(SynpopConstants.CAR_AVAILABLE, false);
            }
        }
        log.info("Done. Setting car availability for population.");
    }

    public static void aggregateMobilityToolsPerZone(Scenario scenario) {
        log.info("Aggregating mobility tools per zone...");
        Zones zones = (Zones) scenario.getScenarioElement(Variables.ZONES);
        Map<Id<Zone>, MobilityToolCount> zoneMap = zones.getZones().stream().collect(HashMap::new, (map, zone) -> map.put(zone.getId(), new MobilityToolCount()), HashMap::putAll);
        for (Person person : scenario.getPopulation().getPersons().values()) {
            var zoneId = Id.create((String) person.getAttributes().getAttribute(SynpopConstants.ZONE_ID), Zone.class);
            MobilityToolCount mobilityToolCount = zoneMap.get(zoneId);
            Boolean hasDrivingLicense = (Boolean) person.getAttributes().getAttribute(SynpopConstants.HAS_DRIVING_LICENSE);
            Boolean hasEbike = (Boolean) person.getAttributes().getAttribute(SynpopConstants.HAS_EBIKE45);
            Boolean carAvailable = (Boolean) person.getAttributes().getAttribute(SynpopConstants.CAR_AVAILABLE);
            Variables.PTSubscriptionType ptSubscription = Variables.PTSubscriptionType.valueOf((String) person.getAttributes().getAttribute(Variables.PT_SUBSCRIPTION));
            mobilityToolCount.addMobilityTool(hasDrivingLicense != null && hasDrivingLicense, hasEbike != null && hasEbike, carAvailable != null && carAvailable, ptSubscription);
        }
        for (Household household : scenario.getHouseholds().getHouseholds().values()) {
            var zoneId = Id.create((String) household.getAttributes().getAttribute(SynpopConstants.ZONE_ID), Zone.class);
            MobilityToolCount mobilityToolCount = zoneMap.get(zoneId);
            Integer numberOfCars = (Integer) household.getAttributes().getAttribute(SynpopConstants.NB_OF_CARS);
            mobilityToolCount.carCount().addAndGet(numberOfCars);

        }
        for (Map.Entry<Id<Zone>, MobilityToolCount> entry : zoneMap.entrySet()) {
            Zone zone = zones.getZone(entry.getKey());
            MobilityToolCount mobilityToolCount = entry.getValue();
            zone.getAttributes().putAttribute(ZonalAttributes.POP_DL, mobilityToolCount.drivingLicenseCount().get());
            zone.getAttributes().putAttribute(ZonalAttributes.POP_CARAVL, mobilityToolCount.carAvailableCount().get());
            zone.getAttributes().putAttribute(ZonalAttributes.POP_CARS, mobilityToolCount.carCount().get());
            zone.getAttributes().putAttribute(ZonalAttributes.POP_GA, mobilityToolCount.gaCount().get());
            zone.getAttributes().putAttribute(ZonalAttributes.POP_VA, mobilityToolCount.vaCount().get());
            zone.getAttributes().putAttribute(ZonalAttributes.POP_HT, mobilityToolCount.htCount().get());
            zone.getAttributes().putAttribute(ZonalAttributes.POP_VA_HT, mobilityToolCount.htvCount().get());
            zone.getAttributes().putAttribute(ZonalAttributes.POP_EBIKE45, mobilityToolCount.ebikeCount().get());
            double motorisationLevel = (double) mobilityToolCount.carAvailableCount().get() / AttributeUtils.getAsDouble(zone, ZonalAttributes.POP_TOTAL, 1);
            zone.getAttributes().putAttribute(ZonalAttributes.MOTORISATION_LEVEL, motorisationLevel);
        }
        log.info("Done. Aggregating mobility tools per zone.");
    }

    public static void aggregatePrimaryLocationChoicePerZone(Scenario scenario) {
        log.info("Aggregating primary location choices per zone...");
        Zones zones = (Zones) scenario.getScenarioElement(Variables.ZONES);
        Map<Id<Zone>, PrimaryLocationCount> zoneMap = zones.getZones().stream().collect(HashMap::new, (map, zone) -> map.put(zone.getId(), new PrimaryLocationCount()), HashMap::putAll);
        for (Person person : scenario.getPopulation().getPersons().values()) {
            String workZoneNo = (String) person.getAttributes().getAttribute("work_zone_no");
            String schoolZoneNo = (String) person.getAttributes().getAttribute("school_zone_no");
            if (workZoneNo != null) {
                zoneMap.get(Id.create(workZoneNo, Zone.class)).workCount.incrementAndGet();
            }
            if (schoolZoneNo != null) {
                zoneMap.get(Id.create(schoolZoneNo, Zone.class)).schoolCount.incrementAndGet();
            }

        }
        for (Map.Entry<Id<Zone>, PrimaryLocationCount> entry : zoneMap.entrySet()) {
            Zone zone = zones.getZone(entry.getKey());
            PrimaryLocationCount count = entry.getValue();
            zone.getAttributes().putAttribute("pop_work", count.workCount.get());
            zone.getAttributes().putAttribute("pop_school", count.schoolCount.get());
        }
        log.info("Done.");
    }

    public static void linkZonesToFacilities(Scenario scenario, boolean allowNullZones) {
        log.info("Matching facilities to zones...");
        final boolean[] warnedAboutNullZones = {false};
        Zones zones = (Zones) scenario.getScenarioElement(Variables.ZONES);
        scenario.getActivityFacilities().getFacilities().values().parallelStream().forEach(activityFacility -> {
            Zone zone = zones.findZone(activityFacility.getCoord());
            if (zone != null) {
                activityFacility.getAttributes().putAttribute(SynpopConstants.ZONE, zone);
                activityFacility.getAttributes().putAttribute(SynpopConstants.ZONE_ID, zone.getId().toString());
            } else {
                if (allowNullZones) {
                    activityFacility.getAttributes().putAttribute(SynpopConstants.ZONE, null);
                    activityFacility.getAttributes().putAttribute(SynpopConstants.ZONE_ID, null);
                    if (!warnedAboutNullZones[0]) {
                        log.warn("Some facilities have no zone assigned. This should only be allowed in test cases.");
                        warnedAboutNullZones[0] = true;
                    }
                } else {
                    throw new RuntimeException("Some facilities have no zone and this is not allowed.");
                }
            }

        });
        log.info("Done.");
    }


    public static void setDefaultAttributes(Scenario scenario) {
        TreeMap<Integer, Integer> levelOfEmploymentCategories = new TreeMap<>(SynpopConstants.LEVEL_OF_EMPLOYMENT_CAT_BOUNDS);
        scenario.getPopulation().getPersons().values().parallelStream().forEach(person -> {
            person.getAttributes().putAttribute(SynpopConstants.NET_DIST_SCHOOL, 0.0);
            person.getAttributes().putAttribute(SynpopConstants.NET_DIST_WORK, 0.0);
            person.getAttributes().putAttribute(SynpopConstants.CAR_AVAILABLE, false);
            int loe = ((Number) person.getAttributes().getAttribute(SynpopConstants.LEVEL_OF_EMPLOYMENT)).intValue();
            int loeCat = levelOfEmploymentCategories.floorEntry(loe).getValue();
            person.getAttributes().putAttribute(SynpopConstants.LEVEL_OF_EMPLOYMENT_CAT, loeCat);

        });
        Set<Id<ActivityFacility>> facilitiyIdsWithoutSector = new HashSet<>();
        scenario.getActivityFacilities().getFacilities().values().stream().filter(facility -> !facility.getId().toString().contains("hh_")).filter(facility -> -1 == AttributeUtils.getAsInteger(facility, SynpopConstants.SECTOR, -1)).forEach(facility -> {
            facility.getAttributes().putAttribute(SynpopConstants.SECTOR, 0);
            facilitiyIdsWithoutSector.add(facility.getId());
        });
        if (!facilitiyIdsWithoutSector.isEmpty()) {
            log.warn(facilitiyIdsWithoutSector.size() + " facilities have no sector assigned. Setting sector to 0 for these facilities. Facility IDs: " + facilitiyIdsWithoutSector);
        }
    }

    public static void createFacilitiesForHouseholds(Scenario scenario) {
        ActivityFacilitiesFactory factory = scenario.getActivityFacilities().getFactory();

        for (Household household : scenario.getHouseholds().getHouseholds().values()) {
            String facilityId = "hh_" + household.getId();
            ActivityFacility activityFacility = factory.createActivityFacility(Id.create(facilityId, ActivityFacility.class), (Coord) household.getAttributes().getAttribute(SynpopConstants.COORD));
            scenario.getActivityFacilities().addActivityFacility(activityFacility);
            household.getAttributes().putAttribute(SynpopConstants.FACILITY, activityFacility);
        }
    }

    record MobilityToolCount(AtomicInteger drivingLicenseCount, AtomicInteger ebikeCount,
                             AtomicInteger carAvailableCount, AtomicInteger carCount, AtomicInteger gaCount,
                             AtomicInteger htCount, AtomicInteger vaCount, AtomicInteger htvCount) {
        MobilityToolCount() {
            this(new AtomicInteger(), new AtomicInteger(), new AtomicInteger(), new AtomicInteger(), new AtomicInteger(), new AtomicInteger(), new AtomicInteger(), new AtomicInteger());
        }

        void addMobilityTool(boolean hasDrivingLicense, boolean hasEbike, boolean hasCarAvailable, Variables.PTSubscriptionType ptSubscription) {
            if (hasDrivingLicense) {
                drivingLicenseCount.incrementAndGet();
            }
            if (hasEbike) {
                ebikeCount.incrementAndGet();
            }
            if (hasCarAvailable) {
                carAvailableCount.incrementAndGet();
            }
            if (ptSubscription != null) {
                switch (ptSubscription) {
                    case GA -> gaCount.incrementAndGet();
                    case HTA -> htCount.incrementAndGet();
                    case VA -> vaCount.incrementAndGet();
                    case HTV -> {
                        htvCount.incrementAndGet();
                        vaCount.incrementAndGet();
                        htCount.incrementAndGet();
                    }
                }
            }

        }
    }

    static class PrimaryLocationCount {
        AtomicInteger workCount = new AtomicInteger();
        AtomicInteger schoolCount = new AtomicInteger();
    }
}
