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
            @OperationParam(name = "activeOnly") BooleanType activeOnly,
            @OperationParam(name = "inferSystem") BooleanType inferSystem,
            @OperationParam(name = "lenient-display-validation") BooleanType lenientDisplayValidation
    ) {
        try {        
        	
        	// 解析系統 URL
            UriType resolvedSystem = resolveUriParameter(system, systemCanonical);
        	
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
                    UriType resolvedUrl = resolveUriParameter(url, urlCanonical);
                    UriType resolvedValueSetUrl = resolveUriParameter(valueSetUrl, valueSetCanonical);
                    
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
                        lenientDisplayValidation
                    );
                    
                    // 處理 SYSTEM_NOT_FOUND 錯誤
                    if (validationResult.errorType() == ValidationErrorType.SYSTEM_NOT_FOUND) {
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
                            null,  // codeSystem 為 null 因為找不到
                            "CodeSystem not found");
                        
                        removeNarratives(errorResult);
                        return errorResult;
                    }
                    
                    // 處理 SYSTEM_VERSION_NOT_FOUND 錯誤
                    if (validationResult.errorType() == ValidationErrorType.SYSTEM_VERSION_NOT_FOUND) {
                        Parameters errorResult = buildCodeSystemVersionNotFoundError(
                            params, 
                            targetValueSet, 
                            effectiveSystemVersion,
                            validationResult.versionException()
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
                        
                        break; // 找到有效的結果，跳出循環
                    }
                    
                    // INVALID_DISPLAY（嚴格模式）- 不要立即返回
                    if (validationResult.errorType() == ValidationErrorType.INVALID_DISPLAY) {
                        // 保存資訊但標記為找到了有效的 code（只是 display 錯誤）
                        foundValidCode = true;
                        matchedConcept = validationResult.concept();
                        matchedCodeSystem = validationResult.codeSystem();
                        matchedDisplay = validationResult.display();
                        successfulParams = params;
                        
                        if (effectiveSystemVersion != null && 
                            (resolvedSystemVersion == null || resolvedSystemVersion.isEmpty())) {
                            resolvedSystemVersion = effectiveSystemVersion;
                        }
                        
                        // **關鍵修改：記錄這個錯誤上下文**
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
                        
                        // **不要在這裡返回，繼續檢查是否有其他 coding**
                        continue;
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
                        
                    } else if (validationResult.errorType() == ValidationErrorType.INVALID_DISPLAY) {

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
                    completeContext = new ValidationContext(
                        failedValidationContext.parameterSource(),
                        failedValidationContext.code(),
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
                        code,
                        resolvedSystem,
                        display,
                        ValidationErrorType.INVALID_CODE,
                        targetValueSet,
                        resolvedSystemVersion,
                        null,
                        codeableConcept
                    );
                }
                
                Parameters errorResult = buildValidationErrorWithOutcome(
                    false, 
                    completeContext,
                    matchedCodeSystem, 
                    "Code validation failed");
                
                removeNarratives(errorResult);
                return errorResult;
            }

            // 檢查是否為「部分成功」情況（CodeableConcept 特有）
            boolean isCodeableConcept = codeableConcept != null || 
                (successfulParams != null && 
                 (successfulParams.originalCodeableConcept() != null ||
                  "codeableConcept".equals(successfulParams.parameterSource()) ||
                  successfulParams.parameterSource().startsWith("codeableConcept.coding")));

            if (isCodeableConcept && !allValidationContexts.isEmpty()) {
                // 檢查是否所有錯誤都是 INVALID_DISPLAY
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
	                        finalSystemVersion
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
            if (!anyValid && foundValidCode && 
                failedValidationContext != null && 
                failedValidationContext.errorType() == ValidationErrorType.INVALID_DISPLAY) {
                
                StringType finalSystemVersion = determineEffectiveSystemVersion(
                    successfulParams, resolvedSystemVersion);
                
                Parameters errorResult = buildInvalidDisplayError(
                    successfulParams, 
                    matchedDisplay, 
                    matchedCodeSystem, 
                    targetValueSet, 
                    finalSystemVersion
                );
                
                removeNarratives(errorResult);
                return errorResult;
            }
            
            // 完全成功的情況
            StringType finalSystemVersion = determineEffectiveSystemVersion(
                successfulParams, resolvedSystemVersion);

            // 檢查是否有 display warning
            Parameters successResult;
            if (hasDisplayWarning) {
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
                String valueSetUrlForError = extractValueSetUrlFromException(e, url, urlCanonical, valueSetUrl, valueSetCanonical);
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
                UriType resolvedSystem = resolveUriParameter(system, systemCanonical);
                
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
                        UriType resolvedUrl = resolveUriParameter(url, urlCanonical);
                        UriType resolvedValueSetUrl = resolveUriParameter(valueSetUrl, valueSetCanonical);
                        
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
                
                Parameters exceptionResult = buildValidationErrorWithOutcome(false, errorContext, null, e.getMessage());
                
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
            Parameters exceptionResult = buildValidationErrorWithOutcome(false, errorContext, null, e.getMessage());
            
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
                
                throw new InvalidRequestException("Invalid request parameters", outcome); // 400
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
            										BooleanType activeOnly, BooleanType lenientDisplayValidation) {
    	
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

        // 優先檢查 expansion
    	if (valueSet.hasExpansion() && isExpansionCurrent(valueSet.getExpansion())) {
            ValidationResult expansionResult = validateCodeInExpansion(
                valueSet.getExpansion(), code, system, display, displayLanguage, abstractAllowed, systemVersion, activeOnly, lenientDisplayValidation);
            if (expansionResult.isValid()) {
                return expansionResult;
            }
        }

        // 處理 compose 規則
    	if (valueSet.hasCompose()) {
            return validateCodeInCompose(valueSet.getCompose(), code, system, display, 
                                       displayLanguage, abstractAllowed, systemVersion, activeOnly, lenientDisplayValidation);
        }

    	return new ValidationResult(false, null, null, null, ValidationErrorType.CODE_NOT_IN_VALUESET, null, null, null);
    }

    private ValidationResult validateCodeInConceptSet(ConceptSetComponent conceptSet, CodeType code, 
            											UriType system, StringType display, 
            											CodeType displayLanguage, BooleanType abstractAllowed,
            											boolean isInclude, StringType resolvedSystemVersion,
            											BooleanType activeOnly, BooleanType lenientDisplayValidation) {
        
    	
        // 先檢查請求的 system 是否存在，再檢查是否匹配
        if (system != null && !system.isEmpty()) {
            // 先嘗試查找請求的 CodeSystem 是否存在
            try {
                findCodeSystemByUrl(system.getValue(), null);
            } catch (ResourceNotFoundException e) {
                // 請求的 CodeSystem 不存在
                System.out.println("Requested CodeSystem not found: " + system.getValue());
                return new ValidationResult(false, null, null, null, 
                    ValidationErrorType.SYSTEM_NOT_FOUND, null, null, null);
            }
            
            // CodeSystem 存在，但與 conceptSet 的 system 不匹配
            if (conceptSet.hasSystem() && !system.getValue().equals(conceptSet.getSystem())) {
                System.out.println("System mismatch: " + system.getValue() + " != " + conceptSet.getSystem());
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
                
                if (versionToUse != null) {
                    codeSystem = findCodeSystemWithVersionFallback(systemUrl, versionToUse);
                } else {
                	// **關鍵修改：當沒有明確版本時，如果有 display，嘗試推斷版本**
                    if (display != null && !display.isEmpty() && code != null) {
                        StringType inferredVersion = inferVersionFromCodeAndDisplay(
                            systemUrl, code.getValue(), display.getValue());
                        
                        if (inferredVersion != null) {
                            codeSystem = findCodeSystemByUrl(systemUrl, inferredVersion.getValue());
                        } else {
                            // 如果無法推斷，使用最新版本
                            codeSystem = findCodeSystemByUrl(systemUrl, null);
                        }
                    } else {
                        codeSystem = findCodeSystemByUrl(systemUrl, null);
                    }
                }
                
            } catch (CodeSystemVersionNotFoundException e) {
                // 版本找不到的特殊處理
                return new ValidationResult(false, null, null, null, 
                    ValidationErrorType.SYSTEM_VERSION_NOT_FOUND, null, e, null);
            } catch (ResourceNotFoundException e) {
            	//標記系統找不到
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
                                           displayLanguage, abstractAllowed, codeSystem, lenientDisplayValidation);
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
                displayLanguage, abstractAllowed, lenientDisplayValidation);
            
            // 保留 INVALID_DISPLAY 錯誤類型
            if (!filterResult.isValid()) {
                // 如果是 INVALID_DISPLAY 錯誤，直接返回
                if (filterResult.errorType() == ValidationErrorType.INVALID_DISPLAY) {
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
            }
            
            return filterResult;
        }

        if (codeSystem != null) {
            ValidationResult result = validateCodeInCodeSystem(codeSystem, code, display, displayLanguage, abstractAllowed, activeOnly, lenientDisplayValidation);
            
            // **修改：保留 INVALID_DISPLAY 和 INVALID_DISPLAY_WARNING 錯誤類型**
            if (!result.isValid()) {
                if (result.errorType() == ValidationErrorType.INVALID_DISPLAY ||
                    result.errorType() == ValidationErrorType.INVALID_DISPLAY_WARNING) {
                    return result; // 直接返回
                }
                
                // 如果找到了 concept，說明 code 存在但不在 ValueSet 中
                if (result.concept() != null) {
                    return new ValidationResult(false, result.concept(), codeSystem, result.display(), 
                                               ValidationErrorType.CODE_NOT_IN_VALUESET, 
                                               result.isInactive(), null, null);
                }
                
                // 如果連 concept 都找不到，返回 INVALID_CODE
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
			return requestVersion;
		}

		// 如果沒有請求版本，使用 ValueSet 中定義的版本
		return conceptSetVersion;
    }
    
    private ValidationResult validateCodeInCompose(ValueSetComposeComponent compose, CodeType code, 
													UriType system, StringType display, 
													CodeType displayLanguage, BooleanType abstractAllowed,
													StringType systemVersion, BooleanType activeOnly,
		                                            BooleanType lenientDisplayValidation) {
			
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
			                 	displayLanguage, abstractAllowed, true, systemVersion, activeOnly, lenientDisplayValidation);
			
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
	            if (result.errorType() == ValidationErrorType.INVALID_DISPLAY) {
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
				if (lastErrorResult != null && lastErrorResult.errorType() != null) {
					// 保留 codeSystem 信息，即使驗證失敗
					if (lastErrorResult.errorType() == ValidationErrorType.INVALID_CODE && 
			                lastFoundConcept != null && lastCheckedCodeSystem != null) {
			                // code 存在於 CodeSystem 但不在 ValueSet 中
			                return new ValidationResult(false, lastFoundConcept, lastCheckedCodeSystem, 
			                                           lastFoundConcept.getDisplay(), 
			                                           ValidationErrorType.CODE_NOT_IN_VALUESET, 
			                                           null, null, null);
			            }
	                return lastErrorResult;
	            }
				return new ValidationResult(false, null, null, null, ValidationErrorType.CODE_NOT_IN_VALUESET, null, null, null);
			}
			
			// 檢查 exclude 部分
			for (ConceptSetComponent exclude : compose.getExclude()) {
				ValidationResult result = validateCodeInConceptSet(exclude, code, system, display, 
			                 	displayLanguage, abstractAllowed, false, systemVersion, activeOnly, lenientDisplayValidation);
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
		                                                BooleanType lenientDisplayValidation) {
		
    	for (ValueSetExpansionContainsComponent contains : expansion.getContains()) {
            ValidationResult result = validateCodeInExpansionContains(contains, code, system, display, 
                                                                    displayLanguage, abstractAllowed, 
                                                                    systemVersion, activeOnly, lenientDisplayValidation);
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
                                                           BooleanType lenientDisplayValidation) {
        
        if (code.getValue().equals(contains.getCode()) && 
            (system == null || system.getValue().equals(contains.getSystem()))) {
            
            if (!isVersionMatch(contains, resolvedSystemVersion)) {
                // 版本不匹配的處理
            } else {
                // 檢查是否為抽象概念
                if (contains.hasAbstract() && contains.getAbstract() && 
                    (abstractAllowed == null || !abstractAllowed.getValue())) {
                    return new ValidationResult(false, null, null, null, 
                                              ValidationErrorType.ABSTRACT_CODE_NOT_ALLOWED, null, null, null);
                }
                
                // 驗證 display
                if (display != null && !display.isEmpty() && contains.hasDisplay() && 
                    !display.getValue().equalsIgnoreCase(contains.getDisplay())) {
                	
                	boolean isLenient = lenientDisplayValidation != null && lenientDisplayValidation.getValue();
                    
                    if (isLenient) {
                        // 寬鬆模式：返回成功但帶有警告
                        Boolean isInactive = contains.hasInactive() ? contains.getInactive() : false;
                        return new ValidationResult(true, null, null, contains.getDisplay(), 
                                                  ValidationErrorType.INVALID_DISPLAY_WARNING, isInactive, null, null);
                    } else {
                        // 嚴格模式：返回錯誤
                        return new ValidationResult(false, null, null, contains.getDisplay(), 
                                                  ValidationErrorType.INVALID_DISPLAY, null, null, null);
                    }
                }
                
                // 檢查是否 inactive
                Boolean isInactive = contains.hasInactive() ? contains.getInactive() : false;
                
                return new ValidationResult(true, null, null, contains.getDisplay(), null, isInactive, null, null);
            }
        }
        
        // 遞歸檢查子概念
        for (ValueSetExpansionContainsComponent child : contains.getContains()) {
            ValidationResult result = validateCodeInExpansionContains(child, code, system, display, 
                                                                    displayLanguage, abstractAllowed, 
                                                                    resolvedSystemVersion, activeOnly,
                                                                    lenientDisplayValidation);
            if (result.isValid()) {
                return result;
            }
        }
        
        return new ValidationResult(false, null, null, null, ValidationErrorType.INVALID_CODE, null, null, null);
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

        result.setMeta(null);
        
        boolean isInvalidDisplay = context.errorType() == ValidationErrorType.INVALID_DISPLAY;

        boolean isCodeableConcept = context.originalCodeableConcept() != null || 
                                    "codeableConcept".equals(context.parameterSource()) ||
                                    context.parameterSource().startsWith("codeableConcept.coding");
        
        boolean isCoding = "coding".equals(context.parameterSource());
        boolean isCodeParam = "code".equals(context.parameterSource());
        
        boolean isSystemNotFound = (context.errorType() == ValidationErrorType.SYSTEM_NOT_FOUND);
        boolean isCodeNotInValueSet = (context.errorType() == ValidationErrorType.CODE_NOT_IN_VALUESET);
        
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
        } else if ("codeableConcept".equals(context.parameterSource()) || 
                   context.parameterSource().startsWith("codeableConcept.coding")) {
            CodeableConcept codeableConcept = reconstructCodeableConcept(context);
            result.addParameter("codeableConcept", codeableConcept);
        } else {
            if (context.code() != null) {
                result.addParameter("code", context.code());
            }
        }
        
        // 如果有 display，添加 display 參數
        if (context.display() != null && !context.display().isEmpty()) {
            result.addParameter("display", context.display());
        } else if (codeSystem != null && context.code() != null) {
            // 嘗試從 CodeSystem 獲取 display
            ConceptDefinitionComponent concept = findConceptRecursive(codeSystem.getConcept(), context.code().getValue());
            if (concept != null && concept.hasDisplay()) {
                result.addParameter("display", new StringType(concept.getDisplay()));
            }
        }
        
        OperationOutcome outcome = new OperationOutcome();
        outcome.setMeta(null);
        outcome.setText(null);
 
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

            //仍然要回傳 system / version（因為 code 是合法的）
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
            } else if (isCoding && !isCodeNotInValueSet) {
                // 只有 coding 參數在非 CODE_NOT_IN_VALUESET 情況下才添加 Unknown_Code_in_Version
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
        if (isCodeParam) {
        	// code 參數：根據錯誤類型決定
            if (isSystemNotFound) {
                // SYSTEM_NOT_FOUND: 使用 $external:3:SystemUrl$
                mainErrorMessage = String.format("$external:3:%s$", systemUrl);
            } else if (isCodeNotInValueSet) {
                // CODE_NOT_IN_VALUESET: 使用 $external:2:ValueSetUrl|version$
                mainErrorMessage = String.format("$external:2:%s|%s$", valueSetUrl, valueSetVersion);
            } else {
                // 其他錯誤（如 INVALID_CODE）: 使用 $external:2:ValueSetUrl|version$
                mainErrorMessage = String.format("$external:2:%s|%s$", valueSetUrl, valueSetVersion);
            }
        } else if (isCoding && isSystemNotFound) {
            // coding 參數且 SYSTEM_NOT_FOUND: 組合完整的錯誤訊息
            mainErrorMessage = String.format(
                "A definition for CodeSystem '%s' could not be found, so the code cannot be validated; " +
                "The provided code '%s#%s' was not found in the value set '%s|%s'",
                systemUrl, systemUrl, codeValue, valueSetUrl, valueSetVersion);
        } else if (isCodeableConcept) {
        	if (isSystemNotFound) {
                // SYSTEM_NOT_FOUND: $external:3:system$
                mainErrorMessage = String.format("$external:3:%s$", systemUrl);
            } else {
                // INVALID_CODE: $external:3$ (不帶 system)
                mainErrorMessage = "$external:3$";
            }
        } else {
            mainErrorMessage = String.format("$external:3:%s$", systemUrl);
        }
        
        result.addParameter("message", new StringType(mainErrorMessage));
        result.addParameter("result", new BooleanType(isValid));
        
        //對於非 codeableConcept，確保回傳 system（添加 fallback）
        if (!isCodeableConcept) {
            UriType systemToReturn = null;
            
            // 優先使用 context.system()
            if (context.system() != null && !context.system().isEmpty()) {
                systemToReturn = context.system();
            } 
            // fallback 到 codeSystem 參數
            else if (codeSystem != null && codeSystem.hasUrl()) {
                systemToReturn = new UriType(codeSystem.getUrl());
            }
            
            if (systemToReturn != null) {
                result.addParameter("system", systemToReturn);
            }
        }

        boolean shouldIncludeVersion = false;
        
        if (isCodeParam) {
            // code 參數：只有在 CodeSystem 找到且不是 SYSTEM_NOT_FOUND 時才返回 version
            shouldIncludeVersion = !isSystemNotFound && codeSystem != null;
        } else {
            // 其他參數類型：根據錯誤類型決定
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
                                                    BooleanType abstractAllowed,BooleanType lenientDisplayValidation) {
        
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

        // 驗證 display
        if (display != null && !display.isEmpty()) {
            boolean isValidDisplay = isDisplayValidExtended(concept, display, displayLanguage);
            if (!isValidDisplay) {
                String correctDisplay = getDisplayForLanguage(concept, displayLanguage);
                
                // 檢查是否啟用寬鬆驗證
                boolean isLenient = lenientDisplayValidation != null && lenientDisplayValidation.getValue();
                
                if (isLenient) {
                    // 寬鬆模式：返回成功但帶有警告
                    return new ValidationResult(true, concept, codeSystem, correctDisplay, 
                                              ValidationErrorType.INVALID_DISPLAY_WARNING, null, null, null);
                } else {
                    // 嚴格模式：返回錯誤
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

    // 在 concept 列表中驗證
    private ValidationResult validateCodeInConceptList(List<ConceptReferenceComponent> concepts, 
                                                     CodeType code, StringType display, 
                                                     CodeType displayLanguage, BooleanType abstractAllowed,
                                                     CodeSystem codeSystem, BooleanType lenientDisplayValidation) {
        
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
                        
                        if (display != null && !display.isEmpty()) {
                            boolean isValidDisplay = isDisplayValidExtended(fullConcept, display, displayLanguage);
                            if (!isValidDisplay) {
                                String correctDisplay = getDisplayForLanguage(fullConcept, displayLanguage);
                                // 檢查是否啟用寬鬆驗證
                                boolean isLenient = lenientDisplayValidation != null && lenientDisplayValidation.getValue();
                                
                                if (isLenient) {
                                    // 寬鬆模式：返回成功但帶有警告
                                    return new ValidationResult(true, fullConcept, codeSystem, correctDisplay, 
                                                              ValidationErrorType.INVALID_DISPLAY_WARNING, null, null, null);
                                } else {
                                    // 嚴格模式：返回錯誤
                                    return new ValidationResult(false, fullConcept, codeSystem, correctDisplay, 
                                                              ValidationErrorType.INVALID_DISPLAY, null, null, null);
                                }
                            }
                        }
                        
                        String resolvedDisplay = getDisplayForLanguage(fullConcept, displayLanguage);
                        return new ValidationResult(true, fullConcept, codeSystem, resolvedDisplay, null, null, null, null);
                    }
                }
                
                // 處理沒有 codeSystem 的情況
                String conceptDisplay = concept.hasDisplay() ? concept.getDisplay() : null;
                if (display != null && !display.isEmpty() && conceptDisplay != null && 
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
                                                        BooleanType lenientDisplayValidation) {

    	ConceptDefinitionComponent concept = findConceptRecursive(codeSystem.getConcept(), code.getValue());
        if (concept == null) {
            return new ValidationResult(false, null, codeSystem, null, ValidationErrorType.INVALID_CODE, null, null, null);
        }

        if (isAbstractConcept(concept) && (abstractAllowed == null || !abstractAllowed.getValue())) {
            return new ValidationResult(false, concept, codeSystem, null, 
                                      ValidationErrorType.ABSTRACT_CODE_NOT_ALLOWED, null, null, null);
        }

        Boolean isInactive = isConceptInactive(concept);

        if (display != null && !display.isEmpty()) {
            boolean isValidDisplay = isDisplayValidExtended(concept, display, displayLanguage);
            if (!isValidDisplay) {
                String correctDisplay = getDisplayForLanguage(concept, displayLanguage);
                // 檢查是否啟用寬鬆驗證
                boolean isLenient = lenientDisplayValidation != null && lenientDisplayValidation.getValue();
                
                if (isLenient) {
                    return new ValidationResult(true, concept, codeSystem, correctDisplay, 
                                              ValidationErrorType.INVALID_DISPLAY_WARNING, 
                                              isInactive, null, null);
                } else {
                    return new ValidationResult(
                        false,
                        concept,
                        codeSystem,
                        correctDisplay,
                        ValidationErrorType.INVALID_DISPLAY,
                        isInactive, 
                        null, 
                        null
                    );
                }
            }
        }

        String resolvedDisplay = getDisplayForLanguage(concept, displayLanguage);

        return new ValidationResult(true, concept, codeSystem, resolvedDisplay, null, isInactive, null, null);
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
		    
		    String displayValue = display.getValue();
		    
		    // 先檢查是否匹配主要的 display（無論語言）
		    if (concept.hasDisplay() && displayValue.equalsIgnoreCase(concept.getDisplay())) {
		        return true;
		    }
		    
		    // 如果沒有指定語言，只檢查主要 display（已在上面檢查）
		    if (displayLanguage == null || displayLanguage.isEmpty()) {
		        return false;
		    }
		    
		    // 解析多個語言並檢查所有 designation
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
		    
		    // 解析多個語言（逗號分隔）
		    String[] languages = displayLanguage.getValue().split(",");
		    
		    // 檢查每個指定的語言
		    for (String lang : languages) {
		        lang = lang.trim();
		        
		        // 檢查 designation 中是否有匹配的語言
		        for (ConceptDefinitionDesignationComponent desig : concept.getDesignation()) {
		            if (lang.equalsIgnoreCase(desig.getLanguage())) {
		                return desig.getValue();
		            }
		        }
		    }
		    
		    // 如果所有指定語言都沒有找到對應的 designation，返回主要的 display
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
                                                  CanonicalType valueSetCanonical) {
        UriType resolvedUrl = resolveUriParameter(url, urlCanonical);
        if (resolvedUrl != null && !resolvedUrl.isEmpty()) {
            return resolvedUrl.getValue();
        }
        
        UriType resolvedValueSetUrl = resolveUriParameter(valueSetUrl, valueSetCanonical);
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
		/*String message = String.format(
			"A definition for CodeSystem %s could not be found, so the code cannot be validated; " +
			"The provided code '%s#%s' was not found in the value set '%s|%s'",
			systemUrl, systemUrl, params.code().getValue(), valueSetUrl, valueSetVersion);
			result.addParameter("message", new StringType(message));*/
			
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
    
    private Parameters buildCodeSystemVersionNotFoundError(
            ValidationParams params,
            ValueSet targetValueSet,
            StringType effectiveSystemVersion,
            CodeSystemVersionNotFoundException versionException) {
        
        Parameters result = new Parameters();
        
        // 1. codeableConcept 參數
        if (params.originalCodeableConcept() != null) {
            result.addParameter("codeableConcept", params.originalCodeableConcept());
        }
        
        // 2. 建立 OperationOutcome
        OperationOutcome outcome = new OperationOutcome();
        outcome.setMeta(null);
        outcome.setText(null);
        
        String systemUrl = params.system() != null ? params.system().getValue() : "";
        String requestedVersion = effectiveSystemVersion != null ? effectiveSystemVersion.getValue() : "";
        String valueSetUrl = targetValueSet != null && targetValueSet.hasUrl() ? 
            targetValueSet.getUrl() : "";
        String valueSetVersion = targetValueSet != null && targetValueSet.hasVersion() ? 
            targetValueSet.getVersion() : "5.0.0";
        
        // 獲取可用版本列表
        List<String> availableVersions = versionException != null && versionException.getAvailableVersions() != null ?
            versionException.getAvailableVersions() : new ArrayList<>();
        String validVersionsText = availableVersions.toString();
        
        // Issue 1: UNKNOWN_CODESYSTEM_VERSION
        var versionNotFoundIssue = outcome.addIssue();
        versionNotFoundIssue.setSeverity(OperationOutcome.IssueSeverity.ERROR);
        versionNotFoundIssue.setCode(OperationOutcome.IssueType.NOTFOUND);
        
        // 添加 extension
        Extension messageIdExt = new Extension();
        messageIdExt.setUrl("http://hl7.org/fhir/StructureDefinition/operationoutcome-message-id");
        messageIdExt.setValue(new StringType("UNKNOWN_CODESYSTEM_VERSION"));
        versionNotFoundIssue.addExtension(messageIdExt);
        
        // 設置 details
        CodeableConcept details1 = new CodeableConcept();
        Coding coding1 = details1.addCoding();
        coding1.setSystem("http://hl7.org/fhir/tools/CodeSystem/tx-issue-type");
        coding1.setCode("not-found");
        details1.setText(String.format(
            "A definition for CodeSystem '%s' version '%s' could not be found, " +
            "so the code cannot be validated. Valid versions: %s",
            systemUrl, requestedVersion, validVersionsText));
        versionNotFoundIssue.setDetails(details1);
        
        int codingIndex = extractCodingIndex(params.parameterSource());
        versionNotFoundIssue.addLocation(String.format("CodeableConcept.coding[%d].system", codingIndex));
        versionNotFoundIssue.addExpression(String.format("CodeableConcept.coding[%d].system", codingIndex));
        
        // Issue 2: UNABLE_TO_CHECK_IF_THE_PROVIDED_CODES_ARE_IN_THE_VALUE_SET_CS (WARNING)
        var unableToCheckIssue = outcome.addIssue();
        unableToCheckIssue.setSeverity(OperationOutcome.IssueSeverity.WARNING);
        unableToCheckIssue.setCode(OperationOutcome.IssueType.NOTFOUND);
        
        // 添加 extension
        Extension messageIdExt2 = new Extension();
        messageIdExt2.setUrl("http://hl7.org/fhir/StructureDefinition/operationoutcome-message-id");
        messageIdExt2.setValue(new StringType("UNABLE_TO_CHECK_IF_THE_PROVIDED_CODES_ARE_IN_THE_VALUE_SET_CS"));
        unableToCheckIssue.addExtension(messageIdExt2);
        
        // 設置 details
        CodeableConcept details2 = new CodeableConcept();
        Coding coding2 = details2.addCoding();
        coding2.setSystem("http://hl7.org/fhir/tools/CodeSystem/tx-issue-type");
        coding2.setCode("vs-invalid");
        details2.setText(String.format(
            "Unable to check whether the code is in the value set '%s|%s' " +
            "because the code system %s|%s was not found",
            valueSetUrl, valueSetVersion, systemUrl, requestedVersion));
        unableToCheckIssue.setDetails(details2);
        
        // 3. issues 參數
        result.addParameter().setName("issues").setResource(outcome);
        
        // 4. message 參數
        String message = buildMessageFromOperationOutcome(outcome);
        result.addParameter("message", new StringType(message));
       /* String message = String.format(
            "A definition for CodeSystem '%s' version '%s' could not be found, " +
            "so the code cannot be validated. Valid versions: %s; " +
            "Unable to check whether the code is in the value set '%s|%s' " +
            "because the code system %s|%s was not found",
            systemUrl, requestedVersion, validVersionsText,
            valueSetUrl, valueSetVersion, systemUrl, requestedVersion);
        result.addParameter("message", new StringType(message));*/
        
        // 5. result 參數
        result.addParameter("result", new BooleanType(false));
        
        // 6. x-caused-by-unknown-system 參數
        String canonicalUrl = systemUrl + "|" + requestedVersion;
        result.addParameter("x-caused-by-unknown-system", new CanonicalType(canonicalUrl));
        
        return result;
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
        // 如果有明確的 concept 列表
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
        
        // 如果是引用整個 CodeSystem,需要實際查詢
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
                // 如果找不到 CodeSystem,返回 false
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
	   /* String message = String.format(
	        "A definition for the value Set '%s' could not be found; " +
	        "Unable to check whether the code is in the value set '%s|%s' because the value set %s was not found",
	        missingValueSetUrl, valueSetUrl, valueSetVersion, missingValueSetUrl);
	    result.addParameter("message", new StringType(message));*/
	    
	    // 6. result 參數
	    result.addParameter("result", new BooleanType(false));
	    
	    // 7. system 和 version 參數 - 只在沒有 codeableConcept 時才添加
	    if (!isCodeableConcept) {
	        // system 參數
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
	                                           StringType effectiveSystemVersion) {
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
	        // **修改：code 參數使用 "display" 而不是 "Coding.display"**
	        displayLocation = "display";
	        displayExpression = "display";
	    }
	    
	    // Issue: Display_Name_for__should_be_one_of__instead_of
	    var invalidDisplayIssue = outcome.addIssue();
	    invalidDisplayIssue.setSeverity(OperationOutcome.IssueSeverity.ERROR);
	    invalidDisplayIssue.setCode(OperationOutcome.IssueType.INVALID);
	    
	    Extension messageIdExt = new Extension();
	    messageIdExt.setUrl(OperationOutcomeMessageId.MESSAGE_ID_EXTENSION_URL);
	    messageIdExt.setValue(new StringType(OperationOutcomeMessageId.DISPLAY_NAME_SHOULD_BE_ONE_OF));
	    invalidDisplayIssue.addExtension(messageIdExt);
	    
	    CodeableConcept details = new CodeableConcept();
	    Coding coding = details.addCoding();
	    coding.setSystem(OperationOutcomeMessageId.TX_ISSUE_TYPE_SYSTEM);
	    coding.setCode("invalid-display");
	    details.setText(String.format("$external:1:%s$", incorrectDisplay));
	    invalidDisplayIssue.setDetails(details);
	    
	    invalidDisplayIssue.addLocation(displayLocation);
	    invalidDisplayIssue.addExpression(displayExpression);
	    
	    // 5. issues 參數
	    result.addParameter().setName("issues").setResource(outcome);
	    
	    // 6. message 參數
	    String message = String.format("$external:2:%s$", incorrectDisplay);
	    result.addParameter("message", new StringType(message));
	    
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
	    
	    // 按錯誤類型分組並建立 issues
	    for (ValidationContext errorContext : allErrors) {
	        int codingIndex = extractCodingIndex(errorContext.parameterSource());
	        
	        if (errorContext.errorType() == ValidationErrorType.INVALID_CODE) {
	            // Unknown_Code_in_Version
	            var unknownCodeIssue = outcome.addIssue();
	            unknownCodeIssue.setSeverity(OperationOutcome.IssueSeverity.ERROR);
	            unknownCodeIssue.setCode(OperationOutcome.IssueType.CODEINVALID);
	            
	            Extension messageIdExt = new Extension();
	            messageIdExt.setUrl(OperationOutcomeMessageId.MESSAGE_ID_EXTENSION_URL);
	            messageIdExt.setValue(new StringType(OperationOutcomeMessageId.UNKNOWN_CODE_IN_VERSION));
	            unknownCodeIssue.addExtension(messageIdExt);
	            
	            String systemUrl = errorContext.system() != null ? errorContext.system().getValue() : "";
	            
	            CodeableConcept details = new CodeableConcept();
	            Coding coding = details.addCoding();
	            coding.setSystem("http://hl7.org/fhir/tools/CodeSystem/tx-issue-type");
	            coding.setCode("invalid-code");
	            details.setText(String.format("$external:2:%s$", systemUrl));
	            unknownCodeIssue.setDetails(details);
	            
	            unknownCodeIssue.addLocation(String.format("CodeableConcept.coding[%d].code", codingIndex));
	            unknownCodeIssue.addExpression(String.format("CodeableConcept.coding[%d].code", codingIndex));
	            
	        } else if (errorContext.errorType() == ValidationErrorType.INVALID_DISPLAY) {
	            // Display_Name_for__should_be_one_of__instead_of
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
	    }
	    
	    // 總是添加 None_of_the_provided_codes_are_in_the_value_set_one (information level)
	    var notInVsIssue = outcome.addIssue();
	    notInVsIssue.setSeverity(OperationOutcome.IssueSeverity.INFORMATION);
	    notInVsIssue.setCode(OperationOutcome.IssueType.CODEINVALID);
	    
	    Extension messageIdExt = new Extension();
	    messageIdExt.setUrl(OperationOutcomeMessageId.MESSAGE_ID_EXTENSION_URL);
	    messageIdExt.setValue(new StringType(OperationOutcomeMessageId.NONE_OF_CODES_IN_VALUE_SET_ONE));
	    notInVsIssue.addExtension(messageIdExt);
	    
	    CodeableConcept details = new CodeableConcept();
	    Coding coding = details.addCoding();
	    coding.setSystem("http://hl7.org/fhir/tools/CodeSystem/tx-issue-type");
	    coding.setCode("this-code-not-in-vs");
	    details.setText(String.format("$external:1:%s$", valueSetUrl));
	    notInVsIssue.setDetails(details);
	    
	    // 這個 issue 指向最後一個有錯誤的 coding
	    if (!allErrors.isEmpty()) {
	        ValidationContext lastError = allErrors.get(allErrors.size() - 1);
	        int lastCodingIndex = extractCodingIndex(lastError.parameterSource());
	        notInVsIssue.addLocation(String.format("CodeableConcept.coding[%d].code", lastCodingIndex));
	        notInVsIssue.addExpression(String.format("CodeableConcept.coding[%d].code", lastCodingIndex));
	    }
	    
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
}