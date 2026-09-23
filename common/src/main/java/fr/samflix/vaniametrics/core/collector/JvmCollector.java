package fr.samflix.vaniametrics.core.collector;

import java.lang.management.BufferPoolMXBean;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryPoolMXBean;
import java.lang.management.MemoryUsage;
import java.lang.management.ThreadMXBean;

import fr.samflix.vaniametrics.api.Collector;
import fr.samflix.vaniametrics.api.Counter;
import fr.samflix.vaniametrics.api.Gauge;
import fr.samflix.vaniametrics.api.MetricRegistry;

/**
 * The JVM: memory, garbage collection, threads.
 *
 * <p>Everything comes from {@code java.lang.management}: in every JDK, readable without
 * privileges, no measurable cost.
 *
 * <p>Reading the heap: "the heap is full" means nothing. A G1 heap always fills up to its
 * threshold before collecting; that is normal. What matters is the heap after collection (the old
 * gen after a full GC) and the allocation rate, which says how fast it fills. The latter comes
 * from spark, not from here.
 */
public final class JvmCollector implements Collector {

	private Gauge memory;
	private Gauge memoryPool;
	private Counter gcCount;
	private Counter gcTime;
	private Gauge threads;
	private Gauge deadlocked;
	private Gauge buffers;
	private Gauge classes;
	private Gauge uptime;

	@Override
	public String name() {
		return "jvm";
	}

	@Override
	public void declare(MetricRegistry r) {
		memory = r.gauge("jvm_memory_bytes",
				"JVM memory. area = heap|nonheap, state = used|committed|max|init.",
				"area", "state");
		memoryPool = r.gauge("jvm_memory_pool_bytes",
				"Memory per garbage collector pool (Eden, Survivor, Old, Metaspace).",
				"pool", "state");
		gcCount = r.counter("jvm_gc_collections_total",
				"Collections run, per garbage collector.", "gc");
		gcTime = r.counter("jvm_gc_seconds_total",
				"Cumulative collection time. This is collection time, NOT stop-the-world time; "
						+ "for actual pauses use JFR or GC logs.",
				"gc");
		threads = r.gauge("jvm_threads", "JVM threads. state = live|daemon|peak|started.", "state");
		deadlocked = r.gauge("jvm_threads_deadlocked",
				"Deadlocked threads. Any non-zero value is an incident.");
		buffers = r.gauge("jvm_buffer_pool_bytes",
				"Off-heap buffers. They count toward CONTAINER memory but not the heap, a classic "
						+ "cause of unexplained OOM kills.",
				"pool", "state");
		classes = r.gauge("jvm_classes_loaded", "Loaded classes.");
		uptime = r.gauge("jvm_uptime_seconds", "Time since the JVM started.");
	}

	@Override
	public void collect(MetricRegistry r) {
		var mem = ManagementFactory.getMemoryMXBean();
		setUsage(memory, "heap", mem.getHeapMemoryUsage());
		setUsage(memory, "nonheap", mem.getNonHeapMemoryUsage());

		for (MemoryPoolMXBean pool : ManagementFactory.getMemoryPoolMXBeans()) {
			setUsage(memoryPool, pool.getName(), pool.getUsage());
		}

		for (GarbageCollectorMXBean gc : ManagementFactory.getGarbageCollectorMXBeans()) {
			// JVM counters are cumulative since startup, like Prometheus counters: mirror the
			// value instead of adding deltas.
			gcCount.mirror(gc.getCollectionCount(), gc.getName());
			gcTime.mirror(gc.getCollectionTime() / 1000.0, gc.getName());
		}

		ThreadMXBean t = ManagementFactory.getThreadMXBean();
		threads.set(t.getThreadCount(), "live");
		threads.set(t.getDaemonThreadCount(), "daemon");
		threads.set(t.getPeakThreadCount(), "peak");
		threads.set(t.getTotalStartedThreadCount(), "started");
		long[] ids = t.findDeadlockedThreads();
		deadlocked.set(ids == null ? 0 : ids.length);

		for (BufferPoolMXBean pool : ManagementFactory.getPlatformMXBeans(BufferPoolMXBean.class)) {
			buffers.set(pool.getMemoryUsed(), pool.getName(), "used");
			buffers.set(pool.getTotalCapacity(), pool.getName(), "capacity");
			buffers.set(pool.getCount(), pool.getName(), "count");
		}

		classes.set(ManagementFactory.getClassLoadingMXBean().getLoadedClassCount());
		uptime.set(ManagementFactory.getRuntimeMXBean().getUptime() / 1000.0);
	}

	private void setUsage(Gauge g, String area, MemoryUsage u) {
		// A memory pool that is no longer valid returns null.
		if (u == null) {
			return;
		}
		g.set(u.getUsed(), area, "used");
		g.set(u.getCommitted(), area, "committed");
		g.set(u.getInit(), area, "init");
		// -1 means "no maximum". Publishing -1 would draw a nonsensical graph; NaN means
		// "unknown", which Prometheus and Grafana both skip.
		g.set(u.getMax() < 0 ? Double.NaN : u.getMax(), area, "max");
	}
}
