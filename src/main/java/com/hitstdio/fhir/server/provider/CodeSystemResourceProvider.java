package com.hitstdio.fhir.server.provider;

import ca.uhn.fhir.jpa.api.dao.DaoRegistry;
import ca.uhn.fhir.jpa.api.dao.IFhirResourceDao;
import ca.uhn.fhir.jpa.searchparam.SearchParameterMap;
import ca.uhn.fhir.model.primitive.XhtmlDt;
import ca.uhn.fhir.rest.annotation.*;
import ca.uhn.fhir.rest.api.MethodOutcome;
import ca.uhn.fhir.rest.api.PatchTypeEnum;
import ca.uhn.fhir.rest.api.server.IBundleProvider;
import ca.uhn.fhir.rest.api.server.RequestDetails;
import ca.uhn.fhir.rest.api.server.SystemRequestDetails;
import ca.uhn.fhir.rest.param.StringParam;
import ca.uhn.fhir.rest.param.TokenParam;
import ca.uhn.fhir.rest.param.UriParam;
import ca.uhn.fhir.rest.server.exceptions.InternalErrorException;
import ca.uhn.fhir.rest.server.exceptions.InvalidRequestException;
import ca.uhn.fhir.rest.server.exceptions.UnprocessableEntityException;

import org.hl7.fhir.instance.model.api.IBaseExtension;
import org.hl7.fhir.instance.model.api.IBaseHasExtensions;
import org.hl7.fhir.instance.model.api.IBaseOperationOutcome;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.*;
import org.hl7.fhir.r4.model.CodeSystem.ConceptDefinitionComponent;
import org.hl7.fhir.r4.model.CodeSystem.ConceptDefinitionDesignationComponent;
import org.hl7.fhir.r4.model.CodeSystem.ConceptPropertyComponent;
import org.hl7.fhir.utilities.xhtml.XhtmlNode;
import org.hl7.fhir.utilities.xhtml.XhtmlParser;
import org.springframework.web.bind.annotation.PostMapping;

import java.util.*;

public class CodeSystemResourceProvider extends BaseResourceProvider<CodeSystem> {
    
    // Extension URLs as constants
    private static final String CODESYSTEM_ALTERNATE_EXT = "http://hl7.org/fhir/StructureDefinition/codesystem-alternate";
    private static final String CODESYSTEM_CONCEPT_ORDER_EXT = "http://hl7.org/fhir/StructureDefinition/codesystem-conceptOrder";
    private static final String CODESYSTEM_LABEL_EXT = "http://hl7.org/fhir/StructureDefinition/codesystem-label";
    private static final String CODING_SCTDESCID_EXT = "http://hl7.org/fhir/StructureDefinition/coding-sctdescid";
    private static final String ITEM_WEIGHT_EXT = "http://hl7.org/fhir/StructureDefinition/itemWeight";
    private static final String RENDERING_STYLE_EXT = "http://hl7.org/fhir/StructureDefinition/rendering-style";
    private static final String RENDERING_XHTML_EXT = "http://hl7.org/fhir/StructureDefinition/rendering-xhtml";
    
    // IFhirResourceDao：泛型接口，提供針對 FHIR 資源的基本操作方法，例如 CRUD 操作
    private final IFhirResourceDao<CodeSystem> dao;
    
    // DaoRegistry：FHIR 的 HAPI FHIR 框架提供的工具，作為 DAO（資料訪問物件）的管理中心
    public CodeSystemResourceProvider(DaoRegistry theDaoRegistry) {
        super(theDaoRegistry);
        this.dao = theDaoRegistry.getResourceDao(CodeSystem.class);
    }
    
    RequestDetails requestDetails = new SystemRequestDetails();
    
    @Patch
    public MethodOutcome patch(
        @IdParam IdType theId,
        @ResourceParam PatchTypeEnum patchType,
        @ResourceParam String thePatchBody
    ) {
        return dao.patch(theId, null, patchType, thePatchBody, null, requestDetails);
    }
    
