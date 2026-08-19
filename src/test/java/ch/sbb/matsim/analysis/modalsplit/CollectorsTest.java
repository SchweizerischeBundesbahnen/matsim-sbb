package ch.sbb.matsim.analysis.modalsplit;

import ch.sbb.matsim.analysis.tripsandlegsanalysis.RailTripsAnalyzer;
import ch.sbb.matsim.config.variables.SBBModes;
import ch.sbb.matsim.config.variables.Variables;
import ch.sbb.matsim.zones.Zone;
import ch.sbb.matsim.zones.Zones;
import ch.sbb.matsim.zones.ZonesCollection;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.Point;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.population.Activity;
import org.matsim.api.core.v01.population.Leg;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.Plan;
import org.matsim.api.core.v01.population.Population;
import org.matsim.api.core.v01.population.PopulationFactory;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.config.groups.ScoringConfigGroup;
import org.matsim.core.population.routes.GenericRouteImpl;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.pt.routes.DefaultTransitPassengerRoute;
import org.matsim.pt.transitSchedule.api.TransitLine;
import org.matsim.pt.transitSchedule.api.TransitRoute;
import org.matsim.pt.transitSchedule.api.TransitStopFacility;
import org.matsim.testcases.MatsimTestUtils;
import org.matsim.utils.objectattributes.attributable.Attributes;
import org.matsim.utils.objectattributes.attributable.AttributesImpl;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

class CollectorsTest {

    @RegisterExtension
    public final MatsimTestUtils helper = new MatsimTestUtils();

    @Test
    void modalSplitCollectorCountsRailTripAndWritesModalSplitFiles() throws IOException {
        Fixture fixture = new Fixture();
        ModalSplitCollector collector = new ModalSplitCollector(
                fixture.config,
                fixture.railTripsAnalyzer,
                List.of("all")
        );

        collector.collect(fixture.person, fixture.plan);
        collector.write(fixture.outputBaseDir, "run-1", 1.0);

        Path path = Path.of(fixture.outputBaseDir + MSVariables.oNModalSplitPF);
        List<String[]> rows = readRows(path);
        Map<String, Integer> headers = readHeaderMap(path);

        Assertions.assertEquals("1", findCell(rows, headers, Map.of("mode", "pt"), "all"));
        Assertions.assertEquals("1", findCell(rows, headers, Map.of("mode", "pt"), "submode_rail"));
    }

    @Test
    void timesCollectorWritesDepartureAndTravelTimeDistributions() throws IOException {
        Fixture fixture = new Fixture();
        TimesCollector collector = new TimesCollector(
                fixture.config,
                fixture.railTripsAnalyzer,
                List.of("all")
        );

        collector.collect(fixture.person, fixture.plan);
        collector.write(fixture.outputBaseDir, "run-2", 1.0);

        Path middlePath = Path.of(fixture.outputBaseDir + MSVariables.oNMiddleTimeSteps);
        Map<String, Integer> middleHeaders = readHeaderMap(middlePath);
        List<String[]> middleRows = readRows(middlePath);

        Assertions.assertEquals("1", findCell(middleRows, middleHeaders, Map.of("time", "1800"), "mode_pt"));
        Assertions.assertEquals("1", findCell(middleRows, middleHeaders, Map.of("time", "1800"), "all"));

        Path travelPath = Path.of(fixture.outputBaseDir + MSVariables.oNTravelTimeDistribution);
        Map<String, Integer> travelHeaders = readHeaderMap(travelPath);
        List<String[]> travelRows = readRows(travelPath);

        Assertions.assertEquals("1", findCell(travelRows, travelHeaders, Map.of("time", "3600"), "mode_pt"));
    }

    @Test
    void feederCollectorWritesAggregatedAndZonalRows() throws IOException {
        Fixture fixture = new Fixture();
        FeederCollector collector = new FeederCollector(
                fixture.zones,
                Set.of(SBBModes.CAR),
                List.of("all")
        );

        collector.collect(fixture.person, fixture.plan);
        collector.write(fixture.outputBaseDir, "run-3", 1.0);

        Path feederPath = Path.of(fixture.outputBaseDir + MSVariables.oNModalSplitFeederPF);
        Map<String, Integer> feederHeaders = readHeaderMap(feederPath);
        List<String[]> feederRows = readRows(feederPath);

        Assertions.assertEquals("1", findCell(feederRows, feederHeaders,
                Map.of("mode", SBBModes.PT, "access_mode", SBBModes.ACCESS_EGRESS_WALK, "egress_mode", "all"),
                "all"));
    }

