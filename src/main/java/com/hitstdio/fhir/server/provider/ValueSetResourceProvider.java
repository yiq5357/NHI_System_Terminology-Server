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
import org.hl7.fhir.instance.model.api.IBaseDatatype;
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
            @OperationParam(name = "system") UrlType systemUrlType,
            @OperationParam(name = "system") UriType system,
            @OperationParam(name = "system") CanonicalType systemCanonical,
            @OperationParam(name = "systemVersion") StringType systemVersion,
            @OperationParam(name = "systemVersion") CodeType systemVersionCode,
            @OperationParam(name = "url") UrlType urlType,
            @OperationParam(name = "url") UriType url,
            @OperationParam(name = "url") CanonicalType urlCanonical,
            @OperationParam(name = "valueSet") UrlType valueSetUrlType,
            @OperationParam(name = "valueSet") UriType valueSetUrl,
            @OperationParam(name = "valueSet") CanonicalType valueSetCanonical,
            @OperationParam(name = "version") StringType version,
            @OperationParam(name = "display") StringType display,
            @OperationParam(name = "coding") Coding coding,
            @OperationParam(name = "codeableConcept") CodeableConcept codeableConcept,
            @OperationParam(name = "displayLanguage") CodeType displayLanguage,
            @OperationParam(name = "abstract") BooleanType abstractAllowed,
            @OperationParam(name = "activeOnly") BooleanType activeOnly,
            @OperationParam(name = "inferSystem") BooleanType inferSystem,
            @OperationParam(name = "lenient-display-validation") BooleanType lenientDisplayValidation,
            @OperationParam(name = "valueset-membership-only") BooleanType valuesetMembershipOnly
    ) {
    	
    	// 在所有驗證之前先檢查 displayLanguage 的有效性
    	if (displayLanguage != null && !displayLanguage.isEmpty()) {
            if (!isValidLanguageCode(displayLanguage.getValue())) {
                // 直接拋出異常，不繼續執行
                throw buildInvalidLanguageCodeException(displayLanguage);
            }
        }    	
    	
        try { 
        	        	
        	// 判斷是否為 membership-only 模式
            boolean isMembershipOnlyMode = valuesetMembershipOnly != null && valuesetMembershipOnly.getValue();
        	
        	// 解析系統 URL
            UriType resolvedSystem = resolveUriParameter(system, systemCanonical, systemUrlType);
            UriType resolvedUrl = resolveUriParameter(url, urlCanonical, urlType);
            UriType resolvedValueSetUrl = resolveUriParameter(valueSetUrl, valueSetCanonical, valueSetUrlType);
        	
            // 解析 systemVersion
            StringType resolvedSystemVersion = resolveSystemVersionParameter(systemVersion, systemVersionCode);
            
            // 處理 inferSystem 邏輯
            if ((resolvedSystem == null || resolvedSystem.isEmpty()) && 
                inferSystem != null && inferSystem.getValue() && 
                code != null && !code.isEmpty()) {
                
                // 先獲取 ValueSet
                ValueSet targetValueSet = null;
                if (resourceId != null) {
                    targetValueSet = getValueSetById(resourceId.getIdPart(), version);
                } else {                  
                    if (resolvedUrl != null) {
                        targetValueSet = findValueSetByUrl(resolvedUrl.getValue(), 
                            version != null ? version.getValue() : null);
                    } else if (resolvedValueSetUrl != null) {
                        targetValueSet = findValueSetByUrl(resolvedValueSetUrl.getValue(), 
                            version != null ? version.getValue() : null);
                    }
                }
                
                if (targetValueSet != null) {
                    resolvedSystem = inferSystemFromValueSet(targetValueSet, code, display);
                    
                    if (resolvedSystem == null || resolvedSystem.isEmpty()) {
                        Parameters errorResult = buildUnableToInferSystemError(
                            code, targetValueSet, display);
                        removeNarratives(errorResult);
                        return errorResult;
                    }                   
                }
            }

            if (coding != null && coding.hasVersion() && 
                (resolvedSystemVersion == null || resolvedSystemVersion.isEmpty())) {
                resolvedSystemVersion = new StringType(coding.getVersion());
            }
            
            // 解析 ValueSet URL
            ValueSet targetValueSet = null;
            
            if (resourceId != null) {
                targetValueSet = getValueSetById(resourceId.getIdPart(), version);
            } else {
              //  UriType resolvedUrl = resolveUriParameter(url, urlCanonical);
              //  UriType resolvedValueSetUrl = resolveUriParameter(valueSetUrl, valueSetCanonical);
                
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

            var paramsList = extractMultipleValidationParams(code, resolvedSystem, display, coding, codeableConcept);
            boolean anyValid = false;
            ConceptDefinitionComponent matchedConcept = null;
            CodeSystem matchedCodeSystem = null;
            ValidationContext failedValidationContext = null;
            String matchedDisplay = null;
            ValidationParams successfulParams = null; 
            boolean hasDisplayWarning = false;
            String incorrectDisplay = null;
            
            List<ValidationContext> allValidationContexts = new ArrayList<>();
            boolean foundValidCode = false;
            
            for (var params : paramsList) {
               
                validateValidationParams(params.code(), params.system(), resourceId, url, valueSetUrl);

                try {
                    StringType effectiveSystemVersion = determineEffectiveSystemVersion(
                        params, resolvedSystemVersion);

                    ValidationResult validationResult = validateCodeInValueSet(
                        targetValueSet, 
                        params.code(), 
                        params.system(), 
                        params.display(), 
                        displayLanguage,
                        abstractAllowed,
                        effectiveSystemVersion,
                        activeOnly,
                        lenientDisplayValidation,
                        isMembershipOnlyMode
                    );
                    
                    System.out.println("=== DEBUG ValidationResult ===");
                    System.out.println("errorType = " + validationResult.errorType());
                    System.out.println("isValid = " + validationResult.isValid());
                    System.out.println("=== END DEBUG ===");
                    
                    // 處理 SYSTEM_NOT_FOUND 錯誤
                    if (validationResult.errorType() == ValidationErrorType.SYSTEM_NOT_FOUND) {
                    	System.out.println(">>> Handling SYSTEM_NOT_FOUND");
                    	ValidationContext systemNotFoundContext = new ValidationContext(
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
                        
                        Parameters errorResult = buildValidationErrorWithOutcome(
                            false, 
                            systemNotFoundContext,
                            null,
                            "CodeSystem not found",
                            isMembershipOnlyMode);
                        
                        removeNarratives(errorResult);
                        return errorResult;
                    }
                    
                    // 處理 SYSTEM_VERSION_NOT_FOUND 錯誤
                    if (validationResult.errorType() == ValidationErrorType.SYSTEM_VERSION_NOT_FOUND) {                    	
                    	System.out.println(">>> Handling SYSTEM_VERSION_NOT_FOUND");
                        System.out.println(">>> Calling buildCodeSystemVersionNotFoundError");
                        Parameters errorResult = buildCodeSystemVersionNotFoundError(
                            params, 
                            targetValueSet, 
                            effectiveSystemVersion,
                            validationResult.versionException()
                        );
                        removeNarratives(errorResult);
                        return errorResult;
                    }
                    
                    // 處理 INVALID_LANGUAGE_CODE 錯誤
                    if (validationResult.errorType() == ValidationErrorType.INVALID_LANGUAGE_CODE) {
                        Parameters errorResult = buildInvalidLanguageCodeError(
                            params,
                            displayLanguage
                        );
                        removeNarratives(errorResult);
                        return errorResult;
                    }
                    
                    // 處理 NO_SYSTEM 錯誤
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
                    
                    // 處理 SUPPLEMENT_NOT_FOUND 錯誤
                    if (validationResult.errorType() == ValidationErrorType.SUPPLEMENT_NOT_FOUND) {
                        Parameters errorResult = buildSupplementNotFoundError(
                            params, 
                            targetValueSet, 
                            effectiveSystemVersion,
                            validationResult.missingValueSets()
                        );
                        removeNarratives(errorResult);
                        return errorResult;
                    }
                    
                    // 處理 REFERENCED_VALUESET_NOT_FOUND 錯誤
                    if (validationResult.errorType() == ValidationErrorType.REFERENCED_VALUESET_NOT_FOUND) {
                        Parameters errorResult = buildReferencedValueSetNotFoundError(
                            params, 
                            targetValueSet, 
                            validationResult.missingValueSets()
                        );
                        removeNarratives(errorResult);
                        return errorResult;
                    }
                    
                    // 處理 INVALID_DISPLAY_WARNING（寬鬆模式）
                    if (validationResult.errorType() == ValidationErrorType.INVALID_DISPLAY_WARNING) {
                    	// membership-only 模式下忽略 display 警告
                        if (isMembershipOnlyMode) {
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
	                    	anyValid = true;
	                        matchedConcept = validationResult.concept();
	                        matchedCodeSystem = validationResult.codeSystem();
	                        matchedDisplay = validationResult.display();
	                        successfulParams = params;
	                        
	                        if (effectiveSystemVersion != null && 
	                            (resolvedSystemVersion == null || resolvedSystemVersion.isEmpty())) {
	                            resolvedSystemVersion = effectiveSystemVersion;
	                        }
	                        
	                        hasDisplayWarning = true;
	                        incorrectDisplay = params.display() != null ? params.display().getValue() : null;
	                        
	                        break;
                        }
                    }
                    
                    // 處理display 錯誤且語言不支援
                    if (validationResult.errorType() == ValidationErrorType.INVALID_DISPLAY_WITH_LANGUAGE_ERROR) {
                        Parameters errorResult = buildInvalidDisplayWithLanguageError(
                            params,
                            validationResult.display(),
                            validationResult.codeSystem(),
                            targetValueSet,
                            effectiveSystemVersion,
                            displayLanguage
                        );
                        removeNarratives(errorResult);
                        return errorResult;
                    }
                    
                    // INVALID_DISPLAY（嚴格模式）- 不要立即返回
                    if (validationResult.errorType() == ValidationErrorType.INVALID_DISPLAY) {
                    	// membership-only 模式下將 INVALID_DISPLAY 視為成功
                        if (isMembershipOnlyMode) {
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
	                        foundValidCode = true;
	                        matchedConcept = validationResult.concept();
	                        matchedCodeSystem = validationResult.codeSystem();
	                        matchedDisplay = validationResult.display();
	                        successfulParams = params;
	                        
	                        if (effectiveSystemVersion != null && 
	                            (resolvedSystemVersion == null || resolvedSystemVersion.isEmpty())) {
	                            resolvedSystemVersion = effectiveSystemVersion;
	                        }
	                        
	                        ValidationContext displayErrorContext = new ValidationContext(
	                            params.parameterSource(),
	                            params.code(),
	                            params.system(),
	                            params.display(),
	                            ValidationErrorType.INVALID_DISPLAY,
	                            targetValueSet,
	                            effectiveSystemVersion,
	                            validationResult.isInactive(),
	                            params.originalCodeableConcept()
	                        );
	                        
	                        allValidationContexts.add(displayErrorContext);
	                        failedValidationContext = displayErrorContext;
	                        
	                        continue;
                        }
                    }
                    
                    // 處理 VERSION_MISMATCH 錯誤（版本不存在但需報告不匹配）
                    if (validationResult.errorType() == ValidationErrorType.VERSION_MISMATCH) {
                    	if (isMembershipOnlyMode) {
                            // 記錄此錯誤到 allValidationContexts
                            ValidationContext versionMismatchContext = new ValidationContext(
                                params.parameterSource(),
                                params.code(),
                                params.system(),
                                params.display(),
                                ValidationErrorType.CODE_NOT_IN_VALUESET,
                                targetValueSet,
                                effectiveSystemVersion,
                                null,
                                params.originalCodeableConcept()
                            );
                            
                            allValidationContexts.add(versionMismatchContext);
                            continue;
                        }
                    	
                    	// 非 membership-only 模式：保持原有邏輯
                        Parameters errorResult = buildVersionMismatchError(
                            params, 
                            targetValueSet, 
                            effectiveSystemVersion,
                            validationResult.versionException()
                        );
                        removeNarratives(errorResult);
                        return errorResult;
                    }

                    // 處理 VERSION_MISMATCH_WITH_VALUESET 錯誤
                    if (validationResult.errorType() == ValidationErrorType.VERSION_MISMATCH_WITH_VALUESET) {
                    	// membership-only 模式下，將其視為普通的驗證失敗
                        if (isMembershipOnlyMode) {
                            ValidationContext versionMismatchContext = new ValidationContext(
                                params.parameterSource(),
                                params.code(),
                                params.system(),
                                params.display(),
                                ValidationErrorType.CODE_NOT_IN_VALUESET,
                                targetValueSet,
                                effectiveSystemVersion,
                                null,
                                params.originalCodeableConcept()
                            );
                            
                            allValidationContexts.add(versionMismatchContext);
                            continue;
                        }
                        
                        // 非 membership-only 模式：保持原有邏輯
                        Parameters errorResult = buildVersionMismatchWithValueSetError(
                            params, 
                            targetValueSet, 
                            effectiveSystemVersion
                        );
                        removeNarratives(errorResult);
                        return errorResult;
                    }

                    if (validationResult.isValid()) {
                    	if (!anyValid) {
                            anyValid = true;
                            matchedConcept = validationResult.concept();
                            matchedCodeSystem = validationResult.codeSystem();
                            matchedDisplay = validationResult.display();
                            successfulParams = params;
                            
                            if (effectiveSystemVersion != null && 
                                (resolvedSystemVersion == null || resolvedSystemVersion.isEmpty())) {
                                resolvedSystemVersion = effectiveSystemVersion;
                            }
                        }
                    	
                    	// 檢查是否有語言警告
                    	 if (validationResult.errorType() == ValidationErrorType.NO_DISPLAY_FOR_LANGUAGE_NONE ||
                             validationResult.errorType() == ValidationErrorType.NO_DISPLAY_FOR_LANGUAGE_SOME) {
                                 
                                 // 建立帶有語言警告的成功回應
                                 Parameters result = buildSuccessResponseWithLanguageWarning(
                                     successfulParams,
                                     matchedDisplay,
                                     matchedCodeSystem,
                                     targetValueSet,
                                     effectiveSystemVersion,
                                     displayLanguage,
                                     validationResult.errorType()
                                 );
                                 
                                 removeNarratives(result);
                                 return result;
                             }
                    	
                        if (validationResult.isInactive() != null && 
                            validationResult.isInactive() && 
                            activeOnly != null && activeOnly.getValue()) {
                            
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
                        
                        anyValid = true;
                        matchedConcept = validationResult.concept();
                        matchedCodeSystem = validationResult.codeSystem();
                        matchedDisplay = validationResult.display();
                        successfulParams = params;
                        
                        if (effectiveSystemVersion != null && 
                            (resolvedSystemVersion == null || resolvedSystemVersion.isEmpty())) {
                            resolvedSystemVersion = effectiveSystemVersion;
                        }
                        
                  /*  } else if (validationResult.errorType() == ValidationErrorType.INVALID_LANGUAGE_CODE) {
                        Parameters errorResult = buildInvalidLanguageCodeError(
                            params,
                            displayLanguage
                        );
                        removeNarratives(errorResult);
                        return errorResult;*/
                        
                    } else if (validationResult.errorType() == ValidationErrorType.INVALID_DISPLAY) {
                    	
                    	// membership-only 模式下忽略此錯誤
                        if (isMembershipOnlyMode) {
                            continue;
                        }

                        if (!foundValidCode) {
                            foundValidCode = true;
                            matchedConcept = validationResult.concept();
                            matchedCodeSystem = validationResult.codeSystem();
                            matchedDisplay = validationResult.display();
                            successfulParams = params;
                            
                            if (effectiveSystemVersion != null && 
                                (resolvedSystemVersion == null || resolvedSystemVersion.isEmpty())) {
                                resolvedSystemVersion = effectiveSystemVersion;
                            }
                        }
                        
                        // 記錄此錯誤
                        allValidationContexts.add(new ValidationContext(
                            params.parameterSource(),
                            params.code(),
                            params.system(),
                            params.display(),
                            ValidationErrorType.INVALID_DISPLAY,
                            targetValueSet,
                            effectiveSystemVersion,
                            validationResult.isInactive(),
                            params.originalCodeableConcept()
                        ));
                        
                    } else if (validationResult.errorType() == ValidationErrorType.CODE_NOT_IN_VALUESET) {
                        // CODE_NOT_IN_VALUESET 的特殊處理
                        // 記錄 concept 和 codeSystem 資訊
                        if (validationResult.concept() != null && matchedConcept == null) {
                            matchedConcept = validationResult.concept();
                        }
                        if (validationResult.codeSystem() != null && matchedCodeSystem == null) {
                            matchedCodeSystem = validationResult.codeSystem();
                        }
                        if (validationResult.display() != null && matchedDisplay == null) {
                            matchedDisplay = validationResult.display();
                        }
                        if (successfulParams == null) {
                            successfulParams = params;
                        }
                        
                        if (effectiveSystemVersion != null && 
                            (resolvedSystemVersion == null || resolvedSystemVersion.isEmpty())) {
                            resolvedSystemVersion = effectiveSystemVersion;
                        }
                        
                     // 建立 ValidationContext 並設置為 failedValidationContext
                        ValidationContext codeNotInVsContext = new ValidationContext(
                            params.parameterSource(),
                            params.code(),
                            params.system(),
                            params.display(),
                            ValidationErrorType.CODE_NOT_IN_VALUESET,
                            targetValueSet,
                            effectiveSystemVersion,
                            validationResult.isInactive(),
                            params.originalCodeableConcept()
                        );
                        
                        // 記錄此錯誤
                        allValidationContexts.add(codeNotInVsContext);
                        
                        // 設置 failedValidationContext
                        failedValidationContext = codeNotInVsContext;
                        
                    } else {
                    	// 其他錯誤類型
                        allValidationContexts.add(new ValidationContext(
                            params.parameterSource(),
                            params.code(),
                            params.system(),
                            params.display(),
                            validationResult.errorType(),
                            targetValueSet,
                            effectiveSystemVersion,
                            validationResult.isInactive(),
                            params.originalCodeableConcept()
                        ));
                        
                        if (validationResult.codeSystem() != null && matchedCodeSystem == null) {
                            matchedCodeSystem = validationResult.codeSystem();
                        }
                    }
                    
                } catch (CodeSystemVersionNotFoundException e) {
                	System.out.println(">>> Caught CodeSystemVersionNotFoundException in catch block");
                    StringType effectiveSystemVersion = determineEffectiveSystemVersion(
                            params, resolvedSystemVersion);
                    failedValidationContext = new ValidationContext(
                        params.parameterSource(),
                        params.code(),
                        params.system(),
                        params.display(),
                        ValidationErrorType.SYSTEM_VERSION_NOT_FOUND,
                        targetValueSet,
                        effectiveSystemVersion,
                        null,
                        params.originalCodeableConcept()
                    );
                    matchedCodeSystem = null;
                } catch (ResourceNotFoundException e) {
                	System.out.println(">>> Caught ResourceNotFoundException in catch block");
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
                    matchedCodeSystem = null;
                }
            }
            
            if (!anyValid && !foundValidCode) {

                ValidationContext completeContext;
                if (failedValidationContext != null) {
                	
                	CodeType contextCode = failedValidationContext.code();
                    if (contextCode == null && successfulParams != null) {
                        contextCode = successfulParams.code();
                    }
                    if (contextCode == null) {
                        contextCode = code;
                    }
                	
                    completeContext = new ValidationContext(
                        failedValidationContext.parameterSource(),
                        // failedValidationContext.code(),
                        contextCode,
                        failedValidationContext.system() != null ? 
                        		failedValidationContext.system() : successfulParams.system(),
                        failedValidationContext.display(),
                        failedValidationContext.errorType(),
                        targetValueSet,
                        failedValidationContext.systemVersion(),
                        failedValidationContext.isInactive(),
                        failedValidationContext.originalCodeableConcept()
                    );
                } else {
                	ValidationParams firstParam = paramsList.isEmpty() ? null : paramsList.get(0);
                    completeContext = new ValidationContext(
                        firstParam != null ? firstParam.parameterSource() : "code",
                        firstParam != null ? firstParam.code() : code,
                        firstParam != null ? firstParam.system() : resolvedSystem,
                        firstParam != null ? firstParam.display() : display,
                        ValidationErrorType.INVALID_CODE,
                        targetValueSet,
                        resolvedSystemVersion,
                        null,
                        firstParam != null ? firstParam.originalCodeableConcept() : codeableConcept
                    );
                }
                
                Parameters errorResult = buildValidationErrorWithOutcome(
                    false, 
                    completeContext,
                    matchedCodeSystem, 
                    "Code validation failed",
                    isMembershipOnlyMode);
                
                removeNarratives(errorResult);
                return errorResult;
            }

            // 檢查是否為「部分成功」情況（CodeableConcept 特有）
            boolean isCodeableConcept = codeableConcept != null || 
                (successfulParams != null && 
                 (successfulParams.originalCodeableConcept() != null ||
                  "codeableConcept".equals(successfulParams.parameterSource()) ||
                  successfulParams.parameterSource().startsWith("codeableConcept.coding")));

            if (!isMembershipOnlyMode && isCodeableConcept && !allValidationContexts.isEmpty()) {
                boolean allDisplayErrors = allValidationContexts.stream()
                    .allMatch(ctx -> ctx.errorType() == ValidationErrorType.INVALID_DISPLAY);
               
                if (allDisplayErrors && allValidationContexts.size() == 1) {
	                StringType finalSystemVersion = determineEffectiveSystemVersion(
	                    successfulParams, resolvedSystemVersion);
	                
	                Parameters errorResult = buildInvalidDisplayError(
	                        successfulParams, 
	                        matchedDisplay, 
	                        matchedCodeSystem, 
	                        targetValueSet, 
	                        finalSystemVersion,
	                        displayLanguage
	                    );
	                    
	                    removeNarratives(errorResult);
	                    return errorResult;
                }
                
                // CodeableConcept 部分成功：有有效的 code 但有其他錯誤（或多個錯誤）
                StringType finalSystemVersion = determineEffectiveSystemVersion(
                    successfulParams, resolvedSystemVersion);
                
                Parameters partialSuccessResult = buildCodeableConceptPartialSuccessError(
                    successfulParams,
                    matchedDisplay,
                    matchedCodeSystem,
                    targetValueSet,
                    finalSystemVersion,
                    allValidationContexts
                );
                
                removeNarratives(partialSuccessResult);
                return partialSuccessResult;
            }
                

            // 處理單純的 INVALID_DISPLAY 錯誤（非 CodeableConcept 或沒有其他錯誤）
            if (!isMembershipOnlyMode && !anyValid && foundValidCode && 
                failedValidationContext != null && 
                failedValidationContext.errorType() == ValidationErrorType.INVALID_DISPLAY) {
                
                StringType finalSystemVersion = determineEffectiveSystemVersion(
                    successfulParams, resolvedSystemVersion);
                
                Parameters errorResult = buildInvalidDisplayError(
                    successfulParams, 
                    matchedDisplay, 
                    matchedCodeSystem, 
                    targetValueSet, 
                    finalSystemVersion,
                    displayLanguage
                );
                
                removeNarratives(errorResult);
                return errorResult;
            }
            
            // 完全成功的情況
            StringType finalSystemVersion = determineEffectiveSystemVersion(
                successfulParams, resolvedSystemVersion);

            // 檢查是否有 display warning
            Parameters successResult;
            if (isMembershipOnlyMode) {
                successResult = buildMembershipOnlySuccessResponse(
                    successfulParams,
                    targetValueSet
                );
            } else if (hasDisplayWarning) {
                // 使用帶有 warning 的回應建立方法
                successResult = buildSuccessResponseWithDisplayWarning(
                    successfulParams, 
                    matchedDisplay,
                    matchedCodeSystem, 
                    targetValueSet, 
                    finalSystemVersion,
                    incorrectDisplay
                );
            } else {
                // 正常的成功回應
                successResult = buildSuccessResponse(
                    successfulParams, 
                    matchedDisplay, 
                    matchedCodeSystem, 
                    targetValueSet, 
                    finalSystemVersion, 
                    null
                );
            }

            removeNarratives(successResult);
            return successResult;

        } catch (ResourceNotFoundException e) {
            if (e.getMessage() != null && e.getMessage().contains("ValueSet")) {
                String valueSetUrlForError = extractValueSetUrlFromException(e, url, urlCanonical, valueSetUrl, valueSetCanonical, urlType, valueSetUrlType);
                OperationOutcome outcome = buildValueSetNotFoundOutcome(valueSetUrlForError, e.getMessage());

                if (outcome.hasIssue()) {
                    for (OperationOutcome.OperationOutcomeIssueComponent issue : outcome.getIssue()) {
                        issue.getExtension().removeIf(ext -> 
                            ext.getUrl() != null && ext.getUrl().contains("narrative"));
                    }
                }
                
                throw new ResourceNotFoundException("ValueSet not found", outcome);
            }
            
            if (e.getMessage() != null && e.getMessage().contains("CodeSystem")) {
                StringType resolvedSystemVersion = resolveSystemVersionParameter(systemVersion, systemVersionCode);
                UriType resolvedSystem = resolveUriParameter(system, systemCanonical, systemUrlType);
                
                ValidationContext errorContext = new ValidationContext(
                    codeableConcept != null ? "codeableConcept" : "code",
                    code,
                    resolvedSystem,
                    display,
                    ValidationErrorType.SYSTEM_NOT_FOUND,
                    null,
                    resolvedSystemVersion,
                    null,
                    codeableConcept 
                );
                
                ValueSet targetValueSet = null;
                try {
                    if (resourceId != null) {
                        targetValueSet = getValueSetById(resourceId.getIdPart(), version);
                    } else {
                        UriType resolvedUrl = resolveUriParameter(url, urlCanonical, urlType);
                        UriType resolvedValueSetUrl = resolveUriParameter(valueSetUrl, valueSetCanonical, valueSetUrlType);
                        
                        if (resolvedUrl != null) {
                            targetValueSet = findValueSetByUrl(resolvedUrl.getValue(), 
                                version != null ? version.getValue() : null);
                        } else if (resolvedValueSetUrl != null) {
                            targetValueSet = findValueSetByUrl(resolvedValueSetUrl.getValue(), 
                                version != null ? version.getValue() : null);
                        }
                    }
                } catch (Exception ex) {
                }
                
                errorContext = new ValidationContext(
                    errorContext.parameterSource(),
                    errorContext.code(),
                    errorContext.system(),
                    errorContext.display(),
                    errorContext.errorType(),
                    targetValueSet,
                    errorContext.systemVersion(),
                    errorContext.isInactive(),
                    errorContext.originalCodeableConcept()
                );
                
                boolean isMembershipOnlyMode = valuesetMembershipOnly != null && valuesetMembershipOnly.getValue();
                Parameters exceptionResult = buildValidationErrorWithOutcome(
                        false, errorContext, null, e.getMessage(), isMembershipOnlyMode);
                
                removeNarratives(exceptionResult);
                return exceptionResult;
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
            boolean isMembershipOnlyMode = valuesetMembershipOnly != null && valuesetMembershipOnly.getValue();
            Parameters exceptionResult = buildValidationErrorWithOutcome(
                false, errorContext, null, e.getMessage(), isMembershipOnlyMode);
            
            removeNarratives(exceptionResult);
            return exceptionResult;
        } catch (InvalidRequestException e) {
        	if (e.getMessage() != null && 
        	   (e.getMessage().contains("'url'") || 
        	    e.getMessage().contains("'valueSet'") || 
        	    e.getMessage().contains("resource ID"))) {
        		OperationOutcome outcome = buildInvalidRequestOutcome(e.getMessage());

                outcome.setText(null);
                outcome.setMeta(null);
                
                throw new InvalidRequestException("Invalid request parameters", outcome);
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
            
            boolean isMembershipOnlyMode = valuesetMembershipOnly != null && valuesetMembershipOnly.getValue();
            Parameters exceptionResult = buildValidationErrorWithOutcome(
                false, errorContext, null, e.getMessage(), isMembershipOnlyMode);
            
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
            
            boolean isMembershipOnlyMode = valuesetMembershipOnly != null && valuesetMembershipOnly.getValue();
            Parameters internalErrorResult = buildValidationErrorWithOutcome(
                false, errorContext, null, "Internal error: " + e.getMessage(), isMembershipOnlyMode);
            
            removeNarratives(internalErrorResult);
            return internalErrorResult;
        }
    }
    
    private StringType determineEffectiveSystemVersion(ValidationParams params, StringType resolvedSystemVersion) {
        // 1. 優先使用 coding 中的 version
        if (params.originalCoding() != null && params.originalCoding().hasVersion()) {
        	String version = params.originalCoding().getVersion();
            return new StringType(params.originalCoding().getVersion());
        }
        
        // 2. 如果 codeableConcept 中的 coding 有 version，也使用它
        if (params.originalCodeableConcept() != null) {
            for (Coding c : params.originalCodeableConcept().getCoding()) {
                if (c.hasVersion()) {
                	String version = c.getVersion();
                    
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
                    
                    // 也檢查 designation
                    for (ConceptDefinitionDesignationComponent designation : concept.getDesignation()) {
                        if (display.equalsIgnoreCase(designation.getValue())) {
                            return codeSystem.hasVersion() ? new StringType(codeSystem.getVersion()) : null;
                        }
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
        
        // 設定排序以獲得一致的順序
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
    private UriType resolveUriParameter(UriType uriParam, CanonicalType canonicalParam, UrlType urlParam) {
        if (uriParam != null && !uriParam.isEmpty()) {
            return uriParam;
        } else if (urlParam != null && !urlParam.isEmpty()) {
            return new UriType(urlParam.getValue());
        } else if (canonicalParam != null && !canonicalParam.isEmpty()) {
            String canonicalValue = canonicalParam.getValue();
            String urlPart = canonicalValue.contains("|") ? 
                canonicalValue.substring(0, canonicalValue.indexOf("|")) : 
                canonicalValue;
            return new UriType(urlPart);
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
    
    private ValidationResult validateCodeInValueSet(ValueSet valueSet, CodeType code, UriType system,
            										StringType display, CodeType displayLanguage, 
            										BooleanType abstractAllowed, StringType systemVersion,
            										BooleanType activeOnly, BooleanType lenientDisplayValidation,
                                                    boolean membershipOnly) {
    	
    	System.out.println("=== DEBUG validateCodeInValueSet ===");
        System.out.println("system = " + (system != null ? system.getValue() : "null"));
        System.out.println("code = " + (code != null ? code.getValue() : "null"));
        System.out.println("systemVersion = " + (systemVersion != null ? systemVersion.getValue() : "null"));
        System.out.println("=== END DEBUG ===");
    	
    	if (valueSet == null || code == null) {
            return new ValidationResult(false, null, null, null, ValidationErrorType.INVALID_CODE, null, null, null); 
        }
    	
    	// 檢查 system 是否為空
        if (system == null || system.isEmpty()) {
            return new ValidationResult(false, null, null, null, 
                                      ValidationErrorType.NO_SYSTEM, null, null, null);
        }
        
        // 檢查是否為相對引用
        if (isRelativeReference(system.getValue())) {
            return new ValidationResult(false, null, null, null, 
                                      ValidationErrorType.RELATIVE_SYSTEM_REFERENCE, null, null, null);
        }
        
        // 檢查 system 是否實際上是 ValueSet URL
        if (isValueSetUrl(system.getValue())) {
            return new ValidationResult(false, null, null, null, 
                                      ValidationErrorType.SYSTEM_IS_VALUESET, null, null, null);
        }
        
        if (systemVersion != null && !systemVersion.isEmpty() && valueSet.hasCompose()) {
        	System.out.println(">>> Checking version match logic");
        	for (ConceptSetComponent include : valueSet.getCompose().getInclude()) {
                if (include.hasSystem() && system.getValue().equals(include.getSystem())) {
                	String valueSetVersion = include.hasVersion() ? include.getVersion() : null;
                    String requestedVersion = systemVersion.getValue();
                    
                    System.out.println(">>> valueSetVersion = " + valueSetVersion);
                    System.out.println(">>> requestedVersion = " + requestedVersion);
                	
                    if (valueSetVersion != null && !valueSetVersion.equals(requestedVersion)) {
                        try {
                        	System.out.println(">>> Trying to find CodeSystem with requested version");
                            findCodeSystemByUrl(system.getValue(), requestedVersion);
                            System.out.println(">>> Found! Returning VERSION_MISMATCH_WITH_VALUESET");
                            return new ValidationResult(false, null, null, null, 
                                    ValidationErrorType.VERSION_MISMATCH_WITH_VALUESET, 
                                    null, null, null);
                        } catch (ResourceNotFoundException e) {

                        	System.out.println(">>> Not found! Building version exception");
                        	
                        	CodeSystemVersionNotFoundException versionException = 
                                    new CodeSystemVersionNotFoundException(
                                        String.format("CodeSystem with URL '%s' and version '%s' not found", 
                                                    system.getValue(), requestedVersion));
                                versionException.setUrl(system.getValue());
                                versionException.setRequestedVersion(requestedVersion);
                            
                                List<String> availableVersions = new ArrayList<>();
                                try {
                                    List<CodeSystem> allVersions = findAllCodeSystemVersions(system.getValue());
                                    for (CodeSystem cs : allVersions) {
                                        if (cs.hasVersion()) {
                                            availableVersions.add(cs.getVersion());
                                        }
                                    }
                            } catch (Exception ex) {

                            }
                            versionException.setAvailableVersions(availableVersions);

                            return new ValidationResult(false, null, null, null, 
                                    ValidationErrorType.VERSION_MISMATCH, 
                                    null, versionException, null);
                        }
                    }
                    
                    if (valueSetVersion == null && requestedVersion != null) {
                        try {
                            CodeSystem requestedCS = findCodeSystemByUrl(system.getValue(), requestedVersion);
                            CodeSystem defaultCS = findCodeSystemByUrl(system.getValue(), null);
                            
                            if (defaultCS.hasVersion() && !defaultCS.getVersion().equals(requestedVersion)) {
                                return new ValidationResult(false, null, null, null, 
                                    ValidationErrorType.VERSION_MISMATCH_WITH_VALUESET, 
                                    null, null, null);
                            }
                            
                        } catch (ResourceNotFoundException e) {
                            // 請求的版本不存在
                            CodeSystemVersionNotFoundException versionException = 
                                new CodeSystemVersionNotFoundException(
                                    String.format("CodeSystem with URL '%s' and version '%s' not found", 
                                                system.getValue(), requestedVersion));
                            versionException.setUrl(system.getValue());
                            versionException.setRequestedVersion(requestedVersion);
                            
                            List<String> availableVersions = new ArrayList<>();
                            try {
                                List<CodeSystem> allVersions = findAllCodeSystemVersions(system.getValue());
                                for (CodeSystem cs : allVersions) {
                                    if (cs.hasVersion()) {
                                        availableVersions.add(cs.getVersion());
                                    }
                                }
                            } catch (Exception ex) {
                            	
                            }
                            versionException.setAvailableVersions(availableVersions);
                            
                            return new ValidationResult(false, null, null, null, 
                                ValidationErrorType.VERSION_MISMATCH, 
                                null, versionException, null);
                        }
                    }
                    break;
                }
            }
        }
        
     // ========== 新增：在進入 compose 之前，檢查 CodeSystem 是否存在（當有明確版本時）==========
        if (systemVersion != null && !systemVersion.isEmpty()) {
            System.out.println(">>> Pre-compose check: Has explicit version, checking if CodeSystem exists");
            try {
                // 嘗試找指定版本的 CodeSystem
                findCodeSystemByUrl(system.getValue(), systemVersion.getValue());
                System.out.println(">>> Found CodeSystem with requested version");
            } catch (ResourceNotFoundException e) {
                System.out.println(">>> CodeSystem with requested version not found");
                // 檢查 CodeSystem 是否完全不存在
                CodeSystemVersionNotFoundException versionException = 
                    new CodeSystemVersionNotFoundException(
                        String.format("CodeSystem with URL '%s' and version '%s' not found", 
                                    system.getValue(), systemVersion.getValue()));
                versionException.setUrl(system.getValue());
                versionException.setRequestedVersion(systemVersion.getValue());
                
                List<String> availableVersions = new ArrayList<>();
                try {
                    System.out.println(">>> Checking if CodeSystem exists at all");
                    List<CodeSystem> allVersions = findAllCodeSystemVersions(system.getValue());
                    System.out.println(">>> Found " + allVersions.size() + " versions");
                    for (CodeSystem cs : allVersions) {
                        if (cs.hasVersion()) {
                            availableVersions.add(cs.getVersion());
                        }
                    }
                } catch (Exception ex) {
                    System.out.println(">>> CodeSystem doesn't exist at all (no versions found)");
                    // availableVersions 保持為空 = CodeSystem 完全不存在
                }
                
                versionException.setAvailableVersions(availableVersions);
                System.out.println(">>> Returning SYSTEM_VERSION_NOT_FOUND with " + availableVersions.size() + " available versions");
                
                return new ValidationResult(false, null, null, null, 
                    ValidationErrorType.SYSTEM_VERSION_NOT_FOUND, 
                    null, versionException, null);
            }
        }

        // 優先檢查 expansion
    	if (valueSet.hasExpansion() && isExpansionCurrent(valueSet.getExpansion())) {
            ValidationResult expansionResult = validateCodeInExpansion(
                valueSet.getExpansion(), code, system, display, displayLanguage, abstractAllowed, 
                systemVersion, activeOnly, lenientDisplayValidation, membershipOnly);
            if (expansionResult.isValid()) {
                return expansionResult;
            }
            // 如果 expansion 返回 INVALID_DISPLAY_WITH_LANGUAGE_ERROR，直接返回
            if (expansionResult.errorType() == ValidationErrorType.INVALID_DISPLAY_WITH_LANGUAGE_ERROR) {
                return expansionResult;
            }
        }
    	
    	// 【新增】檢查 ValueSet 是否引用了不存在的 supplement CodeSystem
        if (valueSet.hasCompose()) {
            List<String> missingSupplements = checkForMissingSupplements(valueSet.getCompose(), system);
            if (!missingSupplements.isEmpty()) {
                return new ValidationResult(false, null, null, null, 
                    ValidationErrorType.SUPPLEMENT_NOT_FOUND, null, null, missingSupplements);
            }
        }

        // 處理 compose 規則
    	if (valueSet.hasCompose()) {
            ValidationResult composeResult = validateCodeInCompose(valueSet.getCompose(), code, system, display, 
                                       displayLanguage, abstractAllowed, systemVersion, activeOnly, lenientDisplayValidation, membershipOnly);
            return composeResult;
        }

    	return new ValidationResult(false, null, null, null, ValidationErrorType.CODE_NOT_IN_VALUESET, null, null, null);
    }
    
    // 新增檢查 supplement 的方法
    private List<String> checkForMissingSupplements(ValueSetComposeComponent compose, UriType system) {
        List<String> missingSupplements = new ArrayList<>();
        
        for (ConceptSetComponent include : compose.getInclude()) {
            // 檢查是否有 supplement 引用
            if (include.hasSystem() && system != null && 
                include.getSystem().equals(system.getValue())) {
                
                // 檢查 CodeSystem 的 supplements
                try {
                    CodeSystem codeSystem = findCodeSystemByUrl(include.getSystem(), 
                        include.hasVersion() ? include.getVersion() : null);
                    
                    if (codeSystem != null && codeSystem.hasSupplements()) {
                        String supplementUrl = codeSystem.getSupplements();
                        
                        // 檢查 supplement CodeSystem 是否存在
                        try {
                            findCodeSystemByUrl(supplementUrl, null);
                        } catch (ResourceNotFoundException e) {
                            missingSupplements.add(supplementUrl);
                        }
                    }
                } catch (ResourceNotFoundException e) {
                    // CodeSystem 本身不存在，這會在其他地方處理
                }
            }
        }
        
        return missingSupplements;
    }

    private ValidationResult validateCodeInConceptSet(ConceptSetComponent conceptSet, CodeType code, 
            											UriType system, StringType display, 
            											CodeType displayLanguage, BooleanType abstractAllowed,
            											boolean isInclude, StringType resolvedSystemVersion,
            											BooleanType activeOnly, BooleanType lenientDisplayValidation,
                                                        boolean membershipOnly) {
        
    	
        // 先檢查請求的 system 是否存在，再檢查是否匹配
        if (system != null && !system.isEmpty()) {
            try {
                findCodeSystemByUrl(system.getValue(), null);
            } catch (ResourceNotFoundException e) {
                return new ValidationResult(false, null, null, null, 
                    ValidationErrorType.SYSTEM_NOT_FOUND, null, null, null);
            }
            
            // CodeSystem 存在，但與 conceptSet 的 system 不匹配
            if (conceptSet.hasSystem() && !system.getValue().equals(conceptSet.getSystem())) {
                return new ValidationResult(false, null, null, null, 
                    ValidationErrorType.INVALID_CODE, null, null, null);
            }
        }

        // 取得 CodeSystem - 改進版本處理邏輯（包含推斷）
        CodeSystem codeSystem = null;
        String systemUrl = conceptSet.hasSystem() ? conceptSet.getSystem() : 
                          (system != null ? system.getValue() : null);
             
        // 標記是否因為系統找不到而失敗
        boolean systemNotFound = false;
        
        if (systemUrl != null) {
            try {
                String versionToUse = determineSystemVersion(conceptSet, resolvedSystemVersion, 
                                                           code, display, system);
                
                // 如果 coding 中有明確指定版本，且與 conceptSet 的版本不同，需要檢查
                if (resolvedSystemVersion != null && !resolvedSystemVersion.isEmpty() && 
                        conceptSet.hasVersion() && !conceptSet.getVersion().isEmpty()) {
                        
                        // 如果兩個版本不同
                        if (!resolvedSystemVersion.getValue().equals(conceptSet.getVersion())) {
                            
                            // 檢查請求的版本是否存在
                            try {
                                findCodeSystemByUrl(systemUrl, resolvedSystemVersion.getValue());
                                
                                return new ValidationResult(false, null, null, null, 
                                        ValidationErrorType.VERSION_MISMATCH_WITH_VALUESET, 
                                        null, null, null);
                                
                            } catch (ResourceNotFoundException e) {
                                // 請求的版本不存在
                                CodeSystemVersionNotFoundException versionException = 
                                    new CodeSystemVersionNotFoundException(
                                        String.format("CodeSystem with URL '%s' and version '%s' not found", 
                                                    systemUrl, resolvedSystemVersion.getValue()));
                                versionException.setUrl(systemUrl);
                                versionException.setRequestedVersion(resolvedSystemVersion.getValue());
                                
                                // 獲取可用版本
                                List<String> availableVersions = new ArrayList<>();
                                try {
                                    List<CodeSystem> allVersions = findAllCodeSystemVersions(systemUrl);
                                    for (CodeSystem cs : allVersions) {
                                        if (cs.hasVersion()) {
                                            availableVersions.add(cs.getVersion());
                                        }
                                    }
                                } catch (Exception ex) {

                                }
                                versionException.setAvailableVersions(availableVersions);
                                
                                // 返回版本不匹配錯誤（版本不存在的情況）
                                return new ValidationResult(false, null, null, null, 
                                    ValidationErrorType.VERSION_MISMATCH, 
                                    null, versionException, null);
                            }
                        }
                    }
                
                if (versionToUse != null) {
                    codeSystem = findCodeSystemWithVersionFallback(systemUrl, versionToUse);
                } else {
                    if (display != null && !display.isEmpty() && code != null) {
                        StringType inferredVersion = inferVersionFromCodeAndDisplay(
                            systemUrl, code.getValue(), display.getValue());
                        
                        if (inferredVersion != null) {
                            codeSystem = findCodeSystemByUrl(systemUrl, inferredVersion.getValue());
                        } else {
                            codeSystem = findCodeSystemByUrl(systemUrl, null);
                        }
                    } else {
                        codeSystem = findCodeSystemByUrl(systemUrl, null);
                    }
                }
                
            } catch (CodeSystemVersionNotFoundException e) {
                return new ValidationResult(false, null, null, null, 
                    ValidationErrorType.SYSTEM_VERSION_NOT_FOUND, null, e, null);
            } catch (ResourceNotFoundException e) {
                systemNotFound = true;
                codeSystem = null;
            }
        }
        
        // 如果系統找不到，立即返回 SYSTEM_NOT_FOUND
        if (systemNotFound) {
            return new ValidationResult(false, null, null, null, 
                ValidationErrorType.SYSTEM_NOT_FOUND, null, null, null);
        }

        if (!conceptSet.getConcept().isEmpty()) {
            ValidationResult result = validateCodeInConceptList(conceptSet.getConcept(), code, display, 
                                           displayLanguage, abstractAllowed, codeSystem, lenientDisplayValidation, 
                                           membershipOnly);
            // 如果 codeSystem 為 null，檢查是否應該返回 SYSTEM_NOT_FOUND
            if (!result.isValid() && codeSystem == null && systemUrl != null) {
                return new ValidationResult(false, null, null, null, 
                    ValidationErrorType.SYSTEM_NOT_FOUND, null, null, null);
            }
            return result;
        }

        // 在驗證 filter 時，如果 code 存在但不符合 filter，應該返回 CODE_NOT_IN_VALUESET
        if (!conceptSet.getFilter().isEmpty() && codeSystem != null) {
            ValidationResult filterResult = validateCodeWithFilters(
                conceptSet.getFilter(), codeSystem, code, display, 
                displayLanguage, abstractAllowed, lenientDisplayValidation, membershipOnly);
            
            // 保留 INVALID_DISPLAY 錯誤類型
            if (!filterResult.isValid()) {
                // 如果是 INVALID_DISPLAY 錯誤，直接返回
                if (!membershipOnly && filterResult.errorType() == ValidationErrorType.INVALID_DISPLAY) {
                    return filterResult;
                }
                
                // 如果驗證失敗，檢查是否因為不符合 filter（而非 code 不存在）
                if (filterResult.concept() != null) {
                    // code 存在但不符合 filter，返回 CODE_NOT_IN_VALUESET
                    return new ValidationResult(false, filterResult.concept(), codeSystem, 
                                               filterResult.display(), 
                                               ValidationErrorType.CODE_NOT_IN_VALUESET, 
                                               filterResult.isInactive(), null, null);
                }
                return filterResult;
            }
            
            return filterResult;
        }

        if (codeSystem != null) {
            ValidationResult result = validateCodeInCodeSystem(codeSystem, code, display, 
            													displayLanguage, abstractAllowed, 
            													activeOnly, lenientDisplayValidation,
                                                                membershipOnly);
            
            if (!result.isValid()) {
            	if (!membershipOnly && 
                    	result.errorType() == ValidationErrorType.INVALID_DISPLAY_WITH_LANGUAGE_ERROR) {
                        return result;
                    }
            	
                if (!membershipOnly && 
                	(result.errorType() == ValidationErrorType.INVALID_DISPLAY ||
                    result.errorType() == ValidationErrorType.INVALID_DISPLAY_WARNING)) {
                    return result;
                }
                
                if (result.concept() != null) {
                    return new ValidationResult(false, result.concept(), codeSystem, result.display(), 
                                               ValidationErrorType.CODE_NOT_IN_VALUESET, 
                                               result.isInactive(), null, null);
                }

                if (result.concept() == null) {
                    return new ValidationResult(false, null, codeSystem, null, 
                                               ValidationErrorType.INVALID_CODE, 
                                               null, null, null);
                }
            }
            
            return result;
        }
     
        // 如果有 systemUrl 但 codeSystem 為 null，返回 SYSTEM_NOT_FOUND
        if (systemUrl != null && codeSystem == null) {
            return new ValidationResult(false, null, null, null, 
                ValidationErrorType.SYSTEM_NOT_FOUND, null, null, null);
        }

        return new ValidationResult(false, null, null, null, ValidationErrorType.INVALID_CODE, null, null, null);
    }
    
    private String determineSystemVersion(ConceptSetComponent conceptSet, 
								            StringType resolvedSystemVersion, 
								            CodeType code, 
								            StringType display, 
								            UriType system) {

    	// 1. 最優先：使用 coding 中明確指定的 version（resolvedSystemVersion）
        String requestVersion = (resolvedSystemVersion != null && !resolvedSystemVersion.isEmpty()) 
                                ? resolvedSystemVersion.getValue() : null;
        
        // 2. conceptSet 中的版本
        String conceptSetVersion = conceptSet.hasVersion() ? conceptSet.getVersion() : null;
        
        // 3. 如果都沒有版本，嘗試從 code 和 display 推斷
        if (requestVersion == null && conceptSetVersion == null && 
            code != null && display != null && system != null) {
            
            StringType inferredVersion = inferVersionFromCodeAndDisplay(
                system.getValue(), code.getValue(), display.getValue());
            
            if (inferredVersion != null) {
                return inferredVersion.getValue();
            }
        }
        
        // 4. 優先返回請求中的版本（這是關鍵改動）
        if (requestVersion != null) {
            return requestVersion;
        }
        
        // 5. 否則使用 conceptSet 的版本
        return conceptSetVersion;
    }
    
    private ValidationResult validateCodeInCompose(ValueSetComposeComponent compose, CodeType code, 
													UriType system, StringType display, 
													CodeType displayLanguage, BooleanType abstractAllowed,
													StringType systemVersion, BooleanType activeOnly,
		                                            BooleanType lenientDisplayValidation,
		                                            boolean membershipOnly) {
			
			boolean foundInInclude = false;
			ValidationResult includeResult = null;
			ValidationResult lastErrorResult = null;
			CodeSystem lastCheckedCodeSystem = null;
	        ConceptDefinitionComponent lastFoundConcept = null;
			
			// 檢查 include 中是否有引用不存在的 ValueSet
			List<String> missingValueSets = new ArrayList<>();
			for (ConceptSetComponent include : compose.getInclude()) {
				if (include.hasValueSet()) {
					for (CanonicalType valueSetRef : include.getValueSet()) {
						String referencedUrl = valueSetRef.getValue();
						try {
							findValueSetByUrl(referencedUrl, null);
						} catch (ResourceNotFoundException e) {
							missingValueSets.add(referencedUrl);
						}
					}
				}
			}
			
			if (!missingValueSets.isEmpty()) {
				return new ValidationResult(false, null, null, null, 
						ValidationErrorType.REFERENCED_VALUESET_NOT_FOUND, 
						null, null, missingValueSets);
			}
			
			// 檢查 include 部分
			for (ConceptSetComponent include : compose.getInclude()) {
				ValidationResult result = validateCodeInConceptSet(include, code, system, display, 
			                 	displayLanguage, abstractAllowed, true, systemVersion, activeOnly, 
			                 	lenientDisplayValidation, membershipOnly);
			
			if (!result.isValid()) {
				lastErrorResult = result;

                if (result.codeSystem() != null) {
                    lastCheckedCodeSystem = result.codeSystem();
                }
                if (result.concept() != null) {
                    lastFoundConcept = result.concept();
                }
			
				// 如果是系統級別的錯誤，立即返回
				if (result.errorType() == ValidationErrorType.SYSTEM_NOT_FOUND ||
					result.errorType() == ValidationErrorType.SYSTEM_VERSION_NOT_FOUND ||
					result.errorType() == ValidationErrorType.NO_SYSTEM ||
					result.errorType() == ValidationErrorType.RELATIVE_SYSTEM_REFERENCE ||
					result.errorType() == ValidationErrorType.SYSTEM_IS_VALUESET) {
					return result;
				}
				
				// 如果是 INVALID_DISPLAY 錯誤，也立即返回（不要修改錯誤類型）
	            if (!membershipOnly && result.errorType() == ValidationErrorType.INVALID_DISPLAY) {
	                return result;
	            }
			}
			
			if (result.isValid()) {
				foundInInclude = true;
				includeResult = result;
				break;
				}
			}
			
			if (!foundInInclude) {
		        if (lastErrorResult.errorType() == ValidationErrorType.CODE_NOT_IN_VALUESET) {
		            return lastErrorResult;
		        }
				
				if (lastErrorResult != null && lastErrorResult.errorType() != null) {
					if (lastErrorResult.errorType() == ValidationErrorType.INVALID_CODE && 
			                lastFoundConcept != null && lastCheckedCodeSystem != null) {
			                return new ValidationResult(false, lastFoundConcept, lastCheckedCodeSystem, 
			                                           lastFoundConcept.getDisplay(), 
			                                           ValidationErrorType.CODE_NOT_IN_VALUESET, 
			                                           null, null, null);
			            }
	                return lastErrorResult;
	            }
				return new ValidationResult(false, null, null, null, ValidationErrorType.CODE_NOT_IN_VALUESET, null, null, null);
			}
			
			for (ConceptSetComponent exclude : compose.getExclude()) {
				ValidationResult result = validateCodeInConceptSet(exclude, code, system, display, 
			                 	displayLanguage, abstractAllowed, false, systemVersion, activeOnly, 
			                 	lenientDisplayValidation, membershipOnly);
				if (result.isValid()) {
					return new ValidationResult(false, result.concept(), result.codeSystem(), 
								result.display(), ValidationErrorType.CODE_NOT_IN_VALUESET, result.isInactive(), null, null);
				}
			}
			
			return includeResult;
		}

    private ValidationResult validateCodeInExpansion(ValueSetExpansionComponent expansion, 
		            									CodeType code, UriType system, StringType display,
		            									CodeType displayLanguage, BooleanType abstractAllowed,
		            									StringType systemVersion, BooleanType activeOnly,
		                                                BooleanType lenientDisplayValidation,
		                                                boolean membershipOnly) {
		
    	for (ValueSetExpansionContainsComponent contains : expansion.getContains()) {
            ValidationResult result = validateCodeInExpansionContains(contains, code, system, display, 
                                                                    displayLanguage, abstractAllowed, 
                                                                    systemVersion, activeOnly, 
                                                                    lenientDisplayValidation, membershipOnly);
            if (result.isValid()) {
                return result;
            }
        }
        
        return new ValidationResult(false, null, null, null, ValidationErrorType.CODE_NOT_IN_VALUESET, null, null, null);
    }

    private ValidationResult validateCodeInExpansionContains(ValueSetExpansionContainsComponent contains,
                                                           CodeType code, UriType system, StringType display,
                                                           CodeType displayLanguage, BooleanType abstractAllowed,
                                                           StringType resolvedSystemVersion, BooleanType activeOnly,
                                                           BooleanType lenientDisplayValidation,
                                                           boolean membershipOnly) {
        
        if (code.getValue().equals(contains.getCode()) && 
            (system == null || system.getValue().equals(contains.getSystem()))) {
            
            if (!isVersionMatch(contains, resolvedSystemVersion)) {

            } else {
                if (contains.hasAbstract() && contains.getAbstract() && 
                    (abstractAllowed == null || !abstractAllowed.getValue())) {
                    return new ValidationResult(false, null, null, null, 
                                              ValidationErrorType.ABSTRACT_CODE_NOT_ALLOWED, null, null, null);
                }
                
                if (!membershipOnly && display != null && !display.isEmpty() && contains.hasDisplay() && 
                    !display.getValue().equalsIgnoreCase(contains.getDisplay())) {
                	
                	boolean isLenient = lenientDisplayValidation != null && lenientDisplayValidation.getValue();
                    
                    if (isLenient) {
                        Boolean isInactive = contains.hasInactive() ? contains.getInactive() : false;
                        return new ValidationResult(true, null, null, contains.getDisplay(), 
                                                  ValidationErrorType.INVALID_DISPLAY_WARNING, isInactive, null, null);
                    } else {
                        return new ValidationResult(false, null, null, contains.getDisplay(), 
                                                  ValidationErrorType.INVALID_DISPLAY, null, null, null);
                    }
                }
                
                Boolean isInactive = contains.hasInactive() ? contains.getInactive() : false;
                
                return new ValidationResult(true, null, null, contains.getDisplay(), null, isInactive, null, null);
            }
        }
        
        for (ValueSetExpansionContainsComponent child : contains.getContains()) {
            ValidationResult result = validateCodeInExpansionContains(child, code, system, display, 
                                                                    displayLanguage, abstractAllowed, 
                                                                    resolvedSystemVersion, activeOnly,
                                                                    lenientDisplayValidation, membershipOnly);
            if (result.isValid()) {
                return result;
            }
        }
        
        return new ValidationResult(false, null, null, null, ValidationErrorType.INVALID_CODE, null, null, null);
    }
    
    private boolean isVersionMatch(ValueSetExpansionContainsComponent contains, StringType resolvedSystemVersion) {
        if (resolvedSystemVersion == null || resolvedSystemVersion.isEmpty()) {
            return true;
        }
        
        String requestedVersion = resolvedSystemVersion.getValue();
        
        if (!contains.hasVersion()) {
            return requestedVersion.isEmpty() || "latest".equals(requestedVersion);
        }
        
        return requestedVersion.equals(contains.getVersion());
    }
    
    private CodeSystem findCodeSystemWithVersionFallback(String url, String preferredVersion) {
        try {
            return findCodeSystemByUrl(url, preferredVersion);
        } catch (ResourceNotFoundException e) {
            if (StringUtils.isNotBlank(preferredVersion)) {
                
                try {
                    CodeSystem latestVersion = findLatestCodeSystemVersion(url);
                    return latestVersion;
                } catch (ResourceNotFoundException fallbackException) {
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
		
		// 判斷是否為 codeableConcept 參數
	    boolean isCodeableConcept = successfulParams != null && 
	                                (successfulParams.originalCodeableConcept() != null || 
	                                 "codeableConcept".equals(successfulParams.parameterSource()) ||
	                                 successfulParams.parameterSource().startsWith("codeableConcept.coding"));

		// 1. code 參數
	    if (successfulParams != null && successfulParams.code() != null) {
	        result.addParameter("code", successfulParams.code());
	    }
	    
	    // 2. codeableConcept 參數
	    if (isCodeableConcept && successfulParams.originalCodeableConcept() != null) {
	        result.addParameter("codeableConcept", successfulParams.originalCodeableConcept());
	    }

	    // 3. display 參數
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

	    // 4. inactive 參數 (如果為 true 才加入)
	    if (isInactive != null && isInactive) {
	        result.addParameter("inactive", new BooleanType(true));
	    }

	    // 5. result 參數
	    result.addParameter("result", new BooleanType(true));

	    // 6. system 參數
	    if (successfulParams != null && successfulParams.system() != null && !successfulParams.system().isEmpty()) {
	        result.addParameter("system", successfulParams.system());
	    } else if (matchedCodeSystem != null && matchedCodeSystem.hasUrl()) {
	        result.addParameter("system", new UriType(matchedCodeSystem.getUrl()));
	    }

	    // 7. version 參數
	    String versionToReturn = null;
	    if (effectiveSystemVersion != null && !effectiveSystemVersion.isEmpty()) {
	        versionToReturn = effectiveSystemVersion.getValue();
	    } else if (matchedCodeSystem != null && matchedCodeSystem.hasVersion()) {
	        versionToReturn = matchedCodeSystem.getVersion();
	    }
	    
	    if (versionToReturn != null) {
	        result.addParameter("version", new StringType(versionToReturn));
	    }

	    return result;
    }

    private Parameters buildValidationErrorWithOutcome(boolean isValid, ValidationContext context,
                                                      CodeSystem codeSystem, String errorMessage,
                                                      boolean membershipOnly) {
    	
    	Parameters result = new Parameters();
    	
        result.setMeta(null);
        
        boolean isInvalidDisplay = context.errorType() == ValidationErrorType.INVALID_DISPLAY;

        boolean isCodeableConcept = context.originalCodeableConcept() != null || 
                                    "codeableConcept".equals(context.parameterSource()) ||
                                    context.parameterSource().startsWith("codeableConcept.coding");
        
        boolean isCoding = "coding".equals(context.parameterSource());
        boolean isCodeParam = "code".equals(context.parameterSource());
        
        boolean isSystemNotFound = (context.errorType() == ValidationErrorType.SYSTEM_NOT_FOUND);
        boolean isCodeNotInValueSet = (context.errorType() == ValidationErrorType.CODE_NOT_IN_VALUESET);
        boolean isInvalidCode = (context.errorType() == ValidationErrorType.INVALID_CODE);
            
        String valueSetUrl = context.valueSet() != null && context.valueSet().hasUrl() ? 
            context.valueSet().getUrl() : "";
        String valueSetVersion = context.valueSet() != null && context.valueSet().hasVersion() ? 
            context.valueSet().getVersion() : "5.0.0";

        String systemUrl = "";
        if (context.system() != null && !context.system().isEmpty()) {
            systemUrl = context.system().getValue();
        } else if (codeSystem != null && codeSystem.hasUrl()) {
            systemUrl = codeSystem.getUrl();
        }
        String codeValue = context.code() != null ? context.code().getValue() : "";

        // 根據參數來源決定回傳格式
        if (context.originalCodeableConcept() != null) {
            result.addParameter("codeableConcept", context.originalCodeableConcept());
            // codeableConcept 情況下，不添加單獨的 code 參數
        } else if ("codeableConcept".equals(context.parameterSource()) || 
                   context.parameterSource().startsWith("codeableConcept.coding")) {
            CodeableConcept codeableConcept = reconstructCodeableConcept(context);
            result.addParameter("codeableConcept", codeableConcept);
            // codeableConcept 情況下，不添加單獨的 code 參數
        } else {
            // 只有在不是 codeableConcept 的情況下才添加 code 參數
            if (context.code() != null) {
                result.addParameter("code", context.code());
            }
        }
        
        if (!membershipOnly) {
            if (context.display() != null && !context.display().isEmpty()) {
                result.addParameter("display", context.display());
            } else if (codeSystem != null && context.code() != null) {
                ConceptDefinitionComponent concept = findConceptRecursive(codeSystem.getConcept(), context.code().getValue());
                if (concept != null && concept.hasDisplay()) {
                    result.addParameter("display", new StringType(concept.getDisplay()));
                }
            }
        }
        
        OperationOutcome outcome = new OperationOutcome();
        outcome.setMeta(null);
        outcome.setText(null);
     
        if (membershipOnly && isCodeableConcept) {
            var generalCcIssue = outcome.addIssue();
            generalCcIssue.setSeverity(OperationOutcome.IssueSeverity.ERROR);
            generalCcIssue.setCode(OperationOutcome.IssueType.CODEINVALID);
            
            Extension messageIdExt = new Extension();
            messageIdExt.setUrl(OperationOutcomeMessageId.MESSAGE_ID_EXTENSION_URL);
            messageIdExt.setValue(new StringType(OperationOutcomeMessageId.TX_GENERAL_CC_ERROR));
            generalCcIssue.addExtension(messageIdExt);
            
            CodeableConcept details1 = new CodeableConcept();
            Coding coding1 = details1.addCoding();
            coding1.setSystem("http://hl7.org/fhir/tools/CodeSystem/tx-issue-type");
            coding1.setCode("not-in-vs");
            details1.setText(String.format("$external:2:%s|%s$", valueSetUrl, valueSetVersion));
            generalCcIssue.setDetails(details1);
            
            if (context.originalCodeableConcept() != null) {
                for (int i = 0; i < context.originalCodeableConcept().getCoding().size(); i++) {
                    Coding c = context.originalCodeableConcept().getCoding().get(i);
                    
                    var notInVsIssue = outcome.addIssue();
                    notInVsIssue.setSeverity(OperationOutcome.IssueSeverity.INFORMATION);
                    notInVsIssue.setCode(OperationOutcome.IssueType.CODEINVALID);
                    
                    Extension messageIdExt2 = new Extension();
                    messageIdExt2.setUrl(OperationOutcomeMessageId.MESSAGE_ID_EXTENSION_URL);
                    messageIdExt2.setValue(new StringType(OperationOutcomeMessageId.NONE_OF_CODES_IN_VALUE_SET_ONE));
                    notInVsIssue.addExtension(messageIdExt2);
                    
                    CodeableConcept details2 = new CodeableConcept();
                    Coding coding2 = details2.addCoding();
                    coding2.setSystem("http://hl7.org/fhir/tools/CodeSystem/tx-issue-type");
                    coding2.setCode("this-code-not-in-vs");
                    
                    String codeText = c.hasCode() ? c.getCode() : "";
                    details2.setText(String.format("$external:%d:%s$", i == 0 ? 3 : 1, codeText));
                    notInVsIssue.setDetails(details2);
                    
                    notInVsIssue.addLocation(String.format("CodeableConcept.coding[%d].code", i));
                    notInVsIssue.addExpression(String.format("CodeableConcept.coding[%d].code", i));
                }
            }
        } else {
	        if (isInvalidDisplay) {            
	            int codingIndex = extractCodingIndex(context.parameterSource());
	
	            var displayIssue = outcome.addIssue();
	            displayIssue.setSeverity(OperationOutcome.IssueSeverity.ERROR);
	            displayIssue.setCode(OperationOutcome.IssueType.INVALID);
	
	            Extension messageIdExt = new Extension();
	            messageIdExt.setUrl(OperationOutcomeMessageId.MESSAGE_ID_EXTENSION_URL);
	            messageIdExt.setValue(
	                new StringType(
	                    OperationOutcomeMessageId.DISPLAY_NAME_SHOULD_BE_ONE_OF
	                )
	            );
	            displayIssue.addExtension(messageIdExt);
	
	            CodeableConcept details = new CodeableConcept();
	            Coding coding = details.addCoding();
	            coding.setSystem("http://hl7.org/fhir/tools/CodeSystem/tx-issue-type");
	            coding.setCode("invalid-display");
	            details.setText(
	                String.format("$external:1:%s$", context.display().getValue())
	            );
	            displayIssue.setDetails(details);
	
	            displayIssue.addLocation(
	                String.format("CodeableConcept.coding[%d].display", codingIndex)
	            );
	            displayIssue.addExpression(
	                String.format("CodeableConcept.coding[%d].display", codingIndex)
	            );
	
	            result.addParameter().setName("issues").setResource(outcome);
	            result.addParameter("message",
	                new StringType(String.format("$external:2:%s$", context.display().getValue()))
	            );
	            result.addParameter("result", new BooleanType(false));
	
	            if (context.system() != null) {
	                result.addParameter("system", context.system());
	            }
	            if (codeSystem != null && codeSystem.hasVersion()) {
	                result.addParameter("version", new StringType(codeSystem.getVersion()));
	            }
	
	            removeNarratives(result);
	            return result;
	        }
	        
	        // 根據錯誤類型動態建立 issues
	        if (isCodeableConcept && !isInvalidDisplay) {
	            // Issue 1: TX_GENERAL_CC_ERROR_MESSAGE
	            var generalCcIssue = outcome.addIssue();
	            generalCcIssue.setSeverity(OperationOutcome.IssueSeverity.ERROR);
	            generalCcIssue.setCode(OperationOutcome.IssueType.CODEINVALID);
	            
	            Extension messageIdExt = new Extension();
	            messageIdExt.setUrl(OperationOutcomeMessageId.MESSAGE_ID_EXTENSION_URL);
	            messageIdExt.setValue(new StringType(OperationOutcomeMessageId.TX_GENERAL_CC_ERROR));
	            generalCcIssue.addExtension(messageIdExt);
	            
	            CodeableConcept details1 = new CodeableConcept();
	            Coding coding1 = details1.addCoding();
	            coding1.setSystem("http://hl7.org/fhir/tools/CodeSystem/tx-issue-type");
	            coding1.setCode("not-in-vs");
	            details1.setText(String.format(
	                "No valid coding was found for the value set '%s|%s'",
	                valueSetUrl, valueSetVersion));
	            generalCcIssue.setDetails(details1);
	            
	            // Issue 2: 根據錯誤類型選擇
	            int codingIndex = extractCodingIndex(context.parameterSource());
	            
	            if (isSystemNotFound) {
	                // UNKNOWN_CODESYSTEM
	                var unknownSystemIssue = outcome.addIssue();
	                unknownSystemIssue.setSeverity(OperationOutcome.IssueSeverity.ERROR);
	                unknownSystemIssue.setCode(OperationOutcome.IssueType.NOTFOUND);
	                
	                Extension messageIdExt2 = new Extension();
	                messageIdExt2.setUrl("http://hl7.org/fhir/StructureDefinition/operationoutcome-message-id");
	                messageIdExt2.setValue(new StringType("UNKNOWN_CODESYSTEM"));
	                unknownSystemIssue.addExtension(messageIdExt2);
	                
	                CodeableConcept details2 = new CodeableConcept();
	                Coding coding2 = details2.addCoding();
	                coding2.setSystem("http://hl7.org/fhir/tools/CodeSystem/tx-issue-type");
	                coding2.setCode("not-found");
	                details2.setText(String.format("$external:2:%s$", systemUrl));
	                unknownSystemIssue.setDetails(details2);
	                
	                unknownSystemIssue.addLocation(String.format("CodeableConcept.coding[%d].system", codingIndex));
	                unknownSystemIssue.addExpression(String.format("CodeableConcept.coding[%d].system", codingIndex));
	            } else if (!isCodeNotInValueSet) {
	                // Unknown_Code_in_Version
	                var unknownCodeIssue = outcome.addIssue();
	                unknownCodeIssue.setSeverity(OperationOutcome.IssueSeverity.ERROR);
	                unknownCodeIssue.setCode(OperationOutcome.IssueType.CODEINVALID);
	                
	                Extension messageIdExt2 = new Extension();
	                messageIdExt2.setUrl(OperationOutcomeMessageId.MESSAGE_ID_EXTENSION_URL);
	                messageIdExt2.setValue(new StringType(OperationOutcomeMessageId.UNKNOWN_CODE_IN_VERSION));
	                unknownCodeIssue.addExtension(messageIdExt2);
	                
	                CodeableConcept details2 = new CodeableConcept();
	                Coding coding2 = details2.addCoding();
	                coding2.setSystem("http://hl7.org/fhir/tools/CodeSystem/tx-issue-type");
	                coding2.setCode("invalid-code");
	                details2.setText(String.format("$external:2:%s$", systemUrl));
	                unknownCodeIssue.setDetails(details2);
	                
	                unknownCodeIssue.addLocation(String.format("CodeableConcept.coding[%d].code", codingIndex));
	                unknownCodeIssue.addExpression(String.format("CodeableConcept.coding[%d].code", codingIndex));
	            }
	            
	            // Issue 3: None_of_the_provided_codes_are_in_the_value_set_one
	            var notInVsIssue = outcome.addIssue();
	            notInVsIssue.setSeverity(OperationOutcome.IssueSeverity.INFORMATION);
	            notInVsIssue.setCode(OperationOutcome.IssueType.CODEINVALID);
	            
	            Extension messageIdExt3 = new Extension();
	            messageIdExt3.setUrl(OperationOutcomeMessageId.MESSAGE_ID_EXTENSION_URL);
	            messageIdExt3.setValue(new StringType(OperationOutcomeMessageId.NONE_OF_CODES_IN_VALUE_SET_ONE));
	            notInVsIssue.addExtension(messageIdExt3);
	            
	            CodeableConcept details3 = new CodeableConcept();
	            Coding coding3 = details3.addCoding();
	            coding3.setSystem("http://hl7.org/fhir/tools/CodeSystem/tx-issue-type");
	            coding3.setCode("this-code-not-in-vs");
	            
	            if (isSystemNotFound) {
	                details3.setText(String.format("$external:1:%s#%s$", systemUrl, codeValue));
	            } else if (isCodeNotInValueSet) {
	                details3.setText(String.format("$external:1:%s|%s$", valueSetUrl, valueSetVersion));
	            } else {
	                details3.setText(String.format("$external:1:%s|%s$", valueSetUrl, valueSetVersion));
	            }
	            notInVsIssue.setDetails(details3);
	            
	            notInVsIssue.addLocation(String.format("CodeableConcept.coding[%d].code", codingIndex));
	            notInVsIssue.addExpression(String.format("CodeableConcept.coding[%d].code", codingIndex));
	            
	        } else {
	            String locationExpressionValue = isCoding ? "Coding.code" : "code";
	            String systemLocationExpressionValue = isCoding ? "Coding.system" : "system";
	            
	            String notInVsDetailsText;
	            String unknownSystemDetailsText;
	            
	            boolean isSpecialErrorType = (context.isInactive() != null && context.isInactive()) ||
	                    					  isSystemNotFound;
	            
	            if (isCodeParam) {
	                notInVsDetailsText = String.format("$external:1:%s|%s$", valueSetUrl, valueSetVersion);
	                unknownSystemDetailsText = String.format("$external:2:%s$", systemUrl);
	            } else if (isSpecialErrorType) {
	                notInVsDetailsText = String.format(
	                    "The provided code '%s#%s' was not found in the value set '%s|%s'",
	                    systemUrl, codeValue, valueSetUrl, valueSetVersion);
	                
	                if (isSystemNotFound) {
	                    unknownSystemDetailsText = String.format(
	                        "A definition for CodeSystem %s could not be found, so the code cannot be validated",
	                        systemUrl);
	                } else {
	                    unknownSystemDetailsText = String.format(
	                        "Unknown code '%s' in the CodeSystem '%s'",
	                        codeValue, systemUrl);
	                }
	            } else {
	                // coding 和 codeableConcept 參數的一般錯誤使用 $external 佔位符
	                notInVsDetailsText = String.format("$external:1:%s|%s$", valueSetUrl, valueSetVersion);
	                unknownSystemDetailsText = String.format("$external:2:%s$", systemUrl);
	            }
	            
	            // Issue 1: None_of_the_provided_codes_are_in_the_value_set_one (總是第一個)
	            var notInVsIssue = new OperationOutcomeIssueBuilder()
	                .setSeverity(OperationOutcome.IssueSeverity.ERROR)
	                .setCode(OperationOutcome.IssueType.CODEINVALID)
	                .setMessageId(OperationOutcomeMessageId.NONE_OF_CODES_IN_VALUE_SET_ONE)
	                .setDetails("not-in-vs", notInVsDetailsText)
	                .addLocation(locationExpressionValue)
	                .addExpression(locationExpressionValue)
	                .build();
	            outcome.addIssue(notInVsIssue);
	            
	            // Issue 2: 根據錯誤類型選擇 (UNKNOWN_CODESYSTEM 或 Unknown_Code_in_Version)
	            if (isSystemNotFound) {
	                var unknownSystemIssue = new OperationOutcomeIssueBuilder()
	                    .setSeverity(OperationOutcome.IssueSeverity.ERROR)
	                    .setCode(OperationOutcome.IssueType.NOTFOUND)
	                    .setMessageId(OperationOutcomeMessageId.UNKNOWN_CODESYSTEM)
	                    .setDetails("not-found", unknownSystemDetailsText)
	                    .addLocation(systemLocationExpressionValue)
	                    .addExpression(systemLocationExpressionValue)
	                    .build();
	                outcome.addIssue(unknownSystemIssue);
	            } else if (!isCodeNotInValueSet) {
	            	// code 參數和 coding 參數在非 CODE_NOT_IN_VALUESET 情況下都添加 Unknown_Code_in_Version
	                var unknownCodeIssue = new OperationOutcomeIssueBuilder()
	                    .setSeverity(OperationOutcome.IssueSeverity.ERROR)
	                    .setCode(OperationOutcome.IssueType.CODEINVALID)
	                    .setMessageId(OperationOutcomeMessageId.UNKNOWN_CODE_IN_VERSION)
	                    .setDetails("invalid-code", unknownSystemDetailsText)
	                    .addLocation(locationExpressionValue)
	                    .addExpression(locationExpressionValue)
	                    .build();
	                outcome.addIssue(unknownCodeIssue);
	            }
	        }
        }
        
        // 在添加到 Parameters 之前再次確保清除
        outcome.setText(null);
        outcome.setMeta(null);
        
        for (OperationOutcome.OperationOutcomeIssueComponent issue : outcome.getIssue()) {
            issue.getExtension().removeIf(ext -> 
                "http://hl7.org/fhir/StructureDefinition/narrativeLink".equals(ext.getUrl()));
        }
        
        // 添加 issues 參數
        result.addParameter().setName("issues").setResource(outcome);

        String mainErrorMessage;
        if (membershipOnly && isCodeableConcept) {
            mainErrorMessage = "$external:4$";
        } else {
	        if (isCodeParam) {
	        	// code 參數：根據錯誤類型決定
	            if (isSystemNotFound) {
	                mainErrorMessage = String.format("$external:3:%s$", systemUrl);
	            } else if (isCodeNotInValueSet) {
	                mainErrorMessage = String.format("$external:2:%s|%s$", valueSetUrl, valueSetVersion);
	            } else {
	                mainErrorMessage = String.format("$external:3:%s$", systemUrl);
	            }
	        } else if (isCoding && isSystemNotFound) {
	            mainErrorMessage = String.format(
	                "A definition for CodeSystem '%s' could not be found, so the code cannot be validated; " +
	                "The provided code '%s#%s' was not found in the value set '%s|%s'",
	                systemUrl, systemUrl, codeValue, valueSetUrl, valueSetVersion);
	        } else if (isCodeableConcept) {
	        	if (isSystemNotFound) {
	                mainErrorMessage = String.format("$external:3:%s$", systemUrl);
	            } else {
	                mainErrorMessage = "$external:3$";
	            }
	        } else {
	            mainErrorMessage = String.format("$external:3:%s$", systemUrl);
	        }
        }
        
        result.addParameter("message", new StringType(mainErrorMessage));
        result.addParameter("result", new BooleanType(isValid));

        if (!membershipOnly && !isCodeableConcept) {
            UriType systemToReturn = null;
            
            if (context.system() != null && !context.system().isEmpty()) {
            	// 確保轉換為 UriType
            	systemToReturn = new UriType(context.system().getValue());
            } 
            else if (codeSystem != null && codeSystem.hasUrl()) {
                systemToReturn = new UriType(codeSystem.getUrl());
            }
            
            if (systemToReturn != null) {
                result.addParameter("system", systemToReturn);
            }
        }

        boolean shouldIncludeVersion = false;
        
        if (!membershipOnly) {
	        if (isCodeParam) {
	        	// code 參數：在有 codeSystem 且不是 SYSTEM_NOT_FOUND 或 INVALID_CODE 時包含 version
	            shouldIncludeVersion = !isSystemNotFound && codeSystem != null && 
	                                   context.errorType() != ValidationErrorType.INVALID_CODE;
	            
	            // CODE_NOT_IN_VALUESET 情況下也要包含 version（但仍需要 codeSystem）
	            if (context.errorType() == ValidationErrorType.CODE_NOT_IN_VALUESET && codeSystem != null) {
	                shouldIncludeVersion = true;
	            }
	        } else {
	            shouldIncludeVersion = codeSystem != null && 
	                                   context.errorType() != ValidationErrorType.SYSTEM_NOT_FOUND &&
	                                   context.errorType() != ValidationErrorType.INVALID_CODE;
	
	            if (context.errorType() == ValidationErrorType.CODE_NOT_IN_VALUESET) {
	                shouldIncludeVersion = true;
	            }
	        }
	        
	        if (shouldIncludeVersion) {
	            if (codeSystem != null && codeSystem.hasVersion()) {
	                result.addParameter("version", new StringType(codeSystem.getVersion()));
	            } else if (context.systemVersion() != null && !context.systemVersion().isEmpty()) {
	                result.addParameter("version", context.systemVersion());
	            }
	        }
        }
        
        // 如果是 SYSTEM_NOT_FOUND，添加 x-unknown-system 參數
        if (isSystemNotFound && context.system() != null && !context.system().isEmpty()) {
            result.addParameter("x-unknown-system", new CanonicalType(context.system().getValue()));
        }

        removeNarratives(result);
        
        return result;
    }
    
    // 從 parameterSource 中提取 coding index
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

            if (param.hasPart()) {
                for (ParametersParameterComponent part : param.getPart()) {
                    if (part.hasResource() && part.getResource() instanceof DomainResource) {
                        DomainResource domainResource = (DomainResource) part.getResource();
                        domainResource.setText(null);

                        if (domainResource.hasMeta()) {
                            domainResource.setMeta(null);
                        }
                    }
                }
            }
        }
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
                                                    BooleanType abstractAllowed,BooleanType lenientDisplayValidation,
                                                    boolean membershipOnly) {
        
        ConceptDefinitionComponent concept = findConceptRecursive(codeSystem.getConcept(), code.getValue());
        if (concept == null) {
            return new ValidationResult(false, null, codeSystem, null, ValidationErrorType.INVALID_CODE, null, null, null);
        }

        // 檢查所有 filter 條件
        for (ConceptSetFilterComponent filter : filters) {
            if (!evaluateFilter(filter, concept, codeSystem)) {
            	return new ValidationResult(false, concept, codeSystem, concept.getDisplay(), 
                        ValidationErrorType.CODE_NOT_IN_VALUESET, null, null, null);
            }
        }

        if (isAbstractConcept(concept) && (abstractAllowed == null || !abstractAllowed.getValue())) {
            return new ValidationResult(false, concept, codeSystem, null, 
                                      ValidationErrorType.ABSTRACT_CODE_NOT_ALLOWED, null, null, null);
        }

        if (!membershipOnly && display != null && !display.isEmpty()) {
            boolean isValidDisplay = isDisplayValidExtended(concept, display, displayLanguage);
            if (!isValidDisplay) {
                String correctDisplay = getDisplayForLanguage(concept, displayLanguage);

                boolean isLenient = lenientDisplayValidation != null && lenientDisplayValidation.getValue();
                
                if (isLenient) {
                    return new ValidationResult(true, concept, codeSystem, correctDisplay, 
                                              ValidationErrorType.INVALID_DISPLAY_WARNING, null, null, null);
                } else {
                    return new ValidationResult(false, concept, codeSystem, correctDisplay, 
                                              ValidationErrorType.INVALID_DISPLAY, null, null, null);
                }
            }
        }

        String resolvedDisplay = getDisplayForLanguage(concept, displayLanguage);
        return new ValidationResult(true, concept, codeSystem, resolvedDisplay, null, null, null, null);
    }

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

    private ValidationResult validateCodeInConceptList(List<ConceptReferenceComponent> concepts, 
                                                     CodeType code, StringType display, 
                                                     CodeType displayLanguage, BooleanType abstractAllowed,
                                                     CodeSystem codeSystem, BooleanType lenientDisplayValidation,
                                                     boolean membershipOnly) {
        
    	for (ConceptReferenceComponent concept : concepts) {
            if (code.getValue().equals(concept.getCode())) {
                if (codeSystem != null) {
                    ConceptDefinitionComponent fullConcept = findConceptRecursive(
                        codeSystem.getConcept(), concept.getCode());
                    if (fullConcept != null) {
                        if (isAbstractConcept(fullConcept) && 
                            (abstractAllowed == null || !abstractAllowed.getValue())) {
                            return new ValidationResult(false, fullConcept, codeSystem, null, 
                                                      ValidationErrorType.ABSTRACT_CODE_NOT_ALLOWED, null, null, null);
                        }
                        
                        if (!membershipOnly && display != null && !display.isEmpty()) {
                            boolean isValidDisplay = isDisplayValidExtended(fullConcept, display, displayLanguage);
                            if (!isValidDisplay) {
                                String correctDisplay = getDisplayForLanguage(fullConcept, displayLanguage);
                                boolean isLenient = lenientDisplayValidation != null && lenientDisplayValidation.getValue();
                                
                                if (isLenient) {
                                    return new ValidationResult(true, fullConcept, codeSystem, correctDisplay, 
                                                              ValidationErrorType.INVALID_DISPLAY_WARNING, null, null, null);
                                } else {
                                    return new ValidationResult(false, fullConcept, codeSystem, correctDisplay, 
                                                              ValidationErrorType.INVALID_DISPLAY, null, null, null);
                                }
                            }
                        }
                        
                        String resolvedDisplay = getDisplayForLanguage(fullConcept, displayLanguage);
                        return new ValidationResult(true, fullConcept, codeSystem, resolvedDisplay, null, null, null, null);
                    }
                }
                
                String conceptDisplay = concept.hasDisplay() ? concept.getDisplay() : null;
                if (!membershipOnly && display != null && !display.isEmpty() && conceptDisplay != null && 
                    !display.getValue().equalsIgnoreCase(conceptDisplay)) {
                	
                	boolean isLenient = lenientDisplayValidation != null && lenientDisplayValidation.getValue();
                    
                    if (isLenient) {
                        return new ValidationResult(true, null, codeSystem, conceptDisplay, 
                                                  ValidationErrorType.INVALID_DISPLAY_WARNING, null, null, null);
                    } else {
                        return new ValidationResult(false, null, codeSystem, conceptDisplay, 
                                                  ValidationErrorType.INVALID_DISPLAY, null, null, null);
                    }
                }
                
                return new ValidationResult(true, null, codeSystem, conceptDisplay, null, null, null, null);
            }
        }
        
        return new ValidationResult(false, null, codeSystem, null, ValidationErrorType.INVALID_CODE, null, null, null);
    }

    private ValidationResult validateCodeInCodeSystem(CodeSystem codeSystem, CodeType code, 
            											StringType display, CodeType displayLanguage, 
            											BooleanType abstractAllowed, BooleanType activeOnly,
                                                        BooleanType lenientDisplayValidation,
                                                        boolean membershipOnly) {

    	ConceptDefinitionComponent concept = findConceptRecursive(codeSystem.getConcept(), code.getValue());
        if (concept == null) {
            return new ValidationResult(false, null, codeSystem, null, ValidationErrorType.INVALID_CODE, null, null, null);
        }

        if (isAbstractConcept(concept) && (abstractAllowed == null || !abstractAllowed.getValue())) {
            return new ValidationResult(false, concept, codeSystem, null, 
                                      ValidationErrorType.ABSTRACT_CODE_NOT_ALLOWED, null, null, null);
        }

        Boolean isInactive = isConceptInactive(concept);
        
        // 檢查 display 是否正確
        if (!membershipOnly && display != null && !display.isEmpty()) {
            boolean isValidDisplay = isDisplayValidExtended(concept, display, displayLanguage);
            if (!isValidDisplay) {
                String correctDisplay = getDisplayForLanguage(concept, displayLanguage);
                boolean isLenient = lenientDisplayValidation != null && lenientDisplayValidation.getValue();
                
                if (isLenient) {
                    return new ValidationResult(true, concept, codeSystem, correctDisplay, 
                                              ValidationErrorType.INVALID_DISPLAY_WARNING, 
                                              isInactive, null, null);
                } else {
                	// 檢查是否因為語言不支援而導致 display 錯誤
                    if (displayLanguage != null && !displayLanguage.isEmpty()) {
                        String requestedLanguage = displayLanguage.getValue();
                        
                        // 檢查是否為多語言格式（包含逗號）
                        boolean isMultiLanguageRequest = requestedLanguage.contains(",");
                        
                        // 如果是多語言請求，直接使用普通的 INVALID_DISPLAY
                        if (isMultiLanguageRequest) {
                            return new ValidationResult(false, concept, codeSystem, correctDisplay, 
                                                      ValidationErrorType.INVALID_DISPLAY, 
                                                      isInactive, null, null);
                        }
                        
                        // 單一語言的情況：先檢查 CodeSystem 是否為多語言系統
                        boolean isMultilingualCodeSystem = codeSystem.hasLanguage() || 
                                                           concept.hasDesignation() || 
                                                           codeSystemHasAnyDesignations(codeSystem);
                        
                        // 只有當 CodeSystem 是多語言系統時，才檢查語言支援
                        if (isMultilingualCodeSystem) {
                            // 檢查 concept 是否有該語言的 designation
                            boolean hasLanguageDesignation = hasDesignationForLanguageList(concept, requestedLanguage);
                            
                            // 檢查 CodeSystem 的預設語言是否匹配
                            boolean codeSystemLanguageMatches = false;
                            if (codeSystem.hasLanguage()) {
                                String csLang = codeSystem.getLanguage();
                                codeSystemLanguageMatches = csLang.equalsIgnoreCase(requestedLanguage) || 
                                                            csLang.startsWith(requestedLanguage + "-");
                            }
                            
                            // 如果既沒有 designation 也不是 CodeSystem 的預設語言
                            if (!hasLanguageDesignation && !codeSystemLanguageMatches) {
                                return new ValidationResult(false, concept, codeSystem, correctDisplay, 
                                                          ValidationErrorType.INVALID_DISPLAY_WITH_LANGUAGE_ERROR, 
                                                          isInactive, null, null);
                            }
                        }
                    }
                    
                    return new ValidationResult(false, concept, codeSystem, correctDisplay, 
                                              ValidationErrorType.INVALID_DISPLAY, 
                                              isInactive, null, null);
                }
            }
        }
     /*   if (!membershipOnly && display != null && !display.isEmpty()) {
            boolean isValidDisplay = isDisplayValidExtended(concept, display, displayLanguage);
            if (!isValidDisplay) {
                String correctDisplay = getDisplayForLanguage(concept, displayLanguage);
                boolean isLenient = lenientDisplayValidation != null && lenientDisplayValidation.getValue();
                
                if (isLenient) {
                    return new ValidationResult(true, concept, codeSystem, correctDisplay, 
                                              ValidationErrorType.INVALID_DISPLAY_WARNING, 
                                              isInactive, null, null);
                } else {
                	// 在這裡添加檢查：是否因為語言不支援而導致 display 錯誤
                    if (displayLanguage != null && !displayLanguage.isEmpty()) {
                        // 檢查是否為多語言系統
                        boolean isMultilingualCodeSystem = codeSystem.hasLanguage() || 
                                                           concept.hasDesignation() || 
                                                           codeSystemHasAnyDesignations(codeSystem);
                        
                        if (isMultilingualCodeSystem) {
                            // 檢查請求的語言是否有 designation
                            boolean hasRequestedLanguageDesignation = hasDesignationForLanguageList(concept, displayLanguage.getValue());
                            
                            // 如果沒有該語言的 designation，返回語言錯誤
                            if (!hasRequestedLanguageDesignation) {
                                return new ValidationResult(false, concept, codeSystem, correctDisplay, 
                                                          ValidationErrorType.INVALID_DISPLAY_WITH_LANGUAGE_ERROR, 
                                                          isInactive, null, null);
                            }
                        }
                    }
                    
                    return new ValidationResult(false, concept, codeSystem, correctDisplay, 
                                              ValidationErrorType.INVALID_DISPLAY, 
                                              isInactive, null, null);
                }
            }
        }*/
        
        // display 正確（或沒有提供），檢查語言支援
        if (displayLanguage != null && !displayLanguage.isEmpty()) {
            String requestedLanguage = displayLanguage.getValue();
                     
            // 檢查使用者提供的 display 是否匹配某個 designation
            boolean isDisplayFromDesignation = false;
            if (display != null && !display.isEmpty()) {
                isDisplayFromDesignation = isDisplayMatchingAnyDesignation(concept, display.getValue());
            }
            
            // 只有當 display 不是來自 designation 時才檢查語言支援
            if (!isDisplayFromDesignation) {
            	
                // 檢查是否使用預設 display 且符合請求的語言
                boolean isUsingDefaultDisplayInRequestedLanguage = false;
                
                // 如果 concept 有預設 display，且與請求的 display 相符（或沒有提供 display）
                if (concept.hasDisplay()) {
                    String conceptDefaultDisplay = concept.getDisplay();
                    boolean displayMatches = (display == null || display.isEmpty()) || 
                                            conceptDefaultDisplay.equalsIgnoreCase(display.getValue());

                    if (displayMatches) {
                        // 檢查 CodeSystem 的語言設定
                        String codeSystemLanguage = codeSystem.hasLanguage() ? codeSystem.getLanguage() : null;
                        
                        // 如果 CodeSystem 的語言與請求的語言匹配，則視為有效
                        if (codeSystemLanguage != null && codeSystemLanguage.startsWith(requestedLanguage)) {
                            isUsingDefaultDisplayInRequestedLanguage = true;
                        }
                        
                        // 也檢查 concept 層級的語言設定
                        if (!isUsingDefaultDisplayInRequestedLanguage) {
                            for (ConceptPropertyComponent property : concept.getProperty()) {
                                if ("language".equals(property.getCode()) && 
                                    property.getValue() instanceof CodeType) {
                                    String conceptLanguage = ((CodeType) property.getValue()).getValue();
                                    if (conceptLanguage != null && conceptLanguage.startsWith(requestedLanguage)) {
                                        isUsingDefaultDisplayInRequestedLanguage = true;
                                        break;
                                    }
                                }
                            }
                        }
                    }
                }
                                
                // 如果正在使用符合請求語言的預設 display，則不產生語言警告
                if (!isUsingDefaultDisplayInRequestedLanguage) {
                    // 檢查是否有該語言的 designation
                    boolean hasRequestedLanguageDesignation = hasDesignationForLanguageList(concept, requestedLanguage);
                    
                    // 如果沒有找到請求語言的 designation
                    if (!hasRequestedLanguageDesignation) {
                        String defaultDisplay = concept.hasDisplay() ? concept.getDisplay() : null;

                        // 1. 先檢查 concept 本身是否有 designation
                        boolean conceptHasAnyDesignation = concept.hasDesignation() && !concept.getDesignation().isEmpty();
                        
                        // 2. 檢查整個 CodeSystem 是否有任何 designation
                        boolean codeSystemHasDesignations = codeSystemHasAnyDesignations(codeSystem);
                        
                        // 3. 檢查 CodeSystem 是否有明確的語言設定（表示它是為特定語言設計的）
                        boolean codeSystemHasLanguage = codeSystem.hasLanguage() && codeSystem.getLanguage() != null;
                        
                        // 判斷是否為多語言系統：
                        // - concept 或其他 concept 有 designation，或
                        // - CodeSystem 有明確的語言設定（即使沒有 designation）
                        boolean isMultilingualCodeSystem = conceptHasAnyDesignation || 
                                                           codeSystemHasDesignations || 
                                                           codeSystemHasLanguage;
                        
                        // 只有當 CodeSystem 是多語言系統時才產生警告
                        if (isMultilingualCodeSystem) {
                            // 檢查 CodeSystem 是否有任何該語言的支援
                            boolean codeSystemSupportsLanguage = codeSystemHasLanguageSupport(codeSystem, requestedLanguage);
                            
                            // 決定使用哪個 message ID
                            ValidationErrorType warningType = codeSystemSupportsLanguage ? 
                                ValidationErrorType.NO_DISPLAY_FOR_LANGUAGE_SOME : 
                                ValidationErrorType.NO_DISPLAY_FOR_LANGUAGE_NONE;
                            
                            // display 正確但語言不支援
                            return new ValidationResult(true, concept, codeSystem, defaultDisplay, 
                                                      warningType, isInactive, null, null);
                        }
                    }
                }
            }
        }

        String resolvedDisplay = getDisplayForLanguage(concept, displayLanguage);

        return new ValidationResult(true, concept, codeSystem, resolvedDisplay, null, isInactive, null, null);
    }
    
    // 檢查 CodeSystem 是否有任何 designation（判斷是否為多語言系統）
    private boolean codeSystemHasAnyDesignations(CodeSystem codeSystem) {
        if (codeSystem == null) {
            return false;
        }
        
        return checkConceptsForAnyDesignation(codeSystem.getConcept());
    }

    private boolean checkConceptsForAnyDesignation(List<ConceptDefinitionComponent> concepts) {
        for (ConceptDefinitionComponent concept : concepts) {
            // 檢查當前 concept 是否有任何 designation
            if (concept.hasDesignation() && !concept.getDesignation().isEmpty()) {
                return true;
            }
            
            // 遞歸檢查子 concept
            if (checkConceptsForAnyDesignation(concept.getConcept())) {
                return true;
            }
        }
        
        return false;
    }
    
    // 檢查 display 是否匹配任何 designation
    private boolean isDisplayMatchingAnyDesignation(ConceptDefinitionComponent concept, String displayValue) {
        if (concept == null || displayValue == null || displayValue.isEmpty()) {
            return false;
        }
        
        for (ConceptDefinitionDesignationComponent designation : concept.getDesignation()) {
            if (displayValue.equalsIgnoreCase(designation.getValue())) {
                return true;
            }
        }
        
        return false;
    }

    // 支援逗號分隔的多個語言代碼檢查
    private boolean hasDesignationForLanguageList(ConceptDefinitionComponent concept, String languageList) {
        if (concept == null || languageList == null || languageList.isEmpty()) {
            return false;
        }
        
        String[] languages = languageList.split(",");
        
        for (String lang : languages) {
            String trimmedLang = lang.trim();
            if (hasDesignationForLanguage(concept, trimmedLang)) {
                return true;
            }
        }
        
        return false;
    }
    
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

	 private boolean isDisplayValidExtended(ConceptDefinitionComponent concept, StringType display, 
	                                      CodeType displayLanguage) {
		 if (display == null || display.isEmpty()) {
		        return true;
		    }
		    
		    String displayValue = display.getValue();
		    
		    if (concept.hasDisplay() && displayValue.equalsIgnoreCase(concept.getDisplay())) {
		        return true;
		    }
		    
		    if (displayLanguage == null || displayLanguage.isEmpty()) {
		        return false;
		    }
		    
		    String[] languages = displayLanguage.getValue().split(",");
		    
		    for (String lang : languages) {
		        lang = lang.trim();
		        
		        // 檢查該語言的 designation
		        for (ConceptDefinitionDesignationComponent desig : concept.getDesignation()) {
		            if (lang.equalsIgnoreCase(desig.getLanguage()) && 
		                displayValue.equalsIgnoreCase(desig.getValue())) {
		                return true;
		            }
		        }
		    }
		    
		    return false;
	 }

	 private String getDisplayForLanguage(ConceptDefinitionComponent concept, CodeType displayLanguage) {
		    if (displayLanguage == null || concept == null) {
		        return concept != null ? concept.getDisplay() : null;
		    }
		    
		    String[] languages = displayLanguage.getValue().split(",");
		    
		    for (String lang : languages) {
		        lang = lang.trim();
		        
		        for (ConceptDefinitionDesignationComponent desig : concept.getDesignation()) {
		            if (lang.equalsIgnoreCase(desig.getLanguage())) {
		                return desig.getValue();
		            }
		        }
		    }
		    
		    return concept.getDisplay();
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
            
            // 在這裡聲明並初始化 availableVersions
            List<String> availableVersions = new ArrayList<>();
            if (allVersionsResult.size() > 0) {
                for (int i = 0; i < allVersionsResult.size(); i++) {
                    CodeSystem cs = (CodeSystem) allVersionsResult.getResources(i, i+1).get(0);
                    if (cs.hasVersion()) {
                        availableVersions.add(cs.getVersion());
                    }
                }
            }
            
            // 拋出包含可用版本信息的異常
            CodeSystemVersionNotFoundException exception = new CodeSystemVersionNotFoundException(
                String.format("CodeSystem with URL '%s' and version '%s' not found", url, version));
            exception.setUrl(url);
            exception.setRequestedVersion(version);
            exception.setAvailableVersions(availableVersions);
            throw exception;
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
        outcome.setText(null);
        
        String codeValue = successfulParams.code().getValue();
        String systemUrl = successfulParams.system() != null ? successfulParams.system().getValue() : "";
        String valueSetUrl = targetValueSet != null && targetValueSet.hasUrl() ? 
            targetValueSet.getUrl() : "";
        String valueSetVersion = targetValueSet != null && targetValueSet.hasVersion() ? 
            targetValueSet.getVersion() : "5.0.0";
        
        // Issue 1: STATUS_CODE_WARNING_CODE (business-rule)
        var statusCodeIssue = outcome.addIssue();
        statusCodeIssue.setSeverity(OperationOutcome.IssueSeverity.ERROR);
        statusCodeIssue.setCode(OperationOutcome.IssueType.BUSINESSRULE);
        
        Extension messageIdExt1 = new Extension();
        messageIdExt1.setUrl("http://hl7.org/fhir/StructureDefinition/operationoutcome-message-id");
        messageIdExt1.setValue(new StringType("STATUS_CODE_WARNING_CODE"));
        statusCodeIssue.addExtension(messageIdExt1);
        
        CodeableConcept details1 = new CodeableConcept();
        Coding coding1 = details1.addCoding();
        coding1.setSystem("http://hl7.org/fhir/tools/CodeSystem/tx-issue-type");
        coding1.setCode("code-rule");
        details1.setText(String.format("The concept '%s' is valid but is not active", codeValue));
        statusCodeIssue.setDetails(details1);
        
        statusCodeIssue.addLocation("Coding.code");
        statusCodeIssue.addExpression("Coding.code");
        
        // Issue 2: Not in ValueSet error
        var notInVsIssue = outcome.addIssue();
        notInVsIssue.setSeverity(OperationOutcome.IssueSeverity.ERROR);
        notInVsIssue.setCode(OperationOutcome.IssueType.CODEINVALID);
        
        Extension messageIdExt2 = new Extension();
        messageIdExt2.setUrl("http://hl7.org/fhir/StructureDefinition/operationoutcome-message-id");
        messageIdExt2.setValue(new StringType("None_of_the_provided_codes_are_in_the_value_set_one"));
        notInVsIssue.addExtension(messageIdExt2);
        
        CodeableConcept details2 = new CodeableConcept();
        Coding coding2 = details2.addCoding();
        coding2.setSystem("http://hl7.org/fhir/tools/CodeSystem/tx-issue-type");
        coding2.setCode("not-in-vs");
        details2.setText(String.format(
            "The provided code '%s#%s' was not found in the value set '%s|%s'",
            systemUrl, codeValue, valueSetUrl, valueSetVersion));
        notInVsIssue.setDetails(details2);
        
        notInVsIssue.addLocation("Coding.code");
        notInVsIssue.addExpression("Coding.code");
        
        // Issue 3: INACTIVE_CONCEPT_FOUND (warning)
        var inactiveConceptIssue = outcome.addIssue();
        inactiveConceptIssue.setSeverity(OperationOutcome.IssueSeverity.WARNING);
        inactiveConceptIssue.setCode(OperationOutcome.IssueType.BUSINESSRULE);
        
        Extension messageIdExt3 = new Extension();
        messageIdExt3.setUrl("http://hl7.org/fhir/StructureDefinition/operationoutcome-message-id");
        messageIdExt3.setValue(new StringType("INACTIVE_CONCEPT_FOUND"));
        inactiveConceptIssue.addExtension(messageIdExt3);
        
        CodeableConcept details3 = new CodeableConcept();
        Coding coding3 = details3.addCoding();
        coding3.setSystem("http://hl7.org/fhir/tools/CodeSystem/tx-issue-type");
        coding3.setCode("code-comment");
        details3.setText(String.format(
            "The concept '%s' has a status of inactive and its use should be reviewed", codeValue));
        inactiveConceptIssue.setDetails(details3);
        
        inactiveConceptIssue.addLocation("Coding");
        inactiveConceptIssue.addExpression("Coding");
        
        // 5. issues 參數
        result.addParameter().setName("issues").setResource(outcome);
        
        // 6. message 參數 - 包含三個訊息
        String message = String.format(
                "The concept '%s' has a status of inactive and its use should be reviewed; " +
                "The concept '%s' is valid but is not active; " +
                "The provided code '%s#%s' was not found in the value set '%s|%s'",
                codeValue, codeValue, systemUrl, codeValue, valueSetUrl, valueSetVersion);
            result.addParameter("message", new StringType(message));
        
        // 7. result 參數
        result.addParameter("result", new BooleanType(false));
        
        // 8. system 參數
        if (successfulParams.system() != null) {
            result.addParameter("system", successfulParams.system());
        }
        
        // 9. version 參數
        if (effectiveSystemVersion != null && !effectiveSystemVersion.isEmpty()) {
            result.addParameter("version", effectiveSystemVersion);
        } else if (matchedCodeSystem != null && matchedCodeSystem.hasVersion()) {
            result.addParameter("version", new StringType(matchedCodeSystem.getVersion()));
        }
        
        return result;
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
    	OperationOutcome outcome = OperationOutcomeHelper.createValueSetNotFoundOutcome(valueSetUrl, errorMessage);

        outcome.setText(null);
        outcome.setMeta(null);
        outcome.setId((String) null);
        
        return outcome;
    }

    private String extractValueSetUrlFromException(ResourceNotFoundException e, 
                                                  UriType url, 
                                                  CanonicalType urlCanonical,
                                                  UriType valueSetUrl, 
                                                  CanonicalType valueSetCanonical,
                                                  UrlType urlParam,
                                                  UrlType valueSetUrlType) {
        UriType resolvedUrl = resolveUriParameter(url, urlCanonical, urlParam);
        if (resolvedUrl != null && !resolvedUrl.isEmpty()) {
            return resolvedUrl.getValue();
        }
        
        UriType resolvedValueSetUrl = resolveUriParameter(valueSetUrl, valueSetCanonical, valueSetUrlType);
        if (resolvedValueSetUrl != null && !resolvedValueSetUrl.isEmpty()) {
            return resolvedValueSetUrl.getValue();
        }

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
    	OperationOutcome outcome = OperationOutcomeHelper.createInvalidRequestOutcome(errorMessage);

        outcome.setText(null);
        outcome.setMeta(null);
        
        return outcome;
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
        outcome.setText(null);
        
        String systemUrl = params.system() != null ? params.system().getValue() : "";
        String valueSetUrl = targetValueSet != null && targetValueSet.hasUrl() ? 
            targetValueSet.getUrl() : "";
        String valueSetVersion = targetValueSet != null && targetValueSet.hasVersion() ? 
            targetValueSet.getVersion() : "5.0.0";
        
        // Issue 1: System is ValueSet error
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
        
        // Issue 2: Code not in ValueSet
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
        
        // 3. issues 參數
        result.addParameter().setName("issues").setResource(outcome);
        
        // 4. message 參數
        String message = buildMessageFromOperationOutcome(outcome);
        result.addParameter("message", new StringType(message));
        
        // 5. result 參數
        result.addParameter("result", new BooleanType(false));
        
        // 6. system 參數
        if (params.system() != null) {
            result.addParameter("system", params.system());
        }
        
        return result;
    }
    
    private String buildMessageFromOperationOutcome(OperationOutcome outcome) {
        if (outcome == null || !outcome.hasIssue()) {
            return "";
        }
        
        List<String> messages = new ArrayList<>();
        
        for (OperationOutcome.OperationOutcomeIssueComponent issue : outcome.getIssue()) {
            if (issue.hasDetails() && issue.getDetails().hasText()) {
                String text = issue.getDetails().getText();
                messages.add(text);
            }
        }
        
        return String.join("; ", messages);
    }
    
    private Parameters buildSuccessResponseWithDisplayWarning(ValidationParams successfulParams, 
		            											String matchedDisplay, 
		            											CodeSystem matchedCodeSystem, 
		            											ValueSet targetValueSet,
		            											StringType effectiveSystemVersion,
		            											String incorrectDisplay) {
		Parameters result = new Parameters();
		
		// 判斷參數來源
	    boolean isCodeableConcept = successfulParams.originalCodeableConcept() != null || 
	                                "codeableConcept".equals(successfulParams.parameterSource()) ||
	                                successfulParams.parameterSource().startsWith("codeableConcept.coding");
	    boolean isCoding = "coding".equals(successfulParams.parameterSource());
		
		// 1. code 參數
		if (successfulParams != null && successfulParams.code() != null) {
			result.addParameter("code", successfulParams.code());
		}
		
		// 2. codeableConcept 參數 - 如果是 codeableConcept 來源
	    if (isCodeableConcept && successfulParams.originalCodeableConcept() != null) {
	        result.addParameter("codeableConcept", successfulParams.originalCodeableConcept());
	    }
		
		// 3. display 參數 - 使用正確的 display
		if (matchedDisplay != null) {
			result.addParameter("display", new StringType(matchedDisplay));
		}
		
		// 4. 建立 OperationOutcome (帶 warning)
		OperationOutcome outcome = new OperationOutcome();
		outcome.setMeta(null);
		outcome.setText(null);
		
		var displayWarningIssue = outcome.addIssue();
		displayWarningIssue.setSeverity(OperationOutcome.IssueSeverity.WARNING);
		displayWarningIssue.setCode(OperationOutcome.IssueType.INVALID);
		
		Extension messageIdExt = new Extension();
		messageIdExt.setUrl("http://hl7.org/fhir/StructureDefinition/operationoutcome-message-id");
		messageIdExt.setValue(new StringType("Display_Name_for__should_be_one_of__instead_of"));
		displayWarningIssue.addExtension(messageIdExt);
		
		CodeableConcept details = new CodeableConcept();
		Coding coding = details.addCoding();
		coding.setSystem("http://hl7.org/fhir/tools/CodeSystem/tx-issue-type");
		coding.setCode("invalid-display");
		details.setText(String.format("$external:1:%s$", incorrectDisplay != null ? incorrectDisplay : ""));
		displayWarningIssue.setDetails(details);
		
		// 根據參數來源設置 location 和 expression
	    if (isCodeableConcept) {
	        int codingIndex = extractCodingIndex(successfulParams.parameterSource());
	        displayWarningIssue.addLocation(String.format("CodeableConcept.coding[%d].display", codingIndex));
	        displayWarningIssue.addExpression(String.format("CodeableConcept.coding[%d].display", codingIndex));
	    } else if (isCoding) {
	        displayWarningIssue.addLocation("Coding.display");
	        displayWarningIssue.addExpression("Coding.display");
	    } else {
	        displayWarningIssue.addLocation("display");
	        displayWarningIssue.addExpression("display");
	    }
		
		// 5. issues 參數
		result.addParameter().setName("issues").setResource(outcome);
		
		// 6. message 參數
		String message;
	    if (isCoding) {
	        message = String.format("$external:1:%s$", incorrectDisplay != null ? incorrectDisplay : "");
	    } else {
	        message = String.format("$external:2:%s$", incorrectDisplay != null ? incorrectDisplay : "");
	    }
	    result.addParameter("message", new StringType(message));
		
		// 7. result 參數 - 寬鬆模式下仍為 true
		result.addParameter("result", new BooleanType(true));
		
		// 8. system 參數
		if (successfulParams != null && successfulParams.system() != null && !successfulParams.system().isEmpty()) {
			result.addParameter("system", successfulParams.system());
		} else if (matchedCodeSystem != null && matchedCodeSystem.hasUrl()) {
			result.addParameter("system", new UriType(matchedCodeSystem.getUrl()));
		}
		
		// 9. version 參數
		String versionToReturn = null;
		if (effectiveSystemVersion != null && !effectiveSystemVersion.isEmpty()) {
			versionToReturn = effectiveSystemVersion.getValue();
		} else if (matchedCodeSystem != null && matchedCodeSystem.hasVersion()) {
			versionToReturn = matchedCodeSystem.getVersion();
		}
		
		if (versionToReturn != null) {
			result.addParameter("version", new StringType(versionToReturn));
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
		outcome.setText(null);
			
		String systemUrl = params.system() != null ? params.system().getValue() : "";
		String valueSetUrl = targetValueSet != null && targetValueSet.hasUrl() ? 
				targetValueSet.getUrl() : "";
		String valueSetVersion = targetValueSet != null && targetValueSet.hasVersion() ? 
				targetValueSet.getVersion() : "5.0.0";		
		
		// Issue 3: Unknown CodeSystem error
	    var unknownSystemIssue = outcome.addIssue();
	    unknownSystemIssue.setSeverity(OperationOutcome.IssueSeverity.ERROR);
	    unknownSystemIssue.setCode(OperationOutcome.IssueType.NOTFOUND);
	    
	    Extension messageIdExt3 = new Extension();
	    messageIdExt3.setUrl("http://hl7.org/fhir/StructureDefinition/operationoutcome-message-id");
	    messageIdExt3.setValue(new StringType("UNKNOWN_CODESYSTEM"));
	    unknownSystemIssue.addExtension(messageIdExt3);
	    
	    CodeableConcept details3 = new CodeableConcept();
	    Coding coding3 = details3.addCoding();
	    coding3.setSystem("http://hl7.org/fhir/tools/CodeSystem/tx-issue-type");
	    coding3.setCode("not-found");
	    details3.setText(String.format(
	        "A definition for CodeSystem '%s' could not be found, so the code cannot be validated",
	        systemUrl));
	    unknownSystemIssue.setDetails(details3);
	    
	    unknownSystemIssue.addLocation("Coding.system");
	    unknownSystemIssue.addExpression("Coding.system");
	    
	    // Issue 2: Relative system reference error
	    var relativeSystemIssue = outcome.addIssue();
	    relativeSystemIssue.setSeverity(OperationOutcome.IssueSeverity.ERROR);
	    relativeSystemIssue.setCode(OperationOutcome.IssueType.INVALID);
	    
	    Extension messageIdExt2 = new Extension();
	    messageIdExt2.setUrl("http://hl7.org/fhir/StructureDefinition/operationoutcome-message-id");
	    messageIdExt2.setValue(new StringType("Terminology_TX_System_Relative"));
	    relativeSystemIssue.addExtension(messageIdExt2);
	    
	    CodeableConcept details2 = new CodeableConcept();
	    Coding coding2 = details2.addCoding();
	    coding2.setSystem("http://hl7.org/fhir/tools/CodeSystem/tx-issue-type");
	    coding2.setCode("invalid-data");
	    details2.setText("Coding.system must be an absolute reference, not a local reference");
	    relativeSystemIssue.setDetails(details2);
	    
	    relativeSystemIssue.addLocation("Coding.system");
	    relativeSystemIssue.addExpression("Coding.system");
		
		// Issue 1: Code not in ValueSet
	    var notInVsIssue = outcome.addIssue();
	    notInVsIssue.setSeverity(OperationOutcome.IssueSeverity.ERROR);
	    notInVsIssue.setCode(OperationOutcome.IssueType.CODEINVALID);
	    
	    Extension messageIdExt1 = new Extension();
	    messageIdExt1.setUrl("http://hl7.org/fhir/StructureDefinition/operationoutcome-message-id");
	    messageIdExt1.setValue(new StringType("None_of_the_provided_codes_are_in_the_value_set_one"));
	    notInVsIssue.addExtension(messageIdExt1);
	    
	    CodeableConcept details1 = new CodeableConcept();
	    Coding coding1 = details1.addCoding();
	    coding1.setSystem("http://hl7.org/fhir/tools/CodeSystem/tx-issue-type");
	    coding1.setCode("not-in-vs");
	    details1.setText(String.format(
	        "The provided code '%s#%s' was not found in the value set '%s|%s'",
	        systemUrl, params.code().getValue(), valueSetUrl, valueSetVersion));
	    notInVsIssue.setDetails(details1);
	    
	    notInVsIssue.addLocation("Coding.code");
	    notInVsIssue.addExpression("Coding.code");
	    
		// 3. issues 參數
		result.addParameter().setName("issues").setResource(outcome);
			
		// 4. message 參數
		String message = buildMessageFromOperationOutcome(outcome);
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
		outcome.setText(null);
		
		String valueSetUrl = targetValueSet != null && targetValueSet.hasUrl() ? 
		targetValueSet.getUrl() : "";
		String valueSetVersion = targetValueSet != null && targetValueSet.hasVersion() ? 
		targetValueSet.getVersion() : "5.0.0";
		
		// Issue 1: No system warning
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
		
		// Issue 2: Code not in ValueSet
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

		// 3. issues 參數
		result.addParameter().setName("issues").setResource(outcome);
		
		// 4. message 參數
		String message = buildMessageFromOperationOutcome(outcome);
		result.addParameter("message", new StringType(message));
		
		// 5. result 參數
		result.addParameter("result", new BooleanType(false));
		
		return result;
	}    
    
    private boolean isRelativeReference(String systemUrl) {
        if (systemUrl == null || systemUrl.isEmpty()) {
            return false;
        }

        return !systemUrl.matches("^[a-zA-Z][a-zA-Z0-9+.-]*:.*");
    }  
    
    public class CodeSystemVersionNotFoundException extends ResourceNotFoundException {
        private String url;
        private String requestedVersion;
        private List<String> availableVersions;
        
        public CodeSystemVersionNotFoundException(String message) {
            super(message);
            this.availableVersions = new ArrayList<>();
        }
        
        public String getUrl() {
            return url;
        }
        
        public void setUrl(String url) {
            this.url = url;
        }
        
        public String getRequestedVersion() {
            return requestedVersion;
        }
        
        public void setRequestedVersion(String requestedVersion) {
            this.requestedVersion = requestedVersion;
        }
        
        public List<String> getAvailableVersions() {
            return availableVersions;
        }
        
        public void setAvailableVersions(List<String> availableVersions) {
            this.availableVersions = availableVersions;
        }
    }
    
    private Parameters buildCodeSystemVersionNotFoundError( ValidationParams params, ValueSet targetValueSet,
												            StringType effectiveSystemVersion,
												            CodeSystemVersionNotFoundException versionException) {
        
    	// === 快速測試開始 ===
        System.out.println("=== DEBUG buildCodeSystemVersionNotFoundError ===");
        System.out.println("params.parameterSource() = " + params.parameterSource());
        System.out.println("effectiveSystemVersion = " + (effectiveSystemVersion != null ? effectiveSystemVersion.getValue() : "null"));
        
        if (params.originalCoding() != null) {
            System.out.println("originalCoding.system = " + params.originalCoding().getSystem());
            System.out.println("originalCoding.code = " + params.originalCoding().getCode());
            System.out.println("originalCoding.version = " + (params.originalCoding().hasVersion() ? params.originalCoding().getVersion() : "null"));
        }
        
        if (params.originalCodeableConcept() != null) {
            System.out.println("originalCodeableConcept has " + params.originalCodeableConcept().getCoding().size() + " codings");
            for (int i = 0; i < params.originalCodeableConcept().getCoding().size(); i++) {
                Coding c = params.originalCodeableConcept().getCoding().get(i);
                System.out.println("  coding[" + i + "].version = " + (c.hasVersion() ? c.getVersion() : "null"));
            }
        }
        
        boolean hasExplicit = hasExplicitlyProvidedVersion(params);
        System.out.println("hasExplicitlyProvidedVersion() = " + hasExplicit);
        System.out.println("=== END DEBUG ===");
        // === 快速測試結束 ===
    	
    	Parameters result = new Parameters();
        
        // 判斷參數來源
        boolean isCoding = "coding".equals(params.parameterSource());
        boolean isCodeableConcept = params.originalCodeableConcept() != null || 
                                    "codeableConcept".equals(params.parameterSource()) ||
                                    params.parameterSource().startsWith("codeableConcept.coding");
        boolean isCodeParam = "code".equals(params.parameterSource());
        
        // 判斷是否為「CodeSystem 完全不存在」的情況(沒有任何已知版本)
        boolean isCodeSystemNotFound = versionException != null && 
                                       versionException.getAvailableVersions() != null &&
                                       versionException.getAvailableVersions().isEmpty();
        
        String systemUrl = params.system() != null ? params.system().getValue() : "";
        String requestedVersion = effectiveSystemVersion != null ? effectiveSystemVersion.getValue() : "";
        String valueSetUrl = targetValueSet != null && targetValueSet.hasUrl() ? 
            targetValueSet.getUrl() : "";
        String valueSetVersion = targetValueSet != null && targetValueSet.hasVersion() ? 
            targetValueSet.getVersion() : "5.0.0";
        String codeValue = params.code() != null ? params.code().getValue() : "";
        
        // 判斷是否有明確指定版本
        // boolean hasExplicitVersion = requestedVersion != null && !requestedVersion.isEmpty();
        boolean hasExplicitVersion = hasExplicitlyProvidedVersion(params);
        
        // CodeSystem 完全不存在 (所有參數類型都適用)
        if (isCodeSystemNotFound) {
            // 1. 根據參數類型添加相應參數
            if (isCodeableConcept && params.originalCodeableConcept() != null) {
                result.addParameter("codeableConcept", params.originalCodeableConcept());
            } else {
                // Coding 或分離參數的情況
                if (params.code() != null) {
                    result.addParameter("code", params.code());
                }
            }
            
            // 2. 建立 OperationOutcome
            OperationOutcome outcome = new OperationOutcome();
            outcome.setMeta(null);
            outcome.setText(null);
            
            // 根據參數來源決定 location 和 expression
            String systemLocation;
            String systemExpression;
            String codeLocation;
            String codeExpression;
            
            if (isCodeableConcept) {
                int codingIndex = extractCodingIndex(params.parameterSource());
                systemLocation = String.format("CodeableConcept.coding[%d].system", codingIndex);
                systemExpression = String.format("CodeableConcept.coding[%d].system", codingIndex);
                codeLocation = String.format("CodeableConcept.coding[%d].code", codingIndex);
                codeExpression = String.format("CodeableConcept.coding[%d].code", codingIndex);
                
                // Issue 1: TX_GENERAL_CC_ERROR_MESSAGE (只有 CodeableConcept 才需要)
                var generalCcIssue = outcome.addIssue();
                generalCcIssue.setSeverity(OperationOutcome.IssueSeverity.ERROR);
                generalCcIssue.setCode(OperationOutcome.IssueType.CODEINVALID);
                
                Extension messageIdExt1 = new Extension();
                messageIdExt1.setUrl("http://hl7.org/fhir/StructureDefinition/operationoutcome-message-id");
                messageIdExt1.setValue(new StringType("TX_GENERAL_CC_ERROR_MESSAGE"));
                generalCcIssue.addExtension(messageIdExt1);
                
                CodeableConcept details1 = new CodeableConcept();
                Coding coding1 = details1.addCoding();
                coding1.setSystem("http://hl7.org/fhir/tools/CodeSystem/tx-issue-type");
                coding1.setCode("not-in-vs");
                details1.setText(String.format(
                    "No valid coding was found for the value set '%s|%s'",
                    valueSetUrl, valueSetVersion));
                generalCcIssue.setDetails(details1);
            } else if (isCoding) {
                systemLocation = "Coding.system";
                systemExpression = "Coding.system";
                codeLocation = "Coding.code";
                codeExpression = "Coding.code";
            } else {
                // 分離參數
                systemLocation = "system";
                systemExpression = "system";
                codeLocation = "code";
                codeExpression = "code";
            }
            
            // Issue 2 (或 Issue 1 for non-CC): 根據是否有明確版本決定使用哪個訊息ID
            var unknownSystemIssue = outcome.addIssue();
            unknownSystemIssue.setSeverity(OperationOutcome.IssueSeverity.ERROR);
            unknownSystemIssue.setCode(OperationOutcome.IssueType.NOTFOUND);
            
            Extension messageIdExt2 = new Extension();
            messageIdExt2.setUrl("http://hl7.org/fhir/StructureDefinition/operationoutcome-message-id");
            
            CodeableConcept details2 = new CodeableConcept();
            Coding coding2 = details2.addCoding();
            coding2.setSystem("http://hl7.org/fhir/tools/CodeSystem/tx-issue-type");
            coding2.setCode("not-found");
            
            if (hasExplicitVersion) {
                // 有明確指定版本：使用 UNKNOWN_CODESYSTEM_VERSION_NONE，並使用完整訊息
                messageIdExt2.setValue(new StringType("UNKNOWN_CODESYSTEM_VERSION_NONE"));
                details2.setText(String.format(
                    "A definition for CodeSystem '%s' version '%s' could not be found, " +
                    "so the code cannot be validated. No versions of this code system are known",
                    systemUrl, requestedVersion));
            } else {
                // 沒有指定版本：使用 UNKNOWN_CODESYSTEM
                messageIdExt2.setValue(new StringType("UNKNOWN_CODESYSTEM"));
                details2.setText(String.format("$external:2:%s$", systemUrl));
            }
            
            unknownSystemIssue.addExtension(messageIdExt2);
            unknownSystemIssue.setDetails(details2);
            unknownSystemIssue.addLocation(systemLocation);
            unknownSystemIssue.addExpression(systemExpression);
            
            // Issue 3 (或 Issue 2 for non-CC): None_of_the_provided_codes_are_in_the_value_set_one
            var notInVsIssue = outcome.addIssue();
            notInVsIssue.setSeverity(OperationOutcome.IssueSeverity.INFORMATION);
            notInVsIssue.setCode(OperationOutcome.IssueType.CODEINVALID);
            
            Extension messageIdExt3 = new Extension();
            messageIdExt3.setUrl("http://hl7.org/fhir/StructureDefinition/operationoutcome-message-id");
            messageIdExt3.setValue(new StringType("None_of_the_provided_codes_are_in_the_value_set_one"));
            notInVsIssue.addExtension(messageIdExt3);
            
            CodeableConcept details3 = new CodeableConcept();
            Coding coding3 = details3.addCoding();
            coding3.setSystem("http://hl7.org/fhir/tools/CodeSystem/tx-issue-type");
            coding3.setCode("this-code-not-in-vs");
            
            if (hasExplicitVersion) {
                // 有版本：使用完整訊息包含版本號
                details3.setText(String.format(
                    "The provided code '%s|%s#%s' was not found in the value set '%s|%s'",
                    systemUrl, requestedVersion, codeValue, valueSetUrl, valueSetVersion));
            } else {
                // 沒有版本：使用佔位符
                details3.setText(String.format("$external:1:%s#%s$", systemUrl, codeValue));
            }
            notInVsIssue.setDetails(details3);
            
            notInVsIssue.addLocation(codeLocation);
            notInVsIssue.addExpression(codeExpression);
            
            // 3. issues 參數
            result.addParameter().setName("issues").setResource(outcome);
            
            // 4. message 參數
            String message;
            if (isCodeableConcept) {
            	if (hasExplicitVersion) {
                    // CodeableConcept 且有版本：使用完整訊息
                    message = String.format(
                        "A definition for CodeSystem '%s' version '%s' could not be found, " +
                        "so the code cannot be validated. No versions of this code system are known; " +
                        "No valid coding was found for the value set '%s|%s'; " +
                        "The provided code '%s|%s#%s' was not found in the value set '%s|%s'",
                        systemUrl, requestedVersion,
                        valueSetUrl, valueSetVersion,
                        systemUrl, requestedVersion, codeValue, valueSetUrl, valueSetVersion);
                } else {
                    // CodeableConcept 但沒有版本：使用佔位符
                    message = String.format("$external:3:%s$", systemUrl);
                }
            } else {
            	if (hasExplicitVersion) {
                    // 非 CodeableConcept 且有版本：使用完整訊息
                    message = String.format(
                        "A definition for CodeSystem '%s' version '%s' could not be found, " +
                        "so the code cannot be validated. No versions of this code system are known; " +
                        "The provided code '%s|%s#%s' was not found in the value set '%s|%s'",
                        systemUrl, requestedVersion,
                        systemUrl, requestedVersion, codeValue, valueSetUrl, valueSetVersion);
                } else {
                    // 非 CodeableConcept 且沒有版本：使用佔位符
                    message = String.format("$external:3:%s$", systemUrl);
                }
            }
            result.addParameter("message", new StringType(message));
            
            // 5. result 參數
            result.addParameter("result", new BooleanType(false));
            
            // 6. system 和 version (非 CodeableConcept 才需要)
            if (!isCodeableConcept) {
                if (params.system() != null) {
                    result.addParameter("system", params.system());
                }
            }
            
            // 7. x-unknown-system 參數 (注意:沒有版本號)
            result.addParameter("x-unknown-system", new CanonicalType(systemUrl));
            
            return result;
        }
                
        // 1. code 參數
        if (params.code() != null) {
            result.addParameter("code", params.code());
        }
        
        // 2. codeableConcept 參數 (如果是 CodeableConcept)
        if (isCodeableConcept && params.originalCodeableConcept() != null) {
            result.addParameter("codeableConcept", params.originalCodeableConcept());
        }
        
        // 3. 取得 ValueSet 使用的版本並查找 display
        String valueSetUsedVersion = null;
        
        if (targetValueSet != null && targetValueSet.hasCompose()) {
            for (ConceptSetComponent include : targetValueSet.getCompose().getInclude()) {
                if (include.hasSystem() && include.getSystem().equals(systemUrl)) {
                    valueSetUsedVersion = include.hasVersion() ? include.getVersion() : null;
                    break;
                }
            }
        }
        
        String vsVersion = valueSetUsedVersion != null ? valueSetUsedVersion : "0.1.0";
        
        // 嘗試從 ValueSet 使用的版本(實際存在的版本)獲取 display
        String displayValue = null;
        
        // 優先使用請求中的 display
        if (params.display() != null && !params.display().isEmpty()) {
            displayValue = params.display().getValue();
        }
        // 如果沒有,從 ValueSet 使用的版本(存在的版本)中查找
        else if (params.system() != null && params.code() != null) {
            try {
                CodeSystem vsCodeSystem = findCodeSystemByUrl(
                    params.system().getValue(), 
                    vsVersion
                );
                if (vsCodeSystem != null) {
                    ConceptDefinitionComponent concept = findConceptRecursive(
                        vsCodeSystem.getConcept(), 
                        params.code().getValue()
                    );
                    if (concept != null && concept.hasDisplay()) {
                        displayValue = concept.getDisplay();
                    }
                }
            } catch (Exception e) {
                // 如果找不到,保持為 null
            }
        }
        
        // 添加 display 參數
        if (displayValue != null) {
            result.addParameter("display", new StringType(displayValue));
        }
        
        // 4. 建立 OperationOutcome
        OperationOutcome outcome = new OperationOutcome();
        outcome.setMeta(null);
        outcome.setText(null);
        
        // 獲取可用版本
        List<String> availableVersions = versionException != null && 
            versionException.getAvailableVersions() != null ? 
            versionException.getAvailableVersions() : new ArrayList<>();
        String validVersionsText = availableVersions.isEmpty() ? 
            "No versions of this code system are known" : 
            String.join(", ", availableVersions);
        
        // 根據參數來源決定 location 和 expression
        String versionLocation;
        String versionExpression;
        String systemLocation;
        String systemExpression;
        
        if (isCodeableConcept) {
            int codingIndex = extractCodingIndex(params.parameterSource());
            versionLocation = String.format("CodeableConcept.coding[%d].version", codingIndex);
            versionExpression = String.format("CodeableConcept.coding[%d].version", codingIndex);
            systemLocation = String.format("CodeableConcept.coding[%d].system", codingIndex);
            systemExpression = String.format("CodeableConcept.coding[%d].system", codingIndex);
        } else if (isCoding) {
            versionLocation = "Coding.version";
            versionExpression = "Coding.version";
            systemLocation = "Coding.system";
            systemExpression = "Coding.system";
        } else {
            versionLocation = "version";
            versionExpression = "version";
            systemLocation = "system";
            systemExpression = "system";
        }
        
        // Issue 1: VALUESET_VALUE_MISMATCH_DEFAULT
        var versionMismatchIssue = outcome.addIssue();
        versionMismatchIssue.setSeverity(OperationOutcome.IssueSeverity.ERROR);
        versionMismatchIssue.setCode(OperationOutcome.IssueType.INVALID);
        
        Extension messageIdExt1 = new Extension();
        messageIdExt1.setUrl("http://hl7.org/fhir/StructureDefinition/operationoutcome-message-id");
        messageIdExt1.setValue(new StringType("VALUESET_VALUE_MISMATCH_DEFAULT"));
        versionMismatchIssue.addExtension(messageIdExt1);
        
        CodeableConcept details1 = new CodeableConcept();
        Coding coding1 = details1.addCoding();
        coding1.setSystem("http://hl7.org/fhir/tools/CodeSystem/tx-issue-type");
        coding1.setCode("vs-invalid");
        details1.setText(String.format(
            "The code system '%s' version '%s' for the versionless include in the ValueSet include " +
            "is different to the one in the value ('%s')",
            systemUrl, vsVersion, requestedVersion));
        versionMismatchIssue.setDetails(details1);
        
        versionMismatchIssue.addLocation(versionLocation);
        versionMismatchIssue.addExpression(versionExpression);
        
        // Issue 2: UNKNOWN_CODESYSTEM_VERSION
        var unknownVersionIssue = outcome.addIssue();
        unknownVersionIssue.setSeverity(OperationOutcome.IssueSeverity.ERROR);
        unknownVersionIssue.setCode(OperationOutcome.IssueType.NOTFOUND);
        
        Extension messageIdExt2 = new Extension();
        messageIdExt2.setUrl("http://hl7.org/fhir/StructureDefinition/operationoutcome-message-id");
        messageIdExt2.setValue(new StringType("UNKNOWN_CODESYSTEM_VERSION"));
        unknownVersionIssue.addExtension(messageIdExt2);
        
        CodeableConcept details2 = new CodeableConcept();
        Coding coding2 = details2.addCoding();
        coding2.setSystem("http://hl7.org/fhir/tools/CodeSystem/tx-issue-type");
        coding2.setCode("not-found");
        details2.setText(String.format(
            "A definition for CodeSystem '%s' version '%s' could not be found, " +
            "so the code cannot be validated. Valid versions: %s",
            systemUrl, requestedVersion, validVersionsText));
        unknownVersionIssue.setDetails(details2);
        
        unknownVersionIssue.addLocation(systemLocation);
        unknownVersionIssue.addExpression(systemExpression);
        
        // 5. issues 參數
        result.addParameter().setName("issues").setResource(outcome);
        
        // 6. message 參數
        String message = String.format(
            "A definition for CodeSystem '%s' version '%s' could not be found, " +
            "so the code cannot be validated. Valid versions: %s; " +
            "The code system '%s' version '%s' for the versionless include in the ValueSet include " +
            "is different to the one in the value ('%s')",
            systemUrl, requestedVersion, validVersionsText,
            systemUrl, vsVersion, requestedVersion);
        result.addParameter("message", new StringType(message));
        
        // 7. result 參數
        result.addParameter("result", new BooleanType(false));
        
        // 8. system 參數 - 明確使用 UriType
        if (params.system() != null && !params.system().isEmpty()) {
            result.addParameter("system", new UriType(params.system().getValue()));
        }
        
        // 9. version 參數 - 使用 ValueSet 中的版本
        result.addParameter("version", new StringType(vsVersion));
        
        // 10. x-caused-by-unknown-system 參數
        String canonicalUrl = systemUrl + "|" + requestedVersion;
        result.addParameter("x-caused-by-unknown-system", new CanonicalType(canonicalUrl));
        
        return result;
    }
    
    private boolean hasExplicitlyProvidedVersion(ValidationParams params) {
        // 檢查 coding 中是否有明確的 version
        if (params.originalCoding() != null && 
            params.originalCoding().hasVersion() && 
            !params.originalCoding().getVersion().isEmpty()) {
            return true;
        }
        
        // 除錯：檢查 originalCoding
        if (params.originalCoding() != null) {
            System.out.println("DEBUG: originalCoding exists");
            System.out.println("DEBUG: originalCoding.hasVersion() = " + params.originalCoding().hasVersion());
            if (params.originalCoding().hasVersion()) {
                System.out.println("DEBUG: originalCoding.getVersion() = " + params.originalCoding().getVersion());
                System.out.println("DEBUG: originalCoding.getVersion().isEmpty() = " + params.originalCoding().getVersion().isEmpty());
            }
        } else {
            System.out.println("DEBUG: originalCoding is null");
        }
        
        // 除錯：檢查 originalCodeableConcept
        if (params.originalCodeableConcept() != null) {
            System.out.println("DEBUG: originalCodeableConcept exists");
            System.out.println("DEBUG: originalCodeableConcept.getCoding().size() = " + params.originalCodeableConcept().getCoding().size());
            for (int i = 0; i < params.originalCodeableConcept().getCoding().size(); i++) {
                Coding c = params.originalCodeableConcept().getCoding().get(i);
                System.out.println("DEBUG: coding[" + i + "].hasVersion() = " + c.hasVersion());
                if (c.hasVersion()) {
                    System.out.println("DEBUG: coding[" + i + "].getVersion() = " + c.getVersion());
                }
            }
        } else {
            System.out.println("DEBUG: originalCodeableConcept is null");
        }
        
        // 檢查 codeableConcept 中是否有明確的 version
        if (params.originalCodeableConcept() != null) {
            for (Coding c : params.originalCodeableConcept().getCoding()) {
                if (c.hasVersion() && !c.getVersion().isEmpty()) {
                	System.out.println("DEBUG: Returning true from originalCodeableConcept coding");
                    return true;
                }
            }
        }
        
        System.out.println("DEBUG: Returning false");
        return false;
    }
    
    private UriType inferSystemFromValueSet(ValueSet valueSet, CodeType code, StringType display) {
        // 1. 優先從 expansion 推斷
        if (valueSet.hasExpansion() && isExpansionCurrent(valueSet.getExpansion())) {
            UriType inferredSystem = inferSystemFromExpansion(valueSet.getExpansion(), code, display);
            if (inferredSystem != null) {
                return inferredSystem;
            }
        }
        
        // 2. 從 compose 推斷
        if (valueSet.hasCompose()) {
            return inferSystemFromCompose(valueSet.getCompose(), code, display);
        }
        
        return null;
    }

    private UriType inferSystemFromExpansion(ValueSetExpansionComponent expansion, 
                                            CodeType code, StringType display) {
        for (ValueSetExpansionContainsComponent contains : expansion.getContains()) {
            UriType result = findSystemInExpansionContains(contains, code, display);
            if (result != null) {
                return result;
            }
        }
        return null;
    }

    private UriType findSystemInExpansionContains(ValueSetExpansionContainsComponent contains,
                                                 CodeType code, StringType display) {
        // 檢查當前 contains
        if (code.getValue().equals(contains.getCode())) {
            // 如果有 display 要求,也要匹配
            if (display == null || !display.hasValue() || 
                (contains.hasDisplay() && display.getValue().equalsIgnoreCase(contains.getDisplay()))) {
                if (contains.hasSystem()) {
                    return new UriType(contains.getSystem());
                }
            }
        }
        
        // 遞歸檢查子概念
        for (ValueSetExpansionContainsComponent child : contains.getContains()) {
            UriType result = findSystemInExpansionContains(child, code, display);
            if (result != null) {
                return result;
            }
        }
        
        return null;
    }

    private UriType inferSystemFromCompose(ValueSetComposeComponent compose, 
                                          CodeType code, StringType display) {
        // 遍歷所有 include
        for (ConceptSetComponent include : compose.getInclude()) {
            if (include.hasSystem()) {
                // 檢查這個 system 中是否包含該 code
                if (isCodeInConceptSet(include, code, display)) {
                    return new UriType(include.getSystem());
                }
            }
        }
        return null;
    }  

    private boolean isCodeInConceptSet(ConceptSetComponent conceptSet, 
                                      CodeType code, StringType display) {

        if (!conceptSet.getConcept().isEmpty()) {
            for (ConceptReferenceComponent concept : conceptSet.getConcept()) {
                if (code.getValue().equals(concept.getCode())) {
                    if (display == null || !display.hasValue() || 
                        (concept.hasDisplay() && display.getValue().equalsIgnoreCase(concept.getDisplay()))) {
                        return true;
                    }
                }
            }
            return false;
        }
        
        if (conceptSet.hasSystem() && conceptSet.getConcept().isEmpty() && conceptSet.getFilter().isEmpty()) {
            try {
                CodeSystem codeSystem = findCodeSystemByUrl(conceptSet.getSystem(), 
                    conceptSet.hasVersion() ? conceptSet.getVersion() : null);
                ConceptDefinitionComponent concept = findConceptRecursive(
                    codeSystem.getConcept(), code.getValue());
                if (concept != null) {
                    if (display == null || !display.hasValue() || 
                        isDisplayMatching(concept, display.getValue())) {
                        return true;
                    }
                }
            } catch (Exception e) {
                return false;
            }
        }
        
        return false;
    }
    
    private Parameters buildUnableToInferSystemError(CodeType code, 
		            								ValueSet targetValueSet,
		            								StringType display) {
		Parameters result = new Parameters();
		
		// 1. code 參數
		if (code != null) {
			result.addParameter("code", code);
		}
		
		// 2. 建立 OperationOutcome
		OperationOutcome outcome = new OperationOutcome();
		outcome.setMeta(null);
		outcome.setText(null);
		
		String valueSetUrl = targetValueSet != null && targetValueSet.hasUrl() ? 
				targetValueSet.getUrl() : "";
		String valueSetVersion = targetValueSet != null && targetValueSet.hasVersion() ? 
				targetValueSet.getVersion() : "5.0.0";
		
		// 嘗試找出可能的 system (用於錯誤訊息)
		String possibleSystem = findPossibleSystemFromValueSet(targetValueSet);
		
		// Issue 1: Code not in ValueSet
		var notInVsIssue = outcome.addIssue();
	    notInVsIssue.setSeverity(OperationOutcome.IssueSeverity.ERROR);
	    notInVsIssue.setCode(OperationOutcome.IssueType.CODEINVALID);
	    
	    Extension messageIdExt1 = new Extension();
	    messageIdExt1.setUrl(OperationOutcomeMessageId.MESSAGE_ID_EXTENSION_URL);
	    messageIdExt1.setValue(new StringType(OperationOutcomeMessageId.NONE_OF_CODES_IN_VALUE_SET_ONE));
	    notInVsIssue.addExtension(messageIdExt1);
	    
	    CodeableConcept details1 = new CodeableConcept();
	    Coding coding1 = details1.addCoding();
	    coding1.setSystem("http://hl7.org/fhir/tools/CodeSystem/tx-issue-type");
	    coding1.setCode("not-in-vs");
	    details1.setText(String.format("$external:1:%s|%s$", valueSetUrl, valueSetVersion));
	    notInVsIssue.setDetails(details1);
	    
	    notInVsIssue.addLocation("code");
	    notInVsIssue.addExpression("code");
	    
	    var unableToInferIssue = outcome.addIssue();
	    unableToInferIssue.setSeverity(OperationOutcome.IssueSeverity.ERROR);
	    unableToInferIssue.setCode(OperationOutcome.IssueType.NOTFOUND);
	    
	    Extension messageIdExt2 = new Extension();
	    messageIdExt2.setUrl("http://hl7.org/fhir/StructureDefinition/operationoutcome-message-id");
	    messageIdExt2.setValue(new StringType("UNABLE_TO_INFER_CODESYSTEM"));
	    unableToInferIssue.addExtension(messageIdExt2);
	    
	    CodeableConcept details2 = new CodeableConcept();
	    Coding coding2 = details2.addCoding();
	    coding2.setSystem("http://hl7.org/fhir/tools/CodeSystem/tx-issue-type");
	    coding2.setCode("cannot-infer");
	    details2.setText(String.format("$external:2:%s$", possibleSystem != null ? possibleSystem : ""));
	    unableToInferIssue.setDetails(details2);
	    
	    unableToInferIssue.addLocation("code");
	    unableToInferIssue.addExpression("code");
			
		// 3. issues 參數
		result.addParameter().setName("issues").setResource(outcome);
			
		// 4. message 參數
		String message = String.format("$external:3:%s$", possibleSystem != null ? possibleSystem : "");
	    result.addParameter("message", new StringType(message));
			
		// 5. result 參數
		result.addParameter("result", new BooleanType(false));
			
		return result;
	}

	private String findPossibleSystemFromValueSet(ValueSet valueSet) {
		
		if (valueSet == null) {
			return null;
		}
		
		// 從 compose.include 中找第一個 system
		if (valueSet.hasCompose()) {
			for (ConceptSetComponent include : valueSet.getCompose().getInclude()) {
				if (include.hasSystem()) {
					return include.getSystem();
				}
			}
		}
		
		// 從 expansion 中找第一個 system
		if (valueSet.hasExpansion()) {
			for (ValueSetExpansionContainsComponent contains : valueSet.getExpansion().getContains()) {
				if (contains.hasSystem()) {
					return contains.getSystem();
				}
			}
		}
		
		return null;
	}    
	
	private Parameters buildReferencedValueSetNotFoundError(
	        ValidationParams params,
	        ValueSet targetValueSet,
	        List<String> missingValueSets) {
	    
	    Parameters result = new Parameters();
	    
	    // 判斷是否為 codeableConcept 參數
	    boolean isCodeableConcept = params.originalCodeableConcept() != null;
	    
	    // 1. codeableConcept 或 code 參數
	    if (isCodeableConcept) {
	        result.addParameter("codeableConcept", params.originalCodeableConcept());
	    } else {
	        // 只有在沒有 codeableConcept 時才添加 code 參數
	        if (params.code() != null) {
	            result.addParameter("code", params.code());
	        }
	    }
	        
	    // 2. display 參數 - 只在沒有 codeableConcept 時才添加
	    if (!isCodeableConcept) {
	        String displayValue = null;
	        CodeSystem codeSystem = null;
	        
	        // 嘗試獲取 CodeSystem 和 display
	        if (params.system() != null && params.code() != null) {
	            try {
	                codeSystem = findCodeSystemByUrl(params.system().getValue(), null);
	                if (codeSystem != null) {
	                    ConceptDefinitionComponent concept = findConceptRecursive(
	                        codeSystem.getConcept(), params.code().getValue());
	                    if (concept != null) {
	                        // 優先使用請求中的 display，如果沒有則使用 concept 的 display
	                        if (params.display() != null && !params.display().isEmpty()) {
	                            displayValue = params.display().getValue();
	                        } else if (concept.hasDisplay()) {
	                            displayValue = concept.getDisplay();
	                        }
	                    }
	                }
	            } catch (Exception e) {
	                // 如果找不到 CodeSystem，使用請求中的 display（如果有）
	                if (params.display() != null && !params.display().isEmpty()) {
	                    displayValue = params.display().getValue();
	                }
	            }
	        } else if (params.display() != null && !params.display().isEmpty()) {
	            // 如果沒有 system，但有 display，直接使用
	            displayValue = params.display().getValue();
	        }
	        
	        if (displayValue != null) {
	            result.addParameter("display", new StringType(displayValue));
	        }
	    }
	    
	    // 3. 建立 OperationOutcome
	    OperationOutcome outcome = new OperationOutcome();
	    outcome.setMeta(null);
	    outcome.setText(null);
	    
	    String valueSetUrl = targetValueSet != null && targetValueSet.hasUrl() ? 
	        targetValueSet.getUrl() : "";
	    String valueSetVersion = targetValueSet != null && targetValueSet.hasVersion() ? 
	        targetValueSet.getVersion() : "5.0.0";
	    
	    // 取得第一個缺失的 ValueSet（用於錯誤訊息）
	    String missingValueSetUrl = !missingValueSets.isEmpty() ? missingValueSets.get(0) : "";
	    
	    // Issue 1: Unable_to_resolve_value_Set_
	    var unableToResolveIssue = outcome.addIssue();
	    unableToResolveIssue.setSeverity(OperationOutcome.IssueSeverity.ERROR);
	    unableToResolveIssue.setCode(OperationOutcome.IssueType.NOTFOUND);
	    
	    Extension messageIdExt1 = new Extension();
	    messageIdExt1.setUrl("http://hl7.org/fhir/StructureDefinition/operationoutcome-message-id");
	    messageIdExt1.setValue(new StringType("Unable_to_resolve_value_Set_"));
	    unableToResolveIssue.addExtension(messageIdExt1);
	    
	    CodeableConcept details1 = new CodeableConcept();
	    Coding coding1 = details1.addCoding();
	    coding1.setSystem("http://hl7.org/fhir/tools/CodeSystem/tx-issue-type");
	    coding1.setCode("not-found");
	    details1.setText(String.format(
	        "A definition for the value Set '%s' could not be found",
	        missingValueSetUrl));
	    unableToResolveIssue.setDetails(details1);
	    
	    // Issue 2: UNABLE_TO_CHECK_IF_THE_PROVIDED_CODES_ARE_IN_THE_VALUE_SET_VS
	    var unableToCheckIssue = outcome.addIssue();
	    unableToCheckIssue.setSeverity(OperationOutcome.IssueSeverity.WARNING);
	    unableToCheckIssue.setCode(OperationOutcome.IssueType.NOTFOUND);
	    
	    Extension messageIdExt2 = new Extension();
	    messageIdExt2.setUrl("http://hl7.org/fhir/StructureDefinition/operationoutcome-message-id");
	    messageIdExt2.setValue(new StringType("UNABLE_TO_CHECK_IF_THE_PROVIDED_CODES_ARE_IN_THE_VALUE_SET_VS"));
	    unableToCheckIssue.addExtension(messageIdExt2);
	    
	    CodeableConcept details2 = new CodeableConcept();
	    Coding coding2 = details2.addCoding();
	    coding2.setSystem("http://hl7.org/fhir/tools/CodeSystem/tx-issue-type");
	    coding2.setCode("vs-invalid");
	    details2.setText(String.format(
	        "Unable to check whether the code is in the value set '%s|%s' because the value set %s was not found",
	        valueSetUrl, valueSetVersion, missingValueSetUrl));
	    unableToCheckIssue.setDetails(details2);
	    
	    // 4. issues 參數
	    result.addParameter().setName("issues").setResource(outcome);
	    
	    // 5. message 參數
	    String message = buildMessageFromOperationOutcome(outcome);
	    result.addParameter("message", new StringType(message));
	    
	    // 6. result 參數
	    result.addParameter("result", new BooleanType(false));
	    
	    // 7. system 和 version 參數 - 只在沒有 codeableConcept 時才添加
	    if (!isCodeableConcept) {
	        if (params.system() != null) {
	            result.addParameter("system", params.system());
	        }
	        
	        // version 參數 - 從多個來源獲取
	        String versionValue = null;
	        
	        // 1. 來自 params 的 coding.version
	        if (params.originalCoding() != null && params.originalCoding().hasVersion()) {
	            versionValue = params.originalCoding().getVersion();
	        }
	        // 2. 嘗試從 CodeSystem 獲取
	        else if (params.system() != null) {
	            try {
	                CodeSystem codeSystem = findCodeSystemByUrl(params.system().getValue(), null);
	                if (codeSystem != null && codeSystem.hasVersion()) {
	                    versionValue = codeSystem.getVersion();
	                }
	            } catch (Exception e) {

	            }
	        }
	        
	        if (versionValue != null) {
	            result.addParameter("version", new StringType(versionValue));
	        }
	    }
	    
	    return result;
	}
	
	// 建立 Invalid Display 錯誤回應的方法
	private Parameters buildInvalidDisplayError(ValidationParams params, 
	                                           String correctDisplay,
	                                           CodeSystem codeSystem,
	                                           ValueSet targetValueSet,
	                                           StringType effectiveSystemVersion,
	                                           CodeType displayLanguage) {
	    Parameters result = new Parameters();
	    
	    // 判斷是否為 codeableConcept 參數
	    boolean isCodeableConcept = params.originalCodeableConcept() != null || 
	                                "codeableConcept".equals(params.parameterSource()) ||
	                                params.parameterSource().startsWith("codeableConcept.coding");
	    
	    // 判斷是否為 coding 參數
	    boolean isCoding = "coding".equals(params.parameterSource());
	    
	    // 判斷是否為單純的 code 參數 (沒有 coding 或 codeableConcept)
	    boolean isCodeParam = "code".equals(params.parameterSource());
	    	    
	    // 1. code 參數
	    if (params.code() != null) {
	        result.addParameter("code", params.code());
	    }
	    
	    // 2. codeableConcept 參數 - 如果是 codeableConcept 來源，添加此參數
	    if (isCodeableConcept && params.originalCodeableConcept() != null) {
	        result.addParameter("codeableConcept", params.originalCodeableConcept());
	    }
	    
	    // 3. display 參數 - 使用正確的 display
	    if (correctDisplay != null) {
	        result.addParameter("display", new StringType(correctDisplay));
	    }
	    
	    // 4. 建立 OperationOutcome
	    OperationOutcome outcome = new OperationOutcome();
	    outcome.setMeta(null);
	    outcome.setText(null);
	    
	    String incorrectDisplay = params.display() != null ? params.display().getValue() : "";
		   
	    String detailsText;
	    String messageText;
	    
	    // 判斷是否為多語言格式（包含逗號或連字號）
	    boolean isMultiLanguage = false;
	    String displayLanguageValue = "--";
	    
	    if (displayLanguage != null && !displayLanguage.isEmpty()) {
	        String languageValue = displayLanguage.getValue();
	        isMultiLanguage = languageValue.contains(",") || languageValue.contains("-");
	        displayLanguageValue = languageValue;
	    }
	    
	    // 獲取 CodeSystem 的預設語言
	    String codeSystemLanguage = null;
	    if (codeSystem != null && codeSystem.hasLanguage()) {
	        codeSystemLanguage = codeSystem.getLanguage();
	    }
	    
	    if (isMultiLanguage) {
	    	// 使用 $external 佔位符
	        detailsText = String.format("$external:1:%s$", incorrectDisplay);
	        // 對於 coding 和 codeableConcept，message 都使用 $external:2
	        // 只有 code 參數才使用 $external:1
	        if (isCodeParam) {
	            messageText = String.format("$external:1:%s$", incorrectDisplay);
	        } else {
	            messageText = String.format("$external:2:%s$", incorrectDisplay);
	        }
	    } else {
	        // 完整說明
	        String systemUrl = params.system() != null ? params.system().getValue() : "";
	        String codeValue = params.code() != null ? params.code().getValue() : "";
	        
	        // 決定是否顯示語言標記
	        boolean shouldShowLanguageTag = false;
	        String languageToShow = displayLanguageValue;
	        
	        // 如果沒有提供 displayLanguage，使用 CodeSystem 的預設語言
	        if (displayLanguage == null || displayLanguage.isEmpty()) {
	            if (codeSystemLanguage != null) {
	                shouldShowLanguageTag = true;
	                languageToShow = codeSystemLanguage;
	            }
	        } else {
	            // 有提供 displayLanguage 的情況（原有邏輯）
	            if (codeSystem != null && params.code() != null) {
	                ConceptDefinitionComponent concept = findConceptRecursive(
	                    codeSystem.getConcept(), 
	                    params.code().getValue()
	                );
	                if (concept != null) {
	                    shouldShowLanguageTag = hasDesignationForLanguage(concept, displayLanguageValue);
	                }
	                
	                if (!shouldShowLanguageTag && codeSystem.hasLanguage()) {
	                    String csLang = codeSystem.getLanguage();
	                    shouldShowLanguageTag = csLang.equalsIgnoreCase(displayLanguageValue) || 
	                                            csLang.startsWith(displayLanguageValue + "-");
	                }
	            }
	        }
	        
	        // 組合錯誤訊息
	        if (shouldShowLanguageTag) {
	            // 有語言標記 - 格式: Valid display is 'Code1' (en)
	            detailsText = String.format(
	                "Wrong Display Name '%s' for %s#%s. Valid display is '%s' (%s) (for the language(s) '%s')",
	                incorrectDisplay, systemUrl, codeValue, correctDisplay, 
	                languageToShow, displayLanguageValue);
	        } else {
	            // 沒有語言標記
	            detailsText = String.format(
	                "Wrong Display Name '%s' for %s#%s. Valid display is '%s' (for the language(s) '%s')",
	                incorrectDisplay, systemUrl, codeValue, correctDisplay, displayLanguageValue);
	        }
	        
	        messageText = detailsText;
	    }
	    
	    // 檢測是否為空白字元差異
	    boolean isWhitespaceDifference = false;
	    if (incorrectDisplay != null && correctDisplay != null) {
	        // 移除所有連續空白字元後比較
	        String normalizedIncorrect = incorrectDisplay.replaceAll("\\s+", " ").trim();
	        String normalizedCorrect = correctDisplay.replaceAll("\\s+", " ").trim();
	        
	        // 如果標準化後相同，表示只是空白字元差異
	        isWhitespaceDifference = normalizedIncorrect.equalsIgnoreCase(normalizedCorrect) &&
	                                !incorrectDisplay.equals(correctDisplay);
	    }
	    
	    // 判斷參數來源以決定 location 和 expression
	    String displayLocation;
	    String displayExpression;
	    
	    if (isCodeableConcept) {
	        // codeableConcept 參數：使用 CodeableConcept.coding[index].display
	        int codingIndex = extractCodingIndex(params.parameterSource());
	        displayLocation = String.format("CodeableConcept.coding[%d].display", codingIndex);
	        displayExpression = String.format("CodeableConcept.coding[%d].display", codingIndex);
	    } else if (isCoding) {
	        // coding 參數：使用 Coding.display
	        displayLocation = "Coding.display";
	        displayExpression = "Coding.display";
	    } else {
	        displayLocation = "display";
	        displayExpression = "display";
	    }
	    
	    // Issue: Display_Name_for__should_be_one_of__instead_of
	    var invalidDisplayIssue = outcome.addIssue();
	    invalidDisplayIssue.setSeverity(OperationOutcome.IssueSeverity.ERROR);
	    invalidDisplayIssue.setCode(OperationOutcome.IssueType.INVALID);
	    
	    Extension messageIdExt = new Extension();
	    messageIdExt.setUrl(OperationOutcomeMessageId.MESSAGE_ID_EXTENSION_URL);
	    // 根據是否為空白字元差異選擇不同的 message ID
	    String messageId = isWhitespaceDifference ? 
	            "Display_Name_WS_for__should_be_one_of__instead_of" :
	            OperationOutcomeMessageId.DISPLAY_NAME_SHOULD_BE_ONE_OF;
	    messageIdExt.setValue(new StringType(messageId));
	    invalidDisplayIssue.addExtension(messageIdExt);
	    
	    CodeableConcept details = new CodeableConcept();
	    Coding coding = details.addCoding();
	    coding.setSystem(OperationOutcomeMessageId.TX_ISSUE_TYPE_SYSTEM);
	    coding.setCode("invalid-display");
	    details.setText(detailsText);
	    invalidDisplayIssue.setDetails(details);
	    
	    invalidDisplayIssue.addLocation(displayLocation);
	    invalidDisplayIssue.addExpression(displayExpression);
	    
	    // 5. issues 參數
	    result.addParameter().setName("issues").setResource(outcome);
	    
	    // 6. message 參數	    
	    result.addParameter("message", new StringType(messageText));
	    
	    // 7. result 參數
	    result.addParameter("result", new BooleanType(false));
	    
	    // 8. system 參數
	    if (params.system() != null && !params.system().isEmpty()) {
	        result.addParameter("system", params.system());
	    } else if (codeSystem != null && codeSystem.hasUrl()) {
	        result.addParameter("system", new UriType(codeSystem.getUrl()));
	    }
	    
	    // 9. version 參數
	    String versionToReturn = null;
	    if (effectiveSystemVersion != null && !effectiveSystemVersion.isEmpty()) {
	        versionToReturn = effectiveSystemVersion.getValue();
	    } else if (codeSystem != null && codeSystem.hasVersion()) {
	        versionToReturn = codeSystem.getVersion();
	    }
	    
	    if (versionToReturn != null) {
	        result.addParameter("version", new StringType(versionToReturn));
	    }
	    
	    return result;
	}
	
	private Parameters buildCodeableConceptPartialSuccessError(
	        ValidationParams successfulParams,
	        String correctDisplay,
	        CodeSystem matchedCodeSystem,
	        ValueSet targetValueSet,
	        StringType effectiveSystemVersion,
	        List<ValidationContext> allErrors) {
	    
	    Parameters result = new Parameters();
	    
	    // 1. code 參數 - 回傳第一個有效的 code
	    if (successfulParams != null && successfulParams.code() != null) {
	        result.addParameter("code", successfulParams.code());
	    }
	    
	    // 2. codeableConcept 參數
	    if (successfulParams != null && successfulParams.originalCodeableConcept() != null) {
	        result.addParameter("codeableConcept", successfulParams.originalCodeableConcept());
	    }
	    
	    // 3. display 參數 - 回傳正確的 display
	    if (correctDisplay != null) {
	        result.addParameter("display", new StringType(correctDisplay));
	    }
	    
	    // 4. 建立包含所有錯誤的 OperationOutcome
	    OperationOutcome outcome = new OperationOutcome();
	    outcome.setMeta(null);
	    outcome.setText(null);
	    
	    String valueSetUrl = targetValueSet != null && targetValueSet.hasUrl() ? 
	        targetValueSet.getUrl() : "";
	    String valueSetVersion = targetValueSet != null && targetValueSet.hasVersion() ? 
	        targetValueSet.getVersion() : "5.0.0";
	    
	    allErrors.sort((e1, e2) -> {
	        int index1 = extractCodingIndex(e1.parameterSource());
	        int index2 = extractCodingIndex(e2.parameterSource());
	        return Integer.compare(index1, index2);
	    });
	    
	    List<ValidationContext> invalidCodeErrors = new ArrayList<>();
	    List<ValidationContext> invalidDisplayErrors = new ArrayList<>();
	    
	    for (ValidationContext errorContext : allErrors) {
	        if (errorContext.errorType() == ValidationErrorType.INVALID_CODE) {
	            invalidCodeErrors.add(errorContext);
	        } else if (errorContext.errorType() == ValidationErrorType.INVALID_DISPLAY) {
	            invalidDisplayErrors.add(errorContext);
	        }
	    }
	    
	    for (ValidationContext errorContext : invalidCodeErrors) {
	        int codingIndex = extractCodingIndex(errorContext.parameterSource());
	        
	        var unknownCodeIssue = outcome.addIssue();
	        unknownCodeIssue.setSeverity(OperationOutcome.IssueSeverity.ERROR);
	        unknownCodeIssue.setCode(OperationOutcome.IssueType.CODEINVALID);
	        
	        Extension messageIdExt = new Extension();
	        messageIdExt.setUrl(OperationOutcomeMessageId.MESSAGE_ID_EXTENSION_URL);
	        messageIdExt.setValue(new StringType(OperationOutcomeMessageId.UNKNOWN_CODE_IN_VERSION));
	        unknownCodeIssue.addExtension(messageIdExt);
	        
	        String codeValue = errorContext.code() != null ? errorContext.code().getValue() : "";
	        
	        CodeableConcept details = new CodeableConcept();
	        Coding coding = details.addCoding();
	        coding.setSystem("http://hl7.org/fhir/tools/CodeSystem/tx-issue-type");
	        coding.setCode("invalid-code");
	        details.setText(String.format("$external:2:%s$", codeValue));
	        unknownCodeIssue.setDetails(details);
	        
	        unknownCodeIssue.addLocation(String.format("CodeableConcept.coding[%d].code", codingIndex));
	        unknownCodeIssue.addExpression(String.format("CodeableConcept.coding[%d].code", codingIndex));
	    }
	    
	    for (ValidationContext errorContext : invalidDisplayErrors) {
	        int codingIndex = extractCodingIndex(errorContext.parameterSource());
	        
	        var displayIssue = outcome.addIssue();
	        displayIssue.setSeverity(OperationOutcome.IssueSeverity.ERROR);
	        displayIssue.setCode(OperationOutcome.IssueType.INVALID);
	        
	        Extension messageIdExt = new Extension();
	        messageIdExt.setUrl(OperationOutcomeMessageId.MESSAGE_ID_EXTENSION_URL);
	        messageIdExt.setValue(new StringType(OperationOutcomeMessageId.DISPLAY_NAME_SHOULD_BE_ONE_OF));
	        displayIssue.addExtension(messageIdExt);
	        
	        String incorrectDisplay = errorContext.display() != null ? 
	                errorContext.display().getValue() : "";
	        
	        CodeableConcept details = new CodeableConcept();
	        Coding coding = details.addCoding();
	        coding.setSystem("http://hl7.org/fhir/tools/CodeSystem/tx-issue-type");
	        coding.setCode("invalid-display");
	        details.setText(String.format("$external:3:%s$", incorrectDisplay));
	        displayIssue.setDetails(details);
	        
	        displayIssue.addLocation(String.format("CodeableConcept.coding[%d].display", codingIndex));
	        displayIssue.addExpression(String.format("CodeableConcept.coding[%d].display", codingIndex));
	    }
	    
	    var notInVsIssue = outcome.addIssue();
	    notInVsIssue.setSeverity(OperationOutcome.IssueSeverity.INFORMATION);
	    notInVsIssue.setCode(OperationOutcome.IssueType.CODEINVALID);
	    
	    Extension messageIdExt = new Extension();
	    messageIdExt.setUrl(OperationOutcomeMessageId.MESSAGE_ID_EXTENSION_URL);
	    messageIdExt.setValue(new StringType(OperationOutcomeMessageId.NONE_OF_CODES_IN_VALUE_SET_ONE));
	    notInVsIssue.addExtension(messageIdExt);
	    
	    ValidationContext lastInvalidCodeError = invalidCodeErrors.isEmpty() ? null : 
	        invalidCodeErrors.get(invalidCodeErrors.size() - 1);
	    
	    String detailsText;
	    int targetCodingIndex;
	    
	    if (lastInvalidCodeError != null) {
	        String invalidCode = lastInvalidCodeError.code() != null ? 
	            lastInvalidCodeError.code().getValue() : "";
	        detailsText = String.format("$external:1:%s$", invalidCode);
	        targetCodingIndex = extractCodingIndex(lastInvalidCodeError.parameterSource());
	    } else {
	        detailsText = String.format("$external:1:%s$", valueSetUrl);
	        targetCodingIndex = allErrors.isEmpty() ? 0 : 
	            extractCodingIndex(allErrors.get(allErrors.size() - 1).parameterSource());
	    }
	    
	    CodeableConcept details = new CodeableConcept();
	    Coding coding = details.addCoding();
	    coding.setSystem("http://hl7.org/fhir/tools/CodeSystem/tx-issue-type");
	    coding.setCode("this-code-not-in-vs");
	    details.setText(detailsText);
	    notInVsIssue.setDetails(details);
	    
	    notInVsIssue.addLocation(String.format("CodeableConcept.coding[%d].code", targetCodingIndex));
	    notInVsIssue.addExpression(String.format("CodeableConcept.coding[%d].code", targetCodingIndex));
	    
	    // 5. issues 參數
	    result.addParameter().setName("issues").setResource(outcome);
	    
	    // 6. message 參數
	    result.addParameter("message", new StringType("$external:4$"));
	    
	    // 7. result 參數 - false
	    result.addParameter("result", new BooleanType(false));
	    
	    // 8. system 參數
	    if (successfulParams != null && successfulParams.system() != null) {
	        result.addParameter("system", successfulParams.system());
	    } else if (matchedCodeSystem != null && matchedCodeSystem.hasUrl()) {
	        result.addParameter("system", new UriType(matchedCodeSystem.getUrl()));
	    }
	    
	    // 9. version 參數
	    String versionToReturn = null;
	    if (effectiveSystemVersion != null && !effectiveSystemVersion.isEmpty()) {
	        versionToReturn = effectiveSystemVersion.getValue();
	    } else if (matchedCodeSystem != null && matchedCodeSystem.hasVersion()) {
	        versionToReturn = matchedCodeSystem.getVersion();
	    }
	    
	    if (versionToReturn != null) {
	        result.addParameter("version", new StringType(versionToReturn));
	    }
	    
	    return result;
	}

	private Parameters buildMembershipOnlySuccessResponse(ValidationParams successfulParams,
	                                                       ValueSet targetValueSet) {
	    Parameters result = new Parameters();
	    
	    // 1. codeableConcept 參數
	    if (successfulParams.originalCodeableConcept() != null) {
	        result.addParameter("codeableConcept", successfulParams.originalCodeableConcept());
	    }
	    
	    // 2. result 參數
	    result.addParameter("result", new BooleanType(true));
	    
	    return result;
	}
	
	private Parameters buildVersionMismatchError(ValidationParams params, ValueSet targetValueSet,
											     StringType requestedVersion,
											     CodeSystemVersionNotFoundException versionException) {
											    
	    Parameters result = new Parameters();
	    
	    // 判斷參數來源
	    boolean isCoding = "coding".equals(params.parameterSource());
	    boolean isCodeableConcept = params.originalCodeableConcept() != null || 
	                                "codeableConcept".equals(params.parameterSource()) ||
	                                params.parameterSource().startsWith("codeableConcept.coding");
	    boolean isCodeParam = "code".equals(params.parameterSource());
	    
	    // 1. code 參數
	    if (params.code() != null) {
	        result.addParameter("code", params.code());
	    }
	    
	    // 2. codeableConcept 參數 - 範例三特有
	    if (isCodeableConcept && params.originalCodeableConcept() != null) {
	        result.addParameter("codeableConcept", params.originalCodeableConcept());
	    }
	    
	    // 3. 取得 ValueSet 使用的版本並查找 display
	    String valueSetUsedVersion = null;
	    String systemUrl = params.system() != null ? params.system().getValue() : "";
	    
	    if (targetValueSet != null && targetValueSet.hasCompose()) {
	        for (ConceptSetComponent include : targetValueSet.getCompose().getInclude()) {
	            if (include.hasSystem() && include.getSystem().equals(systemUrl)) {
	                valueSetUsedVersion = include.hasVersion() ? include.getVersion() : null;
	                break;
	            }
	        }
	    }
	    
	    String vsVersion = valueSetUsedVersion != null ? valueSetUsedVersion : "0.1.0";
	    
	    // 嘗試從 ValueSet 使用的版本(實際存在的版本)獲取 display
	    String displayValue = null;
	    
	    // 優先使用請求中的 display
	    if (params.display() != null && !params.display().isEmpty()) {
	        displayValue = params.display().getValue();
	    }
	    
	    // 如果沒有,從 ValueSet 使用的版本(存在的版本)中查找
	    else if (params.system() != null && params.code() != null) {
	        try {
	            CodeSystem vsCodeSystem = findCodeSystemByUrl(
	                params.system().getValue(), 
	                vsVersion
	            );
	            if (vsCodeSystem != null) {
	                ConceptDefinitionComponent concept = findConceptRecursive(
	                    vsCodeSystem.getConcept(), 
	                    params.code().getValue()
	                );
	                if (concept != null && concept.hasDisplay()) {
	                    displayValue = concept.getDisplay();
	                }
	            }
	        } catch (Exception e) {

	        }
	    }
	    
	    // 添加 display 參數
	    if (displayValue != null) {
	        result.addParameter("display", new StringType(displayValue));
	    }
	    
	    // 4. 建立 OperationOutcome
	    OperationOutcome outcome = new OperationOutcome();
	    outcome.setMeta(null);
	    outcome.setText(null);
	    
	    String requestedVersionStr = requestedVersion != null ? requestedVersion.getValue() : "";
	    	    
	    // 獲取可用版本
	    List<String> availableVersions = versionException != null ? 
	        versionException.getAvailableVersions() : new ArrayList<>();
	    String validVersionsText = String.join(", ", availableVersions);
	    
	    // 根據參數來源決定 location 和 expression
	    String versionLocation;
	    String versionExpression;
	    String systemLocation;
	    String systemExpression;
	    
	    if (isCodeableConcept) {
	        // CodeableConcept
	        int codingIndex = extractCodingIndex(params.parameterSource());
	        versionLocation = String.format("CodeableConcept.coding[%d].version", codingIndex);
	        versionExpression = String.format("CodeableConcept.coding[%d].version", codingIndex);
	        systemLocation = String.format("CodeableConcept.coding[%d].system", codingIndex);
	        systemExpression = String.format("CodeableConcept.coding[%d].system", codingIndex);
	    } else if (isCoding) {
	        // Coding
	        versionLocation = "Coding.version";
	        versionExpression = "Coding.version";
	        systemLocation = "Coding.system";
	        systemExpression = "Coding.system";
	    } else {
	        // 分離參數
	        versionLocation = "version";
	        versionExpression = "version";
	        systemLocation = "system";
	        systemExpression = "system";
	    }
	    
	    // Issue 1: VALUESET_VALUE_MISMATCH_DEFAULT
	    var versionMismatchIssue = outcome.addIssue();
	    versionMismatchIssue.setSeverity(OperationOutcome.IssueSeverity.ERROR);
	    versionMismatchIssue.setCode(OperationOutcome.IssueType.INVALID);
	    
	    Extension messageIdExt1 = new Extension();
	    messageIdExt1.setUrl("http://hl7.org/fhir/StructureDefinition/operationoutcome-message-id");
	    messageIdExt1.setValue(new StringType("VALUESET_VALUE_MISMATCH_DEFAULT"));
	    versionMismatchIssue.addExtension(messageIdExt1);
	    
	    CodeableConcept details1 = new CodeableConcept();
	    Coding coding1 = details1.addCoding();
	    coding1.setSystem("http://hl7.org/fhir/tools/CodeSystem/tx-issue-type");
	    coding1.setCode("vs-invalid");
	    details1.setText(String.format(
	        "The code system '%s' version '%s' for the versionless include in the ValueSet include " +
	        "is different to the one in the value ('%s')",
	        systemUrl, vsVersion, requestedVersionStr));
	    versionMismatchIssue.setDetails(details1);
	    
	    versionMismatchIssue.addLocation(versionLocation);
	    versionMismatchIssue.addExpression(versionExpression);
	    
	    // Issue 2: UNKNOWN_CODESYSTEM_VERSION
	    var unknownVersionIssue = outcome.addIssue();
	    unknownVersionIssue.setSeverity(OperationOutcome.IssueSeverity.ERROR);
	    unknownVersionIssue.setCode(OperationOutcome.IssueType.NOTFOUND);
	    
	    Extension messageIdExt2 = new Extension();
	    messageIdExt2.setUrl("http://hl7.org/fhir/StructureDefinition/operationoutcome-message-id");
	    messageIdExt2.setValue(new StringType("UNKNOWN_CODESYSTEM_VERSION"));
	    unknownVersionIssue.addExtension(messageIdExt2);
	    
	    CodeableConcept details2 = new CodeableConcept();
	    Coding coding2 = details2.addCoding();
	    coding2.setSystem("http://hl7.org/fhir/tools/CodeSystem/tx-issue-type");
	    coding2.setCode("not-found");
	    details2.setText(String.format(
	        "A definition for CodeSystem '%s' version '%s' could not be found, " +
	        "so the code cannot be validated. Valid versions: %s",
	        systemUrl, requestedVersionStr, validVersionsText));
	    unknownVersionIssue.setDetails(details2);
	    
	    unknownVersionIssue.addLocation(systemLocation);
	    unknownVersionIssue.addExpression(systemExpression);
	    
	    // 5. issues 參數
	    result.addParameter().setName("issues").setResource(outcome);
	    
	    // 6. message 參數
	    String message = String.format(
	            "A definition for CodeSystem '%s' version '%s' could not be found, " +
	            "so the code cannot be validated. Valid versions: %s; " +
	            "The code system '%s' version '%s' for the versionless include in the ValueSet include " +
	            "is different to the one in the value ('%s')",
	            systemUrl, requestedVersionStr, validVersionsText,
	            systemUrl, vsVersion, requestedVersionStr);
	        result.addParameter("message", new StringType(message));
	    
	    // 7. result 參數
	    result.addParameter("result", new BooleanType(false));
	    
	    // 8. system 參數
	    if (params.system() != null && !params.system().isEmpty()) {
	        result.addParameter("system", new UriType(params.system().getValue()));
	    }
	    
	    // 9. version 參數 - 使用 ValueSet 中的版本
	    result.addParameter("version", new StringType(vsVersion));
	    
	    // 10. x-caused-by-unknown-system 參數
	    String canonicalUrl = systemUrl + "|" + requestedVersionStr;
	    result.addParameter("x-caused-by-unknown-system", new CanonicalType(canonicalUrl));
	    
	    return result;
	}
	
	private Parameters buildVersionMismatchWithValueSetError(
	        ValidationParams params,
	        ValueSet targetValueSet,
	        StringType requestedVersion) {
	    
	    Parameters result = new Parameters();
	    
	    // 1. code 參數
	    if (params.code() != null) {
	        result.addParameter("code", params.code());
	    }
	    
	    // 2. 取得 ValueSet 使用的版本
	    String valueSetUsedVersion = null;
	    String systemUrl = params.system() != null ? params.system().getValue() : "";
	    
	    if (targetValueSet != null && targetValueSet.hasCompose()) {
	        for (ConceptSetComponent include : targetValueSet.getCompose().getInclude()) {
	            if (include.hasSystem() && include.getSystem().equals(systemUrl)) {
	                valueSetUsedVersion = include.hasVersion() ? include.getVersion() : null;
	                break;
	            }
	        }
	    }
	    
	    String vsVersion = valueSetUsedVersion != null ? valueSetUsedVersion : "0.1.0";
	    
	    // 3. 嘗試從兩個版本獲取 display
	    String displayValue = null;
	    
	    // 優先嘗試從請求的版本獲取 display
	    if (params.system() != null && params.code() != null && 
	        requestedVersion != null && !requestedVersion.isEmpty()) {
	        try {
	            CodeSystem requestedCodeSystem = findCodeSystemByUrl(
	                params.system().getValue(), 
	                requestedVersion.getValue()
	            );
	            if (requestedCodeSystem != null) {
	                ConceptDefinitionComponent concept = findConceptRecursive(
	                    requestedCodeSystem.getConcept(), 
	                    params.code().getValue()
	                );
	                if (concept != null && concept.hasDisplay()) {
	                    displayValue = concept.getDisplay();
	                }
	            }
	        } catch (Exception e) {
	       
	        }
	    }
	    
	    // 如果沒找到，嘗試從 ValueSet 使用的版本獲取
	    if (displayValue == null && params.system() != null && params.code() != null) {
	        try {
	            CodeSystem vsCodeSystem = findCodeSystemByUrl(
	                params.system().getValue(), 
	                vsVersion
	            );
	            if (vsCodeSystem != null) {
	                ConceptDefinitionComponent concept = findConceptRecursive(
	                    vsCodeSystem.getConcept(), 
	                    params.code().getValue()
	                );
	                if (concept != null && concept.hasDisplay()) {
	                    displayValue = concept.getDisplay();
	                }
	            }
	        } catch (Exception e) {
	         
	        }
	    }
	    
	    // 最後使用請求中的 display
	    if (displayValue == null && params.display() != null && !params.display().isEmpty()) {
	        displayValue = params.display().getValue();
	    }
	    
	    if (displayValue != null) {
	        result.addParameter("display", new StringType(displayValue));
	    }
	    
	    // 4. 建立 OperationOutcome
	    OperationOutcome outcome = new OperationOutcome();
	    outcome.setMeta(null);
	    outcome.setText(null);
	    
	    String requestedVersionStr = requestedVersion != null ? requestedVersion.getValue() : "";
	    String valueSetUrl = targetValueSet != null && targetValueSet.hasUrl() ? 
	        targetValueSet.getUrl() : "";
	    String valueSetVersion = targetValueSet != null && targetValueSet.hasVersion() ? 
	        targetValueSet.getVersion() : "5.0.0";
	    
	    // Issue 1: VALUESET_VALUE_MISMATCH_DEFAULT
	    var versionMismatchIssue = outcome.addIssue();
	    versionMismatchIssue.setSeverity(OperationOutcome.IssueSeverity.ERROR);
	    versionMismatchIssue.setCode(OperationOutcome.IssueType.INVALID);
	    
	    Extension messageIdExt1 = new Extension();
	    messageIdExt1.setUrl("http://hl7.org/fhir/StructureDefinition/operationoutcome-message-id");
	    messageIdExt1.setValue(new StringType("VALUESET_VALUE_MISMATCH_DEFAULT"));
	    versionMismatchIssue.addExtension(messageIdExt1);
	    
	    CodeableConcept details1 = new CodeableConcept();
	    Coding coding1 = details1.addCoding();
	    coding1.setSystem("http://hl7.org/fhir/tools/CodeSystem/tx-issue-type");
	    coding1.setCode("vs-invalid");
	    details1.setText(String.format(
	        "The code system '%s' version '%s' for the versionless include in the ValueSet include " +
	        "is different to the one in the value ('%s')",
	        systemUrl, vsVersion, requestedVersionStr));
	    versionMismatchIssue.setDetails(details1);
	    
	    versionMismatchIssue.addLocation("Coding.version");
	    versionMismatchIssue.addExpression("Coding.version");
	    
	    // Issue 2: Code not in ValueSet (因為版本不對，所以 code 不在此 ValueSet 中)
	    var notInVsIssue = outcome.addIssue();
	    notInVsIssue.setSeverity(OperationOutcome.IssueSeverity.ERROR);
	    notInVsIssue.setCode(OperationOutcome.IssueType.CODEINVALID);
	    
	    Extension messageIdExt2 = new Extension();
	    messageIdExt2.setUrl("http://hl7.org/fhir/StructureDefinition/operationoutcome-message-id");
	    messageIdExt2.setValue(new StringType("None_of_the_provided_codes_are_in_the_value_set_one"));
	    notInVsIssue.addExtension(messageIdExt2);
	    
	    CodeableConcept details2 = new CodeableConcept();
	    Coding coding2 = details2.addCoding();
	    coding2.setSystem("http://hl7.org/fhir/tools/CodeSystem/tx-issue-type");
	    coding2.setCode("not-in-vs");
	    details2.setText(String.format(
	        "The provided code '%s#%s' was not found in the value set '%s|%s'",
	        systemUrl, params.code().getValue(), valueSetUrl, valueSetVersion));
	    notInVsIssue.setDetails(details2);
	    
	    notInVsIssue.addLocation("Coding.code");
	    notInVsIssue.addExpression("Coding.code");

	    // 5. issues 參數
	    result.addParameter().setName("issues").setResource(outcome);
	    
	    // 6. message 參數
	    String message = String.format(
	        "The code system '%s' version '%s' for the versionless include in the ValueSet include " +
	        "is different to the one in the value ('%s'); " +
	        "The provided code '%s#%s' was not found in the value set '%s|%s'",
	        systemUrl, vsVersion, requestedVersionStr,
	        systemUrl, params.code().getValue(), valueSetUrl, valueSetVersion);
	    result.addParameter("message", new StringType(message));
	    
	    // 7. result 參數
	    result.addParameter("result", new BooleanType(false));
	    
	    // 8. system 參數
	    if (params.system() != null) {
	        result.addParameter("system", params.system());
	    }
	    
	    // 9. version 參數 - 使用 ValueSet 中的版本
	    result.addParameter("version", new StringType(vsVersion));
	    
	    return result;
	}
	
	// 檢查 concept 是否有特定語言的 designation
	private boolean hasDesignationForLanguage(ConceptDefinitionComponent concept, String language) {
	    if (concept == null || language == null) {
	        return false;
	    }
	    
	    for (ConceptDefinitionDesignationComponent designation : concept.getDesignation()) {
	        if (language.equalsIgnoreCase(designation.getLanguage())) {
	            return true;
	        }
	    }
	    
	    return false;
	}

	// 檢查 CodeSystem 是否支援特定語言
	private boolean codeSystemHasLanguageSupport(CodeSystem codeSystem, String languageList) {
	    if (codeSystem == null || languageList == null || languageList.isEmpty()) {
	        return false;
	    }
	    
	    String[] languages = languageList.split(",");
	    
	    for (String lang : languages) {
	        String trimmedLang = lang.trim();
	        if (checkConceptsForLanguage(codeSystem.getConcept(), trimmedLang)) {
	            return true;
	        }
	    }
	    
	    return false;
	}

	private boolean checkConceptsForLanguage(List<ConceptDefinitionComponent> concepts, String language) {
	    for (ConceptDefinitionComponent concept : concepts) {
	        // 檢查當前 concept
	        if (hasDesignationForLanguage(concept, language)) {
	            return true;
	        }
	        
	        // 遞歸檢查子 concept
	        if (checkConceptsForLanguage(concept.getConcept(), language)) {
	            return true;
	        }
	    }
	    
	    return false;
	}
	
	private Parameters buildSuccessResponseWithLanguageWarning(ValidationParams successfulParams, String matchedDisplay,
	        												   CodeSystem matchedCodeSystem, ValueSet targetValueSet,
	        												   StringType effectiveSystemVersion, CodeType displayLanguage,
	        												   ValidationErrorType warningType) {
	    
	    Parameters result = new Parameters();
	    
	    // 1. code 參數
	    if (successfulParams != null && successfulParams.code() != null) {
	        result.addParameter("code", successfulParams.code());
	    }
	    
	    // 2. display 參數 - 使用預設 display
	    if (matchedDisplay != null) {
	        result.addParameter("display", new StringType(matchedDisplay));
	    }
	    
	    // 3. 建立 OperationOutcome
	    OperationOutcome outcome = new OperationOutcome();
	    outcome.setMeta(null);
	    outcome.setText(null);
	    
	    var languageWarningIssue = outcome.addIssue();
	    languageWarningIssue.setSeverity(OperationOutcome.IssueSeverity.INFORMATION);
	    languageWarningIssue.setCode(OperationOutcome.IssueType.INVALID);
	    
	    Extension messageIdExt = new Extension();
	    messageIdExt.setUrl("http://hl7.org/fhir/StructureDefinition/operationoutcome-message-id");
	    
	    String messageId;
	    String detailsText;
	    String systemUrl = matchedCodeSystem != null && matchedCodeSystem.hasUrl() ? 
	        matchedCodeSystem.getUrl() : "";
	    String codeValue = successfulParams != null && successfulParams.code() != null ?
	        successfulParams.code().getValue() : "";
	    String requestedLanguage = displayLanguage != null ? displayLanguage.getValue() : "";
	    
	    if (warningType == ValidationErrorType.NO_DISPLAY_FOR_LANGUAGE_NONE) {
	        // CodeSystem 完全不支援該語言
	        messageId = "NO_VALID_DISPLAY_FOUND_LANG_NONE";
	        detailsText = String.format(
	            "'%s' is the default display; the code system %s has no Display Names for the language %s",
	            matchedDisplay,
	            systemUrl,
	            requestedLanguage);
	    } else {
	        // CodeSystem 支援該語言但此 concept 沒有
	        messageId = "NO_VALID_DISPLAY_FOUND_LANG_SOME";
	        detailsText = String.format(
	            "'%s' is the default display; no valid Display Names found for %s#%s in the language %s",
	            matchedDisplay,
	            systemUrl,
	            codeValue,
	            requestedLanguage);
	    }
	    
	    messageIdExt.setValue(new StringType(messageId));
	    languageWarningIssue.addExtension(messageIdExt);
	    
	    CodeableConcept details = new CodeableConcept();
	    Coding coding = details.addCoding();
	    coding.setSystem("http://hl7.org/fhir/tools/CodeSystem/tx-issue-type");
	    coding.setCode("display-comment");
	    details.setText(detailsText);
	    languageWarningIssue.setDetails(details);
	    
	    languageWarningIssue.addLocation("Coding.display");
	    languageWarningIssue.addExpression("Coding.display");
	    
	    // 4. issues 參數
	    result.addParameter().setName("issues").setResource(outcome);
	    
	    // 5. result 參數
	    result.addParameter("result", new BooleanType(true));
	    
	    // 6. system 參數
	    if (successfulParams != null && successfulParams.system() != null) {
	        result.addParameter("system", successfulParams.system());
	    } else if (matchedCodeSystem != null && matchedCodeSystem.hasUrl()) {
	        result.addParameter("system", new UriType(matchedCodeSystem.getUrl()));
	    }
	    
	    // 7. version 參數 (如果有)
	    if (effectiveSystemVersion != null && !effectiveSystemVersion.isEmpty()) {
	        result.addParameter("version", effectiveSystemVersion);
	    } else if (matchedCodeSystem != null && matchedCodeSystem.hasVersion()) {
	        result.addParameter("version", new StringType(matchedCodeSystem.getVersion()));
	    }
	    
	    return result;
	}
	
	private boolean isValidLanguageCode(String languageCode) {
		if (languageCode == null || languageCode.isEmpty()) {
	        return false;
	    }
	    
	    // 檢查是否為 "-" (範例二的情況)
	    if ("-".equals(languageCode)) {
	        return false;
	    }
	    
	    // 支援逗號分隔的多個語言代碼
	    String[] languages = languageCode.split(",");
	    
	    // 驗證每個語言代碼
	    for (String lang : languages) {
	        String trimmedLang = lang.trim();
	        
	        // 檢查是否為空或 "-"
	        if (trimmedLang.isEmpty() || "-".equals(trimmedLang)) {
	            return false;
	        }
	        
	        // 符合 BCP 47 格式: 2-3 個字母的主要語言標籤,可選的子標籤
	        // 例如: en, en-US, zh-TW, de
	        String languagePattern = "^[a-zA-Z]{2,3}(-[a-zA-Z0-9]{2,8})*$";
	        if (!trimmedLang.matches(languagePattern)) {
	            return false;
	        }
	    }
	    
	    return true;
	}
	
	private Parameters buildInvalidLanguageCodeError(ValidationParams params, CodeType displayLanguage) {
		OperationOutcome outcome = new OperationOutcome();
		outcome.setMeta(null);
		outcome.setText(null);
		
		var issue = outcome.addIssue();
		issue.setSeverity(OperationOutcome.IssueSeverity.ERROR);
		issue.setCode(OperationOutcome.IssueType.PROCESSING);
		
		CodeableConcept details = new CodeableConcept();
		String languageValue = displayLanguage != null ? displayLanguage.getValue() : "";
		details.setText(String.format("Invalid displayLanguage: '%s'", languageValue));
		issue.setDetails(details);
		
		// 移除 narratives
		outcome.setText(null);
		outcome.setMeta(null);
		
		throw new InvalidRequestException("Invalid displayLanguage parameter", outcome);
	}
	
	private Parameters buildInvalidDisplayWithLanguageError(
	        ValidationParams successfulParams,
	        String correctDisplay,
	        CodeSystem matchedCodeSystem,
	        ValueSet targetValueSet,
	        StringType effectiveSystemVersion,
	        CodeType displayLanguage) {
	    
	    Parameters result = new Parameters();
	    
	    // 1. code 參數
	    if (successfulParams != null && successfulParams.code() != null) {
	        result.addParameter("code", successfulParams.code());
	    }
	    
	    // 2. display 參數 - 使用正確的 display
	    if (correctDisplay != null) {
	        result.addParameter("display", new StringType(correctDisplay));
	    }
	    
	    // 3. 建立 OperationOutcome
	    OperationOutcome outcome = new OperationOutcome();
	    outcome.setMeta(null);
	    outcome.setText(null);
	    
	    var invalidDisplayIssue = outcome.addIssue();
	    invalidDisplayIssue.setSeverity(OperationOutcome.IssueSeverity.ERROR); 
	    invalidDisplayIssue.setCode(OperationOutcome.IssueType.INVALID);
	    
	    Extension messageIdExt = new Extension();
	    messageIdExt.setUrl("http://hl7.org/fhir/StructureDefinition/operationoutcome-message-id");
	    
	    String systemUrl = matchedCodeSystem != null && matchedCodeSystem.hasUrl() ? 
	        matchedCodeSystem.getUrl() : "";
	    String codeValue = successfulParams != null && successfulParams.code() != null ?
	        successfulParams.code().getValue() : "";
	    String requestedLanguage = displayLanguage != null ? displayLanguage.getValue() : "";
	    String incorrectDisplay = successfulParams != null && successfulParams.display() != null ?
	        successfulParams.display().getValue() : "";
	    
	    String messageId = "NO_VALID_DISPLAY_FOUND_NONE_FOR_LANG_ERR";
	    String detailsText = String.format(
	        "Wrong Display Name '%s' for %s#%s. There are no valid display names found for language(s) '%s'. Default display is '%s'",
	        incorrectDisplay,
	        systemUrl,
	        codeValue,
	        requestedLanguage,
	        correctDisplay);
	    
	    messageIdExt.setValue(new StringType(messageId));
	    invalidDisplayIssue.addExtension(messageIdExt);
	    
	    CodeableConcept details = new CodeableConcept();
	    Coding coding = details.addCoding();
	    coding.setSystem("http://hl7.org/fhir/tools/CodeSystem/tx-issue-type");
	    coding.setCode("invalid-display");
	    details.setText(detailsText);
	    invalidDisplayIssue.setDetails(details);
	    
	    invalidDisplayIssue.addLocation("Coding.display");
	    invalidDisplayIssue.addExpression("Coding.display");
	    
	    // 4. issues 參數
	    result.addParameter().setName("issues").setResource(outcome);
	    
	    // 5. message 參數
	    result.addParameter("message", new StringType(detailsText));
	    
	    // 6. result 參數 - false
	    result.addParameter("result", new BooleanType(false));
	    
	    // 7. system 參數
	    if (successfulParams != null && successfulParams.system() != null) {
	        result.addParameter("system", successfulParams.system());
	    } else if (matchedCodeSystem != null && matchedCodeSystem.hasUrl()) {
	        result.addParameter("system", new UriType(matchedCodeSystem.getUrl()));
	    }
	    
	    return result;
	}
	
	private InvalidRequestException buildInvalidLanguageCodeException(CodeType displayLanguage) {
	    OperationOutcome outcome = new OperationOutcome();
	    outcome.setMeta(null);
	    outcome.setText(null);
	    
	    var issue = outcome.addIssue();
	    issue.setSeverity(OperationOutcome.IssueSeverity.ERROR);
	    issue.setCode(OperationOutcome.IssueType.PROCESSING);
	    
	    CodeableConcept details = new CodeableConcept();
	    String languageValue = displayLanguage != null ? displayLanguage.getValue() : "";
	    details.setText(String.format("Invalid displayLanguage: '%s'", languageValue));
	    issue.setDetails(details);
	    
	    // 確保沒有 narrative
	    outcome.setText(null);
	    outcome.setMeta(null);
	    
	    return new InvalidRequestException("Invalid displayLanguage parameter", outcome);
	}
	
	private Parameters buildSupplementNotFoundError(
	        ValidationParams params,
	        ValueSet targetValueSet,
	        StringType effectiveSystemVersion,
	        List<String> missingSupplements) {
	    
	    Parameters result = new Parameters();
	    
	    // 1. code 參數
	    if (params.code() != null) {
	        result.addParameter("code", params.code());
	    }
	    
	    // 2. 建立 OperationOutcome
	    OperationOutcome outcome = new OperationOutcome();
	    outcome.setMeta(null);
	    outcome.setText(null);
	    
	    String supplementUrl = !missingSupplements.isEmpty() ? missingSupplements.get(0) : "";
	    
	    // Issue 1: Supplement not found
	    var supplementIssue = outcome.addIssue();
	    supplementIssue.setSeverity(OperationOutcome.IssueSeverity.ERROR);
	    // 根據實際情況選擇 code: business-rule 或 not-found
	    supplementIssue.setCode(OperationOutcome.IssueType.NOTFOUND);
	    
	    CodeableConcept details = new CodeableConcept();
	    Coding coding = details.addCoding();
	    coding.setSystem("http://hl7.org/fhir/tools/CodeSystem/tx-issue-type");
	    coding.setCode("not-found");
	    details.setText(String.format(
	        "The supplement CodeSystem '%s' could not be found",
	        supplementUrl));
	    supplementIssue.setDetails(details);
	    
	    // 3. issues 參數
	    result.addParameter().setName("issues").setResource(outcome);
	    
	    // 4. message 參數
	    result.addParameter("message", new StringType(
	        String.format("Supplement CodeSystem %s not found", supplementUrl)));
	    
	    // 5. result 參數
	    result.addParameter("result", new BooleanType(false));
	    
	    // 6. system 參數
	    if (params.system() != null) {
	        result.addParameter("system", params.system());
	    }
	    
	    return result;
	}
}