    private CodeSystem getCodeSystem(String system, String version) {
        SearchParameterMap searchParams = new SearchParameterMap();
        searchParams.add(CodeSystem.SP_URL, new UriParam(system));
        if (version != null) {
            searchParams.add(CodeSystem.SP_VERSION, new StringParam(version));
        }
        
        try {
            IBundleProvider searchResult = dao.search(searchParams, null);
            
            if (searchResult.size() == 0) {
                throw new ResourceNotFoundException(
                    String.format("CodeSystem with URL '%s' and version '%s' not found", 
                    system, version));
            }
            
            CodeSystem codeSystem = (CodeSystem) searchResult.getResources(0, 1).get(0);
            
            if (codeSystem == null) {
                throw new ResourceNotFoundException(
                    String.format("CodeSystem with URL '%s' and version '%s' not found", 
                    system, version));
            }
            
            return codeSystem;
            
        } catch (ResourceNotFoundException e) {
            throw e;
        } catch (Exception e) {
            throw new InternalErrorException("Error retrieving CodeSystem", e);
        }
    }
    
    @Operation(name = "$lookup", idempotent = true)
    public IBaseResource lookup(
            @OperationParam(name = "code") CodeType code,
            @OperationParam(name = "system") UriType system,
            @OperationParam(name = "version") StringType version,
            @OperationParam(name = "coding") Coding coding,
            @OperationParam(name = "property") List<StringType> properties) {
          		
        validateInputParameters(code, system, coding);
        
        if (coding != null) {
            code = new CodeType(coding.getCode());
            system = new UriType(coding.getSystem());
            if (coding.hasVersion()) {
                version = new StringType(coding.getVersion());
            }
        }
             
    	try {     
            if (system == null || system.isEmpty()) {
                throw new UnprocessableEntityException("The system parameter is required");
            }
            
            CodeSystem codeSystem = findCodeSystemWithConcept( 
                code.getValue(),
                system.getValue(),      
                version != null ? version.getValue() : null
            );

            Parameters retVal = new Parameters();
            
            String codeValue = code.getValue();
            String codeSystemValue = system.getValue();
            ConceptDefinitionComponent concept = codeSystem.getConcept().stream()
            											   .filter(c -> c.getCode().equals(codeValue))
            											   .findFirst()
            											   .orElseThrow(() -> new ResourceNotFoundException(
            													   String.format("Concept with code '%s' not found in system '%s'", codeValue, codeSystemValue)));
                
            // Enhanced response building with extensions support
            buildSuccessResponseWithExtensions(codeSystem, retVal, concept, properties);

            return retVal;
            
        } catch (ResourceNotFoundException e) {
        	return createOperationOutcome(e);
        } catch (Exception e) {
        	return createErrorOperationOutcome(e);
        }
    }
    
    // Enhanced success response with extensions support
    private void buildSuccessResponseWithExtensions(
    	    CodeSystem codeSystem, 
    	    Parameters retVal, 
    	    ConceptDefinitionComponent concept, 
    	    List<StringType> properties) {
    	    
    	    // 必要的基本參數
    	    retVal.addParameter().setName("name").setValue(new StringType(codeSystem.getName()));
    	    retVal.addParameter().setName("version").setValue(new StringType(codeSystem.getVersion()));
    	    retVal.addParameter().setName("display").setValue(new StringType(concept.getDisplay()));
    	    
    	    if (concept.hasDefinition()) {
    	        retVal.addParameter().setName("definition").setValue(new StringType(concept.getDefinition()));
    	    }
    	    
    	    // 添加所有 designation（包括原始 display）
    	    addDesignations(retVal, concept);
    	    
    	    // 添加所有 property
    	    addAllProperties(retVal, concept, codeSystem);
    	    
    	    // Extensions 支援
    	    addExtensionsToLookupResponse(retVal, codeSystem, concept);
    	    
    	    retVal.addParameter().setName("found").setValue(new BooleanType(true));
    	}

