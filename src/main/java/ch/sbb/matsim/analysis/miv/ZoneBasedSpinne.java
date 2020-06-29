package ch.sbb.matsim.analysis.miv;

import ch.sbb.matsim.config.variables.SBBModes;
import ch.sbb.matsim.config.variables.Variables;
import ch.sbb.matsim.csv.CSVWriter;
import ch.sbb.matsim.zones.Zone;
import ch.sbb.matsim.zones.Zones;
import ch.sbb.matsim.zones.ZonesLoader;
import ch.sbb.matsim.zones.ZonesQueryCache;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.apache.log4j.Logger;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.population.Plan;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.network.io.MatsimNetworkReader;
import org.matsim.core.population.io.StreamingPopulationReader;
import org.matsim.core.population.routes.NetworkRoute;
import org.matsim.core.router.TripStructureUtils;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.core.utils.io.UncheckedIOException;

public class ZoneBasedSpinne {

    private static final Logger log = Logger.getLogger(ZoneBasedSpinne.class);
    private ConcurrentHashMap<Id<Link>, LinkVolumes> linkVolumes = new ConcurrentHashMap<>();

    public static void main(String[] args)  {
        new ZoneBasedSpinne().run("\\\\k13536\\mobi\\50_Ergebnisse\\MOBi_2.0\\sim\\",
                "\\\\k13536\\mobi\\50_Ergebnisse\\MOBi_2.0\\sim\\2.0.0_10pct_release\\output\\CH.10pct.2016.output_network.xml.gz",
                "\\\\k13536\\mobi\\50_Ergebnisse\\MOBi_2.0\\zones\\v6\\output\\epsg_21781\\mobi_zones.shp",
                args[0], args[1], args[2], args[3]);
    }

    private static HashSet<Id<Link>> getLinksInZone(String networkFile, ZonesQueryCache zonesCache, String attName,
                                                    String attValue) {
        Scenario scenario = ScenarioUtils.createScenario(ConfigUtils.createConfig());
        new MatsimNetworkReader(scenario.getNetwork()).readFile(networkFile);

        HashSet<Id<Link>> linksInZone = new HashSet<>();
        scenario.getNetwork().getLinks().values().stream().
                filter(link -> link.getAllowedModes().contains(SBBModes.CAR)).
                forEach(link -> {
                    Coord fromCoord = link.getFromNode().getCoord();
                    Coord toCoord = link.getToNode().getCoord();
                    double x = (fromCoord.getX() + toCoord.getX()) / 2.0;
                    double y = (fromCoord.getY() + toCoord.getY()) / 2.0;
                    Zone z = zonesCache.findZone(x, y);
                    String shapeValue = z == null ? null : z.getAttribute(attName).toString();
                    if (shapeValue != null && shapeValue.equals(attValue)) {
                        linksInZone.add(link.getId());
                    }
                });
        log.info("Links in zone: " + linksInZone.size());
        log.info("Total number of links: " + scenario.getNetwork().getLinks().size());
        return linksInZone;
    }

    public void run(String plans, String networkFile, String shapeFile, String attName,
                    String attValue, String name, String csvOut) {
        Zones zones = ZonesLoader.loadZones("spinne", shapeFile, null);
        ZonesQueryCache zonesCache = new ZonesQueryCache(zones);

        HashSet<Id<Link>> linksInZone = getLinksInZone(networkFile, zonesCache, attName, attValue);

        List<InputFiles> planFiles = new ArrayList<>();
        for (int i = 1; i < 5; i++) {
            planFiles.add(new InputFiles(plans + "2.0.1." + i + "_release_25pct_" + i + "\\output\\CH.25pct." + i + ".2016.output_plans.xml.gz"));
        }
        planFiles.parallelStream().forEach(planFile -> readPopulationAndCalcVolumes(planFile, linksInZone));

        writeCSV(name, csvOut);
    }

    private void readPopulationAndCalcVolumes(InputFiles files, HashSet<Id<Link>> linksInZone)   {
        log.info("loading plans from file " + files.plans);

        Scenario scenario1 = ScenarioUtils.createScenario(ConfigUtils.createConfig());
        StreamingPopulationReader r = new StreamingPopulationReader(scenario1);
        r.addAlgorithm(person -> {
            String subpop = person.getAttributes().getAttribute(Variables.SUBPOPULATION).toString();
            // take agent if it is not a freight agent
            if(subpop.equals("regular") || subpop.equals("cb_road") || subpop.equals("airport_road"))    {
                Plan selectedPlan = person.getSelectedPlan();
                TripStructureUtils.getLegs(selectedPlan).parallelStream().
                        filter(leg -> (leg.getMode().equals(SBBModes.CAR) || leg.getMode().equals(SBBModes.RIDE))).
                        forEach(leg -> {
                            NetworkRoute route = (NetworkRoute) leg.getRoute();
                            if (linksInZone.contains(route.getStartLinkId()) || linksInZone.contains(route.getEndLinkId())) {
                                addRouteToOrigDestVolumes(route);
                            } else {
                                for (Id<Link> link : route.getLinkIds()) {
                                    if (linksInZone.contains(link)) {
                                        addRouteToTransitVolumes(route);
                                        break;
                                    }
                                }
                            }
                        });
            }
        });
        r.readFile(files.plans);
    }

    private static class InputFiles {
        private String plans;

        private InputFiles(String plans) {
            this.plans = plans;
        }
    }

    private void addRouteToOrigDestVolumes(NetworkRoute route)  {
        this.putIfAbsent(route);
        this.linkVolumes.get(route.getStartLinkId()).incVolOrigDest();
        this.linkVolumes.get(route.getEndLinkId()).incVolOrigDest();
        route.getLinkIds().forEach(linkId -> this.linkVolumes.get(linkId).incVolOrigDest());
    }

    private void addRouteToTransitVolumes(NetworkRoute route)  {
        this.putIfAbsent(route);
        this.linkVolumes.get(route.getStartLinkId()).incVolTransit();
        this.linkVolumes.get(route.getEndLinkId()).incVolTransit();
        route.getLinkIds().forEach(linkId -> this.linkVolumes.get(linkId).incVolTransit());
    }

    private void putIfAbsent(NetworkRoute route) {
        this.linkVolumes.putIfAbsent(route.getStartLinkId(), new LinkVolumes());
        this.linkVolumes.putIfAbsent(route.getEndLinkId(), new LinkVolumes());
        route.getLinkIds().forEach(linkId -> this.linkVolumes.putIfAbsent(linkId, new LinkVolumes()));
    }

    private void writeCSV(String name, String fileName) {
        String[] columesColumns = new String[]{
                "LINK_ID_SIM",
                "Spinne_"+ name +"_OrigDest",
                "Spinne_"+ name +"_Transit"
        };

        try (CSVWriter writer = new CSVWriter("", columesColumns, fileName)) {
            for (Map.Entry<Id<Link>, LinkVolumes> entry : this.linkVolumes.entrySet()) {
                Id<Link> link = entry.getKey();
                LinkVolumes volume = entry.getValue();
                writer.set(columesColumns[0], link.toString());
                writer.set(columesColumns[1], Integer.toString(volume.volumesOrigDest));
                writer.set(columesColumns[2], Integer.toString(volume.volumesTransit));
                writer.writeRow();
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static class LinkVolumes {
        private int volumesOrigDest = 0;
        private int volumesTransit = 0;

        private void incVolOrigDest() {
            this.volumesOrigDest ++;
        }

        private void incVolTransit() {
            this.volumesTransit ++;
        }
    }
}
