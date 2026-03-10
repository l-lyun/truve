package com.truve.platform.musical.s3;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class S3Service {
	@Value("${spring.cloud.aws.s3.endpoint}")
	private String endpoint;
	@Value("${spring.cloud.aws.s3.bucket}")
	private String bucketName;

	public String getImageUrl(String fileName) {
		return String.format("%s/%s/%s", endpoint, bucketName, fileName);
	}
}
