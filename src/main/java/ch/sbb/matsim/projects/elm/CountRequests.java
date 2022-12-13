package ch.sbb.matsim.projects.elm;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import org.matsim.api.core.v01.Scenario;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.core.utils.geometry.CoordUtils;
import org.matsim.facilities.ActivityFacilities;
import org.matsim.facilities.ActivityFacility;
import org.matsim.facilities.MatsimFacilitiesReader;
import org.matsim.pt.transitSchedule.api.TransitSchedule;
import org.matsim.pt.transitSchedule.api.TransitScheduleReader;
import org.matsim.pt.transitSchedule.api.TransitStopFacility;

public class CountRequests {

    TransitSchedule transitSchedule;
    List<TransitStopFacility> transitScheduleTrainStation = new ArrayList<>();
    ActivityFacilities facilities;

    public static void main(String[] args) {

        String transitScheduleFile = "Z:/10_Daten/Matsimba_Kalibration/supply/NPVM2017_Simba2017_LV95_carfeeder_alle_links/transitSchedule.xml.gz";
        String facilityFile = "Z:/40_Projekte/20220114_MOBi_3.3/2017/plans/3.3.2017.7.100pct/facilities.xml.gz";

        CountRequests countRequests = new CountRequests(transitScheduleFile, facilityFile);

        var cS = countRequests.countStation();
        var cF = countRequests.countFacilites();

        System.out.println("-------------------------");
        System.out.println("Station Connection: " + cS);
        System.out.println("Facilities Connection: " + cF);
        System.out.println("Total: " + (cS + cF));

    }

    CountRequests(String transitScheduleFile, String facilityFile) {
        Scenario scenario = ScenarioUtils.createScenario(ConfigUtils.createConfig());
        new TransitScheduleReader(scenario).readFile(transitScheduleFile);
        new MatsimFacilitiesReader(scenario).readFile(facilityFile);
        this.transitSchedule = scenario.getTransitSchedule();
        this.facilities = scenario.getActivityFacilities();

        for (TransitStopFacility transitStopFacility : transitSchedule.getFacilities().values()) {
            if (transitStopFacility.getAttributes().getAttribute("03_Stop_Code") != null) {
                transitScheduleTrainStation.add(transitStopFacility);
            }
        }
    }

    private int countFacilites() {
        System.out.println("Start counting population facilites");
        System.out.println("Done: 0%");
        var f = facilities.getFacilities().size();
        var count = 0;
        var pct = 5;
        LinkedHashSet<String> connectionList = new LinkedHashSet<>();
        for (ActivityFacility facility : facilities.getFacilities().values()) {
            for (TransitStopFacility tF : transitScheduleTrainStation) {
                if (!facility.getCoord().equals(tF.getCoord()) &&
                    CoordUtils.calcEuclideanDistance(facility.getCoord(), tF.getCoord()) < 2000) {
                    String f1ID = facility.getId().toString();
                    String f2ID = tF.getId().toString();
                    connectionList.add(f2ID + "_" + f1ID);
                }
            }
            count++;
            if (count == f * pct / 100) {
                System.out.println("Done: " + pct + "%");
                pct += 5;
            }
        }
        System.out.println("Finished counting population facilites");
        return connectionList.size();
    }

    private int countStation() {
        System.out.println("Start counting transit facilites");
        System.out.println("Done: 0%");
        var f = transitSchedule.getFacilities().size();
        var count = 0;
        var pct = 5;

        LinkedHashSet<String> connectionList = new LinkedHashSet<>();
        for (TransitStopFacility facility : transitSchedule.getFacilities().values()) {
            for (TransitStopFacility checkFacility : transitScheduleTrainStation) {
                if (!facility.equals(checkFacility) &&
                    CoordUtils.calcEuclideanDistance(facility.getCoord(), checkFacility.getCoord()) < 2000) {
                    String f1ID = facility.getId().toString();
                    String f2ID = checkFacility.getId().toString();
                    connectionList.add(f1ID + "_" + f2ID);
                }
            }
            count++;
            if (count == f * pct / 100) {
                System.out.println("Done: " + pct + "%");
                pct += 5;
            }
        }
        System.out.println("Finished counting transit facilites");
        return connectionList.size();
    }

}
