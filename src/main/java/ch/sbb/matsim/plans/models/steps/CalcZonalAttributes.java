package ch.sbb.matsim.plans.models.steps;

import ch.sbb.matsim.config.variables.Variables;
import ch.sbb.matsim.csv.CSVReader;
import ch.sbb.matsim.plans.MOBiPlansRunner;
import ch.sbb.matsim.plans.constants.SynpopConstants;
import ch.sbb.matsim.plans.constants.ZonalAttributes;
import ch.sbb.matsim.plans.models.config.MOBiPlansConfig;
import ch.sbb.matsim.zones.Zone;
import ch.sbb.matsim.zones.Zones;
import jakarta.inject.Inject;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.population.Person;
import org.matsim.facilities.ActivityFacility;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

public class CalcZonalAttributes implements ModelStep {

    private final Logger logger = LogManager.getLogger(CalcZonalAttributes.class);

    private final Scenario scenario;
    private final Zones zones;
    private final Path dataFolder;

    // Constants for age group indices in our primitive array
    private static final int AGE_00_17 = 0;
    private static final int AGE_18_24 = 1;
    private static final int AGE_25_44 = 2;
    private static final int AGE_45_64 = 3;
    private static final int AGE_65_74 = 4;
    private static final int AGE_75_XX = 5;

    // Education category codes expected on current_edu attribute
    private static final int EDU_PUPIL_PRIMARY = 2;
    private static final int EDU_PUPIL_SECONDARY = 3;
    private static final int EDU_STUDENT = 4;
    private static final int EDU_APPRENTICE = 5;

    @Inject
    public CalcZonalAttributes(MOBiPlansConfig config, Scenario scenario) {
        this.scenario = scenario;
        this.zones = (Zones) scenario.getScenarioElement(Variables.ZONES);
        this.dataFolder = Paths.get(config.data().modelBaseDir()).resolve(MOBiPlansConfig.MODEL_BASE_FOLDER_DATA);
    }

    @Override
    public void run() {
        aggregateSynpop();
        calcAttractions();
        calcAccessTimesAndParkingCosts();
    }

    @Override
    public String getName() {
        return MOBiPlansRunner.CALC_ZONAL_ATTRIBUTES;
    }

    public void aggregateSynpop() {
        logger.info("Calculating zonal aggregates from SynPop...");

        Map<Id<Zone>, Aggregates> aggByZone = new HashMap<>(zones.getZones().size());
        for (Zone z : zones.getZones()) {
            Aggregates a = new Aggregates();
            a.areaLand = readDoubleAttribute(z, ZonalAttributes.AREA_LAND, 0.0);
            aggByZone.put(z.getId(), a);
        }

        logger.info("  -> Calculating zonal aggregates based on Persons...");
        processPersons(aggByZone);

        logger.info("  -> Calculating zonal aggregates based on Businesses...");
        processBusinesses(aggByZone);

        logger.info("  -> Calculating density per zone...");
        computeDensity(aggByZone);

        logger.info("  -> Storing results on zones...");
        writeBackToZones(aggByZone);

        logger.info("Finished calculating Zonal Aggregates.");
    }

    private void processPersons(Map<Id<Zone>, Aggregates> aggByZone) {
        for (Person p : scenario.getPopulation().getPersons().values()) {
            Object zoneObj = p.getAttributes().getAttribute(SynpopConstants.ZONE_ID);
            if (zoneObj == null) continue;

            Id<Zone> zoneId = Id.create(zoneObj.toString(), Zone.class);
            Aggregates a = aggByZone.get(zoneId);
            if (a == null) continue;

            // Total population
            a.pop_total++;

            // Employment
            double levelOfEmployment = asDouble(p.getAttributes().getAttribute(SynpopConstants.LEVEL_OF_EMPLOYMENT), Double.NaN);
            if (!Double.isNaN(levelOfEmployment) && levelOfEmployment > 0.0) {
                a.pop_empl++;
            }

            // High-Performance Age categorization (Replaces Python/Pandas cut)
            int age = asInteger(p.getAttributes().getAttribute(SynpopConstants.AGE));
            if (age >= 0) {
                if (age < 18) a.ageGroupCounts[AGE_00_17]++;
                else if (age < 25) a.ageGroupCounts[AGE_18_24]++;
                else if (age < 45) a.ageGroupCounts[AGE_25_44]++;
                else if (age < 65) a.ageGroupCounts[AGE_45_64]++;
                else if (age < 75) a.ageGroupCounts[AGE_65_74]++;
                else a.ageGroupCounts[AGE_75_XX]++;
            }

            // Education categories (Using primitives instead of String switching)
            int edu = asInteger(p.getAttributes().getAttribute(SynpopConstants.CURRENT_EDU));
            switch (edu) {
                case EDU_PUPIL_PRIMARY -> a.pop_pup_1++;
                case EDU_PUPIL_SECONDARY -> a.pop_pup_2++;
                case EDU_STUDENT -> a.pop_stud_3++;
                case EDU_APPRENTICE -> a.pop_appr++;
            }
        }
    }

