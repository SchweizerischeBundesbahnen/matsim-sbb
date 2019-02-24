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
                Activity firstAct = activities.get(0);
                Activity lastAct = activities.get( activities.size() - 1 );

                for(Activity act: activities)   {
                    if(act == firstAct) continue;
                    if(act == lastAct)  {
                        processOvernightAct(firstAct, lastAct);
                        break;
                    }

                    double endTime = act.getEndTime();
                    if( Time.isUndefinedTime(endTime) ) endTime = 36.0 * 3600;

                    double startTime = act.getStartTime();
                    if( Time.isUndefinedTime(startTime) )   startTime = 0.0;

                    double duration = endTime - startTime;

                    if( act.getType().equals(SBBActivities.home) ) {
                        if( startTime >= (16.0 * 3600) && startTime < (20 * 3600) )    {
                            long ii = roundSecondsToMinInterval(duration, 30);

                            double hours = startTime / 3600 * 2;
                            double yy = ((int) hours) / 2.0 + 0.5;
                            act.setType(SBBActivities.home + "_" + ii + "_" + yy);
                        }
                        else {
                            long ii = roundSecondsToMinInterval(duration, 30);
                            act.setType(SBBActivities.home + "_" + ii);
                        }
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
                        if( startTime >= (7.25 * 3600) && startTime <= (8.75 * 3600) )    {
                            long ii = roundSecondsToMinInterval(duration, 30);
                            act.setType(SBBActivities.education + "_" + ii + "_mp");
                        }
                        // process noon peak (between 12.5pm and 2pm)
                        else if( startTime >= (12.25 * 3600) && startTime <= (13.75 * 3600) )    {
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
            }
        }
    }

    private static void processOvernightAct(Activity firstAct, Activity lastAct) {
        double lastActStartTime = lastAct.getStartTime();
        double totDuration = ( firstAct.getEndTime() + 24 * 3600.0 ) - lastAct.getStartTime();
        if( totDuration <= 0 ) totDuration = 1;

        if( lastActStartTime >= (16.0 * 3600) && lastActStartTime < (20 * 3600) ) {
            long ii = roundSecondsToMinInterval(totDuration, 30);

            double hours = lastActStartTime / 3600 * 2;
            double yy = ((int) hours) / 2.0 + 0.5;

            firstAct.setType(SBBActivities.home + "_" + ii + "_" + yy);
            lastAct.setType(SBBActivities.home + "_" + ii + "_" + yy);
        }
        else    {
            long ii = roundSecondsToMinInterval(totDuration, 30);
            firstAct.setType(SBBActivities.home + "_" + ii);
            lastAct.setType(SBBActivities.home + "_" + ii);
        }
    }

    private static long roundSecondsToMinInterval(double seconds, int interval) {
        double minutes = seconds / 60;
        double toRound = minutes / interval;
        long rounded = (int) Math.ceil(toRound);
        return ( rounded * interval );
    }
}
