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

import org.apache.commons.lang3.StringUtils;
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
import java.util.stream.Collectors;

public class CodeSystemResourceProvider extends BaseResourceProvider<CodeSystem> {
    
    // Extension URL 常數 - 使用 enum 管理(使用列舉來集中管理 FHIR 擴展的 URL)
    public enum ExtensionUrls {
        CODESYSTEM_ALTERNATE("http://hl7.org/fhir/StructureDefinition/codesystem-alternate"),
        CODESYSTEM_CONCEPT_ORDER("http://hl7.org/fhir/StructureDefinition/codesystem-conceptOrder"),
        CODESYSTEM_LABEL("http://hl7.org/fhir/StructureDefinition/codesystem-label"),
        CODING_SCTDESCID("http://hl7.org/fhir/StructureDefinition/coding-sctdescid"),
        ITEM_WEIGHT("http://hl7.org/fhir/StructureDefinition/itemWeight"),
        RENDERING_STYLE("http://hl7.org/fhir/StructureDefinition/rendering-style"),
        RENDERING_XHTML("http://hl7.org/fhir/StructureDefinition/rendering-xhtml"),
        PROPERTY_DESCRIPTION("http://hl7.org/fhir/StructureDefinition/codesystem-property-description");
        
        private final String url;
        
        ExtensionUrls(String url) {
            this.url = url;
        }
        
        public String getUrl() {
            return url;
        }
    }
    
    private static final String SNOMED_CT_SYSTEM_URL = "snomed.info/sct";
    private static final Set<String> STANDARD_PROPERTIES = Set.of(
        "conceptOrder", "itemWeight", "renderingStyle", "renderingXhtml", "sctDescId"
    );
    
    //dao: 用來存取 CodeSystem 資源的 DAO。
    private final IFhirResourceDao<CodeSystem> dao;
    //systemRequestDetails: 用來執行 DAO 操作時模擬一個系統層級的請求。
    private final RequestDetails systemRequestDetails;
    
    public CodeSystemResourceProvider(DaoRegistry theDaoRegistry) {
        super(theDaoRegistry);
        this.dao = theDaoRegistry.getResourceDao(CodeSystem.class);
        this.systemRequestDetails = new SystemRequestDetails();
    }
    
    @Override
    public Class<CodeSystem> getResourceType() {
        return CodeSystem.class;
    }
    
    @Patch
    public MethodOutcome patch(
        @IdParam IdType theId,
        @ResourceParam PatchTypeEnum patchType,
        @ResourceParam String thePatchBody
    ) {
        return dao.patch(theId, null, patchType, thePatchBody, null, systemRequestDetails);
    }
    
    // $lookup Operation    
    @Operation(name = "$lookup", idempotent = true)
    public IBaseResource lookup(
            @OperationParam(name = "code") CodeType code,
            @OperationParam(name = "system") UriType system,
            @OperationParam(name = "version") StringType version,
            @OperationParam(name = "coding") Coding coding,
            @OperationParam(name = "property") List<StringType> properties) {
        
        try {    
            // 驗證和標準化輸入的查詢參數
            var params = validateAndNormalizeLookupParams(code, system, version, coding);
            // 根據標準化後的參數，尋找包含指定概念的編碼系統
            var codeSystem = findCodeSystemWithConcept(params.code(), params.system(), params.version());
            // 在找到的編碼系統中，尋找特定的概念定義
            var concept = findConceptInCodeSystem(codeSystem, params.code());
            
            return buildLookupResponse(codeSystem, concept, properties);
            
        } catch (InvalidRequestException | UnprocessableEntityException e) {
            return createOperationOutcome(e.getMessage(), 
                OperationOutcome.IssueSeverity.ERROR, 
                OperationOutcome.IssueType.INVALID);
        } catch (ResourceNotFoundException e) {
            return createOperationOutcome(e.getMessage(), 
                OperationOutcome.IssueSeverity.ERROR, 
                OperationOutcome.IssueType.NOTFOUND);
        } catch (Exception e) {
            return createOperationOutcome("Internal server error: " + e.getMessage(), 
                OperationOutcome.IssueSeverity.ERROR, 
                OperationOutcome.IssueType.EXCEPTION);
        }
    }

