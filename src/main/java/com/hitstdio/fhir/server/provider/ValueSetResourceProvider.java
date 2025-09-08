package com.hitstdio.fhir.server.provider;

import ca.uhn.fhir.jpa.api.dao.DaoRegistry;
import ca.uhn.fhir.jpa.api.dao.IFhirResourceDao;
import ca.uhn.fhir.jpa.searchparam.SearchParameterMap;
import ca.uhn.fhir.rest.annotation.IdParam;
import ca.uhn.fhir.rest.annotation.Operation;
import ca.uhn.fhir.rest.annotation.OperationParam;
import ca.uhn.fhir.rest.annotation.ResourceParam;
import ca.uhn.fhir.rest.api.SortOrderEnum;
import ca.uhn.fhir.rest.api.SortSpec;
import ca.uhn.fhir.rest.api.server.RequestDetails;
import ca.uhn.fhir.rest.api.server.SystemRequestDetails;
import ca.uhn.fhir.rest.param.StringParam;
import ca.uhn.fhir.rest.param.TokenParam;
import ca.uhn.fhir.rest.param.UriParam;
import ca.uhn.fhir.rest.server.exceptions.InvalidRequestException;
import ca.uhn.fhir.rest.server.exceptions.ResourceNotFoundException;

import org.apache.commons.lang3.StringUtils;
import org.apache.jena.sparql.function.library.leviathan.log;
import org.hl7.fhir.r4.model.*;
import org.hl7.fhir.r4.model.CodeSystem.ConceptDefinitionComponent;
import org.hl7.fhir.r4.model.CodeSystem.ConceptDefinitionDesignationComponent;
import org.hl7.fhir.r4.model.CodeSystem.ConceptPropertyComponent;
import org.hl7.fhir.r4.model.ValueSet.ConceptReferenceComponent;
import org.hl7.fhir.r4.model.ValueSet.ConceptSetComponent;
import org.hl7.fhir.r4.model.ValueSet.ConceptSetFilterComponent;
import org.hl7.fhir.r4.model.ValueSet.ValueSetComposeComponent;
import org.hl7.fhir.r4.model.ValueSet.ValueSetExpansionComponent;
import org.hl7.fhir.r4.model.ValueSet.ValueSetExpansionContainsComponent;
import org.springframework.beans.factory.annotation.Autowired;

import com.hitstdio.fhir.server.util.ExpansionRequest;
import com.hitstdio.fhir.server.util.ValueSetExpansionService;

import java.util.ArrayList;
import java.util.List;

/**
 * HAPI FHIR Resource Provider for the ValueSet resource.
 * Implements the $expand operation for expanding value sets.
 */
public final class ValueSetResourceProvider extends BaseResourceProvider<ValueSet> {

	@Autowired
	private IFhirResourceDao<ValueSet> myValueSetDao;

	@Autowired 
	private IFhirResourceDao<CodeSystem> myCodeSystemDao;
	
    private final ValueSetExpansionService expansionService;
    
    public ValueSetResourceProvider(DaoRegistry theDaoRegistry) {
        super(theDaoRegistry);
        this.myValueSetDao = theDaoRegistry.getResourceDao(ValueSet.class);
        this.myCodeSystemDao = theDaoRegistry.getResourceDao(CodeSystem.class);
        
        this.expansionService = new ValueSetExpansionService(myValueSetDao, myCodeSystemDao);
    }

    @Override
    public Class<ValueSet> getResourceType() {
        return ValueSet.class;
    }

    /**
     * Handles the type-level $expand operation: /ValueSet/$expand
     */
    @Operation(name = "$expand", idempotent = true)
    public ValueSet expandValueSetTypeLevel(
            @OperationParam(name = "url") UriType theUrl,
            @OperationParam(name = "valueSetVersion") StringType theValueSetVersion,
            @OperationParam(name = "filter") StringType theFilter,
            @OperationParam(name = "date") DateType theDate,
            @OperationParam(name = "offset") IntegerType theOffset,
            @OperationParam(name = "count") IntegerType theCount,
            @OperationParam(name = "includeDesignations") BooleanType theIncludeDesignations,
            @OperationParam(name = "designation") List<StringType> theDesignation,
            @OperationParam(name = "includeDefinition") BooleanType theIncludeDefinition,
            @OperationParam(name = "activeOnly") BooleanType theActiveOnly,
            @OperationParam(name = "excludeNested") BooleanType theExcludeNested,
            @OperationParam(name = "excludeNotForUI") BooleanType theExcludeNotForUI,
            @OperationParam(name = "displayLanguage") CodeType theDisplayLanguageParam,
            @OperationParam(name = "exclude-system") List<CanonicalType> theExcludeSystem,
            @OperationParam(name = "system-version") List<UriType> theSystemVersion,
            @OperationParam(name = "check-system-version") List<UriType> theCheckSystemVersion,
            @OperationParam(name = "force-system-version") List<UriType> theForceSystemVersion,
            @OperationParam(name = "property") List<CodeType> theProperty,
            @ResourceParam Parameters theParameters,
            RequestDetails requestDetails) {

        ExpansionRequest request = ExpansionRequest.builder()
                .url(theUrl)
                .valueSetVersion(theValueSetVersion)
                .filter(theFilter)
                .date(theDate)
                .offset(theOffset)
                .count(theCount)
                .includeDesignations(theIncludeDesignations)
                .designation(theDesignation)
                .includeDefinition(theIncludeDefinition)
                .activeOnly(theActiveOnly)
                .excludeNested(theExcludeNested)
                .excludeNotForUI(theExcludeNotForUI)
                .displayLanguage(theDisplayLanguageParam)
                .excludeSystem(theExcludeSystem)
                .systemVersion(theSystemVersion)
                .checkSystemVersion(theCheckSystemVersion)
                .forceSystemVersion(theForceSystemVersion)
                .property(theProperty)
                .parameters(theParameters)
                .requestDetails(requestDetails)
                .build();

        return expansionService.expand(request);
    }

    /**
     * Handles the instance-level $expand operation: /ValueSet/[id]/$expand
     */
    @Operation(name = "$expand", idempotent = true)
    public ValueSet expandValueSetInstanceLevel(
            @IdParam IdType theId,
            @OperationParam(name = "url") UriType theUrl,
            @OperationParam(name = "valueSetVersion") StringType theValueSetVersion,
            @OperationParam(name = "filter") StringType theFilter,
            @OperationParam(name = "date") DateType theDate,
            @OperationParam(name = "offset") IntegerType theOffset,
            @OperationParam(name = "count") IntegerType theCount,
            @OperationParam(name = "includeDesignations") BooleanType theIncludeDesignations,
            @OperationParam(name = "designation") List<StringType> theDesignation,
            @OperationParam(name = "includeDefinition") BooleanType theIncludeDefinition,
            @OperationParam(name = "activeOnly") BooleanType theActiveOnly,
            @OperationParam(name = "excludeNested") BooleanType theExcludeNested,
            @OperationParam(name = "excludeNotForUI") BooleanType theExcludeNotForUI,
            @OperationParam(name = "displayLanguage") CodeType theDisplayLanguageParam,
            @OperationParam(name = "exclude-system") List<CanonicalType> theExcludeSystem,
            @OperationParam(name = "system-version") List<UriType> theSystemVersion,
            @OperationParam(name = "check-system-version") List<UriType> theCheckSystemVersion,
            @OperationParam(name = "force-system-version") List<UriType> theForceSystemVersion,
            @OperationParam(name = "property") List<CodeType> theProperty,
            @ResourceParam Parameters theParameters,
            RequestDetails requestDetails) {

        ExpansionRequest request = ExpansionRequest.builder()
                .id(theId)
                .url(theUrl)
                .valueSetVersion(theValueSetVersion)
                .filter(theFilter)
                .date(theDate)
                .offset(theOffset)
                .count(theCount)
                .includeDesignations(theIncludeDesignations)
                .designation(theDesignation)
                .includeDefinition(theIncludeDefinition)
                .activeOnly(theActiveOnly)
                .excludeNested(theExcludeNested)
                .excludeNotForUI(theExcludeNotForUI)
                .displayLanguage(theDisplayLanguageParam)
                .excludeSystem(theExcludeSystem)
                .systemVersion(theSystemVersion)
                .checkSystemVersion(theCheckSystemVersion)
                .forceSystemVersion(theForceSystemVersion)
                .property(theProperty)
                .parameters(theParameters)
                .requestDetails(requestDetails)
                .build();

        return expansionService.expand(request);
    }
    
