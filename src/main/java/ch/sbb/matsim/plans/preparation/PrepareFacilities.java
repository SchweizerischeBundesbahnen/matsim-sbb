package ch.sbb.matsim.plans.preparation;

import org.matsim.api.core.v01.Scenario;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.facilities.*;
import org.matsim.utils.objectattributes.ObjectAttributes;
import org.matsim.utils.objectattributes.ObjectAttributesXmlReader;

import java.util.Arrays;

public class PrepareFacilities {

    private final String path;
    private final Scenario scenario;
    private final ActivityFacilitiesFactory factory;
    private final ObjectAttributes attributes;

    public static void main(String[] args)  {
        PrepareFacilities prepare = new PrepareFacilities();
        prepare.loadFacilities();
        prepare.handleFacilities();
        prepare.writeFacilities();
    }

    private PrepareFacilities(){
        this.path = "D:\\MOBi\\synpop\\data\\output\\2016\\for_mobi_plans\\v_02";
        this.scenario = ScenarioUtils.createScenario(ConfigUtils.createConfig());
        this.factory = this.scenario.getActivityFacilities().getFactory();
        this.attributes = this.scenario.getActivityFacilities().getFacilityAttributes();
    }

    private void loadFacilities()    {
        new FacilitiesReaderMatsimV1(this.scenario).readFile(this.path + "/facilities.xml.gz");
        new ObjectAttributesXmlReader(this.scenario.getActivityFacilities().getFacilityAttributes()).readFile(this.path + "/facility_attributes.xml.gz");
    }

    private void handleFacilities()  {
        for (ActivityFacility facility: this.scenario.getActivityFacilities().getFacilities().values()) {
            boolean isSchool = false;

            // check if it is a school
            // attribute: school_type = 1,2,3,4
            if(Arrays.asList("1", "2", "3", "4").contains(this.attributes.getAttribute(facility.getId().toString(), "school_type")))  {
                ActivityOption opt = this.factory.createActivityOption("education");
                facility.addActivityOption(opt);
                isSchool = true;
            }

            // check if it is shop
            // attribute: Type_1 = 3, 4, 5, 9, 10
            if(Arrays.asList("3", "4", "5", "9", "10").contains(this.attributes.getAttribute(facility.getId().toString(), "type_1")))  {
                ActivityOption opt = this.factory.createActivityOption("shop");
                facility.addActivityOption(opt);
            }

            // check if it is leisure
            // all schools + Type_1 = 3, 4, 5, 9, 10
            if(Arrays.asList("3", "4", "5", "9", "10").contains(this.attributes.getAttribute(facility.getId().toString(), "type_1")) ||
                    isSchool)  {
                ActivityOption opt = this.factory.createActivityOption("leisure");
                facility.addActivityOption(opt);
            }

            // check if it is work
            // nr of jobs > 0
            if(facility.getActivityOptions().containsKey("work"))   {
                double fte = Double.parseDouble(this.attributes.getAttribute(facility.getId().toString(), "fte").toString());
                if(fte <= 0)    {
                    facility.getActivityOptions().remove("work");
                }
                else    {
                    ActivityOption opt = this.factory.createActivityOption("business");
                    facility.addActivityOption(opt);
                }
            }

            ActivityOption opt = this.factory.createActivityOption("accompany");
            facility.addActivityOption(opt);
            opt = this.factory.createActivityOption("other");
            facility.addActivityOption(opt);
        }
    }

    private void writeFacilities()  {
        new FacilitiesWriter(this.scenario.getActivityFacilities()).write(this.path + "/facilities_adj.xml.gz");
    }
}