    private void processBusinesses(Map<Id<Zone>, Aggregates> aggByZone) {
        for (ActivityFacility f : scenario.getActivityFacilities().getFacilities().values()) {
            Object zoneAttr = f.getAttributes().getAttribute(SynpopConstants.ZONE_ID);
            if (zoneAttr == null) continue; // Fixed NPE trap

            Id<Zone> zoneId = Id.create(zoneAttr.toString(), Zone.class);
            Aggregates a = aggByZone.get(zoneId);
            if (a == null) continue;

            a.jobs_endo += asDouble(f.getAttributes().getAttribute(SynpopConstants.JOBS_ENDO), 0.0);
            a.jobs_exo += asDouble(f.getAttributes().getAttribute(SynpopConstants.JOBS_EXO), 0.0);
            a.fte_endo += asDouble(f.getAttributes().getAttribute(SynpopConstants.FTE_ENDO), 0.0);
            a.fte_exo += asDouble(f.getAttributes().getAttribute(SynpopConstants.FTE_EXO), 0.0);
        }

        // Compute totals
        for (Aggregates a : aggByZone.values()) {
            a.jobs_total = a.jobs_endo + a.jobs_exo;
            a.fte_total  = a.fte_endo + a.fte_exo;
        }
    }

    private void computeDensity(Map<Id<Zone>, Aggregates> aggByZone) {
        for (Aggregates a : aggByZone.values()) {
            if (a.areaLand <= 0) {
                a.density = 0.0;
            } else {
                a.density = (a.pop_total + a.fte_total) / a.areaLand;
            }
        }
    }

    private void writeBackToZones(Map<Id<Zone>, Aggregates> aggByZone) {
        for (Zone z : zones.getZones()) {
            Aggregates a = aggByZone.get(z.getId());
            if (a == null) continue;

            // Write Age Groups
            z.getAttributes().putAttribute(ZonalAttributes.POP_0017, a.ageGroupCounts[AGE_00_17]);
            z.getAttributes().putAttribute(ZonalAttributes.POP_1824, a.ageGroupCounts[AGE_18_24]);
            z.getAttributes().putAttribute(ZonalAttributes.POP_2544, a.ageGroupCounts[AGE_25_44]);
            z.getAttributes().putAttribute(ZonalAttributes.POP_4564, a.ageGroupCounts[AGE_45_64]);
            z.getAttributes().putAttribute(ZonalAttributes.POP_6574, a.ageGroupCounts[AGE_65_74]);
            z.getAttributes().putAttribute(ZonalAttributes.POP_75XX, a.ageGroupCounts[AGE_75_XX]);

            // Person aggregates
            z.getAttributes().putAttribute(ZonalAttributes.POP_PUP_1, a.pop_pup_1);
            z.getAttributes().putAttribute(ZonalAttributes.POP_PUP_2, a.pop_pup_2);
            z.getAttributes().putAttribute(ZonalAttributes.POP_STUD_3, a.pop_stud_3);
            z.getAttributes().putAttribute(ZonalAttributes.POP_APPR, a.pop_appr);
            z.getAttributes().putAttribute(ZonalAttributes.POP_TOTAL, a.pop_total);
            z.getAttributes().putAttribute(ZonalAttributes.POP_EMPL, a.pop_empl);

            // Business aggregates
            z.getAttributes().putAttribute(ZonalAttributes.JOBS_ENDO, a.jobs_endo);
            z.getAttributes().putAttribute(ZonalAttributes.JOBS_TOTAL, a.jobs_total);
            z.getAttributes().putAttribute(ZonalAttributes.JOBS_EXO, a.jobs_exo);
            z.getAttributes().putAttribute(ZonalAttributes.FTE_ENDO, a.fte_endo);
            z.getAttributes().putAttribute(ZonalAttributes.FTE_TOTAL, a.fte_total);

            // Density
            z.getAttributes().putAttribute(ZonalAttributes.DENSITY, a.density);
        }
    }

