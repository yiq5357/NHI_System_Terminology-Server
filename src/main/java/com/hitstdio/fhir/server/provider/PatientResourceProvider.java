package com.hitstdio.fhir.server.provider;

import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.IdType;
import org.hl7.fhir.r4.model.Patient;
import ca.uhn.fhir.jpa.api.dao.DaoRegistry;
import ca.uhn.fhir.rest.annotation.IdParam;
import ca.uhn.fhir.rest.annotation.Operation;
import ca.uhn.fhir.rest.annotation.OperationParam;
import ca.uhn.fhir.rest.param.DateRangeParam;

public final class PatientResourceProvider extends BaseResourceProvider<Patient> {
	
    public PatientResourceProvider(DaoRegistry theDaoRegistry) {
		super(theDaoRegistry);
	}

    @Operation(name = "$everything", idempotent = true)
    public Bundle patientTypeOperation() {

       Bundle retVal = new Bundle();
       // Populate bundle with matching resources
       return retVal;
    }    
    
    @Operation(name = "$everything", idempotent = true)
    public Bundle patientInstanceOperation(
          @IdParam IdType thePatientId,
          @OperationParam(name = "start") DateRangeParam theStart,
          @OperationParam(name = "end") DateRangeParam theEnd) {

       Bundle retVal = new Bundle();
       // Populate bundle with matching resources
       return retVal;
    }    
    
	@Override
    public Class<Patient> getResourceType() {
        return Patient.class;
    }
    
}