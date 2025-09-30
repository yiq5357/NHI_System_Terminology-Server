package com.hitstdio.fhir.server.util;

import org.hl7.fhir.r4.model.*;
import org.hl7.fhir.r4.model.ValueSet.*;
import org.hl7.fhir.utilities.Utilities;
import ca.uhn.fhir.rest.server.exceptions.UnprocessableEntityException;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Collects concepts from ValueSets and CodeSystems based on compose
 * definitions.
 */
public class ConceptCollector {

	private final ResourceFinder resourceFinder;
	private final ConceptFilter conceptFilter;
	private final ConceptComponentBuilder componentBuilder;

	public ConceptCollector(ResourceFinder resourceFinder, ConceptFilter conceptFilter) {
		this.resourceFinder = resourceFinder;
		this.conceptFilter = conceptFilter;
		this.componentBuilder = new ConceptComponentBuilder(conceptFilter);
	}	
	
	/**
	 * Collects all concepts from a ValueSet based on its compose definition
	 */
    public List<ValueSetExpansionContainsComponent> collectAllConcepts(
            ValueSet sourceValueSet,
            ValueSetExpansionComponent expansion,
            ExpansionRequest request) {
        
        if (!sourceValueSet.hasCompose()) {
            return Collections.emptyList();
        }
        return collectAllConceptsRecursive(sourceValueSet, expansion, request, new HashSet<>());
    }	
	
	private List<ValueSetExpansionContainsComponent> collectAllConceptsRecursive(ValueSet sourceValueSet,
			ValueSetExpansionComponent expansion, ExpansionRequest request, Set<String> expansionChain) {

		String canonicalUrl = sourceValueSet.getUrl() + "|" + sourceValueSet.getVersion();

		if (expansionChain.contains(canonicalUrl)) {
			OperationOutcome oo = new OperationOutcome();
			oo.addIssue().setSeverity(OperationOutcome.IssueSeverity.ERROR)
					.setCode(OperationOutcome.IssueType.PROCESSING)
					.setDetails(new CodeableConcept().setText("Cyclic ValueSet inclusion detected: " + canonicalUrl
							+ " is already in the expansion chain."));
			throw new UnprocessableEntityException("Cyclic ValueSet reference", oo);
		}

        try {
            expansionChain.add(canonicalUrl);

            List<ValueSetExpansionContainsComponent> includedCodes = new ArrayList<>();
            processIncludes(sourceValueSet, expansion, request, includedCodes, expansionChain);
            processExcludes(sourceValueSet, expansion, request, includedCodes, expansionChain);
            
            return includedCodes;

        } finally {
            expansionChain.remove(canonicalUrl);
        }
    }

	/**
	 * Processes the include section of ValueSet compose
	 */
	private void processIncludes(ValueSet sourceValueSet, ValueSetExpansionComponent expansion,
			ExpansionRequest request, List<ValueSetExpansionContainsComponent> includedCodes,
			Set<String> expansionChain) {

		Set<String> excludedSystems = getExcludedSystems(request.getExcludeSystem());
		Map<String, String> systemVersionMap = parseSystemVersions(request.getSystemVersion());
		Map<String, String> checkSystemVersionMap = parseSystemVersions(request.getCheckSystemVersion());
		Map<String, String> forceSystemVersionMap = parseSystemVersions(request.getForceSystemVersion());

		List<ConceptSetComponent> includes = sourceValueSet.getCompose().getInclude();

		for (int i = 0; i < includes.size(); i++) {
			ConceptSetComponent include = includes.get(i);
			validateFilters(include, i, sourceValueSet);

			// Process system-based includes
			if (include.hasSystem()) {
				processSystemInclude(include, sourceValueSet, expansion, request, includedCodes, excludedSystems,
						systemVersionMap, checkSystemVersionMap, forceSystemVersionMap);
			}

			// Process ValueSet-based includes
			if (include.hasValueSet()) {
				processValueSetInclude(include, expansion, request, includedCodes, expansionChain);
			}
		}
	}

