package fr.samflix.vaniametrics.core.collector;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import fr.samflix.vaniametrics.api.Collector;
import fr.samflix.vaniametrics.api.Counter;
import fr.samflix.vaniametrics.api.Gauge;
import fr.samflix.vaniametrics.api.MetricRegistry;
import fr.samflix.vaniametrics.api.Platform;

/**
 * The container: CPU, throttling, memory, I/O.
 *
 * <p>Nothing else provides this, and it explains the hardest outages to diagnose. In a container,
 * TPS can collapse while spark sees nothing wrong in the profile: the cause is one layer down, the
 * kernel froze the process because it exceeded its CPU quota. {@code nr_throttled} shows it, and
 * nothing in the Minecraft ecosystem reads it.
 *
 * <p>Everything comes from text files under {@code /sys/fs/cgroup}: no dependency, no privilege,
 * no native call. The collector turns itself off when the files are missing, which is the case
 * outside a container and on cgroup v1.
 *
 * <p>Compare {@code memory.current} to {@code memory.max}, not the JVM heap. A JVM capped at 6 GiB
 * in an 8 GiB container can be OOM-killed with a half-empty heap: metaspace, thread stacks, direct
 * buffers and mapped files live off-heap and still count. {@code memory.events} is the only metric
 * that tells an OOM kill happened.
 */
public final class CgroupCollector implements Collector {

	private static final Path ROOT = Path.of("/sys/fs/cgroup");

	private final Platform platform;
	private final boolean available;

	private Counter cpuUsage;
	private Counter cpuUser;
	private Counter cpuSystem;
	private Counter throttledPeriods;
	private Counter throttledTime;
	private Counter periods;
	private Gauge quota;
	private Gauge memory;
	private Gauge memoryLimit;
	private Gauge memoryBreakdown;
	private Counter pageFaults;
	private Counter oom;
	private Counter ioBytes;
	private Counter ioOperations;

	public CgroupCollector(Platform platform) {
		this.platform = platform;
		this.available = Files.isReadable(ROOT.resolve("cpu.stat"));
	}

	@Override
	public String name() {
		return "cgroup";
	}

	@Override
	public void declare(MetricRegistry r) {
		if (!available) {
			platform.info("collector cgroup: /sys/fs/cgroup/cpu.stat not readable (not in a "
					+ "container, or cgroup v1); nothing will be published");
			return;
		}
		cpuUsage = r.counter("host_cpu_usage_seconds_total", "CPU time used by the container.");
		cpuUser = r.counter("host_cpu_user_seconds_total", "CPU time in user space.");
		cpuSystem = r.counter("host_cpu_system_seconds_total", "CPU time in kernel space.");
		throttledPeriods = r.counter("host_cpu_throttled_periods_total",
				"Periods during which the kernel FROZE the container for lack of quota. "
						+ "A rising value explains low TPS that spark does not see.");
		throttledTime = r.counter("host_cpu_throttled_seconds_total",
				"Total time spent frozen by the kernel.");
		periods = r.counter("host_cpu_periods_total",
				"Elapsed scheduling periods. The ratio with throttled_periods gives the share of "
						+ "throttled time.");
		quota = r.gauge("host_cpu_quota_cores",
				"CPU quota, in cores. NaN when no limit is set, in which case throttling cannot happen.");
		memory = r.gauge("host_memory_usage_bytes", "Memory used by the CONTAINER, JVM heap included.");
		memoryLimit = r.gauge("host_memory_limit_bytes", "Container memory limit.");
		memoryBreakdown = r.gauge("host_memory_bytes", "Memory breakdown. type = anon|file|kernel|sock.", "type");
		pageFaults = r.counter("host_memory_page_faults_total", "Page faults. type = minor|major.", "type");
		oom = r.counter("host_memory_oom_events_total",
				"Out-of-memory events. type = oom (allocation refused) | oom_kill (process killed). "
						+ "The latter is always an incident.",
				"type");
		ioBytes = r.counter("host_io_bytes_total", "Bytes read and written, per device.", "device", "operation");
		ioOperations = r.counter("host_io_operations_total", "Disk operations, per device.", "device", "operation");
	}

