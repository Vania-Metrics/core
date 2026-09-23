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
 * LE CONTENEUR : processeur, ÉTRANGLEMENT, mémoire, entrées-sorties.
 *
 * <p>C'est le collecteur que rien d'autre ne fournit, et celui qui répond aux pannes les plus
 * difficiles à diagnostiquer. Sur un serveur conteneurisé, le TPS peut s'effondrer sans que spark
 * ne voie quoi que ce soit d'anormal dans le profil : la cause est un étage plus bas, le noyau a
 * gelé le processus parce qu'il dépassait son quota de processeur. {@code nr_throttled} le dit, et
 * rien dans l'écosystème Minecraft ne le lit.
 *
 * <p>TOUT EST DANS DES FICHIERS TEXTE de {@code /sys/fs/cgroup} — aucune dépendance, aucun
 * privilège, aucun appel natif. Le collecteur s'éteint de lui-même si le répertoire n'existe pas,
 * ce qui est le cas hors conteneur et sur cgroup v1.
 *
 * <p>UNE LECTURE À NE PAS RATER : {@code memory.current} contre {@code memory.max}, et pas le tas
 * de la JVM. Une JVM bornée à 6 Gio dans un conteneur de 8 Gio peut se faire tuer par le noyau
 * alors que son tas est à moitié vide — métaspace, piles de fils, tampons directs et cache de
 * fichiers mappés vivent hors du tas et comptent quand même. {@code memory.events} est la seule
 * métrique qui dit qu'un OOM kill a eu lieu.
 */
public final class CgroupCollector implements Collector {

	private static final Path RACINE = Path.of("/sys/fs/cgroup");

	private final Platform plateforme;
	private final boolean disponible;

	private Counter cpuTemps;
	private Counter cpuUtilisateur;
	private Counter cpuSysteme;
	private Counter etranglePeriodes;
	private Counter etrangleTemps;
	private Counter periodes;
	private Gauge quota;
	private Gauge memoire;
	private Gauge memoireLimite;
	private Gauge memoireDetail;
	private Counter defautsPage;
	private Counter oom;
	private Counter ioOctets;
	private Counter ioOperations;

	public CgroupCollector(Platform plateforme) {
		this.plateforme = plateforme;
		this.disponible = Files.isReadable(RACINE.resolve("cpu.stat"));
	}

	@Override
	public String nom() {
		return "cgroup";
	}

	@Override
	public void declarer(MetricRegistry r) {
		if (!disponible) {
			plateforme.info("collecteur cgroup — /sys/fs/cgroup/cpu.stat illisible, hors conteneur "
					+ "ou cgroup v1 : rien ne sera publié");
			return;
		}
		cpuTemps = r.counter("host_cpu_usage_seconds_total", "Temps processeur consommé par le conteneur.");
		cpuUtilisateur = r.counter("host_cpu_user_seconds_total", "Temps processeur en espace utilisateur.");
		cpuSysteme = r.counter("host_cpu_system_seconds_total", "Temps processeur en noyau.");
		etranglePeriodes = r.counter("host_cpu_throttled_periods_total",
				"Périodes pendant lesquelles le noyau a GELÉ le conteneur faute de quota. "
						+ "Toute valeur qui monte explique un TPS bas que spark ne voit pas.");
		etrangleTemps = r.counter("host_cpu_throttled_seconds_total",
				"Temps total passé gelé par le noyau.");
		periodes = r.counter("host_cpu_periods_total",
				"Périodes d'ordonnancement écoulées. Le rapport avec throttled_periods donne la "
						+ "proportion de temps étranglé.");
		quota = r.gauge("host_cpu_quota_cores",
				"Quota processeur, en cœurs. NaN quand aucune limite n'est posée — et sans limite, "
						+ "l'étranglement est impossible.");
		memoire = r.gauge("host_memory_usage_bytes", "Mémoire du CONTENEUR, tas de la JVM compris.");
		memoireLimite = r.gauge("host_memory_limit_bytes", "Plafond mémoire du conteneur.");
		memoireDetail = r.gauge("host_memory_bytes", "Détail de la mémoire. type = anon|file|kernel|sock.", "type");
		defautsPage = r.counter("host_memory_page_faults_total", "Défauts de page. type = minor|major.", "type");
		oom = r.counter("host_memory_oom_events_total",
				"Événements de dépassement mémoire. type = oom (allocation refusée) | "
						+ "oom_kill (processus tué). La seconde est toujours un incident.",
				"type");
		ioOctets = r.counter("host_io_bytes_total", "Octets lus et écrits, par périphérique.", "device", "operation");
		ioOperations = r.counter("host_io_operations_total", "Opérations disque, par périphérique.", "device", "operation");
	}