    	private void addDesignations(Parameters retVal, ConceptDefinitionComponent concept) {
    	    // 添加原始 display 作為 designation
    	    if (concept.hasDisplay()) {
    	        Parameters.ParametersParameterComponent param = retVal.addParameter();
    	        param.setName("designation");
    	        param.addPart().setName("language").setValue(new CodeType("en"));
    	        param.addPart().setName("value").setValue(new StringType(concept.getDisplay()));
    	    }
    	    
    	    // 添加其他 designations
    	    for (ConceptDefinitionDesignationComponent designation : concept.getDesignation()) {
    	        Parameters.ParametersParameterComponent param = retVal.addParameter();
    	        param.setName("designation");
    	        
    	        if (designation.hasLanguage()) {
    	            param.addPart().setName("language").setValue(new CodeType(designation.getLanguage()));
    	        }
    	        if (designation.hasUse()) {
    	            param.addPart().setName("use").setValue(designation.getUse());
    	        }
    	        param.addPart().setName("value").setValue(new StringType(designation.getValue()));
    	    }
    	}

    	private void addAllProperties(Parameters retVal, ConceptDefinitionComponent concept, CodeSystem codeSystem) {
    	    // 添加 concept 的所有 properties
    	    for (ConceptPropertyComponent prop : concept.getProperty()) {
    	        Parameters.ParametersParameterComponent param = retVal.addParameter();
    	        param.setName("property");
    	        param.addPart().setName("code").setValue(new CodeType(prop.getCode()));
    	        param.addPart().setName("value").setValue(prop.getValue());
    	    }
    	}
    
    // Add extensions to lookup response
    private void addExtensionsToLookupResponse(Parameters retVal, CodeSystem codeSystem, ConceptDefinitionComponent concept) {
        
        // 1. codesystem-alternate extension
        if (concept.hasExtension(CODESYSTEM_ALTERNATE_EXT)) {
            List<Extension> alternateExts = concept.getExtensionsByUrl(CODESYSTEM_ALTERNATE_EXT);
            for (Extension ext : alternateExts) {
                Parameters.ParametersParameterComponent param = retVal.addParameter();
                param.setName("designation");
                param.addPart().setName("use").setValue(new Coding().setCode("alternate"));
                param.addPart().setName("value").setValue(ext.getValue());
            }
        }
        
        // 2. codesystem-conceptOrder extension
        if (concept.hasExtension(CODESYSTEM_CONCEPT_ORDER_EXT)) {
            Extension orderExt = concept.getExtensionByUrl(CODESYSTEM_CONCEPT_ORDER_EXT);
            if (orderExt != null && orderExt.hasValue()) {
                Parameters.ParametersParameterComponent prop = retVal.addParameter();
                prop.setName("property");
                prop.addPart().setName("code").setValue(new CodeType("conceptOrder"));
                prop.addPart().setName("value").setValue(orderExt.getValue());
            }
        }
        
        // 3. codesystem-label extension
        if (concept.hasExtension(CODESYSTEM_LABEL_EXT)) {
            List<Extension> labelExts = concept.getExtensionsByUrl(CODESYSTEM_LABEL_EXT);
            for (Extension ext : labelExts) {
                Parameters.ParametersParameterComponent param = retVal.addParameter();
                param.setName("designation");
                param.addPart().setName("use").setValue(new Coding().setCode("label"));
                param.addPart().setName("value").setValue(ext.getValue());
            }
        }
        
        // 4. coding-sctdescid extension (for SNOMED CT)
        if (isSnomedCT(codeSystem.getUrl()) && concept.hasExtension(CODING_SCTDESCID_EXT)) {
            Extension sctDescExt = concept.getExtensionByUrl(CODING_SCTDESCID_EXT);
            if (sctDescExt != null && sctDescExt.hasValue()) {
                Parameters.ParametersParameterComponent prop = retVal.addParameter();
                prop.setName("property");
                prop.addPart().setName("code").setValue(new CodeType("sctDescId"));
                prop.addPart().setName("value").setValue(sctDescExt.getValue());
            }
        }
        
        // 5. itemWeight extension
        if (concept.hasExtension(ITEM_WEIGHT_EXT)) {
            Extension weightExt = concept.getExtensionByUrl(ITEM_WEIGHT_EXT);
            if (weightExt != null && weightExt.hasValue()) {
                Parameters.ParametersParameterComponent prop = retVal.addParameter();
                prop.setName("property");
                prop.addPart().setName("code").setValue(new CodeType("itemWeight"));
                prop.addPart().setName("value").setValue(weightExt.getValue());
            }
        }
        
        // 6. rendering-style extension
        if (concept.hasExtension(RENDERING_STYLE_EXT)) {
            Extension styleExt = concept.getExtensionByUrl(RENDERING_STYLE_EXT);
            if (styleExt != null && styleExt.hasValue()) {
                Parameters.ParametersParameterComponent prop = retVal.addParameter();
                prop.setName("property");
                prop.addPart().setName("code").setValue(new CodeType("renderingStyle"));
                prop.addPart().setName("value").setValue(styleExt.getValue());
            }
        }
        
        // 7. rendering-xhtml extension
        if (concept.hasExtension(RENDERING_XHTML_EXT)) {
            Extension xhtmlExt = concept.getExtensionByUrl(RENDERING_XHTML_EXT);
            if (xhtmlExt != null && xhtmlExt.hasValue()) {
                Parameters.ParametersParameterComponent prop = retVal.addParameter();
                prop.setName("property");
                prop.addPart().setName("code").setValue(new CodeType("renderingXhtml"));
                prop.addPart().setName("value").setValue(xhtmlExt.getValue());
            }
        }
    }
    