	/**
	 * Processes a system-based include
	 */
	private void processSystemInclude(ConceptSetComponent include, ValueSet sourceValueSet,
			ValueSetExpansionComponent expansion, ExpansionRequest request,
			List<ValueSetExpansionContainsComponent> includedCodes, Set<String> excludedSystems,
			Map<String, String> systemVersionMap, Map<String, String> checkSystemVersionMap,
			Map<String, String> forceSystemVersionMap) {

		String systemUrl = include.getSystem();
		if (excludedSystems.contains(systemUrl)) {
			return;
		}

		String version = determineSystemVersion(systemUrl, include.getVersion(), systemVersionMap,
				checkSystemVersionMap, forceSystemVersionMap);

		CodeSystem codeSystem = resourceFinder.findCodeSystem(systemUrl, version, request);

		if (codeSystem != null) {
			addSystemParameters(expansion, codeSystem, systemUrl, version);
			addCodesFromCodeSystem(includedCodes, sourceValueSet, codeSystem, include, request);
		}
	}

	/**
	 * Processes a ValueSet-based include
	 */
	private void processValueSetInclude(ConceptSetComponent include, ValueSetExpansionComponent expansion,
			ExpansionRequest request, List<ValueSetExpansionContainsComponent> includedCodes,
			Set<String> expansionChain) {

		for (CanonicalType valueSetToInclude : include.getValueSet()) {
			ValueSet importedVs = resourceFinder.findValueSetByCanonical(valueSetToInclude, request);

			if (importedVs != null && importedVs.hasUrl() && importedVs.hasVersion()) {
				CanonicalType resolvedCanonical = new CanonicalType(
						importedVs.getUrl() + "|" + importedVs.getVersion());

				expansion.addParameter().setName("used-valueset").setValue(resolvedCanonical.copy());

				// 移除 version 參數
				// expansion.addParameter().setName("version").setValue(resolvedCanonical.copy());

				// Add warning if withdrawn
				String vsStatus = getStandardsStatus(importedVs);
				if ("withdrawn".equals(vsStatus)) {
					addWarningParameter(expansion, "warning-withdrawn", resolvedCanonical);
				}

				// Recursively collect concepts from included ValueSet
				includedCodes.addAll(collectAllConceptsRecursive(importedVs, expansion, request, expansionChain));
			}
		}
	}

	/**
	 * Processes the exclude section of ValueSet compose
	 */
	private void processExcludes(ValueSet sourceValueSet,
            ValueSetExpansionComponent expansion, 
            ExpansionRequest request,
            List<ValueSetExpansionContainsComponent> includedCodes,
            Set<String> expansionChain) {

		if (!sourceValueSet.getCompose().hasExclude()) {
			return;
		}

		Set<String> codesToExclude = new HashSet<>();

		for (ConceptSetComponent exclude : sourceValueSet.getCompose().getExclude()) {
			if (exclude.hasSystem()) {
				String systemUrl = exclude.getSystem();
				CodeSystem codeSystem = resourceFinder.findCodeSystem(systemUrl, exclude.getVersion(), request);

				if (codeSystem != null) {
					collectExcludedCodes(codesToExclude, codeSystem, exclude, systemUrl);
				}
			}
            if (exclude.hasValueSet()) {
                for (CanonicalType valueSetToExclude : exclude.getValueSet()) {
                    ValueSet excludedVs = resourceFinder.findValueSetByCanonical(valueSetToExclude, request);
                    if (excludedVs != null) {
                        List<ValueSetExpansionContainsComponent> conceptsToExcludeFromVs =
                            collectAllConceptsRecursive(excludedVs, expansion, request, expansionChain);
                        
                        conceptsToExcludeFromVs.forEach(comp -> 
                            codesToExclude.add(comp.getSystem() + "|" + comp.getCode())
                        );
                    }
                }
            }			
		}

		// Remove excluded codes
		if (!codesToExclude.isEmpty()) {
			includedCodes
					.removeIf(component -> codesToExclude.contains(component.getSystem() + "|" + component.getCode()));
		}
	}

