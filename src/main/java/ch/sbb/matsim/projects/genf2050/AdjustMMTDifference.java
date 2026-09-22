package ch.sbb.matsim.projects.genf2050;

import ch.sbb.matsim.config.variables.SBBModes;
import ch.sbb.matsim.config.variables.Variables;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.population.Activity;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.Plan;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.gbl.MatsimRandom;
import org.matsim.core.population.io.PopulationReader;
import org.matsim.core.population.io.PopulationWriter;
import org.matsim.core.scenario.ScenarioUtils;

import java.util.Random;

public class AdjustMMTDifference {
    static Random r = MatsimRandom.getRandom();
    static void main() {
        Scenario scenario = ScenarioUtils.createScenario(ConfigUtils.createConfig());
        new PopulationReader(scenario).readFile("/Volumes/mobi/40_Projekte/20251005_GE_2050_plus/plans/l7/output/l7.plans.xml.gz");
        Coord startCoord = new Coord(2495156.720000,1120976.590000);
        Coord endCoord = new Coord(2500795.900000,	1117500.850000);
        var fac = scenario.getPopulation().getFactory();
        for (int i = 0; i < 9800; i++) {
            Person p = fac.createPerson(Id.createPersonId("p_ma_"+i));
            p.getAttributes().putAttribute("slice", i%50);
            p.getAttributes().putAttribute("subpopulation", Variables.CB_RAIL);
            Plan plan = fac.createPlan();
            Coord homeCoord = varyCoord(startCoord, 800);
            Activity home = fac.createActivityFromCoord("home", homeCoord);
            home.setEndTime(6.5 * 3600+ r.nextInt(9000));

            Activity work = fac.createActivityFromCoord("work", varyCoord(endCoord,200));
            work.setEndTime((12.5 * 3600) + r.nextInt(18000));

            Activity home2 = fac.createActivityFromCoord("home", homeCoord);
            plan.addActivity(home);
            plan.addLeg(fac.createLeg(SBBModes.PT));
            plan.addActivity(work);
            plan.addLeg(fac.createLeg(SBBModes.PT));
            plan.addActivity(home2);
            p.addPlan(plan);
            scenario.getPopulation().addPerson(p);

        }
        new PopulationWriter(scenario.getPopulation()).write("/Users/joschka.bischoff/tmp/l7.plans-mmt-diff-added.xml.zst");
    }

    private static Coord varyCoord(Coord startCoord, double diameter) {
        return new Coord(startCoord.getX() + r.nextDouble() * diameter - diameter/2., startCoord.getY() + r.nextDouble() * diameter - diameter/2.);
    }
}
