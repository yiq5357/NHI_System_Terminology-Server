package com.hitstdio.fhir.server.util;

import org.hl7.fhir.r4.model.*;
import org.hl7.fhir.r4.model.CodeSystem.ConceptDefinitionComponent;

public record ValidationResult(
	    boolean isValid,
	    ConceptDefinitionComponent concept,
	    CodeSystem codeSystem,
	    String display,
	    ValidationErrorType errorType,
	    Boolean isInactive
) {}