    @Operation(name = "$validate-code", idempotent = true)
    public Parameters validateCode(
            @IdParam(optional = true) IdType resourceId,
            @OperationParam(name = "code") CodeType code,
            @OperationParam(name = "system") UriType system,
            @OperationParam(name = "system") CanonicalType systemCanonical,
            @OperationParam(name = "systemVersion") StringType systemVersion,
            @OperationParam(name = "systemVersion") CodeType systemVersionCode,
            @OperationParam(name = "url") UriType url,
            @OperationParam(name = "url") CanonicalType urlCanonical,
            @OperationParam(name = "valueSet") UriType valueSetUrl,
            @OperationParam(name = "valueSet") CanonicalType valueSetCanonical,
            @OperationParam(name = "version") StringType version,
            @OperationParam(name = "display") StringType display,
            @OperationParam(name = "coding") Coding coding,
            @OperationParam(name = "codeableConcept") CodeableConcept codeableConcept,
            @OperationParam(name = "displayLanguage") CodeType displayLanguage,
            @OperationParam(name = "abstract") BooleanType abstractAllowed
    ) {
        try {        
        	
        	// 解析系統 URL - 優先使用 UriType，如果沒有則使用 CanonicalType
            UriType resolvedSystem = resolveUriParameter(system, systemCanonical);
        	
            // 解析 systemVersion - 統一處理 StringType 和 CodeType
            StringType resolvedSystemVersion = resolveSystemVersionParameter(systemVersion, systemVersionCode);
                    
            // 如果有 coding 參數且其中包含 version，需要特別處理
            if (coding != null && coding.hasVersion() && 
                (resolvedSystemVersion == null || resolvedSystemVersion.isEmpty())) {
                resolvedSystemVersion = new StringType(coding.getVersion());
            }
            
            // 解析 ValueSet URL - 按優先級處理
            ValueSet targetValueSet = null;
            if (resourceId != null) {
                targetValueSet = getValueSetById(resourceId.getIdPart(), version);
            } else {
                // 解析 URL 參數
                UriType resolvedUrl = resolveUriParameter(url, urlCanonical);
                UriType resolvedValueSetUrl = resolveUriParameter(valueSetUrl, valueSetCanonical);
                
                if (resolvedUrl != null) {
                    targetValueSet = findValueSetByUrl(resolvedUrl.getValue(), 
                        version != null ? version.getValue() : null);
                } else if (resolvedValueSetUrl != null) {
                    targetValueSet = findValueSetByUrl(resolvedValueSetUrl.getValue(), 
                        version != null ? version.getValue() : null);
                } else {
                    throw new InvalidRequestException("Either resource ID, 'url', or 'valueSet' parameter must be provided");
                }
            }

            if (targetValueSet == null) {
                throw new ResourceNotFoundException("ValueSet not found");
            }

            var paramsList = extractMultipleValidationParams(code, resolvedSystem, display, coding, codeableConcept);
            boolean anyValid = false;
            ConceptDefinitionComponent matchedConcept = null;
            CodeSystem matchedCodeSystem = null;
            ValidationContext failedValidationContext = null;
            String matchedDisplay = null;
            ValidationParams successfulParams = null; 

            for (var params : paramsList) {
                validateValidationParams(params.code(), params.system(), resourceId, url, valueSetUrl);

                try {
                	// 確定要使用的 systemVersion - 優先使用 params 中的版本
                    StringType effectiveSystemVersion = determineEffectiveSystemVersion(
                        params, resolvedSystemVersion);
                	
                    // 驗證 code 是否在 ValueSet 中 - 傳入 systemVersion
                    ValidationResult validationResult = validateCodeInValueSet(
                        targetValueSet, 
                        params.code(), 
                        params.system(), 
                        params.display(), 
                        displayLanguage,
                        abstractAllowed,
                        resolvedSystemVersion
                    );

                    if (validationResult.isValid()) {
                        anyValid = true;
                        matchedConcept = validationResult.concept();
                        matchedCodeSystem = validationResult.codeSystem();
                        matchedDisplay = validationResult.display();
                        successfulParams = params; 
                        break;
                    } else {
                        // 保存失敗的驗證上下文
                        failedValidationContext = new ValidationContext(
                            params.parameterSource(),
                            params.code(),
                            params.system(),
                            params.display(),
                            validationResult.errorType(),
                            targetValueSet,
                            resolvedSystemVersion
                        );
                        if (validationResult.codeSystem() != null) {
                            matchedCodeSystem = validationResult.codeSystem();
                        }
                    }
                } catch (ResourceNotFoundException e) {
                	StringType effectiveSystemVersion = determineEffectiveSystemVersion(
                            params, resolvedSystemVersion);
                    failedValidationContext = new ValidationContext(
                        params.parameterSource(),
                        params.code(),
                        params.system(),
                        params.display(),
                        ValidationErrorType.SYSTEM_NOT_FOUND,
                        targetValueSet,
                        resolvedSystemVersion
                    );
                }
            }

            if (!anyValid) {
                return buildValidationErrorWithOutcome(false, failedValidationContext,
                        matchedCodeSystem, "Code validation failed");
            }

            // 建立成功回應 - 傳入成功驗證的參數
            StringType finalSystemVersion = determineEffectiveSystemVersion(successfulParams, resolvedSystemVersion);
            return buildSuccessResponse(successfulParams, matchedDisplay, matchedCodeSystem, 
                    targetValueSet, finalSystemVersion);

        } catch (InvalidRequestException | ResourceNotFoundException e) {
            // 解析 systemVersion 用於錯誤處理
            StringType resolvedSystemVersion = resolveSystemVersionParameter(systemVersion, systemVersionCode);
            ValidationContext errorContext = new ValidationContext(
                "code", code, system, display, ValidationErrorType.GENERAL_ERROR, null, resolvedSystemVersion
            );
            return buildValidationErrorWithOutcome(false, errorContext, null, e.getMessage());
        } catch (Exception e) {
            // 解析 systemVersion 用於錯誤處理
            StringType resolvedSystemVersion = resolveSystemVersionParameter(systemVersion, systemVersionCode);
            ValidationContext errorContext = new ValidationContext(
                "code", code, system, display, ValidationErrorType.INTERNAL_ERROR, null, resolvedSystemVersion
            );
            return buildValidationErrorWithOutcome(false, errorContext, null, "Internal error: " + e.getMessage());
        }
    }
    
    // 新增方法：確定有效的 systemVersion
    private StringType determineEffectiveSystemVersion(ValidationParams params, StringType resolvedSystemVersion) {
        // 1. 優先使用 coding 中的 version
        if (params.originalCoding() != null && params.originalCoding().hasVersion()) {
            return new StringType(params.originalCoding().getVersion());
        }
        
        // 2. 如果 codeableConcept 中的 coding 有 version，也使用它
        if (params.originalCodeableConcept() != null) {
            for (Coding c : params.originalCodeableConcept().getCoding()) {
                if (c.hasVersion()) {
                    return new StringType(c.getVersion());
                }
            }
        }
        
        // 3. 最後使用解析後的 resolvedSystemVersion
        return resolvedSystemVersion;
    }
    
    // 統一處理 systemVersion 參數的方法
    private StringType resolveSystemVersionParameter(StringType stringVersion, CodeType codeVersion) {
        if (stringVersion != null && !stringVersion.isEmpty()) {
            return stringVersion;
        } else if (codeVersion != null && !codeVersion.isEmpty()) {
            // 將 CodeType 轉換為 StringType
            return new StringType(codeVersion.getValue());
        }
        return null;
    }

    // 統一處理 UriType 和 CanonicalType 的方法
    private UriType resolveUriParameter(UriType uriParam, CanonicalType canonicalParam) {
        if (uriParam != null && !uriParam.isEmpty()) {
            return uriParam;
        } else if (canonicalParam != null && !canonicalParam.isEmpty()) {
            // 將 CanonicalType 轉換為 UriType
            return new UriType(canonicalParam.getValue());
        }
        return null;
    }

