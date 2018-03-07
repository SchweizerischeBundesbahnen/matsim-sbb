/*
 * Copyright (C) Schweizerische Bundesbahnen SBB, 2017.
 */

package ch.sbb.matsim.preparation;

import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.GeometryFactory;
import com.vividsolutions.jts.geom.MultiPolygon;
import com.vividsolutions.jts.geom.Point;
import org.apache.log4j.Logger;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.network.Node;
import org.matsim.api.core.v01.population.Activity;
import org.matsim.api.core.v01.population.Leg;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.PlanElement;
import org.matsim.api.core.v01.population.Population;
import org.matsim.api.core.v01.population.Route;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.config.ReflectiveConfigGroup;
import org.matsim.core.network.NetworkUtils;
import org.matsim.core.network.io.NetworkReaderMatsimV1;
import org.matsim.core.network.io.NetworkWriter;
import org.matsim.core.population.PopulationUtils;
import org.matsim.core.population.io.PopulationReader;
import org.matsim.core.population.io.PopulationWriter;
import org.matsim.core.population.routes.GenericRouteImpl;
import org.matsim.core.population.routes.NetworkRoute;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.core.utils.geometry.CoordUtils;
import org.matsim.core.utils.gis.ShapeFileReader;
import org.matsim.core.utils.misc.Counter;
import org.matsim.facilities.ActivityFacilities;
import org.matsim.facilities.FacilitiesUtils;
import org.matsim.households.Household;
import org.matsim.households.Households;
import org.matsim.households.HouseholdsImpl;
import org.matsim.pt.routes.ExperimentalTransitRoute;
import org.matsim.pt.transitSchedule.api.Departure;
import org.matsim.pt.transitSchedule.api.TransitLine;
import org.matsim.pt.transitSchedule.api.TransitRoute;
import org.matsim.pt.transitSchedule.api.TransitRouteStop;
import org.matsim.pt.transitSchedule.api.TransitSchedule;
import org.matsim.pt.transitSchedule.api.TransitScheduleReader;
import org.matsim.pt.transitSchedule.api.TransitScheduleWriter;
import org.matsim.utils.objectattributes.ObjectAttributes;
import org.matsim.utils.objectattributes.ObjectAttributesXmlWriter;
import org.matsim.vehicles.Vehicle;
import org.matsim.vehicles.VehicleReaderV1;
import org.matsim.vehicles.VehicleUtils;
import org.matsim.vehicles.VehicleWriterV1;
import org.matsim.vehicles.Vehicles;
import org.opengis.feature.simple.SimpleFeature;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;


public class Cutter {

    private final static Logger log = Logger.getLogger(Cutter.class);

    private Map<Coord, Boolean> coordCache;
    private Map<Id<Person>, Person> filteredAgents;
    private Map<Id<TransitLine>, Set<Id<TransitRoute>>> usedTransitRouteIds = new HashMap<>();
    private Coord center;
    private int radius;
    private String commuterTag;
    private String popTag;
    private Scenario scenario;

    private boolean useShapeFile;
    private GeometryFactory geometryFactory = new GeometryFactory();
    Collection<SimpleFeature> features = null;

    private Cutter(Config config, CutterConfigGroup cutterConfig) {
        this.coordCache = new HashMap<>();
        this.filteredAgents = new HashMap<>();

        this.scenario = ScenarioUtils.createScenario(config);

        useShapeFile = cutterConfig.getUseShapeFile();
        if (useShapeFile) {
            ShapeFileReader shapeFileReader = new ShapeFileReader();
            shapeFileReader.readFileAndInitialize(cutterConfig.getPathToShapeFile());
            this.features = shapeFileReader.getFeatureSet();
        }
        else {
            this.center = new Coord(cutterConfig.getxCoordCenter(), cutterConfig.getyCoordCenter());
            this.radius = cutterConfig.getRadius();
        }

        new PopulationReader(scenario).readFile(config.plans().getInputFile());
//        new ObjectAttributesXmlReader(scenario.getPopulation().getPersonAttributes()).parse(config.plans().getInputPersonAttributeFile());
//        new HouseholdsReaderV10(scenario.getHouseholds()).readFile(config.households().getInputFile());
//        new ObjectAttributesXmlReader(scenario.getHouseholds().getHouseholdAttributes()).parse(config.households().getInputHouseholdAttributesFile());
//        new FacilitiesReaderMatsimV1(scenario).readFile(config.facilities().getInputFile());
        new NetworkReaderMatsimV1(scenario.getNetwork()).readFile(config.network().getInputFile());
        new TransitScheduleReader(scenario).readFile(config.transit().getTransitScheduleFile());
        new VehicleReaderV1(scenario.getTransitVehicles()).readFile(config.transit().getVehiclesFile());
//
        this.commuterTag = cutterConfig.getCommuterTag();
        this.popTag = cutterConfig.getPopTag();
    }