	@Override
	public void relever(MetricRegistry r) throws IOException {
		if (!disponible) {
			return;
		}
		lireCpu();
		lireMemoire();
		lireIo();
	}

	private void lireCpu() throws IOException {
		for (String ligne : lignes("cpu.stat")) {
			String[] m = ligne.split("\\s+");
			if (m.length < 2) {
				continue;
			}
			double v = valeur(m[1]);
			switch (m[0]) {
				// Les compteurs du noyau sont en MICROsecondes et cumulatifs depuis le démarrage
				// du conteneur — exactement la sémantique d'un compteur Prometheus, à l'unité
				// près. On pose la valeur au lieu d'ajouter un delta.
				case "usage_usec" -> mirror(cpuTemps, v / 1e6);
				case "user_usec" -> mirror(cpuUtilisateur, v / 1e6);
				case "system_usec" -> mirror(cpuSysteme, v / 1e6);
				case "nr_periods" -> mirror(periodes, v);
				case "nr_throttled" -> mirror(etranglePeriodes, v);
				case "throttled_usec" -> mirror(etrangleTemps, v / 1e6);
				default -> { }
			}
		}

		List<String> max = lignes("cpu.max");
		if (!max.isEmpty()) {
			String[] m = max.get(0).trim().split("\\s+");
			// « max <periode> » = aucune limite. Publier une valeur numérique ferait croire à un
			// quota ; NaN dit « pas de limite », ce que Grafana affiche comme un trou.
			quota.set(m.length < 2 || "max".equals(m[0]) ? Double.NaN : valeur(m[0]) / valeur(m[1]));
		}
	}

	private void lireMemoire() throws IOException {
		List<String> courant = lignes("memory.current");
		if (!courant.isEmpty()) {
			memoire.set(valeur(courant.get(0).trim()));
		}
		List<String> limite = lignes("memory.max");
		if (!limite.isEmpty()) {
			String v = limite.get(0).trim();
			memoireLimite.set("max".equals(v) ? Double.NaN : valeur(v));
		}
		for (String ligne : lignes("memory.stat")) {
			String[] m = ligne.split("\\s+");
			if (m.length < 2) {
				continue;
			}
			switch (m[0]) {
				case "anon", "file", "kernel", "sock" -> memoireDetail.set(valeur(m[1]), m[0]);
				case "pgfault" -> mirror(defautsPage, valeur(m[1]), "minor");
				case "pgmajfault" -> mirror(defautsPage, valeur(m[1]), "major");
				default -> { }
			}
		}
		for (String ligne : lignes("memory.events")) {
			String[] m = ligne.split("\\s+");
			if (m.length >= 2 && ("oom".equals(m[0]) || "oom_kill".equals(m[0]))) {
				mirror(oom, valeur(m[1]), m[0]);
			}
		}
	}

	private void lireIo() throws IOException {
		// Une ligne par périphérique : « 8:16 rbytes=0 wbytes=10059776 rios=0 wios=32 … ».
		// L'étiquette est le couple majeur:mineur et non un nom lisible — le conteneur n'a pas
		// accès à /sys/dev/block pour le résoudre, et un nom de périphérique instable ferait de
		// toute façon une mauvaise étiquette.
		for (String ligne : lignes("io.stat")) {
			String[] m = ligne.trim().split("\\s+");
			if (m.length < 2) {
				continue;
			}
			String dev = m[0];
			for (int i = 1; i < m.length; i++) {
				int eq = m[i].indexOf('=');
				if (eq < 0) {
					continue;
				}
				String cle = m[i].substring(0, eq);
				double v = valeur(m[i].substring(eq + 1));
				switch (cle) {
					case "rbytes" -> ioOctets.mirror(v, dev, "read");
					case "wbytes" -> ioOctets.mirror(v, dev, "write");
					case "rios" -> ioOperations.mirror(v, dev, "read");
					case "wios" -> ioOperations.mirror(v, dev, "write");
					default -> { }
				}
			}
		}
	}

	private void mirror(Counter c, double v, String... etiquettes) {
		if (c != null) {
			c.mirror(v, etiquettes);
		}
	}

	private List<String> lignes(String fichier) throws IOException {
		Path p = RACINE.resolve(fichier);
		return Files.isReadable(p) ? Files.readAllLines(p, StandardCharsets.UTF_8) : List.of();
	}

	private static double valeur(String s) {
		try {
			return Double.parseDouble(s);
		} catch (NumberFormatException e) {
			return Double.NaN;
		}
	}
}
