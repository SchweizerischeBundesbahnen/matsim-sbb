package ch.sbb.matsim.preparation;

import ch.sbb.matsim.config.variables.SBBActivities;
import org.matsim.api.core.v01.population.Activity;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.Plan;
import org.matsim.api.core.v01.population.Population;
import org.matsim.core.router.TripStructureUtils;
import org.matsim.core.utils.misc.Time;

import java.util.List;

public class PrepareActivitiesInPlans {

    public static void overwriteActivitiesInPlans(Population population)   {
        for( Person p: population.getPersons().values() )   {
            for( Plan plan: p.getPlans() )  {
                List<Activity> activities = TripStructureUtils.getActivities(plan, SBBActivities.stageActivitiesTypes);
                double homeTime = 0.0;
                for(Activity act: activities)   {
                    double endTime = act.getEndTime();
                    if( endTime == Time.getUndefinedTime() )    {
                        endTime = 36.0;
                    }
                    double startTime = act.getStartTime();
                    if( startTime == Time.getUndefinedTime() )    {
                        startTime = 0.0;
                    }
                    double duration = endTime - startTime;
                    if( act.getType().equals(SBBActivities.home) )  {
                        homeTime += duration;
                    }
                    else if( act.getType().equals(SBBActivities.work) ) {
                        // process morning peak (between 6am and 9am)
                        if( startTime >= (6 * 3600) && startTime <= (9 * 3600) )    {
                            long ii = roundSecondsToMinInterval(duration, 15);
                            act.setType(SBBActivities.work + "_" + ii + "_mp");
                        }
                        // process noon peak (between 12pm and 2pm)
                        else if( startTime >= (12 * 3600) && startTime <= (14 * 3600) )    {
                            long ii = roundSecondsToMinInterval(duration, 15);
                            act.setType(SBBActivities.work + "_" + ii + "_np");
                        }
                        // process rest
                        else    {
                            long ii = roundSecondsToMinInterval(duration, 15);
                            act.setType(SBBActivities.work + "_" + ii);
                        }
                    }
                    else if( act.getType().equals(SBBActivities.education) ) {
                        // process morning peak (between 7am and 9am)
                        if( startTime >= (7 * 3600) && startTime <= (9 * 3600) )    {
                            long ii = roundSecondsToMinInterval(duration, 15);
                            act.setType(SBBActivities.education + "_" + ii + "_mp");
                        }
                        // process noon peak (between 12.5pm and 2pm)
                        else if( startTime >= (12.5 * 3600) && startTime <= (14 * 3600) )    {
                            long ii = roundSecondsToMinInterval(duration, 15);
                            act.setType(SBBActivities.education + "_" + ii + "_np");
                        }
                        // process rest
                        else    {
                            long ii = roundSecondsToMinInterval(duration, 15);
                            act.setType(SBBActivities.education + "_" + ii);
                        }
                    }
                    else if( act.getType().equals(SBBActivities.business) ) {
                        long ii = roundSecondsToMinInterval(duration, 15);
                        act.setType(SBBActivities.business + "_" + ii);
                    }
                    else if( act.getType().equals(SBBActivities.leisure) ) {
                        long ii = roundSecondsToMinInterval(duration, 15);
                        act.setType(SBBActivities.leisure + "_" + ii);
                    }
                    else if( act.getType().equals(SBBActivities.other) ) {
                        long ii = roundSecondsToMinInterval(duration, 15);
                        act.setType(SBBActivities.other + "_" + ii);
                    }
                    else if( act.getType().equals(SBBActivities.shopping) ) {
                        long ii = roundSecondsToMinInterval(duration, 10);
                        act.setType(SBBActivities.shopping + "_" + ii);
                    }
                    else if( act.getType().equals(SBBActivities.accompany) ) {
                        long ii = roundSecondsToMinInterval(duration, 10);
                        act.setType(SBBActivities.accompany + "_" + ii);
                    }
                }

                homeTime = homeTime - 12.0;
                if( homeTime < 0 )  {
                    homeTime = 0;
                }
                long ii = roundSecondsToMinInterval(homeTime, 15);
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
        return ( Math.round(toRound) * interval );
    }
}
