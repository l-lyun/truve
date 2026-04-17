package org.truve.platform.ticketing.service.booking.risk.dto;

import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class BeBotRiskReportRequest {

	@JsonProperty("X-User-Id")
	private UUID userId;

	@JsonProperty("orderId")
	private String orderId;

	@JsonProperty("label")
	private String label;

	@JsonProperty("bot_score")
	private Double botScore;

	@JsonProperty("model_type")
	private String modelType;

	@JsonProperty("model_name")
	private String modelName;

	@JsonProperty("policy_action")
	private String policyAction;

	@JsonProperty("risk_count")
	private Integer riskCount;

	public boolean isBot() {
		return label != null && "bot".equalsIgnoreCase(label);
	}
}
