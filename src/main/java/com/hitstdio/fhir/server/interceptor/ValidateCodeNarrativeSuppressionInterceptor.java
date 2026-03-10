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
import ca.uhn.fhir.rest.server.exceptions.BaseServerResponseException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
@Interceptor
public class ValidateCodeNarrativeSuppressionInterceptor {

	 // Handle normal response
	 @Hook(Pointcut.SERVER_OUTGOING_RESPONSE)
	    public boolean suppressNarrativeForValidateCode(
	            RequestDetails requestDetails,
	            ResponseDetails responseDetails,
	            HttpServletRequest servletRequest,
	            HttpServletResponse servletResponse) throws IOException {

	        // Only handle $validate-code operation
	        if (!"$validate-code".equals(requestDetails.getOperation())) {
	            return true;
	        }

	        Object responseObject = responseDetails.getResponseResource();

	        // Handle Parameters response
	        if (responseObject instanceof Parameters) {
	            Parameters parameters = (Parameters) responseObject;
	            cleanResourceMetadata(parameters);
	            removeNarrativesFromParameters(parameters);

	            writeResponse(requestDetails, responseDetails, servletResponse, parameters);
	            return false;
	        }

	        // Handle OperationOutcome response
	        if (responseObject instanceof OperationOutcome) {
	            OperationOutcome outcome = (OperationOutcome) responseObject;
	            cleanResourceMetadata(outcome);
	            cleanOperationOutcome(outcome);

	            writeResponse(requestDetails, responseDetails, servletResponse, outcome);
	            return false;
	        }

	        return true;
	    }

	 	// Handle exception response
	    @Hook(Pointcut.SERVER_HANDLE_EXCEPTION)
	    public boolean handleException(
	            RequestDetails requestDetails,
	            BaseServerResponseException exception,
	            HttpServletRequest servletRequest,
	            HttpServletResponse servletResponse) throws IOException {

	        // Only handle $validate-code operation
	        if (!"$validate-code".equals(requestDetails.getOperation())) {
	            return true;
	        }

	        // Get OperationOutcome from exception
	        OperationOutcome outcome = (OperationOutcome) exception.getOperationOutcome();

	        if (outcome != null) {
	            cleanResourceMetadata(outcome);
	            cleanOperationOutcome(outcome);

	            // Create ResponseDetails
	            ResponseDetails responseDetails = new ResponseDetails();
	            responseDetails.setResponseResource(outcome);
	            responseDetails.setResponseCode(exception.getStatusCode());

	            writeResponse(requestDetails, responseDetails, servletResponse, outcome);
	            return false; // Exception handled, do not propagate
	        }

	        return true; // Continue normal exception handling
	    }

	    // Unified response write method
	    private void writeResponse(RequestDetails requestDetails,
	                              ResponseDetails responseDetails,
	                              HttpServletResponse servletResponse,
	                              Resource resource) throws IOException {

	    	// Force clean all possible narrative
	        forceCleanNarrative(resource);

	        IParser parser = requestDetails.getFhirContext()
	            .newJsonParser()
	            .setPrettyPrint(true)
	            .setSuppressNarratives(true);

	         // Explicitly exclude these elements from encoding
	            parser.setDontEncodeElements(Set.of(
	                "*.meta",
	                "*.text",
	                "*.id",
	                "*.text.status",
	                "*.text.div"
	            ));

	        String encoded = parser.encodeResourceToString(resource);

	        encoded = removeTextFromJson(encoded);

	        servletResponse.setContentType("application/fhir+json;charset=UTF-8");
	        servletResponse.setStatus(responseDetails.getResponseCode());
	        servletResponse.getWriter().write(encoded);
	        servletResponse.getWriter().flush();
	    }

	    private String removeTextFromJson(String json) {
	        // Use regex to remove text field: "text": { ... } or "text": null
	        return json.replaceAll(",\\s*\"text\"\\s*:\\s*\\{[^}]*\\}", "")
	                   .replaceAll(",\\s*\"text\"\\s*:\\s*null", "")
	                   .replaceAll("\"text\"\\s*:\\s*\\{[^}]*\\}\\s*,", "")
	                   .replaceAll("\"text\"\\s*:\\s*null\\s*,", "")
				       // Handle leading text
			           .replaceAll("\\{\\s*\"text\"\\s*:\\s*\\{[^}]*\\}\\s*,", "{")
			           .replaceAll("\\{\\s*\"text\"\\s*:\\s*null\\s*,", "{");
	    }

	    private void forceCleanNarrative(Resource resource) {
	        if (resource == null) {
	            return;
	        }

	        // Clear meta
	        resource.setMeta(null);
	        resource.setId((String) null);

	        // Clear text
	        if (resource instanceof DomainResource) {
	            DomainResource domainResource = (DomainResource) resource;
	            domainResource.setText(null);

	            // Clear text element via reflection
	            try {
	                java.lang.reflect.Field textField = DomainResource.class.getDeclaredField("text");
	                textField.setAccessible(true);
	                textField.set(domainResource, null);
	            } catch (Exception e) {
	                // Ignore reflection errors
	            }
	        }

	        // If OperationOutcome, clean issue-related content
	        if (resource instanceof OperationOutcome) {
	            cleanOperationOutcome((OperationOutcome) resource);
	        }
	    }

	    private void cleanResourceMetadata(Resource resource) {
	        if (resource == null) {
	            return;
	        }

	        resource.setMeta(null);
	        resource.setId((String) null);

	        if (resource instanceof DomainResource) {
	            ((DomainResource) resource).setText(null);
	        }
	    }

	    private void cleanOperationOutcome(OperationOutcome outcome) {
	        if (outcome == null || !outcome.hasIssue()) {
	            return;
	        }

	        // Remove narrative-related extensions from each issue
	        for (OperationOutcome.OperationOutcomeIssueComponent issue : outcome.getIssue()) {
	            if (issue.hasExtension()) {
	                issue.getExtension().removeIf(ext -> {
	                    String url = ext.getUrl();
	                    return url != null && (
	                        url.contains("narrativeLink") ||
	                        url.contains("rendering") ||
	                        url.equals("http://hl7.org/fhir/StructureDefinition/narrativeLink")
	                    );
	                });
	            }
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

	                // Special handling for OperationOutcome
	                if (resource instanceof OperationOutcome) {
	                    cleanOperationOutcome((OperationOutcome) resource);
	                }
	            }

	            if (param.hasPart()) {
	                removeNarrativesFromNestedParameters(param.getPart());
	            }
	        }
	    }

	    private void removeNarrativesFromNestedParameters(List<ParametersParameterComponent> parts) {
	    	for (ParametersParameterComponent part : parts) {
	            if (part.hasResource()) {
	                Resource resource = part.getResource();
	                cleanResourceMetadata(resource);

	                // Special handling for OperationOutcome
	                if (resource instanceof OperationOutcome) {
	                    cleanOperationOutcome((OperationOutcome) resource);
	                }
	            }

	            // Recurse into nested parts
	            if (part.hasPart()) {
	                removeNarrativesFromNestedParameters(part.getPart());
	            }
	        }
	    }
}
