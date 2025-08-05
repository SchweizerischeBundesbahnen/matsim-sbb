package ch.sbb.matsim.config;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
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
		SBBIntermodalConfiggroup intermodal1 = new SBBIntermodalConfiggroup();
		SBBIntermodalModeParameterSet mode1a = new SBBIntermodalModeParameterSet();
		mode1a.setMode("bicycle_feeder");
		mode1a.setSimulatedOnNetwork(false);
		mode1a.setRoutedOnNetwork(false);
		intermodal1.addModeParameters(mode1a);
		SBBIntermodalModeParameterSet mode1b = new SBBIntermodalModeParameterSet();
		mode1b.setMode("scooter");
		mode1b.setSimulatedOnNetwork(true);
		mode1b.setRoutedOnNetwork(true);
		intermodal1.addModeParameters(mode1b);
		SBBIntermodalModeParameterSet mode1c = new SBBIntermodalModeParameterSet();
		mode1c.setMode("car_feeder");
		mode1c.setSimulatedOnNetwork(true);
		mode1c.setRoutedOnNetwork(true);
		mode1c.setAccessTimeZoneId("at_car");
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

		SBBIntermodalConfiggroup intermodal2 = new SBBIntermodalConfiggroup();
		Config config2 = ConfigUtils.createConfig(intermodal2);
		try (ByteArrayInputStream byteIn = new ByteArrayInputStream(data)) {
			new ConfigReader(config2).parse(byteIn);
		}

		List<SBBIntermodalModeParameterSet> modes2 = intermodal2.getModeParameterSets();
		Assertions.assertEquals(3, modes2.size());

		SBBIntermodalModeParameterSet mode2a = null;
		SBBIntermodalModeParameterSet mode2b = null;
		SBBIntermodalModeParameterSet mode2c = null;

		for (SBBIntermodalModeParameterSet mode2 : modes2) {
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
		Assertions.assertNotNull(mode2a);
		Assertions.assertNotNull(mode2b);
		Assertions.assertNotNull(mode2c);

		Assertions.assertEquals(mode1a.isSimulatedOnNetwork(), mode2a.isSimulatedOnNetwork());
		Assertions.assertEquals(mode1a.isRoutedOnNetwork(), mode2a.isRoutedOnNetwork());

		Assertions.assertEquals(mode1b.isSimulatedOnNetwork(), mode2b.isSimulatedOnNetwork());
		Assertions.assertEquals(mode1b.isRoutedOnNetwork(), mode2b.isRoutedOnNetwork());

		Assertions.assertEquals(mode1c.isSimulatedOnNetwork(), mode2c.isSimulatedOnNetwork());
		Assertions.assertEquals(mode1c.isRoutedOnNetwork(), mode2c.isRoutedOnNetwork());
		Assertions.assertEquals(mode1c.doUseMinimalTransferTimes(), mode2c.doUseMinimalTransferTimes());
	}

}