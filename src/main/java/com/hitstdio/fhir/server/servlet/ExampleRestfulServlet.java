package com.hitstdio.fhir.server.servlet;

import java.io.IOException;
import java.util.List;

import org.hl7.fhir.common.hapi.validation.support.CommonCodeSystemsTerminologyService;
import org.hl7.fhir.common.hapi.validation.support.InMemoryTerminologyServerValidationSupport;
import org.hl7.fhir.common.hapi.validation.support.NpmPackageValidationSupport;
import org.hl7.fhir.common.hapi.validation.support.SnapshotGeneratingValidationSupport;
import org.hl7.fhir.common.hapi.validation.support.ValidationSupportChain;
import org.hl7.fhir.r4.model.TerminologyCapabilities;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Import;

import com.hitstdio.fhir.server.provider.TerminologyCapabilitiesResourceProvider;
import com.hitstdio.fhir.server.r4.TestServerR4AppCtx;

import ca.uhn.fhir.batch2.jobs.config.Batch2JobsConfig;
import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.context.support.DefaultProfileValidationSupport;
import ca.uhn.fhir.jpa.api.dao.DaoRegistry;
import ca.uhn.fhir.jpa.batch2.JpaBatch2Config;
import ca.uhn.fhir.narrative.DefaultThymeleafNarrativeGenerator;
import ca.uhn.fhir.narrative.INarrativeGenerator;
import ca.uhn.fhir.parser.IParser;
import ca.uhn.fhir.rest.api.EncodingEnum;
import ca.uhn.fhir.rest.api.server.RequestDetails;
import ca.uhn.fhir.rest.server.IResourceProvider;
import ca.uhn.fhir.rest.server.RestfulServer;
import ca.uhn.fhir.rest.server.exceptions.AuthenticationException;
import ca.uhn.fhir.rest.server.interceptor.InterceptorAdapter;
import ca.uhn.fhir.rest.server.interceptor.LoggingInterceptor;
import ca.uhn.fhir.rest.server.interceptor.ResponseHighlighterInterceptor;
import ca.uhn.fhir.rest.server.interceptor.StaticCapabilityStatementInterceptor;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;


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
        
        validationSupportChain = new ValidationSupportChain(
                new DefaultProfileValidationSupport(ctx),
                new InMemoryTerminologyServerValidationSupport(ctx),
                new CommonCodeSystemsTerminologyService(ctx),
                new SnapshotGeneratingValidationSupport(ctx)
                //new RemoteTerminologyServiceValidationSupport(ctx, "http://localhost/r4/")
        );
        Class<?> configClass = TestServerR4AppCtx.class;
        AnnotationConfigApplicationContext appCtx = new AnnotationConfigApplicationContext(configClass);
		List<IResourceProvider> providers = appCtx.getBean("resourceProviders", List.class);
		
		
		StaticCapabilityStatementInterceptor interceptor = new StaticCapabilityStatementInterceptor();
	    interceptor.setCapabilityStatementResource("classpath:capabilitystatement.json");
	    registerInterceptor(interceptor);
		
		
		// 注册 TerminologyCapabilitiesResourceProvider
        TerminologyCapabilitiesResourceProvider terminologyProvider = new TerminologyCapabilitiesResourceProvider(appCtx.getBean(DaoRegistry.class));
        providers.add(terminologyProvider);
		
		setResourceProviders(providers);
		setDefaultResponseEncoding(EncodingEnum.JSON);		
		
		// 添加自定义拦截器来处理 /metadata?mode=terminology 请求
        registerInterceptor(new CapabilityStatementInterceptor(terminologyProvider));

		// 禁用 NarrativeGenerator 以避免自動生成 narrative text
		// INarrativeGenerator narrativeGen = new DefaultThymeleafNarrativeGenerator();
		// getFhirContext().setNarrativeGenerator(narrativeGen);

		registerInterceptor(new ResponseHighlighterInterceptor());        
        
		//setServerConformanceProvider(new TerminologyCapabilitiesResourceProvider(getFhirContext(), this, theDaoRegistry));
		
		//setServerConformanceProvider(new CapabilityStatementResourceProvider(getFhirContext(), this, mySystemDao));
		
		/*StaticCapabilityStatementInterceptor staticCapabilityStatementInterceptor = new StaticCapabilityStatementInterceptor();
		staticCapabilityStatementInterceptor.setCapabilityStatementResource("classpath:capabilitystatement.json");
	    registerInterceptor(staticCapabilityStatementInterceptor);*/
	  
		/*OpenApiInterceptor openApiInterceptor = new OpenApiInterceptor();
	    registerInterceptor(openApiInterceptor);*/
	    
		LoggingInterceptor loggingInterceptor = new LoggingInterceptor();
		loggingInterceptor.setLoggerName("fhir.access");
		loggingInterceptor.setMessageFormat(
		    "Path[${servletPath}] " +
		    "Source[${requestHeader.x-forwarded-for}] " +
		    "Operation[${operationType} ${idOrResourceName}] " +
		    "UA[${requestHeader.user-agent}] " +
		    "Params[${requestParameters}] " +
		    "RequestBody[${requestBodyFhir}] " +
		    "ResponseCode[${responseCode}] " +
		    "ProcessingTime[${processingTimeMillis}ms]"
		);
		registerInterceptor(loggingInterceptor);
	}
	 // 创建自定义拦截器来处理 /metadata 请求
    private class CapabilityStatementInterceptor extends InterceptorAdapter {
        private final TerminologyCapabilitiesResourceProvider terminologyProvider;
        
        public CapabilityStatementInterceptor(TerminologyCapabilitiesResourceProvider terminologyProvider) {
            this.terminologyProvider = terminologyProvider;
        }
        
        @Override
        public boolean incomingRequestPostProcessed(RequestDetails theRequestDetails, HttpServletRequest theRequest, HttpServletResponse theResponse) throws AuthenticationException {
            String requestPath = theRequestDetails.getRequestPath();
            
            String modeValue = null;
            String[] modeParam = theRequestDetails.getParameters().get("mode");
            if (modeParam != null && modeParam.length > 0) {
                modeValue = modeParam[0];
            }
            
            if ("metadata".equals(requestPath) && modeValue != null && "terminology".equals(modeValue)) {
                try {
                    // 调用 TerminologyCapabilitiesResourceProvider 的方法获取 TerminologyCapabilities
                    TerminologyCapabilities terminologyCapabilities = terminologyProvider.getTerminologyCapabilities("terminology", theRequestDetails);
                    
                    if (terminologyCapabilities != null) {
                        IParser parser;
                        if (theRequestDetails.getParameters().containsKey("_format")) {
                            String[] formatParam = theRequestDetails.getParameters().get("_format");
                            if (formatParam != null && formatParam.length > 0 && formatParam[0].contains("xml")) {
                                parser = getFhirContext().newXmlParser();
                            } else {
                                parser = getFhirContext().newJsonParser();
                            }
                        } else {
                            parser = getFhirContext().newJsonParser();
                        }
                        
                        parser.setPrettyPrint(true);
                        String responseContent = parser.encodeResourceToString(terminologyCapabilities);
                        
                        theResponse.setStatus(200);
	                    theResponse.setContentType("application/fhir+json; charset=UTF-8");
                        theResponse.getWriter().write(responseContent);
                        theResponse.getWriter().close();
                        
                        // 返回 true 表示请求已处理，无需进一步处理
                        return false;
                    }
                } catch (Exception e) {
                    // 处理异常
                    //ourLog.error("Error handling terminology capabilities request", e);
                }
            }
            
            // 返回 true 表示继续处理请求
            return true;
        }
    }
}
