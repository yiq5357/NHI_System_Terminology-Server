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
        Map<String, Resource> txResources = request.getTxResources();
        if (txResources.containsKey(system)) {
            Resource resource = txResources.get(system);
            if (resource instanceof CodeSystem) {
                return (CodeSystem) resource;
            }
        }

        SearchParameterMap searchParams = new SearchParameterMap();
        searchParams.add(PARAM_URL, new UriParam(system));

        // Check if version is a wildcard pattern (e.g., "1.x.x", "1.2.x")
        boolean isWildcard = version != null && !version.trim().isEmpty() &&
                            (version.contains("x") || version.contains("X"));

        if (version != null && !version.trim().isEmpty() && !isWildcard) {
            // Exact version match
            searchParams.add(PARAM_VERSION, new TokenParam(version));

            IBundleProvider results = codeSystemDao.search(searchParams, request.getRequestDetails());

            if (results.isEmpty()) {
                String diagnosticMessage = "CodeSystem not found for URL: " + system +
                    " and version: " + version;

                OperationOutcome oo = new OperationOutcome();
                OperationOutcome.OperationOutcomeIssueComponent issue = oo.addIssue();
                issue.setSeverity(OperationOutcome.IssueSeverity.ERROR);
                issue.setCode(OperationOutcome.IssueType.NOTFOUND);

                CodeableConcept details = new CodeableConcept();
                details.setText("Unable to find CodeSystem for canonical URL '" + system + "|" + version + "'");
                issue.setDetails(details);

                throw new ResourceNotFoundException(diagnosticMessage, oo);
            }

            return (CodeSystem) results.getResources(0, 1).get(0);
        }

        // For wildcards or no version: get all versions and select the highest by semantic version
        // Per FHIR spec: "Servers SHOULD use the CodeSystem.version, not lastUpdated, to determine the latest release."
        IBundleProvider results = codeSystemDao.search(searchParams, request.getRequestDetails());

        if (results.isEmpty()) {
            String diagnosticMessage = "CodeSystem not found for URL: " + system +
                (version != null ? " matching version pattern: " + version : "");

            OperationOutcome oo = new OperationOutcome();
            OperationOutcome.OperationOutcomeIssueComponent issue = oo.addIssue();
            issue.setSeverity(OperationOutcome.IssueSeverity.ERROR);
            issue.setCode(OperationOutcome.IssueType.NOTFOUND);

            CodeableConcept details = new CodeableConcept();
            details.setText("Unable to find CodeSystem for canonical URL '" + system +
                            (version != null ? "|" + version : "") + "'");
            issue.setDetails(details);

            throw new ResourceNotFoundException(diagnosticMessage, oo);
        }

        // Get all resources and find the one with the highest version
        java.util.List<org.hl7.fhir.instance.model.api.IBaseResource> allResources = results.getAllResources();
        CodeSystem bestMatch = null;
        String highestVersion = null;

        for (org.hl7.fhir.instance.model.api.IBaseResource baseResource : allResources) {
            if (baseResource instanceof CodeSystem) {
                CodeSystem cs = (CodeSystem) baseResource;

                // If wildcard pattern is specified, check if version matches
                if (isWildcard && cs.hasVersion() && !matchesVersionWildcard(cs.getVersion(), version)) {
                    continue;
                }

                // Find the CodeSystem with the highest semantic version
                if (cs.hasVersion()) {
                    if (highestVersion == null || compareVersions(cs.getVersion(), highestVersion) > 0) {
                        highestVersion = cs.getVersion();
                        bestMatch = cs;
                    }
                } else if (bestMatch == null) {
                    // If no version info, use as fallback only if no versioned CS found
                    bestMatch = cs;
                }
            }
        }

        if (bestMatch == null) {
            String diagnosticMessage = "No suitable CodeSystem found for URL: " + system +
                (isWildcard ? " matching version pattern: " + version : "");

            OperationOutcome oo = new OperationOutcome();
            OperationOutcome.OperationOutcomeIssueComponent issue = oo.addIssue();
            issue.setSeverity(OperationOutcome.IssueSeverity.ERROR);
            issue.setCode(OperationOutcome.IssueType.NOTFOUND);

            CodeableConcept details = new CodeableConcept();
            details.setText("Unable to find CodeSystem for canonical URL '" + system +
                            (version != null ? "|" + version : "") + "'");
            issue.setDetails(details);

            throw new ResourceNotFoundException(diagnosticMessage, oo);
        }

        return bestMatch;
    }

    /**
     * Checks if a version string matches a wildcard pattern
     * e.g., "1.2.0" matches "1.x.x" or "1.2.x"
     */
    private boolean matchesVersionWildcard(String actualVersion, String wildcardPattern) {
        if (actualVersion == null || wildcardPattern == null) {
            return false;
        }

        String[] actualParts = actualVersion.split("\\.");
        String[] patternParts = wildcardPattern.split("\\.");

        // Match each part
        int minLength = Math.min(actualParts.length, patternParts.length);
        for (int i = 0; i < minLength; i++) {
            String patternPart = patternParts[i].toLowerCase();
            if (!patternPart.equals("x") && !patternPart.equals(actualParts[i])) {
                return false;
            }
        }

        return true;
    }

    /**
     * Compares two version strings (e.g., "1.2.0" vs "1.0.0")
     * Returns positive if v1 > v2, negative if v1 < v2, 0 if equal
     */
    private int compareVersions(String v1, String v2) {
        if (v1 == null || v2 == null) {
            return 0;
        }

        String[] parts1 = v1.split("\\.");
        String[] parts2 = v2.split("\\.");
        int maxLength = Math.max(parts1.length, parts2.length);

        for (int i = 0; i < maxLength; i++) {
            int num1 = i < parts1.length ? parseVersionPart(parts1[i]) : 0;
            int num2 = i < parts2.length ? parseVersionPart(parts2[i]) : 0;

            if (num1 != num2) {
                return num1 - num2;
            }
        }

        return 0;
    }

    /**
     * Parses a version part to integer, handling non-numeric suffixes
     * e.g., "1" -> 1, "1-beta" -> 1
     */
    private int parseVersionPart(String part) {
        try {
            // Extract numeric prefix
            StringBuilder numStr = new StringBuilder();
            for (char c : part.toCharArray()) {
                if (Character.isDigit(c)) {
                    numStr.append(c);
                } else {
                    break;
                }
            }
            return numStr.length() > 0 ? Integer.parseInt(numStr.toString()) : 0;
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /**
     * Finds all CodeSystems that supplement the given system URL
     */
    public java.util.List<CodeSystem> findSupplementsForSystem(String systemUrl, ExpansionRequest request) {
        java.util.List<CodeSystem> supplements = new java.util.ArrayList<>();

        // First check tx-resources for supplements
        Map<String, Resource> txResources = request.getTxResources();
        for (Resource resource : txResources.values()) {
            if (resource instanceof CodeSystem) {
                CodeSystem cs = (CodeSystem) resource;
                if (cs.hasSupplements() && systemUrl.equals(cs.getSupplements())) {
                    supplements.add(cs);
                }
            }
        }

        // Then search the database for supplements
        SearchParameterMap searchParams = new SearchParameterMap();
        searchParams.add("supplements", new UriParam(systemUrl));

        try {
            IBundleProvider results = codeSystemDao.search(searchParams, request.getRequestDetails());
            if (!results.isEmpty()) {
                java.util.List<org.hl7.fhir.instance.model.api.IBaseResource> resources = results.getAllResources();
                for (org.hl7.fhir.instance.model.api.IBaseResource baseResource : resources) {
                    if (baseResource instanceof CodeSystem) {
                        CodeSystem cs = (CodeSystem) baseResource;
                        // Avoid duplicates from tx-resources
                        boolean alreadyIncluded = supplements.stream()
                            .anyMatch(existing -> existing.getUrl().equals(cs.getUrl()) &&
                                     java.util.Objects.equals(existing.getVersion(), cs.getVersion()));
                        if (!alreadyIncluded) {
                            supplements.add(cs);
                        }
                    }
                }
            }
        } catch (Exception e) {
            // If search fails (e.g., supplements search parameter not supported),
            // just return what we found in tx-resources
        }

        return supplements;
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