    // 驗證和標準化輸入的查詢參數
    private NormalizedParams validateAndNormalizeLookupParams(CodeType code, UriType system, 
                                                             StringType version, Coding coding) {
        if (code == null && coding == null) {
            throw new InvalidRequestException("Either 'code' or 'coding' parameter must be provided");
        }
        
        if (coding != null) {
            if (coding.isEmpty()) {
                throw new InvalidRequestException("Coding parameter cannot be empty");
            }
            return extractFromCoding(coding, version);
        }
        
        return extractFromParameters(code, system, version);
    }
    
    // 如果有coding
    private NormalizedParams extractFromCoding(Coding coding, StringType version) {
        String codeValue = coding.getCode();
        String systemValue = coding.getSystem();
        String versionValue = coding.hasVersion() ? coding.getVersion() : 
                             (version != null ? version.getValue() : null);
        
        if (StringUtils.isBlank(codeValue)) {
            throw new UnprocessableEntityException("Code is required");
        }
        if (StringUtils.isBlank(systemValue)) {
            throw new UnprocessableEntityException("System is required");
        }
        
        return new NormalizedParams(codeValue, systemValue, versionValue);
    }
    
    // 沒有coding
    private NormalizedParams extractFromParameters(CodeType code, UriType system, StringType version) {
        if (code == null || code.isEmpty()) {
            throw new UnprocessableEntityException("Code parameter is required");
        }
        if (system == null || system.isEmpty()) {
            throw new UnprocessableEntityException("System parameter is required");
        }
        
        return new NormalizedParams(
            code.getValue(),
            system.getValue(),
            version != null ? version.getValue() : null
        );
    }
    
    private Parameters buildLookupResponse(CodeSystem codeSystem, 
                                          ConceptDefinitionComponent concept,
                                          List<StringType> requestedProperties) {
        var response = new Parameters();
        
        addBasicLookupParameters(response, codeSystem, concept);
        addDesignations(response, concept);
        addProperties(response, concept, codeSystem, requestedProperties);
        
        return response;
    }
    
    private void addBasicLookupParameters(Parameters response, CodeSystem codeSystem, 
                                         ConceptDefinitionComponent concept) {
        // Add abstract property first if exists
        addAbstractProperty(response, concept);
        
        response.addParameter("code", new CodeType(concept.getCode()));
        
        if (concept.hasDisplay()) {
            response.addParameter("display", new StringType(concept.getDisplay()));
        }
        
        if (codeSystem.hasName()) {
            response.addParameter("name", new StringType(codeSystem.getName()));
        }
        
        response.addParameter("system", new UriType(codeSystem.getUrl()));
        
        if (codeSystem.hasVersion()) {
            response.addParameter("version", new StringType(codeSystem.getVersion()));
        }
    }
    
    private void addAbstractProperty(Parameters response, ConceptDefinitionComponent concept) {
        concept.getProperty().stream()
            .filter(prop -> "abstract".equals(prop.getCode()))
            .findFirst()
            .ifPresent(prop -> response.addParameter("abstract", prop.getValue()));
    }
    
    private void addDesignations(Parameters response, ConceptDefinitionComponent concept) {
        // Regular designations
        concept.getDesignation().forEach(designation -> 
            addDesignationParameter(response, designation));
        
        // Extension-based designations
        addExtensionAsDesignation(response, concept, ExtensionUrls.CODESYSTEM_ALTERNATE.getUrl(), "alternate");
        addExtensionAsDesignation(response, concept, ExtensionUrls.CODESYSTEM_LABEL.getUrl(), "label");
    }
    
    private void addDesignationParameter(Parameters response, ConceptDefinitionDesignationComponent designation) {
        var param = response.addParameter().setName("designation");
        
        if (designation.hasUse()) {
            param.addPart()
            		.setName("use")
            		.setValue(designation.getUse());
        }
        
        if (designation.hasLanguage()) {
            param.addPart()
            	.setName("language")
            	.setValue(new CodeType(designation.getLanguage()));
        }
        
        param.addPart()
        		.setName("value")
        		.setValue(new StringType(designation.getValue()));
    }
    
    private void addExtensionAsDesignation(Parameters response, ConceptDefinitionComponent concept,
                                         String extensionUrl, String useCode) {
        concept.getExtensionsByUrl(extensionUrl).forEach(ext -> {
            var param = response.addParameter().setName("designation");
            param.addPart()
            		.setName("use")
            		.setValue(new Coding().setCode(useCode));
            param.addPart()
            		.setName("value")
            		.setValue(ext.getValue());
        });
    }
    
