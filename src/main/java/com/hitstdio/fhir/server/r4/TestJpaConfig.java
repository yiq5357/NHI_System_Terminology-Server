package com.hitstdio.fhir.server.r4;

import ca.uhn.fhir.jpa.subscription.match.deliver.email.IEmailSender;
import ca.uhn.fhir.jpa.util.LoggingEmailSender;
import org.springframework.context.annotation.Bean;

public class TestJpaConfig {
	@Bean
	public IEmailSender emailSender() {
		return new LoggingEmailSender();
	}
}
