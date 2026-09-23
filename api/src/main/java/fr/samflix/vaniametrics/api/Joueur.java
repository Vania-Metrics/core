package fr.samflix.vaniametrics.api;

/**
 * UN JOUEUR TEL QU'IL APPARAÎT DANS UNE MÉTRIQUE : son pseudonyme ET son identifiant.
 *
 * <p>LES DEUX, ET PAS L'UN OU L'AUTRE, parce qu'ils ne répondent pas à la même question. Le
 * pseudonyme est ce qu'on lit dans un tableau de bord, ce qu'on tape dans un filtre, ce qu'on
 * reconnaît. L'identifiant est ce qui ne change pas : un joueur qui se renomme reste le même, et
 * lui seul permet de recoller son histoire de part et d'autre du changement.
 *
 * <p>Concrètement, dans Grafana :
 *
 * <pre>
 *   mc_quest_player_tags{player="Thesam1798"}                     lisible, mais rompu par un renommage
 *   mc_quest_player_tags{uuid="f84c6a79-0a4e-45e0-879b-cd49ebd4c4e2"}   stable, mais illisible
 * </pre>
 *
 * <p>Les deux étiquettes côte à côte laissent choisir selon l'usage, et le coût est nul : elles
 * sont en correspondance exacte, donc les porter toutes les deux ne crée pas une seule série de
 * plus. Le seul cas où le compte augmente est justement le renommage — l'ancienne série s'arrête,
 * une nouvelle commence, et l'identifiant permet de les relier. C'est le comportement voulu, pas un
 * effet de bord.
 *
 * <p>L'ORDRE DES ÉTIQUETTES EST FIXE — {@code player} puis {@code uuid} — et vaut pour tous les
 * modules. Prometheus s'en moque, les humains non : une convention tenue partout est ce qui permet
 * de copier une requête d'un tableau de bord à l'autre sans la relire.
 *
 * <p>Cette classe ne connaît ni Bukkit ni Velocity : elle vit dans l'API, que les deux plateformes
 * partagent. C'est à l'adaptateur de construire le record depuis son propre type de joueur.
 *
 * @param uuid l'identifiant unique, forme canonique à tirets
 * @param nom le pseudonyme affiché
 */
public record Joueur(String uuid, String nom) {

	public Joueur {
		uuid = uuid == null ? "" : uuid;
		nom = nom == null ? "" : nom;
	}

	/**
	 * Le constructeur à employer partout : il fixe LA forme de l'identifiant.
	 *
	 * <p>{@code UUID.toString()} donne la forme à tirets. Elle n'a rien d'évident — Mojang expose
	 * aussi la forme compacte, sans tirets — et deux modules qui n'auraient pas choisi la même
	 * publieraient deux séries pour un seul joueur, sans que rien ne le signale. D'où ce point de
	 * passage unique.
	 *
	 * <p>{@code java.util.UUID} vient du JDK, pas de Bukkit : cette classe reste utilisable par
	 * l'adaptateur Velocity comme par les modules, qui ne voient que l'API.
	 */
	public static Joueur de(java.util.UUID identifiant, String nom) {
		return new Joueur(identifiant == null ? "" : identifiant.toString(), nom);
	}

	/**
	 * Les valeurs d'étiquettes, dans l'ordre de la convention.
	 *
	 * <p>À passer telle quelle aux instruments déclarés avec {@code "player", "uuid"}. Passer par
	 * cette méthode plutôt que d'écrire {@code (nom, uuid)} à la main sur chaque appel est ce qui
	 * garantit que l'ordre reste le même d'un module à l'autre — une inversion ne casserait rien
	 * de visible, elle rendrait seulement les tableaux de bord faux.
	 */
	public String[] etiquettes() {
		return new String[] {nom, uuid};
	}

	/** Les mêmes, suivies d'étiquettes propres à l'instrument. */
	public String[] etiquettes(String... suite) {
		String[] tout = new String[2 + suite.length];
		tout[0] = nom;
		tout[1] = uuid;
		System.arraycopy(suite, 0, tout, 2, suite.length);
		return tout;
	}
}
