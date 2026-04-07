package com.truve.platform.apigateway.logging;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/telemetry")
public class TelemetryController {

	@PostMapping
	public ResponseEntity<Void> receive() {
		return ResponseEntity.ok().build();
	}
}