package com.hitstdio.fhir.server.provider;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.CodeSystem;
import org.hl7.fhir.r4.model.CodeSystem.CodeSystemContentMode;
import org.hl7.fhir.r4.model.CodeType;
import org.hl7.fhir.r4.model.Enumerations;
import org.hl7.fhir.r4.model.TerminologyCapabilities;
import org.hl7.fhir.r4.model.TerminologyCapabilities.CapabilityStatementKind;
import org.hl7.fhir.r4.model.TerminologyCapabilities.TerminologyCapabilitiesCodeSystemComponent;
import org.hl7.fhir.r4.model.TerminologyCapabilities.TerminologyCapabilitiesCodeSystemVersionComponent;
import org.hl7.fhir.r4.model.TerminologyCapabilities.TerminologyCapabilitiesExpansionComponent;

import com.hitstdio.fhir.server.interceptor.CodeSystemContentModeInterceptor;

import ca.uhn.fhir.jpa.api.dao.DaoRegistry;
import ca.uhn.fhir.jpa.api.dao.IFhirResourceDao;
import ca.uhn.fhir.jpa.searchparam.SearchParameterMap;
import ca.uhn.fhir.rest.api.server.IBundleProvider;
import ca.uhn.fhir.rest.api.server.RequestDetails;

/**
 * Builds the TerminologyCapabilities (FHIR R4) returned by GET /metadata?mode=terminology.
 * Invoked from CapabilityStatementInterceptor in ExampleRestfulServlet.
 */
public class TerminologyCapabilitiesResourceProvider extends BaseResourceProvider<TerminologyCapabilities> {

	// Must match the $expand parameters in ValueSetResourceProvider
	// (url / valueSet / valueSetVersion identify the input and are not listed)
	private static final List<String> EXPAND_PARAMETERS = Arrays.asList(
			"activeOnly", "check-system-version", "count", "date", "designation", "displayLanguage",
			"exclude-system", "excludeNested", "excludeNotForUI", "filter", "force-system-version",
			"includeDefinition", "includeDesignations", "offset", "property", "system-version",
			"tx-resource");

	// R5 TerminologyCapabilities.codeSystem.content, carried into R4 as a cross-version extension
	static final String CODESYSTEM_CONTENT_EXTENSION_URL =
			"http://hl7.org/fhir/5.0/StructureDefinition/extension-TerminologyCapabilities.codeSystem.content";

	private final IFhirResourceDao<CodeSystem> codeSystemDao;

	// Same rule GET /CodeSystem uses to return large code systems as not-present
	private CodeSystemContentModeInterceptor contentModeInterceptor;

	public TerminologyCapabilitiesResourceProvider(DaoRegistry theDaoRegistry) {
		super(theDaoRegistry);
		this.codeSystemDao = theDaoRegistry.getResourceDao(CodeSystem.class);
	}

	public void setContentModeInterceptor(CodeSystemContentModeInterceptor theContentModeInterceptor) {
		this.contentModeInterceptor = theContentModeInterceptor;
	}

	public TerminologyCapabilities getTerminologyCapabilities(RequestDetails requestDetails) {
		TerminologyCapabilities tc = new TerminologyCapabilities();

		tc.setVersion("1.0.0");
		tc.setName("TerminologyCapabilities");
		tc.setTitle("Terminology Capabilities Statement");
		tc.setStatus(Enumerations.PublicationStatus.ACTIVE);
		tc.setDate(new Date());

		// /metadata describes this running server, so kind = instance (tcp-1: implementation required)
		tc.setKind(CapabilityStatementKind.INSTANCE);
		String serverBase = requestDetails.getFhirServerBase();
		tc.getImplementation()
				.setDescription("FHIR Terminology Server running at " + serverBase)
				.setUrl(serverBase);

		addCodeSystems(tc, requestDetails);

		TerminologyCapabilitiesExpansionComponent expansion = tc.getExpansion();
		expansion.setPaging(true); // offset / count are supported
		for (String paramName : EXPAND_PARAMETERS) {
			expansion.addParameter().setName(paramName);
		}

		// $translate is not implemented, so $validate-code does not validate translations
		tc.getValidateCode().setTranslations(false);

		return tc;
	}

