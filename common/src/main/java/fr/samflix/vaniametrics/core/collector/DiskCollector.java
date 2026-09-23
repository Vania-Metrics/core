package fr.samflix.vaniametrics.core.collector;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.concurrent.atomic.AtomicLong;

import fr.samflix.vaniametrics.api.Collector;
import fr.samflix.vaniametrics.api.Config;
import fr.samflix.vaniametrics.api.Gauge;
import fr.samflix.vaniametrics.api.MetricRegistry;
import fr.samflix.vaniametrics.api.Platform;

/**
 * Disk: free space, and the size of what is written there.
 *
 * <p>Runs in the background. Walking a 2 GB world while a tick waits would create the very lag
 * this plugin measures. {@code getUsableSpace()} is instant and could run on scrape, but the
 * recursive walk cannot, and splitting them would make two collectors for one subject.
 *
 * <p>On the network this was written for, a backup volume sat at 100% for months without anything
 * flagging it. This collector would have.
 */
public final class DiskCollector implements Collector {

	private final Platform platform;
	private final String[] paths;

	private Gauge free;
	private Gauge total;
	private Gauge usage;
	private Gauge files;

	public DiskCollector(Platform platform, Config config) {
		this.platform = platform;
		// Explicit paths rather than auto-discovery: the interesting volumes are mounted by the
		// deployment, which knows them, and walking "everything mounted" would pick up kernel
		// filesystems.
		this.paths = config.getString("collector.disk.paths", "/data,/backups,/server")
				.split("\\s*,\\s*");
	}

	@Override
	public String name() {
		return "disk";
	}

	@Override
	public boolean isBackground() {
		return true;
	}

	@Override
	public long intervalSeconds() {
		return 300;
	}

	@Override
	public void declare(MetricRegistry r) {
		free = r.gauge("host_disk_free_bytes", "Free space on the mount point.", "path");
		total = r.gauge("host_disk_total_bytes", "Size of the mount point.", "path");
		usage = r.gauge("host_disk_usage_bytes",
				"Size of the contents, computed by walking the tree. Measured in the background, "
						+ "never on scrape.",
				"path");
		files = r.gauge("host_disk_files", "Number of files under the path.", "path");
	}

	@Override
	public void collect(MetricRegistry r) {
		for (String path : paths) {
			Path p = Path.of(path.trim());
			if (!Files.isDirectory(p)) {
				continue;
			}
			try {
				free.set(Files.getFileStore(p).getUsableSpace(), path);
				total.set(Files.getFileStore(p).getTotalSpace(), path);
			} catch (IOException e) {
				platform.warn("could not read disk space for " + path + ": " + e.getMessage());
			}
			long[] count = walk(p);
			usage.set(count[0], path);
			files.set(count[1], path);
		}
	}

	private long[] walk(Path root) {
		AtomicLong bytes = new AtomicLong();
		AtomicLong fileCount = new AtomicLong();
		try {
			Files.walkFileTree(root, new SimpleFileVisitor<>() {
				@Override
				public FileVisitResult visitFile(Path f, BasicFileAttributes a) {
					if (a.isRegularFile()) {
						bytes.addAndGet(a.size());
						fileCount.incrementAndGet();
					}
					return FileVisitResult.CONTINUE;
				}

				@Override
				public FileVisitResult visitFileFailed(Path f, IOException e) {
					// A file deleted mid-walk, or a broken link. The world changes under our feet;
					// that is normal, not an error.
					return FileVisitResult.CONTINUE;
				}
			});
		} catch (IOException e) {
			platform.warn("walk of " + root + " interrupted: " + e.getMessage());
		}
		return new long[] {bytes.get(), fileCount.get()};
	}
}
