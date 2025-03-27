package com.hitstdio.fhir.server.provider;

import org.hl7.fhir.r4.model.AllergyIntolerance;
import org.hl7.fhir.r4.model.ConceptMap;
import org.hl7.fhir.r4.model.ValueSet;

import ca.uhn.fhir.jpa.api.dao.DaoRegistry;

public final class ConceptMapResourceProvider extends BaseResourceProvider<ConceptMap> {
	
    public ConceptMapResourceProvider(DaoRegistry theDaoRegistry) {
		super(theDaoRegistry);
	}

	@Override
    public Class<ConceptMap> getResourceType() {
        return ConceptMap.class;
    }

}