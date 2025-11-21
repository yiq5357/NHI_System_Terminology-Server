package com.hitstdio.fhir.server.util;

import ca.uhn.fhir.rest.api.RequestTypeEnum;
import org.hl7.fhir.r4.model.*;
import org.hl7.fhir.r4.model.ValueSet.ConceptReferenceComponent;
import org.hl7.fhir.r4.model.ValueSet.ConceptSetComponent;
import org.hl7.fhir.r4.model.ValueSet.ValueSetExpansionComponent;
import org.hl7.fhir.r4.model.ValueSet.ValueSetExpansionContainsComponent;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Builds the final expansion component with all parameters and metadata.
 */
public class ExpansionBuilder {

	private static final String EXT_EXPANSION_PROPERTY = "http://hl7.org/fhir/5.0/StructureDefinition/extension-ValueSet.expansion.property";
	private static final String EXT_STANDARDS_STATUS = "http://hl7.org/fhir/StructureDefinition/structuredefinition-standards-status";

	private final ConceptFilter conceptFilter;
	private final ResourceFinder resourceFinder;

	public ExpansionBuilder(ConceptFilter conceptFilter, ResourceFinder resourceFinder) {
		this.conceptFilter = conceptFilter;
		this.resourceFinder = resourceFinder;
	}

	/**
	 * Builds the complete expansion with all parameters and concepts
	 */
	public void buildExpansion(ValueSetExpansionComponent expansion, ValueSet sourceValueSet,
			List<ValueSetExpansionContainsComponent> allConcepts,
			List<ValueSetExpansionContainsComponent> pagedConcepts, ExpansionRequest request) {

		// Add expansion parameters
		addExpansionParameters(expansion, sourceValueSet, request);

		// Reorder parameters according to FHIR specification
		reorderExpansionParameters(expansion);

		// Add property declarations if needed
		addPropertyDeclarations(expansion, sourceValueSet, request);

		// Auto-declare properties used in contains
		autoDeclarePropertiesFromContains(expansion, pagedConcepts);

		// Set total count
		expansion.setTotal(allConcepts.size());

		// Set offset if provided
		if (request.getOffset() != null && request.getOffset().hasValue()) {
			expansion.setOffset(request.getOffset().getValue());
		}

		// Add the paged concepts
		pagedConcepts.forEach(expansion::addContains);
	}

	/**
	 * Adds all expansion parameters to the expansion component
	 */
	private void addExpansionParameters(ValueSetExpansionComponent expansion, ValueSet sourceValueSet,
			ExpansionRequest request) {

		// Add warning if ValueSet is withdrawn
		String vsStatus = getStandardsStatus(sourceValueSet);
		if ("withdrawn".equals(vsStatus)) {
			String vsCanonical = sourceValueSet.getUrl() + "|" + sourceValueSet.getVersion();
			expansion.addParameter().setName("warning-withdrawn").setValue(new UriType(vsCanonical));
		}

		// Add display language parameter
		addDisplayLanguageParameter(expansion, sourceValueSet, request);

		// Add excludeNested parameter
		if (request.getExcludeNested() != null && !request.getExcludeNested().isEmpty()) {
			expansion.addParameter().setName("excludeNested").setValue(request.getExcludeNested());
		}

		// Add excludeNotForUI parameter
		if (request.getExcludeNotForUI() != null && !request.getExcludeNotForUI().isEmpty()) {
			expansion.addParameter().setName("excludeNotForUI").setValue(request.getExcludeNotForUI());
		}

		// Add exclude-system parameters
		if (request.getExcludeSystem() != null) {
			for (CanonicalType es : request.getExcludeSystem()) {
				if (es != null && !es.isEmpty()) {
					expansion.addParameter().setName("exclude-system").setValue(es);
				}
			}
		}

		// Add URL parameter for GET requests
		if (request.getRequestDetails().getRequestType() == RequestTypeEnum.GET) {
			if (request.getUrl() != null && !request.getUrl().isEmpty()) {
				expansion.addParameter().setName("url").setValue(request.getUrl());
			}
		}

		// Add filter parameter
		if (request.getFilter() != null && !request.getFilter().isEmpty()) {
			expansion.addParameter().setName("filter").setValue(request.getFilter());
		}

		// Add date parameter
		if (request.getDate() != null && !request.getDate().isEmpty()) {
			expansion.addParameter().setName("date").setValue(request.getDate());
		}

		// Add offset parameter
		if (request.getOffset() != null && !request.getOffset().isEmpty()) {
			expansion.addParameter().setName("offset").setValue(request.getOffset());
		}

		// Add count parameter
		if (request.getCount() != null && !request.getCount().isEmpty()) {
			expansion.addParameter().setName("count").setValue(request.getCount());
		}

		// Add includeDesignations parameter
		if (request.getIncludeDesignations() != null && !request.getIncludeDesignations().isEmpty()) {
			expansion.addParameter().setName("includeDesignations").setValue(request.getIncludeDesignations());
		}

		// Add activeOnly parameter
		if (request.getActiveOnly() != null && !request.getActiveOnly().isEmpty()) {
			expansion.addParameter().setName("activeOnly").setValue(request.getActiveOnly());
		}

		// Add designation parameters
		if (request.getDesignation() != null) {
			for (StringType d : request.getDesignation()) {
				if (d != null && !d.isEmpty()) {
					expansion.addParameter().setName("designation").setValue(d);
				}
			}
		}

		// Add system-version parameters
		addVersionParameters(expansion, request);
	}