	@Override
	public void collect(MetricRegistry r) throws IOException {
		if (!available) {
			return;
		}
		readCpu();
		readMemory();
		readIo();
	}

	private void readCpu() throws IOException {
		for (String line : lines("cpu.stat")) {
			String[] m = line.split("\\s+");
			if (m.length < 2) {
				continue;
			}
			double v = parse(m[1]);
			switch (m[0]) {
				// Kernel counters are in microseconds and cumulative since the container started:
				// exactly Prometheus counter semantics, units aside. Mirror instead of adding deltas.
				case "usage_usec" -> cpuUsage.mirror(v / 1e6);
				case "user_usec" -> cpuUser.mirror(v / 1e6);
				case "system_usec" -> cpuSystem.mirror(v / 1e6);
				case "nr_periods" -> periods.mirror(v);
				case "nr_throttled" -> throttledPeriods.mirror(v);
				case "throttled_usec" -> throttledTime.mirror(v / 1e6);
				default -> { }
			}
		}

		List<String> max = lines("cpu.max");
		if (!max.isEmpty()) {
			String[] m = max.get(0).trim().split("\\s+");
			// "max <period>" means no limit. A number would suggest a quota; NaN shows as a gap in
			// Grafana.
			quota.set(m.length < 2 || "max".equals(m[0]) ? Double.NaN : parse(m[0]) / parse(m[1]));
		}
	}

	private void readMemory() throws IOException {
		List<String> current = lines("memory.current");
		if (!current.isEmpty()) {
			memory.set(parse(current.get(0).trim()));
		}
		List<String> limit = lines("memory.max");
		if (!limit.isEmpty()) {
			String v = limit.get(0).trim();
			memoryLimit.set("max".equals(v) ? Double.NaN : parse(v));
		}
		for (String line : lines("memory.stat")) {
			String[] m = line.split("\\s+");
			if (m.length < 2) {
				continue;
			}
			switch (m[0]) {
				case "anon", "file", "kernel", "sock" -> memoryBreakdown.set(parse(m[1]), m[0]);
				case "pgfault" -> pageFaults.mirror(parse(m[1]), "minor");
				case "pgmajfault" -> pageFaults.mirror(parse(m[1]), "major");
				default -> { }
			}
		}
		for (String line : lines("memory.events")) {
			String[] m = line.split("\\s+");
			if (m.length >= 2 && ("oom".equals(m[0]) || "oom_kill".equals(m[0]))) {
				oom.mirror(parse(m[1]), m[0]);
			}
		}
	}

	private void readIo() throws IOException {
		// One line per device: "8:16 rbytes=0 wbytes=10059776 rios=0 wios=32 ...".
		// The label is major:minor rather than a readable name: the container cannot read
		// /sys/dev/block to resolve it, and device names are unstable labels anyway.
		for (String line : lines("io.stat")) {
			String[] m = line.trim().split("\\s+");
			if (m.length < 2) {
				continue;
			}
			String dev = m[0];
			for (int i = 1; i < m.length; i++) {
				int eq = m[i].indexOf('=');
				if (eq < 0) {
					continue;
				}
				String key = m[i].substring(0, eq);
				double v = parse(m[i].substring(eq + 1));
				switch (key) {
					case "rbytes" -> ioBytes.mirror(v, dev, "read");
					case "wbytes" -> ioBytes.mirror(v, dev, "write");
					case "rios" -> ioOperations.mirror(v, dev, "read");
					case "wios" -> ioOperations.mirror(v, dev, "write");
					default -> { }
				}
			}
		}
	}

	private List<String> lines(String file) throws IOException {
		Path p = ROOT.resolve(file);
		return Files.isReadable(p) ? Files.readAllLines(p, StandardCharsets.UTF_8) : List.of();
	}

	private static double parse(String s) {
		try {
			return Double.parseDouble(s);
		} catch (NumberFormatException e) {
			return Double.NaN;
		}
	}
}
