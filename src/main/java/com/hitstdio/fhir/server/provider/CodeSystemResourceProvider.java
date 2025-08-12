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

    // 從 Coding 物件中提取參數
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

    // 從獨立參數中提取
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

    // 構建 $lookup 操作的回應
    private Parameters buildLookupResponse(CodeSystem codeSystem, 
                                          ConceptDefinitionComponent concept,
                                          List<StringType> requestedProperties) {
        
    	// 驗證版本號是否符合
        if (codeSystem.hasVersion() && requestedProperties != null) {
            String requestedVersion = requestedProperties.stream()
                .filter(p -> "version".equalsIgnoreCase(p.getValue()))
                .map(StringType::getValue)
                .findFirst()
                .orElse(null);
            if (requestedVersion != null && !requestedVersion.equals(codeSystem.getVersion())) {
                throw new InvalidRequestException(
                    "Requested version '" + requestedVersion + "' does not match CodeSystem version '" + codeSystem.getVersion() + "'"
                );
            }
        }
    	
    	var response = new Parameters();
        
        addBasicLookupParameters(response, codeSystem, concept);
        addDesignations(response, codeSystem, concept);
        addProperties(response, concept, codeSystem, requestedProperties);
        
        return response;
    }

    // 添加基本的 lookup 參數
    private void addBasicLookupParameters(Parameters response, CodeSystem codeSystem, 
                                         ConceptDefinitionComponent concept) {
        // abstract (可選，如果存在的話)
        addAbstractParameter(response, concept);
        
        // code - 必要
        response.addParameter("code", new CodeType(concept.getCode()));
        
        // display - 如果存在
        if (concept.hasDisplay()) {
            response.addParameter("display", new StringType(concept.getDisplay()));
        }
        
        // name - CodeSystem 名稱
        if (codeSystem.hasName()) {
            response.addParameter("name", new StringType(codeSystem.getName()));
        }
        
        // system - CodeSystem URL，必要
        response.addParameter("system", new UriType(codeSystem.getUrl()));
        
        // version - CodeSystem 版本
        if (codeSystem.hasVersion()) {
            response.addParameter("version", new StringType(codeSystem.getVersion()));
        }
    }

    // 添加 abstract 參數
    private void addAbstractParameter(Parameters response, ConceptDefinitionComponent concept) {
        concept.getProperty().stream()
            .filter(prop -> "abstract".equals(prop.getCode()))
            .filter(prop -> prop.getValue() instanceof BooleanType)
            .findFirst()
            .ifPresent(abstractProp -> {
                BooleanType abstractValue = (BooleanType) abstractProp.getValue();
                response.addParameter("abstract", abstractValue);
            });
    }

    // 添加指定項目
    private void addDesignations(Parameters response, CodeSystem codeSystem, ConceptDefinitionComponent concept) {
        // 標準指定項目
        concept.getDesignation().forEach(designation -> 
            addDesignationParameter(response, designation));
        
     // 自動補一個基於 display 的 designation
        if (concept.hasDisplay()) {
            var param = response.addParameter().setName("designation");

            // 語言：先嘗試 concept extension -> CodeSystem.language -> 預設 en
            String languageCode = null;
            if (concept.hasExtension("http://hl7.org/fhir/StructureDefinition/language")) {
                languageCode = concept.getExtensionString("http://hl7.org/fhir/StructureDefinition/language");
            }
            if (languageCode == null || languageCode.isBlank()) {
                if (codeSystem.hasLanguage()) {
                    languageCode = codeSystem.getLanguage();
                } else {
                    languageCode = "en"; // 預設值
                }
            }

            param.addPart()
                .setName("language")
                .setValue(new CodeType(languageCode));

            param.addPart()
                .setName("use")
                .setValue(new Coding()
                    .setSystem("http://terminology.hl7.org/CodeSystem/designation-usage")
                    .setCode("display"));

            param.addPart()
                .setName("value")
                .setValue(new StringType(concept.getDisplay()));
        }
        
        // 基於擴展的指定項目
        addExtensionAsDesignation(response, concept, ExtensionUrls.CODESYSTEM_ALTERNATE.getUrl(), "alternate");
        addExtensionAsDesignation(response, concept, ExtensionUrls.CODESYSTEM_LABEL.getUrl(), "label");
    }

    // 添加指定項目參數
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

    // 將擴展作為指定項目添加
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

    // 添加屬性
    private void addProperties(Parameters response, ConceptDefinitionComponent concept,
                             CodeSystem codeSystem, List<StringType> requestedProperties) {
        var requestedCodes = getRequestedPropertyCodes(requestedProperties);
        
        // 添加所有標準屬性
        addStandardProperties(response, concept, requestedCodes);
        
        // 添加子概念作為 child 屬性
        addChildConceptProperties(response, concept, requestedCodes);
        
        // 添加父概念作為 parent 屬性
        addParentConceptProperties(response, concept, codeSystem, requestedCodes);
        
        // 基於擴展的屬性
        addPropertiesFromExtensions(response, concept, codeSystem, requestedCodes);
    }

    // 添加標準屬性
    private void addStandardProperties(Parameters response, ConceptDefinitionComponent concept, 
                                     Set<String> requestedCodes) {
    	// 使用 AtomicBoolean 或先檢查屬性存在性
        boolean hasInactive = concept.getProperty().stream()
                                    .anyMatch(prop -> "inactive".equals(prop.getCode()));
        boolean hasStatus = concept.getProperty().stream()
                                  .anyMatch(prop -> "status".equals(prop.getCode()));
    	
    	// 處理所有概念屬性
        concept.getProperty().forEach(prop -> {
            String code = prop.getCode();
            
            // 如果沒有指定特定屬性，或者該屬性在請求列表中
            if (requestedCodes.isEmpty() || requestedCodes.contains(code)) {
                // 特殊處理 abstract - 它已經在頂層處理了，這裡不重複
                if (!"abstract".equals(code)) {
                    addPropertyParameter(response, prop, concept, code);
                }
            }
        });
        
        var param = response.addParameter().setName("property");
        if(!concept.getDefinition().isEmpty()) {
        	// code 部分
            param.addPart()
                 .setName("code")
                 .setValue(new CodeType("definition"));
            
            // value 部分
            param.addPart()
                 .setName("value")
                 .setValue(new StringType(concept.getDefinition()));
        }
        
        // 如果沒有 inactive 屬性，但有 status 屬性，則根據 status 推導 inactive
        if (!hasInactive && hasStatus && 
            (requestedCodes.isEmpty() || requestedCodes.contains("inactive"))) {
            addInactivePropertyFromStatus(response, concept);
        }
        // 如果既沒有 inactive 也沒有 status，則添加預設的 inactive 屬性
        else if (!hasInactive && !hasStatus && 
                 (requestedCodes.isEmpty() || requestedCodes.contains("inactive"))) {
            addDefaultInactiveProperty(response);
        }
    }

    // 添加屬性參數
    private void addPropertyParameter(Parameters response, ConceptPropertyComponent prop, 
                                    ConceptDefinitionComponent concept, String code) {
        var param = response.addParameter().setName("property");
        
        // code 部分
        param.addPart()
             .setName("code")
             .setValue(new CodeType(prop.getCode()));
        
        // value 部分
        param.addPart()
             .setName("value")
             .setValue(prop.getValue());

    }
    
    // 根據 status 屬性推導 inactive 屬性
    private void addInactivePropertyFromStatus(Parameters response, ConceptDefinitionComponent concept) {
        Optional<ConceptPropertyComponent> statusProp = concept.getProperty().stream()
            .filter(prop -> "status".equals(prop.getCode()))
            .findFirst();
        
        if (statusProp.isPresent()) {
            var statusValue = statusProp.get().getValue();
            boolean isInactive = false;
            
            // 判斷 status 值來決定 inactive 狀態
            if (statusValue instanceof CodeType) {
                String statusCode = ((CodeType) statusValue).getValue();
                // retired, deprecated, withdrawn 等狀態視為 inactive
                isInactive = "retired".equals(statusCode) || 
                            "deprecated".equals(statusCode) || 
                            "withdrawn".equals(statusCode);
            } else if (statusValue instanceof StringType) {
                String statusString = ((StringType) statusValue).getValue();
                // 也支援 StringType 的情況
                isInactive = "retired".equals(statusString) || 
                            "deprecated".equals(statusString) || 
                            "withdrawn".equals(statusString);
            }
            
            // 添加推導的 inactive 屬性
            var param = response.addParameter().setName("property");
            param.addPart()
                .setName("code")
                .setValue(new CodeType("inactive"));
            param.addPart()
                .setName("value")
                .setValue(new BooleanType(isInactive));
        }
    }
   
    // 添加預設的 inactive 屬性
    private void addDefaultInactiveProperty(Parameters response) {
        var param = response.addParameter().setName("property");
        param.addPart()
            .setName("code")
            .setValue(new CodeType("inactive"));
        param.addPart()
            .setName("value")
            .setValue(new BooleanType(false));
    }

    // 添加子概念屬性
    private void addChildConceptProperties(Parameters response, ConceptDefinitionComponent concept, 
                                         Set<String> requestedCodes) {
        if (concept.hasConcept() && !concept.getConcept().isEmpty() && 
            (requestedCodes.isEmpty() || requestedCodes.contains("child"))) {
            
            concept.getConcept().forEach(childConcept -> {
                var param = response.addParameter().setName("property");
                
                param.addPart()
                    .setName("code")
                    .setValue(new CodeType("child"));

                // 添加描述（如果子概念有 display）
                if (childConcept.hasDisplay()) {
                    param.addPart()
                        .setName("description")
                        .setValue(new StringType(childConcept.getDisplay()));
                }
                
                param.addPart()
                    .setName("value")
                    .setValue(new CodeType(childConcept.getCode()));
            });
        }
    }

    // 添加父概念屬性
    private void addParentConceptProperties(Parameters response, ConceptDefinitionComponent concept, 
    		                                CodeSystem codeSystem, Set<String> requestedCodes) {
        if (requestedCodes.isEmpty() || requestedCodes.contains("parent")) {
        	// 首先檢查概念本身是否有 parent 屬性（明確定義的父概念）
        	concept.getProperty().stream()
                .filter(prop -> "parent".equals(prop.getCode()))
                .forEach(prop -> {
                	// 嘗試找到父概念的 display 資訊
                    String parentCode = null;
                    if (prop.getValue() instanceof CodeType) {
                        parentCode = ((CodeType) prop.getValue()).getValue();
                    } else if (prop.getValue() instanceof StringType) {
                        parentCode = ((StringType) prop.getValue()).getValue();
                    }
                    
                    String parentDisplay = null;
                    if (parentCode != null) {
                        parentDisplay = findConceptDisplay(codeSystem, parentCode);
                    }
                	
                	//寫入propaerty
                	var param = response.addParameter().setName("property");
                    
                    param.addPart()
                        .setName("code")
                        .setValue(new CodeType("parent"));
                    
                    if (parentDisplay != null) {
                        param.addPart()
                            .setName("description")
                            .setValue(new StringType(parentDisplay));
                    }
                    
                    param.addPart()
                        .setName("value")
                        .setValue(prop.getValue());
                });
        	
        	// 如果沒有明確的 parent 屬性，則透過層級結構查找父概念
            if (concept.getProperty().stream().noneMatch(prop -> "parent".equals(prop.getCode()))) {
                findParentConceptsInHierarchy(response, concept, codeSystem);
            }
        }
    }
    
    // 根據代碼查找概念的 display
    private String findConceptDisplay(CodeSystem codeSystem, String code) {
        return findConceptDisplayInList(codeSystem.getConcept(), code);
    }
    
    // 遞迴在概念清單中查找指定代碼的 display
    private String findConceptDisplayInList(List<ConceptDefinitionComponent> concepts, String code) {
        for (ConceptDefinitionComponent concept : concepts) {
            if (code.equals(concept.getCode())) {
                return concept.getDisplay();
            }
            
            // 遞迴搜尋子概念
            String display = findConceptDisplayInList(concept.getConcept(), code);
            if (display != null) {
                return display;
            }
        }
        return null;
    }
    
    // 在層級結構中查找父概念
    private void findParentConceptsInHierarchy(Parameters response, ConceptDefinitionComponent targetConcept, 
                                             CodeSystem codeSystem) {
        String targetCode = targetConcept.getCode();
        
        // 遍歷 CodeSystem 的所有頂層概念
        for (ConceptDefinitionComponent rootConcept : codeSystem.getConcept()) {
            ConceptDefinitionComponent parent = findParentInConcept(rootConcept, targetCode, null);
            if (parent != null) {
                addParentPropertyParameter(response, parent);
                break; // 找到父概念後退出
            }
        }
    }
    
    // 遞迴查找指定代碼的父概念
    private ConceptDefinitionComponent findParentInConcept(ConceptDefinitionComponent currentConcept, 
                                                         String targetCode, 
                                                         ConceptDefinitionComponent potentialParent) {
        // 如果目前概念就是目標概念，回傳其父概念
        if (targetCode.equals(currentConcept.getCode())) {
            return potentialParent;
        }
        
        // 遞迴搜尋子概念
        for (ConceptDefinitionComponent childConcept : currentConcept.getConcept()) {
            ConceptDefinitionComponent parent = findParentInConcept(childConcept, targetCode, currentConcept);
            if (parent != null) {
                return parent;
            }
        }
        
        return null;
    }
    
    // 新增父概念屬性參數
    private void addParentPropertyParameter(Parameters response, ConceptDefinitionComponent parentConcept) {
        var param = response.addParameter().setName("property");
        
        param.addPart()
            .setName("code")
            .setValue(new CodeType("parent"));
        
        // 新增父概念的 display 作為描述
        if (parentConcept.hasDisplay()) {
            param.addPart()
                .setName("description")
                .setValue(new StringType(parentConcept.getDisplay()));
        }
        
        param.addPart()
            .setName("value")
            .setValue(new CodeType(parentConcept.getCode()));
    }

    // 獲取請求的屬性代碼集合
    private Set<String> getRequestedPropertyCodes(List<StringType> requestedProperties) {
        return requestedProperties != null ? 
            requestedProperties.stream()
                .map(StringType::getValue)
                .collect(Collectors.toSet()) :
            Collections.emptySet();
    }

    // 從擴展中添加屬性
    private void addPropertiesFromExtensions(Parameters response, ConceptDefinitionComponent concept,
                                           CodeSystem codeSystem, Set<String> requestedCodes) {
        
        // 定義擴展 URL 與屬性代碼的映射關係
        var extensionMappings = Map.of(
            ExtensionUrls.CODESYSTEM_CONCEPT_ORDER.getUrl(), "conceptOrder",
            ExtensionUrls.ITEM_WEIGHT.getUrl(), "itemWeight",
            ExtensionUrls.RENDERING_STYLE.getUrl(), "renderingStyle",
            ExtensionUrls.RENDERING_XHTML.getUrl(), "renderingXhtml"
        );
        
        // 遍歷所有的擴展映射
        extensionMappings.forEach((extensionUrl, propertyCode) -> {
            if (requestedCodes.isEmpty() || requestedCodes.contains(propertyCode)) {
                addExtensionAsProperty(response, concept, extensionUrl, propertyCode);
            }
        });
        
        // 特殊處理 SNOMED CT 代碼系統的描述 ID
        if (isSnomedCT(codeSystem.getUrl()) && 
            (requestedCodes.isEmpty() || requestedCodes.contains("sctDescId"))) {
            addExtensionAsProperty(response, concept, ExtensionUrls.CODING_SCTDESCID.getUrl(), "sctDescId");
        }
    }

    // 將擴展作為屬性添加
    private void addExtensionAsProperty(Parameters response, ConceptDefinitionComponent concept,
                                      String extensionUrl, String propertyCode) {
        Optional.ofNullable(concept.getExtensionByUrl(extensionUrl))
            .filter(Extension::hasValue)
            .ifPresent(ext -> {
                var param = response.addParameter().setName("property");
                
                param.addPart()
                    .setName("code")
                    .setValue(new CodeType(propertyCode));
                
                param.addPart()
                    .setName("value")
                    .setValue(ext.getValue());
            });
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
            @OperationParam(name = "codeableConcept") CodeableConcept codeableConcept,
            @OperationParam(name = "valueSet") ValueSet valueSet,
            @OperationParam(name = "url") UriType valueSetUrl,
            @OperationParam(name = "valueSetVersion") StringType valueSetVersion,
            @OperationParam(name = "displayLanguage") CodeType displayLanguage
    ) {
        try {
            // 解析輸入參數
            var paramsList = extractMultipleValidationParams(code, system, display, coding, codeableConcept);

            boolean anyValid = false;
            ConceptDefinitionComponent matchedConcept = null;
            CodeSystem matchedCodeSystem = null;

            for (var params : paramsList) {
                validateValidationParams(params.code(), params.system(), resourceId);

                // 若提供 ValueSet，先驗證該 code 是否在 ValueSet 中
                if (valueSet != null || valueSetUrl != null) {
                    if (isCodeInValueSet(params.code().getValue(), params.system().getValue(), valueSet, valueSetUrl, valueSetVersion)) {
                        anyValid = true;
                        break;
                    }
                }

                var codeSystem = findCodeSystemForValidation(resourceId, params, version);
                var concept = findConceptRecursive(codeSystem.getConcept(), params.code().getValue());

                if (concept != null) {
                    matchedConcept = concept;
                    matchedCodeSystem = codeSystem;

                    boolean isValidDisplay = isDisplayValid(concept, params.display());
                    anyValid = isValidDisplay || (params.display() == null || params.display().isEmpty());
                    if (anyValid) break;
                }
            }

            if (!anyValid) {
                return buildValidationError(false, code, system, "Code not found in specified CodeSystem/ValueSet");
            }

            // 準備顯示語言的 display
            String resolvedDisplay = getDisplayForLanguage(matchedConcept, displayLanguage);

            // 準備訊息
            String message = "Code validation successful";
            if (display != null && !display.isEmpty() && !display.getValue().equals(resolvedDisplay)) {
                message = String.format("Code is valid but display '%s' does not match expected display '%s'",
                        display.getValue(), resolvedDisplay);
            }

            // 建立成功回應
            Parameters result = buildValidationResultWithExtensions(
                    true, code, system, matchedCodeSystem, matchedConcept, message);

            // 回應 abstract 屬性
            matchedConcept.getProperty().stream()
                    .filter(prop -> "abstract".equals(prop.getCode()))
                    .filter(prop -> prop.getValue() instanceof BooleanType)
                    .findFirst()
                    .ifPresent(abstractProp -> {
                        BooleanType abstractValue = (BooleanType) abstractProp.getValue();
                        result.addParameter("abstract", abstractValue);
                    });
            
            // displayLanguage 指定時覆蓋 display
            if (displayLanguage != null && resolvedDisplay != null) {
                result.addParameter("display", new StringType(resolvedDisplay));
            }

            return result;

        } catch (InvalidRequestException | ResourceNotFoundException e) {
            return buildValidationError(false, code, system, e.getMessage());
        } catch (Exception e) {
            return buildValidationError(false, code, system, "Internal error: " + e.getMessage());
        }
    }
    
    private List<ValidationParams> extractMultipleValidationParams(CodeType code, UriType system, StringType display,
            Coding coding, CodeableConcept codeableConcept) {
		List<ValidationParams> paramsList = new ArrayList<>();
		
		if (coding != null) {
			paramsList.add(new ValidationParams(coding.getCodeElement(), coding.getSystemElement(), coding.getDisplayElement()));
		}
		
		if (codeableConcept != null && !codeableConcept.getCoding().isEmpty()) {
			for (Coding c : codeableConcept.getCoding()) {
				paramsList.add(new ValidationParams(c.getCodeElement(), c.getSystemElement(), c.getDisplayElement()));
			}
		}
		
		if (code != null) {
			paramsList.add(new ValidationParams(code, system, display));
		}
		
			return paramsList;
		}
		
		private boolean isCodeInValueSet(String code, String system, ValueSet valueSet,
				                         UriType valueSetUrl, StringType valueSetVersion) {
		// TODO: 這裡要呼叫你的 Terminology Service 去檢查 code 是否在 ValueSet 中
		// 這可能需要用 $expand 或本地快取的 ValueSet 概念
		return false; // 預設 false
		}
		
		private String getDisplayForLanguage(ConceptDefinitionComponent concept, CodeType displayLanguage) {
			if (displayLanguage == null || concept == null) {
				return concept != null ? concept.getDisplay() : null;
		}
		String lang = displayLanguage.getValue();
			for (ConceptDefinitionDesignationComponent desig : concept.getDesignation()) {
				if (lang.equalsIgnoreCase(desig.getLanguage())) {
					return desig.getValue();
				}
		}
			return concept.getDisplay();
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