    // ValidationContext 記錄類以支援 systemVersion
    private record ValidationContext(
    	    String parameterSource,
    	    CodeType code,
    	    UriType system,
    	    StringType display,
    	    ValidationErrorType errorType,
    	    ValueSet valueSet,
    	    StringType systemVersion
    	) {}
    
    // validateCodeInValueSet 方法簽名
    private ValidationResult validateCodeInValueSet(ValueSet valueSet, CodeType code, UriType system,
                                                  StringType display, CodeType displayLanguage, 
                                                  BooleanType abstractAllowed, StringType systemVersion) {
    	
        if (valueSet == null || code == null) {
            return new ValidationResult(false, null, null, null, ValidationErrorType.INVALID_CODE);
        }

        // 優先檢查 expansion（如果存在且最新）
        if (valueSet.hasExpansion() && isExpansionCurrent(valueSet.getExpansion())) {
            ValidationResult expansionResult = validateCodeInExpansion(
                valueSet.getExpansion(), code, system, display, displayLanguage, abstractAllowed, systemVersion);
            if (expansionResult.isValid()) {
                return expansionResult;
            }
        }

        // 處理 compose 規則
        if (valueSet.hasCompose()) {
            return validateCodeInCompose(valueSet.getCompose(), code, system, display, 
                                       displayLanguage, abstractAllowed, systemVersion);
        }

        return new ValidationResult(false, null, null, null, ValidationErrorType.CODE_NOT_IN_VALUESET);
    }

    // validateCodeInConceptSet 方法中關於版本處理的邏輯
    private ValidationResult validateCodeInConceptSet(ConceptSetComponent conceptSet, CodeType code, 
                                                    UriType system, StringType display, 
                                                    CodeType displayLanguage, BooleanType abstractAllowed,
                                                    boolean isInclude, StringType resolvedSystemVersion) {
        
        // 檢查 system 是否匹配
        if (system != null && conceptSet.hasSystem() && 
            !system.getValue().equals(conceptSet.getSystem())) {
            return new ValidationResult(false, null, null, null, ValidationErrorType.INVALID_CODE);
        }

        // 取得 CodeSystem - 改進版本處理邏輯
        CodeSystem codeSystem = null;
        String systemUrl = conceptSet.hasSystem() ? conceptSet.getSystem() : 
                          (system != null ? system.getValue() : null);
        
        if (systemUrl != null) {
            try {
                String versionToUse = determineSystemVersion(conceptSet, resolvedSystemVersion);
                
                // 使用新的版本回退策略
                if (versionToUse != null) {
                    codeSystem = findCodeSystemWithVersionFallback(systemUrl, versionToUse);
                } else {
                    codeSystem = findCodeSystemByUrl(systemUrl, null);
                }
                
            } catch (ResourceNotFoundException e) {
                return new ValidationResult(false, null, null, null, ValidationErrorType.SYSTEM_NOT_FOUND);
            }
        }

        // 如果有明確的 concept 列表
        if (!conceptSet.getConcept().isEmpty()) {
            return validateCodeInConceptList(conceptSet.getConcept(), code, display, 
                                           displayLanguage, abstractAllowed, codeSystem);
        }

        // 處理 filter 條件
        if (!conceptSet.getFilter().isEmpty() && codeSystem != null) {
            return validateCodeWithFilters(conceptSet.getFilter(), codeSystem, code, display, 
                                         displayLanguage, abstractAllowed);
        }

        // 如果沒有特定的 concept 也沒有 filter，則包含整個 CodeSystem
        if (codeSystem != null) {
            return validateCodeInCodeSystem(codeSystem, code, display, displayLanguage, abstractAllowed);
        }

        return new ValidationResult(false, null, null, null, ValidationErrorType.INVALID_CODE);
    }
    
    // 修改 determineSystemVersion 方法 - 更靈活的版本決定策略
    private String determineSystemVersion(ConceptSetComponent conceptSet, StringType resolvedSystemVersion) {
        String conceptSetVersion = conceptSet.hasVersion() ? conceptSet.getVersion() : null;
        String requestVersion = (resolvedSystemVersion != null && !resolvedSystemVersion.isEmpty()) 
                              ? resolvedSystemVersion.getValue() : null;
        
        // 新的版本決定邏輯：
        // 1. 如果請求中明確指定了 systemVersion，優先使用請求的版本
        // 2. 如果請求沒有指定版本，但 ValueSet 中有指定，使用 ValueSet 的版本
        // 3. 如果兩者都有但不同，使用請求的版本並記錄警告
        
        if (requestVersion != null) {
            if (conceptSetVersion != null && !conceptSetVersion.equals(requestVersion)) {
                System.out.println("Warning: Using requested systemVersion (" + requestVersion + 
                                 ") instead of ValueSet compose.include.version (" + conceptSetVersion + ")");
            }
            return requestVersion;
        }
        
        // 如果沒有請求版本，使用 ValueSet 中定義的版本
        return conceptSetVersion;
    }

    private ValidationResult validateCodeInCompose(ValueSetComposeComponent compose, CodeType code, 
                                                 UriType system, StringType display, 
                                                 CodeType displayLanguage, BooleanType abstractAllowed,
                                                 StringType systemVersion) {
        
        boolean foundInInclude = false;
        ValidationResult includeResult = null;
        
        // 檢查 include 部分
        for (ConceptSetComponent include : compose.getInclude()) {
            ValidationResult result = validateCodeInConceptSet(include, code, system, display, 
                                                             displayLanguage, abstractAllowed, true, systemVersion);
            if (result.isValid()) {
                foundInInclude = true;
                includeResult = result;
                break;
            }
        }
        
        if (!foundInInclude) {
            return new ValidationResult(false, null, null, null, ValidationErrorType.CODE_NOT_IN_VALUESET);
        }
        
        // 檢查 exclude 部分
        for (ConceptSetComponent exclude : compose.getExclude()) {
            ValidationResult result = validateCodeInConceptSet(exclude, code, system, display, 
                                                             displayLanguage, abstractAllowed, false, systemVersion);
            if (result.isValid()) {
                // 在 exclude 中找到，表示該 code 被排除
                return new ValidationResult(false, result.concept(), result.codeSystem(), 
                                          result.display(), ValidationErrorType.CODE_NOT_IN_VALUESET);
            }
        }
        
        return includeResult;
    }

    // 修改 validateCodeInExpansion 方法簽名
    private ValidationResult validateCodeInExpansion(ValueSetExpansionComponent expansion, 
                                                   CodeType code, UriType system, StringType display,
                                                   CodeType displayLanguage, BooleanType abstractAllowed,
                                                   StringType systemVersion) {
        
        for (ValueSetExpansionContainsComponent contains : expansion.getContains()) {
            ValidationResult result = validateCodeInExpansionContains(contains, code, system, display, 
                                                                    displayLanguage, abstractAllowed, systemVersion);
            if (result.isValid()) {
                return result;
            }
        }
        
        return new ValidationResult(false, null, null, null, ValidationErrorType.CODE_NOT_IN_VALUESET);
    }

 // 修改 validateCodeInExpansionContains 方法，改進版本匹配邏輯
    private ValidationResult validateCodeInExpansionContains(ValueSetExpansionContainsComponent contains,
                                                           CodeType code, UriType system, StringType display,
                                                           CodeType displayLanguage, BooleanType abstractAllowed,
                                                           StringType resolvedSystemVersion) {
        
        // 檢查當前層級
        if (code.getValue().equals(contains.getCode()) && 
            (system == null || system.getValue().equals(contains.getSystem()))) {
            
            // 改進版本匹配邏輯
            if (!isVersionMatch(contains, resolvedSystemVersion)) {
                // 版本不匹配，記錄調試信息但繼續尋找
                System.out.println("Version mismatch: expansion contains version = " + 
                                 (contains.hasVersion() ? contains.getVersion() : "null") +
                                 ", requested version = " + 
                                 (resolvedSystemVersion != null ? resolvedSystemVersion.getValue() : "null"));
            } else {
                // 檢查是否為抽象概念
                if (contains.hasAbstract() && contains.getAbstract() && 
                    (abstractAllowed == null || !abstractAllowed.getValue())) {
                    return new ValidationResult(false, null, null, null, 
                                              ValidationErrorType.ABSTRACT_CODE_NOT_ALLOWED);
                }
                
                // 驗證 display
                if (display != null && !display.isEmpty() && contains.hasDisplay() && 
                    !display.getValue().equalsIgnoreCase(contains.getDisplay())) {
                    return new ValidationResult(false, null, null, contains.getDisplay(), 
                                              ValidationErrorType.INVALID_DISPLAY);
                }
                
                return new ValidationResult(true, null, null, contains.getDisplay(), null);
            }
        }
        
        // 遞歸檢查子概念
        for (ValueSetExpansionContainsComponent child : contains.getContains()) {
            ValidationResult result = validateCodeInExpansionContains(child, code, system, display, 
                                                                    displayLanguage, abstractAllowed, resolvedSystemVersion);
            if (result.isValid()) {
                return result;
            }
        }
        
        return new ValidationResult(false, null, null, null, ValidationErrorType.INVALID_CODE);
    }
    
