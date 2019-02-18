package ch.sbb.matsim.preparation;

import ch.sbb.matsim.config.variables.SBBActivities;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.population.Activity;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.Plan;
import org.matsim.api.core.v01.population.Population;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.population.io.PopulationReader;
import org.matsim.core.population.io.PopulationWriter;
import org.matsim.core.router.TripStructureUtils;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.core.utils.misc.Time;

import java.util.List;

public class PrepareActivitiesInPlans {

    public static void main(String[] args)  {
        String pathIn = args[0];
        String pathOut = args[1];

        Scenario scenario = ScenarioUtils.createScenario(ConfigUtils.createConfig());
        new PopulationReader(scenario).readFile(pathIn);

        overwriteActivitiesInPlans(scenario.getPopulation());

        new PopulationWriter(scenario.getPopulation()).write(pathOut);
    }

    public static void overwriteActivitiesInPlans(Population population)   {
        for( Person p: population.getPersons().values() )   {
            for( Plan plan: p.getPlans() )  {
                List<Activity> activities = TripStructureUtils.getActivities(plan, SBBActivities.stageActivitiesTypes);
                double homeTime = 0.0;
                for(Activity act: activities)   {
                    double endTime = act.getEndTime();
                    if( Time.isUndefinedTime(endTime) )    {
                        endTime = 36.0 * 3600;
                    }
                    double startTime = act.getStartTime();
                    if( Time.isUndefinedTime(startTime) )    {
                        startTime = 0.0;
                    }
                    double duration = endTime - startTime;

                    if( act.getType().equals(SBBActivities.home) )  {
                        homeTime += duration;
                    }
                    else if( act.getType().equals(SBBActivities.work) ) {
                        // process morning peak (between 6.5am and 8.5am)
                        if( startTime >= (6.5 * 3600) && startTime <= (8.5 * 3600) )    {
                            long ii = roundSecondsToMinInterval(duration, 30);
                            act.setType(SBBActivities.work + "_" + ii + "_mp");
                        }
                        // process noon peak (between 12pm and 2pm)
                        else if( startTime >= (12.25 * 3600) && startTime <= (13.75 * 3600) )    {
                            long ii = roundSecondsToMinInterval(duration, 30);
                            act.setType(SBBActivities.work + "_" + ii + "_np");
                        }
                        // process rest
                        else    {
                            long ii = roundSecondsToMinInterval(duration, 30);
                            act.setType(SBBActivities.work + "_" + ii);
                        }
                    }
                    else if( act.getType().equals(SBBActivities.education) ) {
                        // process morning peak (between 7am and 9am)
                        if( startTime >= (7.0 * 3600) && startTime <= (8.5 * 3600) )    {
                            long ii = roundSecondsToMinInterval(duration, 30);
                            act.setType(SBBActivities.education + "_" + ii + "_mp");
                        }
                        // process noon peak (between 12.5pm and 2pm)
                        else if( startTime >= (12.25 * 3600) && startTime <= (14 * 3600) )    {
                            long ii = roundSecondsToMinInterval(duration, 30);
                            act.setType(SBBActivities.education + "_" + ii + "_np");
                        }
                        // process rest
                        else    {
                            long ii = roundSecondsToMinInterval(duration, 30);
                            act.setType(SBBActivities.education + "_" + ii);
                        }
                    }
                    else if( act.getType().equals(SBBActivities.business) ) {
                        long ii = roundSecondsToMinInterval(duration, 30);
                        act.setType(SBBActivities.business + "_" + ii);
                    }
                    else if( act.getType().equals(SBBActivities.leisure) ) {
                        long ii = roundSecondsToMinInterval(duration, 15);
                        act.setType(SBBActivities.leisure + "_" + ii);
                    }
                    else if( act.getType().equals(SBBActivities.other) ) {
                        long ii = roundSecondsToMinInterval(duration, 30);
                        act.setType(SBBActivities.other + "_" + ii);
                    }
                    else if( act.getType().equals(SBBActivities.shopping) ) {
                        long ii = roundSecondsToMinInterval(duration, 15);
                        act.setType(SBBActivities.shopping + "_" + ii);
                    }
                    else if( act.getType().equals(SBBActivities.accompany) ) {
                        long ii = roundSecondsToMinInterval(duration, 10);
                        act.setType(SBBActivities.accompany + "_" + ii);
                    }
                }

                homeTime = homeTime - (12.0 * 3600);
                if( homeTime < 0 )  {
                    homeTime = 1;
                }
                long ii = roundSecondsToMinInterval(homeTime, 30);
                for(Activity act: activities)   {
                    if( act.getType().equals(SBBActivities.home) )  {
                        act.setType(SBBActivities.home + "_" + ii);
                    }
                }
            }
        }
    }

    private static long roundSecondsToMinInterval(double seconds, int interval) {
        double minutes = seconds / 60;
        double toRound = minutes / interval;
        long rounded = (int) Math.ceil(toRound);
        return ( rounded * interval );
    }
}