    // Helper method to check if CodeSystem is SNOMED CT
    private boolean isSnomedCT(String systemUrl) {
        return systemUrl != null && systemUrl.contains("snomed.info/sct");
    }
    
    // Enhanced validate-code operation with extensions support
    @Operation(name = "$validate-code", idempotent = true)
    public Parameters validateCode(
            @IdParam(optional = true) IdType resourceId,
            @OperationParam(name = "code") CodeType code,
            @OperationParam(name = "system") UriType system,
            @OperationParam(name = "version") StringType version,
            @OperationParam(name = "display") StringType display,
            @OperationParam(name = "coding") Coding coding,
            @OperationParam(name = "codeableConcept") CodeableConcept codeableConcept
    ) {
        // Extract values from Coding or CodeableConcept if provided
        if (coding != null) {
            code = coding.getCodeElement();
            system = coding.getSystemElement();
            display = coding.getDisplayElement();
        } else if (codeableConcept != null && !codeableConcept.getCoding().isEmpty()) {
            Coding first = codeableConcept.getCodingFirstRep();
            code = first.getCodeElement();
            system = first.getSystemElement();
            display = first.getDisplayElement();
        }

        validateCodeAndSystem(code, system, resourceId);

        try {
            CodeSystem codeSystem = (resourceId != null) ?
                getCodeSystemById(resourceId.getIdPart(), version) :
                validateFindCodeSystemWithConcept(code.getValue(), system.getValue(), version != null ? version.getValue() : null);

            ConceptDefinitionComponent concept = findConceptRecursive(codeSystem.getConcept(), code.getValue());
            if (concept == null) {
                return buildResult(false, code, system, null, "Concept with code '" + code.getValue() + "' not found.");
            }

            boolean isValid = isDisplayValid(concept, display);
            String message = isValid ? "Code validated successfully." : "Display does not match.";

            // Enhanced result with extensions
            return buildResultWithExtensions(isValid, code, system, codeSystem, concept, message);

        } catch (ResourceNotFoundException e) {
            return buildResult(false, code, system, null, e.getMessage());
        } catch (Exception e) {
            return buildErrorOutcome(e);
        }
    }
    
