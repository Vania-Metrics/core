package fr.samflix.vaniametrics.core.game;

import java.util.function.LongSupplier;

/**
 * TPS over 1, 5 and 15 minutes, for loaders that do not compute it (CraftBukkit, Spigot, Folia's
 * global region, Sponge): the loader runs it every tick, and TPS over a window is the number of
 * ticks in it.
 *
 * <p>This measures ticks per second only. The interval between two ticks is never below 50 ms, so
 * it says nothing about tick duration (MSPT), and no duration histogram is derived from it.
 *
 * <p>It may be read from another thread than the one ticking it (Folia), hence the
 * synchronization; uncontended, it costs nothing.
 */
public final class TpsMeter implements Runnable {

	private static final int TICKS_PER_SECOND = 20;
	private static final int[] WINDOWS_SECONDS = {60, 300, 900};
	private static final int CAPACITY = WINDOWS_SECONDS[WINDOWS_SECONDS.length - 1] * TICKS_PER_SECOND + 1;

	private final LongSupplier clock;
	private final long[] stamps = new long[CAPACITY];
	private int count;
	private int next;
	private int ticks;

	public TpsMeter() {
		this(System::nanoTime);
	}

	/** @param clock nanoseconds, like {@link System#nanoTime()}; replaceable for tests */
	TpsMeter(LongSupplier clock) {
		this.clock = clock;
	}

	@Override
	public synchronized void run() {
		stamps[next] = clock.getAsLong();
		next = (next + 1) % CAPACITY;
		if (count < CAPACITY) {
			count++;
		}
		ticks++;
	}

	public synchronized int ticks() {
		return ticks;
	}

	/** TPS over 1, 5 and 15 minutes, or {@code null} before the first two ticks. */
	public synchronized double[] tps() {
		if (count < 2) {
			return null;
		}
		long now = stamps[(next - 1 + CAPACITY) % CAPACITY];
		double[] result = new double[WINDOWS_SECONDS.length];
		for (int w = 0; w < WINDOWS_SECONDS.length; w++) {
			long windowNanos = WINDOWS_SECONDS[w] * 1_000_000_000L;
			int inWindow = 0;
			long oldest = now;
			for (int i = 1; i < count; i++) {
				long t = stamps[(next - 1 - i + 2 * CAPACITY) % CAPACITY];
				if (now - t > windowNanos) {
					break;
				}
				inWindow++;
				oldest = t;
			}
			// Over the span actually covered, so a server up for 30 s does not read as 10 TPS on
			// the 1-minute window.
			double span = (now - oldest) / 1e9;
			result[w] = span > 0 ? Math.min(TICKS_PER_SECOND, inWindow / span) : TICKS_PER_SECOND;
		}
		return result;
	}
}
