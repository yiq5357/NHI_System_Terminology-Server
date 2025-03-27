package com.hitstdio.fhir.server.provider;

import org.hl7.fhir.r4.model.DocumentReference;

import ca.uhn.fhir.jpa.api.dao.DaoRegistry;

public final class DocumentReferenceResourceProvider extends BaseResourceProvider<DocumentReference> {
	
    public DocumentReferenceResourceProvider(DaoRegistry theDaoRegistry) {
		super(theDaoRegistry);
	}

	@Override
    public Class<DocumentReference> getResourceType() {
        return DocumentReference.class;
    }

}