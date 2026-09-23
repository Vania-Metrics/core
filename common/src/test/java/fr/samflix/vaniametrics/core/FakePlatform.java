package fr.samflix.vaniametrics.core;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import fr.samflix.vaniametrics.api.Platform;

/**
 * A platform that runs nothing on its own: background tasks are recorded and run on demand, the
 * main thread is the calling thread, and log lines are kept for assertions.
 */
public final class FakePlatform implements Platform {

	/** A task handed to {@link #scheduleRepeating}. */
	public record Scheduled(Runnable task, long intervalSeconds) {
	}

	public final List<String> infos = new CopyOnWriteArrayList<>();
	public final List<String> warnings = new CopyOnWriteArrayList<>();
	public final List<String> errors = new CopyOnWriteArrayList<>();
	public final List<Scheduled> scheduled = new CopyOnWriteArrayList<>();

	private final Path dataDirectory;
	private volatile Exception mainThreadFailure;

	public FakePlatform(Path dataDirectory) {
		this.dataDirectory = dataDirectory;
	}

	/** Makes {@link #runOnMainThread} throw instead of running the task. */
	public void failMainThread(Exception e) {
		mainThreadFailure = e;
	}

	/** Runs every background task once, as one tick of the scheduler would. */
	public void runBackgroundTasks() {
		scheduled.forEach(s -> s.task().run());
	}

	@Override
	public String type() {
		return "paper";
	}

	@Override
	public String serverName() {
		return "lobby";
	}

	@Override
	public String serverVersion() {
		return "1.21.11-test";
	}

	@Override
	public Path dataDirectory() {
		return dataDirectory;
	}

	@Override
	public void info(String message) {
		infos.add(message);
	}

	@Override
	public void warn(String message) {
		warnings.add(message);
	}

	@Override
	public void error(String message, Throwable cause) {
		errors.add(message + ": " + cause);
	}

	@Override
	public boolean isPluginPresent(String name) {
		return false;
	}

	@Override
	public void runOnMainThread(Runnable task) throws Exception {
		Exception failure = mainThreadFailure;
		if (failure != null) {
			throw failure;
		}
		task.run();
	}

	@Override
	public void scheduleRepeating(Runnable task, long intervalSeconds) {
		scheduled.add(new Scheduled(task, intervalSeconds));
	}
}
