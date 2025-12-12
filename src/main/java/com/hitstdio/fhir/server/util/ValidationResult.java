package com.hitstdio.fhir.server.util;

import java.util.List;

import org.hl7.fhir.r4.model.*;
import org.hl7.fhir.r4.model.CodeSystem.ConceptDefinitionComponent;

import com.hitstdio.fhir.server.provider.ValueSetResourceProvider.CodeSystemVersionNotFoundException;

public record ValidationResult(
	    boolean isValid,
	    ConceptDefinitionComponent concept,
	    CodeSystem codeSystem,
	    String display,
	    ValidationErrorType errorType,
	    Boolean isInactive,
	    CodeSystemVersionNotFoundException versionException,
	    List<String> missingValueSets
	) {
	// 為了向後兼容，提供靜態工廠方法
    public static ValidationResult of(boolean isValid, ConceptDefinitionComponent concept, 
                                     CodeSystem codeSystem, String display, 
                                     ValidationErrorType errorType, Boolean isInactive) {
        return new ValidationResult(isValid, concept, codeSystem, display, errorType, isInactive, null, null);
    }
    
    // 提供包含 versionException 的靜態工廠方法
    public static ValidationResult withVersionException(boolean isValid, ConceptDefinitionComponent concept, 
                                                       CodeSystem codeSystem, String display, 
                                                       ValidationErrorType errorType, Boolean isInactive,
                                                       CodeSystemVersionNotFoundException versionException) {
        return new ValidationResult(isValid, concept, codeSystem, display, errorType, isInactive, versionException, null);
    }
    
    // 提供包含 missingValueSets 的靜態工廠方法
    public static ValidationResult withMissingValueSets(boolean isValid, ConceptDefinitionComponent concept, 
                                                       CodeSystem codeSystem, String display, 
                                                       ValidationErrorType errorType, Boolean isInactive,
                                                       CodeSystemVersionNotFoundException versionException,
                                                       List<String> missingValueSets) {
        return new ValidationResult(isValid, concept, codeSystem, display, errorType, isInactive, versionException, missingValueSets);
    }
}
