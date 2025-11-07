package ch.sbb.matsim.plans.models.utils.analysis;


import ch.sbb.matsim.csv.CSVWriter;
import ch.sbb.matsim.plans.constants.SynpopConstants;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.Population;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.population.io.PopulationReader;
import org.matsim.core.scenario.ScenarioUtils;

public class AnalyzeDistanceDistribution {

    public static void writeDistanceDistribution(String distanceDistributionFile,
                                                                                                  Population population) {
        int workDistances[] = new int[40];
        int eduDistances[] = new int[40];
        for (Person person : population.getPersons().values()) {
            if (person.getAttributes().getAttribute(SynpopConstants.NET_DIST_WORK) != null) {
                double workDistance = (double) person.getAttributes().getAttribute(SynpopConstants.NET_DIST_WORK);
                int distanceBin = (int) Math.min(workDistance / 10000.0, 39);
                workDistances[distanceBin]++;
            }
            if (person.getAttributes().getAttribute(SynpopConstants.NET_DIST_SCHOOL) != null) {
                double eduDistance = (double) person.getAttributes().getAttribute(SynpopConstants.NET_DIST_SCHOOL);
                int distanceBin = (int) Math.min(eduDistance / 10000.0, 39);
                eduDistances[distanceBin]++;
            }
        }
        try (CSVWriter writer = new CSVWriter(null, new String[]{"distance_bin", "work_count", "edu_count"},distanceDistributionFile)) {
            for (int i = 0; i < workDistances.length; i++) {
                writer.set("distance_bin", Integer.toString( i* 10));
                writer.set("work_count", Integer.toString( workDistances[i]));
                writer.set("edu_count", Integer.toString( eduDistances[i]));
                writer.writeRow();
            }
        } catch (Exception e) {
            e.printStackTrace();

    }

}

    public static void main(String[] args) {
        String inputPopulationFile = args[0];
        String outputDistanceDistributionFile = args[1];
        Scenario scenario = ScenarioUtils.createScenario(ConfigUtils.createConfig());
        new PopulationReader(scenario).readFile(inputPopulationFile);
        writeDistanceDistribution(outputDistanceDistributionFile, scenario.getPopulation());
    }
}
