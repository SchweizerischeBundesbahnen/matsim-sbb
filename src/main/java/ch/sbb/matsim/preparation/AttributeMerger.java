/*
 * Copyright (C) Schweizerische Bundesbahnen SBB, 2017.
 */

package ch.sbb.matsim.preparation;

import org.apache.log4j.Logger;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.population.Person;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.population.PersonUtils;
import org.matsim.core.population.PopulationReaderMatsimV5;
import org.matsim.core.population.PopulationWriter;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.utils.objectattributes.ObjectAttributes;
import org.matsim.utils.objectattributes.ObjectAttributesXmlReader;
import org.matsim.utils.objectattributes.ObjectAttributesXmlWriter;

import java.io.File;

public class AttributeMerger {
    public static void main(final String[] args) {
        Logger log = Logger.getLogger(Cutter.class);
        final Config config = ConfigUtils.createConfig();
        final String planFile = args[0];
        final String attributeFileA = args[1];
        final String attributeFileB = args[2];
        final String attributeFile = args[3];
        final String attributesStr = args[4];
        final String populationFile = args[5];
        config.plans().setInputFile(planFile);

        Scenario scenario = ScenarioUtils.createScenario(config);

        new PopulationReaderMatsimV5(scenario).readFile(config.plans().getInputFile());

        final ObjectAttributes personAttributesA = new ObjectAttributes();
        final ObjectAttributes personAttributesB = new ObjectAttributes();

        new ObjectAttributesXmlReader(personAttributesA).parse(attributeFileA);
        new ObjectAttributesXmlReader(personAttributesB).parse(attributeFileB);


        String[] attributes = attributesStr.split(",");

       for(Person person: scenario.getPopulation().getPersons().values()){

           for(String attribute: attributes) {
               Object A = personAttributesA.getAttribute(person.getId().toString(), attribute);
               Object B = personAttributesB.getAttribute(person.getId().toString(), attribute);
               Object C = "";
               if(A != null && B == null){
                   C = A;
               }
               else if(A == null && B!= null){
                   C = B;
               }
               else if(A != null && B!= null){
                   C = A.toString()+"_"+B.toString();
               }

               scenario.getPopulation().getPersonAttributes().putAttribute(person.getId().toString(), attribute, C);

               if(attribute.equals("availability: car")){
                   if(!"never".equals(C.toString())){
                       person.getCustomAttributes().put("carAvail", "always");
                       PersonUtils.setLicence(person, "yes");
                   }
                   else{
                       person.getCustomAttributes().put("carAvail", "never");
                       PersonUtils.setLicence(person, "no");
                   }
               }
               if(attribute.equals("age") && C != ""){
                   person.getCustomAttributes().put("age", Integer.parseInt(C.toString()));
               }
               if(attribute.equals("gender") || attribute.equals("sex")){
                   person.getCustomAttributes().put("gender", C);
                 }
           }

       }
        new ObjectAttributesXmlWriter(scenario.getPopulation().getPersonAttributes()).writeFile( attributeFile);
        new PopulationWriter(scenario.getPopulation()).write(populationFile);
    }
}