    // 修改 validateCodeInExpansionContains 中的版本匹配邏輯
    private boolean isVersionMatch(ValueSetExpansionContainsComponent contains, StringType resolvedSystemVersion) {
        // 如果沒有指定 systemVersion，則任何版本都匹配
        if (resolvedSystemVersion == null || resolvedSystemVersion.isEmpty()) {
            return true;
        }
        
        String requestedVersion = resolvedSystemVersion.getValue();
        
        // 如果 expansion 中沒有版本信息，檢查是否為"無版本"的匹配
        if (!contains.hasVersion()) {
            // 如果請求的是空字串或"latest"，視為匹配無版本的概念
            return requestedVersion.isEmpty() || "latest".equals(requestedVersion);
        }
        
        // 精確版本匹配
        return requestedVersion.equals(contains.getVersion());
    }
    
 // 改善 CodeSystem 搜尋，支援版本回退策略
    private CodeSystem findCodeSystemWithVersionFallback(String url, String preferredVersion) {
        try {
            // 首先嘗試找指定版本
            return findCodeSystemByUrl(url, preferredVersion);
        } catch (ResourceNotFoundException e) {
            if (StringUtils.isNotBlank(preferredVersion)) {
                System.out.println("Preferred version not found, attempting version fallback...");
                
                // 嘗試找最新版本
                try {
                    CodeSystem latestVersion = findLatestCodeSystemVersion(url);
                    System.out.println("Using latest available version: " + 
                                     (latestVersion.hasVersion() ? latestVersion.getVersion() : "no version"));
                    return latestVersion;
                } catch (ResourceNotFoundException fallbackException) {
                    // 如果連最新版本都找不到，拋出原始異常
                    throw e;
                }
            } else {
                throw e;
            }
        }
    }

    // 查找最新版本的 CodeSystem
    private CodeSystem findLatestCodeSystemVersion(String url) {
        var searchParams = new SearchParameterMap();
        searchParams.add(CodeSystem.SP_URL, new UriParam(url));
        
        // 按版本排序（如果實作支援的話）
        searchParams.setSort(new SortSpec(CodeSystem.SP_VERSION).setOrder(SortOrderEnum.DESC));
        
        var searchResult = myCodeSystemDao.search(searchParams, new SystemRequestDetails());
        
        if (searchResult.size() == 0) {
            throw new ResourceNotFoundException(
                String.format("No CodeSystem found with URL '%s'", url));
        }
        
        return (CodeSystem) searchResult.getResources(0, 1).get(0);
    }

 // 修改 buildSuccessResponse 方法，確保正確回傳 coding 中的版本
    private Parameters buildSuccessResponse(ValidationParams successfulParams, String matchedDisplay, 
            								CodeSystem matchedCodeSystem, ValueSet targetValueSet,
            								StringType effectiveSystemVersion) {
        Parameters result = new Parameters();

        // 1. code 參數
        if (successfulParams != null && successfulParams.code() != null) {
            result.addParameter("code", successfulParams.code());
        }

        // 2. display 參數 - 優先使用原始請求中的 display，如果驗證通過的話
        String displayToReturn = null;
        
        // 如果原始請求有 display 參數，優先使用它（前提是驗證通過）
        if (successfulParams != null && successfulParams.display() != null && 
            !successfulParams.display().isEmpty()) {
            displayToReturn = successfulParams.display().getValue();
        } 
        // 如果原始請求沒有 display，則使用從 CodeSystem 或驗證過程中找到的 display
        else if (matchedDisplay != null) {
            displayToReturn = matchedDisplay;
        }
        
        // 只要有 display 值就添加到回應中
        if (displayToReturn != null) {
            result.addParameter("display", new StringType(displayToReturn));
        }

        // 3. result 參數
        result.addParameter("result", new BooleanType(true));

        // 4. system 參數 - 統一使用 UriType 格式回傳
        if (successfulParams != null && successfulParams.system() != null && !successfulParams.system().isEmpty()) {
            result.addParameter("system", successfulParams.system());
        } else if (matchedCodeSystem != null && matchedCodeSystem.hasUrl()) {
            result.addParameter("system", new UriType(matchedCodeSystem.getUrl()));
        }

        // 5. version 參數 - 使用 effectiveSystemVersion
        String versionToReturn = null;
        
        // 優先使用有效的 systemVersion 參數
        if (effectiveSystemVersion != null && !effectiveSystemVersion.isEmpty()) {
            versionToReturn = effectiveSystemVersion.getValue();
        } 
        // 如果沒有有效的 systemVersion，則使用 CodeSystem 的版本
        else if (matchedCodeSystem != null && matchedCodeSystem.hasVersion()) {
            versionToReturn = matchedCodeSystem.getVersion();
        }
        
        // 只有當有版本資訊時才添加 version 參數
        if (versionToReturn != null) {
            result.addParameter("version", new StringType(versionToReturn));
        }

        // 6. codeableConcept 參數 - 只有在原始請求包含時才回傳
        if (successfulParams != null && successfulParams.originalCodeableConcept() != null) {
            result.addParameter("codeableConcept", successfulParams.originalCodeableConcept());
        }

        return result;
    }

