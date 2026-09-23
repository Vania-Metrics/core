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
 * La JVM : mémoire, ramasse-miettes, fils.
 *
 * <p>Tout vient de {@code java.lang.management}, présent dans tout JDK, lisible sans privilège et
 * sans coût mesurable. C'est le collecteur le plus rentable du lot.
 *
 * <p>ATTENTION À LA LECTURE DU TAS. « Le tas est plein » ne veut rien dire : un tas G1 monte
 * toujours jusqu'à son seuil avant de collecter, c'est son fonctionnement normal. Ce qu'il faut
 * regarder, c'est le tas APRÈS collecte — donc la ligne {@code old gen} après un GC complet — et
 * le TAUX D'ALLOCATION, qui dit à quelle vitesse on remplit. Le second vient de spark, pas d'ici.
 */
public final class JvmCollector implements Collector {

	private Gauge memoire;
	private Gauge memoirePool;
	private Counter gcNombre;
	private Counter gcTemps;
	private Gauge fils;
	private Gauge interblocages;
	private Gauge tampons;
	private Gauge classes;
	private Gauge demarrage;

	@Override
	public String nom() {
		return "jvm";
	}

	@Override
	public void declarer(MetricRegistry r) {
		memoire = r.gauge("jvm_memory_bytes",
				"Mémoire de la JVM. area = heap|nonheap, state = used|committed|max|init.",
				"area", "state");
		memoirePool = r.gauge("jvm_memory_pool_bytes",
				"Mémoire par zone du ramasse-miettes (Eden, Survivor, Old, Metaspace).",
				"pool", "state");
		gcNombre = r.counter("jvm_gc_collections_total",
				"Collectes effectuées, par ramasse-miettes.", "gc");
		gcTemps = r.counter("jvm_gc_seconds_total",
				"Temps cumulé de collecte. C'est du temps de collecte, PAS du temps d'arrêt du "
						+ "monde — pour la vraie pause il faut JFR ou les journaux GC.",
				"gc");
		fils = r.gauge("jvm_threads", "Fils de la JVM. state = live|daemon|peak|started.", "state");
		interblocages = r.gauge("jvm_threads_deadlocked",
				"Fils en interblocage. Toute valeur non nulle est un incident.");
		tampons = r.gauge("jvm_buffer_pool_bytes",
				"Tampons hors tas. Ils comptent dans la mémoire du CONTENEUR mais pas dans le tas, "
						+ "et c'est une cause classique d'OOM kill inexpliqué.",
				"pool", "state");
		classes = r.gauge("jvm_classes_loaded", "Classes chargées.");
		demarrage = r.gauge("jvm_uptime_seconds", "Temps depuis le démarrage de la JVM.");
	}

	@Override
	public void relever(MetricRegistry r) {
		var mem = ManagementFactory.getMemoryMXBean();
		poser(memoire, "heap", mem.getHeapMemoryUsage());
		poser(memoire, "nonheap", mem.getNonHeapMemoryUsage());

		for (MemoryPoolMXBean pool : ManagementFactory.getMemoryPoolMXBeans()) {
			poser(memoirePool, pool.getName(), pool.getUsage());
		}

		for (GarbageCollectorMXBean gc : ManagementFactory.getGarbageCollectorMXBeans()) {
			// Les compteurs de la JVM sont cumulatifs depuis le démarrage, comme ceux de
			// Prometheus : on POSE la valeur au lieu d'ajouter un delta, ce qui évite de tenir
			// un état et reste juste même si un relevé est sauté.
			gcNombre.mirror(gc.getCollectionCount(), gc.getName());
			gcTemps.mirror(gc.getCollectionTime() / 1000.0, gc.getName());
		}

		ThreadMXBean t = ManagementFactory.getThreadMXBean();
		fils.set(t.getThreadCount(), "live");
		fils.set(t.getDaemonThreadCount(), "daemon");
		fils.set(t.getPeakThreadCount(), "peak");
		fils.set(t.getTotalStartedThreadCount(), "started");
		long[] bloques = t.findDeadlockedThreads();
		interblocages.set(bloques == null ? 0 : bloques.length);

		for (BufferPoolMXBean pool : ManagementFactory.getPlatformMXBeans(BufferPoolMXBean.class)) {
			tampons.set(pool.getMemoryUsed(), pool.getName(), "used");
			tampons.set(pool.getTotalCapacity(), pool.getName(), "capacity");
			tampons.set(pool.getCount(), pool.getName(), "count");
		}

		classes.set(ManagementFactory.getClassLoadingMXBean().getLoadedClassCount());
		demarrage.set(ManagementFactory.getRuntimeMXBean().getUptime() / 1000.0);
	}

	private void poser(Gauge g, String zone, MemoryUsage u) {
		if (u == null) {
			return;
		}
		g.set(u.getUsed(), zone, "used");
		g.set(u.getCommitted(), zone, "committed");
		g.set(u.getInit(), zone, "init");
		// -1 signifie « pas de maximum ». Publier -1 ferait un graphique absurde ; NaN dit
		// « inconnu », que Prometheus et Grafana savent tous deux ignorer.
		g.set(u.getMax() < 0 ? Double.NaN : u.getMax(), zone, "max");
	}

}
