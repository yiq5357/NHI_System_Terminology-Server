package com.hitstdio.fhir.server.provider;

import org.hl7.fhir.r4.model.Composition;

import ca.uhn.fhir.jpa.api.dao.DaoRegistry;


public final class CompositionResourceProvider extends BaseResourceProvider<Composition> {

    public CompositionResourceProvider(DaoRegistry theDaoRegistry) {
		super(theDaoRegistry);
	}

	@Override
    public Class<Composition> getResourceType() {
        return Composition.class;
    }

}