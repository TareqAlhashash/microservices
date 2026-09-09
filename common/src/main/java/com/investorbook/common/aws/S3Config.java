package com.investorbook.common.aws;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;

/**
 * Lives in {@code common} rather than being component-scanned: consuming
 * services sit in a different base package, so this must be pulled in
 * explicitly with {@code @Import(S3Config.class)} on the application class.
 */
@Configuration
public class S3Config {

	@Bean
	public AmazonS3 amazonS3(@Value("${investorbook.aws.s3.region:us-east-1}") String region) {
		return AmazonS3ClientBuilder.standard().withRegion(region).build();
	}

	@Bean
	public ProfilePictureStorage profilePictureStorage(AmazonS3 amazonS3,
			@Value("${investorbook.aws.s3.profile-pic-bucket:investorbook-profile-pic}") String bucket) {
		return new ProfilePictureStorage(amazonS3, bucket);
	}
}