	/**
	 * Adds display language parameter with fallback logic
	 */
	private void addDisplayLanguageParameter(ValueSetExpansionComponent expansion, ValueSet sourceValueSet,
			ExpansionRequest request) {

		String displayLanguage = request.getDisplayLanguage();

		// If not provided, check ValueSet compose extensions
		if ((displayLanguage == null || displayLanguage.isEmpty()) && sourceValueSet.hasCompose()) {
			displayLanguage = extractDisplayLanguageFromCompose(sourceValueSet);
		}

		// If still not found, use ValueSet language
		if ((displayLanguage == null || displayLanguage.isEmpty()) && sourceValueSet.hasLanguage()) {
			displayLanguage = sourceValueSet.getLanguage();
		}

		// Add parameter if we have a value
		if (displayLanguage != null && !displayLanguage.isEmpty()) {
			expansion.addParameter().setName("displayLanguage").setValue(new CodeType(displayLanguage));
		}
	}

	/**
	 * Extracts display language from ValueSet compose extensions
	 */
	private String extractDisplayLanguageFromCompose(ValueSet sourceValueSet) {
		final String VS_EXP_PARAM_URL = "http://hl7.org/fhir/StructureDefinition/valueset-expansion-parameter";

		for (Extension composeExtension : sourceValueSet.getCompose().getExtension()) {
			if (VS_EXP_PARAM_URL.equals(composeExtension.getUrl())) {
				String paramName = null;
				Type paramValue = null;

				for (Extension subExtension : composeExtension.getExtension()) {
					if ("name".equals(subExtension.getUrl()) && subExtension.hasValue()) {
						paramName = subExtension.getValue().primitiveValue();
					}
					if ("value".equals(subExtension.getUrl()) && subExtension.hasValue()) {
						paramValue = subExtension.getValue();
					}
				}

				if ("displayLanguage".equals(paramName) && paramValue != null) {
					return paramValue.primitiveValue();
				}
			}
		}

		return null;
	}

	/**
	 * Adds version-related parameters
	 */
	private void addVersionParameters(ValueSetExpansionComponent expansion, ExpansionRequest request) {

		// Add system-version parameters
		if (request.getSystemVersion() != null) {
			for (UriType sv : request.getSystemVersion()) {
				if (sv != null && !sv.isEmpty() && isVersionParameterUsed(sv, request)) {
					expansion.addParameter().setName("system-version").setValue(sv);
				}
			}
		}

		// check-system-version is a validation-only parameter and should NOT be echoed

		// Add force-system-version parameters
		if (request.getForceSystemVersion() != null) {
			for (UriType fsv : request.getForceSystemVersion()) {
				if (fsv != null && !fsv.isEmpty()) {
					expansion.addParameter().setName("force-system-version").setValue(fsv);
				}
			}
		}
	}



