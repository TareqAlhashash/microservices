package com.investorbook.common.aws;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URL;
import java.util.Date;

import org.springframework.web.multipart.MultipartFile;

import com.amazonaws.regions.Regions;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.model.GeneratePresignedUrlRequest;
import com.amazonaws.services.s3.model.PutObjectRequest;

public class S3Utils {
	public static AmazonS3 s3Client = AmazonS3ClientBuilder.standard().withRegion(Regions.US_EAST_1).build();
	public static final String S3_PROFILE_PIC_BUCKET = "investerbook-profile-pic";
	public static final String S3_URL_PREFIX = "https://s3.amazonaws.com/";
	public static final long DEFAULT_EXPIRY = 5 * 60;

	// TODO stream directly to s3 dont save to local file
	public static String uploadProfilePic(MultipartFile file, String id) throws IOException {
		String fileName = id + file.getOriginalFilename().substring(file.getOriginalFilename().lastIndexOf("."));
		File tempFile = convertMultiPartToFile(file, fileName);

		try {
			s3Client.putObject(new PutObjectRequest(S3_PROFILE_PIC_BUCKET, fileName, tempFile));
		} finally {
			if (tempFile.exists()) {
				tempFile.delete();
			}
		}

		return S3_URL_PREFIX + S3_PROFILE_PIC_BUCKET + "/" + fileName;
	}

	// to be changed
	private static File convertMultiPartToFile(MultipartFile file, String fileName) throws IOException {
		File convFile = new File(fileName);
		FileOutputStream fos = new FileOutputStream(convFile);
		fos.write(file.getBytes());
		fos.close();
		return convFile;
	}

	public static URL getPresignedUrl(String url) {

		return getPresignedUrl(url, DEFAULT_EXPIRY);
	}

	public static URL getPresignedUrl(String url, long expirationInSeconds) {
		GeneratePresignedUrlRequest generatePresignedUrlRequest = new GeneratePresignedUrlRequest(S3_PROFILE_PIC_BUCKET,
				url);
		Date expiration = new java.util.Date();
		long expirationInMs = expiration.getTime();
		expirationInMs += 1000 * expirationInSeconds;
		expiration.setTime(expirationInMs);
		generatePresignedUrlRequest.setExpiration(expiration);
		return s3Client.generatePresignedUrl(generatePresignedUrlRequest);
	}

	// public static void main(String[] args) {
	// System.out.println(s3Client.listBuckets());
	// }
}
