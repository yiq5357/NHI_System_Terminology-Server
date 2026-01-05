package com.hitstdio.fhir.server.config;

import java.util.Arrays;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.context.FhirVersionEnum;
import ca.uhn.fhir.to.FhirTesterMvcConfig;
import ca.uhn.fhir.to.TesterConfig;

//@formatter:off
/**
 * This spring config file configures the web testing module. It serves two
 * purposes:
 * 1. It imports FhirTesterMvcConfig, which is the spring config for the
 *    tester itself
 * 2. It tells the tester which server(s) to talk to, via the testerConfig()
 *    method below
 */
@Configuration
@Import(FhirTesterMvcConfig.class)
public class FhirTesterConfig {
	
	private static final String SERVER_ID = DatabaseConfig.getFhirId();
	private static final String SERVER_BASEURL = DatabaseConfig.getFhirBaseUrl();
	private static final String SERVER_NAME = DatabaseConfig.getFhirName();

	/**
	 * This bean tells the testing webpage which servers it should configure itself
	 * to communicate with. In this example we configure it to talk to the local
	 * server, as well as one public server. If you are creating a project to 
	 * deploy somewhere else, you might choose to only put your own server's 
	 * address here.
	 */
	@Bean
	public TesterConfig testerConfig() {
		TesterConfig retVal = new TesterConfig();
		retVal
			.addServer()
				.withId(SERVER_ID)
				.withFhirVersion(FhirVersionEnum.R4)
				.withBaseUrl(SERVER_BASEURL)
				.withName(SERVER_NAME);

		/*
		 * Use the method below to supply a client "factory" which can be used 
		 * if your server requires authentication
		 */
		// retVal.setClientFactory(clientFactory);
		
		return retVal;
	}
	
	 @Bean
	    public FhirContext fhirContext() {
	        FhirContext ctx = FhirContext.forR4();
	        
	        // 禁用 narrative 生成器
	        ctx.setNarrativeGenerator(null);
	        
	        // 全局禁用 narrative 生成
	        ctx.getParserOptions().setDontStripVersionsFromReferencesAtPaths(Arrays.asList("none"));
	        ctx.getParserOptions().setStripVersionsFromReferences(false);
	        
	        return ctx;
	    }

}
//@formatter:on