    // 修改錯誤回應方法以支援 systemVersion 相關錯誤
    private Parameters buildValidationErrorWithOutcome(boolean isValid, ValidationContext context,
                                                      CodeSystem codeSystem, String errorMessage) {
        Parameters result = new Parameters();

        // 添加基本參數
        if (context.code() != null) {
            result.addParameter("code", context.code());
        }

        // 建立 OperationOutcome 包含多個 issue
        OperationOutcome outcome = new OperationOutcome();
        
        // 檢查是否為 systemVersion 相關錯誤
        if (context.errorType() == ValidationErrorType.SYSTEM_NOT_FOUND && 
            context.systemVersion() != null && !context.systemVersion().isEmpty()) {
            
            // Issue 1: CodeSystem 版本找不到的錯誤
            var versionIssue = outcome.addIssue();
            versionIssue.setSeverity(OperationOutcome.IssueSeverity.ERROR);
            versionIssue.setCode(OperationOutcome.IssueType.NOTFOUND);

            versionIssue.addExtension(new Extension(
                "http://hl7.org/fhir/StructureDefinition/operationoutcome-message-id",
                new StringType("UNKNOWN_CODESYSTEM_VERSION")
            ));

            CodeableConcept versionDetails = new CodeableConcept();
            versionDetails.addCoding(new Coding(
                "http://hl7.org/fhir/tools/CodeSystem/tx-issue-type",
                "not-found",
                null
            ));
            
            String versionMessage = buildCodeSystemVersionErrorMessage(context);
            versionDetails.setText(versionMessage);
            versionIssue.setDetails(versionDetails);
            
            versionIssue.addLocation("system");
            versionIssue.addExpression("system");

            // Issue 2: ValueSet 無法驗證的警告
            var valueSetWarning = outcome.addIssue();
            valueSetWarning.setSeverity(OperationOutcome.IssueSeverity.WARNING);
            valueSetWarning.setCode(OperationOutcome.IssueType.NOTFOUND);

            valueSetWarning.addExtension(new Extension(
                "http://hl7.org/fhir/StructureDefinition/operationoutcome-message-id",
                new StringType("UNABLE_TO_CHECK_IF_THE_PROVIDED_CODES_ARE_IN_THE_VALUE_SET_CS")
            ));

            CodeableConcept vsWarningDetails = new CodeableConcept();
            vsWarningDetails.addCoding(new Coding(
                "http://hl7.org/fhir/tools/CodeSystem/tx-issue-type",
                "vs-invalid",
                null
            ));
            
            String vsWarningMessage = buildValueSetWarningMessage(context);
            vsWarningDetails.setText(vsWarningMessage);
            valueSetWarning.setDetails(vsWarningDetails);
        } else {
            // 原有的錯誤處理邏輯
            if (context.errorType() == ValidationErrorType.CODE_NOT_IN_VALUESET || 
                context.errorType() == ValidationErrorType.INVALID_CODE) {
                var valueSetIssue = outcome.addIssue();
                valueSetIssue.setSeverity(OperationOutcome.IssueSeverity.ERROR);
                valueSetIssue.setCode(OperationOutcome.IssueType.CODEINVALID);

                valueSetIssue.addExtension(new Extension(
                    "http://hl7.org/fhir/StructureDefinition/operationoutcome-message-id",
                    new StringType("None_of_the_provided_codes_are_in_the_value_set_one")
                ));

                CodeableConcept valueSetDetails = new CodeableConcept();
                valueSetDetails.addCoding(new Coding(
                    "http://hl7.org/fhir/tools/CodeSystem/tx-issue-type",
                    "not-in-vs",
                    null
                ));
                
                String valueSetMessage = buildValueSetErrorMessage(context);
                valueSetDetails.setText(valueSetMessage);
                valueSetIssue.setDetails(valueSetDetails);
                
                valueSetIssue.addLocation("code");
                valueSetIssue.addExpression("code");
            }

            if (context.errorType() == ValidationErrorType.INVALID_CODE || 
                context.errorType() == ValidationErrorType.CODE_NOT_IN_VALUESET) {
                var codeSystemIssue = outcome.addIssue();
                codeSystemIssue.setSeverity(OperationOutcome.IssueSeverity.ERROR);
                codeSystemIssue.setCode(OperationOutcome.IssueType.CODEINVALID);

                codeSystemIssue.addExtension(new Extension(
                    "http://hl7.org/fhir/StructureDefinition/operationoutcome-message-id",
                    new StringType("Unknown_Code_in_Version")
                ));

                CodeableConcept codeSystemDetails = new CodeableConcept();
                codeSystemDetails.addCoding(new Coding(
                    "http://hl7.org/fhir/tools/CodeSystem/tx-issue-type",
                    "invalid-code",
                    null
                ));
                
                String codeSystemMessage = buildCodeSystemErrorMessage(context, codeSystem);
                codeSystemDetails.setText(codeSystemMessage);
                codeSystemIssue.setDetails(codeSystemDetails);
                
                codeSystemIssue.addLocation("code");
                codeSystemIssue.addExpression("code");
            }
        }

        // 添加其他特定錯誤類型的 issue
        addSpecificErrorIssues(outcome, context, codeSystem);

        // 添加 issues 參數
        result.addParameter().setName("issues").setResource(outcome);

        // 添加 message 參數
        String mainErrorMessage;
        if (context.errorType() == ValidationErrorType.SYSTEM_NOT_FOUND && 
            context.systemVersion() != null && !context.systemVersion().isEmpty()) {
            mainErrorMessage = buildCodeSystemVersionErrorMessage(context);
        } else {
            mainErrorMessage = buildCodeSystemErrorMessage(context, codeSystem);
        }
        result.addParameter("message", new StringType(mainErrorMessage));

        // 添加 result 參數
        result.addParameter("result", new BooleanType(isValid));

        // 添加 system 參數
        if (context.system() != null) {
            result.addParameter("system", context.system());
        }

        // 添加 version 參數（如果有 systemVersion）
        if (context.systemVersion() != null && !context.systemVersion().isEmpty()) {
            result.addParameter("version", context.systemVersion());
        }

        // 添加 x-caused-by-unknown-system 參數（當系統找不到時）
        if (context.errorType() == ValidationErrorType.SYSTEM_NOT_FOUND && 
            context.system() != null && context.systemVersion() != null) {
            String canonicalUrl = context.system().getValue() + "|" + context.systemVersion().getValue();
            result.addParameter("x-caused-by-unknown-system", new CanonicalType(canonicalUrl));
        }

        return result;
    }
    // 建立 CodeSystem 版本錯誤訊息
    private String buildCodeSystemVersionErrorMessage(ValidationContext context) {
        if (context.system() != null && context.systemVersion() != null) {
            return String.format("%s|%s", 
                context.system().getValue(), 
                context.systemVersion().getValue());
        }
        return "CodeSystem with specified version not found";
    }

    // 建立 ValueSet 警告訊息
    private String buildValueSetWarningMessage(ValidationContext context) {
        if (context.valueSet() != null && context.valueSet().hasUrl()) {
            String version = context.valueSet().hasVersion() ? context.valueSet().getVersion() : "unknown";
            return String.format("%s|%s", context.valueSet().getUrl(), version);
        }
        return "Unable to validate against ValueSet";
    }

    // 修改 buildCodeSystemErrorMessage 以支援 systemVersion
    private String buildCodeSystemErrorMessage(ValidationContext context, CodeSystem codeSystem) {
        String code = (context.code() != null) ? context.code().getValue() : "null";
        String systemUrl = null;
        String version = null;

        if (context.system() != null) {
            systemUrl = context.system().getValue();
        } else if (codeSystem != null && codeSystem.hasUrl()) {
            systemUrl = codeSystem.getUrl();
        }

        // 優先使用 systemVersion，然後是 CodeSystem 的版本
        if (context.systemVersion() != null && !context.systemVersion().isEmpty()) {
            version = context.systemVersion().getValue();
        } else if (codeSystem != null && codeSystem.hasVersion()) {
            version = codeSystem.getVersion();
        } else {
            version = "unspecified";
        }

        if (systemUrl != null) {
            // 如果是版本找不到的錯誤，只回傳系統|版本格式
            if (context.errorType() == ValidationErrorType.SYSTEM_NOT_FOUND && 
                context.systemVersion() != null) {
                return String.format("%s|%s", systemUrl, version);
            }
            // 否則回傳完整的錯誤訊息
            return String.format("Unknown code '%s' in CodeSystem '%s' (version '%s')",
                code, systemUrl, version);
        }

        return String.format("Unknown code '%s' in the specified CodeSystem", code);
    }

    // 其他方法實現...
    private boolean isExpansionCurrent(ValueSetExpansionComponent expansion) {
        if (!expansion.hasTimestamp()) {
            return false;
        }
        return true; // 簡化實作
    }

    private ValidationResult validateCodeWithFilters(List<ConceptSetFilterComponent> filters, 
                                                    CodeSystem codeSystem, CodeType code, 
                                                    StringType display, CodeType displayLanguage, 
                                                    BooleanType abstractAllowed) {
        
        ConceptDefinitionComponent concept = findConceptRecursive(codeSystem.getConcept(), code.getValue());
        if (concept == null) {
            return new ValidationResult(false, null, codeSystem, null, ValidationErrorType.INVALID_CODE);
        }

        // 檢查所有 filter 條件
        for (ConceptSetFilterComponent filter : filters) {
            if (!evaluateFilter(filter, concept, codeSystem)) {
                return new ValidationResult(false, concept, codeSystem, null, 
                                          ValidationErrorType.CODE_NOT_IN_VALUESET);
            }
        }

        // 通過所有 filter，進行其他驗證
        if (isAbstractConcept(concept) && (abstractAllowed == null || !abstractAllowed.getValue())) {
            return new ValidationResult(false, concept, codeSystem, null, 
                                      ValidationErrorType.ABSTRACT_CODE_NOT_ALLOWED);
        }

        boolean isValidDisplay = isDisplayValidExtended(concept, display, displayLanguage);
        if (!isValidDisplay && display != null && !display.isEmpty()) {
            return new ValidationResult(false, concept, codeSystem, null, 
                                      ValidationErrorType.INVALID_DISPLAY);
        }

        String resolvedDisplay = getDisplayForLanguage(concept, displayLanguage);
        return new ValidationResult(true, concept, codeSystem, resolvedDisplay, null);
    }

