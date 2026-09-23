package fr.samflix.vaniametrics.api;

import java.util.Collection;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Le registre : il tient les instruments et sait les rendre au format de Prometheus.
 *
 * <p>Il n'y a pas de bibliothèque derrière, et c'est délibéré. Le format d'exposition tient en une
 * page ; y ajouter {@code simpleclient} ferait entrer une dépendance à reloger dans un jar de
 * plugin, avec le risque de conflit de classes que ça traîne quand deux plugins embarquent la même
 * en versions différentes. Ce qui suit est tout ce dont on a besoin.
 */
public final class MetricRegistry {

	/**
	 * Le préfixe de toutes les métriques.
	 *
	 * <p>Un préfixe unique par sujet est une convention de Prometheus, pas une coquetterie : c'est
	 * lui qui rend {@code mc_} utilisable en autocomplétion dans Grafana et qui évite qu'une
	 * métrique du serveur se confonde avec une du nœud.
	 */
	public static final String PREFIXE = "mc_";

	/**
	 * LES DOMAINES AUTORISÉS, ET LA CONVENTION DE NOMMAGE.
	 *
	 * <pre>
	 *   mc_&lt;domaine&gt;_&lt;sujet&gt;[_&lt;unité&gt;]
	 * </pre>
	 *
	 * <p>LE DOMAINE DIT D'OÙ VIENT LA MESURE, et c'est sa seule raison d'être. En lisant
	 * {@code mc_server_tps} on sait que le serveur de jeu la fournit lui-même ; en lisant
	 * {@code mc_economy_total} on sait qu'elle vient d'un plugin, et lequel se retrouve par
	 * {@code mc_exporter_module_info}.
	 *
	 * <table border="1">
	 *   <caption>Domaines</caption>
	 *   <tr><th>domaine</th><th>origine</th><th>ce qu'il couvre</th></tr>
	 *   <tr><td>{@code server}</td><td>serveur de base</td><td>tps, ticks, joueurs, morts, blocs, chat</td></tr>
	 *   <tr><td>{@code world}</td><td>serveur de base</td><td>entités, chunks, blocs-entités, météo</td></tr>
	 *   <tr><td>{@code proxy}</td><td>Velocity</td><td>joueurs, serveurs d'arrière-plan</td></tr>
	 *   <tr><td>{@code jvm}</td><td>machine virtuelle</td><td>mémoire, ramasse-miettes, fils</td></tr>
	 *   <tr><td>{@code host}</td><td>conteneur</td><td>processeur, mémoire, disque, entrées-sorties</td></tr>
	 *   <tr><td>{@code economy}</td><td>plugin</td><td>monnaies, soldes, flux</td></tr>
	 *   <tr><td>{@code quest}</td><td>plugin</td><td>tags, points, journal</td></tr>
	 *   <tr><td>{@code permission}</td><td>plugin</td><td>groupes, pistes</td></tr>
	 *   <tr><td>{@code network}</td><td>plugin</td><td>paquets, version des clients</td></tr>
	 *   <tr><td>{@code multiverse}</td><td>plugin</td><td>mondes déclarés, chargés ou non</td></tr>
	 *   <tr><td>{@code spark}</td><td>plugin</td><td>quantiles de tick, processeur, allocation</td></tr>
	 *   <tr><td>{@code anticheat}</td><td>plugin</td><td>violations, contrôles déclenchés</td></tr>
	 *   <tr><td>{@code mob}</td><td>plugin</td><td>mobs personnalisés apparus, tués</td></tr>
	 *   <tr><td>{@code region}</td><td>plugin</td><td>régions protégées, actions refusées</td></tr>
	 *   <tr><td>{@code pregen}</td><td>plugin</td><td>avancement de la prégénération</td></tr>
	 *   <tr><td>{@code inventory}</td><td>plugin</td><td>bascules d'inventaire entre mondes</td></tr>
	 *   <tr><td>{@code portal}</td><td>plugin</td><td>passages de portail</td></tr>
	 *   <tr><td>{@code nova}</td><td>plugin</td><td>blocs et objets personnalisés</td></tr>
	 *   <tr><td>{@code crate}</td><td>plugin</td><td>coffres ouverts, récompenses tirées, clés</td></tr>
	 *   <tr><td>{@code placeholder}</td><td>plugin</td><td>valeurs relevées par PlaceholderAPI</td></tr>
	 *   <tr><td>{@code exporter}</td><td>ce plugin</td><td>sa propre santé</td></tr>
	 * </table>
	 *
	 * <p>LE DÉTAIL PAR JOUEUR RESTE DANS SON DOMAINE, en sous-segment : le solde d'un joueur est
	 * {@code mc_economy_player_balance} et non {@code mc_player_balance}. Le domaine d'abord,
	 * toujours — sinon {@code mc_player_*} deviendrait un fourre-tout où plus rien ne dit d'où
	 * vient quoi.
	 *
	 * <p>LA LISTE EST FERMÉE ET VÉRIFIÉE À LA DÉCLARATION. Une convention qui n'est qu'écrite dans
	 * un document dérive au troisième module ; celle-ci refuse de se charger. Ajouter un domaine
	 * est un geste délibéré, qui passe par ce tableau.
	 */
	private static final java.util.Set<String> DOMAINES = java.util.Set.of(
			// Ce que le serveur et la machine exposent d'eux-mêmes.
			"server", "world", "proxy", "jvm", "host",
			// Un plugin derrière chacun.
			"economy", "quest", "permission", "network", "multiverse", "spark",
			"anticheat", "mob", "region", "pregen", "inventory", "portal", "nova",
			"placeholder", "crate",
			// L'exportateur lui-même.
			"exporter", "build");

