package com.hitstdio.fhir.server.provider;

import ca.uhn.fhir.jpa.api.dao.DaoRegistry;
import ca.uhn.fhir.jpa.api.dao.IFhirResourceDao;
import ca.uhn.fhir.jpa.searchparam.SearchParameterMap;
import ca.uhn.fhir.rest.annotation.IdParam;
import ca.uhn.fhir.rest.annotation.Operation;
import ca.uhn.fhir.rest.annotation.OperationParam;
import ca.uhn.fhir.rest.annotation.Patch;
import ca.uhn.fhir.rest.annotation.ResourceParam;
import ca.uhn.fhir.rest.api.MethodOutcome;
import ca.uhn.fhir.rest.api.PatchTypeEnum;
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
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.*;
import org.hl7.fhir.r4.model.CodeSystem.ConceptDefinitionComponent;
import org.hl7.fhir.r4.model.CodeSystem.ConceptDefinitionDesignationComponent;
import org.hl7.fhir.r4.model.CodeSystem.ConceptPropertyComponent;
import org.hl7.fhir.r4.model.Parameters.ParametersParameterComponent;
import org.hl7.fhir.r4.model.ValueSet.ConceptReferenceComponent;
import org.hl7.fhir.r4.model.ValueSet.ConceptSetComponent;
import org.hl7.fhir.r4.model.ValueSet.ConceptSetFilterComponent;
import org.hl7.fhir.r4.model.ValueSet.ValueSetComposeComponent;
import org.hl7.fhir.r4.model.ValueSet.ValueSetExpansionComponent;
import org.hl7.fhir.r4.model.ValueSet.ValueSetExpansionContainsComponent;
import org.springframework.beans.factory.annotation.Autowired;