    private void addProperties(Parameters response, ConceptDefinitionComponent concept,
                             CodeSystem codeSystem, List<StringType> requestedProperties) {
        var requestedCodes = getRequestedPropertyCodes(requestedProperties);
        
        // concept底下的property
        concept.getProperty().stream()
            .filter(prop -> requestedCodes.isEmpty() || requestedCodes.contains(prop.getCode()))
            .forEach(prop -> addPropertyParameter(response, prop));
        
        // Extension的property
        addPropertiesFromExtensions(response, concept, codeSystem, requestedCodes);
        
        // 子概念的properties
        addChildConceptProperties(response, concept, requestedCodes);
    }
    
    private Set<String> getRequestedPropertyCodes(List<StringType> requestedProperties) {
        return requestedProperties != null ? 
            requestedProperties.stream()
                .map(StringType::getValue)   // 提取每個 StringType 的字串值
                .collect(Collectors.toSet()) :  // 收集成 Set
            Collections.emptySet(); // 如果為 null 則回傳空集合
    }
    
    private void addPropertyParameter(Parameters response, ConceptPropertyComponent prop) {
        var param = response.addParameter().setName("property");
        param.addPart()
				.setName("code")
				.setValue(new CodeType(prop.getCode()));
        
        // Add description if available
        Optional.ofNullable(prop.getExtensionByUrl(ExtensionUrls.PROPERTY_DESCRIPTION.getUrl()))
            .filter(Extension::hasValue) //確保只有在 extension 有值時才會進一步呼叫 .getValue()
            .ifPresent(descExt -> param.addPart()
            							.setName("description")
            							.setValue(descExt.getValue()));
        
        param.addPart()
        		.setName("value")
        		.setValue(prop.getValue());
    }
    
    //從概念定義的擴展中添加屬性到回應參數中
    private void addPropertiesFromExtensions(Parameters response, ConceptDefinitionComponent concept,
                                           CodeSystem codeSystem, Set<String> requestedCodes) {
        
    	// 定義擴展 URL 與屬性代碼的映射關係
        // 這個 Map 將 FHIR 擴展的完整 URL 對應到簡化的屬性代碼名稱
    	var extensionMappings = Map.of(
            ExtensionUrls.CODESYSTEM_CONCEPT_ORDER.getUrl(), "conceptOrder",  // 概念排序
            ExtensionUrls.ITEM_WEIGHT.getUrl(), "itemWeight",				  // 項目權重
            ExtensionUrls.RENDERING_STYLE.getUrl(), "renderingStyle",		  // 渲染樣式
            ExtensionUrls.RENDERING_XHTML.getUrl(), "renderingXhtml"		  // XHTML 渲染
        );
        
    	// 遍歷所有的擴展映射
        extensionMappings.forEach((extensionUrl, propertyCode) -> {
        	// 檢查是否需要處理此屬性：
            // 1. 如果 requestedCodes 為空，表示要處理所有屬性
            // 2. 或者該屬性代碼在請求的代碼集合中
            if (requestedCodes.isEmpty() || requestedCodes.contains(propertyCode)) {
            	// 將擴展作為屬性添加到回應中
            	addExtensionAsProperty(response, concept, extensionUrl, propertyCode);
            }
        });
        
        // 特殊處理 SNOMED CT 代碼系統的描述 ID
        // 只有當代碼系統是 SNOMED CT 且符合請求條件時才處理
        if (isSnomedCT(codeSystem.getUrl()) && 
            (requestedCodes.isEmpty() || requestedCodes.contains("sctDescId"))) {
        	// 添加 SNOMED CT 描述 ID 作為屬性
        	addExtensionAsProperty(response, concept, ExtensionUrls.CODING_SCTDESCID.getUrl(), "sctDescId");
        }
    }
    
    // 將概念的擴展屬性添加到 Parameters 回應中
    private void addExtensionAsProperty(Parameters response, ConceptDefinitionComponent concept,
                                      String extensionUrl, String propertyCode) {
    	// 使用 Optional 鏈式操作來安全地處理可能為 null 的值
    	Optional.ofNullable(concept.getExtensionByUrl(extensionUrl))    // 根據 URL 獲取擴展，可能為 null
            .filter(Extension::hasValue)								// 過濾出有值的擴展
            .ifPresent(ext -> {											// 如果擴展存在且有值，則執行以下操作
            	// 在回應中添加一個新的 "property" 參數
            	var param = response.addParameter().setName("property");
            	
            	// 添加 "code" 部分，包含屬性的代碼
            	param.addPart()
                		.setName("code")
                		.setValue(new CodeType(propertyCode));
                
            	// 添加 "value" 部分，包含擴展的實際值
            	param.addPart()
                		.setName("value")
                		.setValue(ext.getValue());
            });
    }
    
