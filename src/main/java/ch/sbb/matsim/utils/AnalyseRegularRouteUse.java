package ch.sbb.matsim.utils;

import ch.sbb.matsim.config.variables.SBBModes;
import ch.sbb.matsim.config.variables.Variables;
import ch.sbb.matsim.routing.access.AccessEgressModule;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.network.NetworkWriter;
import org.matsim.api.core.v01.population.Plan;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.network.NetworkUtils;
import org.matsim.core.network.io.MatsimNetworkReader;
import org.matsim.core.population.PopulationUtils;
import org.matsim.core.population.io.StreamingPopulationReader;
import org.matsim.core.population.routes.NetworkRoute;
import org.matsim.core.router.TripStructureUtils;
import org.matsim.core.scenario.ScenarioUtils;

import java.util.HashMap;
import java.util.Map;

public class AnalyseRegularRouteUse {


    public static void main(String[] args) {
        String plans = "\\\\wsbbrz0283\\mobi\\40_Projekte\\20220114_MOBi_3.3\\2017\\sim\\3.3.2017.0\\output\\M332017.0.output_plans.xml.gz";
        String net = "\\\\wsbbrz0283\\mobi\\40_Projekte\\20220114_MOBi_3.3\\2017\\sim\\3.3.2017.0\\output\\M332017.0.output_network.xml.gz";
        Map<Id<Link>,Integer> foreignLinks = new HashMap<>();
        Network network = NetworkUtils.createNetwork();
        new MatsimNetworkReader(network).readFile(net);
        StreamingPopulationReader spr = new StreamingPopulationReader(ScenarioUtils.createScenario(ConfigUtils.createConfig()));
        spr.addAlgorithm(person -> {
            String subpop = PopulationUtils.getSubpopulation(person);
            if (subpop.equals(Variables.REGULAR)){
                Plan plan = person.getSelectedPlan();
                TripStructureUtils.getLegs(plan).stream().filter(leg->leg.getMode().equals(SBBModes.CAR)).forEach(
                        leg -> {
                            NetworkRoute r = (NetworkRoute) leg.getRoute();
                            for (Id<Link> l : r.getLinkIds()){
                                Link link = network.getLinks().get(l);
                                boolean isCh = (boolean) link.getAttributes().getAttribute(AccessEgressModule.IS_CH);
                                if (!isCh){
                                    int load = foreignLinks.computeIfAbsent(l,linkId->0);
                                    load++;
                                    foreignLinks.put(l,load);
                                }
                            }
                        }
                );
            }
        });
        spr.readFile(plans);
        for (Map.Entry<Id<Link>,Integer> e : foreignLinks.entrySet()){
            Link l = network.getLinks().get(e.getKey());
            l.getAttributes().putAttribute("foreignLoad",e.getValue());
        }

        new NetworkWriter(network).write("C:\\devsbb\\330-foreignNet.xml.gz");

    }
}
