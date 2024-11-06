package ch.sbb.matsim.projects.synpop.roadExogeneous;

import ch.sbb.matsim.config.variables.SBBModes;
import ch.sbb.matsim.config.variables.Variables;
import ch.sbb.matsim.projects.synpop.SingleTripAgentToCSVConverter;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.population.*;
import org.matsim.contrib.common.util.WeightedRandomSelection;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.gbl.MatsimRandom;
import org.matsim.core.population.PopulationUtils;
import org.matsim.core.scenario.ScenarioUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

//The NPVM does not contain enough transit trips compared to the Alpen- und grenzquerende Verkehrsstatistik, and also compared to traffic counts
//this fixes this, using numbers obtained from comparing these values
public class GenerateAdditionalTransitTrips {
    public static void main(String[] args) throws IOException {
        String timeDistributionFile = "\\\\wsbbrz0283\\mobi\\40_Projekte\\20240207_MOBi_5.0\\plans_exogeneous\\MIV international\\input\\2016_Ganglinie.csv";
        double globalFactor = 1.30;
        Random random = MatsimRandom.getRandom();
        List<MissingDemand> missingDemandList = new ArrayList<>();
        var timeDistribution = SingleTripAgentCreator.readTimeDistribution(timeDistributionFile, random);
        fillMissingDemandList(random, missingDemandList);
        Scenario scenario = ScenarioUtils.createScenario(ConfigUtils.createConfig());
        generatePopulation(globalFactor, missingDemandList, scenario.getPopulation(), timeDistribution, random);
        //new PopulationWriter(scenario.getPopulation()).write("\\\\wsbbrz0283\\mobi\\40_Projekte\\20240207_MOBi_5.0\\plans_exogeneous\\MIV international\\output\\cb_road_additional.xml.gz");
        SingleTripAgentToCSVConverter.writeSingleTripsAsCSV("\\\\wsbbrz0283\\mobi\\40_Projekte\\20240816_Prognose_LFP25\\2050\\plans_exogenous\\cb_road_additional.csv", scenario.getPopulation());
    }

