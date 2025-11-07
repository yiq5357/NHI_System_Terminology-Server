package com.hitstdio.fhir.server.interceptor;

import java.io.IOException;
import java.util.List;
import java.util.Set;

import org.hl7.fhir.r4.model.DomainResource;
import org.hl7.fhir.r4.model.OperationOutcome;
import org.hl7.fhir.r4.model.Parameters;
import org.hl7.fhir.r4.model.Parameters.ParametersParameterComponent;
import org.hl7.fhir.r4.model.Resource;
import org.springframework.stereotype.Component;

import ca.uhn.fhir.interceptor.api.Hook;
import ca.uhn.fhir.interceptor.api.Interceptor;
import ca.uhn.fhir.interceptor.api.Pointcut;
import ca.uhn.fhir.parser.IParser;
import ca.uhn.fhir.rest.api.server.RequestDetails;
import ca.uhn.fhir.rest.api.server.ResponseDetails;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
@Interceptor
public class ValidateCodeNarrativeSuppressionInterceptor {

	 @Hook(Pointcut.SERVER_OUTGOING_RESPONSE)
	    public boolean suppressNarrativeForValidateCode(
	            RequestDetails requestDetails,
	            ResponseDetails responseDetails,
	            HttpServletRequest servletRequest,
	            HttpServletResponse servletResponse) throws IOException {
	        
	        // 只處理 $validate-code 操作
	        if (!"$validate-code".equals(requestDetails.getOperation())) {
	            return true;
	        }
	        
	        Object responseObject = responseDetails.getResponseResource();
	        
	        // 處理 Parameters 回應
	        if (responseObject instanceof Parameters) {
	            Parameters parameters = (Parameters) responseObject;
	            cleanResourceMetadata(parameters);
	            removeNarrativesFromParameters(parameters);
	            
	            writeResponse(requestDetails, responseDetails, servletResponse, parameters);
	            return false;
	        }
	        
	        // 處理 OperationOutcome 回應
	        if (responseObject instanceof OperationOutcome) {
	            OperationOutcome outcome = (OperationOutcome) responseObject;
	            cleanResourceMetadata(outcome);
	            
	            writeResponse(requestDetails, responseDetails, servletResponse, outcome);
	            return false;
	        }
	        
	        return true;
	    }
	    
	    /**
	     * 統一的回應寫入方法
	     */
	    private void writeResponse(RequestDetails requestDetails, 
	                              ResponseDetails responseDetails,
	                              HttpServletResponse servletResponse,
	                              Resource resource) throws IOException {
	        
	        IParser parser = requestDetails.getFhirContext()
	            .newJsonParser()
	            .setPrettyPrint(true)
	            .setSuppressNarratives(true)
	            .setDontEncodeElements(Set.of("*.meta", "*.text"));
	        
	        String encoded = parser.encodeResourceToString(resource);
	        
	        servletResponse.setContentType("application/fhir+json;charset=UTF-8");
	        servletResponse.setStatus(responseDetails.getResponseCode());
	        servletResponse.getWriter().write(encoded);
	    }
	    
	    private void cleanResourceMetadata(Resource resource) {
	        if (resource == null) {
	            return;
	        }
	        
	        resource.setMeta(null);
	        
	        if (resource instanceof DomainResource) {
	            ((DomainResource) resource).setText(null);
	        }
	    }
	    
	    private void removeNarrativesFromParameters(Parameters parameters) {
	        if (parameters == null) {
	            return;
	        }
	        
	        for (ParametersParameterComponent param : parameters.getParameter()) {
	            if (param.hasResource()) {
	                Resource resource = param.getResource();
	                cleanResourceMetadata(resource);
	            }
	            
	            if (param.hasPart()) {
	                removeNarrativesFromNestedParameters(param.getPart());
	            }
	        }
	    }
	    
	    private void removeNarrativesFromNestedParameters(List<ParametersParameterComponent> parts) {
	        for (ParametersParameterComponent part : parts) {
	            if (part.hasResource()) {
	                cleanResourceMetadata(part.getResource());
	            }
	            
	            if (part.hasPart()) {
	                removeNarrativesFromNestedParameters(part.getPart());
	            }
	        }
	    }
}