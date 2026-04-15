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
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.population.Person;
import org.matsim.facilities.ActivityFacility;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

public class CalcZonalAttributes implements ModelStep {
    private final String name = MOBiPlansRunner.CALC_ZONAL_ATTRIBUTES;
    private final Logger logger = LogManager.getLogger(CalcZonalAttributes.class);
    private final MOBiPlansConfig config;
    private final Scenario scenario;
    private final Zones zones;
    private final Path dataFolder;

    // Configure these to match your SynPop attribute naming
    // Example age group variables similar to sc.SynpopAttrs.person_age_groups_vars in Python
    // The code parses the lower bound from each label (e.g., "age_00_04" -> 0).
    private final List<String> PERSON_AGE_GROUP_VARS = Arrays.asList(
            ZonalAttributes.POP_0017, ZonalAttributes.POP_1824, ZonalAttributes.POP_2544, ZonalAttributes.POP_4564, ZonalAttributes.POP_6574,
            ZonalAttributes.POP_75XX
    );

    // Education category codes expected on current_edu attribute
    private static final String EDU_PUPIL_PRIMARY = "2";
    private static final String EDU_PUPIL_SECONDARY = "3";
    private static final String EDU_STUDENT = "4";
    private static final String EDU_APPRENTICE = "5";



    @Inject
    public CalcZonalAttributes(MOBiPlansConfig config, Scenario scenario) {
        this.config = config;
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

    public void aggregateSynpop() {
        logger.info("Calculating zonal aggregates from SynPop...");

        // Prepare zone maps and initialize aggregates
        Map<String, Zone> zoneById = indexZonesById(zones);
        Map<String, Aggregates> aggByZone = new HashMap<>(zoneById.size());
        initializeAggregates(zoneById, aggByZone);

        // 1) Aggregates based on persons
        logger.info("Calculating zonal aggregates based on Persons...");
        processPersons(aggByZone, zoneById);

        // 2) Aggregates based on businesses (locations)
        logger.info("Calculating zonal aggregates based on Businesses...");
        processBusinesses(aggByZone, zoneById);

        // 3) Calculate density of residents + fte_total per area_land
        logger.info("Calculating density per zone...");
        computeDensity(aggByZone);

        // 4) Store results to Zones
        logger.info("Storing results on zones...");
        writeBackToZones(aggByZone, zoneById);

        logger.info("Finished calculating Zonal Aggregates.");
    }

    public void calcAttractions() {
        logger.info("Calculating zonal destination-choice attractions...");

        // Resolve CSV paths from config (adjust keys to your config if they differ)
        String ratesPath = dataFolder.resolve(MOBiPlansConfig.ATTRACTION_RATES).toString();
        String foreignPath = config.data().foreign_attractions();
        if (ratesPath == null || ratesPath.isBlank()) {
            throw new IllegalStateException("Missing config value 'attractionRatesFile' for attraction rates CSV.");
        }

        // 1) Read attraction rates (rows indexed by 'attribute', columns are attraction outputs)
        // Build:
        // - attributes: ordered list of attribute names (row index)
        // - attractions: ordered list of columns (attraction names)
        // - coeffs[attrIndex][purposeIndex] = rate
        List<String> attractions;
        List<String> attributes = new ArrayList<>();
        double[][] coeffs;

        try (CSVReader reader = new CSVReader(ratesPath, ";")) {
            String[] cols = reader.getColumns();

            // Expect an index column named 'attribute'
            int attributeIdx = -1;
            for (int i = 0; i < cols.length; i++) {
                if ("attribute".equalsIgnoreCase(cols[i])) {
                    attributeIdx = i;
                    break;
                }
            }
            if (attributeIdx < 0) {
                throw new IllegalStateException("Attraction rates CSV must contain an 'attribute' column as index.");
            }
            // attractions are all columns except 'attribute' (keep order)
            attractions = new ArrayList<>();
            for (int i = 0; i < cols.length; i++) {
                if (i != attributeIdx) {
                    attractions.add(cols[i]);
                }
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
                if (attr == null || attr.isBlank()) {
                    // skip empty attribute rows
                    continue;
                }
                attributes.add(attr);
                for (int c = 0; c < attractions.size(); c++) {
                    String p = attractions.get(c);
                    coeffs[r][c] = parseDouble(row.get(p), 0.0);
                }
                r++;
            }
            // If some rows were empty and skipped, shrink arrays
            if (r < coeffs.length) {
                double[][] tmp = new double[r][attractions.size()];
                System.arraycopy(coeffs, 0, tmp, 0, r);
                coeffs = tmp;
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to read attraction rates CSV: " + ratesPath, e);
        }

        // 2) Read foreign zones attractions (index column 'zone_id', columns = attractions)
        // Build: foreignMap[zoneId] = double[attractions]
        Map<String, double[]> foreignMap = new HashMap<>();
        if (foreignPath != null) {
            try (CSVReader reader = new CSVReader(foreignPath, ";")) {
                String[] cols = reader.getColumns();
                int zoneIdIdx = -1;
                for (int i = 0; i < cols.length; i++) {
                    if ("zone_id".equalsIgnoreCase(cols[i])) {
                        zoneIdIdx = i;
                        break;
                    }
                }
                if (zoneIdIdx < 0) {
                    throw new IllegalStateException("Foreign attractions CSV must contain a 'zone_id' column as index.");
                }
                // Verify that all attractions exist as columns
                Set<String> colSet = new HashSet<>(Arrays.asList(cols));
                for (String p : attractions) {
                    if (!colSet.contains(p)) {
                        logger.warn("Foreign attractions CSV missing column '{}'. Values will default to 0.", p);
                    }
                }
                Map<String, String> line = reader.readLine();
                while (line != null) {
                    String zoneId = line.get(cols[zoneIdIdx]);
                    if (zoneId == null || zoneId.isBlank()) continue;
                    double[] vals = new double[attractions.size()];
                    for (int i = 0; i < attractions.size(); i++) {
                        String p = attractions.get(i);
                        vals[i] = parseDouble(line.get(p), 0.0);
                    }
                    foreignMap.put(zoneId, vals);
                    line = reader.readLine();
                }
            } catch (IOException e) {
                throw new RuntimeException("Failed to read foreign attractions CSV: " + foreignPath, e);
            }
        }

        // Threshold separating domestic vs foreign zones (as in Python)
        final long domesticThreshold = 710101001L;

        // 3) Compute attractions per zone
        List<Zone> allZones = zones.getZones();
        int nAttractions = attractions.size();

        // Prepare attribute-value buffer for dot product, re-used per zone
        // zoneAttrVals[a] = value of attribute 'attributes[a]' in current zone
        double[] zoneAttrVals = new double[attributes.size()];

        for (Zone z : allZones) {
            String zoneIdStr = z.getId().toString();
            long zoneIdNum;
            try {
                zoneIdNum = Long.parseLong(zoneIdStr);
            } catch (NumberFormatException ex) {
                // If zone id isn't numeric, treat as domestic by default
                zoneIdNum = Long.MIN_VALUE;
            }

            if (zoneIdNum < domesticThreshold) {
                // Domestic: attractions = dot( zone[attributes], coeffs )
                // Load zone attributes into vector
                for (int a = 0; a < attributes.size(); a++) {
                    String attrName = attributes.get(a);
                    zoneAttrVals[a] = asDouble(z.getAttributes().getAttribute(attrName), 0.0);
                }
                // Dot product for each purpose
                for (int j = 0; j < nAttractions; j++) {
                    double sum = 0.0;
                    for (int a = 0; a < attributes.size(); a++) {
                        sum += zoneAttrVals[a] * coeffs[a][j];
                    }
                    z.getAttributes().putAttribute(attractions.get(j), sum);
                }
            } else {
                // Foreign: copy values from foreign CSV (if available)
                double[] vals = foreignMap.get(zoneIdStr);
                if (vals == null) {
                    // Default zeros if not found
                    for (int j = 0; j < nAttractions; j++) {
                        z.getAttributes().putAttribute(attractions.get(j), 0.0);
                    }
                } else {
                    for (int j = 0; j < nAttractions; j++) {
                        z.getAttributes().putAttribute(attractions.get(j), vals[j]);
                    }
                }
            }
        }

        logger.info("Finished calculating zonal destination-choice attractions.");
    }

    public void calcAccessTimesAndParkingCosts() {
        logger.info("Calculating access times and parking costs (car, ride) from density changes...");

        // Path to special cases CSV from config (adjust the config key if yours differs)

        String specialCasesPath = dataFolder.resolve(MOBiPlansConfig.SPECIAL_CASES_ATIMES_PCOSTS).toString();

        // Read special cases keyed by zone_id
        Map<String, Map<String, String>> specialCases = new HashMap<>();
        try (CSVReader reader = new CSVReader(specialCasesPath, ";")) {
            String[] cols = reader.getColumns();
            int zoneIdIdx = -1;
            for (int i = 0; i < cols.length; i++) {
                if ("zone_id".equalsIgnoreCase(cols[i])) {
                    zoneIdIdx = i;
                    break;
                }
            }
            if (zoneIdIdx < 0) {
                throw new IllegalStateException("Special cases CSV must contain a 'zone_id' column.");
            }
            Map<String, String> line = reader.readLine();
            while (line != null) {
                String zoneId = line.get(cols[zoneIdIdx]);
                if (zoneId == null || zoneId.isBlank()) continue;
                specialCases.put(zoneId, line);
                line = reader.readLine();
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to read special cases CSV: " + specialCasesPath, e);
        }

        // Sanity check: zone_ids unique in the CSV
        // (Map key uniqueness already enforces this; optional explicit check is omitted)

        // Iterate zones and compute
        for (Zone z : zones.getZones()) {
            String zoneIdStr = z.getId().toString();
            Map<String, String> scRow = specialCases.get(zoneIdStr);

            // Read density and baselines (from CSV; else default)
            double density      = asDouble(z.getAttributes().getAttribute("density"), Double.NaN);
            double density2017  = readFallback(scRow, "density_2017", Double.NaN);

            double atCar2017    = readFallback(scRow, "at_car_2017", 0.0);
            double atRide2017   = readFallback(scRow, "at_ride_2017", 0.0);
            double pcCar2017    = readFallback(scRow, "pc_car_2017", 0.0);
            double pcRide2017   = readFallback(scRow, "pc_ride_2017", 0.0);

            // Special-case base values (if provided), else treat as missing (-99)
            double atCarSpecial = scRow != null ? parseDouble(scRow.get("at_car"), -99.0) : -99.0;
            double pcCarSpecial = scRow != null ? parseDouble(scRow.get("pc_car"), -99.0) : -99.0;

            // Compute density factor
            double densityFactor;
            if (Double.isNaN(density) || Double.isNaN(density2017) || density2017 <= 0) {
                densityFactor = 1.0; // cannot compute scaling; keep baseline
            } else {
                densityFactor = Math.sqrt(density / density2017);
                if (densityFactor < 1.0) densityFactor = 1.0; // np.maximum(..., 1.0)
            }

            // ---- Access times (sec) ----
            double atCar;
            boolean specialAt = atCarSpecial > -99.0;
            if (specialAt) {
                // Allow increasing access times for special cases
                atCar = atCarSpecial * densityFactor;
            } else {
                // Base on density with min 45s
                atCar = Math.max(60.0 * density / 5600.0, 45.0);
                // Cap at 7.5 minutes scaled
                double maxAt = 60.0 * 7.5 * densityFactor;
                atCar = Math.min(atCar, maxAt);
            }
            // Never lower than 2017 baseline
            atCar = Math.max(atCar, atCar2017);

            // Ride: half of car with min 30s, then increase by delta from car vs 2017
            double atRide = Math.max(atCar / 2.0, 30.0);
            atRide = atRide2017 + atCar - atCar2017;

            if (atCar < 0 || atRide < 0) {
                throw new IllegalStateException("Access times must not be negative for zone " + zoneIdStr
                        + " (at_car=" + atCar + ", at_ride=" + atRide + ").");
            }

            // Round at_car and at_ride like DataFrame.round(0)
            double atCarRounded = Math.round(atCar);
            double atRideRounded = Math.round(atRide);

            // ---- Parking costs (CHF/hr) ----
            double pcCar;
            boolean specialPc = pcCarSpecial > -99.0;
            boolean specialPcZero = specialPc && pcCarSpecial == 0.0;

            if (specialPc) {
                // Allow increasing parking costs for special cases
                pcCar = pcCarSpecial * densityFactor;
            } else {
                // For low densities (pre-change < 629.495), cost 0; else exp model
                double preChangeDensity = (densityFactor == 0.0) ? 0.0 : density / densityFactor;
                if (preChangeDensity < 629.495) {
                    pcCar = 0.0;
                } else {
                    pcCar = Math.exp(-1.5571 + 0.4567 * Math.log(Math.max(density, 1e-9))) / 8.0;
                }
                // Cap at 5 CHF/hr scaled
                pcCar = Math.min(pcCar, 5.0 * densityFactor);
            }

            // Special-case with zero base cost responsive to densities
            double preChangeDensity = (densityFactor == 0.0) ? 0.0 : density / densityFactor;
            if (specialPcZero && preChangeDensity >= 629.495) {
                double factor = Math.min(3.0 * Math.max(densityFactor - 1.0, 0.0), 1.0);
                double base = Math.exp(-1.5571 + 0.4567 * Math.log(Math.max(density, 1e-9))) / 8.0;
                pcCar = factor * base;
                // Cap at 0.5 * 5 * density_factor
                pcCar = Math.min(pcCar, 2.5 * densityFactor);
            }

            // Never lower than 2017 baseline
            pcCar = Math.max(pcCar, pcCar2017);

            // Ride is half of car, then increased a bit more
            double pcRide = (pcCar / 2.0);
            pcRide = 2.0 * pcRide - pcRide2017; // = pcCar - pcRide2017

            if (pcCar < 0 || pcRide < 0) {
                throw new IllegalStateException("Parking costs must not be negative for zone " + zoneIdStr
                        + " (pc_car=" + pcCar + ", pc_ride=" + pcRide + ").");
            }

            // ---- Write back to zone attributes ----
            z.getAttributes().putAttribute(ZonalAttributes.AT_CAR, atCarRounded);
            z.getAttributes().putAttribute(ZonalAttributes.AT_RIDE, atRideRounded);
            z.getAttributes().putAttribute(ZonalAttributes.PC_CAR, pcCar);
            z.getAttributes().putAttribute(ZonalAttributes.PC_RIDE, pcRide);
        }

        logger.info("Finished calculating access times and parking costs.");
    }

    private static double readFallback(Map<String, String> csvRow, String key, double defaultVal) {
        if (csvRow != null) {
            return parseDouble(csvRow.get(key), defaultVal);
        }
        return defaultVal;
    }

    private Map<String, Zone> indexZonesById(Zones zones) {
        Map<String, Zone> map = new HashMap<>();
        // Adapt the iteration over your Zones implementation
        for (Zone z : zones.getZones()) {
            map.put(z.getId().toString(), z);
        }
        return map;
    }

    private void initializeAggregates(Map<String, Zone> zoneById, Map<String, Aggregates> aggByZone) {
        for (Map.Entry<String, Zone> e : zoneById.entrySet()) {
            String zId = e.getKey();
            Zone z = e.getValue();

            Aggregates a = new Aggregates(PERSON_AGE_GROUP_VARS);
            a.areaLand = readDoubleAttribute(z, ZonalAttributes.AREA_LAND, 0.0);
            aggByZone.put(zId, a);
        }
    }

    private void processPersons(Map<String, Aggregates> aggByZone, Map<String, Zone> zoneById) {
        Map<String, Integer> ageGroupLowerBounds = computeAgeGroupLowerBounds(PERSON_AGE_GROUP_VARS);
        List<String> orderedAgeLabels = orderAgeLabelsByLowerBound(PERSON_AGE_GROUP_VARS, ageGroupLowerBounds);

        for (Person p : scenario.getPopulation().getPersons().values()) {
            String zoneId = asString(p.getAttributes().getAttribute(SynpopConstants.ZONE_ID));
            if (zoneId == null) {
                continue; // person without zone
            }
            Aggregates a = aggByZone.get(zoneId);
            if (a == null) {
                // Zone not present in our zones set => skip
                continue;
            }

            // Total population
            a.pop_total++;

            // Employment
            double levelOfEmployment = asDouble(p.getAttributes().getAttribute(SynpopConstants.LEVEL_OF_EMPLOYMENT), Double.NaN);
            if (!Double.isNaN(levelOfEmployment) && levelOfEmployment > 0.0) {
                a.pop_empl++;
            }

            // Age group counts
            Integer age = asInteger(p.getAttributes().getAttribute(SynpopConstants.AGE));
            if (age != null && age >= 0) {
                String label = findAgeLabel(age, orderedAgeLabels, ageGroupLowerBounds);
                if (label != null) {
                    a.ageGroupCounts.merge(label, 1L, Long::sum);
                }
            }

            // Education categories
            String edu = asString(p.getAttributes().getAttribute(SynpopConstants.CURRENT_EDU));
            if (edu != null) {
                switch (edu) {
                    case EDU_PUPIL_PRIMARY -> a.pop_pup_1++;
                    case EDU_PUPIL_SECONDARY -> a.pop_pup_2++;
                    case EDU_STUDENT -> a.pop_stud_3++;
                    case EDU_APPRENTICE -> a.pop_appr++;
                    default -> { /* ignore other categories */ }
                }
            }
        }
    }

    private void processBusinesses(Map<String, Aggregates> aggByZone, Map<String, Zone> zoneById) {
        for (ActivityFacility f : scenario.getActivityFacilities().getFacilities().values()) {
            String zoneId = f.getAttributes().getAttribute(SynpopConstants.ZONE_ID).toString();
            if (zoneId == null) continue;
            Aggregates a = aggByZone.get(zoneId);
            if (a == null) continue; // zone not modeled

            double jobsEndo = parseDouble(f.getAttributes().getAttribute(SynpopConstants.JOBS_ENDO).toString(), 0.0);
            double jobsExo = parseDouble(f.getAttributes().getAttribute(SynpopConstants.JOBS_EXO).toString(), 0.0);
            double fteEndo = parseDouble(f.getAttributes().getAttribute(SynpopConstants.FTE_ENDO).toString(), 0.0);
            double fteExo = parseDouble(f.getAttributes().getAttribute(SynpopConstants.FTE_EXO).toString(), 0.0);

            a.jobs_endo += jobsEndo;
            a.jobs_exo += jobsExo;
            a.fte_endo += fteEndo;
            a.fte_exo += fteExo;
        }

        // Compute total
        for (Aggregates a : aggByZone.values()) {
            a.jobs_total = a.jobs_endo + a.jobs_exo;
            a.fte_total  = a.fte_endo + a.fte_exo;
        }
    }

    private void computeDensity(Map<String, Aggregates> aggByZone) {
        for (Aggregates a : aggByZone.values()) {
            double pop = a.pop_total;
            double fte = a.fte_total;
            double area = a.areaLand;

            if (Double.isNaN(pop)) pop = 0;
            if (Double.isNaN(fte)) fte = 0;
            if (Double.isNaN(area) || area <= 0) {
                a.density = 0.0;
            } else {
                a.density = (pop + fte) / area;
            }
        }
    }

    private void writeBackToZones(Map<String, Aggregates> aggByZone, Map<String, Zone> zoneById) {
        for (Map.Entry<String, Zone> e : zoneById.entrySet()) {
            String zoneId = e.getKey();
            Zone z = e.getValue();
            Aggregates a = aggByZone.get(zoneId);
            if (a == null) continue;

            // Age groups
            for (String label : PERSON_AGE_GROUP_VARS) {
                long cnt = a.ageGroupCounts.getOrDefault(label, 0L);
                z.getAttributes().putAttribute(label, cnt);
            }

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

    // Helpers

    private static class Aggregates {
        double areaLand;
        // persons
        long pop_total = 0;
        long pop_empl = 0;
        long pop_pup_1 = 0;
        long pop_pup_2 = 0;
        long pop_stud_3 = 0;
        long pop_appr = 0;
        Map<String, Long> ageGroupCounts = new HashMap<>();
        // businesses
        double jobs_endo = 0;
        double jobs_exo = 0;
        double jobs_total = 0;
        double fte_endo = 0;
        double fte_exo = 0;
        double fte_total = 0;
        // derived
        double density = 0;

        Aggregates(List<String> ageGroupLabels) {
            for (String l : ageGroupLabels) {
                ageGroupCounts.put(l, 0L);
            }
        }
    }

    private static Map<String, Integer> computeAgeGroupLowerBounds(List<String> labels) {
        // labels are pop_0017, pop_1844, etc.
        Map<String, Integer> map = new HashMap<>();
        for (String l : labels) {
            map.put(l, Integer.parseInt(l.substring(4, 6)));
        }
        return map;
    }

    private static List<String> orderAgeLabelsByLowerBound(List<String> labels, Map<String, Integer> lowerBounds) {
        List<String> ordered = new ArrayList<>(labels);
        ordered.sort(Comparator.comparingInt(lowerBounds::get));
        return ordered;
    }

    private static String findAgeLabel(int age, List<String> orderedLabels, Map<String, Integer> lowerBounds) {
        // Equivalent to pandas.cut with right=false and labels ordered by lower bound.
        // We create artificial upper bound 1000 like in Python.
        for (int i = 0; i < orderedLabels.size(); i++) {
            String label = orderedLabels.get(i);
            int lower = lowerBounds.get(label);
            int upper = (i + 1 < orderedLabels.size()) ? lowerBounds.get(orderedLabels.get(i + 1)) : 1000;
            if (age >= lower && age < upper) {
                return label;
            }
        }
        return null;
    }

    private static double readDoubleAttribute(Zone z, String key, double defaultVal) {
        Object o = z.getAttributes().getAttribute(key);
        return asDouble(o, defaultVal);
    }

    private static String asString(Object o) {
        return (o == null) ? null : String.valueOf(o);
    }

    private static Integer asInteger(Object o) {
        if (o == null) return null;
        if (o instanceof Integer i) return i;
        if (o instanceof Number n) return n.intValue();
        try {
            return Integer.parseInt(o.toString());
        } catch (Exception e) {
            return null;
        }
    }

    private static double asDouble(Object o, double defaultVal) {
        if (o == null) return defaultVal;
        if (o instanceof Number n) return n.doubleValue();
        try {
            return Double.parseDouble(o.toString());
        } catch (Exception e) {
            return defaultVal;
        }
    }

    private static double parseDouble(String s, double defaultVal) {
        if (s == null || s.isEmpty()) return defaultVal;
        try {
            return Double.parseDouble(s);
        } catch (Exception e) {
            return defaultVal;
        }
    }

    public String getName() {
        return name;
    }

}