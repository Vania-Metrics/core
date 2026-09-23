package fr.samflix.vaniametrics.core.game;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class TpsMeterTest {

	private long now;
	private final TpsMeter meter = new TpsMeter(() -> now);

	private void tick(int times, long everyMillis) {
		for (int i = 0; i < times; i++) {
			now += everyMillis * 1_000_000L;
			meter.run();
		}
	}

	@Test
	void nothingBeforeTwoTicks() {
		assertNull(meter.tps());
		tick(1, 50);
		assertNull(meter.tps());
	}

	@Test
	void aServerKeepingUpReadsTwenty() {
		tick(20 * 60, 50);
		assertArrayEquals(new double[] {20, 20, 20}, meter.tps(), 0.05);
	}

	@Test
	void aServerAtHalfSpeedReadsTen() {
		tick(10 * 120, 100);
		assertArrayEquals(new double[] {10, 10, 10}, meter.tps(), 0.05);
	}

	@Test
	void aYoungServerIsJudgedOnTheTimeItHasBeenUp() {
		// 30 s at full speed must not read as 10 TPS on the one-minute window.
		tick(20 * 30, 50);
		assertEquals(20, meter.tps()[0], 0.05);
	}

	@Test
	void theWindowsForgetOldTicks() {
		tick(10 * 900, 100);
		tick(20 * 60, 50);
		double[] tps = meter.tps();
		assertEquals(20, tps[0], 0.05);
		assertEquals(12, tps[1], 0.1);
	}

	@Test
	void theRingBufferWraps() {
		tick(20 * 900 + 500, 50);
		assertEquals(20 * 900 + 500, meter.ticks());
		assertArrayEquals(new double[] {20, 20, 20}, meter.tps(), 0.05);
	}
}