    // Enhanced result building with extensions
    private Parameters buildResultWithExtensions(boolean isValid, CodeType code, UriType system, 
                                               CodeSystem codeSystem, ConceptDefinitionComponent concept, String message) {
        Parameters result = new Parameters();

        result.addParameter().setName("result").setValue(new BooleanType(isValid));
        
        if (!isValid) {
            // 驗證失敗時應該回傳 issues 參數
            Parameters.ParametersParameterComponent issuesParam = result.addParameter();
            issuesParam.setName("issues");
            
            // 建立 OperationOutcome 作為 issues 的值
            OperationOutcome outcome = new OperationOutcome();
            outcome.addIssue()
                   .setSeverity(OperationOutcome.IssueSeverity.ERROR)
                   .setCode(OperationOutcome.IssueType.CODEINVALID)
                   .setDetails(new CodeableConcept().setText(message));
            
            issuesParam.setResource(outcome);
        } else {
            // 驗證成功時才添加其他參數
            result.addParameter().setName("code").setValue(code);
            result.addParameter().setName("system").setValue(system);
            
            if (concept != null && concept.hasDisplay()) {
                result.addParameter().setName("display").setValue(new StringType(concept.getDisplay()));
            }
            
            result.addParameter().setName("message").setValue(new StringType(message));
            
            // 只有驗證成功時才添加 extensions
            if (concept != null) {
                addExtensionsToValidateResponse(result, codeSystem, concept);
            }
        }
        
        return result;
    }
    
    // Add extensions to validate-code response
    private void addExtensionsToValidateResponse(Parameters result, CodeSystem codeSystem, ConceptDefinitionComponent concept) {
        
        // Add concept order if available
        if (concept.hasExtension(CODESYSTEM_CONCEPT_ORDER_EXT)) {
            Extension orderExt = concept.getExtensionByUrl(CODESYSTEM_CONCEPT_ORDER_EXT);
            if (orderExt != null && orderExt.hasValue()) {
                Parameters.ParametersParameterComponent prop = result.addParameter();
                prop.setName("property");
                prop.addPart().setName("code").setValue(new CodeType("conceptOrder"));
                prop.addPart().setName("value").setValue(orderExt.getValue());
            }
        }
        
        // Add labels if available
        if (concept.hasExtension(CODESYSTEM_LABEL_EXT)) {
            List<Extension> labelExts = concept.getExtensionsByUrl(CODESYSTEM_LABEL_EXT);
            for (Extension ext : labelExts) {
                Parameters.ParametersParameterComponent param = result.addParameter();
                param.setName("designation");
                param.addPart().setName("use").setValue(new Coding().setCode("label"));
                param.addPart().setName("value").setValue(ext.getValue());
            }
        }
        
        // Add item weight if available
        if (concept.hasExtension(ITEM_WEIGHT_EXT)) {
            Extension weightExt = concept.getExtensionByUrl(ITEM_WEIGHT_EXT);
            if (weightExt != null && weightExt.hasValue()) {
                Parameters.ParametersParameterComponent prop = result.addParameter();
                prop.setName("property");
                prop.addPart().setName("code").setValue(new CodeType("itemWeight"));
                prop.addPart().setName("value").setValue(weightExt.getValue());
            }
        }
    }
    
    // Utility method to add extension to concept
    public static void addExtensionToConcept(ConceptDefinitionComponent concept, String extensionUrl, Type value) {
        Extension extension = new Extension(extensionUrl, value);
        concept.addExtension(extension);
    }
    
    // Utility method to add extension to CodeSystem
    public static void addExtensionToCodeSystem(CodeSystem codeSystem, String extensionUrl, Type value) {
        Extension extension = new Extension(extensionUrl, value);
        codeSystem.addExtension(extension);
    }
    
