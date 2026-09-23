package fr.samflix.vaniametrics.testkit;

import java.util.List;
import java.util.Map;

/**
 * One pinned server build to test a loader on.
 *
 * <p>Most loaders have one. Some have two: Velocity, because production still runs 3.5 while
 * the manifest vouches for 4.x; Sponge, because 1.21.11 only exists as a release candidate of an
 * API newer than the one the plugin compiles against.
 *
 * @param name shown in reports: {@code paper}, {@code velocity-3.5.1}
 * @param build the exact build, as the report should state it
 * @param exploratory never blocking, whatever the manifest says: a preview build
 * @param env container environment that selects the build
 */
public record Variant(Loader loader, String name, String build, boolean exploratory, Map<String, String> env) {

	/** The pinned builds. Bump them here, deliberately. */
	public static List<Variant> of(Loader loader) {
		return switch (loader) {
			case PAPER -> List.of(new Variant(loader, "paper", "Paper 1.21.11-132", false,
					Map.of("TYPE", "PAPER", "VERSION", "1.21.11", "PAPER_BUILD", "132")));
			case PURPUR -> List.of(new Variant(loader, "purpur", "Purpur 1.21.11-2568", false,
					Map.of("TYPE", "PURPUR", "VERSION", "1.21.11", "PURPUR_BUILD", "2568")));
			case FOLIA -> List.of(new Variant(loader, "folia", "Folia 1.21.11-14", false,
					Map.of("TYPE", "FOLIA", "VERSION", "1.21.11", "FOLIA_BUILD", "14")));
			default -> List.of();
		};
	}
}