    public static void main(final String[] args) {
        final Config config = ConfigUtils.loadConfig(args[0], new CutterConfigGroup(CutterConfigGroup.GROUP_NAME));
        final CutterConfigGroup cutterConfig = ConfigUtils.addOrGetModule(config, CutterConfigGroup.class);

        // load files
        Cutter cutter = new Cutter(config, cutterConfig);
        // cut to area
        Population filteredPopulation = cutter.geographicallyFilterPopulation();

        String output = cutterConfig.getPathToTargetFolder();
        try {
            Files.createDirectories(Paths.get(output));
        } catch (IOException e) {
            log.error("Could not create output directory " + output, e);
        }

        File f = new File(config.plans().getInputFile());
        new PopulationWriter(filteredPopulation).write(output + File.separator+f.getName());

       // Households filteredHouseholds = cutter.filterHouseholdsWithPopulation();
       // ActivityFacilities filteredFacilities = cutter.filterFacilitiesWithPopulation();
        TransitSchedule filteredSchedule = cutter.cutPT();
        Vehicles filteredVehicles = cutter.cleanVehicles(filteredSchedule);
       Network filteredOnlyCarNetwork = cutter.getOnlyCarNetwork(filteredPopulation);
       Network filteredNetwork = cutter.cutNetwork(filteredSchedule, filteredOnlyCarNetwork);
       // cutter.resetPopulation(filteredPopulation);

        f = new File(config.transit().getTransitScheduleFile());
        new TransitScheduleWriter(filteredSchedule).writeFile(output + File.separator + f.getName());
        f = new File(config.transit().getVehiclesFile());
        new VehicleWriterV1(filteredVehicles).writeFile(output+ File.separator+f.getName());
        f = new File(config.network().getInputFile());
        new NetworkWriter(filteredNetwork).write(output+ File.separator+f.getName());
        f = new File(config.plans().getInputPersonAttributeFile());
        new ObjectAttributesXmlWriter(cutter.scenario.getPopulation().getPersonAttributes()).writeFile( output+ File.separator+f.getName());

       // // write new files
       // //F2LCreator.createF2L(filteredFacilities, filteredOnlyCarNetwork, cutterConfig.getPathToTargetFolder() + File.separator + FACILITIES2LINKS);
       // writeNewFiles(cutterConfig.getPathToTargetFolder() + File.separator, cutter.scenario,
       //         filteredPopulation, filteredHouseholds, filteredFacilities, filteredSchedule, filteredVehicles,
       //         filteredNetwork, cutter.createConfig(cutterConfig));
       // cutter.cutPTCounts(filteredNetwork, cutterConfig);
    }


