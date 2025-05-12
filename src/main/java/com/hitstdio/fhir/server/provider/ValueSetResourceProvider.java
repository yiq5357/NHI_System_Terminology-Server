package com.hitstdio.fhir.server.provider;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.hl7.fhir.instance.model.api.IIdType;
import org.hl7.fhir.r4.model.CodeSystem;
import org.hl7.fhir.r4.model.DateType;
import org.hl7.fhir.r4.model.StringType;
import org.hl7.fhir.r4.model.UriType;
import org.hl7.fhir.r4.model.ValueSet;

import ca.uhn.fhir.jpa.api.dao.DaoRegistry;
import ca.uhn.fhir.jpa.api.dao.IFhirResourceDao;
import ca.uhn.fhir.jpa.searchparam.SearchParameterMap;
import ca.uhn.fhir.rest.annotation.Operation;
import ca.uhn.fhir.rest.annotation.OperationParam;
import ca.uhn.fhir.rest.api.server.IBundleProvider;
import ca.uhn.fhir.rest.api.server.RequestDetails;
import ca.uhn.fhir.rest.param.UriParam;
import ca.uhn.fhir.rest.server.exceptions.InvalidRequestException;
import ca.uhn.fhir.rest.server.exceptions.ResourceNotFoundException;
import ca.uhn.fhir.rest.server.exceptions.UnprocessableEntityException;
import ca.uhn.fhir.rest.param.TokenParam;


public final class ValueSetResourceProvider extends BaseResourceProvider<ValueSet> {
	
	private final IFhirResourceDao<ValueSet> valueSetDao;
	private final IFhirResourceDao<CodeSystem> codeSystemDao;

	public ValueSetResourceProvider(DaoRegistry theDaoRegistry) {
	    super(theDaoRegistry);
	    this.valueSetDao = theDaoRegistry.getResourceDao(ValueSet.class);
	    this.codeSystemDao = theDaoRegistry.getResourceDao(CodeSystem.class);
	}

    @Operation(name = "$expand", idempotent = true)
    public ValueSet expandValueSet(
            @OperationParam(name = "url") UriType theUrl,
            @OperationParam(name = "valueSetVersion") StringType theValueSetVersion, 
            @OperationParam(name = "filter") StringType theFilter, 
            @OperationParam(name = "date") DateType theDate,
            @OperationParam(name = "system-version") List<UriType> theSystemVersion,
            @OperationParam(name = "check-system-version") List<UriType> theCheckSystemVersion,
            @OperationParam(name = "force-system-version") List<UriType> theForceSystemVersion,            
            RequestDetails requestDetails) {
        
        ValueSet valueSet = retrieveValueSet(theUrl, requestDetails);

        Map<String, String> systemVersionMap = parseSystemVersions(theSystemVersion);
        Map<String, String> checkSystemVersionMap = parseSystemVersions(theCheckSystemVersion);
        Map<String, String> forceSystemVersionMap = parseSystemVersions(theForceSystemVersion);        
        
        ValueSet result = new ValueSet();
        result.setMeta(valueSet.getMeta());
        result.setUrl(valueSet.getUrl());
        result.setIdentifier(valueSet.getIdentifier());
        result.setVersion(valueSet.getVersion());
        result.setName(valueSet.getName());
        result.setTitle(valueSet.getTitle());
        result.setStatus(valueSet.getStatus());
        result.setExperimental(valueSet.getExperimental());
        result.setDate(valueSet.getDate());
        result.setImmutable(valueSet.getImmutable());
        
        ValueSet.ValueSetExpansionComponent expansion = new ValueSet.ValueSetExpansionComponent();
        result.setExpansion(expansion);
        expansion.setTimestamp(new Date());
        expansion.setIdentifier("urn:uuid:" + UUID.randomUUID().toString());
        
        String displayLanguage = requestDetails.getHeader("Accept-Language");
        if (displayLanguage != null) {
            expansion.addParameter().setName("displayLanguage").setValue(new StringType(displayLanguage));
        }
        
        for (Map.Entry<String, String> entry : forceSystemVersionMap.entrySet()) {
            expansion.addParameter().setName("force-system-version")
                .setValue(new UriType(entry.getKey() + "|" + entry.getValue()));
        } 
        
        for (ValueSet.ConceptSetComponent include : valueSet.getCompose().getInclude()) {
            String system = include.getSystem();
            String version = systemVersion(system, include.getVersion(), 
                    systemVersionMap, checkSystemVersionMap, forceSystemVersionMap);

            CodeSystem codeSystem = findCodeSystem(system, version, requestDetails); 
            
            if (version == null) {
            	version = codeSystem.getVersion();
            } 
            
            expansion.addParameter().setName("used-codesystem")
            .setValue(new UriType(system + (version != null ? "|" + version : "")));
            
            if (version != null) {
                expansion.addParameter().setName("version")
                    .setValue(new UriType(system + "|" + version));
            }
            
            if (codeSystem != null) {
                addCodesFromCodeSystem(expansion, codeSystem, include, theFilter);
            }
        }
        
        expansion.setTotal(expansion.getContains().size());
        
        return result;
    }

