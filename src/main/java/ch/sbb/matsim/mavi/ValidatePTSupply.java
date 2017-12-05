/*
 * Copyright (C) Schweizerische Bundesbahnen SBB, 2017.
 */

package ch.sbb.matsim.mavi;

import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.apache.log4j.Logger;

import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.population.Leg;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.core.utils.geometry.CoordUtils;
import org.matsim.core.utils.gis.ShapeFileReader;
import org.matsim.pt.router.FakeFacility;
import org.matsim.pt.router.TransitRouterConfig;
import org.matsim.pt.router.TransitRouterImpl;
import org.opengis.feature.simple.SimpleFeature;

import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.geom.GeometryFactory;
import com.vividsolutions.jts.geom.MultiPolygon;
import com.vividsolutions.jts.geom.Point;
import com.vividsolutions.jts.geom.Polygon;


/**
 *
 * TestPTRouting: Class to route a configurable amount of random OD-Pairs within a shape file through a public transport network at a certain time of the day
 *
 * Input: config, transitSchedule, shape file, number of OD-Pairs, time of the day (in hours, e.g. 8am)
 * Output: a csv containing the beeline distance between the OD-pairs and the time needed to go from origin to destination using the public transport network.
 *
 * @author manserpa
 *
 */

public final class ValidatePTSupply {

    private GeometryFactory factory;
    private Geometry include;
    private Geometry exclude;
    private Logger log;
    private TransitRouterImpl router;

    // available scenarios: CNB, ZURICH, CH, CITY2CITY
    private final String SCENARIONAME = "CNB";
    private final String PATHTOSHAPEFILES = "";

    private final String PATHTOPT = "";

    public static void main(final String[] args) { new ValidatePTSupply(); }

    private ValidatePTSupply()	{
        log = Logger.getLogger(ValidatePTSupply.class);
        log.info("Validation starts...");

        run();
    }

    private void run()  {
        createGeometryFactory();
        readShapeFile(PATHTOSHAPEFILES + SCENARIONAME + ".shp");

        createScenario();

        runStopToStop(this.router);

    }

    private void runStopToStop(TransitRouterImpl router)    {


    }

    private void runRandomODPairs(TransitRouterImpl router, double numberOfTrips, double dayTime, String schedule) throws IOException	{

        String csvFile = "PseudoPTTripTime"+schedule+dayTime+".csv";
        FileWriter writer = new FileWriter(csvFile);

        // TODO abstimmen mit SBB CSV writer
        // CSVUtils.writeLine(writer, Arrays.asList("BeelineDistance", "TotalTripTime", "TotalTripDistance"), ';');

        double minX = Double.MAX_VALUE;
        double maxX = -Double.MAX_VALUE;
        double minY = Double.MAX_VALUE;
        double maxY = -Double.MAX_VALUE;

        Coordinate[] coordinatesShape = this.include.getCoordinates();

        for(int i = 0; i < coordinatesShape.length; i++)	{
            double xShape = coordinatesShape[i].x;
            double yShape = coordinatesShape[i].y;
            minX = Math.min(minX, xShape);
            maxX = Math.max(maxX, xShape);
            minY = Math.min(minY, yShape);
            maxY = Math.max(maxY, yShape);
        }

        double x;
        double y;
        double x1;
        double y1;


        for(int i = 0; i < numberOfTrips; i++)	{
            do {
                x = minX + (maxX - minX) * Math.random();
                y = minY + (maxY - minY) * Math.random();
            } while(!nodeInServiceArea(x,y));

            do {
                x1 = minX + (maxX - minX) * Math.random();
                y1 = minY + (maxY - minY) * Math.random();
            } while(!nodeInServiceArea(x1,y1));

            List<Leg> legs = router.calcRoute(new FakeFacility(new Coord(x1, y1)), new FakeFacility(new Coord(x, y)), dayTime*3600, null);

            double totTime = 0;
            double totDistance = 0;

            // prüfen ob legs = null
            if(legs == null)
                continue;

            for (Leg e : legs)	{
                totTime += e.getTravelTime();
                totDistance += e.getRoute().getDistance();
                System.out.println(e.getRoute().getDistance());
            }
            Coord fromCoord = new Coord(x1,y1);
            Coord toCoord = new Coord(x,y);
            double beelineDistance = CoordUtils.calcEuclideanDistance(fromCoord,toCoord);

            //CSVUtils.writeLine(writer, Arrays.asList(Double.toString(beelineDistance), Double.toString(totTime), Double.toString(totTime)), ';');
        }
        writer.flush();
        writer.close();
    }

    private void createScenario()   {
        Config config = ConfigUtils.createConfig();
        config.network().setInputFile("");
        config.transit().setTransitScheduleFile("");
        config.transit().setVehiclesFile("");

        Scenario scenario = ScenarioUtils.loadScenario(config);

        this.router = new TransitRouterImpl( new TransitRouterConfig(config.planCalcScore(),
                config.plansCalcRoute(), config.transitRouter(), config.vspExperimental() ), scenario.getTransitSchedule() );
    }

    private void createGeometryFactory()    { this.factory = new GeometryFactory(); };

    private void readShapeFile(String shpFile) {
        log.info("Reading shape file");
        Collection<SimpleFeature> features = new ShapeFileReader().readFileAndInitialize(shpFile);
        Collection<Geometry> include = new ArrayList<>();
        Collection<Geometry> exclude = new ArrayList<>();

        for(SimpleFeature f: features){
            boolean incl = true;
            Geometry g = null;
            for(Object o: f.getAttributes()){
                if(o instanceof Polygon){
                    g = (Geometry) o;
                } else if (o instanceof MultiPolygon){
                    g = (Geometry) o;
                } else if (o instanceof String){
                    incl = Boolean.parseBoolean((String) o);
                }
            }
            if(! (g == null)){
                if(incl){
                    include.add(g);
                } else {
                    exclude.add(g);
                }
            }
        }
        this.include = this.factory.createGeometryCollection(include.toArray(new Geometry[include.size()])).buffer(0);
        this.exclude = this.factory.createGeometryCollection(exclude.toArray(new Geometry[exclude.size()])).buffer(0);
        log.info("Finished reading shape file.");
    }

    private boolean nodeInServiceArea(double x, double y) {
        Coordinate coord = new Coordinate(x, y);
        Point p = factory.createPoint(coord);
        if(this.include.contains(p)){
            if(exclude.contains(p)){
                return false;
            }
            return true;
        }
        return false;
    }
}