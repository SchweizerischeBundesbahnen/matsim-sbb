package ch.sbb.matsim.analysis.tripsandlegsanalysis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Random;
import org.junit.jupiter.api.Test;
import org.matsim.api.core.v01.Id;
import org.matsim.pt.transitSchedule.api.TransitLine;

public class PtLinkVolumeAnalyzerTest {

	private static Map<Id<TransitLine>, Long> lineVolumes(long... volumes) {
		Map<Id<TransitLine>, Long> map = new LinkedHashMap<>();
		for (int i = 0; i < volumes.length; i++) {
			map.put(Id.create("line" + i, TransitLine.class), volumes[i]);
		}
		return map;
	}

	private static int sum(Map<Id<TransitLine>, Integer> values) {
		return values.values().stream().mapToInt(Integer::intValue).sum();
	}

	@Test
	void perLineVolumesSumUpToTotal() {
		double scalefactor = 4.7619;
		Map<Id<TransitLine>, Long> perLine = lineVolumes(3, 7, 11, 1, 1);
		int total = (int) Math.round(perLine.values().stream().mapToLong(Long::longValue).sum() * scalefactor);

		Map<Id<TransitLine>, Integer> scaled = PtLinkVolumeAnalyzer.scaleAndRoundToTotal(perLine, scalefactor, total);

		assertEquals(total, sum(scaled));
		assertEquals(perLine.keySet(), scaled.keySet());
	}

	@Test
	void integerScaleFactorIsExact() {
		Map<Id<TransitLine>, Long> perLine = lineVolumes(3, 7, 11);
		Map<Id<TransitLine>, Integer> scaled = PtLinkVolumeAnalyzer.scaleAndRoundToTotal(perLine, 4.0, 84);

		assertEquals(12, scaled.get(Id.create("line0", TransitLine.class)));
		assertEquals(28, scaled.get(Id.create("line1", TransitLine.class)));
		assertEquals(44, scaled.get(Id.create("line2", TransitLine.class)));
	}

	@Test
	void roundedValuesStayCloseToTheExactValue() {
		Random random = new Random(4711);
		for (int run = 0; run < 200; run++) {
			double scalefactor = 1.0 + random.nextDouble() * 20.0;
			long[] volumes = new long[1 + random.nextInt(10)];
			for (int i = 0; i < volumes.length; i++) {
				volumes[i] = 1 + random.nextInt(50);
			}
			Map<Id<TransitLine>, Long> perLine = lineVolumes(volumes);
			long rawTotal = perLine.values().stream().mapToLong(Long::longValue).sum();
			int total = (int) Math.round(rawTotal * scalefactor);

			Map<Id<TransitLine>, Integer> scaled = PtLinkVolumeAnalyzer.scaleAndRoundToTotal(perLine, scalefactor, total);

			assertEquals(total, sum(scaled));
			for (Map.Entry<Id<TransitLine>, Long> e : perLine.entrySet()) {
				double exact = e.getValue() * scalefactor;
				assertTrue(Math.abs(scaled.get(e.getKey()) - exact) < 1.0,
						"value for " + e.getKey() + " deviates too much from " + exact);
			}
		}
	}
}
