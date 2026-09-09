package com.investorbook.common.aws;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.containers.localstack.LocalStackContainer.Service;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.client.builder.AwsClientBuilder.EndpointConfiguration;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;

/**
 * Exercises ProfilePictureStorage (from the `common` module) against a real
 * S3 API served by LocalStack. Lives here rather than in `common` itself
 * because `common` has no test infrastructure of its own yet and
 * member-service is its only consumer.
 */
@Testcontainers
class ProfilePictureStorageIntegrationTest {

	private static final String BUCKET = "test-profile-pics";

	@Container
	private static final LocalStackContainer LOCALSTACK = new LocalStackContainer(
			DockerImageName.parse("localstack/localstack:3.4.0")).withServices(Service.S3);

	private static AmazonS3 s3Client;
	private static ProfilePictureStorage storage;

	@BeforeAll
	static void setUpS3() {
		s3Client = AmazonS3ClientBuilder.standard()
				.withEndpointConfiguration(new EndpointConfiguration(
						LOCALSTACK.getEndpointOverride(Service.S3).toString(), LOCALSTACK.getRegion()))
				.withCredentials(new AWSStaticCredentialsProvider(
						new BasicAWSCredentials(LOCALSTACK.getAccessKey(), LOCALSTACK.getSecretKey())))
				.withPathStyleAccessEnabled(true)
				.build();
		s3Client.createBucket(BUCKET);
		storage = new ProfilePictureStorage(s3Client, BUCKET);
	}

	@Test
	void uploadThenGetPresignedUrl_roundTripsARealS3Object() throws IOException {
		MockMultipartFile file = new MockMultipartFile("file", "avatar.png", "image/png",
				"fake-image-bytes".getBytes(StandardCharsets.UTF_8));

		String key = storage.upload(file, "member-42");

		assertThat(key).isEqualTo("member-42.png");
		assertThat(s3Client.doesObjectExist(BUCKET, key)).isTrue();

		URL presignedUrl = storage.getPresignedUrl(key);

		assertThat(presignedUrl.toString()).contains(key);
	}

	@Test
	void upload_keepsOnlyASafeExtension_fromAnAttackerControlledFilename() throws IOException {
		MockMultipartFile file = new MockMultipartFile("file", "../../etc/passwd.jpg", "image/jpeg",
				"fake-image-bytes".getBytes(StandardCharsets.UTF_8));

		String key = storage.upload(file, "member-7");

		assertThat(key).isEqualTo("member-7.jpg");
		assertThat(s3Client.doesObjectExist(BUCKET, key)).isTrue();
	}
}
