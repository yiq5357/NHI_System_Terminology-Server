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

	 //處理正常回應
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
	            cleanOperationOutcome(outcome);
	            
	            writeResponse(requestDetails, responseDetails, servletResponse, outcome);
	            return false;
	        }
	        
	        return true;
	    }
	 
	 	//處理異常情況下的回應 - 新增這個 Hook
	    @Hook(Pointcut.SERVER_HANDLE_EXCEPTION)
	    public boolean handleException(
	            RequestDetails requestDetails,
	            BaseServerResponseException exception,
	            HttpServletRequest servletRequest,
	            HttpServletResponse servletResponse) throws IOException {
	        
	        // 只處理 $validate-code 操作
	        if (!"$validate-code".equals(requestDetails.getOperation())) {
	            return true;
	        }
	        
	        // 獲取異常中的 OperationOutcome
	        OperationOutcome outcome = (OperationOutcome) exception.getOperationOutcome();
	        
	        if (outcome != null) {
	            cleanResourceMetadata(outcome);
	            cleanOperationOutcome(outcome);
	            
	            // 創建 ResponseDetails
	            ResponseDetails responseDetails = new ResponseDetails();
	            responseDetails.setResponseResource(outcome);
	            responseDetails.setResponseCode(exception.getStatusCode());
	            
	            writeResponse(requestDetails, responseDetails, servletResponse, outcome);
	            return false; // 表示已處理異常，不需要繼續傳播
	        }
	        
	        return true; // 繼續正常的異常處理
	    }
	    
	    //統一的回應寫入方法
	    private void writeResponse(RequestDetails requestDetails, 
	                              ResponseDetails responseDetails,
	                              HttpServletResponse servletResponse,
	                              Resource resource) throws IOException {
	        
	    	// 強制清除所有可能的 narrative
	        forceCleanNarrative(resource);
	    	
	        IParser parser = requestDetails.getFhirContext()
	            .newJsonParser()
	            .setPrettyPrint(true)
	            .setSuppressNarratives(true);

	         // 明確指定不要編碼這些元素
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
	        // 使用正則表達式移除 text 欄位
	        // 匹配 "text": { ... } 或 "text": null
	        return json.replaceAll(",\\s*\"text\"\\s*:\\s*\\{[^}]*\\}", "")
	                   .replaceAll(",\\s*\"text\"\\s*:\\s*null", "")
	                   .replaceAll("\"text\"\\s*:\\s*\\{[^}]*\\}\\s*,", "")
	                   .replaceAll("\"text\"\\s*:\\s*null\\s*,", "")
				       // 處理開頭的情況
			           .replaceAll("\\{\\s*\"text\"\\s*:\\s*\\{[^}]*\\}\\s*,", "{")
			           .replaceAll("\\{\\s*\"text\"\\s*:\\s*null\\s*,", "{");
	    }
	    
	    private void forceCleanNarrative(Resource resource) {
	        if (resource == null) {
	            return;
	        }
	        
	        // 清除 meta
	        resource.setMeta(null);
	        resource.setId((String) null);
	        
	        // 清除 text
	        if (resource instanceof DomainResource) {
	            DomainResource domainResource = (DomainResource) resource;
	            domainResource.setText(null);
	            
	            // 嘗試通過反射徹底清除 text 元素
	            try {
	                java.lang.reflect.Field textField = DomainResource.class.getDeclaredField("text");
	                textField.setAccessible(true);
	                textField.set(domainResource, null);
	            } catch (Exception e) {
	                // 忽略反射錯誤
	            }
	        }
	        
	        // 如果是 OperationOutcome，清除 issue 中的相關內容
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
	        
	        // 清除每個 issue 中與 narrative 相關的 extensions
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
	                
	                // 特別處理 OperationOutcome
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
	                
	                // 特別處理 OperationOutcome
	                if (resource instanceof OperationOutcome) {
	                    cleanOperationOutcome((OperationOutcome) resource);
	                }
	            }
	            
	            // 繼續遞歸
	            if (part.hasPart()) {
	                removeNarrativesFromNestedParameters(part.getPart());
	            }
	        }
	    }
}