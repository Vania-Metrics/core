package fr.samflix.vaniametrics.api;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Properties;

/**
 * La configuration : un fichier {@code metrics.properties}, que l'environnement peut surcharger.
 *
 * <p>POURQUOI DES PROPERTIES ET PAS DU YAML, alors que tout le dépôt est en YAML. Parce que ce
 * plugin tourne sur DEUX plateformes qui n'ont pas le même format de configuration — Bukkit lit du
 * YAML par SnakeYAML, Velocity du TOML par Configurate — et que le noyau ne doit connaître ni
 * l'une ni l'autre. {@code java.util.Properties} est dans le JDK, se lit pareil des deux côtés, et
 * n'ajoute aucune dépendance à reloger dans le jar.
 *
 * <p>L'ENVIRONNEMENT L'EMPORTE SUR LE FICHIER, comme pour Plan et LuckPerms dans ce dépôt : la clé
 * {@code http.port} se surcharge par {@code VANIA_METRICS_HTTP_PORT}. C'est ce qui permet au chart
 * de tout décider depuis values.yaml sans livrer de fichier.
 */
public final class Config {

	private static final String PREFIXE_ENV = "VANIA_METRICS_";

	private final Properties props = new Properties();

	private Config() {}

	/**
	 * Charge la configuration, en écrivant le fichier par défaut s'il manque.
	 *
	 * <p>Le fichier écrit porte ses commentaires : un opérateur qui l'ouvre doit comprendre ce
	 * qu'il règle sans aller lire le code.
	 */
	public static Config charger(Platform plateforme) {
		Config c = new Config();
		Path fichier = plateforme.repertoire().resolve("metrics.properties");
		try {
			if (!Files.exists(fichier)) {
				Files.createDirectories(plateforme.repertoire());
				try (InputStream in = Config.class.getResourceAsStream("/metrics.properties")) {
					if (in != null) {
						try (OutputStream out = Files.newOutputStream(fichier)) {
							in.transferTo(out);
						}
					}
				}
				plateforme.info("configuration écrite : " + fichier);
			}
			if (Files.exists(fichier)) {
				try (InputStream in = Files.newInputStream(fichier)) {
					c.props.load(in);
				}
			}
		} catch (IOException e) {
			plateforme.erreur("configuration illisible, valeurs par défaut utilisées", e);
		}
		return c;
	}

	/** Pour les tests, et pour une plateforme qui n'aurait pas de disque. */
	public static Config vide() {
		return new Config();
	}

	private String brut(String cle) {
		String env = System.getenv(PREFIXE_ENV + cle.replace('.', '_').toUpperCase(Locale.ROOT));
		return env != null && !env.isEmpty() ? env : props.getProperty(cle);
	}

	public String texte(String cle, String defaut) {
		String v = brut(cle);
		return v == null ? defaut : v.trim();
	}

	public int entier(String cle, int defaut) {
		String v = brut(cle);
		if (v == null) {
			return defaut;
		}
		try {
			return Integer.parseInt(v.trim());
		} catch (NumberFormatException e) {
			return defaut;
		}
	}

	public long duree(String cle, long defautSecondes) {
		return entier(cle, (int) defautSecondes);
	}

	public boolean actif(String cle, boolean defaut) {
		String v = brut(cle);
		return v == null ? defaut : Boolean.parseBoolean(v.trim());
	}

	/**
	 * Ce collecteur est-il activé ?
	 *
	 * <p>Tous le sont par défaut SAUF ceux qu'on sait coûteux — {@code packets} et {@code sql} —,
	 * qui doivent être demandés. Un exportateur qui ralentit le serveur dès l'installation ne
	 * serait jamais réinstallé.
	 */
	public boolean collecteurActif(String nom, boolean defaut) {
		return actif("collector." + nom, defaut);
	}
}