    public void calcAttractions() {
        logger.info("Calculating zonal destination-choice attractions...");

        String ratesPath = dataFolder.resolve(MOBiPlansConfig.ATTRACTION_RATES).toString();
        AttractionRates rates = getAttractionRates(ratesPath);

        int nAttractions = rates.attractions().size();
        int nAttributes = rates.attributes().size();
        double[] zoneAttrVals = new double[nAttributes];

        for (Zone z : zones.getZones()) {
            if (Variables.isSwissZone(z.getId())) {
                // Load zone attributes into vector
                for (int a = 0; a < nAttributes; a++) {
                    zoneAttrVals[a] = readDoubleAttribute(z, rates.attributes().get(a), 0.0);
                }

                // Dot product for each purpose
                for (int j = 0; j < nAttractions; j++) {
                    double sum = 0.0;
                    for (int a = 0; a < nAttributes; a++) {
                        sum += zoneAttrVals[a] * rates.coeffs()[a][j];
                    }
                    z.getAttributes().putAttribute(rates.attractions().get(j), sum);
                }
            }
        }
        logger.info("Finished calculating zonal destination-choice attractions.");
    }

    public void calcAccessTimesAndParkingCosts() {
        logger.info("Calculating access times and parking costs (car, ride) from density changes...");

        String specialCasesPath = dataFolder.resolve(MOBiPlansConfig.SPECIAL_CASES_ATIMES_PCOSTS).toString();
        Map<Id<Zone>, Map<String, String>> specialCases = readSpecialCases(specialCasesPath);

        for (Zone z : zones.getZones()) {
            Map<String, String> scRow = specialCases.get(z.getId());

            double density = readDoubleAttribute(z, ZonalAttributes.DENSITY, Double.NaN);
            double density2017 = readFallback(scRow, "density_2017", Double.NaN);
            double atCar2017 = readFallback(scRow, "at_car_2017", 0.0);
            double atRide2017 = readFallback(scRow, "at_ride_2017", 0.0);
            double pcCar2017 = readFallback(scRow, "pc_car_2017", 0.0);
            double pcRide2017 = readFallback(scRow, "pc_ride_2017", 0.0);

            double atCarSpecial = readFallback(scRow, ZonalAttributes.AT_CAR, -99.0);
            double pcCarSpecial = readFallback(scRow, ZonalAttributes.PC_CAR, -99.0);

            // Compute density factor
            double densityFactor = 1.0;
            if (!Double.isNaN(density) && !Double.isNaN(density2017) && density2017 > 0) {
                densityFactor = Math.max(Math.sqrt(density / density2017), 1.0);
            }

            // ---- Access times (sec) ----
            double atCar;
            if (atCarSpecial > -99.0) {
                atCar = atCarSpecial * densityFactor;
            } else {
                atCar = Math.min(Math.max(60.0 * density / 5600.0, 45.0), 60.0 * 7.5 * densityFactor);
            }
            atCar = Math.max(atCar, atCar2017);

            double atRide;
            if (atRide2017 > 0.0 && atCar2017 > 0.0) {
                // We have a 2017 baseline, so shift it by the exact same delta as the car
                atRide = atRide2017 + (atCar - atCar2017);
            } else {
                // No baseline exists, use the theoretical fallback (half of car, min 30s)
                atRide = Math.max(atCar / 2.0, 30.0);
            }

            // ---- Parking costs (CHF/hr) ----
            double pcCar;
            boolean specialPcZero = (pcCarSpecial == 0.0);

            double pcCar1 = Math.exp(-1.5571 + 0.4567 * Math.log(Math.max(density, 1e-9))) / 8.0;
            if (pcCarSpecial > -99.0 && !specialPcZero) {
                pcCar = pcCarSpecial * densityFactor;
            } else {
                double preChangeDensity = (densityFactor == 0.0) ? 0.0 : density / densityFactor;
                if (preChangeDensity < 629.495) {
                    pcCar = 0.0;
                } else {
                    pcCar = pcCar1;
                }
                pcCar = Math.min(pcCar, 5.0 * densityFactor);
            }

            if (specialPcZero) {
                double preChangeDensity = (densityFactor == 0.0) ? 0.0 : density / densityFactor;
                if (preChangeDensity >= 629.495) {
                    double factor = Math.min(3.0 * Math.max(densityFactor - 1.0, 0.0), 1.0);
                    pcCar = Math.min(factor * pcCar1, 2.5 * densityFactor);
                } else {
                    pcCar = 0.0;
                }
            }

            pcCar = Math.max(pcCar, pcCar2017);
            double pcRide = 2.0 * (pcCar / 2.0) - pcRide2017;

            // ---- Write back to zone attributes ----
            z.getAttributes().putAttribute(ZonalAttributes.AT_CAR, Math.round(atCar));
            z.getAttributes().putAttribute(ZonalAttributes.AT_RIDE, Math.round(atRide));
            z.getAttributes().putAttribute(ZonalAttributes.PC_CAR, pcCar);
            z.getAttributes().putAttribute(ZonalAttributes.PC_RIDE, pcRide);
        }
        logger.info("Finished calculating access times and parking costs.");
    }