	private final Map<String, Metric> instruments = new ConcurrentHashMap<>();

	/** Déclare une jauge. Rappeler la méthode avec le même nom rend le même instrument. */
	public Gauge gauge(String nom, String aide, String... etiquettes) {
		verifier(nom);
		return (Gauge) instruments.computeIfAbsent(
				PREFIXE + nom, n -> new Gauge(n, aide, etiquettes));
	}

	/** Déclare un compteur. Le nom DOIT se terminer par {@code _total}. */
	public Counter counter(String nom, String aide, String... etiquettes) {
		verifier(nom);
		if (!nom.endsWith("_total")) {
			throw new IllegalArgumentException("un compteur se termine par _total : " + nom);
		}
		return (Counter) instruments.computeIfAbsent(
				PREFIXE + nom, n -> new Counter(n, aide, etiquettes));
	}

	/** Déclare un histogramme. Le nom porte l'unité, pas de suffixe {@code _total}. */
	public Histogram histogram(String nom, String aide, double[] seuils, String... etiquettes) {
		verifier(nom);
		return (Histogram) instruments.computeIfAbsent(
				PREFIXE + nom, n -> new Histogram(n, aide, seuils, etiquettes));
	}

	/**
	 * Fait respecter la convention, à la déclaration.
	 *
	 * <p>ÉCHOUER ICI EST LE BUT. Un module mal nommé ne se charge pas, son message dit quoi
	 * corriger, et les autres continuent — c'est {@code Exporter} qui attrape. L'alternative,
	 * une règle écrite quelque part, aurait dérivé au troisième module.
	 */
	private static void verifier(String nom) {
		int sep = nom.indexOf('_');
		String domaine = sep < 0 ? nom : nom.substring(0, sep);
		if (!DOMAINES.contains(domaine)) {
			throw new IllegalArgumentException(
					"« " + nom + " » n'a pas de domaine connu. Attendu mc_<domaine>_<sujet>, "
							+ "domaine parmi " + new java.util.TreeSet<>(DOMAINES)
							+ " — voir la table de MetricRegistry.");
		}
	}

	Collection<Metric> instruments() {
		return instruments.values();
	}

	/**
	 * Rend tout le registre au format texte de Prometheus, version 0.0.4.
	 *
	 * <p>LES FAMILLES SONT TRIÉES PAR NOM. Prometheus ne l'exige pas, mais un {@code curl
	 * /metrics} lisible à l'œil vaut tous les outils de diagnostic le jour où quelque chose cloche.
	 */
	public String rendre() {
		StringBuilder out = new StringBuilder(16 * 1024);
		instruments.values().stream()
				.sorted(java.util.Comparator.comparing(m -> m.name))
				.forEach(m -> rendreInstrument(out, m));
		return out.toString();
	}

