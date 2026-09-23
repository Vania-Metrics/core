package fr.samflix.vaniametrics.testkit;

import org.testcontainers.utility.DockerImageName;

/**
 * Every image the harness runs, pinned by digest.
 *
 * <p>A floating tag ({@code java21}, {@code latest}) changes under a test that did not change,
 * and then a red cell says nothing about the plugin. Bumping a digest is a deliberate commit:
 * {@code skopeo inspect --format '{{.Digest}}' docker://<image>:<tag>}.
 *
 * <p>itzg images come from ghcr.io and official images from AWS's public mirror of Docker Hub:
 * both answer anonymous pulls without Docker Hub's rate limit, which CI runners hit.
 */
public final class Images {

	private Images() {}

	/** itzg/minecraft-server, tag java21. */
	public static final DockerImageName SERVER_JAVA21 = DockerImageName.parse(
			"ghcr.io/itzg/minecraft-server@sha256:21e149dffd4c9fd9640baab2ed491f91a46ad51347dc7f169ea1be843b9fda0e");

	/** itzg/minecraft-server, tag java25: WorldEdit 7.4.3+ and ExcellentEconomy need it. */
	public static final DockerImageName SERVER_JAVA25 = DockerImageName.parse(
			"ghcr.io/itzg/minecraft-server@sha256:c8ace6eaf0e9c66a501b86e8a8e7080d8c3dbeb92d7d45b6e42aa2cab217a5c3");

	/** itzg/mc-proxy, tag java21. */
	public static final DockerImageName PROXY_JAVA21 = DockerImageName.parse(
			"ghcr.io/itzg/mc-proxy@sha256:b9db260e0e7081f7b06fa203e7473f05540f4c774b5df19ee8deeae744c17563");

	/** itzg/mc-proxy, tag java25: Velocity 4 needs it. */
	public static final DockerImageName PROXY_JAVA25 = DockerImageName.parse(
			"ghcr.io/itzg/mc-proxy@sha256:d06e5af9a545f90421202e6eebab04674068ed9a057ce61de5a4f83bca8f4b54");

	/** eclipse-temurin, tag 21-jre: for what itzg cannot run (Geyser Standalone). */
	public static final DockerImageName TEMURIN21 = DockerImageName.parse(
			"public.ecr.aws/docker/library/eclipse-temurin@sha256:49e21e16e3c86eb7816a44a67549910ed090fbeb40c29c525d58bf5e02e91b0f");

	/** node, tag 24-bookworm-slim: the bots. Debian, because raknet-native ships glibc builds. */
	public static final DockerImageName NODE24 = DockerImageName.parse(
			"public.ecr.aws/docker/library/node@sha256:0e0ff40c39bc087845bfb27465a0df4ea419520094bc35842ff83dd8cbe6f9b6");

	/** alpine, tag 3.22: the container runtime check. */
	public static final DockerImageName ALPINE = DockerImageName.parse(
			"public.ecr.aws/docker/library/alpine@sha256:5291449c3df73caf6ed85e649dec1b9e818b39a5d8c871e97afc13e9cd5e8fa8");
}