    private static AttractionRates getAttractionRates(String ratesPath) {
        List<String> attractions = new ArrayList<>();
        List<String> attributes = new ArrayList<>();
        double[][] coeffs;

        try (CSVReader reader = new CSVReader(ratesPath, ";")) {
            String[] cols = reader.getColumns();
            int attributeIdx = -1;
            for (int i = 0; i < cols.length; i++) {
                if ("attribute".equalsIgnoreCase(cols[i])) {
                    attributeIdx = i; break;
                }
            }

            for (int i = 0; i < cols.length; i++) {
                if (i != attributeIdx) attractions.add(cols[i]);
            }

            List<Map<String, String>> rows = new ArrayList<>();
            var line = reader.readLine();
            while (line != null) {
                rows.add(line);
                line = reader.readLine();
            }

            coeffs = new double[rows.size()][attractions.size()];
            int r = 0;
            for (Map<String, String> row : rows) {
                String attr = row.get(cols[attributeIdx]);
                if (attr == null || attr.isBlank()) continue;

                attributes.add(attr);
                for (int c = 0; c < attractions.size(); c++) {
                    coeffs[r][c] = parseDouble(row.get(attractions.get(c)), 0.0);
                }
                r++;
            }

            // Shrink arrays if empty rows were skipped
            if (r < coeffs.length) {
                double[][] tmp = new double[r][attractions.size()];
                System.arraycopy(coeffs, 0, tmp, 0, r);
                coeffs = tmp;
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to read attraction rates CSV", e);
        }
        return new AttractionRates(attractions, attributes, coeffs);
    }

    private static Map<Id<Zone>, Map<String, String>> readSpecialCases(String specialCasesPath) {
        Map<Id<Zone>, Map<String, String>> specialCases = new HashMap<>();
        try (CSVReader reader = new CSVReader(specialCasesPath, ";")) {
            Map<String, String> line = reader.readLine();
            while (line != null) {
                String zoneIdStr = line.get(ZonalAttributes.ZONE_ID);
                if (zoneIdStr != null && !zoneIdStr.isBlank()) {
                    specialCases.put(Id.create(zoneIdStr, Zone.class), line);
                }
                line = reader.readLine();
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to read special cases CSV", e);
        }
        return specialCases;
    }

    // --- Helpers & Records ---

    private record AttractionRates(List<String> attractions, List<String> attributes, double[][] coeffs) {}

    private static class Aggregates {
        double areaLand = 0;
        long pop_total = 0, pop_empl = 0;
        long pop_pup_1 = 0, pop_pup_2 = 0, pop_stud_3 = 0, pop_appr = 0;

        // Primitive array instead of Map<String, Long> -> zero memory overhead
        long[] ageGroupCounts = new long[6];

        double jobs_endo = 0, jobs_exo = 0, jobs_total = 0;
        double fte_endo = 0, fte_exo = 0, fte_total = 0;
        double density = 0;
    }

    private static double readFallback(Map<String, String> csvRow, String key, double defaultVal) {
        return csvRow != null ? parseDouble(csvRow.get(key), defaultVal) : defaultVal;
    }

    private static double readDoubleAttribute(Zone z, String key, double defaultVal) {
        return asDouble(z.getAttributes().getAttribute(key), defaultVal);
    }

    private static int asInteger(Object o) {
        if (o == null) return -1;
        if (o instanceof Number n) return n.intValue();
        try { return Integer.parseInt(o.toString()); }
        catch (NumberFormatException e) { return -1; }
    }

    private static double asDouble(Object o, double defaultVal) {
        if (o == null) return defaultVal;
        if (o instanceof Number n) return n.doubleValue();
        try { return Double.parseDouble(o.toString()); }
        catch (NumberFormatException e) { return defaultVal; }
    }

    private static double parseDouble(String s, double defaultVal) {
        if (s == null || s.isEmpty()) return defaultVal;
        try { return Double.parseDouble(s); }
        catch (NumberFormatException e) { return defaultVal; }
    }
}