    // 評估 filter 條件的實現
    private boolean evaluateFilter(ConceptSetFilterComponent filter, ConceptDefinitionComponent concept, 
                                 CodeSystem codeSystem) {
        String property = filter.getProperty();
        String op = filter.getOp().toCode();
        String value = filter.getValue();

        switch (property) {
            case "concept":
                return evaluateConceptFilter(op, value, concept, codeSystem);
            case "code":
                return evaluateCodeFilter(op, value, concept.getCode());
            case "status":
                return evaluateStatusFilter(op, value, concept);
            case "inactive":
                return evaluateInactiveFilter(op, value, concept);
            default:
                return evaluateCustomPropertyFilter(property, op, value, concept);
        }
    }

    private boolean evaluateConceptFilter(String op, String value, ConceptDefinitionComponent concept, 
                                        CodeSystem codeSystem) {
        switch (op) {
            case "is-a":
                return isDescendantOf(concept, value, codeSystem);
            case "descendent-of":
                return isDescendantOf(concept, value, codeSystem) && !concept.getCode().equals(value);
            case "is-not-a":
                return !isDescendantOf(concept, value, codeSystem);
            case "in":
                return isInConceptSet(concept.getCode(), value);
            case "not-in":
                return !isInConceptSet(concept.getCode(), value);
            default:
                return false;
        }
    }

    private boolean isDescendantOf(ConceptDefinitionComponent concept, String parentCode, 
                                 CodeSystem codeSystem) {
        for (ConceptPropertyComponent property : concept.getProperty()) {
            if ("parent".equals(property.getCode()) && 
                property.getValue() instanceof CodeType &&
                parentCode.equals(((CodeType) property.getValue()).getValue())) {
                return true;
            }
        }
        return isDescendantInHierarchy(concept.getCode(), parentCode, codeSystem);
    }

    private boolean isDescendantInHierarchy(String conceptCode, String parentCode, CodeSystem codeSystem) {
        ConceptDefinitionComponent parentConcept = findConceptRecursive(codeSystem.getConcept(), parentCode);
        if (parentConcept == null) {
            return false;
        }
        return containsConceptInHierarchy(parentConcept, conceptCode);
    }

    private boolean containsConceptInHierarchy(ConceptDefinitionComponent parentConcept, String targetCode) {
        for (ConceptDefinitionComponent child : parentConcept.getConcept()) {
            if (child.getCode().equals(targetCode)) {
                return true;
            }
            if (containsConceptInHierarchy(child, targetCode)) {
                return true;
            }
        }
        return false;
    }

    private boolean evaluateCodeFilter(String op, String value, String conceptCode) {
        switch (op) {
            case "=":
            case "equals":
                return conceptCode.equals(value);
            case "!=":
            case "not-equals":
                return !conceptCode.equals(value);
            case "regex":
                return conceptCode.matches(value);
            case "in":
                return isInConceptSet(conceptCode, value);
            case "not-in":
                return !isInConceptSet(conceptCode, value);
            default:
                return false;
        }
    }

    private boolean evaluateStatusFilter(String op, String value, ConceptDefinitionComponent concept) {
        String conceptStatus = null;
        for (ConceptPropertyComponent property : concept.getProperty()) {
            if ("status".equals(property.getCode())) {
                if (property.getValue() instanceof CodeType) {
                    conceptStatus = ((CodeType) property.getValue()).getValue();
                } else if (property.getValue() instanceof StringType) {
                    conceptStatus = ((StringType) property.getValue()).getValue();
                }
                break;
            }
        }
        
        if (conceptStatus == null) {
            conceptStatus = "active";
        }
        
        switch (op) {
            case "=":
            case "equals":
                return value.equals(conceptStatus);
            case "!=":
            case "not-equals":
                return !value.equals(conceptStatus);
            default:
                return false;
        }
    }

    private boolean evaluateInactiveFilter(String op, String value, ConceptDefinitionComponent concept) {
        Boolean isInactive = null;
        
        for (ConceptPropertyComponent property : concept.getProperty()) {
            if ("inactive".equals(property.getCode())) {
                if (property.getValue() instanceof BooleanType) {
                    isInactive = ((BooleanType) property.getValue()).getValue();
                }
                break;
            }
        }
        
        if (isInactive == null) {
            for (ConceptPropertyComponent property : concept.getProperty()) {
                if ("status".equals(property.getCode())) {
                    String status = null;
                    if (property.getValue() instanceof CodeType) {
                        status = ((CodeType) property.getValue()).getValue();
                    } else if (property.getValue() instanceof StringType) {
                        status = ((StringType) property.getValue()).getValue();
                    }
                    
                    if (status != null) {
                        isInactive = "retired".equals(status) || 
                                    "deprecated".equals(status) || 
                                    "withdrawn".equals(status);
                    }
                    break;
                }
            }
        }
        
        if (isInactive == null) {
            isInactive = false;
        }
        
        boolean expectedInactive = Boolean.parseBoolean(value);
        
        switch (op) {
            case "=":
            case "equals":
                return isInactive.equals(expectedInactive);
            case "!=":
            case "not-equals":
                return !isInactive.equals(expectedInactive);
            default:
                return false;
        }
    }

    private boolean evaluateCustomPropertyFilter(String property, String op, String value, 
                                               ConceptDefinitionComponent concept) {
        for (ConceptPropertyComponent prop : concept.getProperty()) {
            if (property.equals(prop.getCode())) {
                String propertyValue = null;
                
                if (prop.getValue() instanceof StringType) {
                    propertyValue = ((StringType) prop.getValue()).getValue();
                } else if (prop.getValue() instanceof CodeType) {
                    propertyValue = ((CodeType) prop.getValue()).getValue();
                } else if (prop.getValue() instanceof IntegerType) {
                    propertyValue = String.valueOf(((IntegerType) prop.getValue()).getValue());
                } else if (prop.getValue() instanceof BooleanType) {
                    propertyValue = String.valueOf(((BooleanType) prop.getValue()).getValue());
                }
                
                if (propertyValue != null) {
                    return evaluatePropertyValue(op, value, propertyValue);
                }
            }
        }
        return false;
    }

    private boolean evaluatePropertyValue(String op, String expectedValue, String actualValue) {
        switch (op) {
            case "=":
            case "equals":
                return actualValue.equals(expectedValue);
            case "!=":
            case "not-equals":
                return !actualValue.equals(expectedValue);
            case "regex":
                return actualValue.matches(expectedValue);
            case "exists":
                return actualValue != null;
            default:
                return false;
        }
    }

    private boolean isInConceptSet(String conceptCode, String valueList) {
        if (valueList == null || valueList.trim().isEmpty()) {
            return false;
        }
        
        String[] codes = valueList.split(",");
        for (String code : codes) {
            if (conceptCode.equals(code.trim())) {
                return true;
            }
        }
        return false;
    }

    // 在 concept 列表中驗證
    private ValidationResult validateCodeInConceptList(List<ConceptReferenceComponent> concepts, 
                                                     CodeType code, StringType display, 
                                                     CodeType displayLanguage, BooleanType abstractAllowed,
                                                     CodeSystem codeSystem) {
        
        for (ConceptReferenceComponent concept : concepts) {
            if (code.getValue().equals(concept.getCode())) {
                if (codeSystem != null) {
                    ConceptDefinitionComponent fullConcept = findConceptRecursive(
                        codeSystem.getConcept(), concept.getCode());
                    if (fullConcept != null) {
                        if (isAbstractConcept(fullConcept) && 
                            (abstractAllowed == null || !abstractAllowed.getValue())) {
                            return new ValidationResult(false, fullConcept, codeSystem, null, 
                                                      ValidationErrorType.ABSTRACT_CODE_NOT_ALLOWED);
                        }
                        
                        boolean isValidDisplay = isDisplayValidExtended(fullConcept, display, displayLanguage);
                        if (!isValidDisplay && display != null && !display.isEmpty()) {
                            return new ValidationResult(false, fullConcept, codeSystem, null, 
                                                      ValidationErrorType.INVALID_DISPLAY);
                        }
                        
                        String resolvedDisplay = getDisplayForLanguage(fullConcept, displayLanguage);
                        return new ValidationResult(true, fullConcept, codeSystem, resolvedDisplay, null);
                    }
                }
                
                String conceptDisplay = concept.hasDisplay() ? concept.getDisplay() : null;
                if (display != null && !display.isEmpty() && conceptDisplay != null && 
                    !display.getValue().equalsIgnoreCase(conceptDisplay)) {
                    return new ValidationResult(false, null, codeSystem, conceptDisplay, 
                                              ValidationErrorType.INVALID_DISPLAY);
                }
                
                return new ValidationResult(true, null, codeSystem, conceptDisplay, null);
            }
        }
        
        return new ValidationResult(false, null, codeSystem, null, ValidationErrorType.INVALID_CODE);
    }

