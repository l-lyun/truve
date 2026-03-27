package com.truve.platform.musical.s3;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class S3Service {
	@Value("${s3.region}")
	private String region;
	@Value("${s3.bucket}")
	private String bucketName;

	public String getImageUrl(String fileName) {
		if (fileName == null || fileName.isEmpty()) {
			return null;
		}

		return String.format("https://%s.s3.%s.amazonaws.com/%s", bucketName, region, fileName);
	}
}