    private Map<String, String> parseSystemVersions(List<UriType> systemVersions) {
        Map<String, String> result = new HashMap<>();
        if (systemVersions != null) {
            for (UriType systemVersion : systemVersions) {
                String value = systemVersion.getValueAsString();
                if (value != null && value.contains("|")) {
                    String[] parts = value.split("\\|", 2);
                    result.put(parts[0], parts[1]);
                }
            }
        }
        return result;
    }   

    private String systemVersion(String system, String includeVersion, 
            Map<String, String> systemVersionMap, 
            Map<String, String> checkSystemVersionMap,
            Map<String, String> forceSystemVersionMap) {
        
        if (forceSystemVersionMap.containsKey(system)) {
            return forceSystemVersionMap.get(system);
        }
        
        if (checkSystemVersionMap.containsKey(system)) {
            String expectedVersion = checkSystemVersionMap.get(system);
            if (includeVersion != null && !includeVersion.equals(expectedVersion)) {
                throw new UnprocessableEntityException(
                    "ValueSet specifies version '" + includeVersion + "' for system '" + system + 
                    "', but check-system-version requires version '" + expectedVersion + "'");
            }
            return expectedVersion;
        }
        
        if (includeVersion != null) {
            return includeVersion;
        }
        
        if (systemVersionMap.containsKey(system)) {
            return systemVersionMap.get(system);
        }
        return null;
    }
    
    private ValueSet retrieveValueSet(UriType theUrl, RequestDetails requestDetails) {
        if (theUrl == null && requestDetails.getId() == null) {
            throw new InvalidRequestException("Either 'url' parameter or ValueSet ID must be provided");
        }

        if (theUrl != null) {
            IBundleProvider bundle = valueSetDao.search(
                new SearchParameterMap().add("url", new UriParam(theUrl.getValueAsString())), requestDetails);
            if (bundle.isEmpty()) {
                throw new ResourceNotFoundException("ValueSet with URL " + theUrl.getValueAsString() + " not found");
            }
            return (ValueSet) bundle.getResources(0, 1).get(0);
        } else {
            IIdType id = requestDetails.getId();
            return valueSetDao.read(id, requestDetails);
        }
    }

    private CodeSystem findCodeSystem(String system, String version, RequestDetails requestDetails) {
        SearchParameterMap params = new SearchParameterMap().add("url", new UriParam(system));
        if (version != null) {
        	params.add("version", new TokenParam(version));
        }
        
        IBundleProvider results = codeSystemDao.search(params, requestDetails);
        if (!results.isEmpty()) {
            return (CodeSystem) results.getResources(0, 1).get(0);
        }
        return null;
    }

    private void addCodesFromCodeSystem(ValueSet.ValueSetExpansionComponent expansion, 
                                       CodeSystem codeSystem, 
                                       ValueSet.ConceptSetComponent include,
                                       StringType filter) {
        if (!include.getConcept().isEmpty()) {
            for (ValueSet.ConceptReferenceComponent concept : include.getConcept()) {
                if (matchesFilter(concept.getCode(), concept.getDisplay(), filter)) {
                    addCodeToExpansion(expansion, include.getSystem(), concept.getCode(), concept.getDisplay());
                }
            }
        } else {
            for (CodeSystem.ConceptDefinitionComponent concept : codeSystem.getConcept()) {
                processCodeSystemConcept(expansion, include.getSystem(), concept, filter);
            }
        }
    }

    private void processCodeSystemConcept(ValueSet.ValueSetExpansionComponent expansion, 
                                         String system, 
                                         CodeSystem.ConceptDefinitionComponent concept, 
                                         StringType filter) {
        if (matchesFilter(concept.getCode(), concept.getDisplay(), filter)) {
            addCodeToExpansion(expansion, system, concept.getCode(), concept.getDisplay());
        }
        
        for (CodeSystem.ConceptDefinitionComponent child : concept.getConcept()) {
            processCodeSystemConcept(expansion, system, child, filter);
        }
    }

    //簡單的比對，日後再新增Fuzzy
    private boolean matchesFilter(String code, String display, StringType filter) {
        if (filter == null || filter.getValue() == null || filter.getValue().isEmpty()) {
            return true;
        }
        
        String filterStr = filter.getValue().toLowerCase();
        return (code != null && code.toLowerCase().contains(filterStr)) ||
               (display != null && display.toLowerCase().contains(filterStr));
    }

    private void addCodeToExpansion(ValueSet.ValueSetExpansionComponent expansion, 
                                   String system, String code, String display) {
        ValueSet.ValueSetExpansionContainsComponent component = new ValueSet.ValueSetExpansionContainsComponent();
        component.setSystem(system);
        component.setCode(code);
        component.setDisplay(display);
        expansion.addContains(component);
    }    
    
	@Override
    public Class<ValueSet> getResourceType() {
        return ValueSet.class;
    }

}