    // 修改驗證邏輯，在 display 不匹配時發出警告但不阻止驗證成功
    private ValidationResult validateCodeInCodeSystem(CodeSystem codeSystem, CodeType code, 
                                                     StringType display, CodeType displayLanguage, 
                                                     BooleanType abstractAllowed) {
        
        ConceptDefinitionComponent concept = findConceptRecursive(codeSystem.getConcept(), code.getValue());
        if (concept == null) {
            return new ValidationResult(false, null, codeSystem, null, ValidationErrorType.INVALID_CODE);
        }

        if (isAbstractConcept(concept) && (abstractAllowed == null || !abstractAllowed.getValue())) {
            return new ValidationResult(false, concept, codeSystem, null, 
                                      ValidationErrorType.ABSTRACT_CODE_NOT_ALLOWED);
        }

        // 檢查 display 是否有效，但不讓 display 不匹配阻止驗證成功
        boolean isValidDisplay = isDisplayValidExtended(concept, display, displayLanguage);
        
        // 如果您希望嚴格驗證 display，請保留以下程式碼
        // 如果您希望寬鬆驗證，請註解掉以下程式碼
        /*
        if (!isValidDisplay && display != null && !display.isEmpty()) {
            return new ValidationResult(false, concept, codeSystem, null, 
                                      ValidationErrorType.INVALID_DISPLAY);
        }
        */
        
        // 記錄 display 不匹配的警告（如果需要）
        if (!isValidDisplay && display != null && !display.isEmpty()) {
            System.out.println("Warning: Display '" + display.getValue() + 
                             "' does not match expected display for code '" + code.getValue() + "'");
        }

        String resolvedDisplay = getDisplayForLanguage(concept, displayLanguage);
        return new ValidationResult(true, concept, codeSystem, resolvedDisplay, null);
    }
    // 檢查概念是否為抽象概念
    private boolean isAbstractConcept(ConceptDefinitionComponent concept) {
        for (ConceptPropertyComponent property : concept.getProperty()) {
            if ("abstract".equals(property.getCode()) && 
                property.hasValueBooleanType() && 
                property.getValueBooleanType().getValue()) {
                return true;
            }
        }
        return false;
    }

