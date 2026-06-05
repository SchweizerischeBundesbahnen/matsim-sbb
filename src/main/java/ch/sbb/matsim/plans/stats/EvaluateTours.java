package ch.sbb.matsim.plans.stats;

import ch.sbb.matsim.plans.constants.SynpopConstants;
import ch.sbb.matsim.plans.models.utils.AttributeUtils;
import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;
import org.matsim.api.core.v01.Scenario;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.population.io.StreamingPopulationReader;
import org.matsim.core.scenario.ScenarioUtils;

public class EvaluateTours {
    static void main() {
        DescriptiveStatistics workTours =  new DescriptiveStatistics();
        DescriptiveStatistics eduTours =  new DescriptiveStatistics();
        DescriptiveStatistics otherTours =  new DescriptiveStatistics();
        DescriptiveStatistics businessTours =  new DescriptiveStatistics();
        StreamingPopulationReader streamingPopulationReader = new StreamingPopulationReader(ScenarioUtils.createScenario(ConfigUtils.createConfig()));
        streamingPopulationReader.addAlgorithm(person -> {
            workTours.addValue(AttributeUtils.getAsInteger(person,SynpopConstants.NO_WORK_TOURS,0));
            eduTours.addValue(AttributeUtils.getAsInteger(person,SynpopConstants.NO_EDUCATION_TOURS,0));
            otherTours.addValue(AttributeUtils.getAsInteger(person,SynpopConstants.NO_OTHER_TOURS,0));
            businessTours.addValue(AttributeUtils.getAsInteger(person,SynpopConstants.NO_BUSINESS_TOURS,0));
        });

        streamingPopulationReader.readFile("/Volumes/mobi/40_Projekte/20250408_mobi_plans_6.0/test_input_synpop/full_run_output/test.plans.xml.gz");
        System.out.println("Work tours: " + workTours.getMean());
        System.out.println("Edu tours: " + eduTours.getMean());
        System.out.println("Other tours: " + otherTours.getMean());
        System.out.println("Business tours: " + businessTours.getMean());


    }
}