	private boolean isVersionParameterUsed(UriType systemVersion, ExpansionRequest request) {
		String value = systemVersion.getValue();
		if (value == null || value.trim().isEmpty()) {
			return false;
		}

		String[] parts = value.split("\\|", 2);
		if (parts.length != 2) {
			return false;
		}

		String systemUrl = parts[0];
		String requestedVersion = parts[1];

		java.util.List<String> usedVersions = request.getUsedCodeSystemVersions(systemUrl);
		if (usedVersions == null || !usedVersions.contains(requestedVersion)) {
			return false;
		}

		String versionSource = request.getVersionSource(systemUrl);
		return "system-version".equals(versionSource);
	}

	/**
	 * Adds property declarations to the expansion
	 */
	private void addPropertyDeclarations(ValueSetExpansionComponent expansion, ValueSet sourceValueSet,
			ExpansionRequest request) {

		List<CodeType> allPropertiesToInclude = request.getProperty();

		if (allPropertiesToInclude == null || allPropertiesToInclude.isEmpty()) {
			return;
		}

		String systemUrl = null;
		CodeSystem codeSystem = null;
		if (sourceValueSet.hasCompose()) {
			for (ValueSet.ConceptSetComponent include : sourceValueSet.getCompose().getInclude()) {
				if (include.hasSystem()) {
					systemUrl = include.getSystem();
					break;
				}
			}
		}
		if (systemUrl == null) {
			return;
		}
		codeSystem = resourceFinder.findCodeSystem(systemUrl, null, request);
		if (codeSystem == null) {
			return;
		}

		final Set<String> definedPropertyCodes = codeSystem.getProperty().stream()
				.map(CodeSystem.PropertyComponent::getCode).collect(Collectors.toSet());

		Set<String> explicitlyRequestedCodes = new HashSet<>();
		Parameters parameters = request.getParameters();
		if (parameters != null) {
			for (Parameters.ParametersParameterComponent param : parameters.getParameter()) {
				if ("property".equals(param.getName()) && param.hasValue() && param.getValue() instanceof CodeType) {
					explicitlyRequestedCodes.add(((CodeType) param.getValue()).getCode());
				}
			}
		}

		List<CodeType> propertiesToDeclare = allPropertiesToInclude.stream().filter(prop -> {
			String code = prop.getCode();
			boolean isExplicitlyRequested = explicitlyRequestedCodes.contains(code);

			if (!isExplicitlyRequested) {
				return true;
			}

			return !definedPropertyCodes.contains(code);
		}).collect(Collectors.toList());


		if (propertiesToDeclare.isEmpty()) {
			return;
		}

		Map<String, String> propertyUris = buildPropertyUriMap(codeSystem);
		for (CodeType propToDeclare : propertiesToDeclare) {
			String code = propToDeclare.getCode();
			if (propertyUris.containsKey(code)) {
				addPropertyDeclaration(expansion, code, propertyUris.get(code));
			}
		}
	}

	private void discoverPropertiesFromCompose(ValueSet valueSet, Set<String> properties) {
		if (!valueSet.hasCompose()) {
			return;
		}
		for (ConceptSetComponent include : valueSet.getCompose().getInclude()) {
			for (ConceptReferenceComponent concept : include.getConcept()) {
				for (Extension ext : concept.getExtension()) {
					getPropertyCodeFromExtensionUrl(ext.getUrl()).ifPresent(properties::add);
				}
			}
		}
	}