	 // 同時需要修改 display 驗證邏輯，確保在有 display 參數時進行適當的驗證
	 // 修改 isDisplayValidExtended 方法，使其在驗證失敗時不影響整體驗證結果（如果需要寬鬆驗證）
	 private boolean isDisplayValidExtended(ConceptDefinitionComponent concept, StringType display, 
	                                      CodeType displayLanguage) {
	     if (display == null || display.isEmpty()) {
	         return true; // 沒有 display 參數時視為有效
	     }
	
	     String expected = getDisplayForLanguage(concept, displayLanguage);
	     if (expected != null && display.getValue().equalsIgnoreCase(expected)) {
	         return true;
	     }
	
	     // 檢查所有 designation
	     for (ConceptDefinitionDesignationComponent desig : concept.getDesignation()) {
	         if (display.getValue().equalsIgnoreCase(desig.getValue())) {
	             return true;
	         }
	     }
	     
	     // 如果您希望在 display 不匹配時仍然回傳成功，可以在這裡回傳 true
	     // 並在呼叫端記錄警告，這樣可以滿足您的範例需求
	     return false;
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

    // 添加其他特定錯誤類型的 issue
    private void addSpecificErrorIssues(OperationOutcome outcome, ValidationContext context, 
                                       CodeSystem codeSystem) {
        switch (context.errorType()) {
            case ABSTRACT_CODE_NOT_ALLOWED:
                var abstractIssue = outcome.addIssue();
                abstractIssue.setSeverity(OperationOutcome.IssueSeverity.ERROR);
                abstractIssue.setCode(OperationOutcome.IssueType.BUSINESSRULE);
                abstractIssue.addExtension(new Extension(
                    "http://hl7.org/fhir/StructureDefinition/operationoutcome-message-id",
                    new StringType("Abstract_Code_Not_Allowed")
                ));
                
                CodeableConcept abstractDetails = new CodeableConcept();
                abstractDetails.addCoding(new Coding(
                    "http://hl7.org/fhir/tools/CodeSystem/tx-issue-type",
                    "abstract-code",
                    null
                ));
                abstractDetails.setText(String.format("Code '%s' is abstract and not allowed in this context",
                    context.code() != null ? context.code().getValue() : ""));
                abstractIssue.setDetails(abstractDetails);
                
                abstractIssue.addLocation("code");
                abstractIssue.addExpression("code");
                break;

            case INVALID_DISPLAY:
                var displayIssue = outcome.addIssue();
                displayIssue.setSeverity(OperationOutcome.IssueSeverity.ERROR);
                displayIssue.setCode(OperationOutcome.IssueType.INVALID);
                displayIssue.addExtension(new Extension(
                    "http://hl7.org/fhir/StructureDefinition/operationoutcome-message-id",
                    new StringType("Invalid_Display")
                ));
                
                CodeableConcept displayDetails = new CodeableConcept();
                displayDetails.addCoding(new Coding(
                    "http://hl7.org/fhir/tools/CodeSystem/tx-issue-type",
                    "invalid-display",
                    null
                ));
                displayDetails.setText(String.format("Display '%s' is not valid for code '%s'",
                    context.display() != null ? context.display().getValue() : "",
                    context.code() != null ? context.code().getValue() : ""));
                displayIssue.setDetails(displayDetails);
                
                setDisplayLocationAndExpression(displayIssue, context);
                break;
        }
    }

    // 為 display 錯誤設定 location 和 expression
    private void setDisplayLocationAndExpression(OperationOutcome.OperationOutcomeIssueComponent issue, 
                                              ValidationContext context) {
        if ("coding".equals(context.parameterSource())) {
            issue.addLocation("coding.display");
            issue.addExpression("coding.display");
        } else if (context.parameterSource().startsWith("codeableConcept")) {
            String location = context.parameterSource() + ".display";
            issue.addLocation(location);
            issue.addExpression(location);
        } else {
            issue.addLocation("display");
            issue.addExpression("display");
        }
    }

 // 修改 extractMultipleValidationParams 方法以正確處理 coding 中的所有欄位
    private List<ValidationParams> extractMultipleValidationParams(CodeType code, UriType resolvedSystem, 
                                                                 StringType display, Coding coding, 
                                                                 CodeableConcept codeableConcept) {
        List<ValidationParams> paramsList = new ArrayList<>();

        if (coding != null) {
            // 優先使用 coding 中的 system，如果沒有則使用 resolvedSystem
            UriType codingSystem = null;
            if (coding.hasSystem()) {
                codingSystem = coding.getSystemElement();
            } else if (resolvedSystem != null) {
                codingSystem = resolvedSystem;
            }
            
            // 優先使用 coding 中的 display，如果沒有則使用參數中的 display
            StringType codingDisplay = null;
            if (coding.hasDisplay()) {
                codingDisplay = coding.getDisplayElement();
            } else if (display != null) {
                codingDisplay = display;
            }
            
            // 使用 coding 中的 code
            CodeType codingCode = null;
            if (coding.hasCode()) {
                codingCode = coding.getCodeElement();
            } else if (code != null) {
                codingCode = code;
            }
            
            if (codingCode != null) {
                paramsList.add(new ValidationParams(
                    codingCode,
                    codingSystem,
                    codingDisplay,
                    "coding",
                    coding,
                    null
                ));
            }
        }

        if (codeableConcept != null && !codeableConcept.getCoding().isEmpty()) {
            for (int i = 0; i < codeableConcept.getCoding().size(); i++) {
                Coding c = codeableConcept.getCoding().get(i);
                
                // 優先使用每個 coding 中的欄位
                UriType codingSystem = null;
                if (c.hasSystem()) {
                    codingSystem = c.getSystemElement();
                } else if (resolvedSystem != null) {
                    codingSystem = resolvedSystem;
                }
                
                StringType codingDisplay = null;
                if (c.hasDisplay()) {
                    codingDisplay = c.getDisplayElement();
                } else if (display != null) {
                    codingDisplay = display;
                }
                
                CodeType codingCode = null;
                if (c.hasCode()) {
                    codingCode = c.getCodeElement();
                } else if (code != null) {
                    codingCode = code;
                }
                
                if (codingCode != null) {
                    paramsList.add(new ValidationParams(
                        codingCode,
                        codingSystem,
                        codingDisplay,
                        "codeableConcept.coding[" + i + "]",
                        c,
                        codeableConcept
                    ));
                }
            }
        }

        // 只有在沒有 coding 或 codeableConcept 時才添加基本的 code 參數
        if (code != null && (coding == null || !coding.hasCode()) && 
            (codeableConcept == null || codeableConcept.getCoding().isEmpty())) {
            paramsList.add(new ValidationParams(
                code, 
                resolvedSystem, 
                display, 
                "code",
                null,
                null
            ));
        }

        return paramsList;
    }
    
    // 驗證參數方法
    private void validateValidationParams(CodeType code, UriType system, IdType resourceId, 
                                        UriType url, UriType valueSetUrl) {
        if (code == null || code.isEmpty()) {
            throw new InvalidRequestException("Parameter 'code' is required");
        }
        if (resourceId == null && url == null && valueSetUrl == null) {
            throw new InvalidRequestException("Either resource ID, 'url', or 'valueSet' parameter must be provided");
        }
    }

    // 根據 ID 獲取 ValueSet
    private ValueSet getValueSetById(String id, StringType version) {
    	var searchParams = new SearchParameterMap();
        searchParams.add("_id", new TokenParam(id));
        
        if (version != null && !version.isEmpty()) {
            searchParams.add(ValueSet.SP_VERSION, new TokenParam(version.getValue()));
        }
        
        var searchResult = myValueSetDao.search(searchParams, new SystemRequestDetails());
        
        if (searchResult.size() == 0) {
            throw new ResourceNotFoundException("ValueSet not found with ID: " + id);
        }
        
        return (ValueSet) searchResult.getResources(0, 1).get(0);
    }

    // 根據 URL 查找 ValueSet
    private ValueSet findValueSetByUrl(String url, String version) {
    	var searchParams = new SearchParameterMap();
        searchParams.add(ValueSet.SP_URL, new UriParam(url));
        
        if (StringUtils.isNotBlank(version)) {
            searchParams.add(ValueSet.SP_VERSION, new StringParam(version));
        }
        
        var searchResult = myValueSetDao.search(searchParams, new SystemRequestDetails());
        
        if (searchResult.size() == 0) {
            throw new ResourceNotFoundException(
                String.format("ValueSet with URL '%s'%s not found", 
                    url, version != null ? " and version '" + version + "'" : ""));
        }
        
        return (ValueSet) searchResult.getResources(0, 1).get(0);
    }

    // 修改 findCodeSystemByUrl 方法 - 改善版本搜尋策略
    private CodeSystem findCodeSystemByUrl(String url, String version) {
        System.out.println("=== Debug findCodeSystemByUrl ===");
        System.out.println("systemUrl: " + url);
        System.out.println("requested version: " + version);
        
        var searchParams = new SearchParameterMap();
        searchParams.add(CodeSystem.SP_URL, new UriParam(url));
        
        // 如果指定了版本，先嘗試精確匹配
        if (StringUtils.isNotBlank(version)) {
            searchParams.add(CodeSystem.SP_VERSION, new TokenParam(version));
            
            var searchResult = myCodeSystemDao.search(searchParams, new SystemRequestDetails());
            
            if (searchResult.size() > 0) {
                CodeSystem codeSystem = (CodeSystem) searchResult.getResources(0, 1).get(0);
                System.out.println("Found exact version match: " + codeSystem.getUrl() + 
                                 ", version: " + codeSystem.getVersion());
                return codeSystem;
            }
            
            // 如果找不到指定版本，記錄錯誤並拋出異常
            System.out.println("Exact version not found, checking available versions...");
            
            // 查詢所有版本以提供更好的錯誤信息
            var allVersionsParams = new SearchParameterMap();
            allVersionsParams.add(CodeSystem.SP_URL, new UriParam(url));
            var allVersionsResult = myCodeSystemDao.search(allVersionsParams, new SystemRequestDetails());
            
            if (allVersionsResult.size() > 0) {
                System.out.println("Available versions:");
                for (int i = 0; i < Math.min(allVersionsResult.size(), 10); i++) {
                    CodeSystem cs = (CodeSystem) allVersionsResult.getResources(i, i+1).get(0);
                    System.out.println("  - version: " + (cs.hasVersion() ? cs.getVersion() : "no version"));
                }
            }
            
            throw new ResourceNotFoundException(
                String.format("CodeSystem with URL '%s' and version '%s' not found", url, version));
        }
        
        // 如果沒有指定版本，返回最新版本（或任意版本）
        var searchResult = myCodeSystemDao.search(searchParams, new SystemRequestDetails());
        
        if (searchResult.size() == 0) {
            throw new ResourceNotFoundException(
                String.format("CodeSystem with URL '%s' not found", url));
        }
        
        CodeSystem codeSystem = (CodeSystem) searchResult.getResources(0, 1).get(0);
        System.out.println("Found CodeSystem (no version specified): " + codeSystem.getUrl() + 
                         ", version: " + (codeSystem.hasVersion() ? codeSystem.getVersion() : "no version"));
        
        return codeSystem;
    }

    // 遞歸查找概念
    private ConceptDefinitionComponent findConceptRecursive(List<ConceptDefinitionComponent> concepts, 
                                                           String code) {
        
    	System.out.println("=== Debug findConceptRecursive ===");
        System.out.println("Looking for code: " + code);
        System.out.println("Available concepts:");
    	
    	for (ConceptDefinitionComponent concept : concepts) {
    		System.out.println("  - " + concept.getCode() + " (display: " + concept.getDisplay() + ")");
            if (code.equals(concept.getCode())) {
            	System.out.println("Found matching concept!");
                return concept;
            }
            ConceptDefinitionComponent found = findConceptRecursive(concept.getConcept(), code);
            if (found != null) {
                return found;
            }
        }
    	System.out.println("No matching concept found");
        return null;
    }

    // 錯誤類型枚舉
    private enum ValidationErrorType {
        INVALID_CODE,
        INVALID_DISPLAY,
        SYSTEM_NOT_FOUND,
        CODE_NOT_IN_VALUESET,
        ABSTRACT_CODE_NOT_ALLOWED,
        GENERAL_ERROR,
        INTERNAL_ERROR
    }

    // ValidationParams 記錄
    private record ValidationParams(
    	CodeType code,
    	UriType system,
    	StringType display,
    	String parameterSource,
    	Coding originalCoding,
    	CodeableConcept originalCodeableConcept
    ) {}

    // ValueSet 驗證結果記錄類
    private record ValidationResult(
        boolean isValid,
        ConceptDefinitionComponent concept,
        CodeSystem codeSystem,
        String display,
        ValidationErrorType errorType
    ) {}

    // 構建 ValueSet 層級的錯誤訊息
    private String buildValueSetErrorMessage(ValidationContext context) {
        if (context.valueSet() != null && context.valueSet().hasUrl() && context.code() != null) {
            String valueSetUrl = context.valueSet().getUrl();
            String version = context.valueSet().hasVersion() ? context.valueSet().getVersion() : "unknown";
            return String.format("Code '%s' is not in the ValueSet '%s|%s'",
                context.code().getValue(),
                valueSetUrl,
                version);
        }
        return "Code is not in the specified ValueSet";
    }
}