	/**
	 * Collects codes to be excluded
	 */
	private void collectExcludedCodes(Set<String> codesToExclude, CodeSystem codeSystem, ConceptSetComponent exclude,
			String systemUrl) {

		// Handle is-not-a filter
		Optional<ConceptSetFilterComponent> isNotAFilter = exclude.getFilter().stream()
				.filter(f -> "concept".equals(f.getProperty()) && f.getOp() == FilterOperator.ISNOTA).findFirst();

		if (isNotAFilter.isPresent()) {
			String codeToExclude = isNotAFilter.get().getValue();
			CodeSystem.ConceptDefinitionComponent conceptToExclude = resourceFinder.findConceptInCodeSystem(codeSystem,
					codeToExclude);
			collectDescendantsAndSelf(conceptToExclude, codesToExclude, systemUrl);
		}

		// Handle property filters
		List<ConceptSetFilterComponent> propertyFilters = exclude.getFilter().stream()
				.filter(f -> f.getOp() != FilterOperator.ISNOTA).collect(Collectors.toList());

		if (!propertyFilters.isEmpty()) {
			collectExcludedCodesRecursive(codesToExclude, codeSystem, codeSystem.getConcept(), propertyFilters);
		}

		// Handle explicit concept exclusions
		if (exclude.hasConcept()) {
			for (ConceptReferenceComponent conceptRef : exclude.getConcept()) {
				if (conceptRef.hasCode()) {
					codesToExclude.add(systemUrl + "|" + conceptRef.getCode());
				}
			}
		}
	}

	/**
	 * Adds codes from a CodeSystem based on include criteria
	 */
	private void addCodesFromCodeSystem(List<ValueSetExpansionContainsComponent> allCodes, ValueSet sourceValueSet,
			CodeSystem codeSystem, ConceptSetComponent include, ExpansionRequest request) {

		// Handle explicit concept references
		if (include.hasConcept()) {
			processExplicitConcepts(allCodes, sourceValueSet, codeSystem, include, request);
			return;
		}

		// Handle filter-based includes
		processFilteredConcepts(allCodes, sourceValueSet, codeSystem, include, request);
	}

	/**
	 * Processes explicitly listed concepts
	 */
	private void processExplicitConcepts(List<ValueSetExpansionContainsComponent> allCodes, ValueSet sourceValueSet,
			CodeSystem codeSystem, ConceptSetComponent include, ExpansionRequest request) {

		for (ConceptReferenceComponent conceptRef : include.getConcept()) {
			CodeSystem.ConceptDefinitionComponent baseConceptDef = resourceFinder.findConceptInCodeSystem(codeSystem,
					conceptRef.getCode());

			if (baseConceptDef != null) {
				CodeSystem.ConceptDefinitionComponent mergedConceptDef = baseConceptDef.copy();

				if (conceptRef.hasDesignation()) {
					for (ConceptReferenceDesignationComponent vsDesg : conceptRef.getDesignation()) {

						CodeSystem.ConceptDefinitionDesignationComponent newDesg = new CodeSystem.ConceptDefinitionDesignationComponent();

						if (vsDesg.hasLanguage())
							newDesg.setLanguage(vsDesg.getLanguage());
						if (vsDesg.hasUse())
							newDesg.setUse(vsDesg.getUse());
						if (vsDesg.hasValue())
							newDesg.setValue(vsDesg.getValue());
						if (vsDesg.hasExtension())
							newDesg.setExtension(vsDesg.getExtension());

						mergedConceptDef.addDesignation(newDesg);
					}
				}

				if (conceptRef.hasExtension()) {
					applyValueSetConceptExtensions(mergedConceptDef, conceptRef.getExtension());
				}

				if (conceptFilter.matchesAllFilters(mergedConceptDef, include.getFilter(), codeSystem)
						&& conceptFilter.shouldIncludeConcept(sourceValueSet, mergedConceptDef,
								conceptRef.hasDisplay() ? conceptRef.getDisplay() : mergedConceptDef.getDisplay(),
								request)) {

					allCodes.add(componentBuilder.createExpansionComponent(codeSystem, mergedConceptDef, request));
				}
			}
		}
	}

