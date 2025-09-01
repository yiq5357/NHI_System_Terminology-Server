package com.hitstdio.fhir.server.provider;

import ca.uhn.fhir.jpa.api.dao.DaoRegistry;
import ca.uhn.fhir.jpa.api.dao.IFhirResourceDao;
import ca.uhn.fhir.jpa.searchparam.SearchParameterMap;
import ca.uhn.fhir.rest.annotation.IdParam;
import ca.uhn.fhir.rest.annotation.Operation;
import ca.uhn.fhir.rest.annotation.OperationParam;
import ca.uhn.fhir.rest.annotation.ResourceParam;
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

            for (var params : paramsList) {
                validateValidationParams(params.code(), params.system(), resourceId, url, valueSetUrl);

                try {
                    // 驗證 code 是否在 ValueSet 中
                    ValidationResult validationResult = validateCodeInValueSet(
                        targetValueSet, 
                        params.code(), 
                        params.system(), 
                        params.display(), 
                        displayLanguage,
                        abstractAllowed
                    );

                    if (validationResult.isValid()) {
                        anyValid = true;
                        matchedConcept = validationResult.concept();
                        matchedCodeSystem = validationResult.codeSystem();
                        matchedDisplay = validationResult.display();
                        break;
                    } else {
                        // 保存失敗的驗證上下文
                        failedValidationContext = new ValidationContext(
                            params.parameterSource(),
                            params.code(),
                            params.system(),
                            params.display(),
                            validationResult.errorType(),
                            targetValueSet
                        );
                        if (validationResult.codeSystem() != null) {
                            matchedCodeSystem = validationResult.codeSystem();
                        }
                    }
                } catch (ResourceNotFoundException e) {
                    failedValidationContext = new ValidationContext(
                        params.parameterSource(),
                        params.code(),
                        params.system(),
                        params.display(),
                        ValidationErrorType.SYSTEM_NOT_FOUND,
                        targetValueSet
                    );
                }
            }

            if (!anyValid) {
                return buildValidationErrorWithOutcome(false, failedValidationContext,
                        matchedCodeSystem, "Code validation failed");
            }

         // 建立成功回應 - 修改這部分以符合期望的輸出格式
            return buildSuccessResponse(code, matchedDisplay, matchedCodeSystem, targetValueSet);

        } catch (InvalidRequestException | ResourceNotFoundException e) {
            ValidationContext errorContext = new ValidationContext(
                "code", code, system, display, ValidationErrorType.GENERAL_ERROR, null
            );
            return buildValidationErrorWithOutcome(false, errorContext, null, e.getMessage());
        } catch (Exception e) {
            ValidationContext errorContext = new ValidationContext(
                "code", code, system, display, ValidationErrorType.INTERNAL_ERROR, null
            );
            return buildValidationErrorWithOutcome(false, errorContext, null, "Internal error: " + e.getMessage());
        }
    }
    
    // 新增：統一處理 UriType 和 CanonicalType 的方法
    private UriType resolveUriParameter(UriType uriParam, CanonicalType canonicalParam) {
        if (uriParam != null && !uriParam.isEmpty()) {
            return uriParam;
        } else if (canonicalParam != null && !canonicalParam.isEmpty()) {
            // 將 CanonicalType 轉換為 UriType
            return new UriType(canonicalParam.getValue());
        }
        return null;
    }

    
 // 修改建立成功回應的方法，確保正確處理 canonical URL
    private Parameters buildSuccessResponse(CodeType code, String matchedDisplay, 
                                           CodeSystem matchedCodeSystem, ValueSet targetValueSet) {
        Parameters result = new Parameters();

        // 1. code 參數
        if (code != null) {
            result.addParameter("code", code);
        }

        // 2. display 參數
        if (matchedDisplay != null) {
            result.addParameter("display", new StringType(matchedDisplay));
        }

        // 3. result 參數
        result.addParameter("result", new BooleanType(true));

        // 4. system 參數 - 優先回傳 canonical URL（如果是標準術語系統）
        if (matchedCodeSystem != null && matchedCodeSystem.hasUrl()) {
            String systemUrl = matchedCodeSystem.getUrl();
            
            // 判斷是否為標準的 FHIR 術語系統，如果是則使用 canonical 格式
            if (isStandardFhirTerminology(systemUrl)) {
                result.addParameter("system", new CanonicalType(systemUrl));
            } else {
                result.addParameter("system", new UriType(systemUrl));
            }
        }

        // 5. version 參數
        if (matchedCodeSystem != null && matchedCodeSystem.hasVersion()) {
            result.addParameter("version", new StringType(matchedCodeSystem.getVersion()));
        }

        return result;
    }

    // 輔助方法：判斷是否為標準 FHIR 術語系統
    private boolean isStandardFhirTerminology(String systemUrl) {
        return systemUrl != null && (
            systemUrl.startsWith("http://hl7.org/fhir/") ||
            systemUrl.startsWith("http://terminology.hl7.org/") ||
            systemUrl.startsWith("http://snomed.info/") ||
            systemUrl.startsWith("http://loinc.org") ||
            systemUrl.startsWith("http://www.nlm.nih.gov/research/umls/rxnorm") ||
            systemUrl.startsWith("http://unitsofmeasure.org")
        );
    }
    
    // ValueSet 驗證結果記錄類
    private record ValidationResult(
        boolean isValid,
        ConceptDefinitionComponent concept,
        CodeSystem codeSystem,
        String display,
        ValidationErrorType errorType
    ) {}

    // 驗證上下文記錄類
    private record ValidationContext(
        String parameterSource,
        CodeType code,
        UriType system,
        StringType display,
        ValidationErrorType errorType,
        ValueSet valueSet
    ) {}

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
        String parameterSource
    ) {}

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

 // 改善 validateCodeInValueSet 以處理更複雜的組合邏輯
    private ValidationResult validateCodeInValueSet(ValueSet valueSet, CodeType code, UriType system,
                                                  StringType display, CodeType displayLanguage, 
                                                  BooleanType abstractAllowed) {
        
        if (valueSet == null || code == null) {
            return new ValidationResult(false, null, null, null, ValidationErrorType.INVALID_CODE);
        }

        // 優先檢查 expansion（如果存在且最新）
        if (valueSet.hasExpansion() && isExpansionCurrent(valueSet.getExpansion())) {
            ValidationResult expansionResult = validateCodeInExpansion(
                valueSet.getExpansion(), code, system, display, displayLanguage, abstractAllowed);
            if (expansionResult.isValid()) {
                return expansionResult;
            }
        }

        // 處理 compose 規則
        if (valueSet.hasCompose()) {
            return validateCodeInCompose(valueSet.getCompose(), code, system, display, 
                                       displayLanguage, abstractAllowed);
        }

        return new ValidationResult(false, null, null, null, ValidationErrorType.CODE_NOT_IN_VALUESET);
    }

    private boolean isExpansionCurrent(ValueSetExpansionComponent expansion) {
        // 檢查 expansion 是否為最新
        if (!expansion.hasTimestamp()) {
            return false;
        }
        // 可以根據業務需求設定過期時間
        return true; // 簡化實作
    }

    private ValidationResult validateCodeInCompose(ValueSetComposeComponent compose, CodeType code, 
                                                 UriType system, StringType display, 
                                                 CodeType displayLanguage, BooleanType abstractAllowed) {
        
        boolean foundInInclude = false;
        ValidationResult includeResult = null;
        
        // 檢查 include 部分
        for (ConceptSetComponent include : compose.getInclude()) {
            ValidationResult result = validateCodeInConceptSet(include, code, system, display, 
                                                             displayLanguage, abstractAllowed, true);
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
                                                             displayLanguage, abstractAllowed, false);
            if (result.isValid()) {
                // 在 exclude 中找到，表示該 code 被排除
                return new ValidationResult(false, result.concept(), result.codeSystem(), 
                                          result.display(), ValidationErrorType.CODE_NOT_IN_VALUESET);
            }
        }
        
        return includeResult;
    }

    // 在 ConceptSet 中驗證 code
    private ValidationResult validateCodeInConceptSet(ConceptSetComponent conceptSet, CodeType code, 
                                                    UriType system, StringType display, 
                                                    CodeType displayLanguage, BooleanType abstractAllowed,
                                                    boolean isInclude) {
        
        // 檢查 system 是否匹配
        if (system != null && conceptSet.hasSystem() && 
            !system.getValue().equals(conceptSet.getSystem())) {
            return new ValidationResult(false, null, null, null, ValidationErrorType.INVALID_CODE);
        }

        // 取得 CodeSystem
        CodeSystem codeSystem = null;
        String systemUrl = conceptSet.hasSystem() ? conceptSet.getSystem() : 
                          (system != null ? system.getValue() : null);
        
        if (systemUrl != null) {
            try {
                codeSystem = findCodeSystemByUrl(systemUrl, 
                    conceptSet.hasVersion() ? conceptSet.getVersion() : null);
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
    
 // 新增：處理 filter 條件的驗證
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

    // 新增：評估單個 filter 條件
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
                // 處理 CodeSystem 定義的自定義屬性
                return evaluateCustomPropertyFilter(property, op, value, concept);
        }
    }
    

	private boolean evaluateConceptFilter(String op, String value, ConceptDefinitionComponent concept, 
	                                    CodeSystem codeSystem) {
	    switch (op) {
	        case "is-a":
	            // 檢查是否為指定概念的子類
	            return isDescendantOf(concept, value, codeSystem);
	        case "descendent-of":
	            return isDescendantOf(concept, value, codeSystem) && !concept.getCode().equals(value);
	        case "is-not-a":
	            return !isDescendantOf(concept, value, codeSystem);
	        case "in":
	            // 檢查是否在指定的概念列表中
	            return isInConceptSet(concept.getCode(), value);
	        case "not-in":
	            return !isInConceptSet(concept.getCode(), value);
	        default:
	            return false;
	    }
	}
		
		
		private boolean isDescendantOf(ConceptDefinitionComponent concept, String parentCode, 
		                             CodeSystem codeSystem) {
		    // 檢查直接父子關係
		    for (ConceptPropertyComponent property : concept.getProperty()) {
		        if ("parent".equals(property.getCode()) && 
		            property.getValue() instanceof CodeType &&
		            parentCode.equals(((CodeType) property.getValue()).getValue())) {
		            return true;
		        }
		    }
		    
		    // 遞迴檢查層級結構
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
		    // 檢查直接子概念
		    for (ConceptDefinitionComponent child : parentConcept.getConcept()) {
		        if (child.getCode().equals(targetCode)) {
		            return true;
		        }
		        // 遞迴檢查
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
		            // value 應該是逗號分隔的代碼列表
		            return isInConceptSet(conceptCode, value);
		        case "not-in":
		            return !isInConceptSet(conceptCode, value);
		        default:
		            // 不支援的操作符，預設返回 false
		            return false;
		    }
		}

		private boolean evaluateStatusFilter(String op, String value, ConceptDefinitionComponent concept) {
		    // 從概念屬性中查找 status
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
		        conceptStatus = "active"; // 預設狀態
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
		    // 檢查 inactive 屬性
		    Boolean isInactive = null;
		    
		    for (ConceptPropertyComponent property : concept.getProperty()) {
		        if ("inactive".equals(property.getCode())) {
		            if (property.getValue() instanceof BooleanType) {
		                isInactive = ((BooleanType) property.getValue()).getValue();
		            }
		            break;
		        }
		    }
		    
		    // 如果沒有明確的 inactive 屬性，檢查 status
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
		        isInactive = false; // 預設為 active
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
		    // 查找自定義屬性
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
		    
		    // 處理逗號分隔的代碼列表
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
                // 檢查是否為抽象概念
                if (codeSystem != null) {
                    ConceptDefinitionComponent fullConcept = findConceptRecursive(
                        codeSystem.getConcept(), concept.getCode());
                    if (fullConcept != null) {
                        if (isAbstractConcept(fullConcept) && 
                            (abstractAllowed == null || !abstractAllowed.getValue())) {
                            return new ValidationResult(false, fullConcept, codeSystem, null, 
                                                      ValidationErrorType.ABSTRACT_CODE_NOT_ALLOWED);
                        }
                        
                        // 驗證 display
                        boolean isValidDisplay = isDisplayValidExtended(fullConcept, display, displayLanguage);
                        if (!isValidDisplay && display != null && !display.isEmpty()) {
                            return new ValidationResult(false, fullConcept, codeSystem, null, 
                                                      ValidationErrorType.INVALID_DISPLAY);
                        }
                        
                        String resolvedDisplay = getDisplayForLanguage(fullConcept, displayLanguage);
                        return new ValidationResult(true, fullConcept, codeSystem, resolvedDisplay, null);
                    }
                }
                
                // 如果找不到完整的概念定義，使用基本驗證
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

    // 在 expansion 中驗證
    private ValidationResult validateCodeInExpansion(ValueSetExpansionComponent expansion, 
                                                   CodeType code, UriType system, StringType display,
                                                   CodeType displayLanguage, BooleanType abstractAllowed) {
        
        for (ValueSetExpansionContainsComponent contains : expansion.getContains()) {
            ValidationResult result = validateCodeInExpansionContains(contains, code, system, display, 
                                                                    displayLanguage, abstractAllowed);
            if (result.isValid()) {
                return result;
            }
        }
        
        return new ValidationResult(false, null, null, null, ValidationErrorType.CODE_NOT_IN_VALUESET);
    }

    // 在 expansion contains 中驗證（遞歸處理嵌套結構）
    private ValidationResult validateCodeInExpansionContains(ValueSetExpansionContainsComponent contains,
                                                           CodeType code, UriType system, StringType display,
                                                           CodeType displayLanguage, BooleanType abstractAllowed) {
        
        // 檢查當前層級
        if (code.getValue().equals(contains.getCode()) && 
            (system == null || system.getValue().equals(contains.getSystem()))) {
            
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
        
        // 遞歸檢查子概念
        for (ValueSetExpansionContainsComponent child : contains.getContains()) {
            ValidationResult result = validateCodeInExpansionContains(child, code, system, display, 
                                                                    displayLanguage, abstractAllowed);
            if (result.isValid()) {
                return result;
            }
        }
        
        return new ValidationResult(false, null, null, null, ValidationErrorType.INVALID_CODE);
    }

    // 在 CodeSystem 中驗證 code
    private ValidationResult validateCodeInCodeSystem(CodeSystem codeSystem, CodeType code, 
                                                     StringType display, CodeType displayLanguage, 
                                                     BooleanType abstractAllowed) {
        
        ConceptDefinitionComponent concept = findConceptRecursive(codeSystem.getConcept(), code.getValue());
        if (concept == null) {
            return new ValidationResult(false, null, codeSystem, null, ValidationErrorType.INVALID_CODE);
        }

        // 檢查抽象概念
        if (isAbstractConcept(concept) && (abstractAllowed == null || !abstractAllowed.getValue())) {
            return new ValidationResult(false, concept, codeSystem, null, 
                                      ValidationErrorType.ABSTRACT_CODE_NOT_ALLOWED);
        }

        // 驗證 display
        boolean isValidDisplay = isDisplayValidExtended(concept, display, displayLanguage);
        if (!isValidDisplay && display != null && !display.isEmpty()) {
            return new ValidationResult(false, concept, codeSystem, null, 
                                      ValidationErrorType.INVALID_DISPLAY);
        }

        String resolvedDisplay = getDisplayForLanguage(concept, displayLanguage);
        return new ValidationResult(true, concept, codeSystem, resolvedDisplay, null);
    }

    // 檢查概念是否為抽象概念
    private boolean isAbstractConcept(ConceptDefinitionComponent concept) {
        // 檢查概念的屬性中是否有 abstract = true
        for (ConceptPropertyComponent property : concept.getProperty()) {
            if ("abstract".equals(property.getCode()) && 
                property.hasValueBooleanType() && 
                property.getValueBooleanType().getValue()) {
                return true;
            }
        }
        return false;
    }

    // 支援大小寫忽略與 designation 比對
    private boolean isDisplayValidExtended(ConceptDefinitionComponent concept, StringType display, 
                                         CodeType displayLanguage) {
        if (display == null || display.isEmpty()) {
            return true; // 沒傳 display 就直接 pass
        }

        String expected = getDisplayForLanguage(concept, displayLanguage);
        if (expected != null && display.getValue().equalsIgnoreCase(expected)) {
            return true;
        }

        // 檢查 designation
        for (ConceptDefinitionDesignationComponent desig : concept.getDesignation()) {
            if (display.getValue().equalsIgnoreCase(desig.getValue())) {
                return true;
            }
        }
        return false;
    }

    // 失敗回應加 OperationOutcome
    private Parameters buildValidationErrorWithOutcome(boolean isValid, ValidationContext context,
                                                      CodeSystem codeSystem, String errorMessage) {
        Parameters result = new Parameters();

        // 添加基本參數
        if (context.code() != null) {
            result.addParameter("code", context.code());
        }

        // 根據錯誤類型建立詳細訊息
        String detailedMessage = buildDetailedErrorMessage(context, codeSystem, errorMessage);

        // 建立 OperationOutcome
        OperationOutcome outcome = new OperationOutcome();
        var issue = outcome.addIssue();

        // 設定基本 issue 屬性
        issue.setSeverity(OperationOutcome.IssueSeverity.ERROR);
        issue.setCode(getIssueTypeForError(context.errorType()));

        // 添加適當的擴展
        issue.addExtension(new Extension(
            "http://hl7.org/fhir/StructureDefinition/operationoutcome-message-id",
            new StringType(getMessageIdForError(context.errorType()))
        ));

        // 設定 details
        CodeableConcept details = new CodeableConcept();
        details.addCoding(new Coding(
            "http://hl7.org/fhir/tools/CodeSystem/tx-issue-type",
            getDetailCodeForError(context.errorType()),
            null
        ));
        details.setText(detailedMessage);
        issue.setDetails(details);

        // 設定動態的 location 和 expression
        setLocationAndExpression(issue, context);

        // 添加 issues 參數
        result.addParameter().setName("issues").setResource(outcome);

        // 添加 message 參數
        result.addParameter("message", new StringType(detailedMessage));

        // 添加 result 參數
        result.addParameter("result", new BooleanType(isValid));

        // 添加 system 參數
        if (context.system() != null) {
            result.addParameter("system", context.system());
        }

        // 添加 ValueSet 參數
        if (context.valueSet() != null && context.valueSet().hasUrl()) {
            result.addParameter("valueSet", new UriType(context.valueSet().getUrl()));
        }

        return result;
    }

    // 根據錯誤類型建立詳細訊息
    private String buildDetailedErrorMessage(ValidationContext context, CodeSystem codeSystem, 
                                            String errorMessage) {
        switch (context.errorType()) {
            case INVALID_CODE:
                if (codeSystem != null && context.code() != null && context.system() != null) {
                    return String.format("Unknown code '%s' in the CodeSystem '%s'%s",
                        context.code().getValue(),
                        context.system().getValue(),
                        codeSystem.hasVersion() ? " version '" + codeSystem.getVersion() + "'" : "");
                }
                break;

            case CODE_NOT_IN_VALUESET:
                if (context.code() != null && context.valueSet() != null) {
                    return String.format("Code '%s' is not in the ValueSet '%s'",
                        context.code().getValue(),
                        context.valueSet().hasUrl() ? context.valueSet().getUrl() : "Unknown");
                }
                break;

            case ABSTRACT_CODE_NOT_ALLOWED:
                if (context.code() != null) {
                    return String.format("Code '%s' is abstract and not allowed in this context",
                        context.code().getValue());
                }
                break;

            case INVALID_DISPLAY:
                if (context.display() != null && context.code() != null) {
                    return String.format("Display '%s' is not valid for code '%s'",
                        context.display().getValue(),
                        context.code().getValue());
                }
                break;

            case SYSTEM_NOT_FOUND:
                if (context.system() != null) {
                    return String.format("CodeSystem '%s' not found", context.system().getValue());
                }
                break;
        }

        return errorMessage;
    }

    // 根據錯誤類型獲取 FHIR issue type
    private OperationOutcome.IssueType getIssueTypeForError(ValidationErrorType errorType) {
        switch (errorType) {
            case INVALID_CODE:
                return OperationOutcome.IssueType.CODEINVALID;
            case CODE_NOT_IN_VALUESET:
                return OperationOutcome.IssueType.CODEINVALID;
            case ABSTRACT_CODE_NOT_ALLOWED:
                return OperationOutcome.IssueType.BUSINESSRULE;
            case INVALID_DISPLAY:
                return OperationOutcome.IssueType.INVALID;
            case SYSTEM_NOT_FOUND:
                return OperationOutcome.IssueType.NOTFOUND;
            default:
                return OperationOutcome.IssueType.PROCESSING;
        }
    }

    // 根據錯誤類型獲取 message ID
    private String getMessageIdForError(ValidationErrorType errorType) {
        switch (errorType) {
            case INVALID_CODE:
                return "Unknown_Code_in_Version";
            case CODE_NOT_IN_VALUESET:
                return "Code_Not_In_ValueSet";
            case ABSTRACT_CODE_NOT_ALLOWED:
                return "Abstract_Code_Not_Allowed";
            case INVALID_DISPLAY:
                return "Invalid_Display";
            case SYSTEM_NOT_FOUND:
                return "CodeSystem_Not_Found";
            default:
                return "Validation_Error";
        }
    }

    // 根據錯誤類型獲取 detail code
    private String getDetailCodeForError(ValidationErrorType errorType) {
        switch (errorType) {
            case INVALID_CODE:
                return "invalid-code";
            case CODE_NOT_IN_VALUESET:
                return "not-in-vs";
            case ABSTRACT_CODE_NOT_ALLOWED:
                return "abstract-code";
            case INVALID_DISPLAY:
                return "invalid-display";
            case SYSTEM_NOT_FOUND:
                return "not-found";
            default:
                return "processing";
        }
    }

    private void setLocationAndExpression(OperationOutcome.OperationOutcomeIssueComponent issue, 
                                        ValidationContext context) {
        switch (context.errorType()) {
            case INVALID_CODE:
            case CODE_NOT_IN_VALUESET:
            case ABSTRACT_CODE_NOT_ALLOWED:
                if ("coding".equals(context.parameterSource())) {
                    issue.addLocation("coding.code");
                    issue.addExpression("coding.code");
                } else if (context.parameterSource().startsWith("codeableConcept")) {
                    String location = context.parameterSource() + ".code";
                    issue.addLocation(location);
                    issue.addExpression(location);
                } else {
                    issue.addLocation("code");
                    issue.addExpression("code");
                }
                break;

            case INVALID_DISPLAY:
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
                break;

            case SYSTEM_NOT_FOUND:
                if ("coding".equals(context.parameterSource())) {
                    issue.addLocation("coding.system");
                    issue.addExpression("coding.system");
                } else if (context.parameterSource().startsWith("codeableConcept")) {
                    String location = context.parameterSource() + ".system";
                    issue.addLocation(location);
                    issue.addExpression(location);
                } else {
                    issue.addLocation("system");
                    issue.addExpression("system");
                }
                break;

            default:
                // 預設情況
                issue.addLocation(context.parameterSource());
                issue.addExpression(context.parameterSource());
                break;
        }
    }
    
 // 修改 extractMultipleValidationParams 方法以支援解析後的系統參數
    private List<ValidationParams> extractMultipleValidationParams(CodeType code, UriType resolvedSystem, 
                                                                 StringType display, Coding coding, 
                                                                 CodeableConcept codeableConcept) {
        List<ValidationParams> paramsList = new ArrayList<>();

        if (coding != null) {
            // 對 Coding 中的 system，也需要支援 canonical
            UriType codingSystem = coding.getSystemElement();
            if (codingSystem == null || codingSystem.isEmpty()) {
                // 如果 Coding 沒有系統，使用解析後的系統
                codingSystem = resolvedSystem;
            }
            
            paramsList.add(new ValidationParams(
                coding.getCodeElement(),
                codingSystem,
                coding.getDisplayElement(),
                "coding"
            ));
        }

        if (codeableConcept != null && !codeableConcept.getCoding().isEmpty()) {
            for (int i = 0; i < codeableConcept.getCoding().size(); i++) {
                Coding c = codeableConcept.getCoding().get(i);
                UriType codingSystem = c.getSystemElement();
                if (codingSystem == null || codingSystem.isEmpty()) {
                    codingSystem = resolvedSystem;
                }
                
                paramsList.add(new ValidationParams(
                    c.getCodeElement(),
                    codingSystem,
                    c.getDisplayElement(),
                    "codeableConcept.coding[" + i + "]"
                ));
            }
        }

        if (code != null) {
            paramsList.add(new ValidationParams(code, resolvedSystem, display, "code"));
        }

        return paramsList;
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

    // 輔助方法：根據 ID 獲取 ValueSet
    private ValueSet getValueSetById(String id, StringType version) {
    	var searchParams = new SearchParameterMap();
        searchParams.add("_id", new TokenParam(id));
        
        if (version != null && !version.isEmpty()) {
            searchParams.add(ValueSet.SP_VERSION, new StringParam(version.getValue()));
        }
        
        var searchResult = myValueSetDao.search(searchParams, new SystemRequestDetails());
        
        if (searchResult.size() == 0) {
            throw new ResourceNotFoundException("ValueSet not found with ID: " + id);
        }
        
        return (ValueSet) searchResult.getResources(0, 1).get(0);
    }

    // 輔助方法：根據 URL 查找 ValueSet
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

    // 輔助方法：根據 URL 查找 CodeSystem
    private CodeSystem findCodeSystemByUrl(String url, String version) {
    	var searchParams = new SearchParameterMap();
        searchParams.add(CodeSystem.SP_URL, new UriParam(url));
        
        if (StringUtils.isNotBlank(version)) {
            searchParams.add(CodeSystem.SP_VERSION, new StringParam(version));
        }
        
        var searchResult = myCodeSystemDao.search(searchParams, new SystemRequestDetails());
        
        if (searchResult.size() == 0) {
            throw new ResourceNotFoundException(
                String.format("CodeSystem with URL '%s'%s not found", 
                    url, version != null ? " and version '" + version + "'" : ""));
        }
        
        return (CodeSystem) searchResult.getResources(0, 1).get(0);
    }

    // 輔助方法：遞歸查找概念
    private ConceptDefinitionComponent findConceptRecursive(List<ConceptDefinitionComponent> concepts, 
                                                           String code) {
        // 實現遞歸查找概念的邏輯
        for (ConceptDefinitionComponent concept : concepts) {
            if (code.equals(concept.getCode())) {
                return concept;
            }
            // 遞歸搜尋子概念
            ConceptDefinitionComponent found = findConceptRecursive(concept.getConcept(), code);
            if (found != null) {
                return found;
            }
        }
        return null;
    }
    
}