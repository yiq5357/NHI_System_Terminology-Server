package com.hitstdio.fhir.server.provider;

import java.util.Date;

import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.TerminologyCapabilities;
import org.hl7.fhir.r4.model.Enumerations;

import ca.uhn.fhir.jpa.api.dao.DaoRegistry;
import ca.uhn.fhir.rest.annotation.Metadata;
import ca.uhn.fhir.rest.annotation.Operation;
import ca.uhn.fhir.rest.annotation.OperationParam;
import ca.uhn.fhir.rest.api.server.RequestDetails;
import ca.uhn.fhir.rest.server.RestfulServer;

public class TerminologyCapabilitiesResourceProvider extends BaseResourceProvider<TerminologyCapabilities> {

	public TerminologyCapabilitiesResourceProvider(DaoRegistry theDaoRegistry) {
		super(theDaoRegistry);
	}
	
	/*@Operation(name = "$metadata", idempotent = true)
    public TerminologyCapabilities getTerminologyCapabilities(@OperationParam(name = "mode") String mode) {*/
		
	//@Metadata
	@Operation(name = "$terminology-capabilities", idempotent = true, returnParameters = {})
	public TerminologyCapabilities getTerminologyCapabilities(RequestDetails requestDetails) {
        
		//if (mode != null && mode.equals("terminology")) {
		TerminologyCapabilities terminologyCapabilities = new TerminologyCapabilities();
        
        // 設置必要屬性
        terminologyCapabilities.setStatus(Enumerations.PublicationStatus.ACTIVE);
        terminologyCapabilities.setDate(new Date());
        terminologyCapabilities.setPublisher("Your Organization");
        
        // 添加術語能力描述
        terminologyCapabilities.addCodeSystem()
            .setUri("http://loinc.org");
        
        // 其他配置...
        
        return terminologyCapabilities;
		
		//return null; // 如果不是 terminology 模式，返回 null 讓其他處理器處理
    }
	
	@Override
    public Class<TerminologyCapabilities> getResourceType() {
        return TerminologyCapabilities.class;
    }
}