	/**
	 * Groups all CodeSystem versions under one codeSystem entry per url (tcp-6) and marks the
	 * highest version as default (tcp-7), matching ResourceFinder's choice when no version is given.
	 */
	private void addCodeSystems(TerminologyCapabilities tc, RequestDetails requestDetails) {
		IBundleProvider results = codeSystemDao.search(SearchParameterMap.newSynchronous(), requestDetails);

		Map<String, List<String>> versionsByUrl = new LinkedHashMap<>();
		// content mode as GET returns it, keyed by url then version ("" = no version)
		Map<String, Map<String, CodeSystemContentMode>> contentByUrl = new LinkedHashMap<>();
		for (IBaseResource resource : results.getAllResources()) {
			if (!(resource instanceof CodeSystem)) {
				continue;
			}
			CodeSystem cs = (CodeSystem) resource;
			// A supplement is not a standalone code system
			if (!cs.hasUrl() || cs.getContent() == CodeSystem.CodeSystemContentMode.SUPPLEMENT) {
				continue;
			}
			List<String> versions = versionsByUrl.computeIfAbsent(cs.getUrl(), k -> new ArrayList<>());
			if (cs.hasVersion() && !versions.contains(cs.getVersion())) {
				versions.add(cs.getVersion());
			}
			contentByUrl.computeIfAbsent(cs.getUrl(), k -> new LinkedHashMap<>())
					.putIfAbsent(cs.hasVersion() ? cs.getVersion() : "", getEffectiveContent(cs));
		}

		for (Map.Entry<String, List<String>> entry : versionsByUrl.entrySet()) {
			TerminologyCapabilitiesCodeSystemComponent csComponent = tc.addCodeSystem()
					.setUri(entry.getKey());

			List<String> versions = entry.getValue();
			versions.sort(TerminologyCapabilitiesResourceProvider::compareVersions);
			String defaultVersion = versions.isEmpty() ? "" : versions.get(versions.size() - 1);

			// R5 puts content on the codeSystem (not per version): describe the default version
			Map<String, CodeSystemContentMode> contents = contentByUrl.get(entry.getKey());
			CodeSystemContentMode content = contents.getOrDefault(defaultVersion, contents.values().iterator().next());
			if (content != null && content != CodeSystemContentMode.NULL) {
				csComponent.addExtension(CODESYSTEM_CONTENT_EXTENSION_URL, new CodeType(content.toCode()));
			}

			if (versions.isEmpty()) {
				continue;
			}
			for (String version : versions) {
				TerminologyCapabilitiesCodeSystemVersionComponent versionComponent = csComponent.addVersion()
						.setCode(version);
				if (versions.size() > 1) {
					versionComponent.setIsDefault(version.equals(defaultVersion));
				}
			}
		}
	}

	private CodeSystemContentMode getEffectiveContent(CodeSystem cs) {
		return contentModeInterceptor != null ? contentModeInterceptor.getEffectiveContent(cs) : cs.getContent();
	}

	/**
	 * Same rule as ResourceFinder.compareVersions (numeric prefix of each dotted part),
	 * falling back to string order so the sort is stable for equal numeric versions.
	 */
	private static int compareVersions(String v1, String v2) {
		String[] parts1 = v1.split("\\.");
		String[] parts2 = v2.split("\\.");
		int maxLength = Math.max(parts1.length, parts2.length);
		for (int i = 0; i < maxLength; i++) {
			int num1 = i < parts1.length ? parseVersionPart(parts1[i]) : 0;
			int num2 = i < parts2.length ? parseVersionPart(parts2[i]) : 0;
			if (num1 != num2) {
				return Integer.compare(num1, num2);
			}
		}
		return v1.compareTo(v2);
	}

	private static int parseVersionPart(String part) {
		int end = 0;
		while (end < part.length() && Character.isDigit(part.charAt(end))) {
			end++;
		}
		if (end == 0) {
			return 0;
		}
		try {
			return Integer.parseInt(part.substring(0, end));
		} catch (NumberFormatException e) {
			return 0;
		}
	}

	@Override
	public Class<TerminologyCapabilities> getResourceType() {
		return TerminologyCapabilities.class;
	}
}