	private void applyValueSetConceptExtensions(CodeSystem.ConceptDefinitionComponent conceptDef,
			List<Extension> vsExtensions) {
		for (Extension ext : vsExtensions) {

			conceptDef.getExtension().removeIf(e -> e.getUrl().equals(ext.getUrl()));
			conceptDef.addExtension(ext);

			// 暫時不見天日
			/*
			 * if
			 * ("http://hl7.org/fhir/StructureDefinition/valueset-concept-definition".equals
			 * (ext.getUrl()) && ext.hasValue()) {
			 * conceptDef.setDefinition(ext.getValue().primitiveValue()); }
			 */

			mapExtensionToProperty(ext, "http://hl7.org/fhir/StructureDefinition/valueset-conceptOrder", "order",
					conceptDef);
			mapExtensionToProperty(ext, "http://hl7.org/fhir/StructureDefinition/valueset-label", "label", conceptDef);
			mapExtensionToProperty(ext, "http://hl7.org/fhir/StructureDefinition/itemWeight", "weight", conceptDef);
		}
	}

	private void mapExtensionToProperty(Extension ext, String url, String propertyCode,
			CodeSystem.ConceptDefinitionComponent conceptDef) {
		if (url.equals(ext.getUrl()) && ext.hasValue()) {
			conceptDef.getProperty().removeIf(p -> propertyCode.equals(p.getCode()));
			conceptDef.addProperty().setCode(propertyCode).setValue(ext.getValue());
		}
	}

	/**
	 * Processes concepts based on filters
	 */
	private void processFilteredConcepts(List<ValueSetExpansionContainsComponent> allCodes, ValueSet sourceValueSet,
			CodeSystem codeSystem, ConceptSetComponent include, ExpansionRequest request) {

		List<ConceptSetFilterComponent> filters = include.getFilter();

		// Check for hierarchy filters
		Optional<ConceptSetFilterComponent> isAFilter = findFilter(filters, FilterOperator.ISA);
		Optional<ConceptSetFilterComponent> descendentOfFilter = findFilter(filters, FilterOperator.DESCENDENTOF);
		Optional<ConceptSetFilterComponent> generalizesFilter = findFilter(filters, FilterOperator.GENERALIZES);

		List<ConceptSetFilterComponent> propertyFilters = filters.stream().filter(f -> f.getOp() != FilterOperator.ISA
				&& f.getOp() != FilterOperator.DESCENDENTOF && f.getOp() != FilterOperator.GENERALIZES)
				.collect(Collectors.toList());

		List<ValueSetExpansionContainsComponent> conceptsForThisInclude = new ArrayList<>();

		if (generalizesFilter.isPresent()) {
			processGeneralizes(conceptsForThisInclude, codeSystem, generalizesFilter.get().getValue(), propertyFilters,
					request);
		} else {
			processHierarchicalFilters(conceptsForThisInclude, sourceValueSet, codeSystem, isAFilter,
					descendentOfFilter, propertyFilters, request);
		}

		allCodes.addAll(conceptsForThisInclude);
	}

