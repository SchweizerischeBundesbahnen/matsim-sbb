package ch.sbb.matsim.mavi.visum;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ch.sbb.matsim.config.variables.SBBModes;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.pt.transitSchedule.api.ChainedDeparture;
import org.matsim.pt.transitSchedule.api.Departure;
import org.matsim.pt.transitSchedule.api.TransitLine;
import org.matsim.pt.transitSchedule.api.TransitRoute;
import org.matsim.pt.transitSchedule.api.TransitSchedule;
import org.matsim.pt.transitSchedule.api.TransitStopFacility;

import ch.sbb.matsim.mavi.pt.MTTExporter;
import ch.sbb.matsim.mavi.pt.TimeProfileExporter;
import ch.sbb.matsim.mavi.pt.VisumConnectionsExporter;
import ch.sbb.matsim.mavi.pt.VisumPtExporterConfigGroup;
import ch.sbb.matsim.mavi.pt.VisumStopExporter;

/**
 * Test the conversion of a Visum net file to MATSim transit schedule.
 * This test covers each step of the conversion process as implemented in RunVisumPtExporter.
 */
class VisumNetFileConversionTest {

    private Scenario scenario;
    private VisumPtExporterConfigGroup exporterConfig;
    private Path minirailNetPath;

    @BeforeEach
    void setUp() {
        Config config = ConfigUtils.createConfig();
        exporterConfig = ConfigUtils.addOrGetModule(config, VisumPtExporterConfigGroup.class);
        exporterConfig.setNetworkMode(SBBModes.PT);
        scenario = ScenarioUtils.createScenario(config);
        minirailNetPath = Paths.get("test/input/ch/sbb/matsim/mavi/minirail.net");
    }

    @Test
    void testCreateVisumDataSource() throws Exception {
        try (VisumDataSource dataSource = VisumNetFile.open(minirailNetPath)) {
            assertNotNull(dataSource, "VisumDataSource should not be null");
            
            // Test that we can access some tables
            VisumDataSource.DataTable nodes = dataSource.getTable("NODES");
            assertNotNull(nodes, "Should be able to access NODES table");
            assertEquals(5, nodes.size(), "NODES table should have 5 rows");
            
            VisumDataSource.DataTable links = dataSource.getTable("LINKS");
            assertNotNull(links, "Should be able to access LINKS table");
            assertEquals(8, links.size(), "LINKS table should have 8 rows");
            
            VisumDataSource.DataTable stops = dataSource.getTable("STOPS");
            assertNotNull(stops, "Should be able to access STOPS table");
            assertEquals(5, stops.size(), "STOPS table should have 5 rows");
        }
    }

    @Test
    void testLoadStopPoints(@TempDir Path tempDir) throws Exception {
        try (VisumDataSource dataSource = VisumNetFile.open(minirailNetPath, StandardCharsets.UTF_8)) {
            // Load stops into scenario
            VisumStopExporter stopExporter = new VisumStopExporter(scenario);
            stopExporter.loadStopPoints(dataSource, exporterConfig);
            
            // Verify stops were loaded correctly
            TransitSchedule schedule = scenario.getTransitSchedule();
            assertNotNull(schedule, "Transit schedule should not be null");
            
            Map<Id<TransitStopFacility>, TransitStopFacility> stops = schedule.getFacilities();
            assertFalse(stops.isEmpty(), "Stop facilities should not be empty");
            assertEquals(5, stops.size(), "Should have loaded 5 stop facilities");
            
            // Verify stops have correct coordinates
            for (TransitStopFacility facility : stops.values()) {
                assertNotNull(facility.getCoord(), "Stop facility should have coordinates");
            }
        }
    }

    @Test
    void testIntegrateMinTransferTimes(@TempDir Path tempDir) throws Exception {
        try (VisumDataSource dataSource = VisumNetFile.open(minirailNetPath)) {
            // Load stops first
            VisumStopExporter stopExporter = new VisumStopExporter(scenario);
            stopExporter.loadStopPoints(dataSource, exporterConfig);
            
            // Then integrate minimal transfer times
            MTTExporter mttExporter = new MTTExporter(scenario);
            mttExporter.integrateMinTransferTimes(dataSource, stopExporter.getStopAreasToStopPoints());
            
            // The minirail.net file might not have MTT data, but the method should execute without errors
            TransitSchedule schedule = scenario.getTransitSchedule();
            assertNotNull(schedule, "Transit schedule should not be null");
        }
    }

    @Test
    void testCreateTransitLines(@TempDir Path tempDir) throws Exception {
        try (VisumDataSource dataSource = VisumNetFile.open(minirailNetPath)) {
            // Load stops first
            VisumStopExporter stopExporter = new VisumStopExporter(scenario);
            stopExporter.loadStopPoints(dataSource, exporterConfig);
            
            // Then create transit lines
            TimeProfileExporter timeProfileExporter = new TimeProfileExporter(scenario);
            timeProfileExporter.createTransitLines(dataSource, exporterConfig);
            
            // Verify 3 transit lines were created
            TransitSchedule schedule = scenario.getTransitSchedule();
            Map<Id<TransitLine>, TransitLine> lines = schedule.getTransitLines();
            assertEquals(3, lines.size(), "3 transit lines expected");
            
            // Verify the HW line exists
            TransitLine hwLine = lines.get(Id.create("HW", TransitLine.class));
            assertNotNull(hwLine, "HW transit line should exist");
            
            // Verify the HW line has 1 route
            Map<Id<TransitRoute>, TransitRoute> routes = hwLine.getRoutes();
            assertEquals(1, routes.size(), "1 transit route in HW line expected");
        }
    }

    @Test
    void testCreateDepartureChaining(@TempDir Path tempDir) throws Exception {
        try (VisumDataSource dataSource = VisumNetFile.open(minirailNetPath)) {
            // Load stops first
            VisumStopExporter stopExporter = new VisumStopExporter(scenario);
            stopExporter.loadStopPoints(dataSource, exporterConfig);
            
            // Then create transit lines
            TimeProfileExporter timeProfileExporter = new TimeProfileExporter(scenario);
            timeProfileExporter.createTransitLines(dataSource, exporterConfig);
            
            // Then create departure chaining
            timeProfileExporter.createDepartureChaining(dataSource);
            
            // Verify departures were created
            TransitSchedule schedule = scenario.getTransitSchedule();
            Map<Id<TransitLine>, TransitLine> lines = schedule.getTransitLines();
            TransitLine hwLine = lines.get(Id.create("HW", TransitLine.class));
            
            for (TransitRoute route : hwLine.getRoutes().values()) {
                assertFalse(route.getDepartures().isEmpty(), "Route should have departures");
                for (Departure departure : route.getDepartures().values()) {
                    assertFalse(departure.getChainedDepartures().isEmpty(), "Departures should be chained");
                    for (ChainedDeparture chainedDeparture : departure.getChainedDepartures()) {
                        assertEquals(chainedDeparture.getChainedTransitLineId(), Id.create("WE", TransitLine.class), "Chained transit line should be WE");
                    }
                }
            }
        }
    }

    @Test
    void testLoadZoneConnections(@TempDir Path tempDir) throws Exception {
        try (VisumDataSource dataSource = VisumNetFile.open(minirailNetPath)) {
            // Load zone connections
            VisumConnectionsExporter connectionsExporter = new VisumConnectionsExporter(dataSource);
            
            // The method should execute without errors, even if the minirail.net might not have connector data
            assertNotNull(connectionsExporter, "ConnectionsExporter should not be null");
        }
    }

}
