package com.investorbook.common.aws;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.util.Date;
import java.util.Objects;
import java.util.regex.Pattern;

import org.springframework.web.multipart.MultipartFile;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.GeneratePresignedUrlRequest;
import com.amazonaws.services.s3.model.PutObjectRequest;

/**
 * Stores and retrieves member profile pictures in S3. Constructor-injected so
 * it can be pointed at a real bucket or, in tests, at a LocalStack endpoint.
 */
public class ProfilePictureStorage {

	private static final long DEFAULT_EXPIRY_SECONDS = 5 * 60;

	// original filename comes from the client; only trust a short alphanumeric
	// extension out of it, never the rest of the name, to avoid writing outside
	// the intended temp location or storing under an attacker-chosen S3 key.
	private static final Pattern SAFE_EXTENSION = Pattern.compile("\\.[A-Za-z0-9]{1,10}");

	private final AmazonS3 s3Client;
	private final String bucket;

	public ProfilePictureStorage(AmazonS3 s3Client, String bucket) {
		this.s3Client = Objects.requireNonNull(s3Client, "s3Client must not be null");
		this.bucket = Objects.requireNonNull(bucket, "bucket must not be null");
	}

	/**
	 * @return the S3 object key the picture was stored under
	 */
	public String upload(MultipartFile file, String memberId) throws IOException {
		Objects.requireNonNull(file, "file must not be null");
		Objects.requireNonNull(memberId, "memberId must not be null");

		String key = memberId + safeExtensionOf(file.getOriginalFilename());
		File tempFile = Files.createTempFile("profile-pic-", ".upload").toFile();
		try {
			file.transferTo(tempFile);
			s3Client.putObject(new PutObjectRequest(bucket, key, tempFile));
		} finally {
			tempFile.delete();
		}
		return key;
	}

	public URL getPresignedUrl(String key) {
		return getPresignedUrl(key, DEFAULT_EXPIRY_SECONDS);
	}

	public URL getPresignedUrl(String key, long expirationInSeconds) {
		Objects.requireNonNull(key, "key must not be null");
		GeneratePresignedUrlRequest request = new GeneratePresignedUrlRequest(bucket, key);
		request.setExpiration(new Date(System.currentTimeMillis() + expirationInSeconds * 1000));
		return s3Client.generatePresignedUrl(request);
	}

	private static String safeExtensionOf(String originalFilename) {
		if (originalFilename == null) {
			return "";
		}
		int dotIndex = originalFilename.lastIndexOf('.');
		if (dotIndex < 0) {
			return "";
		}
		String extension = originalFilename.substring(dotIndex);
		return SAFE_EXTENSION.matcher(extension).matches() ? extension : "";
	}
}
