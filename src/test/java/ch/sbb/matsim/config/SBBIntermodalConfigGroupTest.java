package ch.sbb.matsim.config;

import org.junit.Assert;
import org.junit.Test;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigReader;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.config.ConfigWriter;

import java.io.*;
import java.util.List;

/**
 * @author mrieser
 */
public class SBBIntermodalConfigGroupTest {

    @Test
    public void testIO() throws IOException {
        SBBIntermodalConfigGroup intermodal1 = new SBBIntermodalConfigGroup();
        SBBIntermodalConfigGroup.SBBIntermodalModeParameterSet mode1a = new SBBIntermodalConfigGroup.SBBIntermodalModeParameterSet();
        mode1a.setMode("bicycle_feeder");
        mode1a.setDetourFactor(0.95); // the bicycle takes short cuts ;-)
        mode1a.setWaitingTime(3*60);
        mode1a.setSimulatedOnNetwork(false);
        mode1a.setRoutedOnNetwork(false);
        intermodal1.addModeParameters(mode1a);
        SBBIntermodalConfigGroup.SBBIntermodalModeParameterSet mode1b = new SBBIntermodalConfigGroup.SBBIntermodalModeParameterSet();
        mode1b.setMode("scooter");
        mode1b.setDetourFactor(1.08);
        mode1b.setWaitingTime(2*60);
        mode1b.setSimulatedOnNetwork(true);
        mode1b.setRoutedOnNetwork(true);
        intermodal1.addModeParameters(mode1b);
        SBBIntermodalConfigGroup.SBBIntermodalModeParameterSet mode1c = new SBBIntermodalConfigGroup.SBBIntermodalModeParameterSet();
        mode1c.setMode("car_feeder");
        mode1c.setDetourFactor(1.08);
        mode1c.setSimulatedOnNetwork(true);
        mode1c.setRoutedOnNetwork(true);
        mode1c.setAccessTimeZoneId("ACCCAR");
        mode1c.setEgressTimeZoneId("ACCCAR");
        mode1c.setUseMinimalTransferTimes(true);
        intermodal1.addModeParameters(mode1c);

        Config config1 = ConfigUtils.createConfig(intermodal1);
        byte[] data;
        try (ByteArrayOutputStream byteOut = new ByteArrayOutputStream();
             Writer writer = new OutputStreamWriter(byteOut)) {

             new ConfigWriter(config1).writeStream(writer);
             writer.flush();
             data = byteOut.toByteArray();
        }

        SBBIntermodalConfigGroup intermodal2 = new SBBIntermodalConfigGroup();
        Config config2 = ConfigUtils.createConfig(intermodal2);
        try (ByteArrayInputStream byteIn = new ByteArrayInputStream(data)) {
            new ConfigReader(config2).parse(byteIn);
        }

        List<SBBIntermodalConfigGroup.SBBIntermodalModeParameterSet> modes2 = intermodal2.getModeParameterSets();
        Assert.assertEquals(3, modes2.size());

        SBBIntermodalConfigGroup.SBBIntermodalModeParameterSet mode2a = null;
        SBBIntermodalConfigGroup.SBBIntermodalModeParameterSet mode2b = null;
        SBBIntermodalConfigGroup.SBBIntermodalModeParameterSet mode2c = null;

        for (SBBIntermodalConfigGroup.SBBIntermodalModeParameterSet mode2 : modes2) {
            if (mode2.getMode().equals(mode1a.getMode())) {
                mode2a = mode2;
            }
            if (mode2.getMode().equals(mode1b.getMode())) {
                mode2b = mode2;
            }
            if (mode2.getMode().equals(mode1c.getMode())) {
                mode2c = mode2;
            }
        }
        Assert.assertNotNull("bicycle_feeder mode is missing", mode2a);
        Assert.assertNotNull("scooter mode is missing", mode2b);
        Assert.assertNotNull("car_feeder mode is missing", mode2c);

        Assert.assertEquals(mode1a.getDetourFactor(), mode2a.getDetourFactor(), 1e-7);
        Assert.assertEquals(mode1a.getWaitingTime(), mode2a.getWaitingTime(), 1e-7);
        Assert.assertEquals(mode1a.isSimulatedOnNetwork(), mode2a.isSimulatedOnNetwork());
        Assert.assertEquals(mode1a.isRoutedOnNetwork(), mode2a.isRoutedOnNetwork());

        Assert.assertEquals(mode1b.getDetourFactor(), mode2b.getDetourFactor(), 1e-7);
        Assert.assertEquals(mode1b.getWaitingTime(), mode2b.getWaitingTime(), 1e-7);
        Assert.assertEquals(mode1b.isSimulatedOnNetwork(), mode2b.isSimulatedOnNetwork());
        Assert.assertEquals(mode1b.isRoutedOnNetwork(), mode2b.isRoutedOnNetwork());

        Assert.assertEquals(mode1c.getDetourFactor(), mode2c.getDetourFactor(), 1e-7);
        Assert.assertEquals(mode1c.getWaitingTime(), mode2c.getWaitingTime(), 1e-7);
        Assert.assertEquals(mode1c.isSimulatedOnNetwork(), mode2c.isSimulatedOnNetwork());
        Assert.assertEquals(mode1c.isRoutedOnNetwork(), mode2c.isRoutedOnNetwork());
        Assert.assertEquals(mode1c.getAccessTimeZoneId(), mode2c.getAccessTimeZoneId());
        Assert.assertEquals(mode1c.getEgressTimeZoneId(), mode2c.getEgressTimeZoneId());
        Assert.assertEquals(mode1c.doUseMinimalTransferTimes(), mode2c.doUseMinimalTransferTimes());
    }

}