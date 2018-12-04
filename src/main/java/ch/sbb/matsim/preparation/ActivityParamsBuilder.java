package ch.sbb.matsim.preparation;

import ch.sbb.matsim.RunSBB;
import ch.sbb.matsim.config.variables.SBBActivities;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigWriter;
import org.matsim.core.config.groups.PlanCalcScoreConfigGroup.ActivityParams;

public class ActivityParamsBuilder {

    public static void main(final String[] args) {
        final String configIn = args[0];
        final String configOut = args[1];

        final Config config = RunSBB.buildConfig(configIn);

        buildActivityParams(config);

        new ConfigWriter(config).write(configOut);
    }

    public static void buildActivityParams(Config config)   {
        for( String stageActivityType: SBBActivities.stageActivityTypeList )    {
            final ActivityParams params = new ActivityParams( stageActivityType ) ;
            params.setTypicalDuration( 120.0 );
            params.setScoringThisActivityAtAll( false );
            config.planCalcScore().addActivityParams( params );
        }

        for ( long ii = 30 ; ii <= 1440 ; ii += 30 ) {
            final ActivityParams params = new ActivityParams( SBBActivities.home + "_" + ii ) ;
            params.setTypicalDuration( ii );
            params.setScoringThisActivityAtAll( true );
            config.planCalcScore().addActivityParams( params );
        }

        // work
        for ( long ii = 30 ; ii <= 720 ; ii += 30 ) {
            final ActivityParams params = new ActivityParams( SBBActivities.work + "_" + ii ) ;
            params.setTypicalDuration( ii );
            params.setOpeningTime( 4 * 3600.0 );
            params.setClosingTime( 23 * 3600.0 );
            params.setScoringThisActivityAtAll( true );
            config.planCalcScore().addActivityParams( params );
        }

        for ( long ii = 30 ; ii <= 720 ; ii += 30 ) {
            // morning peak is assumed to be between 6am and 9am
            final ActivityParams params = new ActivityParams( SBBActivities.work + "_" + ii + "_mp" ) ;
            params.setTypicalDuration( ii );
            params.setOpeningTime( 5.5 * 3600.0 );
            params.setLatestStartTime( 9.5 * 3600.0 );
            params.setClosingTime( 23 * 3600.0 );
            params.setScoringThisActivityAtAll( true );
            config.planCalcScore().addActivityParams( params );
        }

        for ( long ii = 30 ; ii <= 720 ; ii += 30 ) {
            // noon peak is assumed to be between 12pm and 2pm
            final ActivityParams params = new ActivityParams( SBBActivities.work + "_" + ii + "_np" ) ;
            params.setTypicalDuration( ii );
            params.setOpeningTime( 11.5 * 3600.0 );
            params.setLatestStartTime( 14.5 * 3600.0 );
            params.setClosingTime( 23 * 3600.0 );
            params.setScoringThisActivityAtAll( true );
            config.planCalcScore().addActivityParams( params );
        }

        // education
        for ( long ii = 30 ; ii <= 720 ; ii += 30 ) {
            final ActivityParams params = new ActivityParams( SBBActivities.education + "_" + ii ) ;
            params.setTypicalDuration( ii );
            params.setOpeningTime( 6 * 3600.0 );
            params.setClosingTime( 21 * 3600.0 );
            params.setScoringThisActivityAtAll( true );
            config.planCalcScore().addActivityParams( params );
        }

        for ( long ii = 30 ; ii <= 720 ; ii += 30 ) {
            // morning peak is assumed to be between 7am and 9am
            final ActivityParams params = new ActivityParams( SBBActivities.education + "_" + ii + "_mp" ) ;
            params.setTypicalDuration( ii );
            params.setOpeningTime( 6.5 * 3600.0 );
            params.setLatestStartTime( 9.5 * 3600.0 );
            params.setClosingTime( 21 * 3600.0 );
            params.setScoringThisActivityAtAll( true );
            config.planCalcScore().addActivityParams( params );
        }

        for ( long ii = 30 ; ii <= 720 ; ii += 30 ) {
            // noon peak is assumed to be between 12.5pm and 2pm
            final ActivityParams params = new ActivityParams( SBBActivities.education + "_" + ii + "_np" ) ;
            params.setTypicalDuration( ii );
            params.setOpeningTime( 12 * 3600.0 );
            params.setLatestStartTime( 14.5 * 3600.0 );
            params.setClosingTime( 21 * 3600.0 );
            params.setScoringThisActivityAtAll( true );
            config.planCalcScore().addActivityParams( params );
        }

        // business
        for ( long ii = 30 ; ii <= 540 ; ii += 30 ) {
            final ActivityParams params = new ActivityParams( SBBActivities.business + "_" + ii ) ;
            params.setTypicalDuration( ii );
            params.setOpeningTime( 4 * 3600.0 );
            params.setClosingTime( 23 * 3600.0 );
            params.setScoringThisActivityAtAll( true );
            config.planCalcScore().addActivityParams( params );
        }

        // leisure
        for ( long ii = 15 ; ii <= 480 ; ii += 15 ) {
            final ActivityParams params = new ActivityParams( SBBActivities.leisure + "_" + ii ) ;
            params.setTypicalDuration( ii );
            params.setOpeningTime( 4 * 3600.0 );
            params.setClosingTime( 24 * 3600.0 );
            params.setScoringThisActivityAtAll( true );
            config.planCalcScore().addActivityParams( params );
        }

        // shopping
        for ( long ii = 15 ; ii <= 330 ; ii += 15 ) {
            final ActivityParams params = new ActivityParams( SBBActivities.shopping + "_" + ii ) ;
            params.setTypicalDuration( ii );
            params.setOpeningTime( 5 * 3600.0 );
            params.setClosingTime( 22 * 3600.0 );
            params.setScoringThisActivityAtAll( true );
            config.planCalcScore().addActivityParams( params );
        }

        // accompany
        for ( long ii = 10 ; ii <= 120 ; ii += 10 ) {
            final ActivityParams params = new ActivityParams( SBBActivities.accompany + "_" + ii ) ;
            params.setTypicalDuration( ii );
            params.setScoringThisActivityAtAll( true );
            config.planCalcScore().addActivityParams( params );
        }

        // other
        for ( long ii = 30 ; ii <= 600 ; ii += 30 ) {
            final ActivityParams params = new ActivityParams( SBBActivities.other + "_" + ii ) ;
            params.setTypicalDuration( ii );
            params.setScoringThisActivityAtAll( true );
            config.planCalcScore().addActivityParams( params );
        }
    }
}
