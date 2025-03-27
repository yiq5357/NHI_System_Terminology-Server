package com.hitstdio.fhir.server.provider;

import org.hl7.fhir.r4.model.Organization;

import ca.uhn.fhir.jpa.api.dao.DaoRegistry;

public final class OrganizationResourceProvider extends BaseResourceProvider<Organization> {

    public OrganizationResourceProvider(DaoRegistry theDaoRegistry) {
		super(theDaoRegistry);
	}

	@Override
    public Class<Organization> getResourceType() {
        return Organization.class;
    }

}