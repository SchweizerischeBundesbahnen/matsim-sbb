package ch.sbb.matsim.projects.genf2050;

import ch.sbb.matsim.config.variables.SBBModes;
import ch.sbb.matsim.csv.CSVReader;
import ch.sbb.matsim.zones.Zone;
import ch.sbb.matsim.zones.Zones;
import ch.sbb.matsim.zones.ZonesLoader;
import org.apache.logging.log4j.LogManager;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.population.*;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.gbl.MatsimRandom;
import org.matsim.core.population.io.PopulationReader;
import org.matsim.core.scenario.ScenarioUtils;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class AdjustExogeneousDemand {

    Population carDemand;
    Population ptDemand;
    Zones zones;

    ConcurrentHashMap<String, Set<Id<Person>>> carPopulationPerZone = new ConcurrentHashMap<>();

    Map<String, Integer> carCorrectionPerZone = new HashMap<>();
    Map<String, Integer> ptCorrectionPerZone = new HashMap<>();
    ConcurrentHashMap<String, Set<Id<Person>>> ptPopulationPerZone = new ConcurrentHashMap<>();
    Map<String, List<Coord>> insertionPointsPerZone = new HashMap<>();
    List<Coord> destinationCoords = List.of(new Coord(2499770.51984111, 1119868.9745656),
            new Coord(2500344.4893983, 1118336.75201323),
            new Coord(2500019.14977779, 1118468.80825104),
            new Coord(2500315.2699545, 1117674.92252405),
            new Coord(2501777.92744991, 1117380.68031702),
            new Coord(2500424.33691912, 1117335.93628518),
            new Coord(2499457.13748388, 1118288.85431251),
            new Coord(2499768.50295232, 1117729.29052459),
            new Coord(2497450.35929153, 1120741.93427062),
            new Coord(2501177.60514723, 1117759.2755544),
            new Coord(2508062.98980717, 1137471.09537986),
            new Coord(2537930.45351674, 1152090.45089376)

    );
    Random random = MatsimRandom.getRandom();

    public AdjustExogeneousDemand(Population carDemand, Population ptDemand, Zones zones, String alterationTable) {
        this.zones = zones;
        this.carDemand = carDemand;
        this.ptDemand = ptDemand;
        readAlterationTable(alterationTable);
        LogManager.getLogger(getClass()).info("Reading PT table");
        sortPopulationByZone(ptPopulationPerZone, ptDemand);
        LogManager.getLogger(getClass()).info("Reading Car table");
        sortPopulationByZone(carPopulationPerZone, carDemand);
    }

    public static void main(String[] args) {

        String inputCarDemand = "\\\\wsbbrz0283\\mobi\\50_Ergebnisse\\MOBi_4.0\\2050\\plans_exogeneous\\cb_road\\100pct\\plans.xml.gz";
        String inputPtDemand = "\\\\wsbbrz0283\\mobi\\40_Projekte\\20220411_Genf_2050\\plans_exogeneous\\cb_rail\\100pct\\plans.xml.gz";
        String variante = "10.5-sans_I_avec_Furet_miv";
        String outputCarDemand = "\\\\wsbbrz0283\\mobi\\40_Projekte\\20220411_Genf_2050\\sim\\" + variante + "\\plans_exogeneous\\cb_road\\plans.xml.gz";
        String outputPTDemand = "\\\\wsbbrz0283\\mobi\\40_Projekte\\20220411_Genf_2050\\sim\\" + variante + "\\plans_exogeneous\\cb_rail\\plans.xml.gz";
        String zonesFile = "\\\\wsbbrz0283\\mobi\\40_Projekte\\20220411_Genf_2050\\skims\\zonierung_frankreich\\bordering_comm_ge.shp";
        String alterationTable = "\\\\wsbbrz0283\\mobi\\40_Projekte\\20220411_Genf_2050\\sim\\" + variante + "\\demand-f.csv";

        Scenario scenario1 = ScenarioUtils.createScenario(ConfigUtils.createConfig());
        new PopulationReader(scenario1).readFile(inputCarDemand);
        int initialCarDemand = scenario1.getPopulation().getPersons().size();
        Scenario scenario2 = ScenarioUtils.createScenario(ConfigUtils.createConfig());
        new PopulationReader(scenario2).readFile(inputPtDemand);
        int initialPtDemand = scenario2.getPopulation().getPersons().size();

        Zones zones = ZonesLoader.loadZones("zones", zonesFile, "insee");
        AdjustExogeneousDemand adjustExogeneousDemand = new AdjustExogeneousDemand(scenario1.getPopulation(), scenario2.getPopulation(), zones, alterationTable);
        adjustExogeneousDemand.adjustDemand();
        int newCarDemand = scenario1.getPopulation().getPersons().size();
        int newPtDemand = scenario2.getPopulation().getPersons().size();
        new PopulationWriter(scenario1.getPopulation()).write(outputCarDemand);
        new PopulationWriter(scenario2.getPopulation()).write(outputPTDemand);
        LogManager.getLogger(AdjustExogeneousDemand.class).info("Change in Car population " + (newCarDemand - initialCarDemand));
        LogManager.getLogger(AdjustExogeneousDemand.class).info("Change in PT population " + (newPtDemand - initialPtDemand));
    }

    private void sortPopulationByZone(ConcurrentHashMap<String, Set<Id<Person>>> populationPerZone, Population demand) {
        demand.getPersons().values().parallelStream().forEach(p -> {
            Plan plan = p.getSelectedPlan();
            Activity start = (Activity) plan.getPlanElements().get(0);
            Activity end = (Activity) plan.getPlanElements().get(2);
            Coord startCoord = start.getCoord();
            Coord endCoord = end.getCoord();
            Zone startZone = zones.findZone(startCoord);
            Zone endZone = zones.findZone(endCoord);
            if (startZone != null) {
                populationPerZone.computeIfAbsent(startZone.getId().toString(), a -> ConcurrentHashMap.newKeySet()).add(p.getId());
            }
            if (endZone != null) {
                populationPerZone.computeIfAbsent(endZone.getId().toString(), a -> ConcurrentHashMap.newKeySet()).add(p.getId());
            }
        });
    }

    private void readAlterationTable(String alterationTable) {
        try (CSVReader reader = new CSVReader(alterationTable, ";")) {
            var line = reader.readLine();
            while (line != null) {
                String zone = line.get("Zone");
                int car = Integer.parseInt(line.get("carDiff"));
                int pt = Integer.parseInt(line.get("ptDiff"));
                carCorrectionPerZone.put(zone, car);
                ptCorrectionPerZone.put(zone, pt);

                double x1 = Double.parseDouble(line.get("x1"));
                double x2 = Double.parseDouble(line.get("x2"));
                double x3 = Double.parseDouble(line.get("x3"));
                double y1 = Double.parseDouble(line.get("y1"));
                double y2 = Double.parseDouble(line.get("y2"));
                double y3 = Double.parseDouble(line.get("y3"));
                Coord coord1 = new Coord(x1, y1);
                Coord coord2 = new Coord(x2, y2);
                Coord coord3 = new Coord(x3, y3);
                insertionPointsPerZone.put(zone, List.of(coord1, coord2, coord3));
                line = reader.readLine();
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void adjustDemand() {
        adjustModalDemand(carDemand, carPopulationPerZone, carCorrectionPerZone, SBBModes.CAR);
        adjustModalDemand(ptDemand, ptPopulationPerZone, ptCorrectionPerZone, SBBModes.PT);
    }

    public void adjustModalDemand(Population demand, Map<String, Set<Id<Person>>> populationPerZone, Map<String, Integer> correctionPerZone, String mode) {
        for (var e : correctionPerZone.entrySet()) {
            String zone = e.getKey();
            int correction = e.getValue();
            System.out.println(zone + " " + correction);
            var persons = populationPerZone.get(zone);
            if (persons == null) {
                LogManager.getLogger(getClass()).info("Zone " + zone + " has no demand for " + mode);
            } else if (correction < 0) {
                if (persons.size() < Math.abs(correction)) {
                    LogManager.getLogger(getClass()).info("Zone " + zone + " less demand by " + mode + " than expected removal");
                    persons.forEach(p -> demand.removePerson(p));
                } else {
                    List<Id<Person>> personCandidates = new ArrayList<>(persons);
                    Collections.shuffle(personCandidates);
                    for (int i = 0; i < Math.abs(correction); i++) {
                        demand.removePerson(personCandidates.get(i));
                    }
                }
            } else if (correction > 0) {
                List<Id<Person>> personCandidates = new ArrayList<>(persons);
                Collections.shuffle(personCandidates);
                for (int i = 0; i < correction; i++) {
                    int pid = i < personCandidates.size() ? i : random.nextInt(personCandidates.size());
                    Person toClone = demand.getPersons().get(personCandidates.get(pid));
                    Id<Person> newId = Id.createPersonId("z_" + zone + "_" + mode + "_" + i);
                    Person clone = demand.getFactory().createPerson(newId);
                    Plan plan = demand.getFactory().createPlan();
                    clone.addPlan(plan);
                    Activity origigStart = (Activity) toClone.getSelectedPlan().getPlanElements().get(0);
                    boolean outbound = zones.getZone(Id.create(zone, Zone.class)).getEnvelope().contains(origigStart.getCoord().getX(), origigStart.getCoord().getY());
                    double departureTime = origigStart.getEndTime().seconds();
                    Coord zoneCoord = insertionPointsPerZone.get(zone).get(random.nextInt(3));
                    Coord destCoord = destinationCoords.get(random.nextInt(destinationCoords.size()));
                    Activity startAct = demand.getFactory().createActivityFromCoord("cbHome", outbound ? zoneCoord : destCoord);
                    startAct.setEndTime(departureTime);
                    plan.addActivity(startAct);
                    plan.addLeg(demand.getFactory().createLeg(mode));
                    plan.addActivity(demand.getFactory().createActivityFromCoord("cbHome", outbound ? destCoord : zoneCoord));
                    demand.addPerson(clone);
                }
            }
        }
    }
}
