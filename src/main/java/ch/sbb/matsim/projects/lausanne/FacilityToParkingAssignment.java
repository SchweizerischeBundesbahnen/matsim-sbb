package ch.sbb.matsim.projects.lausanne;

import org.apache.commons.lang3.mutable.MutableInt;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.network.Link;
import org.matsim.contrib.common.util.WeightedRandomSelection;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.gbl.MatsimRandom;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.core.utils.geometry.CoordUtils;
import org.matsim.facilities.FacilitiesUtils;
import org.matsim.facilities.FacilitiesWriter;
import org.matsim.facilities.MatsimFacilitiesReader;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class FacilityToParkingAssignment {

    public static void main(String[] args) {
        Random random = MatsimRandom.getRandom();
        Scenario scenario = ScenarioUtils.createScenario(ConfigUtils.createConfig());
        new MatsimFacilitiesReader(scenario).readFile("\\\\wsbbrz0283\\mobi\\40_Projekte\\20240226_Lausanne\\Etappe_2\\sim\\ref\\prepared\\plans_facilities.xml.gz");
        double threshold = 300d;
        List<ParkingGarage> parkingGarageList = new ArrayList<>();

        WeightedRandomSelection<Id<Link>> flonParking = new WeightedRandomSelection<>(random);
        flonParking.add(Id.createLinkId("1gha_fzi2"), 0.5);
        flonParking.add(Id.createLinkId("1gji_8rc2"), 0.5);
        parkingGarageList.add(new ParkingGarage("flon", new Coord(2537659.26141681, 1152603.01554034), flonParking));

        WeightedRandomSelection<Id<Link>> riponneParking = new WeightedRandomSelection<>(random);
        riponneParking.add(Id.createLinkId("1hql_7abq"), 1.0);
        //riponneParking.add(Id.createLinkId("1hsw_fmms"), 0.7);
        parkingGarageList.add(new ParkingGarage("riponne", new Coord(2538151.99941723, 1152795.25284032), riponneParking));
        WeightedRandomSelection<Id<Link>> centraleParking = new WeightedRandomSelection<>(random);
        centraleParking.add(Id.createLinkId("1ilz_cvkc"), 1.0);
        parkingGarageList.add(new ParkingGarage("centrale", new Coord(2538392.46961752, 1152497.58924012), centraleParking));
        MutableInt mutableInt = new MutableInt();
        scenario.getActivityFacilities().getFacilities().values().forEach(
                activityFacility -> {
                    ParkingGarage garage = findNearestParking(activityFacility.getCoord(), parkingGarageList);
                    if (CoordUtils.calcEuclideanDistance(garage.coord, activityFacility.getCoord()) < threshold) {
                        FacilitiesUtils.setLinkID(activityFacility, garage.accessLinkId.select());
                        mutableInt.increment();
                    }
                }
        );
        System.out.println("Set the facility link id for " + mutableInt);
        new FacilitiesWriter(scenario.getActivityFacilities()).write("\\\\wsbbrz0283\\mobi\\40_Projekte\\20240226_Lausanne\\Etappe_2\\sim\\ref\\plans_facilities_mappedToParking_pplus_v2.xml.gz");

    }

    private static ParkingGarage findNearestParking(Coord coord, List<ParkingGarage> parkingGarageList) {
        double shortestDistance = Double.MAX_VALUE;
        ParkingGarage closestGarage = null;
        for (ParkingGarage garage : parkingGarageList) {
            double distance = CoordUtils.calcEuclideanDistance(coord, garage.coord);
            if (distance < shortestDistance) {
                shortestDistance = distance;
                closestGarage = garage;
            }
        }
        return closestGarage;
    }

    record ParkingGarage(String name, Coord coord, WeightedRandomSelection<Id<Link>> accessLinkId) {
    }
}
