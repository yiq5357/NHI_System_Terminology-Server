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
	// 為了向後兼容，提供原有的建構函數
    public ValidationResult(boolean isValid, ConceptDefinitionComponent concept, 
                          CodeSystem codeSystem, String display, 
                          ValidationErrorType errorType, Boolean isInactive) {
        this(isValid, concept, codeSystem, display, errorType, isInactive, null, null);
    }
    
    public ValidationResult(boolean isValid, ConceptDefinitionComponent concept, 
                          CodeSystem codeSystem, String display, 
                          ValidationErrorType errorType, Boolean isInactive,
                          CodeSystemVersionNotFoundException versionException) {
        this(isValid, concept, codeSystem, display, errorType, isInactive, versionException, null);
    }
  }