	private void rendreInstrument(StringBuilder out, Metric m) {
		if (m.series.isEmpty()) {
			return;
		}
		out.append("# HELP ").append(m.name).append(' ').append(echapperAide(m.help)).append('\n');
		out.append("# TYPE ").append(m.name).append(' ').append(m.type()).append('\n');

		for (Map.Entry<Metric.LabelValues, double[]> e : m.series.entrySet()) {
			String[] valeurs = e.getKey().valeurs;
			double[] s = e.getValue();
			if (m instanceof Histogram h) {
				rendreHistogramme(out, h, valeurs, s);
			} else {
				ligne(out, m.name, m.labelNames, valeurs, null, null, s[0]);
			}
		}
	}

	private void rendreHistogramme(StringBuilder out, Histogram h, String[] valeurs, double[] s) {
		// Les seaux sont CUMULATIFS et doivent sortir dans l'ordre croissant : Prometheus
		// s'appuie sur cet ordre pour interpoler les quantiles.
		for (int i = 0; i < h.seuils.length; i++) {
			ligne(out, h.name + "_bucket", h.labelNames, valeurs, "le", nombre(h.seuils[i]), s[i]);
		}
		double total = s[h.seuils.length + 1];
		ligne(out, h.name + "_bucket", h.labelNames, valeurs, "le", "+Inf", total);
		ligne(out, h.name + "_sum", h.labelNames, valeurs, null, null, s[h.seuils.length]);
		ligne(out, h.name + "_count", h.labelNames, valeurs, null, null, total);
	}

	private void ligne(StringBuilder out, String nom, String[] noms, String[] valeurs,
			String nomSup, String valeurSup, double valeur) {
		out.append(nom);
		if (noms.length > 0 || nomSup != null) {
			out.append('{');
			for (int i = 0; i < noms.length; i++) {
				if (i > 0) {
					out.append(',');
				}
				out.append(noms[i]).append("=\"").append(echapper(valeurs[i])).append('"');
			}
			if (nomSup != null) {
				if (noms.length > 0) {
					out.append(',');
				}
				out.append(nomSup).append("=\"").append(valeurSup).append('"');
			}
			out.append('}');
		}
		out.append(' ').append(nombre(valeur)).append('\n');
	}

	/**
	 * Un nombre au format attendu.
	 *
	 * <p>Locale.ROOT est OBLIGATOIRE : sur un serveur en locale française, {@code %f} écrirait
	 * « 20,0 » et Prometheus rejetterait la ligne entière. Le serveur tourne en Europe/Paris, le
	 * piège est réel.
	 *
	 * <p>Les entiers sortent sans décimale pour que le fichier reste lisible, et les valeurs
	 * spéciales prennent l'orthographe de Prometheus, qui n'est pas celle de Java.
	 */
	static String nombre(double v) {
		if (Double.isNaN(v)) {
			return "NaN";
		}
		if (v == Double.POSITIVE_INFINITY) {
			return "+Inf";
		}
		if (v == Double.NEGATIVE_INFINITY) {
			return "-Inf";
		}
		if (v == Math.rint(v) && Math.abs(v) < 1e15) {
			return String.format(Locale.ROOT, "%d", (long) v);
		}
		double abs = Math.abs(v);
		if (abs < 1e-6 || abs >= 1e15) {
			// Notation scientifique : Prometheus l'accepte, et écrire 1e-9 en décimal donnerait
			// une ligne illisible pour une valeur qui ne veut de toute façon rien dire.
			return Double.toString(v);
		}
		// La représentation la plus COURTE qui relit exactement le même double. « %.6g » rendait
		// 0,001 sous la forme « 0.00100000 » : juste, mais un seuil d'histogramme se relit à
		// l'œil, et huit zéros de plus n'aident personne.
		return new java.math.BigDecimal(Double.toString(v)).stripTrailingZeros().toPlainString();
	}

	/** Dans une valeur d'étiquette, trois caractères doivent être protégés. */
	static String echapper(String v) {
		if (v == null) {
			return "";
		}
		return v.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
	}

	/** Dans une ligne d'aide, deux seulement — les guillemets y sont libres. */
	static String echapperAide(String v) {
		return v.replace("\\", "\\\\").replace("\n", "\\n");
	}
}