	private java.util.Optional<String> getPropertyCodeFromExtensionUrl(String url) {
		if (url == null)
			return java.util.Optional.empty();
		switch (url) {
		case "http://hl7.org/fhir/StructureDefinition/valueset-conceptOrder":
			return java.util.Optional.of("order");
		case "http://hl7.org/fhir/StructureDefinition/valueset-label":
			return java.util.Optional.of("label");
		case "http://hl7.org/fhir/StructureDefinition/itemWeight":
			return java.util.Optional.of("weight");
		case "http://hl7.org/fhir/StructureDefinition/structuredefinition-standards-status":
			return java.util.Optional.of("status");
		default:
			return java.util.Optional.empty();
		}
	}

	/**
	 * Builds a map of property codes to URIs
	 */
	private Map<String, String> buildPropertyUriMap(CodeSystem codeSystem) {
		Map<String, String> propertyUris = codeSystem.getProperty().stream()
				.collect(Collectors.toMap(prop -> prop.getCode(), prop -> prop.getUri()));

		propertyUris.put("definition", "http://hl7.org/fhir/concept-properties#definition");
		propertyUris.put("status", "http://hl7.org/fhir/concept-properties#status");
		propertyUris.put("notSelectable", "http://hl7.org/fhir/concept-properties#notSelectable");

		propertyUris.put("order", "http://hl7.org/fhir/concept-properties#order");
		propertyUris.put("label", "http://hl7.org/fhir/concept-properties#label");
		propertyUris.put("weight", "http://hl7.org/fhir/concept-properties#itemWeight");
		return propertyUris;
	}

	/**
	 * Adds a property declaration extension
	 */
	private void addPropertyDeclaration(ValueSetExpansionComponent expansion, String code, String uri) {
		Extension expansionPropertyExt = expansion.addExtension();
		expansionPropertyExt.setUrl(EXT_EXPANSION_PROPERTY);

		expansionPropertyExt.addExtension().setUrl("code").setValue(new CodeType(code));

		expansionPropertyExt.addExtension().setUrl("uri").setValue(new UriType(uri));
	}

	/**
	 * Reorders expansion parameters according to FHIR specification:
	 * 1. Request parameter echoes (in original request order)
	 * 2. Server-generated parameters (used-codesystem, used-supplement, etc.)
	 */
	private void reorderExpansionParameters(ValueSetExpansionComponent expansion) {
		if (expansion.getParameter().isEmpty()) {
			return;
		}

		// Define the order of request parameters
		List<String> requestParamOrder = List.of(
			"excludeNested", "includeDesignations", "includeDefinition",
			"activeOnly", "displayLanguage", "exclude-system",
			"system-version", "check-system-version", "force-system-version",
			"count", "offset", "property", "designation", "filter", "date", "url"
		);

		// Define the order of server-generated parameters
		List<String> serverParamOrder = List.of(
			"used-codesystem", "used-supplement", "version",
			"warning-draft", "warning-experimental", "warning-withdrawn"
		);

		// Separate parameters into categories
		List<ValueSet.ValueSetExpansionParameterComponent> requestParams = new java.util.ArrayList<>();
		List<ValueSet.ValueSetExpansionParameterComponent> serverParams = new java.util.ArrayList<>();
		List<ValueSet.ValueSetExpansionParameterComponent> otherParams = new java.util.ArrayList<>();

		for (ValueSet.ValueSetExpansionParameterComponent param : expansion.getParameter()) {
			String name = param.getName();
			if (requestParamOrder.contains(name)) {
				requestParams.add(param);
			} else if (serverParamOrder.contains(name)) {
				serverParams.add(param);
			} else {
				otherParams.add(param);
			}
		}

		// Sort request parameters by defined order
		requestParams.sort((a, b) -> {
			int indexA = requestParamOrder.indexOf(a.getName());
			int indexB = requestParamOrder.indexOf(b.getName());
			return Integer.compare(indexA, indexB);
		});

		// Sort server parameters by defined order
		serverParams.sort((a, b) -> {
			int indexA = serverParamOrder.indexOf(a.getName());
			int indexB = serverParamOrder.indexOf(b.getName());
			return Integer.compare(indexA, indexB);
		});

		// Clear and rebuild the parameter list in correct order
		expansion.getParameter().clear();
		expansion.getParameter().addAll(requestParams);
		expansion.getParameter().addAll(serverParams);
		expansion.getParameter().addAll(otherParams);
	}