	/**
	 * Processes hierarchical filters (is-a and descendent-of)
	 */
	private void processHierarchicalFilters(List<ValueSetExpansionContainsComponent> concepts, ValueSet sourceValueSet,
			CodeSystem codeSystem, Optional<ConceptSetFilterComponent> isAFilter,
			Optional<ConceptSetFilterComponent> descendentOfFilter, List<ConceptSetFilterComponent> propertyFilters,
			ExpansionRequest request) {

		List<CodeSystem.ConceptDefinitionComponent> startingConcepts = new ArrayList<>(codeSystem.getConcept());

		if (isAFilter.isPresent()) {
			String parentCode = isAFilter.get().getValue();
			CodeSystem.ConceptDefinitionComponent parentConcept = resourceFinder.findConceptInCodeSystem(codeSystem,
					parentCode);
			if (parentConcept != null) {
				startingConcepts = Collections.singletonList(parentConcept);
			} else {
				startingConcepts.clear();
			}
		} else if (descendentOfFilter.isPresent()) {
			String parentCode = descendentOfFilter.get().getValue();
			CodeSystem.ConceptDefinitionComponent parentConcept = resourceFinder.findConceptInCodeSystem(codeSystem,
					parentCode);
			if (parentConcept != null && parentConcept.hasConcept()) {
				startingConcepts = parentConcept.getConcept();
			} else {
				startingConcepts.clear();
			}
		}

		for (CodeSystem.ConceptDefinitionComponent conceptDef : startingConcepts) {
			processConceptRecursive(concepts, sourceValueSet, codeSystem, conceptDef, request, propertyFilters);
		}
	}

	/**
	 * Recursively processes a concept and its children
	 */
	private void processConceptRecursive(List<ValueSetExpansionContainsComponent> parentContainsList,
			ValueSet sourceValueSet, CodeSystem codeSystem, CodeSystem.ConceptDefinitionComponent conceptDef,
			ExpansionRequest request, List<ConceptSetFilterComponent> vsFilters) {

		boolean buildHierarchy = false; // Can be changed based on requirements

		// Check if current concept should be included
		boolean isIncluded = conceptFilter.matchesAllFilters(conceptDef, vsFilters, codeSystem)
				&& conceptFilter.shouldIncludeConcept(sourceValueSet, conceptDef, conceptDef.getDisplay(), request);

		ValueSetExpansionContainsComponent currentComponent = null;
		if (isIncluded) {
			currentComponent = componentBuilder.createExpansionComponent(codeSystem, conceptDef, request);
			parentContainsList.add(currentComponent);
		}

		// Determine where to add children
		List<ValueSetExpansionContainsComponent> listForChildren;
		if (buildHierarchy && currentComponent != null) {
			listForChildren = currentComponent.getContains();
		} else {
			listForChildren = parentContainsList;
		}

		// Process children recursively
		if (conceptDef.hasConcept()) {
			for (CodeSystem.ConceptDefinitionComponent child : conceptDef.getConcept()) {
				processConceptRecursive(listForChildren, sourceValueSet, codeSystem, child, request, vsFilters);
			}
		}
	}

	/**
	 * Processes generalizes filter (finds ancestors)
	 */
	private void processGeneralizes(List<ValueSetExpansionContainsComponent> concepts, CodeSystem codeSystem,
			String startCode, List<ConceptSetFilterComponent> propertyFilters, ExpansionRequest request) {

		Map<CodeSystem.ConceptDefinitionComponent, CodeSystem.ConceptDefinitionComponent> parentMap = buildParentMap(
				codeSystem);

		CodeSystem.ConceptDefinitionComponent currentConcept = resourceFinder.findConceptInCodeSystem(codeSystem,
				startCode);

		while (currentConcept != null) {
			if (conceptFilter.matchesAllFilters(currentConcept, propertyFilters, codeSystem)) {
				concepts.add(componentBuilder.createExpansionComponent(codeSystem, currentConcept, request));
			}
			currentConcept = parentMap.get(currentConcept);
		}
	}

	/**
	 * Builds a parent map for the CodeSystem hierarchy
	 */
	private Map<CodeSystem.ConceptDefinitionComponent, CodeSystem.ConceptDefinitionComponent> buildParentMap(
			CodeSystem codeSystem) {

		Map<CodeSystem.ConceptDefinitionComponent, CodeSystem.ConceptDefinitionComponent> parentMap = new HashMap<>();

		Stack<CodeSystem.ConceptDefinitionComponent> stack = new Stack<>();
		codeSystem.getConcept().forEach(stack::push);

		while (!stack.isEmpty()) {
			CodeSystem.ConceptDefinitionComponent parent = stack.pop();
			if (parent.hasConcept()) {
				for (CodeSystem.ConceptDefinitionComponent child : parent.getConcept()) {
					parentMap.put(child, parent);
					stack.push(child);
				}
			}
		}

		return parentMap;
	}

