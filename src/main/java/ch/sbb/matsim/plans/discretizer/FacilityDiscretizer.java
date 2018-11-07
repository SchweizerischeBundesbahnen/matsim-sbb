package ch.sbb.matsim.plans.discretizer;

import ch.sbb.matsim.analysis.matrices.Utils;
import ch.sbb.matsim.csv.CSVReader;
import ch.sbb.matsim.csv.CSVWriter;
import org.apache.log4j.Logger;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.core.utils.gis.ShapeFileReader;
import org.matsim.facilities.ActivityFacilities;
import org.matsim.facilities.ActivityFacility;
import org.matsim.facilities.MatsimFacilitiesReader;
import org.matsim.utils.objectattributes.ObjectAttributesXmlReader;
import org.opengis.feature.simple.SimpleFeature;

import java.io.IOException;
import java.util.*;

public class FacilityDiscretizer {

    private static final Logger log = Logger.getLogger(FacilityDiscretizer.class);

    private final ActivityFacilities facilities;
    private final Random random;
    private final Map<Integer, SimpleFeature> zonesById;
    private AbmZoneFacilities zoneData;

    public FacilityDiscretizer(ActivityFacilities facilities, Map<Integer, SimpleFeature> zonesById)    {
        this.facilities = facilities;
        this.random = new Random(20180906L);
        this.zonesById = zonesById;
        assignFacilitiesToZones();
    }

    private void assignFacilitiesToZones()   {
        AbmZoneFacilities zoneData = new AbmZoneFacilities();

        for(ActivityFacility fac : this.facilities.getFacilities().values())  {
            int zoneId = (int) this.facilities.getFacilityAttributes().getAttribute(fac.getId().toString(), "tZone");
            if(zoneId != -99)   {
                Set<String> options = fac.getActivityOptions().keySet();
                for(String opt: options)    {
                    zoneData.addFacility(zoneId, opt, fac.getId());
                }
            }
        }
        this.zoneData = zoneData;
    }

    // TODO: would be better to return a facility instead of coord, maybe not possible...
    // would be necessary to "invent" new facilities
    public Coord getRandomCoord(int zoneId, String type)  {
        AbmFacilities typesInZone = this.zoneData.getActivityTypes(zoneId);
        if (typesInZone == null)    {
            Coord coord = Utils.getRandomCoordinateInFeature(this.zonesById.get(zoneId), this.random);
            log.info("Could not find any facility in zone " + zoneId + "...");
            return coord;
        }

        List<Id<ActivityFacility>> facilityList = this.zoneData.getActivityTypes(zoneId).getFacilitiesForType(type);
        if(facilityList == null)    {
            Coord coord = Utils.getRandomCoordinateInFeature(this.zonesById.get(zoneId), this.random);
            log.info("Could not find any facility in zone " + zoneId + " for type " + type + "...");
            return coord;
        }


        Id<ActivityFacility> fid = facilityList.get(this.random.nextInt(facilityList.size()));
        return this.facilities.getFacilities().get(fid).getCoord();
    }

    // TODO: get facility from weighted list

    // use discretizer as stand-alone tool
    public static void main(String[] args)  {
        String pathToFacilities = args[0];
        String pathToFacilityAttributes = args[1];
        String pathToShapeFile = args[2];
        String pathToTripList = args[3];
        String output = args[4];
        String activitiyType = args[5];

        Scenario scenario = ScenarioUtils.createScenario(ConfigUtils.createConfig());
        new MatsimFacilitiesReader(scenario).readFile(pathToFacilities);
        new ObjectAttributesXmlReader(scenario.getActivityFacilities().getFacilityAttributes()).
                readFile(pathToFacilityAttributes);

        Collection<SimpleFeature> zones = new ShapeFileReader().readFileAndInitialize(pathToShapeFile);
        Map<Integer, SimpleFeature> zonesById = new HashMap<>();
        for (SimpleFeature zone : zones) {
            int zoneId = (int) Double.parseDouble(zone.getAttribute("ID").toString());
            zonesById.put(zoneId, zone);
        }

        FacilityDiscretizer discretizer = new FacilityDiscretizer(scenario.getActivityFacilities(), zonesById);

        String[] columnsIn = {"trip_id","from_zone","to_zone"};
        String[] columnsOut = {"trip_id","from_zone","to_zone","from_x","from_y","to_x","to_y"};

        try (CSVReader reader = new CSVReader(columnsIn, pathToTripList, ";");
             CSVWriter writer = new CSVWriter("", columnsOut, output)) {

            Map<String, String> map = reader.readLine(); // header
            while ((map = reader.readLine()) != null) {
                // set trip id
                writer.set(columnsOut[0], map.get(columnsIn[0]));

                String fromZone = map.get(columnsIn[1]);
                if(!fromZone.isEmpty()) {
                    int zoneId = Integer.parseInt(fromZone);
                    Coord fromCoord = discretizer.getRandomCoord(zoneId, activitiyType);
                    writer.set(columnsOut[1], fromZone);
                    writer.set(columnsOut[3], String.valueOf(fromCoord.getX()));
                    writer.set(columnsOut[4], String.valueOf(fromCoord.getY()));
                }
                else    {
                    writer.set(columnsOut[1], "");
                    writer.set(columnsOut[3], "");
                    writer.set(columnsOut[4], "");
                }

                String toZone = map.get(columnsIn[2]);
                if(!fromZone.isEmpty()) {
                    int zoneId = Integer.parseInt(toZone);
                    Coord toCoord = discretizer.getRandomCoord(zoneId, activitiyType);
                    writer.set(columnsOut[2], fromZone);
                    writer.set(columnsOut[5], String.valueOf(toCoord.getX()));
                    writer.set(columnsOut[6], String.valueOf(toCoord.getY()));
                }
                else    {
                    writer.set(columnsOut[2], "");
                    writer.set(columnsOut[5], "");
                    writer.set(columnsOut[6], "");
                }

                writer.writeRow();
            }
        }
        catch (IOException e)   {
            log.warn(e);
        }
    }
}
