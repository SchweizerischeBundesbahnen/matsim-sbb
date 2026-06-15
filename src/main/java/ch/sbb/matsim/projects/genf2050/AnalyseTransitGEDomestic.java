package ch.sbb.matsim.projects.genf2050;

import ch.sbb.matsim.config.variables.SBBModes;
import ch.sbb.matsim.zones.Zone;
import ch.sbb.matsim.zones.Zones;
import ch.sbb.matsim.zones.ZonesLoader;
import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.Plan;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.population.io.PopulationReader;
import org.matsim.core.router.TripStructureUtils;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.pt.routes.TransitPassengerRoute;
import org.matsim.pt.transitSchedule.api.TransitLine;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class AnalyseTransitGEDomestic {
    static void main() {
        Zones zones = ZonesLoader.loadZones("zones","/Volumes/mobi/40_Projekte/20251005_GE_2050_plus/plans/var/output/var.mobi-zones.shp");
        Scenario scenario = ScenarioUtils.createScenario(ConfigUtils.createConfig());
        new PopulationReader(scenario).readFile("/Volumes/mobi/40_Projekte/20251005_GE_2050_plus/sim/var_cornavin_metro_as_other/output/var_cornavin_metro_as_other.output_experienced_plans.xml.gz");
        Set<Id<TransitLine>> linesOfInterest = Set.of(Id.create("M1_EV",TransitLine.class),Id.create("SBB_AK35k_001-D-11501",TransitLine.class),Id.create("SBB_AK35k_041-C-01127",TransitLine.class));
        Set<Id<Zone>> zonesOfInterest = zones.getZones().stream().filter(zone -> "25".equals(String.valueOf(zone.getAttribute("kt_id")))).map(Zone::getId).collect(Collectors.toSet());
        Map<Id<TransitLine>, DescriptiveStatistics> distancesPerLine = linesOfInterest.stream().collect(Collectors.toMap(transitLineId ->  transitLineId, _ -> new DescriptiveStatistics()));
        Map<Id<TransitLine>, DescriptiveStatistics> distancesPerLineOnly = linesOfInterest.stream().collect(Collectors.toMap(transitLineId ->  transitLineId, _ -> new DescriptiveStatistics()));
        DescriptiveStatistics ptDist = new DescriptiveStatistics();
        for (Person person : scenario.getPopulation().getPersons().values()){
            Plan plan = person.getSelectedPlan();
            TripStructureUtils.getTrips(plan).stream()
                    .filter(trip -> trip.getLegsOnly().stream().anyMatch(leg -> leg.getRoutingMode().equals(SBBModes.PT)))
                    .filter(trip -> isEntirelyInZones(trip, zonesOfInterest, zones))
                    .forEach(trip -> {
                                double ptDistance = trip.getLegsOnly().stream().filter(leg -> leg.getRoute() instanceof TransitPassengerRoute).mapToDouble(leg -> leg.getRoute().getDistance()).sum();
                                Set<Id<TransitLine>> collectedLinesOfInterest = new HashSet<>();
                                trip.getLegsOnly().stream().filter(leg -> leg.getRoute() instanceof TransitPassengerRoute)
                                        .filter(leg -> linesOfInterest.contains(((TransitPassengerRoute) leg.getRoute()).getLineId()))
                                        .forEach(
                                            leg -> {
                                                TransitPassengerRoute route = (TransitPassengerRoute) leg.getRoute();
                                                collectedLinesOfInterest.add(route.getLineId());
                                                distancesPerLineOnly.get(route.getLineId()).addValue(route.getDistance());
                                            }
                                        );
                                ptDist.addValue(ptDistance);
                                for (Id<TransitLine> lineId : collectedLinesOfInterest) {
                                    distancesPerLine.get(lineId).addValue(ptDistance);
                                }
                            }
                        );
                    }

        for (var l : distancesPerLine.entrySet()) {
            System.out.println(l.getKey());
            System.out.println("total pt distance" + distancesPerLine.get(l.getKey()).getMean());
            System.out.println("line pt distance" + distancesPerLineOnly.get(l.getKey()).getMean());
            System.out.println(distancesPerLine.get(l.getKey()).getN()*4);

        }
        System.out.println("PT Distance overall");
        System.out.println(ptDist.getMean());
        System.out.println(ptDist.getN()*4);

        }

    private static boolean isEntirelyInZones(TripStructureUtils.Trip trip, Set<Id<Zone>> zonesOfInterest, Zones zones) {
        Zone originZone = zones.findZone(trip.getOriginActivity().getCoord());
        Zone destinationZone = zones.findZone(trip.getDestinationActivity().getCoord());
        if (originZone == null || destinationZone == null) {
            return false;}
        return zonesOfInterest.contains(originZone.getId()) || zonesOfInterest.contains(destinationZone.getId());
    }

}