    private static void fillMissingDemandList(Random random, List<MissingDemand> missingDemandList) {
        {
            String identifier = "de_de";
            WeightedRandomSelection fromCoords = new WeightedRandomSelection(random);
            fromCoords.add(new Coord(2730614.1326, 1280889.7858), 1.0);
            WeightedRandomSelection toCoords = new WeightedRandomSelection(random);
            toCoords.add(new Coord(2663770.52409449, 1271933.1622523), 1.0);
            missingDemandList.add(new MissingDemand(identifier, toCoords, fromCoords, 190));
            missingDemandList.add(new MissingDemand(identifier, fromCoords, toCoords, 190));
        }
        {
            String identifier = "fr_fr";
            WeightedRandomSelection fromCoords = new WeightedRandomSelection(random);
            fromCoords.add(new Coord(2497253.2486, 1088660.9058), 2.0); //annecy
            fromCoords.add(new Coord(2529828.9912, 1136454.3519), 1.0); //evian
            fromCoords.add(new Coord(2508168.5617, 1116002.9995), 1.0); //annemasse
            WeightedRandomSelection toCoords = new WeightedRandomSelection(random);
            toCoords.add(new Coord(2593426.9523, 1288806.8801), 2.0); //mulhouse
            toCoords.add(new Coord(2493681.1193, 1132137.1996), 4.0); //bex
            toCoords.add(new Coord(2492792.3769, 1233629.9982), 1.0); //besancon
            missingDemandList.add(new MissingDemand(identifier, toCoords, fromCoords, 6424));
            missingDemandList.add(new MissingDemand(identifier, fromCoords, toCoords, 6424));
        }
        {
            String identifier = "it_de";
            WeightedRandomSelection fromCoords = new WeightedRandomSelection(random);
            fromCoords.add(new Coord(2746840.3232, 1024974.8824), 5.0); //Milano
            fromCoords.add(new Coord(2623498.5578, 999546.4805), 2.0); //Torino
            fromCoords.add(new Coord(2804800.57, 1060038.5102), 1.0); //Brescia
            fromCoords.add(new Coord(2824773.0325, 1151001.9561), 0.5); //Livigno


            WeightedRandomSelection toCoords = new WeightedRandomSelection(random);
            toCoords.add(new Coord(2623998.9751, 1333937.8866), 3.0); //freiburg
            toCoords.add(new Coord(2777324.6719, 1286538.4086), 2.0); //allgau-lindau
            toCoords.add(new Coord(2685980.1125, 1327403.8671), 2.0); //Rottweil
            missingDemandList.add(new MissingDemand(identifier, fromCoords, toCoords, 7855)); //inkl belgien und nl
            missingDemandList.add(new MissingDemand("de_it", toCoords, fromCoords, 6214)); //inkl belgien und nl
        }
        {
            String identifier = "it_fr";
            WeightedRandomSelection fromCoords = new WeightedRandomSelection(random);
            fromCoords.add(new Coord(2746840.3232, 1024974.8824), 5.0); //Milano
            fromCoords.add(new Coord(2623498.5578, 999546.4805), 2.0); //Torino
            fromCoords.add(new Coord(2804800.57, 1060038.5102), 1.0); //Brescia

            WeightedRandomSelection toCoords = new WeightedRandomSelection(random);
            toCoords.add(new Coord(2593426.9523, 1288806.8801), 4.0); //mulhouse
            toCoords.add(new Coord(2492792.3769, 1233629.9982), 1.0); //besancon
            missingDemandList.add(new MissingDemand(identifier, fromCoords, toCoords, 1050));
            missingDemandList.add(new MissingDemand("fr_it", toCoords, fromCoords, 1050));
        }
        {
            String identifier = "it_it";
            WeightedRandomSelection fromCoords = new WeightedRandomSelection(random);
            fromCoords.add(new Coord(2746840.3232, 1024974.8824), 5.0); //Milano
            fromCoords.add(new Coord(2623498.5578, 999546.4805), 2.0); //Torino
            fromCoords.add(new Coord(2804800.57, 1060038.5102), 1.0); //Brescia

            WeightedRandomSelection toCoords = new WeightedRandomSelection(random);
            toCoords.add(new Coord(2593426.9523, 1288806.8801), 1.0); //Chiavenna
            toCoords.add(new Coord(2824773.0325, 1151001.9561), 1.0); //Livigno
            missingDemandList.add(new MissingDemand(identifier, fromCoords, toCoords, 1450));
            missingDemandList.add(new MissingDemand(identifier, toCoords, fromCoords, 1450));
        }
    }

    private static void generatePopulation(double globalFactor, List<MissingDemand> missingDemandList, Population population, WeightedRandomSelection<Integer> timeDistribution, Random random) {
        int i = 0;
        PopulationFactory factory = population.getFactory();
        for (var demand : missingDemandList) {
            for (int j = 0; j < demand.demand * globalFactor; j++) {
                int departureTime = timeDistribution.select() * 3600 + random.nextInt(3600);
                var personId = Id.createPersonId(Variables.CB_ROAD + "_" + demand.identifier + "_" + i);
                Person person = factory.createPerson(personId);
                population.addPerson(person);
                Plan plan = factory.createPlan();
                person.addPlan(plan);
                Activity start = factory.createActivityFromCoord("cbHome", demand.fromCoords.select());
                start.setEndTime(departureTime);
                plan.addActivity(start);
                plan.addLeg(factory.createLeg(SBBModes.CAR));
                plan.addActivity(factory.createActivityFromCoord("cbHome", demand.toCoords().select()));
                PopulationUtils.putSubpopulation(person, Variables.CB_ROAD);
                i++;
            }
        }
    }


    record MissingDemand(String identifier, WeightedRandomSelection<Coord> fromCoords,
                         WeightedRandomSelection<Coord> toCoords, int demand) {
    }
}