    private Network getOnlyCarNetwork(Population population) {
        Set<Id<Link>> linksToKeep = new HashSet<>();
        for(Person p: population.getPersons().values()){
            for(PlanElement pe: p.getSelectedPlan().getPlanElements()){
                if (pe instanceof Leg) {
                    Leg leg = (Leg) pe;

                    if(leg.getRoute() == null){
                        continue;
                    }

                    linksToKeep.add(leg.getRoute().getStartLinkId());
                    linksToKeep.add(leg.getRoute().getEndLinkId());
                    if(leg.getRoute() instanceof NetworkRoute){
                        NetworkRoute route = (NetworkRoute) leg.getRoute();
                        linksToKeep.addAll(route.getLinkIds());
                    }
                }
                else{
                    Activity act = (Activity) pe;
                    linksToKeep.add(act.getLinkId());

                }
            }
        }

        Network carNetworkToKeep = NetworkUtils.createNetwork();
        for (Link link : scenario.getNetwork().getLinks().values()) {
            if (linksToKeep.contains(link.getId())) addLink(carNetworkToKeep, link);
            else if (link.getAllowedModes().contains("car") && (link.getCapacity() >= 10000)) addLink(carNetworkToKeep, link);
            else {
                if (useShapeFile) {
                    Point point = geometryFactory.createPoint( new Coordinate(link.getCoord().getX(), link.getCoord().getY()));
                    for (SimpleFeature feature : features) {
                        MultiPolygon p = (MultiPolygon) feature.getDefaultGeometry();
                        if (p.distance(point) <= 5000) {
                            // CRS have to be such that distance returns meter-units!
                            addLink(carNetworkToKeep, link);
                            break;
                        }
                    }
                }
                else {
                    if (CoordUtils.calcEuclideanDistance(center, link.getCoord()) <= radius + 5000) addLink(carNetworkToKeep, link); // and we keep all links within radius + 5km)
                }
            }
        }
        return carNetworkToKeep;
    }

    private Network cutNetwork(TransitSchedule filteredSchedule, Network filteredOnlyCarNetwork) {
        Network filteredNetwork = NetworkUtils.createNetwork();
        Set<Id<Link>> linksToKeep = getPTLinksToKeep(filteredSchedule);
        for (Link link : scenario.getNetwork().getLinks().values()) {
            if (linksToKeep.contains(link.getId()) || // we keep all links we need for pt
                    filteredOnlyCarNetwork.getLinks().containsKey(link.getId())) {
                addLink(filteredNetwork, link);
                //if (!linksToKeep.contains(link.getId())) {
                 //   Set<String> allowedModes = new HashSet<>();
                 //   allowedModes.add("car");
                 //   link.setAllowedModes(allowedModes);
                //}
            }
        }
        return filteredNetwork;
    }

    private Set<Id<Link>> getPTLinksToKeep(TransitSchedule filteredSchedule) {
        Set<Id<Link>> linksToKeep = new HashSet<>();
        for (TransitLine transitLine : filteredSchedule.getTransitLines().values()) {
            for (TransitRoute transitRoute : transitLine.getRoutes().values()) {
                linksToKeep.add(transitRoute.getRoute().getStartLinkId());
                linksToKeep.addAll(transitRoute.getRoute().getLinkIds());
                linksToKeep.add(transitRoute.getRoute().getEndLinkId());
            }
        }
        return linksToKeep;
    }

    private void addLink(Network network, Link link) {
        if (!network.getNodes().containsKey(link.getFromNode().getId())) {
            Node node = network.getFactory().createNode(link.getFromNode().getId(), link.getFromNode().getCoord());
            network.addNode(node);
        }
        if (!network.getNodes().containsKey(link.getToNode().getId())) {
            Node node = network.getFactory().createNode(link.getToNode().getId(), link.getToNode().getCoord());
            network.addNode(node);
        }
        network.addLink(link);
        link.setFromNode(network.getNodes().get(link.getFromNode().getId()));
        link.setToNode(network.getNodes().get(link.getToNode().getId()));
    }

