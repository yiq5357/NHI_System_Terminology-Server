package com.hitstdio.fhir.server.provider;

import org.hl7.fhir.r4.model.Condition;

import ca.uhn.fhir.jpa.api.dao.DaoRegistry;

public final class ConditionResourceProvider extends BaseResourceProvider<Condition> {
	
    public ConditionResourceProvider(DaoRegistry theDaoRegistry) {
		super(theDaoRegistry);
	}

	@Override
    public Class<Condition> getResourceType() {
        return Condition.class;
    }

}