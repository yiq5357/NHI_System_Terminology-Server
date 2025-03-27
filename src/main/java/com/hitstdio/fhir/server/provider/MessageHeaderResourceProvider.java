package com.hitstdio.fhir.server.provider;

import org.hl7.fhir.r4.model.MessageHeader;

import ca.uhn.fhir.jpa.api.dao.DaoRegistry;

public final class MessageHeaderResourceProvider extends BaseResourceProvider<MessageHeader> {
	
    public MessageHeaderResourceProvider(DaoRegistry theDaoRegistry) {
		super(theDaoRegistry);
	}

	@Override
    public Class<MessageHeader> getResourceType() {
        return MessageHeader.class;
    }
    
}