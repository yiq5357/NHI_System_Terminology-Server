package com.hitstdio.fhir.server.provider;

import org.hl7.fhir.r4.model.Device;

import ca.uhn.fhir.jpa.api.dao.DaoRegistry;

public final class DeviceResourceProvider extends BaseResourceProvider<Device> {
	
    public DeviceResourceProvider(DaoRegistry theDaoRegistry) {
		super(theDaoRegistry);
	}

	@Override
    public Class<Device> getResourceType() {
        return Device.class;
    }

}