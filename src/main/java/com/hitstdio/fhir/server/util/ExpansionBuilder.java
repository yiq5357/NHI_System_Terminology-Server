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

		// Add property declarations if needed
		addPropertyDeclarations(expansion, sourceValueSet, request);

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
		final String VS_EXP_PARAM_URL = "http://hl7.org/fhir/tools/StructureDefinition/valueset-expansion-parameter";

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
				if (sv != null && !sv.isEmpty()) {
					expansion.addParameter().setName("system-version").setValue(sv);
				}
			}
		}

		// Add check-system-version parameters
		if (request.getCheckSystemVersion() != null) {
			for (UriType csv : request.getCheckSystemVersion()) {
				if (csv != null && !csv.isEmpty()) {
					expansion.addParameter().setName("check-system-version").setValue(csv);
				}
			}
		}

		// Add force-system-version parameters
		if (request.getForceSystemVersion() != null) {
			for (UriType fsv : request.getForceSystemVersion()) {
				if (fsv != null && !fsv.isEmpty()) {
					expansion.addParameter().setName("force-system-version").setValue(fsv);
				}
			}
		}
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
	 * Gets the standards status from a resource
	 */
	private String getStandardsStatus(DomainResource resource) {
		return resource.getExtension().stream()
				.filter(ext -> EXT_STANDARDS_STATUS.equals(ext.getUrl()) && ext.getValue() instanceof CodeType)
				.map(ext -> ((CodeType) ext.getValue()).getCode()).findFirst().orElse(null);
	}
}
