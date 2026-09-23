package fr.samflix.vaniametrics.testkit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import fr.samflix.vaniametrics.testkit.Manifest.Claim;

class ManifestTest {

	@TempDir
	Path dir;

	private Manifest load(String yaml) throws Exception {
		Path file = dir.resolve("compatibility.yml");
		Files.writeString(file, yaml);
		return Manifest.load(file);
	}

	@Test
	void theCoreManifestMapsTestedToYes() throws Exception {
		Manifest m = load("""
				minecraft: 1.21.11
				version: 0.4.0
				platforms:
				  paper:  { jar: vania-metrics-bukkit, status: tested, on: "Paper 1.21.11-132" }
				  bukkit: { jar: vania-metrics-bukkit, status: untested, note: "same code path as Spigot" }
				""");
		assertTrue(m.isCore());
		assertEquals(Claim.YES, m.entries().get("paper").claim());
		assertEquals(Claim.UNTESTED, m.entries().get("bukkit").claim());
		assertEquals("1.21.11", m.get("minecraft"));
	}

	@Test
	void yesAndNoStayWordsNotBooleans() throws Exception {
		Manifest m = load("""
				minecraft: 1.21.11
				core: v0.4.0
				plugin:
				  name: Chunky
				  compiled-against: 1.4.40
				platforms:
				  paper: { plugin: yes, collector: yes }
				  spigot: { plugin: yes, collector: untested }
				  folia: { plugin: yes, collector: no }
				  sponge: { plugin: unknown, collector: no }
				""");
		assertFalse(m.isCore());
		assertEquals(Claim.YES, m.entries().get("paper").claim());
		assertEquals("yes", m.entries().get("paper").plugin());
		assertEquals(Claim.UNTESTED, m.entries().get("spigot").claim());
		assertEquals(Claim.NO, m.entries().get("folia").claim());
		assertEquals("unknown", m.entries().get("sponge").plugin());
		assertEquals("1.4.40", m.plugin("compiled-against"));
		assertEquals("v0.4.0", m.get("core"));
	}

	@Test
	void anUnknownClaimIsRefused() {
		assertThrows(IllegalArgumentException.class, () -> load("""
				minecraft: 1.21.11
				core: v0.4.0
				plugin: { name: X }
				platforms:
				  paper: { plugin: yes, collector: maybe }
				"""));
	}

	@Test
	void theRealManifestsLoad() throws Exception {
		Path core = Path.of(System.getProperty("vania.it.projectDir", ".."), "compatibility.yml");
		if (Files.exists(core)) {
			Manifest m = Manifest.load(core);
			assertTrue(m.isCore());
			assertEquals(10, m.entries().size());
		}
	}
}