import com.hitstdio.fhir.server.util.ExpansionRequest;
import com.hitstdio.fhir.server.util.OperationOutcomeHelper;
import com.hitstdio.fhir.server.util.OperationOutcomeIssueBuilder;
import com.hitstdio.fhir.server.util.OperationOutcomeMessageId;
import com.hitstdio.fhir.server.util.ValidationContext;
import com.hitstdio.fhir.server.util.ValidationErrorType;
import com.hitstdio.fhir.server.util.ValidationParams;
import com.hitstdio.fhir.server.util.ValidationResult;
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
    
    private final RequestDetails systemRequestDetails;
    
    public ValueSetResourceProvider(DaoRegistry theDaoRegistry) {
        super(theDaoRegistry);
        this.myValueSetDao = theDaoRegistry.getResourceDao(ValueSet.class);
        this.myCodeSystemDao = theDaoRegistry.getResourceDao(CodeSystem.class);
        this.expansionService = new ValueSetExpansionService(myValueSetDao, myCodeSystemDao);
        this.systemRequestDetails = new SystemRequestDetails();
    }

    @Override
    public Class<ValueSet> getResourceType() {
        return ValueSet.class;
    }
    
    @Patch
    public MethodOutcome patch(
        @IdParam IdType theId,
        @ResourceParam PatchTypeEnum patchType,
        @ResourceParam String thePatchBody
    ) {
        return myValueSetDao.patch(theId, null, patchType, thePatchBody, null, systemRequestDetails);
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
    public IBaseResource validateCode(
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
            @OperationParam(name = "abstract") BooleanType abstractAllowed,
            @OperationParam(name = "activeOnly") BooleanType activeOnly
    ) {
        try {        
        	
        	// 解析系統 URL
            UriType resolvedSystem = resolveUriParameter(system, systemCanonical);
        	
            // 解析 systemVersion
            StringType resolvedSystemVersion = resolveSystemVersionParameter(systemVersion, systemVersionCode);
                    
            // 如果有 coding 參數且其中包含 version，需要特別處理
            if (coding != null && coding.hasVersion() && 
                (resolvedSystemVersion == null || resolvedSystemVersion.isEmpty())) {
                resolvedSystemVersion = new StringType(coding.getVersion());
            }
            
            // 解析 ValueSet URL
            ValueSet targetValueSet = null;
            String requestedValueSetUrl = null;
            
            if (resourceId != null) {
                requestedValueSetUrl = "ID: " + resourceId.getIdPart();
                targetValueSet = getValueSetById(resourceId.getIdPart(), version);
            } else {
                // 解析 URL 參數
                UriType resolvedUrl = resolveUriParameter(url, urlCanonical);
                UriType resolvedValueSetUrl = resolveUriParameter(valueSetUrl, valueSetCanonical);
                
                if (resolvedUrl != null) {
                    requestedValueSetUrl = resolvedUrl.getValue();
                    targetValueSet = findValueSetByUrl(resolvedUrl.getValue(), 
                        version != null ? version.getValue() : null);
                } else if (resolvedValueSetUrl != null) {
                    requestedValueSetUrl = resolvedValueSetUrl.getValue();
                    targetValueSet = findValueSetByUrl(resolvedValueSetUrl.getValue(), 
                        version != null ? version.getValue() : null);
                } else {
                    throw new InvalidRequestException("Either resource ID, 'url', or 'valueSet' parameter must be provided");
                }
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
                    // 確定要使用的 systemVersion - 包含版本推斷邏輯
                    StringType effectiveSystemVersion = determineEffectiveSystemVersion(
                        params, resolvedSystemVersion);

                    // 驗證 code 是否在 ValueSet 中 - 使用推斷或指定的版本
                    ValidationResult validationResult = validateCodeInValueSet(
                        targetValueSet, 
                        params.code(), 
                        params.system(), 
                        params.display(), 
                        displayLanguage,
                        abstractAllowed,
                        effectiveSystemVersion,
                        activeOnly
                    );
                    
                    // 新增：處理 NO_SYSTEM 錯誤
                    if (validationResult.errorType() == ValidationErrorType.NO_SYSTEM) {
                        Parameters errorResult = buildNoSystemError(
                            params, 
                            targetValueSet, 
                            effectiveSystemVersion
                        );
                        removeNarratives(errorResult);
                        return errorResult;
                    }
                    
                    // 處理 RELATIVE_SYSTEM_REFERENCE 錯誤
                    if (validationResult.errorType() == ValidationErrorType.RELATIVE_SYSTEM_REFERENCE) {
                        Parameters errorResult = buildRelativeSystemError(
                            params, 
                            targetValueSet, 
                            effectiveSystemVersion
                        );
                        removeNarratives(errorResult);
                        return errorResult;
                    }
                    
                    // 處理 SYSTEM_IS_VALUESET 錯誤
                    if (validationResult.errorType() == ValidationErrorType.SYSTEM_IS_VALUESET) {
                        Parameters errorResult = buildSystemIsValueSetError(
                            params, 
                            targetValueSet, 
                            effectiveSystemVersion
                        );
                        removeNarratives(errorResult);
                        return errorResult;
                    }

                    if (validationResult.isValid()) {
                        // 如果 code 有效但是 inactive，且 activeOnly=true
                        if (validationResult.isInactive() != null && 
                            validationResult.isInactive() && 
                            activeOnly != null && activeOnly.getValue()) {
                            
                            // 儲存這個結果用於後續建立錯誤回應
                            matchedConcept = validationResult.concept();
                            matchedCodeSystem = validationResult.codeSystem();
                            matchedDisplay = validationResult.display();
                            successfulParams = params;
                            
                            if (effectiveSystemVersion != null && 
                                (resolvedSystemVersion == null || resolvedSystemVersion.isEmpty())) {
                                resolvedSystemVersion = effectiveSystemVersion;
                            }
                            
                            // 建立 inactive code 錯誤回應
                            Parameters errorResult = buildInactiveCodeError(
                                params, 
                                validationResult.display(), 
                                validationResult.codeSystem(), 
                                targetValueSet, 
                                effectiveSystemVersion, 
                                null
                            );
                            
                            removeNarratives(errorResult);
                            return errorResult;
                        }
                        
                        // 正常的有效 code
                        anyValid = true;
                        matchedConcept = validationResult.concept();
                        matchedCodeSystem = validationResult.codeSystem();
                        matchedDisplay = validationResult.display();
                        successfulParams = params;
                        
                        if (effectiveSystemVersion != null && 
                            (resolvedSystemVersion == null || resolvedSystemVersion.isEmpty())) {
                            resolvedSystemVersion = effectiveSystemVersion;
                        }
                        
                        break;
                    } else {
                        // 保存失敗的驗證上下文，使用有效的系統版本
                        failedValidationContext = new ValidationContext(
                            params.parameterSource(),
                            params.code(),
                            params.system(),
                            params.display(),
                            validationResult.errorType(),
                            targetValueSet,
                            effectiveSystemVersion,
                            validationResult.isInactive(),
                            params.originalCodeableConcept()
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
                        effectiveSystemVersion,
                        null,
                        params.originalCodeableConcept()
                    );
                }
            }

            if (!anyValid) {
            	Parameters errorResult = buildValidationErrorWithOutcome(false, failedValidationContext,
                        matchedCodeSystem, "Code validation failed");
                
                removeNarratives(errorResult);
                return errorResult;
            }
            
            StringType finalSystemVersion = determineEffectiveSystemVersion(successfulParams, resolvedSystemVersion);

            Parameters successResult = buildSuccessResponse(successfulParams, matchedDisplay, matchedCodeSystem, 
                    targetValueSet, finalSystemVersion, null);

            removeNarratives(successResult);
            return successResult;

        } catch (ResourceNotFoundException e) {
        	 // ValueSet 找不到時，直接返回 OperationOutcome
            if (e.getMessage() != null && e.getMessage().contains("ValueSet")) {
                String valueSetUrlForError = extractValueSetUrlFromException(e, url, urlCanonical, valueSetUrl, valueSetCanonical);
                return buildValueSetNotFoundOutcome(valueSetUrlForError, e.getMessage());
            }
            
            // 其他 ResourceNotFoundException 維持原有的 Parameters 回傳邏輯
            StringType resolvedSystemVersion = resolveSystemVersionParameter(systemVersion, systemVersionCode);
            ValidationContext errorContext = new ValidationContext(
                codeableConcept != null ? "codeableConcept" : "code",
                code,
                system,
                display,
                ValidationErrorType.GENERAL_ERROR,
                null,
                resolvedSystemVersion,
                null,
                codeableConcept 
            );
            Parameters exceptionResult = buildValidationErrorWithOutcome(false, errorContext, null, e.getMessage());
            
            removeNarratives(exceptionResult);
            return exceptionResult;
        } catch (InvalidRequestException e) {
        	// 對於無效請求，也返回 OperationOutcome
        	if (e.getMessage() != null && 
        	   (e.getMessage().contains("'url'") || 
        	    e.getMessage().contains("'valueSet'") || 
        	    e.getMessage().contains("resource ID"))) {
        	   return buildInvalidRequestOutcome(e.getMessage());
            }
            
            StringType resolvedSystemVersion = resolveSystemVersionParameter(systemVersion, systemVersionCode);
            ValidationContext errorContext = new ValidationContext(
                codeableConcept != null ? "codeableConcept" : "code",
                code,
                system,
                display,
                ValidationErrorType.GENERAL_ERROR,
                null,
                resolvedSystemVersion,
                null,
                codeableConcept 
            );
            Parameters exceptionResult = buildValidationErrorWithOutcome(false, errorContext, null, e.getMessage());
            
            removeNarratives(exceptionResult);
            return exceptionResult;
        } catch (Exception e) {
            StringType resolvedSystemVersion = resolveSystemVersionParameter(systemVersion, systemVersionCode);
            ValidationContext errorContext = new ValidationContext(
                codeableConcept != null ? "codeableConcept" : "code",
                code,
                system,
                display,
                ValidationErrorType.GENERAL_ERROR,
                null,
                resolvedSystemVersion,
                null,
                codeableConcept
            );
            Parameters internalErrorResult = buildValidationErrorWithOutcome(false, errorContext, null, "Internal error: " + e.getMessage());
            
            removeNarratives(internalErrorResult);
            return internalErrorResult;
        }
    }
    
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
        
        // 3. 如果沒有明確指定版本，嘗試根據 code 和 display 推斷版本
        if ((resolvedSystemVersion == null || resolvedSystemVersion.isEmpty()) && 
            params.code() != null && params.display() != null && params.system() != null) {
            
            StringType inferredVersion = inferVersionFromCodeAndDisplay(
                params.system().getValue(), 
                params.code().getValue(), 
                params.display().getValue()
            );
            
            if (inferredVersion != null) {
                return inferredVersion;
            }
        }
        
        // 4. 最後使用解析後的 resolvedSystemVersion
        return resolvedSystemVersion;
    }
    
    // 根據 code 和 display 推斷版本的方法
    private StringType inferVersionFromCodeAndDisplay(String systemUrl, String code, String display) {
        try {
            // 獲取該 CodeSystem 的所有版本
            List<CodeSystem> allVersions = findAllCodeSystemVersions(systemUrl);
            
            // 遍歷每個版本，找到 code 和 display 都匹配的版本
            for (CodeSystem codeSystem : allVersions) {
                ConceptDefinitionComponent concept = findConceptRecursive(codeSystem.getConcept(), code);
                if (concept != null) {
                    // 檢查 display 是否匹配
                    if (isDisplayMatching(concept, display)) {
                        return codeSystem.hasVersion() ? new StringType(codeSystem.getVersion()) : null;
                    }
                }
            }
            
            return null;
            
        } catch (Exception e) {
            return null;
        }
    }

    // 獲取 CodeSystem 所有版本的方法
    private List<CodeSystem> findAllCodeSystemVersions(String systemUrl) {
        var searchParams = new SearchParameterMap();
        searchParams.add(CodeSystem.SP_URL, new UriParam(systemUrl));
        
        // 設定排序以獲得一致的順序（最新版本優先）
        searchParams.setSort(new SortSpec(CodeSystem.SP_VERSION).setOrder(SortOrderEnum.DESC));
        
        var searchResult = myCodeSystemDao.search(searchParams, new SystemRequestDetails());
        
        List<CodeSystem> codeSystems = new ArrayList<>();
        for (int i = 0; i < searchResult.size(); i++) {
            codeSystems.add((CodeSystem) searchResult.getResources(i, i+1).get(0));
        }
        
        return codeSystems;
    }

    // 檢查 display 是否匹配的方法
    private boolean isDisplayMatching(ConceptDefinitionComponent concept, String requestedDisplay) {
        if (concept == null || requestedDisplay == null || requestedDisplay.isEmpty()) {
            return false;
        }
        
        // 檢查主要的 display
        if (concept.hasDisplay() && requestedDisplay.equalsIgnoreCase(concept.getDisplay())) {
            return true;
        }
        
        // 檢查所有的 designation
        for (ConceptDefinitionDesignationComponent designation : concept.getDesignation()) {
            if (requestedDisplay.equalsIgnoreCase(designation.getValue())) {
                return true;
            }
        }
        
        return false;
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
    
    private boolean isValueSetUrl(String systemUrl) {
        if (systemUrl == null || systemUrl.isEmpty()) {
            return false;
        }
        
        try {
            var searchParams = new SearchParameterMap();
            searchParams.add(ValueSet.SP_URL, new UriParam(systemUrl));
            var searchResult = myValueSetDao.search(searchParams, new SystemRequestDetails());
            
            return searchResult.size() > 0;
        } catch (Exception e) {
            return false;
        }
    }
    
    // validateCodeInValueSet 方法簽名
    private ValidationResult validateCodeInValueSet(ValueSet valueSet, CodeType code, UriType system,
            										StringType display, CodeType displayLanguage, 
            										BooleanType abstractAllowed, StringType systemVersion,
            										BooleanType activeOnly) {
    	
    	if (valueSet == null || code == null) {
            return new ValidationResult(false, null, null, null, ValidationErrorType.INVALID_CODE, null); 
        }
    	
    	// 新增：檢查 system 是否為空
        if (system == null || system.isEmpty()) {
            return new ValidationResult(false, null, null, null, 
                                      ValidationErrorType.NO_SYSTEM, null);
        }
        
        // 檢查是否為相對引用
        if (isRelativeReference(system.getValue())) {
            return new ValidationResult(false, null, null, null, 
                                      ValidationErrorType.RELATIVE_SYSTEM_REFERENCE, null);
        }
        
        // 檢查 system 是否實際上是 ValueSet URL
        if (isValueSetUrl(system.getValue())) {
            return new ValidationResult(false, null, null, null, 
                                      ValidationErrorType.SYSTEM_IS_VALUESET, null);
        }

        // 優先檢查 expansion
    	if (valueSet.hasExpansion() && isExpansionCurrent(valueSet.getExpansion())) {
            ValidationResult expansionResult = validateCodeInExpansion(
                valueSet.getExpansion(), code, system, display, displayLanguage, abstractAllowed, systemVersion, activeOnly);
            if (expansionResult.isValid()) {
                return expansionResult;
            }
        }

        // 處理 compose 規則
    	if (valueSet.hasCompose()) {
            return validateCodeInCompose(valueSet.getCompose(), code, system, display, 
                                       displayLanguage, abstractAllowed, systemVersion, activeOnly);
        }

    	return new ValidationResult(false, null, null, null, ValidationErrorType.CODE_NOT_IN_VALUESET, null);  // 添加第6個參數
    }

    private ValidationResult validateCodeInConceptSet(ConceptSetComponent conceptSet, CodeType code, 
            											UriType system, StringType display, 
            											CodeType displayLanguage, BooleanType abstractAllowed,
            											boolean isInclude, StringType resolvedSystemVersion,
            											BooleanType activeOnly) {
        
        // 檢查 system 是否匹配
    	if (system != null && conceptSet.hasSystem() && 
    	        !system.getValue().equals(conceptSet.getSystem())) {
    	        return new ValidationResult(false, null, null, null, ValidationErrorType.INVALID_CODE, null);  // 添加第6個參數
    	    }

        // 取得 CodeSystem - 改進版本處理邏輯（包含推斷）
        CodeSystem codeSystem = null;
        String systemUrl = conceptSet.hasSystem() ? conceptSet.getSystem() : 
                          (system != null ? system.getValue() : null);
        
        if (systemUrl != null) {
            try {
                // 傳入 code 和 display 以支援版本推斷
                String versionToUse = determineSystemVersion(conceptSet, resolvedSystemVersion, 
                                                           code, display, system);
                
                // 使用版本回退策略
                if (versionToUse != null) {
                    codeSystem = findCodeSystemWithVersionFallback(systemUrl, versionToUse);
                } else {
                    codeSystem = findCodeSystemByUrl(systemUrl, null);
                }
                
            } catch (ResourceNotFoundException e) {
                return new ValidationResult(false, null, null, null, ValidationErrorType.SYSTEM_NOT_FOUND, null);  // 添加第6個參數
            }
        }

        if (!conceptSet.getConcept().isEmpty()) {
            return validateCodeInConceptList(conceptSet.getConcept(), code, display, 
                                           displayLanguage, abstractAllowed, codeSystem);
        }

        if (!conceptSet.getFilter().isEmpty() && codeSystem != null) {
            return validateCodeWithFilters(conceptSet.getFilter(), codeSystem, code, display, 
                                         displayLanguage, abstractAllowed);
        }

        if (codeSystem != null) {
            ValidationResult result = validateCodeInCodeSystem(codeSystem, code, display, displayLanguage, abstractAllowed, activeOnly);
            
            return result;
        }

        return new ValidationResult(false, null, null, null, ValidationErrorType.INVALID_CODE, null);  // 添加第6個參數
    }
    
    private String determineSystemVersion(ConceptSetComponent conceptSet, 
								            StringType resolvedSystemVersion, 
								            CodeType code, 
								            StringType display, 
								            UriType system) {

		String conceptSetVersion = conceptSet.hasVersion() ? conceptSet.getVersion() : null;
		String requestVersion = (resolvedSystemVersion != null && !resolvedSystemVersion.isEmpty()) 
								? resolvedSystemVersion.getValue() : null;
		
		// 如果都沒有明確的版本資訊，嘗試推斷
		if (conceptSetVersion == null && requestVersion == null && 
				code != null && display != null && system != null) {
		
			StringType inferredVersion = inferVersionFromCodeAndDisplay(
				system.getValue(), code.getValue(), display.getValue());
		
			if (inferredVersion != null) {
				return inferredVersion.getValue();
			}
		}
		
		// 優先使用請求的版本
		if (requestVersion != null) {
			if (conceptSetVersion != null && !conceptSetVersion.equals(requestVersion)) {
			}
			return requestVersion;
		}

		// 如果沒有請求版本，使用 ValueSet 中定義的版本
		return conceptSetVersion;
    }
    
    private ValidationResult validateCodeInCompose(ValueSetComposeComponent compose, CodeType code, 
            										UriType system, StringType display, 
            										CodeType displayLanguage, BooleanType abstractAllowed,
            										StringType systemVersion, BooleanType activeOnly) {

        boolean foundInInclude = false;
        ValidationResult includeResult = null;
        
        // 檢查 include 部分
        for (ConceptSetComponent include : compose.getInclude()) {
            ValidationResult result = validateCodeInConceptSet(include, code, system, display, 
                                                             displayLanguage, abstractAllowed, true, systemVersion, activeOnly);
            if (result.isValid()) {
                foundInInclude = true;
                includeResult = result;
                break;
            }
        }
        
        if (!foundInInclude) {
            return new ValidationResult(false, null, null, null, ValidationErrorType.CODE_NOT_IN_VALUESET, null);  // 添加第6個參數
        }
        
        // 檢查 exclude 部分
        for (ConceptSetComponent exclude : compose.getExclude()) {
            ValidationResult result = validateCodeInConceptSet(exclude, code, system, display, 
                                                             displayLanguage, abstractAllowed, false, systemVersion, activeOnly);
            if (result.isValid()) {
                return new ValidationResult(false, result.concept(), result.codeSystem(), 
                                          result.display(), ValidationErrorType.CODE_NOT_IN_VALUESET, result.isInactive());
            }
        }
        
        return includeResult;
    }

    private ValidationResult validateCodeInExpansion(ValueSetExpansionComponent expansion, 
		            									CodeType code, UriType system, StringType display,
		            									CodeType displayLanguage, BooleanType abstractAllowed,
		            									StringType systemVersion, BooleanType activeOnly) {
		
    	for (ValueSetExpansionContainsComponent contains : expansion.getContains()) {
            ValidationResult result = validateCodeInExpansionContains(contains, code, system, display, 
                                                                    displayLanguage, abstractAllowed, 
                                                                    systemVersion, activeOnly);
            if (result.isValid()) {
                return result;
            }
        }
        
        return new ValidationResult(false, null, null, null, ValidationErrorType.CODE_NOT_IN_VALUESET, null);  // 添加第6個參數
    }

    private ValidationResult validateCodeInExpansionContains(ValueSetExpansionContainsComponent contains,
                                                           CodeType code, UriType system, StringType display,
                                                           CodeType displayLanguage, BooleanType abstractAllowed,
                                                           StringType resolvedSystemVersion, BooleanType activeOnly) {
        
        if (code.getValue().equals(contains.getCode()) && 
            (system == null || system.getValue().equals(contains.getSystem()))) {
            
            if (!isVersionMatch(contains, resolvedSystemVersion)) {
                // 版本不匹配的處理
            } else {
                // 檢查是否為抽象概念
                if (contains.hasAbstract() && contains.getAbstract() && 
                    (abstractAllowed == null || !abstractAllowed.getValue())) {
                    return new ValidationResult(false, null, null, null, 
                                              ValidationErrorType.ABSTRACT_CODE_NOT_ALLOWED, null);
                }
                
                // 驗證 display
                if (display != null && !display.isEmpty() && contains.hasDisplay() && 
                    !display.getValue().equalsIgnoreCase(contains.getDisplay())) {
                    return new ValidationResult(false, null, null, contains.getDisplay(), 
                                              ValidationErrorType.INVALID_DISPLAY, null);
                }
                
                // 檢查是否 inactive
                Boolean isInactive = contains.hasInactive() ? contains.getInactive() : false;
                
                return new ValidationResult(true, null, null, contains.getDisplay(), null, isInactive);
            }
        }
        
        // 遞歸檢查子概念
        for (ValueSetExpansionContainsComponent child : contains.getContains()) {
            ValidationResult result = validateCodeInExpansionContains(child, code, system, display, 
                                                                    displayLanguage, abstractAllowed, 
                                                                    resolvedSystemVersion, activeOnly);
            if (result.isValid()) {
                return result;
            }
        }
        
        return new ValidationResult(false, null, null, null, ValidationErrorType.INVALID_CODE, null);
    }
    
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
    
    private CodeSystem findCodeSystemWithVersionFallback(String url, String preferredVersion) {
        try {
            // 首先嘗試找指定版本
            return findCodeSystemByUrl(url, preferredVersion);
        } catch (ResourceNotFoundException e) {
            if (StringUtils.isNotBlank(preferredVersion)) {
                
                // 嘗試找最新版本
                try {
                    CodeSystem latestVersion = findLatestCodeSystemVersion(url);
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

    private Parameters buildSuccessResponse(ValidationParams successfulParams, String matchedDisplay, 
								            CodeSystem matchedCodeSystem, ValueSet targetValueSet,
								            StringType effectiveSystemVersion, Boolean isInactive) {
		Parameters result = new Parameters();

		// 1. code 參數
	    if (successfulParams != null && successfulParams.code() != null) {
	        result.addParameter("code", successfulParams.code());
	    }

	    // 2. display 參數
	    String displayToReturn = null;
	    if (successfulParams != null && successfulParams.display() != null && 
	        !successfulParams.display().isEmpty()) {
	        displayToReturn = successfulParams.display().getValue();
	    } else if (matchedDisplay != null) {
	        displayToReturn = matchedDisplay;
	    }
	    
	    if (displayToReturn != null) {
	        result.addParameter("display", new StringType(displayToReturn));
	    }

	    // 3. inactive 參數 (如果為 true 才加入)
	    if (isInactive != null && isInactive) {
	        result.addParameter("inactive", new BooleanType(true));
	    }

	    // 4. result 參數
	    result.addParameter("result", new BooleanType(true));

	    // 5. system 參數
	    if (successfulParams != null && successfulParams.system() != null && !successfulParams.system().isEmpty()) {
	        result.addParameter("system", successfulParams.system());
	    } else if (matchedCodeSystem != null && matchedCodeSystem.hasUrl()) {
	        result.addParameter("system", new UriType(matchedCodeSystem.getUrl()));
	    }

	    // 6. version 參數
	    String versionToReturn = null;
	    if (effectiveSystemVersion != null && !effectiveSystemVersion.isEmpty()) {
	        versionToReturn = effectiveSystemVersion.getValue();
	    } else if (matchedCodeSystem != null && matchedCodeSystem.hasVersion()) {
	        versionToReturn = matchedCodeSystem.getVersion();
	    }
	    
	    if (versionToReturn != null) {
	        result.addParameter("version", new StringType(versionToReturn));
	    }

	    // 7. codeableConcept 參數
	    if (successfulParams != null && successfulParams.originalCodeableConcept() != null) {
	        result.addParameter("codeableConcept", successfulParams.originalCodeableConcept());
	    }

	    return result;
    }

    private Parameters buildValidationErrorWithOutcome(boolean isValid, ValidationContext context,
                                                      CodeSystem codeSystem, String errorMessage) {
        Parameters result = new Parameters();
        
        // 確保不設置 meta
        result.setMeta(null);

        // 根據參數來源決定回傳格式
        if (context.originalCodeableConcept() != null) {
            // 使用原始的 codeableConcept
            result.addParameter("codeableConcept", context.originalCodeableConcept());
        } else if ("codeableConcept".equals(context.parameterSource()) || 
                   context.parameterSource().startsWith("codeableConcept.coding")) {
            // 如果沒有保存原始的,就重建
            CodeableConcept codeableConcept = reconstructCodeableConcept(context);
            result.addParameter("codeableConcept", codeableConcept);
        } else {
            // 如果來源是 code/coding,回傳 code 參數
            if (context.code() != null) {
                result.addParameter("code", context.code());
            }
        }

        // 建立 OperationOutcome
        OperationOutcome outcome = new OperationOutcome();
        outcome.setMeta(null);
        
        // 判斷是否為 codeableConcept 參數
        boolean isCodeableConcept = context.originalCodeableConcept() != null || 
                                    "codeableConcept".equals(context.parameterSource()) ||
                                    context.parameterSource().startsWith("codeableConcept.coding");
        
        // 根據錯誤類型動態建立 issues
        if (isCodeableConcept) {
            // CodeableConcept 的錯誤格式
            
            // Issue 1: TX_GENERAL_CC_ERROR_MESSAGE
            var generalCcIssue = new OperationOutcomeIssueBuilder()
                .setSeverity(OperationOutcome.IssueSeverity.ERROR)
                .setCode(OperationOutcome.IssueType.CODEINVALID)
                .setMessageId(OperationOutcomeMessageId.TX_GENERAL_CC_ERROR)
                .setDetails("not-in-vs", String.format(
                    "No valid coding was found for the value set '%s|%s'",
                    context.valueSet() != null && context.valueSet().hasUrl() ? context.valueSet().getUrl() : "",
                    context.valueSet() != null && context.valueSet().hasVersion() ? context.valueSet().getVersion() : "5.0.0"))
                .build();
            outcome.addIssue(generalCcIssue);
            
            // Issue 2: UNKNOWN_CODESYSTEM (如果是 system not found 錯誤)
            if (codeSystem == null || context.errorType() == ValidationErrorType.SYSTEM_NOT_FOUND) {
                String systemUrl = context.system() != null ? context.system().getValue() : "";
                var unknownSystemIssue = new OperationOutcomeIssueBuilder()
                    .setSeverity(OperationOutcome.IssueSeverity.ERROR)
                    .setCode(OperationOutcome.IssueType.NOTFOUND)
                    .setMessageId(OperationOutcomeMessageId.UNKNOWN_CODESYSTEM)
                    .setDetails("not-found", String.format("$external:2:%s$", systemUrl))
                    .build();
                
                // 設置 location 和 expression
                int codingIndex = extractCodingIndex(context.parameterSource());
                unknownSystemIssue.addLocation(String.format("CodeableConcept.coding[%d].system", codingIndex));
                unknownSystemIssue.addExpression(String.format("CodeableConcept.coding[%d].system", codingIndex));
                outcome.addIssue(unknownSystemIssue);
            }
            
            // Issue 3: None_of_the_provided_codes_are_in_the_value_set_one
            String systemUrl = context.system() != null ? context.system().getValue() : "";
            String code = context.code() != null ? context.code().getValue() : "";
            var notInVsIssue = new OperationOutcomeIssueBuilder()
                .setSeverity(OperationOutcome.IssueSeverity.INFORMATION)
                .setCode(OperationOutcome.IssueType.CODEINVALID)
                .setMessageId(OperationOutcomeMessageId.NONE_OF_CODES_IN_VALUE_SET_ONE)
                .setDetails("this-code-not-in-vs", String.format("$external:1:%s#%s$", systemUrl, code))
                .build();
            
            int codingIndex = extractCodingIndex(context.parameterSource());
            notInVsIssue.addLocation(String.format("CodeableConcept.coding[%d].code", codingIndex));
            notInVsIssue.addExpression(String.format("CodeableConcept.coding[%d].code", codingIndex));
            outcome.addIssue(notInVsIssue);
            
        } else {
            // 原有的 code/coding 參數的錯誤格式
            if (context.errorType() == ValidationErrorType.SYSTEM_NOT_FOUND && 
                context.systemVersion() != null && !context.systemVersion().isEmpty()) {
                
                outcome.addIssue(OperationOutcomeHelper.createCodeSystemVersionNotFoundIssue(context));
                outcome.addIssue(OperationOutcomeHelper.createValueSetValidationWarningIssue(context));
                
            } else {
                outcome.addIssue(OperationOutcomeHelper.createGeneralValidationErrorIssue(context));
                
                if (context.errorType() == ValidationErrorType.INVALID_CODE || 
                    context.errorType() == ValidationErrorType.CODE_NOT_IN_VALUESET) {
                    outcome.addIssue(OperationOutcomeHelper.createCodeNotInCodeSystemIssue(context, codeSystem));
                }
                
                outcome.addIssue(OperationOutcomeHelper.createCodeNotInValueSetIssue(context));
            }
            
            // 添加其他特定錯誤類型的 issue
            addSpecificErrorIssues(outcome, context, codeSystem);
        }

        // 添加 issues 參數
        result.addParameter().setName("issues").setResource(outcome);

        // 添加其他參數
        String mainErrorMessage = buildMessageErrorText(context, codeSystem);
        result.addParameter("message", new StringType(mainErrorMessage));
        result.addParameter("result", new BooleanType(isValid));

        // 只在非 codeableConcept 的情況下添加 system 和 version
        if (!"codeableConcept".equals(context.parameterSource())) {
            if (context.system() != null) {
                result.addParameter("system", context.system());
            }

            if (context.systemVersion() != null && context.systemVersion().hasValue()) {
                result.addParameter("version", context.systemVersion());
            }
        }

        // 添加 x-unknown-system 參數（當 system not found 時）
        if (context.errorType() == ValidationErrorType.SYSTEM_NOT_FOUND && context.system() != null) {
            result.addParameter("x-unknown-system", new CanonicalType(context.system().getValue()));
        }

        removeNarratives(result);
        
        return result;
    }
    
    // 新增輔助方法：從 parameterSource 中提取 coding index
    private int extractCodingIndex(String parameterSource) {
        if (parameterSource != null && parameterSource.startsWith("codeableConcept.coding[")) {
            try {
                int start = parameterSource.indexOf('[') + 1;
                int end = parameterSource.indexOf(']');
                return Integer.parseInt(parameterSource.substring(start, end));
            } catch (Exception e) {
                return 0;
            }
        }
        return 0;
    }
    
    private void removeNarratives(Parameters parameters) {
        if (parameters == null) {
            return;
        }
        
        // 清除 Parameters 本身的 meta
        if (parameters.hasMeta()) {
            parameters.setMeta(null);
        }
        
        for (ParametersParameterComponent param : parameters.getParameter()) {
            if (param.hasResource() && param.getResource() instanceof DomainResource) {
                DomainResource domainResource = (DomainResource) param.getResource();

                domainResource.setText(null);
                
                if (domainResource.hasMeta()) {
                    domainResource.setMeta(null);
                }
            }
            
            // 遞歸處理嵌套的參數
            if (param.hasPart()) {
                for (ParametersParameterComponent part : param.getPart()) {
                    if (part.hasResource() && part.getResource() instanceof DomainResource) {
                        DomainResource domainResource = (DomainResource) part.getResource();
                        domainResource.setText(null);
                        
                        // 清除 meta
                        if (domainResource.hasMeta()) {
                            domainResource.setMeta(null);
                        }
                    }
                }
            }
        }
    }

    // 為 message 參數建立的訊息格式
    private String buildMessageErrorText(ValidationContext context, CodeSystem codeSystem) {
        String systemUrl = null;

        if (context.system() != null) {
            systemUrl = context.system().getValue();
        } else if (codeSystem != null && codeSystem.hasUrl()) {
            systemUrl = codeSystem.getUrl();
        }

        if (systemUrl != null) {
            return String.format("$external:3:%s$", systemUrl);
        }

        return "$external:3:CodeSystem$";
    }

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
        if (context.systemVersion() != null && context.systemVersion().hasValue()) {
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

    private boolean isExpansionCurrent(ValueSetExpansionComponent expansion) {
        if (!expansion.hasTimestamp()) {
            return false;
        }
        return true;
    }

    private ValidationResult validateCodeWithFilters(List<ConceptSetFilterComponent> filters, 
                                                    CodeSystem codeSystem, CodeType code, 
                                                    StringType display, CodeType displayLanguage, 
                                                    BooleanType abstractAllowed) {
        
        ConceptDefinitionComponent concept = findConceptRecursive(codeSystem.getConcept(), code.getValue());
        if (concept == null) {
            return new ValidationResult(false, null, codeSystem, null, ValidationErrorType.INVALID_CODE, null);  // 添加第6個參數
        }

        // 檢查所有 filter 條件
        for (ConceptSetFilterComponent filter : filters) {
            if (!evaluateFilter(filter, concept, codeSystem)) {
                return new ValidationResult(false, concept, codeSystem, null, 
                                          ValidationErrorType.CODE_NOT_IN_VALUESET, null);  // 添加第6個參數
            }
        }

        // 通過所有 filter，進行其他驗證
        if (isAbstractConcept(concept) && (abstractAllowed == null || !abstractAllowed.getValue())) {
            return new ValidationResult(false, concept, codeSystem, null, 
                                      ValidationErrorType.ABSTRACT_CODE_NOT_ALLOWED, null);  // 添加第6個參數
        }

        boolean isValidDisplay = isDisplayValidExtended(concept, display, displayLanguage);
        if (!isValidDisplay && display != null && !display.isEmpty()) {
            return new ValidationResult(false, concept, codeSystem, null, 
                                      ValidationErrorType.INVALID_DISPLAY, null);  // 添加第6個參數
        }

        String resolvedDisplay = getDisplayForLanguage(concept, displayLanguage);
        return new ValidationResult(true, concept, codeSystem, resolvedDisplay, null, null);  // 添加第6個參數
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
                                                      ValidationErrorType.ABSTRACT_CODE_NOT_ALLOWED, null);  // 添加第6個參數
                        }
                        
                        boolean isValidDisplay = isDisplayValidExtended(fullConcept, display, displayLanguage);
                        if (!isValidDisplay && display != null && !display.isEmpty()) {
                            return new ValidationResult(false, fullConcept, codeSystem, null, 
                                                      ValidationErrorType.INVALID_DISPLAY, null);  // 添加第6個參數
                        }
                        
                        String resolvedDisplay = getDisplayForLanguage(fullConcept, displayLanguage);
                        return new ValidationResult(true, fullConcept, codeSystem, resolvedDisplay, null, null);  // 添加第6個參數
                    }
                }
                
                String conceptDisplay = concept.hasDisplay() ? concept.getDisplay() : null;
                if (display != null && !display.isEmpty() && conceptDisplay != null && 
                    !display.getValue().equalsIgnoreCase(conceptDisplay)) {
                    return new ValidationResult(false, null, codeSystem, conceptDisplay, 
                                              ValidationErrorType.INVALID_DISPLAY, null);  // 添加第6個參數
                }
                
                return new ValidationResult(true, null, codeSystem, conceptDisplay, null, null);  // 添加第6個參數
            }
        }
        
        return new ValidationResult(false, null, codeSystem, null, ValidationErrorType.INVALID_CODE, null);  // 添加第6個參數
    }

    private ValidationResult validateCodeInCodeSystem(CodeSystem codeSystem, CodeType code, 
            											StringType display, CodeType displayLanguage, 
            											BooleanType abstractAllowed, BooleanType activeOnly) {

    	ConceptDefinitionComponent concept = findConceptRecursive(codeSystem.getConcept(), code.getValue());
        if (concept == null) {
            return new ValidationResult(false, null, codeSystem, null, ValidationErrorType.INVALID_CODE, null);
        }

        // 檢查 abstract
        if (isAbstractConcept(concept) && (abstractAllowed == null || !abstractAllowed.getValue())) {
            return new ValidationResult(false, concept, codeSystem, null, 
                                      ValidationErrorType.ABSTRACT_CODE_NOT_ALLOWED, null);
        }

        // 檢查 inactive 狀態
        Boolean isInactive = isConceptInactive(concept);

        // Display 驗證
        boolean isValidDisplay = isDisplayValidExtended(concept, display, displayLanguage);
        if (!isValidDisplay && display != null && !display.isEmpty()) {
            // Display 不匹配
        }

        String resolvedDisplay = getDisplayForLanguage(concept, displayLanguage);

        return new ValidationResult(true, concept, codeSystem, resolvedDisplay, null, isInactive);
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
	 private boolean isDisplayValidExtended(ConceptDefinitionComponent concept, StringType display, 
	                                      CodeType displayLanguage) {
	     if (display == null || display.isEmpty()) {
	         return true;
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
	            outcome.addIssue(OperationOutcomeHelper.createAbstractCodeNotAllowedIssue());
	            break;
	        
	        case INVALID_DISPLAY:
	            outcome.addIssue(OperationOutcomeHelper.createInvalidDisplayIssue(context));
	            break;

	    }
    }

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
            String versionInfo = version != null ? " (version: " + version + ")" : "";
            throw new ResourceNotFoundException(
                String.format("ValueSet with URL '%s'%s not found", url, versionInfo));
        }
        
        return (ValueSet) searchResult.getResources(0, 1).get(0);
    }

    private CodeSystem findCodeSystemByUrl(String url, String version) {
        
        var searchParams = new SearchParameterMap();
        searchParams.add(CodeSystem.SP_URL, new UriParam(url));
        
        // 如果指定了版本，先嘗試精確匹配
        if (StringUtils.isNotBlank(version)) {
            searchParams.add(CodeSystem.SP_VERSION, new TokenParam(version));
            
            var searchResult = myCodeSystemDao.search(searchParams, new SystemRequestDetails());
            
            if (searchResult.size() > 0) {
                CodeSystem codeSystem = (CodeSystem) searchResult.getResources(0, 1).get(0);
                return codeSystem;
            }
            
            // 查詢所有版本以提供更好的錯誤信息
            var allVersionsParams = new SearchParameterMap();
            allVersionsParams.add(CodeSystem.SP_URL, new UriParam(url));
            var allVersionsResult = myCodeSystemDao.search(allVersionsParams, new SystemRequestDetails());
            
            if (allVersionsResult.size() > 0) {
                for (int i = 0; i < Math.min(allVersionsResult.size(), 10); i++) {
                    CodeSystem cs = (CodeSystem) allVersionsResult.getResources(i, i+1).get(0);
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
        
        return codeSystem;
    }

    // 遞歸查找概念
    private ConceptDefinitionComponent findConceptRecursive(List<ConceptDefinitionComponent> concepts, 
                                                           String code) {
    	
    	for (ConceptDefinitionComponent concept : concepts) {
            if (code.equals(concept.getCode())) {
                return concept;
            }
            ConceptDefinitionComponent found = findConceptRecursive(concept.getConcept(), code);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

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
    
    private Boolean isConceptInactive(ConceptDefinitionComponent concept) {
        if (concept == null) {
            return null;
        }
        
        // 檢查 inactive 屬性
        for (ConceptPropertyComponent property : concept.getProperty()) {
            if ("inactive".equals(property.getCode())) {
                if (property.getValue() instanceof BooleanType) {
                    return ((BooleanType) property.getValue()).getValue();
                }
            }
        }
        
        // 檢查 status 屬性
        for (ConceptPropertyComponent property : concept.getProperty()) {
            if ("status".equals(property.getCode())) {
                String status = null;
                if (property.getValue() instanceof CodeType) {
                    status = ((CodeType) property.getValue()).getValue();
                } else if (property.getValue() instanceof StringType) {
                    status = ((StringType) property.getValue()).getValue();
                }
                
                if (status != null) {
                    return "retired".equals(status) || 
                           "deprecated".equals(status) || 
                           "withdrawn".equals(status) ||
                           "inactive".equals(status);
                }
            }
        }
        
        // 默認為 active
        return false;
    }    
    
    private Parameters buildInactiveCodeError(ValidationParams successfulParams, String matchedDisplay, 
								            CodeSystem matchedCodeSystem, ValueSet targetValueSet,
								            StringType effectiveSystemVersion, ValidationContext context) {
		Parameters result = new Parameters();
		
		// 1. code 參數
        if (successfulParams != null && successfulParams.code() != null) {
            result.addParameter("code", successfulParams.code());
        }
        
        // 2. display 參數
        String displayToReturn = null;
        if (successfulParams != null && successfulParams.display() != null && 
                !successfulParams.display().isEmpty()) {
            displayToReturn = successfulParams.display().getValue();
        } else if (matchedDisplay != null) {
            displayToReturn = matchedDisplay;
        }
        
        if (displayToReturn != null) {
            result.addParameter("display", new StringType(displayToReturn));
        }
        
        // 3. inactive 參數
        result.addParameter("inactive", new BooleanType(true));
        
        // 4. 建立 OperationOutcome
        OperationOutcome outcome = new OperationOutcome();
        outcome.setMeta(null);
        
        // 使用 Helper 建立 issues
        outcome.addIssue(OperationOutcomeHelper.createInactiveCodeIssue(
            successfulParams.code().getValue()));
        
        // Issue 2: Not in ValueSet error
        var notInVsIssue = new OperationOutcomeIssueBuilder()
            .setSeverity(OperationOutcome.IssueSeverity.ERROR)
            .setCode(OperationOutcome.IssueType.CODEINVALID)
            .setMessageId(OperationOutcomeMessageId.NONE_OF_CODES_IN_VALUE_SET_ONE)
            .setDetails("not-in-vs", buildNotInValueSetMessage(
                successfulParams, targetValueSet))
            .build();
        
        notInVsIssue.addLocation("Coding.code");
        notInVsIssue.addExpression("Coding.code");
        outcome.addIssue(notInVsIssue);
        
        // 5-9. 其他參數...
        result.addParameter().setName("issues").setResource(outcome);
        result.addParameter("message", new StringType(buildNotInValueSetMessage(
            successfulParams, targetValueSet)));
        result.addParameter("result", new BooleanType(false));
        
        if (successfulParams.system() != null) {
            result.addParameter("system", successfulParams.system());
        }
        
        if (effectiveSystemVersion != null && !effectiveSystemVersion.isEmpty()) {
            result.addParameter("version", effectiveSystemVersion);
        } else if (matchedCodeSystem != null && matchedCodeSystem.hasVersion()) {
            result.addParameter("version", new StringType(matchedCodeSystem.getVersion()));
        }
        
        return result;
    }
    
    private String buildNotInValueSetMessage(ValidationParams params, ValueSet valueSet) {
        String systemUrl = (params.system() != null) ? params.system().getValue() : "";
        String valueSetUrl = (valueSet != null && valueSet.hasUrl()) ? valueSet.getUrl() : "";
        String valueSetVersion = (valueSet != null && valueSet.hasVersion()) ? 
            valueSet.getVersion() : "5.0.0";
        
        return String.format(
            "The provided code '%s#%s' was not found in the value set '%s|%s'",
            systemUrl, params.code().getValue(), valueSetUrl, valueSetVersion
        );
    }
    
    private CodeableConcept reconstructCodeableConcept(ValidationContext context) {
        CodeableConcept codeableConcept = new CodeableConcept();
        Coding coding = new Coding();
        
        if (context.system() != null && !context.system().isEmpty()) {
            coding.setSystem(context.system().getValue());
        }
        if (context.code() != null && !context.code().isEmpty()) {
            coding.setCode(context.code().getValue());
        }
        if (context.display() != null && !context.display().isEmpty()) {
            coding.setDisplay(context.display().getValue());
        }
        if (context.systemVersion() != null && !context.systemVersion().isEmpty()) {
            coding.setVersion(context.systemVersion().getValue());
        }
        
        codeableConcept.addCoding(coding);
        return codeableConcept;
    }
    
    private OperationOutcome buildValueSetNotFoundOutcome(String valueSetUrl, String errorMessage) {
        return OperationOutcomeHelper.createValueSetNotFoundOutcome(valueSetUrl, errorMessage);
    }
    

    private String extractValueSetUrlFromException(ResourceNotFoundException e, 
                                                  UriType url, 
                                                  CanonicalType urlCanonical,
                                                  UriType valueSetUrl, 
                                                  CanonicalType valueSetCanonical) {
        // 優先從參數中提取
        UriType resolvedUrl = resolveUriParameter(url, urlCanonical);
        if (resolvedUrl != null && !resolvedUrl.isEmpty()) {
            return resolvedUrl.getValue();
        }
        
        UriType resolvedValueSetUrl = resolveUriParameter(valueSetUrl, valueSetCanonical);
        if (resolvedValueSetUrl != null && !resolvedValueSetUrl.isEmpty()) {
            return resolvedValueSetUrl.getValue();
        }
        
        // 嘗試從異常訊息中解析
        String message = e.getMessage();
        if (message != null && message.contains("'") && message.contains("not found")) {
            int start = message.indexOf("'") + 1;
            int end = message.indexOf("'", start);
            if (end > start) {
                return message.substring(start, end);
            }
        }
        
        return null;
    }
    
    private OperationOutcome buildInvalidRequestOutcome(String errorMessage) {
        return OperationOutcomeHelper.createInvalidRequestOutcome(errorMessage);
    }
    
    private Parameters buildSystemIsValueSetError(ValidationParams params, 
                                                 ValueSet targetValueSet,
                                                 StringType effectiveSystemVersion) {
        Parameters result = new Parameters();
        
        // 1. code 參數
        if (params.code() != null) {
            result.addParameter("code", params.code());
        }
        
        // 2. 建立 OperationOutcome
        OperationOutcome outcome = new OperationOutcome();
        outcome.setMeta(null);
        
        String systemUrl = params.system() != null ? params.system().getValue() : "";
        String valueSetUrl = targetValueSet != null && targetValueSet.hasUrl() ? 
            targetValueSet.getUrl() : "";
        String valueSetVersion = targetValueSet != null && targetValueSet.hasVersion() ? 
            targetValueSet.getVersion() : "5.0.0";
        
        // Issue 1: Code not in ValueSet
        var notInVsIssue = new OperationOutcomeIssueBuilder()
            .setSeverity(OperationOutcome.IssueSeverity.ERROR)
            .setCode(OperationOutcome.IssueType.CODEINVALID)
            .setMessageId(OperationOutcomeMessageId.NONE_OF_CODES_IN_VALUE_SET_ONE)
            .setDetails("not-in-vs", String.format(
                "The provided code '%s#%s' was not found in the value set '%s|%s'",
                systemUrl, params.code().getValue(), valueSetUrl, valueSetVersion))
            .build();
        
        notInVsIssue.addLocation("Coding.code");
        notInVsIssue.addExpression("Coding.code");
        outcome.addIssue(notInVsIssue);
        
        // Issue 2: System is ValueSet error
        var systemIsVsIssue = new OperationOutcomeIssueBuilder()
            .setSeverity(OperationOutcome.IssueSeverity.ERROR)
            .setCode(OperationOutcome.IssueType.INVALID)
            .setMessageId(OperationOutcomeMessageId.TERMINOLOGY_TX_SYSTEM_VALUESET2)
            .setDetails("invalid-data", String.format(
                "The Coding references a value set, not a code system ('%s')",
                systemUrl))
            .build();
        
        systemIsVsIssue.addLocation("Coding.system");
        systemIsVsIssue.addExpression("Coding.system");
        outcome.addIssue(systemIsVsIssue);
        
        // 3. issues 參數
        result.addParameter().setName("issues").setResource(outcome);
        
        // 4. message 參數
        String message = String.format(
            "The Coding references a value set, not a code system ('%s'); " +
            "The provided code '%s#%s' was not found in the value set '%s|%s'",
            systemUrl, systemUrl, params.code().getValue(), valueSetUrl, valueSetVersion);
        result.addParameter("message", new StringType(message));
        
        // 5. result 參數
        result.addParameter("result", new BooleanType(false));
        
        // 6. system 參數
        if (params.system() != null) {
            result.addParameter("system", params.system());
        }
        
        return result;
    }
    
    private Parameters buildRelativeSystemError(ValidationParams params, 
			            						ValueSet targetValueSet,
			            						StringType effectiveSystemVersion) {
		Parameters result = new Parameters();
			
		// 1. code 參數
		if (params.code() != null) {
			result.addParameter("code", params.code());
		}
			
		// 2. 建立 OperationOutcome
		OperationOutcome outcome = new OperationOutcome();
		outcome.setMeta(null);
			
		String systemUrl = params.system() != null ? params.system().getValue() : "";
		String valueSetUrl = targetValueSet != null && targetValueSet.hasUrl() ? 
		targetValueSet.getUrl() : "";
		String valueSetVersion = targetValueSet != null && targetValueSet.hasVersion() ? 
		targetValueSet.getVersion() : "5.0.0";
			
		// Issue 1: Code not in ValueSet
		var notInVsIssue = new OperationOutcomeIssueBuilder()
	            .setSeverity(OperationOutcome.IssueSeverity.ERROR)
				.setCode(OperationOutcome.IssueType.CODEINVALID)
				.setMessageId(OperationOutcomeMessageId.NONE_OF_CODES_IN_VALUE_SET_ONE)
				.setDetails("not-in-vs", String.format(
						"The provided code '%s#%s' was not found in the value set '%s|%s'",
						systemUrl, params.code().getValue(), valueSetUrl, valueSetVersion))
				.build();
			
		notInVsIssue.addLocation("Coding.code");
		notInVsIssue.addExpression("Coding.code");
		outcome.addIssue(notInVsIssue);
			
		// Issue 2: Relative system reference error
		var relativeSystemIssue = new OperationOutcomeIssueBuilder()
			.setSeverity(OperationOutcome.IssueSeverity.ERROR)
			.setCode(OperationOutcome.IssueType.INVALID)
			.setMessageId(OperationOutcomeMessageId.TERMINOLOGY_TX_SYSTEM_RELATIVE)
			.setDetails("invalid-data", 
			"Coding.system must be an absolute reference, not a local reference")
			.build();
			
		relativeSystemIssue.addLocation("Coding.system");
		relativeSystemIssue.addExpression("Coding.system");
		outcome.addIssue(relativeSystemIssue);
			
		// Issue 3: Unknown CodeSystem error
		var unknownSystemIssue = new OperationOutcomeIssueBuilder()
			.setSeverity(OperationOutcome.IssueSeverity.ERROR)
			.setCode(OperationOutcome.IssueType.NOTFOUND)
			.setMessageId(OperationOutcomeMessageId.UNKNOWN_CODESYSTEM)
			.setDetails("not-found", String.format(
			"A definition for CodeSystem %s could not be found, so the code cannot be validated",
			systemUrl))
			.build();
			
		unknownSystemIssue.addLocation("Coding.system");
		unknownSystemIssue.addExpression("Coding.system");
		outcome.addIssue(unknownSystemIssue);
			
		// 3. issues 參數
		result.addParameter().setName("issues").setResource(outcome);
			
		// 4. message 參數
		String message = String.format(
			"A definition for CodeSystem %s could not be found, so the code cannot be validated; " +
			"The provided code '%s#%s' was not found in the value set '%s|%s'",
			systemUrl, systemUrl, params.code().getValue(), valueSetUrl, valueSetVersion);
			result.addParameter("message", new StringType(message));
			
		// 5. result 參數
		result.addParameter("result", new BooleanType(false));
			
		// 6. system 參數
		if (params.system() != null) {
			result.addParameter("system", params.system());
		}
			
		// 7. x-unknown-system 參數
		if (params.system() != null) {
			result.addParameter("x-unknown-system", new CanonicalType(systemUrl));
		}
			
		return result;
	}
    
    private Parameters buildNoSystemError(ValidationParams params, 
		            					  ValueSet targetValueSet,
		            					  StringType effectiveSystemVersion) {
		Parameters result = new Parameters();
		
		// 1. code 參數
		if (params.code() != null) {
			result.addParameter("code", params.code());
		}
		
		// 2. 建立 OperationOutcome
		OperationOutcome outcome = new OperationOutcome();
		outcome.setMeta(null);
		
		String valueSetUrl = targetValueSet != null && targetValueSet.hasUrl() ? 
		targetValueSet.getUrl() : "";
		String valueSetVersion = targetValueSet != null && targetValueSet.hasVersion() ? 
		targetValueSet.getVersion() : "5.0.0";
		
		// Issue 1: Code not in ValueSet
		var notInVsIssue = new OperationOutcomeIssueBuilder()
				.setSeverity(OperationOutcome.IssueSeverity.ERROR)
				.setCode(OperationOutcome.IssueType.CODEINVALID)
				.setMessageId(OperationOutcomeMessageId.NONE_OF_CODES_IN_VALUE_SET_ONE)
				.setDetails("not-in-vs", String.format(
				"The provided code '#%s' was not found in the value set '%s|%s'",
				params.code().getValue(), valueSetUrl, valueSetVersion))
				.build();
		
		notInVsIssue.addLocation("Coding.code");
		notInVsIssue.addExpression("Coding.code");
		outcome.addIssue(notInVsIssue);
		
		// Issue 2: No system warning
		var noSystemIssue = new OperationOutcomeIssueBuilder()
				.setSeverity(OperationOutcome.IssueSeverity.WARNING)
				.setCode(OperationOutcome.IssueType.INVALID)
				.setMessageId(OperationOutcomeMessageId.CODING_HAS_NO_SYSTEM_CANNOT_VALIDATE)
				.setDetails("invalid-data", 
						"Coding has no system. A code with no system has no defined meaning, " +
						"and it cannot be validated. A system should be provided")
				.build();
		
		noSystemIssue.addLocation("Coding");
		noSystemIssue.addExpression("Coding");
		outcome.addIssue(noSystemIssue);
		
		// 3. issues 參數
		result.addParameter().setName("issues").setResource(outcome);
		
		// 4. message 參數
		String message = String.format(
				"Coding has no system. A code with no system has no defined meaning, " +
						"and it cannot be validated. A system should be provided; " +
						"The provided code '#%s' was not found in the value set '%s|%s'",
						params.code().getValue(), valueSetUrl, valueSetVersion);
		result.addParameter("message", new StringType(message));
		
		// 5. result 參數
		result.addParameter("result", new BooleanType(false));
		
		return result;
	}    
    
    private boolean isRelativeReference(String systemUrl) {
        if (systemUrl == null || systemUrl.isEmpty()) {
            return false;
        }
        // 檢查是否為絕對 URL（包含 scheme）
        return !systemUrl.matches("^[a-zA-Z][a-zA-Z0-9+.-]*:.*");
    }  
}