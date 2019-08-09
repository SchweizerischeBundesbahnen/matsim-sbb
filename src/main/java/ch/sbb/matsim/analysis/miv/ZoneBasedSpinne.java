package ch.sbb.matsim.analysis.miv;

import ch.sbb.matsim.csv.CSVWriter;
import ch.sbb.matsim.zones.Zone;
import ch.sbb.matsim.zones.Zones;
import ch.sbb.matsim.zones.ZonesLoader;
import ch.sbb.matsim.zones.ZonesQueryCache;
import org.apache.log4j.Logger;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.population.Plan;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.network.io.MatsimNetworkReader;
import org.matsim.core.population.io.StreamingPopulationReader;
import org.matsim.core.population.routes.NetworkRoute;
import org.matsim.core.router.TripStructureUtils;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.core.utils.io.UncheckedIOException;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class ZoneBasedSpinne {

    private static final Logger log = Logger.getLogger(ZoneBasedSpinne.class);
    private ConcurrentHashMap<Id<Link>, Integer> linkVolumes;

    public static void main(String[] args)  {
        new ZoneBasedSpinne().run("\\\\k13536\\mobi\\50_Ergebnisse\\MOBi_2.0\\sim\\",
                "\\\\k13536\\mobi\\50_Ergebnisse\\MOBi_2.0\\sim\\2.0.0_10pct_release\\output\\CH.10pct.2016.output_network.xml.gz",
                "\\\\k13536\\mobi\\50_Ergebnisse\\MOBi_2.0\\zones\\v6\\output\\epsg_21781\\mobi_zones.shp",
                "msrid", "1",
                "\\\\k13536\\mobi\\99_Playgrounds\\PM\\Test_ZoneBasedMIVSpinne\\MIV_Spinne_MSR_Zuerich.csv.gz");
    }

    public void run(String plans, String networkFile, String shapeFile, String attName,
                    String attValue, String csvOut)    {
        Zones zones = ZonesLoader.loadZones("spinne", shapeFile, null);
        ZonesQueryCache zonesCache = new ZonesQueryCache(zones);

        Scenario scenario = ScenarioUtils.createScenario(ConfigUtils.createConfig());
        new MatsimNetworkReader(scenario.getNetwork()).readFile(networkFile);

        HashSet<Id<Link>> linksInZone = getLinksInZone(scenario, zonesCache, attName, attValue);

        this.linkVolumes = new ConcurrentHashMap<>();
        List<String> planFiles = new ArrayList<>();
        for(int i = 1; i < 5; i++) {
            planFiles.add(plans + "2.0.1."+i+"_release_25pct_"+i+"\\output\\CH.25pct."+i+".2016.output_plans.xml.gz");
        }
        planFiles.parallelStream().forEach(planFile -> readPopulationAndCalcVolumes(planFile, linksInZone));

        writeCSV(csvOut);
    }

    private static HashSet<Id<Link>> getLinksInZone(Scenario scenario, ZonesQueryCache zonesCache, String attName,
                                                    String attValue)  {
        HashSet<Id<Link>> linksInZone = new HashSet<>();
        scenario.getNetwork().getLinks().values().parallelStream().
                filter(link -> link.getAllowedModes().contains(TransportMode.car)).
                forEach(link -> {
                    Coord fromCoord = link.getFromNode().getCoord();
                    Coord toCoord = link.getToNode().getCoord();
                    double x = (fromCoord.getX() + toCoord.getX()) / 2.0;
                    double y = (fromCoord.getY() + toCoord.getY()) / 2.0;
                    Zone z = zonesCache.findZone(x, y);
                    String shapeValue = z == null ? null : z.getAttribute(attName).toString();
                    if (shapeValue == null) {
                        return;
                    }
                    if (shapeValue.equals(attValue)) {
                        linksInZone.add(link.getId());
                    }
                });
        log.info("Links in zone: " + linksInZone.size());
        log.info("Total number of links: " + scenario.getNetwork().getLinks().size());
        return linksInZone;
    }

    private void readPopulationAndCalcVolumes(String plansFile, HashSet<Id<Link>> linksInZone)   {
        log.info("loading plans from file " + plansFile);

        Scenario scenario = ScenarioUtils.createScenario(ConfigUtils.createConfig());
        StreamingPopulationReader r = new StreamingPopulationReader(scenario);
        r.addAlgorithm(person -> {
            Plan selectedPlan = person.getSelectedPlan();
            TripStructureUtils.getLegs(selectedPlan).parallelStream().
                    filter(leg -> leg.getMode().equals(TransportMode.car)).
                    forEach(leg -> {
                        NetworkRoute route = (NetworkRoute) leg.getRoute();
                        if (linksInZone.contains(route.getStartLinkId()) || linksInZone.contains(route.getEndLinkId())) {
                            addRouteToLinkVolumes(route);
                        }
                        else    {
                            for (Id<Link> link: route.getLinkIds()) {
                                if (linksInZone.contains(link)) {
                                    addRouteToLinkVolumes(route);
                                    break;
                                }
                            }
                        }
                    });
        });
        r.readFile(plansFile);
    }

    private void addRouteToLinkVolumes(NetworkRoute route)  {
        this.linkVolumes.merge(route.getStartLinkId(), 1, (a, b) -> a + b);
        this.linkVolumes.merge(route.getEndLinkId(), 1, (a, b) -> a + b);
        route.getLinkIds().forEach(linkId -> this.linkVolumes.merge(linkId, 1, (a, b) -> a + b));
    }

    private void writeCSV(String fileName) {
        try (CSVWriter writer = new CSVWriter("", VOLUMES_COLUMNS, fileName)) {
            for (Map.Entry<Id<Link>, Integer> entry : this.linkVolumes.entrySet()) {
                Id<Link> link = entry.getKey();
                int volume = entry.getValue();
                writer.set(VOLUMES_COLUMNS[0], link.toString());
                writer.set(VOLUMES_COLUMNS[1], Integer.toString(volume));
                writer.writeRow();
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static final String[] VOLUMES_COLUMNS = new String[]{
            "LINK_ID_SIM",
            "VOLUME_SIM"
    };
}