    private static List<String[]> readRows(Path path) throws IOException {
        List<String> lines = Files.readAllLines(path);
        if (lines.size() <= 1) {
            return List.of();
        }
        return lines.subList(1, lines.size()).stream()
                .map(line -> line.split(";"))
                .toList();
    }

    private static Map<String, Integer> readHeaderMap(Path path) throws IOException {
        List<String> lines = Files.readAllLines(path);
        String[] headers = lines.getFirst().split(";");
        Map<String, Integer> headerMap = new HashMap<>();
        for (int i = 0; i < headers.length; i++) {
            headerMap.put(headers[i], i);
        }
        return headerMap;
    }

    private static String findCell(List<String[]> rows, Map<String, Integer> headers,
                                   Map<String, String> criteria, String targetColumn) {
        for (String[] row : rows) {
            boolean matches = true;
            for (Map.Entry<String, String> entry : criteria.entrySet()) {
                Integer index = headers.get(entry.getKey());
                if (index == null || !Objects.equals(row[index], entry.getValue())) {
                    matches = false;
                    break;
                }
            }
            if (matches) {
                Integer index = headers.get(targetColumn);
                if (index == null) {
                    throw new AssertionError("Column not found: " + targetColumn);
                }
                return row[index];
            }
        }
        throw new AssertionError("Could not find row matching criteria " + criteria + " in " + rows);
    }

    private class Fixture {
        private final Config config;
        private final Person person;
        private final Plan plan;
        private final RailTripsAnalyzer railTripsAnalyzer;
        private final Zones zones;
        private final String outputBaseDir;

