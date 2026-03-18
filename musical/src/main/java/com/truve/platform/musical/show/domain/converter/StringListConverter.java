package com.truve.platform.musical.show.domain.converter;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.util.StringUtils;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter
public class StringListConverter implements AttributeConverter<List<String>, String> {

	@Override
	public String convertToDatabaseColumn(List<String> attribute) {
		if (attribute == null || attribute.isEmpty()) {
			return null;
		}

		List<String> sanitized = attribute.stream()
			.map(value -> value == null ? "" : value.trim())
			.filter(StringUtils::hasText)
			.toList();
		if (sanitized.isEmpty()) {
			return null;
		}
		return String.join(",", sanitized);
	}

	@Override
	public List<String> convertToEntityAttribute(String dbData) {
		if (!StringUtils.hasText(dbData)) {
			return Collections.emptyList();
		}

		return Arrays.stream(dbData.split(","))
			.map(String::trim)
			.filter(StringUtils::hasText)
			.collect(Collectors.toList());
	}
}