    private TransitSchedule cutPT() {
        TransitSchedule filteredSchedule = ScenarioUtils.createScenario(ConfigUtils.createConfig()).getTransitSchedule();
        for (TransitLine transitLine : scenario.getTransitSchedule().getTransitLines().values()) {

            Set<Id<TransitRoute>> _routes = new HashSet<>();

            if(usedTransitRouteIds.containsKey(transitLine.getId())){
                _routes = usedTransitRouteIds.get(transitLine.getId());
                log.info(transitLine+": "+_routes);
            }
            for (TransitRoute transitRoute : transitLine.getRoutes().values()) {
                if(_routes.contains(transitRoute.getId())){
                    Id<TransitLine> newLineId = addLine(filteredSchedule, transitLine);
                    filteredSchedule.getTransitLines().get(newLineId).addRoute(transitRoute);
                    addStopFacilities(filteredSchedule, transitRoute);
                }

                else {
                    for (TransitRouteStop transitStop : transitRoute.getStops()) {
                        if (inArea(transitStop.getStopFacility().getCoord())) {
                            Id<TransitLine> newLineId = addLine(filteredSchedule, transitLine);
                            filteredSchedule.getTransitLines().get(newLineId).addRoute(transitRoute);
                            addStopFacilities(filteredSchedule, transitRoute);
                            break;
                        }
                    }
                }
            }
        }
        return filteredSchedule;
    }

    private void addStopFacilities(TransitSchedule schedule, TransitRoute transitRoute) {
        for (TransitRouteStop newStop : transitRoute.getStops()) {
            if (!schedule.getFacilities().containsKey(newStop.getStopFacility().getId())) {
                schedule.addStopFacility(newStop.getStopFacility());
            }
        }
    }

    private Id<TransitLine> addLine(TransitSchedule schedule, TransitLine transitLine) {
        Id<TransitLine> newLineId = Id.create(transitLine.getId().toString(), TransitLine.class);
        if (!schedule.getTransitLines().containsKey(newLineId)) {
            TransitLine newLine = schedule.getFactory().createTransitLine(newLineId);
            schedule.addTransitLine(newLine);
            newLine.setName(transitLine.getName());
        }
        return newLineId;
    }

    private Vehicles cleanVehicles(TransitSchedule transitSchedule) {
        Vehicles filteredVehicles = VehicleUtils.createVehiclesContainer();
        for (TransitLine line : transitSchedule.getTransitLines().values()) {
            for (TransitRoute route : line.getRoutes().values()) {
                for (Departure departure : route.getDepartures().values()) {
                    Vehicle vehicleToKeep = scenario.getTransitVehicles().getVehicles().get(departure.getVehicleId());
                    if (!filteredVehicles.getVehicleTypes().containsValue(vehicleToKeep.getType())) {
                        filteredVehicles.addVehicleType(vehicleToKeep.getType());
                    }
                    filteredVehicles.addVehicle(vehicleToKeep);
                }
            }
        }
        return filteredVehicles;
    }


    private List<Id<Link>> getPuTLinksRoute(ExperimentalTransitRoute route, TransitSchedule transit){
        TransitRoute tr = transit.getTransitLines().get(route.getLineId()).getRoutes().get(route.getRouteId());
        List<Id<Link>> linkIds = new ArrayList<>();
        Boolean record = false;
        for (Id<Link> linkId: tr.getRoute().getLinkIds()){
            if (linkId == route.getStartLinkId()) record = true;
            if (record){
                linkIds.add(linkId);
            }
            if (linkId == route.getEndLinkId()) record = false;
        }
        return linkIds;
    }


    private Boolean linksInArea(List<Id<Link>> linkIds){
        for(Id<Link> linkId: linkIds){
            Link link = this.scenario.getNetwork().getLinks().get(linkId);
            if(inArea(link.getFromNode().getCoord()) || inArea(link.getToNode().getCoord())){
                return true;
            }
        }
        return false;
    }

    private Boolean intersects(Leg leg, TransitSchedule transit){
        boolean intersection = false;
        List<Id<Link>> linkIds = new ArrayList<>();
        if(leg.getRoute() instanceof ExperimentalTransitRoute){
            ExperimentalTransitRoute route = (ExperimentalTransitRoute) leg.getRoute();
            if (route == null){
                log.info("Population should be routed. I will ignore this leg"+ leg);
            }
            else {
                linkIds = getPuTLinksRoute(route, transit);
            }
        }
        else if (leg.getRoute() instanceof GenericRouteImpl){
        }

        else{

            NetworkRoute route = (NetworkRoute) leg.getRoute();
            if (route == null){
                log.info("Population should be routed. I will ignore this leg"+ leg);
            }
            else {
                linkIds = route.getLinkIds();
            }
        }
        if(linksInArea(linkIds)){
            intersection = true;
        }
        return intersection;
    }

