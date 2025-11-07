package com.hitstdio.fhir.server.util;

import org.hl7.fhir.r4.model.*;

public record ValidationContext (
	String parameterSource,
    CodeType code,
    UriType system,
    StringType display,
    ValidationErrorType errorType,
    ValueSet valueSet,
    StringType systemVersion,
    Boolean isInactive,
    CodeableConcept originalCodeableConcept
    //boolean systemIsValueSet
) {}