    // [Keep all existing methods unchanged]
    private void validateInputParameters(CodeType code, UriType system, Coding coding) {
        if (code == null && coding == null) {
            throw new InvalidRequestException("Either 'code' or 'coding' parameter must be provided");
        }
        
        if (coding != null && coding.isEmpty()) {
            throw new InvalidRequestException("If coding is provided, it cannot be empty");
        }
    }
    
    private CodeSystem findCodeSystemWithConcept(String code, String system, String version) {
        try {
            CodeSystem codeSystem = getCodeSystem(system, version);
            codeSystem.getConcept().stream()
                      .filter(c -> c.getCode().equals(code))
                      .findFirst()
                      .orElseThrow(() -> new ResourceNotFoundException(
                    		  String.format("Concept with code '%s' not found in system '%s'", code, system)));
            return codeSystem;
        } catch (Exception e) {
            throw new ResourceNotFoundException(
                String.format("Error finding concept: %s", e.getMessage()), e);
        }
    }
    
    private OperationOutcome createOperationOutcome(Exception e) {
        return createOperationOutcome(
            e.getMessage(), 
            OperationOutcome.IssueSeverity.ERROR, 
            OperationOutcome.IssueType.NOTFOUND
        );
    }
    
    private OperationOutcome createOperationOutcome(
            String message, 
            OperationOutcome.IssueSeverity severity, 
            OperationOutcome.IssueType issueType) {
        
        OperationOutcome outcome = new OperationOutcome();
        outcome.setId("exception");
        
        String htmlDiv = String.format(
        		"<div xmlns=\"http://www.w3.org/1999/xhtml\">" +
        		        "<h1>Operation Outcome</h1>" +
        		        "<table border=\"0\">" +
        		        "<tr><td style=\"font-weight: bold;\">%s</td><td>[]</td><td>%s</td></tr>" +
        		        "</table></div>", 
        		        severity.toCode(),
        		        message
        );
 
        Narrative narrative = new Narrative();
        narrative.setStatus(Narrative.NarrativeStatus.GENERATED);
        narrative.setDivAsString(htmlDiv);
        
        outcome.setText(narrative);
        
        outcome.addIssue()
               .setSeverity(severity)
               .setCode(issueType)
               .setDetails(new CodeableConcept().setText(message));
        
        return outcome;
    }
    
    private OperationOutcome createErrorOperationOutcome(Exception e) {
        OperationOutcome outcome = new OperationOutcome();
        outcome.setId("exception");
        
        outcome.addIssue()
               .setSeverity(OperationOutcome.IssueSeverity.ERROR)
               .setCode(OperationOutcome.IssueType.EXCEPTION)
               .setDetails(new CodeableConcept().setText("Error processing lookup operation: " + e.getMessage()));
        
        return outcome;
    }
    
    public class ResourceNotFoundException extends RuntimeException {
        public ResourceNotFoundException(String message) {
            super(message);
        }

        public ResourceNotFoundException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    private void addPropertyToParameters(Parameters params, ConceptDefinitionComponent concept, String propertyName) {
        concept.getProperty().stream()
               .filter(p -> p.getCode().equals(propertyName))
               .forEach(p -> {
            	   Parameters.ParametersParameterComponent prop = params.addParameter();
            	   prop.setName("property");
            	   prop.addPart().setName("code").setValue(new CodeType(p.getCode()));
                
                Type value = p.getValue();
                Parameters.ParametersParameterComponent valuePart = prop.addPart().setName("value");
                if (value instanceof CodeType) {
                    valuePart.setValue(new CodeType(((CodeType)value).getValue()));
                } else if (value instanceof StringType) {
                    valuePart.setValue(new StringType(((StringType)value).getValue()));
                } else if (value instanceof BooleanType) {
                    valuePart.setValue(new BooleanType(((BooleanType)value).getValue()));
                } else {
                    valuePart.setValue(value);
                }
            });
    } 
    
