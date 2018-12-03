package ch.sbb.matsim.plans.facilities;

import ch.sbb.matsim.config.variables.Filenames;
import ch.sbb.matsim.config.variables.SBBActivities;
import ch.sbb.matsim.csv.CSVReader;
import ch.sbb.matsim.synpop.facilities.ZoneIdAssigner;
import ch.sbb.matsim.synpop.zoneAggregator.ZoneAggregator;
import org.apache.log4j.Logger;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.facilities.*;

import java.io.File;
import java.io.IOException;
import java.util.Map;

public class FacilitiesReader {
    public static final String FACILITY_ID = "facility_id";
    public static final String X = "X";
    public static final String Y = "Y";

    private static final Logger log = Logger.getLogger(FacilitiesReader.class);
    private final ActivityFacilities facilities;
    private final String splitBy;

    public FacilitiesReader(final String splitBy) {
        final Scenario scenario = ScenarioUtils.createScenario(ConfigUtils.createConfig());
        this.facilities = scenario.getActivityFacilities();
        this.splitBy = splitBy;

    }

    private void read(final String filename) {
        try (final CSVReader reader = new CSVReader(filename, this.splitBy)) {
            Map<String, String> map;
            while ((map = reader.readLine()) != null) {

                final Id<ActivityFacility> id = Id.create(map.get(FACILITY_ID), ActivityFacility.class);
                final Coord coord = new Coord(Float.parseFloat(map.get(X)), Float.parseFloat(map.get(Y)));

                final ActivityFacility facility = this.facilities.getFactory().createActivityFacility(id, coord);

                OpeningTime openingTime = null;
                if (!map.get("opening").equals("")) {
                    openingTime = new OpeningTimeImpl(Double.parseDouble(map.get("opening")), Double.parseDouble(map.get("closing")));
                }

                for (final String activity : SBBActivities.abmActs2matsimActs.keySet()) {
                    if (map.containsKey(activity) && map.get(activity).equals("True")) {
                        final ActivityOption option = this.facilities.getFactory().createActivityOption(SBBActivities.abmActs2matsimActs.get(activity));
                        if (openingTime != null) {
                            option.addOpeningTime(openingTime);
                        }
                        facility.addActivityOption(option);
                    }
                }

                /*
                for (final String column : map.keySet()) {
                    if (!(column.equals(FACILITY_ID) || !column.equals(X) || !column.equals(Y)) || !SBBActivities.abmActs2matsimActs.values().contains(column)) {
                        facility.getAttributes().putAttribute(column, map.get(column));
                    }
                }
                */

                this.facilities.addActivityFacility(facility);
            }
        } catch (IOException e) {
            log.warn(e);
        }

    }

    private void addSpatialInformation(String shapefile, String shapeAttribute, String facilityAttribute)    {
        ZoneAggregator zoneAggregator = new ZoneAggregator<>(shapefile, shapeAttribute);
        for (ActivityFacility activityFacility : this.facilities.getFacilities().values()) {
            zoneAggregator.add(activityFacility, activityFacility.getCoord());
        }
        ZoneIdAssigner assigner = new ZoneIdAssigner(zoneAggregator);
        assigner.assignIds(facilityAttribute);
    }

    private void write(String folder) {
        new FacilitiesWriter(this.facilities).write(new File(folder, Filenames.FACILITIES).toString());
    }

    public ActivityFacilities convert(String filename, String shapeFile, String folder) {
        this.read(filename);
        this.addSpatialInformation(shapeFile, "msrid", "ms_region");
        this.write(folder);
        return this.facilities;
    }

}
