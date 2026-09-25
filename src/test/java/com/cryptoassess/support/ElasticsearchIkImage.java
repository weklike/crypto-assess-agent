package com.cryptoassess.support;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.stream.Stream;

import org.testcontainers.elasticsearch.ElasticsearchContainer;
import org.testcontainers.images.builder.ImageFromDockerfile;
import org.testcontainers.utility.DockerImageName;

/**
 * 集成测试使用的 ES + IK 镜像：和 compose 共用 deploy/elasticsearch/Dockerfile。
 * 镜像标签带上构建目录的内容哈希，改了 Dockerfile 或词典会自动重建。
 */
public final class ElasticsearchIkImage {

	private static final Path BUILD_CONTEXT = Path.of("deploy/elasticsearch");

	private static final String ES_VERSION = "9.4.5";

	private static volatile DockerImageName resolved;

	private ElasticsearchIkImage() {
	}

	public static synchronized DockerImageName resolve() {
		if (resolved == null) {
			String name = "crypto-assess/elasticsearch-ik:" + ES_VERSION + "-" + contentHash();
			String built = new ImageFromDockerfile(name, false).withFileFromPath(".", BUILD_CONTEXT).get();
			resolved = DockerImageName.parse(built)
				.asCompatibleSubstituteFor("docker.elastic.co/elasticsearch/elasticsearch");
		}
		return resolved;
	}

	public static ElasticsearchContainer newContainer() {
		return new ElasticsearchContainer(resolve()).withEnv("xpack.security.enabled", "false")
			.withEnv("ES_JAVA_OPTS", "-Xms512m -Xmx512m")
			.withCertPath("");
	}

	private static String contentHash() {
		try (Stream<Path> files = Files.walk(BUILD_CONTEXT)) {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			List<Path> sorted = files.filter(Files::isRegularFile).sorted().toList();
			for (Path file : sorted) {
				digest.update(BUILD_CONTEXT.relativize(file).toString().getBytes());
				digest.update(Files.readAllBytes(file));
			}
			return HexFormat.of().formatHex(digest.digest()).substring(0, 12);
		}
		catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
		catch (NoSuchAlgorithmException ex) {
			throw new IllegalStateException(ex);
		}
	}

}
