package ch.sbb.matsim.plans.synpop;

import ch.sbb.matsim.config.variables.Variables;
import ch.sbb.matsim.plans.constants.SynpopConstants;
import ch.sbb.matsim.plans.constants.ZonalAttributes;
import ch.sbb.matsim.zones.Zone;
import ch.sbb.matsim.zones.Zones;
import com.google.common.hash.HashFunction;
import com.google.common.hash.Hashing;
import org.apache.logging.log4j.LogManager;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.population.Person;
import org.matsim.households.Household;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;

public class SynPopUtils {

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
            person.getAttributes().putAttribute(Variables.ZONE_ID,
                    household.getAttributes().getAttribute(Variables.ZONE_ID));
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
        LogManager.getLogger(SynPopUtils.class).info("Calculating household compositions...");
        for (Household household : scenario.getHouseholds().getHouseholds().values()) {
            Integer numberOfAdults = 0;
            Integer numberOfAdultsAbove24 = 0;
            Integer numberOfChildren = 0;
            Integer numberOfChildrenUnder24 = 0;
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
            Integer coupleType = 0;
            if (numberOfAdultsAbove24 >= 2 && numberOfAdultsAbove24 <= 3) {
                if (numberOfChildrenUnder24 > 0) {
                    coupleType = 1; // couple with children
                } else {
                    coupleType = 2; // couple without children
                }
            }
            Boolean singleParent = numberOfAdults == 1 && numberOfChildren > 0;
            // not more than 9 children and 11 adults per household according to the MTMC
            household.getAttributes().putAttribute(SynpopConstants.NB_OF_KIDS, Math.min(numberOfChildren, 9));
            household.getAttributes().putAttribute(SynpopConstants.NB_OF_ADULTS, Math.min(numberOfAdults, 11));
            household.getAttributes().putAttribute(SynpopConstants.HAS_CHILD_IN_HH, numberOfChildren > 0);
            household.getAttributes().putAttribute(SynpopConstants.COUPLE_TYPE, coupleType);
            household.getAttributes().putAttribute(SynpopConstants.SINGLE_PARENT, singleParent);
            household.getAttributes().putAttribute(SynpopConstants.SIZE, household.getMemberIds().size());
            household.getAttributes().putAttribute(SynpopConstants.LANGUAGE, minLanguage);

        }
        LogManager.getLogger(SynPopUtils.class).info("Done. Calculating household compositions.");
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
        LogManager.getLogger(SynPopUtils.class).info("Calculating driving licenses per household...");
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
            double drivingLicensesPerAdult = (double) Math.min(drivingLicenseCount, numberOfAdults) /
                    Math.max(numberOfAdults, 1);
            household.getAttributes().putAttribute(SynpopConstants.NB_OF_DRIVING_LICENSES_PER_ADULT, drivingLicensesPerAdult);
        }
        LogManager.getLogger(SynPopUtils.class).info("Done. Calculating driving licenses per household.");
    }

    public static void setCarAvailableForPopulation(Scenario scenario) {
        LogManager.getLogger(SynPopUtils.class).info("Setting car availability for population...");
        for (Person person : scenario.getPopulation().getPersons().values()) {
            Boolean hasDrivingLicense = (Boolean) person.getAttributes().getAttribute(SynpopConstants.HAS_DRIVING_LICENSE);
            if (hasDrivingLicense) {
                Integer numberOfCars = (Integer) ((Household) person.getAttributes().getAttribute(SynpopConstants.HOUSEHOLD)).getAttributes().getAttribute(SynpopConstants.NB_OF_CARS);
                if (numberOfCars != null && numberOfCars > 0) {
                    person.getAttributes().putAttribute(SynpopConstants.CAR_AVAILABLE, true);
                } else {
                    person.getAttributes().putAttribute(SynpopConstants.CAR_AVAILABLE, false);
                }
            }
        }
        LogManager.getLogger(SynPopUtils.class).info("Done. Setting car availability for population.");
    }

    public static void aggregateMobilityToolsPerZone(Scenario scenario) {
        LogManager.getLogger(SynPopUtils.class).info("Aggregating mobility tools per zone...");
        Zones zones = (Zones) scenario.getScenarioElement(Variables.ZONES);
        Map<Id<Zone>, MobilityToolCount> zoneMap = zones.getZones().stream()
                .collect(HashMap::new, (map, zone) -> map.put(zone.getId(), new MobilityToolCount()), HashMap::putAll);
        for (Person person : scenario.getPopulation().getPersons().values()) {
            var zoneId = Id.create((String) person.getAttributes().getAttribute(SynpopConstants.ZONE_ID), Zone.class);
            MobilityToolCount mobilityToolCount = zoneMap.get(zoneId);
            Boolean hasDrivingLicense = (Boolean) person.getAttributes().getAttribute(SynpopConstants.HAS_DRIVING_LICENSE);
            Boolean hasEbike = (Boolean) person.getAttributes().getAttribute(SynpopConstants.HAS_EBIKE45);
            Boolean carAvailable = (Boolean) person.getAttributes().getAttribute(SynpopConstants.CAR_AVAILABLE);
            Variables.PTSubscriptionType ptSubscription = Variables.PTSubscriptionType.valueOf((String) person.getAttributes().getAttribute(Variables.PT_SUBSCRIPTION));
            mobilityToolCount.addMobilityTool(hasDrivingLicense != null && hasDrivingLicense,
                    hasEbike != null && hasEbike,
                    carAvailable != null && carAvailable, ptSubscription);
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
        }
        LogManager.getLogger(SynPopUtils.class).info("Done. Aggregating mobility tools per zone.");
    }

    public static void aggregatePrimaryLocationChoicePerZone(Scenario scenario) {
        LogManager.getLogger(SynPopUtils.class).info("Aggregating primary location choices per zone...");
        Zones zones = (Zones) scenario.getScenarioElement(Variables.ZONES);
        Map<Id<Zone>, PrimaryLocationCount> zoneMap = zones.getZones().stream()
                .collect(HashMap::new, (map, zone) -> map.put(zone.getId(), new PrimaryLocationCount()), HashMap::putAll);
        for (Person person : scenario.getPopulation().getPersons().values()) {
            String workZoneNo = (String) person.getAttributes().getAttribute("work_zone_no");
            String schoolZoneNo = (String) person.getAttributes().getAttribute("school_zone_no");
            String zoneIdStr = (String) person.getAttributes().getAttribute(SynpopConstants.ZONE_ID);
            if (zoneIdStr != null) {
                Id<Zone> zoneId = Id.create(zoneIdStr, Zone.class);
                PrimaryLocationCount count = zoneMap.get(zoneId);
                if (workZoneNo != null) {
                    count.workCount.incrementAndGet();
                }
                if (schoolZoneNo != null) {
                    count.schoolCount.incrementAndGet();
                }
            }
        }
        for (Map.Entry<Id<Zone>, PrimaryLocationCount> entry : zoneMap.entrySet()) {
            Zone zone = zones.getZone(entry.getKey());
            PrimaryLocationCount count = entry.getValue();
            zone.getAttributes().putAttribute("pop_work", count.workCount.get());
            zone.getAttributes().putAttribute("pop_school", count.schoolCount.get());
        }
        LogManager.getLogger(SynPopUtils.class).info("Done. Aggregating primary location choices per zone.");
    }

    record MobilityToolCount(AtomicInteger drivingLicenseCount, AtomicInteger ebikeCount,
                             AtomicInteger carAvailableCount, AtomicInteger carCount, AtomicInteger gaCount,
                             AtomicInteger htCount, AtomicInteger vaCount, AtomicInteger htvCount) {
        MobilityToolCount() {
            this(new AtomicInteger(), new AtomicInteger(), new AtomicInteger(),
                    new AtomicInteger(), new AtomicInteger(), new AtomicInteger(),
                    new AtomicInteger(), new AtomicInteger());
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