	/**
	 * Collects a concept and all its descendants
	 */
	private void collectDescendantsAndSelf(CodeSystem.ConceptDefinitionComponent concept, Set<String> codesToExclude,
			String systemUrl) {
		if (concept == null)
			return;

		codesToExclude.add(systemUrl + "|" + concept.getCode());

		if (concept.hasConcept()) {
			for (CodeSystem.ConceptDefinitionComponent child : concept.getConcept()) {
				collectDescendantsAndSelf(child, codesToExclude, systemUrl);
			}
		}
	}

	/**
	 * Recursively collects excluded codes based on filters
	 */
	private void collectExcludedCodesRecursive(Set<String> codesToExclude, CodeSystem codeSystem,
			List<CodeSystem.ConceptDefinitionComponent> concepts, List<ConceptSetFilterComponent> filters) {

		if (concepts == null || concepts.isEmpty() || filters == null || filters.isEmpty()) {
			return;
		}

		for (CodeSystem.ConceptDefinitionComponent concept : concepts) {
			if (conceptFilter.matchesAllFilters(concept, filters, codeSystem)) {
				codesToExclude.add(codeSystem.getUrl() + "|" + concept.getCode());
			}

			if (concept.hasConcept()) {
				collectExcludedCodesRecursive(codesToExclude, codeSystem, concept.getConcept(), filters);
			}
		}
	}

	// Helper methods

	private Optional<ConceptSetFilterComponent> findFilter(List<ConceptSetFilterComponent> filters,
			FilterOperator operator) {
		return filters.stream().filter(f -> "concept".equals(f.getProperty()) && f.getOp() == operator).findFirst();
	}

	private Set<String> getExcludedSystems(List<CanonicalType> excludeSystemParams) {
		if (excludeSystemParams == null || excludeSystemParams.isEmpty()) {
			return Collections.emptySet();
		}

		return excludeSystemParams.stream().map(CanonicalType::getValue).filter(Objects::nonNull)
				.collect(Collectors.toSet());
	}

	private Map<String, String> parseSystemVersions(List<UriType> systemVersions) {
		if (systemVersions == null || systemVersions.isEmpty()) {
			return Collections.emptyMap();
		}

		return systemVersions.stream().map(UriType::getValue).filter(v -> v != null && v.contains("|"))
				.map(v -> v.split("\\|", 2))
				.filter(parts -> parts.length > 1 && parts[1] != null && !parts[1].trim().isEmpty())
				.collect(Collectors.toMap(parts -> parts[0], parts -> parts[1], (v1, v2) -> v2));
	}

	private String determineSystemVersion(String system, String includeVersion, Map<String, String> systemVersionMap,
			Map<String, String> checkSystemVersionMap, Map<String, String> forceSystemVersionMap) {

		if (forceSystemVersionMap.containsKey(system)) {
			return forceSystemVersionMap.get(system);
		}

		if (checkSystemVersionMap.containsKey(system)) {
			String expectedVersion = checkSystemVersionMap.get(system);
			if (includeVersion != null && !includeVersion.equals(expectedVersion)) {
				throw new UnprocessableEntityException(
						"ValueSet specifies version '" + includeVersion + "' for system '" + system
								+ "', but check-system-version requires version '" + expectedVersion + "'");
			}
			return expectedVersion;
		}

		if (includeVersion != null && !includeVersion.trim().isEmpty()) {
			return includeVersion;
		}

		return systemVersionMap.get(system);
	}

