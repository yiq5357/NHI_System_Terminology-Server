package com.hitstdio.fhir.server.config;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.jpa.api.dao.DaoRegistry;
import ca.uhn.fhir.jpa.api.dao.IFhirResourceDao;
import ca.uhn.fhir.rest.api.server.SystemRequestDetails;
import ca.uhn.fhir.rest.api.server.RequestDetails;
import ca.uhn.fhir.rest.server.exceptions.ResourceNotFoundException;
import org.hl7.fhir.r4.model.CodeSystem;
import org.hl7.fhir.r4.model.ValueSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 載入 tests-version 資料夾中的 ValueSet 與 CodeSystem 資源，以支援 HL7 Terminology FHIR Server
 * 註冊中關於 version 的 $validate-code 範例（tests-version 情境）。
 * <p>
 * 僅在設定 fhir.load-tests-version=true 時執行載入，不影響既有 tests 資料與行為。
 * 資源可放在 classpath 的 tests-version/ 下（例如 src/main/resources/tests-version/），
 * 或透過 fhir.tests-version.path 指定檔案系統路徑。
 * 若 tests-version 資料夾僅含 request/response 的 Parameters JSON，請將 ValueSet/CodeSystem 定義檔
 * 置於上述路徑或另設 fhir.tests-version.path 指向含 valueset-*.json、codesystem-*.json 的目錄。
 */
@Component
@Order(100)
public class TestsVersionDataLoader implements ApplicationRunner {

    private static final Logger ourLog = LoggerFactory.getLogger(TestsVersionDataLoader.class);

    private static final String VALUESET_PREFIX = "valueset-";
    private static final String CODESYSTEM_PREFIX = "codesystem-";
    private static final String JSON_SUFFIX = ".json";

    private static final String[] VALUESET_FILES = {
        "valueset-version-1.json",
        "valueset-version-2.json",
        "valueset-all-version.json",
        "valueset-all-version-1.json",
        "valueset-all-version-2.json",
        "valueset-version-w.json",
        "valueset-version-n.json",
        "valueset-version-mixed.json",
        "valueset-version-w-bad.json"
    };

    private static final String[] CODESYSTEM_FILES = {
        "codesystem-version-1.json",
        "codesystem-version-2.json"
    };

    @Value("${fhir.load-tests-version:false}")
    private boolean loadTestsVersion;

    @Value("${fhir.tests-version.path:}")
    private String testsVersionPath;

    private final DaoRegistry daoRegistry;
    private final FhirContext fhirContext;

    public TestsVersionDataLoader(DaoRegistry daoRegistry, FhirContext fhirContext) {
        this.daoRegistry = daoRegistry;
        this.fhirContext = fhirContext;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!loadTestsVersion) {
            return;
        }
        IFhirResourceDao<ValueSet> valueSetDao = daoRegistry.getResourceDao(ValueSet.class);
        IFhirResourceDao<CodeSystem> codeSystemDao = daoRegistry.getResourceDao(CodeSystem.class);
        RequestDetails requestDetails = new SystemRequestDetails();

        int loadedVs = 0;
        int loadedCs = 0;

        for (String filename : CODESYSTEM_FILES) {
            try {
                CodeSystem cs = loadCodeSystem(filename);
                if (cs != null) {
                    createOrSkipCodeSystem(codeSystemDao, cs, requestDetails);
                    loadedCs++;
                }
            } catch (Exception e) {
                ourLog.warn("Tests-version: skip or error loading {}: {}", filename, e.getMessage());
            }
        }

        for (String filename : VALUESET_FILES) {
            try {
                ValueSet vs = loadValueSet(filename);
                if (vs != null) {
                    createOrSkipValueSet(valueSetDao, vs, requestDetails);
                    loadedVs++;
                }
            } catch (Exception e) {
                ourLog.warn("Tests-version: skip or error loading {}: {}", filename, e.getMessage());
            }
        }

        if (loadedVs > 0 || loadedCs > 0) {
            ourLog.info("Tests-version: loaded {} ValueSets and {} CodeSystems", loadedVs, loadedCs);
        }
    }

    private InputStream openStream(String filename) {
        InputStream in = getClass().getClassLoader().getResourceAsStream("tests-version/" + filename);
        if (in != null) {
            return in;
        }
        if (testsVersionPath != null && !testsVersionPath.isEmpty()) {
            try {
                Path path = Paths.get(testsVersionPath, filename);
                if (Files.isRegularFile(path)) {
                    return Files.newInputStream(path);
                }
            } catch (Exception e) {
                ourLog.debug("Tests-version file path not found: {}", filename);
            }
        }
        return null;
    }

    private ValueSet loadValueSet(String filename) {
        try (InputStream in = openStream(filename)) {
            if (in == null) {
                return null;
            }
            return fhirContext.newJsonParser().parseResource(ValueSet.class, in);
        } catch (Exception e) {
            ourLog.debug("Could not load ValueSet {}: {}", filename, e.getMessage());
            return null;
        }
    }

    private CodeSystem loadCodeSystem(String filename) {
        try (InputStream in = openStream(filename)) {
            if (in == null) {
                return null;
            }
            return fhirContext.newJsonParser().parseResource(CodeSystem.class, in);
        } catch (Exception e) {
            ourLog.debug("Could not load CodeSystem {}: {}", filename, e.getMessage());
            return null;
        }
    }

    private void createOrSkipValueSet(IFhirResourceDao<ValueSet> dao, ValueSet vs, RequestDetails requestDetails) {
        String url = vs.hasUrl() ? vs.getUrl() : null;
        String version = vs.hasVersion() ? vs.getVersion() : null;
        if (url == null) {
            return;
        }
        ca.uhn.fhir.jpa.searchparam.SearchParameterMap map =
            ca.uhn.fhir.jpa.searchparam.SearchParameterMap.newSynchronous()
                .add(ValueSet.SP_URL, new ca.uhn.fhir.rest.param.UriParam(url));
        if (version != null && !version.isEmpty()) {
            map.add(ValueSet.SP_VERSION, new ca.uhn.fhir.rest.param.TokenParam(version));
        }
        try {
            var existing = dao.search(map, requestDetails);
            if (existing.size() > 0) {
                return;
            }
        } catch (ResourceNotFoundException ignored) {
        }
        vs.setId((String) null);
        dao.create(vs, requestDetails);
    }

    private void createOrSkipCodeSystem(IFhirResourceDao<CodeSystem> dao, CodeSystem cs, RequestDetails requestDetails) {
        String url = cs.hasUrl() ? cs.getUrl() : null;
        String version = cs.hasVersion() ? cs.getVersion() : null;
        if (url == null) {
            return;
        }
        ca.uhn.fhir.jpa.searchparam.SearchParameterMap map =
            ca.uhn.fhir.jpa.searchparam.SearchParameterMap.newSynchronous()
                .add(CodeSystem.SP_URL, new ca.uhn.fhir.rest.param.UriParam(url));
        if (version != null && !version.isEmpty()) {
            map.add(CodeSystem.SP_VERSION, new ca.uhn.fhir.rest.param.TokenParam(version));
        }
        try {
            var existing = dao.search(map, requestDetails);
            if (existing.size() > 0) {
                return;
            }
        } catch (ResourceNotFoundException ignored) {
        }
        cs.setId((String) null);
        dao.create(cs, requestDetails);
    }
}
