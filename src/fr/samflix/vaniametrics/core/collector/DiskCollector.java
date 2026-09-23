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
 * Le disque : place restante, et taille de ce qu'on y écrit.
 *
 * <p>EN FOND, ET C'EST LE POINT. Parcourir un monde de deux gigaoctets pendant qu'un tick attend,
 * c'est fabriquer exactement le lag qu'on prétend mesurer. {@code getUsableSpace()} est instantané
 * et pourrait tenir au scrape, mais le parcours récursif ne peut pas, et séparer les deux ferait
 * deux collecteurs pour un même sujet.
 *
 * <p>Le PVC {@code lobby-backups} de ce réseau est arrivé à 100 % sans que rien ne le signale,
 * pendant des mois. C'est ce collecteur qui aurait prévenu.
 */
public final class DiskCollector implements Collector {

	private final Platform plateforme;
	private final String[] chemins;

	private Gauge libre;
	private Gauge total;
	private Gauge taille;
	private Gauge fichiers;

	public DiskCollector(Platform plateforme, Config config) {
		this.plateforme = plateforme;
		// Des chemins et non une découverte automatique : ce qui nous intéresse est monté par le
		// chart et connu de lui, et parcourir « tout ce qui est monté » ramasserait les systèmes
		// de fichiers du noyau.
		this.chemins = config.texte("collector.disk.paths", "/data,/backups,/server")
				.split("\\s*,\\s*");
	}

	@Override
	public String nom() {
		return "disk";
	}

	@Override
	public boolean enFond() {
		return true;
	}

	@Override
	public long intervalleSecondes() {
		return 300;
	}

	@Override
	public void declarer(MetricRegistry r) {
		libre = r.gauge("host_disk_free_bytes", "Place disponible sur le point de montage.", "path");
		total = r.gauge("host_disk_total_bytes", "Taille du point de montage.", "path");
		taille = r.gauge("host_disk_usage_bytes",
				"Taille du contenu, calculée par parcours. Mesurée en fond, jamais au scrape.",
				"path");
		fichiers = r.gauge("host_disk_files", "Nombre de fichiers sous le chemin.", "path");
	}

	@Override
	public void relever(MetricRegistry r) {
		for (String chemin : chemins) {
			Path p = Path.of(chemin.trim());
			if (!Files.isDirectory(p)) {
				continue;
			}
			try {
				libre.set(Files.getFileStore(p).getUsableSpace(), chemin);
				total.set(Files.getFileStore(p).getTotalSpace(), chemin);
			} catch (IOException e) {
				plateforme.avertir("place disque illisible pour " + chemin + " : " + e.getMessage());
			}
			long[] compte = parcourir(p);
			taille.set(compte[0], chemin);
			fichiers.set(compte[1], chemin);
		}
	}

	private long[] parcourir(Path racine) {
		AtomicLong octets = new AtomicLong();
		AtomicLong nombre = new AtomicLong();
		try {
			Files.walkFileTree(racine, new SimpleFileVisitor<>() {
				@Override
				public FileVisitResult visitFile(Path f, BasicFileAttributes a) {
					if (a.isRegularFile()) {
						octets.addAndGet(a.size());
						nombre.incrementAndGet();
					}
					return FileVisitResult.CONTINUE;
				}

				@Override
				public FileVisitResult visitFileFailed(Path f, IOException e) {
					// Un fichier supprimé pendant le parcours, ou un lien cassé. Le monde vit
					// sous nos pieds : ce n'est pas une erreur, c'est la normale.
					return FileVisitResult.CONTINUE;
				}
			});
		} catch (IOException e) {
			plateforme.avertir("parcours de " + racine + " interrompu : " + e.getMessage());
		}
		return new long[] {octets.get(), nombre.get()};
	}
}