    private Population geographicallyFilterPopulation() {
        ObjectAttributes personAttributes = scenario.getPopulation().getPersonAttributes();
        TransitSchedule transit = scenario.getTransitSchedule();
        Population filteredPopulation = PopulationUtils.createPopulation(ConfigUtils.createConfig());
        Counter counter = new Counter(" person # ");
        boolean actInArea;
        boolean actNotInArea;
        boolean intersection;
        for (Person p : scenario.getPopulation().getPersons().values()) {
            counter.incCounter();
            if (p.getSelectedPlan() != null) {
                actInArea = false;
                intersection = false;
                actNotInArea = false;
                for (PlanElement pe : p.getSelectedPlan().getPlanElements()) {
                    if (pe instanceof Activity) {

                        Activity act = (Activity) pe;
                        if (inArea(act.getCoord())) {
                            actInArea = true;
                        } else {
                            actNotInArea = true;
                        }
                    }

                    else if (pe instanceof Leg) {
                        if(((Leg) pe).getRoute() == null){
                            actInArea = false;
                            intersection = false;
                            break;
                        }
                        intersection = intersects( (Leg) pe, transit);
                    }
                }

                if (actInArea) {
                    filteredPopulation.addPerson(p);
                    filteredAgents.put(p.getId(), p);
                } else if (intersection) {
                    filteredPopulation.addPerson(p);
                    filteredAgents.put(p.getId(), p);
                }

                if (actNotInArea) {
                    personAttributes.putAttribute(p.getId().toString(), "subpopulation", commuterTag);
                }
                else{
                    personAttributes.putAttribute(p.getId().toString(), "subpopulation", popTag);
                }

            }
        }


        for(Person p: filteredPopulation.getPersons().values()){
            for (PlanElement pe : p.getSelectedPlan().getPlanElements()) {
                     if (pe instanceof Leg) {
                        Route route = ((Leg) pe).getRoute();
                        if (route instanceof ExperimentalTransitRoute) {
                            ExperimentalTransitRoute myRoute = (ExperimentalTransitRoute) route;
                            if(!usedTransitRouteIds.containsKey(myRoute.getLineId())){
                                usedTransitRouteIds.put(myRoute.getLineId(), new HashSet<Id<TransitRoute>>());
                            }

                            usedTransitRouteIds.get(myRoute.getLineId()).add(myRoute.getRouteId());
                        }
                    }
            }
        }

        log.info("filtered population:" + filteredPopulation.getPersons().size());
        return filteredPopulation;
    }


    private Households filterHouseholdsWithPopulation() {
        Households filteredHouseholds = new HouseholdsImpl();

        for (Household household : scenario.getHouseholds().getHouseholds().values()) {
            Set<Id<Person>> personIdsToRemove = new HashSet<>();
            for (Id<Person> personId : household.getMemberIds()) {
                if (!filteredAgents.keySet().contains(personId)) {
                    personIdsToRemove.add(personId);
                }
            }
            for (Id<Person> personId : personIdsToRemove) {
                household.getMemberIds().remove(personId);
            }
            if (!household.getMemberIds().isEmpty()) {
                filteredHouseholds.getHouseholds().put(household.getId(), household);
            } else {
                scenario.getHouseholds().getHouseholdAttributes().removeAllAttributes(household.getId().toString());
            }
        }

        return filteredHouseholds;
    }

