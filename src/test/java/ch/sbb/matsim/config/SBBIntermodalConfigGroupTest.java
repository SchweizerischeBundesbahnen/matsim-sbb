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
        mode1a.setConstant(1.2);
        mode1a.setMode("bicycle_feeder");
        mode1a.setDetourFactor(0.95); // the bicycle takes short cuts ;-)
        mode1a.setWaitingTime(3*60);
        mode1a.setMUTT(-0.6);
        intermodal1.addModeParameters(mode1a);
        SBBIntermodalConfigGroup.SBBIntermodalModeParameterSet mode1b = new SBBIntermodalConfigGroup.SBBIntermodalModeParameterSet();
        mode1b.setConstant(1.4);
        mode1b.setMode("scooter");
        mode1b.setDetourFactor(1.08);
        mode1b.setWaitingTime(2*60);
        mode1b.setMUTT(-0.9);
        intermodal1.addModeParameters(mode1b);

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
        Assert.assertEquals(2, modes2.size());

        SBBIntermodalConfigGroup.SBBIntermodalModeParameterSet mode2a = null;
        SBBIntermodalConfigGroup.SBBIntermodalModeParameterSet mode2b = null;

        for (SBBIntermodalConfigGroup.SBBIntermodalModeParameterSet mode2 : modes2) {
            if (mode2.getMode().equals(mode1a.getMode())) {
                mode2a = mode2;
            }
            if (mode2.getMode().equals(mode1b.getMode())) {
                mode2b = mode2;
            }
        }
        Assert.assertNotNull("bicycle_feeder mode is missing", mode2a);
        Assert.assertNotNull("scooter mode is missing", mode2b);

        Assert.assertEquals(mode1a.getConstant(), mode2a.getConstant(), 1e-7);
        Assert.assertEquals(mode1a.getDetourFactor(), mode2a.getDetourFactor(), 1e-7);
        Assert.assertEquals(mode1a.getWaitingTime(), mode2a.getWaitingTime(), 1e-7);
        Assert.assertEquals(mode1a.getMUTT(), mode2a.getMUTT(), 1e-7);

        Assert.assertEquals(mode1b.getConstant(), mode2b.getConstant(), 1e-7);
        Assert.assertEquals(mode1b.getDetourFactor(), mode2b.getDetourFactor(), 1e-7);
        Assert.assertEquals(mode1b.getWaitingTime(), mode2b.getWaitingTime(), 1e-7);
        Assert.assertEquals(mode1b.getMUTT(), mode2b.getMUTT(), 1e-7);
    }

}