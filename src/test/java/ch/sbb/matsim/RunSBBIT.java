package ch.sbb.matsim;

import org.junit.Test;


/**
 * An integration test to see if the Mobi Scenario is running.
 */
public class RunSBBIT {

    @Test
    public void main() {
       
			RunSBB.main(new String[]{"test/input/scenarios/mobi20test/testconfig.xml"});

    }

    @Test
    public void interModalIT() {
        
			RunSBB.main(new String[]{"test/input/scenarios/mobi20test/intermodal_testconfig.xml"});

    }
}