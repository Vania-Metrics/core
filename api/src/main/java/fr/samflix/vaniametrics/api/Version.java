package fr.samflix.vaniametrics.api;

/**
 * LA VERSION, ET LE SEUL ENDROIT OÙ ELLE EST ÉCRITE.
 *
 * <p>Elle sert à quatre choses, et c'est pour ça qu'elle vit dans l'API plutôt que dans le noyau :
 * l'étiquette de {@code mc_build_info}, l'annotation {@code @Plugin} de chaque entrée Velocity —
 * qui exige une CONSTANTE de compilation, d'où {@code static final} —, le {@code plugin.yml} de
 * chaque entrée Bukkit, et le nom des jars. {@code build.sh} la lit ici ; rien ne la recopie.
 *
 * <p>Noyau et modules avancent ensemble tant qu'ils sont construits d'un seul jet. Le jour où un
 * module part dans son propre dépôt, il prendra sa version à lui et déclarera celle de l'API dont
 * il a besoin — c'est précisément le rôle d'un artefact d'API versionné.
 */
public final class Version {

	public static final String VALEUR = "0.2.2";

	private Version() {}
}
