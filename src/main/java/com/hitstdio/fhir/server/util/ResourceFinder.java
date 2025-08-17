package com.hitstdio.fhir.server.util;

import ca.uhn.fhir.jpa.api.dao.IFhirResourceDao;
import ca.uhn.fhir.jpa.searchparam.SearchParameterMap;
import ca.uhn.fhir.rest.api.SortOrderEnum;
import ca.uhn.fhir.rest.api.SortSpec;
import ca.uhn.fhir.rest.api.server.IBundleProvider;
import ca.uhn.fhir.rest.api.server.RequestDetails;
import ca.uhn.fhir.rest.param.TokenParam;
import ca.uhn.fhir.rest.param.UriParam;
import ca.uhn.fhir.rest.server.exceptions.InvalidRequestException;
import ca.uhn.fhir.rest.server.exceptions.ResourceNotFoundException;
import org.hl7.fhir.r4.model.*;

import java.util.Map;

/**
 * Handles finding and retrieving FHIR resources (ValueSets and CodeSystems).
 */
public class ResourceFinder {
    
    private static final String PARAM_URL = "url";
    private static final String PARAM_VERSION = "version";
    
    private final IFhirResourceDao<ValueSet> valueSetDao;
    private final IFhirResourceDao<CodeSystem> codeSystemDao;
    
    public ResourceFinder(IFhirResourceDao<ValueSet> valueSetDao,
                         IFhirResourceDao<CodeSystem> codeSystemDao) {
        this.valueSetDao = valueSetDao;
        this.codeSystemDao = codeSystemDao;
    }
    
    /**
     * Finds a ValueSet by URL and optional version
     */
    public ValueSet findValueSetByUrl(String url, String version, RequestDetails requestDetails) {
        SearchParameterMap searchParams = new SearchParameterMap();
        searchParams.add(ValueSet.SP_URL, new UriParam(url));
        
        if (version != null && !version.isEmpty()) {
            searchParams.add(ValueSet.SP_VERSION, new TokenParam(version));
        } else {
            // Sort by last updated to get the most recent version
            searchParams.setSort(new SortSpec("_lastUpdated").setOrder(SortOrderEnum.DESC));
        }
        
        IBundleProvider bundle = valueSetDao.search(searchParams, requestDetails);
        
        if (bundle.isEmpty()) {
            String message = "ValueSet with URL '" + url + "'";
            if (version != null && !version.isEmpty()) {
                message += " and version '" + version + "'";
            }
            throw new ResourceNotFoundException(message + " not found.");
        }
        
        return (ValueSet) bundle.getResources(0, 1).get(0);
    }
    
    /**
     * Finds a ValueSet by canonical reference
     */
    public ValueSet findValueSetByCanonical(CanonicalType canonical, ExpansionRequest request) {
        if (canonical == null || !canonical.hasValue()) {
            return null;
        }
        
        String urlAndVersion = canonical.getValue();
        String[] parts = urlAndVersion.split("\\|", 2);
        
        if (parts.length == 0 || parts[0].trim().isEmpty()) {
            throw new InvalidRequestException("Invalid ValueSet canonical URL format: " + urlAndVersion);
        }
        
        String url = parts[0];
        String versionFromInclude = parts.length > 1 ? parts[1] : null;
        
        // Check for default version
        String finalVersion = versionFromInclude;
        if (finalVersion == null) {
            Map<String, String> defaultVersions = request.getDefaultValueSetVersions();
            if (defaultVersions.containsKey(url)) {
                finalVersion = defaultVersions.get(url);
            }
        }
        
        SearchParameterMap searchParams = new SearchParameterMap();
        searchParams.add(ValueSet.SP_URL, new UriParam(url));
        
        if (finalVersion != null) {
            searchParams.add(ValueSet.SP_VERSION, new TokenParam(finalVersion));
        } else {
            searchParams.setSort(new SortSpec("_lastUpdated").setOrder(SortOrderEnum.DESC));
        }
        
        IBundleProvider bundle = valueSetDao.search(searchParams, request.getRequestDetails());
        
        if (bundle.isEmpty()) {
            String diagnosticMessage = "Included ValueSet not found for URL: " + url + 
                (finalVersion != null ? " and version: " + finalVersion : "");
            
            OperationOutcome oo = new OperationOutcome();
            oo.addIssue()
                .setSeverity(OperationOutcome.IssueSeverity.ERROR)
                .setCode(OperationOutcome.IssueType.NOTFOUND)
                .setDetails(new CodeableConcept()
                    .addCoding(new Coding()
                        .setSystem("http://hl7.org/fhir/tools/CodeSystem/tx-issue-type")
                        .setCode("not-found"))
                    .setText("The ValueSet '" + url + (finalVersion != null ? "|" + finalVersion : "") + 
                            "' could not be found."));
            
            throw new ResourceNotFoundException(diagnosticMessage, oo);
        }
        
        return (ValueSet) bundle.getResources(0, 1).get(0);
    }
    
    /**
     * Finds a CodeSystem by URL and optional version
     */
    public CodeSystem findCodeSystem(String system, String version, ExpansionRequest request) {
        // Check tx-resources first
        Map<String, Resource> txResources = request.getTxResources();
        if (txResources.containsKey(system)) {
            return (CodeSystem) txResources.get(system);
        }
        
        // Search in database
        SearchParameterMap searchParams = new SearchParameterMap();
        searchParams.add(PARAM_URL, new UriParam(system));
        
        if (version != null && !version.trim().isEmpty()) {
            searchParams.add(PARAM_VERSION, new TokenParam(version));
        }
        
        IBundleProvider results = codeSystemDao.search(searchParams, request.getRequestDetails());
        return results.isEmpty() ? null : (CodeSystem) results.getResources(0, 1).get(0);
    }
    
    /**
     * Finds a concept within a CodeSystem by code
     */
    public CodeSystem.ConceptDefinitionComponent findConceptInCodeSystem(CodeSystem codeSystem, String code) {
        if (codeSystem == null || code == null) {
            return null;
        }
        
        for (CodeSystem.ConceptDefinitionComponent concept : codeSystem.getConcept()) {
            CodeSystem.ConceptDefinitionComponent found = findConceptRecursive(concept, code);
            if (found != null) {
                return found;
            }
        }
        return null;
    }
    
    /**
     * Recursively searches for a concept by code
     */
    private CodeSystem.ConceptDefinitionComponent findConceptRecursive(
            CodeSystem.ConceptDefinitionComponent concept, String code) {
        if (code.equals(concept.getCode())) {
            return concept;
        }
        
        for (CodeSystem.ConceptDefinitionComponent child : concept.getConcept()) {
            CodeSystem.ConceptDefinitionComponent found = findConceptRecursive(child, code);
            if (found != null) {
                return found;
            }
        }
        return null;
    }
}