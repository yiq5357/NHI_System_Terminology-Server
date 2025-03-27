package com.hitstdio.fhir.server.servlet;

import java.io.IOException;
import java.util.List;

import org.hl7.fhir.common.hapi.validation.support.CommonCodeSystemsTerminologyService;
import org.hl7.fhir.common.hapi.validation.support.InMemoryTerminologyServerValidationSupport;
import org.hl7.fhir.common.hapi.validation.support.NpmPackageValidationSupport;
import org.hl7.fhir.common.hapi.validation.support.SnapshotGeneratingValidationSupport;
import org.hl7.fhir.common.hapi.validation.support.ValidationSupportChain;
import org.hl7.fhir.r4.model.CapabilityStatement;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Import;

import com.hitstdio.fhir.server.provider.CodeSystemResourceProvider;
import com.hitstdio.fhir.server.provider.TerminologyCapabilitiesResourceProvider;
import com.hitstdio.fhir.server.r4.TestServerR4AppCtx;

import ca.uhn.fhir.batch2.jobs.config.Batch2JobsConfig;
import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.context.support.DefaultProfileValidationSupport;
import ca.uhn.fhir.jpa.batch2.JpaBatch2Config;
import ca.uhn.fhir.narrative.DefaultThymeleafNarrativeGenerator;
import ca.uhn.fhir.narrative.INarrativeGenerator;
import ca.uhn.fhir.rest.api.EncodingEnum;
import ca.uhn.fhir.rest.server.IResourceProvider;
import ca.uhn.fhir.rest.server.IServerConformanceProvider;
import ca.uhn.fhir.rest.server.RestfulServer;
import ca.uhn.fhir.rest.server.interceptor.ResponseHighlighterInterceptor;
import ca.uhn.fhir.rest.server.interceptor.StaticCapabilityStatementInterceptor;
import ca.uhn.fhir.rest.server.provider.ServerCapabilityStatementProvider;


/**
 * This servlet is the actual FHIR server itself
 */
@Import({
	JpaBatch2Config.class,
	Batch2JobsConfig.class
})
public final class ExampleRestfulServlet extends RestfulServer {	
	private static final long serialVersionUID = 1L;
	public static ValidationSupportChain validationSupportChain;
	/**
	 * Constructor
	 */
	public ExampleRestfulServlet() {
		super(FhirContext.forR4Cached()); // This is an R4 server
	}
	
	/**
	 * This method is called automatically when the
	 * servlet is initializing.
	 */
	@Override
	public void initialize() {
		FhirContext ctx = getFhirContext();
		
		NpmPackageValidationSupport npmPackageSupport = new NpmPackageValidationSupport(ctx);
        try {
            npmPackageSupport.loadPackageFromClasspath("classpath:package/tw.gov.mohw.twcore.tgz");
            npmPackageSupport.loadPackageFromClasspath("classpath:package/tw.gov.mohw.cdc.twiam.tgz");
        } catch (IOException e) {
            throw new RuntimeException("Failed to load NPM packages", e);
        }
        
        validationSupportChain = new ValidationSupportChain(
                npmPackageSupport,
                new DefaultProfileValidationSupport(ctx),
                new InMemoryTerminologyServerValidationSupport(ctx),
                new CommonCodeSystemsTerminologyService(ctx),
                new SnapshotGeneratingValidationSupport(ctx)
                //new RemoteTerminologyServiceValidationSupport(ctx, "http://localhost/r4/")
        );
        Class<?> configClass = TestServerR4AppCtx.class;
        AnnotationConfigApplicationContext appCtx = new AnnotationConfigApplicationContext(configClass);
		List<IResourceProvider> providers = appCtx.getBean("resourceProviders", List.class);
		setResourceProviders(providers);
		setDefaultResponseEncoding(EncodingEnum.JSON);		

		INarrativeGenerator narrativeGen = new DefaultThymeleafNarrativeGenerator();
		getFhirContext().setNarrativeGenerator(narrativeGen);

		registerInterceptor(new ResponseHighlighterInterceptor());
		
		
		//setServerConformanceProvider(new TerminologyCapabilitiesResourceProvider(getFhirContext(), this, theDaoRegistry));
		
		//setServerConformanceProvider(new CapabilityStatementResourceProvider(getFhirContext(), this, mySystemDao));
		
		/*StaticCapabilityStatementInterceptor staticCapabilityStatementInterceptor = new StaticCapabilityStatementInterceptor();
		staticCapabilityStatementInterceptor.setCapabilityStatementResource("classpath:capabilitystatement.json");
	    registerInterceptor(staticCapabilityStatementInterceptor);*/
	  
		/*OpenApiInterceptor openApiInterceptor = new OpenApiInterceptor();
	    registerInterceptor(openApiInterceptor);*/
	    
	}

}