    private ActivityFacilities filterFacilitiesWithPopulation() {
        ActivityFacilities filteredFacilities = FacilitiesUtils.createActivityFacilities();

        for (Person person : filteredAgents.values()) {
            if (person.getSelectedPlan() != null) {
                for (PlanElement pe : person.getSelectedPlan().getPlanElements()) {
                    if (pe instanceof Activity) {
                        Activity act = (Activity) pe;
                        if (act.getFacilityId() != null && !filteredFacilities.getFacilities().containsKey(act.getFacilityId())) {
                            filteredFacilities.addActivityFacility(scenario.getActivityFacilities().getFacilities().get(act.getFacilityId()));
                        }
                    }
                }
            }
        }

        return filteredFacilities;
    }

    private boolean inArea(Coord coord) {
        if (coordCache.containsKey(coord)) {
            return coordCache.get(coord);
        } else {
            boolean coordIsInArea = false;
            if (useShapeFile) {
                for (SimpleFeature feature : features) {
                    MultiPolygon p = (MultiPolygon) feature.getDefaultGeometry();
                    Point point = geometryFactory.createPoint( new Coordinate(coord.getX(), coord.getY()));
                    coordIsInArea = p.contains(point);
                    if (coordIsInArea) break;
                }
            }
            else {
                coordIsInArea = CoordUtils.calcEuclideanDistance(center, coord) <= radius;
            }
            coordCache.put(coord, coordIsInArea);
            return coordIsInArea;
        }
    }

    private static class CutterConfigGroup extends ReflectiveConfigGroup {

        static final String GROUP_NAME = "cutter";

        private String commuterTag = "outAct";
        private String popTag = "inAct";
        private String pathToInputScnearioFolder;
        private String pathToTargetFolder = "Cutter";

        private double xCoordCenter = 598720.4;
        private double yCoordCenter = 122475.3;
        private int radius = 30000;
        private boolean useShapeFile = false;
        private String pathToShapeFile = null;

        CutterConfigGroup(String name) {
            super(name);
        }

        @StringGetter("commuterTag")
        String getCommuterTag() {
            return commuterTag;
        }

        @StringSetter("commuterTag")
        void setCommuterTag(String commuterTag) {
            this.commuterTag = commuterTag;
        }

        @StringGetter("inputScenarioFolder")
        String getPathToInputScenarioFolder() {
            return pathToInputScnearioFolder;
        }

        @StringSetter("inputScenarioFolder")
        void setPathToInputScenarioFolder(String pathToInputScenarioFolder) {
            this.pathToInputScnearioFolder = pathToInputScenarioFolder;
        }

        @StringGetter("outputFolder")
        String getPathToTargetFolder() {
            return pathToTargetFolder;
        }

        @StringSetter("outputFolder")
        void setPathToTargetFolder(String pathToTargetFolder) {
            this.pathToTargetFolder = pathToTargetFolder;
        }

        @StringGetter("xCoordCenter")
        double getxCoordCenter() {
            return xCoordCenter;
        }

        @StringSetter("xCoordCenter")
        void setxCoordCenter(double xCoordCenter) {
            this.xCoordCenter = xCoordCenter;
        }

        @StringGetter("yCoordCenter")
        double getyCoordCenter() {
            return yCoordCenter;
        }

        @StringSetter("yCoordCenter")
        void setyCoordCenter(double yCoordCenter) {
            this.yCoordCenter = yCoordCenter;
        }

        @StringGetter("radius")
        int getRadius() {
            return radius;
        }

        @StringSetter("radius")
        void setRadius(int radius) {
            this.radius = radius;
        }

        @StringGetter("useShapeFile")
        boolean getUseShapeFile() {
            return useShapeFile;
        }

        @StringSetter("useShapeFile")
        void setUseShapeFile(boolean useShapeFile) {
            this.useShapeFile = useShapeFile;
        }

        @StringGetter("pathToShapeFile")
        String getPathToShapeFile() {
            return pathToShapeFile;
        }

        @StringSetter("pathToShapeFile")
        void setPathToShapeFile(String pathToShapeFile) {
            this.pathToShapeFile = pathToShapeFile;
        }

        @StringGetter("popTag")
        public String getPopTag() {
            return popTag;
        }

        @StringSetter("popTag")
        void setPopTag(String tag){
            this.popTag = tag;
        }
    }
}