    private void validateCodeAndSystem(CodeType code, UriType system, IdType resourceId) {
    	if (code == null || code.isEmpty()) {
            throw new InvalidRequestException("Parameter 'code' is required.");
        }
    	if (system == null && resourceId == null) {
            throw new InvalidRequestException("Either 'system' or resource ID must be provided.");
        }
    }

    private boolean isDisplayValid(ConceptDefinitionComponent concept, StringType display) {
    	return display == null || display.isEmpty() || display.getValue().equals(concept.getDisplay());
    }

    private ConceptDefinitionComponent findConceptRecursive(List<ConceptDefinitionComponent> concepts, String code) {
    	for (ConceptDefinitionComponent concept : concepts) {
            if (concept.getCode().equals(code)) {
                return concept;
            }
            ConceptDefinitionComponent nested = findConceptRecursive(concept.getConcept(), code);
            if (nested != null) return nested;
        }
        return null;
    }

    private Parameters buildResult(boolean isValid, CodeType code, UriType system, ConceptDefinitionComponent concept, String message) {
        Parameters result = new Parameters();
        result.addParameter().setName("result").setValue(new BooleanType(isValid));
        result.addParameter().setName("code").setValue(code);
        result.addParameter().setName("system").setValue(system);
        if (concept != null && concept.hasDisplay()) {
            result.addParameter().setName("display").setValue(new StringType(concept.getDisplay()));
        }
        result.addParameter().setName("message").setValue(new StringType(message));
        return result;
    }

    private Parameters buildErrorOutcome(Exception e) {
        Parameters outcome = new Parameters();
        outcome.addParameter().setName("result").setValue(new BooleanType(false));
        outcome.addParameter().setName("message").setValue(new StringType("Internal error: " + e.getMessage()));
        return outcome;
    }

    private CodeSystem getCodeSystemById(String id, StringType version) {
    	try {
            SearchParameterMap searchParams = new SearchParameterMap();
            searchParams.add("_id", new TokenParam(id));
            
            if (version != null && !version.isEmpty()) {
                searchParams.add(CodeSystem.SP_VERSION, new StringParam(version.getValue()));
            }

            IBundleProvider searchResult = dao.search(searchParams, null);
            
            if (searchResult.size() == 0) {
                throw new ResourceNotFoundException("CodeSystem not found with ID: " + id);
            }

            CodeSystem codeSystem = (CodeSystem) searchResult.getResources(0, 1).get(0);
            
            if (codeSystem == null) {
                throw new ResourceNotFoundException("CodeSystem not found with ID: " + id);
            }

            if (version != null && !version.isEmpty() && 
                !version.getValue().equals(codeSystem.getVersion())) {
                throw new ResourceNotFoundException(
                    String.format("Version %s not found for CodeSystem %s", 
                    version.getValue(), id)
                );
            }

            return codeSystem;

        } catch (ResourceNotFoundException e) {
            throw e;
        } catch (Exception e) {
            throw new InternalErrorException("Error retrieving CodeSystem", e);
        }
    }

    private CodeSystem validateFindCodeSystemWithConcept(String code, String system, String version) {
    	SearchParameterMap params = new SearchParameterMap();
        params.add(CodeSystem.SP_URL, new UriParam(system));
        
        if (version != null) {
            params.add(CodeSystem.SP_VERSION, new StringParam(version));
        } 

        IBundleProvider results = dao.search(params, null);

        for (IBaseResource resource : results.getResources(0, results.size())) {
            CodeSystem cs = (CodeSystem) resource;
            ConceptDefinitionComponent concept = findConceptRecursive(cs.getConcept(), code);
            if (concept != null) {
                return cs;
            }
        }

        throw new ResourceNotFoundException(String.format(
            "No CodeSystem with system '%s' and version '%s' contains code '%s'",
            system, version, code
        ));
    }
    
	@Override
    public Class<CodeSystem> getResourceType() {
        return CodeSystem.class;
    }
}