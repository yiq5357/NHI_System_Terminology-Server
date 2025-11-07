package com.hitstdio.fhir.server.util;

import org.hl7.fhir.r4.model.*;

public record ValidationParams(
	    CodeType code,
	    UriType system,
	    StringType display,
	    String parameterSource,
	    Coding originalCoding,
	    CodeableConcept originalCodeableConcept
) {}