    // 處理子概念屬性
    // 檢查concept中是否包含子概念，如果有則將其作為子代碼顯示
    private void addChildConceptProperties(Parameters response, ConceptDefinitionComponent concept, Set<String> requestedCodes) {
        // 檢查是否有子概念
        if (concept.hasConcept() && !concept.getConcept().isEmpty()) {
            // 如果沒有特定請求的屬性，或者請求了"child"屬性
            if (requestedCodes.isEmpty() || requestedCodes.contains("child")) {
                // 遍歷所有子概念
                concept.getConcept().forEach(childConcept -> {
                    addChildConceptAsProperty(response, childConcept);
                });
            }
        }
    }

    // 將子概念作為屬性添加到回應中
    private void addChildConceptAsProperty(Parameters response, ConceptDefinitionComponent childConcept) {
        var param = response.addParameter().setName("property");
        
        // 設定代碼為"child"，標明這是子代碼
        param.addPart()
        	.setName("code")
            .setValue(new CodeType("child"));
        
        // 設定值為子概念的代碼
        param.addPart()
            .setName("value")
            .setValue(new CodeType(childConcept.getCode()));
    }

    
    // $validate-code Operation    
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
        
        try {
            var params = extractValidationParams(code, system, display, coding, codeableConcept);
            validateValidationParams(params.code(), params.system(), resourceId);
            
            var codeSystem = findCodeSystemForValidation(resourceId, params, version);
            var concept = findConceptRecursive(codeSystem.getConcept(), params.code().getValue());
            
            if (concept == null) {
                return buildValidationResult(false, params.code(), params.system(), null, 
                    "Code '" + params.code().getValue() + "' not found in the specified CodeSystem");
            }
            
            boolean isValid = isDisplayValid(concept, params.display());
            String message = isValid ? "Code validation successful" : 
                           "Code is valid but display '" + params.display().getValue() + "' does not match expected display '" + concept.getDisplay() + "'";
            
            return buildValidationResultWithExtensions(isValid, params.code(), params.system(), 
                                                     codeSystem, concept, message);
            
        } catch (InvalidRequestException e) {
            return buildValidationError(false, code, system, e.getMessage());
        } catch (ResourceNotFoundException e) {
            return buildValidationError(false, code, system, e.getMessage());
        } catch (Exception e) {
            return buildValidationError(false, code, system, "Internal error: " + e.getMessage());
        }
    }
    
    private ValidationParams extractValidationParams(CodeType code, UriType system, StringType display,
                                                   Coding coding, CodeableConcept codeableConcept) {
        if (coding != null) {
            return new ValidationParams(
                coding.getCodeElement(), 
                coding.getSystemElement(), 
                coding.getDisplayElement()
            );
        } 
        
        if (codeableConcept != null && !codeableConcept.getCoding().isEmpty()) {
            var firstCoding = codeableConcept.getCodingFirstRep();
            return new ValidationParams(
                firstCoding.getCodeElement(), 
                firstCoding.getSystemElement(), 
                firstCoding.getDisplayElement()
            );
        }
        
        return new ValidationParams(code, system, display);
    }
    
    private void validateValidationParams(CodeType code, UriType system, IdType resourceId) {
        if (code == null || code.isEmpty()) {
            throw new InvalidRequestException("Parameter 'code' is required");
        }
        if (system == null && resourceId == null) {
            throw new InvalidRequestException("Either 'system' parameter or resource ID must be provided");
        }
    }
    
    private CodeSystem findCodeSystemForValidation(IdType resourceId, ValidationParams params, StringType version) {
        if (resourceId != null) {
            return getCodeSystemById(resourceId.getIdPart(), version);
        }
        return findCodeSystemByUrl(params.system().getValue(), version != null ? version.getValue() : null);
    }
    
    private Parameters buildValidationResultWithExtensions(boolean isValid, CodeType code, UriType system, 
                                                         CodeSystem codeSystem, ConceptDefinitionComponent concept, 
                                                         String message) {
        var result = new Parameters();
        result.addParameter("result", new BooleanType(isValid));
        
        if (!isValid) {
            // Add issues for invalid results
            var issuesParam = result.addParameter().setName("issues");
            var outcome = new OperationOutcome();
            outcome.addIssue()
                   .setSeverity(OperationOutcome.IssueSeverity.ERROR)
                   .setCode(OperationOutcome.IssueType.CODEINVALID)
                   .setDetails(new CodeableConcept().setText(message));
            issuesParam.setResource(outcome);
        } else {
            // Add success details
            result.addParameter("code", code);
            result.addParameter("system", system);
            
            if (concept != null && concept.hasDisplay()) {
                result.addParameter("display", new StringType(concept.getDisplay()));
            }
            
            result.addParameter("message", new StringType(message));
            
            // Add extensions
            if (concept != null) {
                addValidationExtensions(result, codeSystem, concept);
            }
        }
        
        return result;
    }
    
    private void addValidationExtensions(Parameters result, CodeSystem codeSystem, ConceptDefinitionComponent concept) {
        // Concept order extension
        addExtensionAsValidationProperty(result, concept, ExtensionUrls.CODESYSTEM_CONCEPT_ORDER.getUrl(), "conceptOrder");
        
        // Label extensions as designations
        concept.getExtensionsByUrl(ExtensionUrls.CODESYSTEM_LABEL.getUrl()).forEach(ext -> {
            var param = result.addParameter().setName("designation");
            param.addPart()
            		.setName("use")
            		.setValue(new Coding().setCode("label"));
            param.addPart()
    				.setName("value")
    				.setValue(ext.getValue());
        });
        
        // Item weight extension
        addExtensionAsValidationProperty(result, concept, ExtensionUrls.ITEM_WEIGHT.getUrl(), "itemWeight");
    }
    
    private void addExtensionAsValidationProperty(Parameters result, ConceptDefinitionComponent concept, 
                                                String extensionUrl, String propertyCode) {
        var extension = concept.getExtensionByUrl(extensionUrl);
        if (extension != null && extension.hasValue()) {
            var prop = result.addParameter().setName("property");
            prop.addPart()
            		.setName("code")
            		.setValue(new CodeType(propertyCode));
            prop.addPart()
            		.setName("value")
            		.setValue(extension.getValue());
        }
    }
    
    private Parameters buildValidationResult(boolean isValid, CodeType code, UriType system, 
                                           ConceptDefinitionComponent concept, String message) {
        var result = new Parameters();
        result.addParameter("result", new BooleanType(isValid));
        
        if (code != null) {
            result.addParameter("code", code);
        }
        if (system != null) {
            result.addParameter("system", system);
        }
        if (concept != null && concept.hasDisplay()) {
            result.addParameter("display", new StringType(concept.getDisplay()));
        }
        
        result.addParameter("message", new StringType(message));
        return result;
    }
    
    private Parameters buildValidationError(boolean isValid, CodeType code, UriType system, String message) {
        var result = new Parameters();
        result.addParameter("result", new BooleanType(isValid));
        result.addParameter("message", new StringType(message));
        return result;
    }
    
    // Core CodeSystem Operations
    private CodeSystem getCodeSystem(String systemUrl, String version) {
        var searchParams = new SearchParameterMap();
        searchParams.add(CodeSystem.SP_URL, new UriParam(systemUrl));
        
        if (StringUtils.isNotBlank(version)) {
            searchParams.add(CodeSystem.SP_VERSION, new StringParam(version));
        }
        
        var searchResult = dao.search(searchParams, systemRequestDetails);
        
        if (searchResult.size() == 0) {
            throw new ResourceNotFoundException(
                String.format("CodeSystem with URL '%s'%s not found", 
                    systemUrl, version != null ? " and version '" + version + "'" : ""));
        }
        
        var codeSystem = (CodeSystem) searchResult.getResources(0, 1).get(0);
        if (codeSystem == null) {
            throw new ResourceNotFoundException(
                String.format("CodeSystem with URL '%s'%s not found", 
                    systemUrl, version != null ? " and version '" + version + "'" : ""));
        }
        
        return codeSystem;
    }
    
    private CodeSystem getCodeSystemById(String id, StringType version) {
        var searchParams = new SearchParameterMap();
        searchParams.add("_id", new TokenParam(id));
        
        if (version != null && !version.isEmpty()) {
            searchParams.add(CodeSystem.SP_VERSION, new StringParam(version.getValue()));
        }
        
        var searchResult = dao.search(searchParams, systemRequestDetails);
        
        if (searchResult.size() == 0) {
            throw new ResourceNotFoundException("CodeSystem not found with ID: " + id);
        }
        
        var codeSystem = (CodeSystem) searchResult.getResources(0, 1).get(0);
        if (codeSystem == null) {
            throw new ResourceNotFoundException("CodeSystem not found with ID: " + id);
        }
        
        if (version != null && !version.isEmpty() && 
            !version.getValue().equals(codeSystem.getVersion())) {
            throw new ResourceNotFoundException(
                String.format("Version %s not found for CodeSystem %s", version.getValue(), id));
        }
        
        return codeSystem;
    }
    
    private CodeSystem findCodeSystemByUrl(String systemUrl, String version) {
        return getCodeSystem(systemUrl, version);
    }
    
    private CodeSystem findCodeSystemWithConcept(String code, String systemUrl, String version) {
        var codeSystem = getCodeSystem(systemUrl, version);
        
        var conceptExists = codeSystem.getConcept().stream()
            .anyMatch(c -> c.getCode().equals(code)) ||
            findConceptRecursive(codeSystem.getConcept(), code) != null;
        
        if (!conceptExists) {
            throw new ResourceNotFoundException(
                String.format("Concept with code '%s' not found in CodeSystem '%s'", code, systemUrl));
        }
        
        return codeSystem;
    }
    
    private ConceptDefinitionComponent findConceptInCodeSystem(CodeSystem codeSystem, String code) {
        return findConceptRecursive(codeSystem.getConcept(), code);
    }
    
    private ConceptDefinitionComponent findConceptRecursive(List<ConceptDefinitionComponent> concepts, String code) {
        for (var concept : concepts) {
            if (concept.getCode().equals(code)) {
                return concept;
            }
            
            var nestedConcept = findConceptRecursive(concept.getConcept(), code);
            if (nestedConcept != null) {
                return nestedConcept;
            }
        }
        return null;
    }
    
    // Utility Methods
    
    private boolean isSnomedCT(String systemUrl) {
        return systemUrl != null && systemUrl.contains(SNOMED_CT_SYSTEM_URL);
    }
    
    private boolean isDisplayValid(ConceptDefinitionComponent concept, StringType display) {
        return display == null || display.isEmpty() || 
               StringUtils.equals(display.getValue(), concept.getDisplay());
    }
    
    private OperationOutcome createOperationOutcome(String message, 
                                                   OperationOutcome.IssueSeverity severity, 
                                                   OperationOutcome.IssueType issueType) {
        var outcome = new OperationOutcome();
        outcome.setId("operation-outcome");
        
        // Create narrative
        String htmlDiv = String.format(
            "<div xmlns=\"http://www.w3.org/1999/xhtml\">" +
            "<h1>Operation Outcome</h1>" +
            "<p><strong>%s:</strong> %s</p>" +
            "</div>", 
            severity.toCode().toUpperCase(), message
        );
        
        var narrative = new Narrative();
        narrative.setStatus(Narrative.NarrativeStatus.GENERATED);
        narrative.setDivAsString(htmlDiv);
        outcome.setText(narrative);
        
        // Add issue
        outcome.addIssue()
               .setSeverity(severity)
               .setCode(issueType)
               .setDetails(new CodeableConcept().setText(message));
        
        return outcome;
    }
    
    // Static Utility Methods for External Use
    
    public static void addExtensionToConcept(ConceptDefinitionComponent concept, String extensionUrl, Type value) {
        concept.addExtension(new Extension(extensionUrl, value));
    }
    
    public static void addExtensionToCodeSystem(CodeSystem codeSystem, String extensionUrl, Type value) {
        codeSystem.addExtension(new Extension(extensionUrl, value));
    }

    // Record 用來簡化資料載體類別（data carrier classes）的建立
    private record NormalizedParams(String code, String system, String version) {}
    private record ValidationParams(CodeType code, UriType system, StringType display) {}
    
    // Custom Exception Classes
    
    public static class ResourceNotFoundException extends RuntimeException {
        public ResourceNotFoundException(String message) {
            super(message);
        }
        
        public ResourceNotFoundException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}