	private void validateFilters(ConceptSetComponent include, int includeIndex, ValueSet sourceValueSet) {

		if (!include.hasFilter()) {
			return;
		}

		List<ConceptSetFilterComponent> filters = include.getFilter();
		for (int filterIndex = 0; filterIndex < filters.size(); filterIndex++) {
			ConceptSetFilterComponent filter = filters.get(filterIndex);

			if (Utilities.noString(filter.getValue())) {
				createAndThrowInvalidFilterException(include, includeIndex, filter, filterIndex, sourceValueSet);
			}
		}
	}

	private void createAndThrowInvalidFilterException(ConceptSetComponent include, int includeIndex,
			ConceptSetFilterComponent filter, int filterIndex, ValueSet sourceValueSet) {

		String errorMessage = String.format("The system %s filter with property = %s, op = %s has no value",
				include.getSystem(), filter.getProperty(), filter.getOp() != null ? filter.getOp().toCode() : "null");

		OperationOutcome outcome = new OperationOutcome();
		OperationOutcome.OperationOutcomeIssueComponent issue = outcome.addIssue();
		issue.setSeverity(OperationOutcome.IssueSeverity.ERROR);
		issue.setCode(OperationOutcome.IssueType.INVALID);

		CodeableConcept details = new CodeableConcept();
		details.addCoding().setSystem("http://hl7.org/fhir/tools/CodeSystem/tx-issue-type").setCode("vs-invalid");
		details.setText(errorMessage);
		issue.setDetails(details);

		String location = String.format("ValueSet[%s|%s].compose.include[%d].filter[%d].value", sourceValueSet.getUrl(),
				sourceValueSet.getVersion(), includeIndex, filterIndex);
		issue.addLocation(location);
		issue.addExpression(location);

		throw new UnprocessableEntityException(outcome);
	}

	private void addSystemParameters(ValueSetExpansionComponent expansion, CodeSystem codeSystem, String systemUrl,
			String version) {

		String finalVersion = (version != null) ? version : codeSystem.getVersion();

		UriType parameterValue;
		if (finalVersion != null && !finalVersion.trim().isEmpty()) {
			parameterValue = new UriType(systemUrl + "|" + finalVersion);
		} else {
			parameterValue = new UriType(systemUrl);
		}

		// Add used-codesystem parameter
		if (!hasParameter(expansion, "used-codesystem", parameterValue)) {
			expansion.addParameter().setName("used-codesystem").setValue(parameterValue.copy());
			// 移除 version 參數
			// expansion.addParameter().setName("version").setValue(parameterValue.copy());
		}

		// Add warnings if needed
		if (codeSystem.getStatus() == Enumerations.PublicationStatus.DRAFT) {
			addWarningParameter(expansion, "warning-draft", parameterValue);
		}

		if (codeSystem.getExperimental()) {
			addWarningParameter(expansion, "warning-experimental", parameterValue);
		}

		String csStatus = getStandardsStatus(codeSystem);
		if ("deprecated".equals(csStatus)) {
			addWarningParameter(expansion, "warning-deprecated", parameterValue);
		}
	}

	private boolean hasParameter(ValueSetExpansionComponent expansion, String name, Type value) {
		return expansion.getParameter().stream().anyMatch(p -> name.equals(p.getName()) && value.equals(p.getValue()));
	}

	private void addWarningParameter(ValueSetExpansionComponent expansion, String warningType, Type value) {
		if (!hasParameter(expansion, warningType, value)) {
			expansion.addParameter().setName(warningType).setValue(value.copy());
		}
	}

	private String getStandardsStatus(DomainResource resource) {
		final String EXT_STANDARDS_STATUS = "http://hl7.org/fhir/StructureDefinition/structuredefinition-standards-status";

		return resource.getExtension().stream()
				.filter(ext -> EXT_STANDARDS_STATUS.equals(ext.getUrl()) && ext.getValue() instanceof CodeType)
				.map(ext -> ((CodeType) ext.getValue()).getCode()).findFirst().orElse(null);
	}
}