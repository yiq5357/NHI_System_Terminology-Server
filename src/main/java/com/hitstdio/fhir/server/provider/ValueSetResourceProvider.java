package com.hitstdio.fhir.server.provider;

import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.IdType;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.ValueSet;

import ca.uhn.fhir.jpa.api.dao.DaoRegistry;
import ca.uhn.fhir.rest.annotation.IdParam;
import ca.uhn.fhir.rest.annotation.Operation;
import ca.uhn.fhir.rest.annotation.OperationParam;
import ca.uhn.fhir.rest.param.DateRangeParam;

public final class ValueSetResourceProvider extends BaseResourceProvider<ValueSet> {
	
    public ValueSetResourceProvider(DaoRegistry theDaoRegistry) {
		super(theDaoRegistry);
	} 
    
	@Override
    public Class<ValueSet> getResourceType() {
        return ValueSet.class;
    }
    
}