        private Fixture() {
            this.config = ConfigUtils.createConfig();
            configureConfig(this.config);
            Scenario scenario = ScenarioUtils.createScenario(this.config);
            Population population = scenario.getPopulation();
            this.person = population.getFactory().createPerson(Id.createPersonId("person-1"));
            this.plan = createPlan(population.getFactory());
            this.person.addPlan(this.plan);
            this.person.setSelectedPlan(this.plan);
            population.addPerson(this.person);
            this.person.getAttributes().putAttribute(Variables.ANALYSIS_SUBPOPULATION, "all");

            this.zones = new TestZones();
            ZonesCollection zonesCollection = new ZonesCollection();
            zonesCollection.addZones(this.zones);
            this.railTripsAnalyzer = new RailTripsAnalyzer(scenario.getTransitSchedule(), scenario.getNetwork(), zonesCollection);

            this.outputBaseDir = helper.getOutputDirectory() + "/collectors/";
            try {
                Files.createDirectories(Path.of(this.outputBaseDir));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        private void configureConfig(Config config) {
            config.qsim().setEndTime(24 * 3600);
            ScoringConfigGroup.ModeParams walkParams = new ScoringConfigGroup.ModeParams(SBBModes.WALK_FOR_ANALYSIS);
            walkParams.setMarginalUtilityOfTraveling(0);
            walkParams.setConstant(0);
            walkParams.setMarginalUtilityOfDistance(0);
            walkParams.setMonetaryDistanceRate(0);
            config.scoring().addModeParams(walkParams);

            ScoringConfigGroup.ModeParams ptParams = new ScoringConfigGroup.ModeParams(SBBModes.PT);
            ptParams.setMarginalUtilityOfTraveling(0);
            ptParams.setConstant(0);
            ptParams.setMarginalUtilityOfDistance(0);
            ptParams.setMonetaryDistanceRate(0);
            config.scoring().addModeParams(ptParams);

            ScoringConfigGroup.ModeParams carParams = new ScoringConfigGroup.ModeParams(SBBModes.CAR);
            carParams.setMarginalUtilityOfTraveling(0);
            carParams.setConstant(0);
            carParams.setMarginalUtilityOfDistance(0);
            carParams.setMonetaryDistanceRate(0);
            config.scoring().addModeParams(carParams);
        }

        private Plan createPlan(PopulationFactory factory) {
            Activity home = factory.createActivityFromCoord("home", new Coord(0, 0));
            home.setEndTime(0.0);
            Activity work = factory.createActivityFromCoord("work", new Coord(1000, 1000));
            work.setStartTime(3_600.0);

            Leg walkToRail = factory.createLeg(SBBModes.WALK_FOR_ANALYSIS);
            GenericRouteImpl walkRoute = new GenericRouteImpl(Id.createLinkId("walk-link"), Id.createLinkId("rail-link"));
            walkRoute.setDistance(1_000.0);
            walkToRail.setRoute(walkRoute);

            Leg railLeg = factory.createLeg(SBBModes.PTSubModes.RAIL);
            DefaultTransitPassengerRoute transitRoute = new DefaultTransitPassengerRoute(
                    Id.createLinkId("access-link"),
                    Id.createLinkId("egress-link"),
                    Id.create("access-stop", TransitStopFacility.class),
                    Id.create("egress-stop", TransitStopFacility.class),
                    Id.create("line-1", TransitLine.class),
                    Id.create("route-1", TransitRoute.class)
            );
            transitRoute.setDistance(2_000.0);
            railLeg.setRoute(transitRoute);

            Leg walkToWork = factory.createLeg(SBBModes.WALK_FOR_ANALYSIS);
            GenericRouteImpl walkToWorkRoute = new GenericRouteImpl(Id.createLinkId("rail-link"), Id.createLinkId("work-link"));
            walkToWorkRoute.setDistance(500.0);
            walkToWork.setRoute(walkToWorkRoute);

            Plan plan = factory.createPlan();
            plan.addActivity(home);
            plan.addLeg(walkToRail);
            plan.addLeg(railLeg);
            plan.addLeg(walkToWork);
            plan.addActivity(work);
            return plan;
        }
    }

    private static class TestZones implements Zones {
        private final Zone originZone = new TestZone(Id.create("zone-1", Zone.class), 0.0, 500.0, 0.0, 500.0);
        private final Zone destinationZone = new TestZone(Id.create("zone-2", Zone.class), 500.0, 1000.0, 500.0, 1000.0);

        @Override
        public Id<Zones> getId() {
            return Id.create("zones", Zones.class);
        }

        @Override
        public int size() {
            return 2;
        }

        @Override
        public Zone findZone(double x, double y) {
            if (x <= 500.0 && y <= 500.0) {
                return originZone;
            }
            return destinationZone;
        }

        @Override
        public Zone findNearestZone(double x, double y, double maxDistance) {
            return findZone(x, y);
        }

        @Override
        public Zone getZone(Id<Zone> id) {
            return id.equals(originZone.getId()) ? originZone : destinationZone;
        }

        @Override
        public List<Zone> getZones() {
            return List.of(originZone, destinationZone);
        }
    }

    private static class TestZone implements Zone {
        private final Id<Zone> id;
        private final Envelope envelope;
        private final Attributes attributes = new AttributesImpl();

        private TestZone(Id<Zone> id, double minX, double minY, double maxX, double maxY) {
            this.id = id;
            this.envelope = new Envelope(minX, maxX, minY, maxY);
            attributes.putAttribute("sl3_id", "1");
        }

        @Override
        public Id<Zone> getId() {
            return id;
        }

        @Override
        public Object getAttribute(String name) {
            return attributes.getAttribute(name);
        }

        @Override
        public Envelope getEnvelope() {
            return envelope;
        }

        @Override
        public boolean contains(Point pt) {
            return envelope.contains(pt.getCoordinate());
        }

        @Override
        public double distance(Point pt) {
            double dx = 0.0;
            if (pt.getX() < envelope.getMinX()) {
                dx = envelope.getMinX() - pt.getX();
            } else if (pt.getX() > envelope.getMaxX()) {
                dx = pt.getX() - envelope.getMaxX();
            }

            double dy = 0.0;
            if (pt.getY() < envelope.getMinY()) {
                dy = envelope.getMinY() - pt.getY();
            } else if (pt.getY() > envelope.getMaxY()) {
                dy = pt.getY() - envelope.getMaxY();
            }

            return Math.sqrt(dx * dx + dy * dy);
        }

        @Override
        public Attributes getAttributes() {
            return attributes;
        }
    }
}