	/**
	 * Automatically declares properties that are used in the expansion contains
	 * but haven't been explicitly declared yet.
	 */
	private void autoDeclarePropertiesFromContains(ValueSetExpansionComponent expansion,
			List<ValueSetExpansionContainsComponent> contains) {

		// Collect all property codes used in contains
		Set<String> usedPropertyCodes = new HashSet<>();
		collectPropertyCodesFromContains(contains, usedPropertyCodes);

		if (usedPropertyCodes.isEmpty()) {
			return;
		}

		// Get already declared property codes from extensions
		Set<String> declaredPropertyCodes = new HashSet<>();
		for (Extension ext : expansion.getExtension()) {
			if (EXT_EXPANSION_PROPERTY.equals(ext.getUrl())) {
				for (Extension subExt : ext.getExtension()) {
					if ("code".equals(subExt.getUrl()) && subExt.getValue() instanceof CodeType) {
						declaredPropertyCodes.add(((CodeType) subExt.getValue()).getCode());
					}
				}
			}
		}

		// Declare any missing properties
		for (String propertyCode : usedPropertyCodes) {
			if (!declaredPropertyCodes.contains(propertyCode)) {
				String uri = getStandardPropertyUri(propertyCode);
				addPropertyDeclaration(expansion, propertyCode, uri);
			}
		}
	}

	/**
	 * Recursively collects all property codes from contains and nested contains
	 */
	private void collectPropertyCodesFromContains(List<ValueSetExpansionContainsComponent> contains,
			Set<String> propertyCodeSet) {
		for (ValueSetExpansionContainsComponent containsComponent : contains) {
			// Collect property codes from this concept
			List<Extension> propertyExts = containsComponent.getExtensionsByUrl(
				"http://hl7.org/fhir/5.0/StructureDefinition/extension-ValueSet.expansion.contains.property");

			for (Extension propertyExt : propertyExts) {
				for (Extension subExt : propertyExt.getExtension()) {
					if ("code".equals(subExt.getUrl()) && subExt.getValue() instanceof CodeType) {
						propertyCodeSet.add(((CodeType) subExt.getValue()).getCode());
					}
				}
			}

			// Recursively collect from nested contains
			if (containsComponent.hasContains()) {
				collectPropertyCodesFromContains(containsComponent.getContains(), propertyCodeSet);
			}
		}
	}

	/**
	 * Returns the standard URI for common FHIR concept properties
	 */
	private String getStandardPropertyUri(String propertyCode) {
		Map<String, String> standardUris = Map.of(
			"status", "http://hl7.org/fhir/concept-properties#status",
			"inactive", "http://hl7.org/fhir/concept-properties#inactive",
			"definition", "http://hl7.org/fhir/concept-properties#definition",
			"label", "http://hl7.org/fhir/concept-properties#label",
			"order", "http://hl7.org/fhir/concept-properties#order",
			"weight", "http://hl7.org/fhir/concept-properties#itemWeight",
			"parent", "http://hl7.org/fhir/concept-properties#parent",
			"child", "http://hl7.org/fhir/concept-properties#child"
		);

		return standardUris.getOrDefault(propertyCode,
			"http://hl7.org/fhir/concept-properties#" + propertyCode);
	}

	/**
	 * Gets the standards status from a resource
	 */
	private String getStandardsStatus(DomainResource resource) {
		return resource.getExtension().stream()
				.filter(ext -> EXT_STANDARDS_STATUS.equals(ext.getUrl()) && ext.getValue() instanceof CodeType)
				.map(ext -> ((CodeType) ext.getValue()).getCode()).findFirst().orElse(null);
	}
}
