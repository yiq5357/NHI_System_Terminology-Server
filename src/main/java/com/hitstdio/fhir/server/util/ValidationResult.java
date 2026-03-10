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
	    List<String> missingValueSets,
	    String normalizedCode
	) {
	// Backward compatibility: static factory method
    public static ValidationResult of(boolean isValid, ConceptDefinitionComponent concept,
                                     CodeSystem codeSystem, String display,
                                     ValidationErrorType errorType, Boolean isInactive) {
        return new ValidationResult(isValid, concept, codeSystem, display, errorType, isInactive, null, null, null);
    }

    // Static factory with versionException
    public static ValidationResult withVersionException(boolean isValid, ConceptDefinitionComponent concept,
                                                       CodeSystem codeSystem, String display,
                                                       ValidationErrorType errorType, Boolean isInactive,
                                                       CodeSystemVersionNotFoundException versionException) {
        return new ValidationResult(isValid, concept, codeSystem, display, errorType, isInactive, versionException, null, null);
    }

    // Static factory with missingValueSets
    public static ValidationResult withMissingValueSets(boolean isValid, ConceptDefinitionComponent concept,
                                                       CodeSystem codeSystem, String display,
                                                       ValidationErrorType errorType, Boolean isInactive,
                                                       CodeSystemVersionNotFoundException versionException,
                                                       List<String> missingValueSets) {
        return new ValidationResult(isValid, concept, codeSystem, display, errorType, isInactive, versionException, missingValueSets, null);
    }
}
