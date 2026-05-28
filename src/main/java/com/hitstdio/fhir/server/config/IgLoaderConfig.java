package com.hitstdio.fhir.server.config;

import ca.uhn.fhir.jpa.packages.IPackageInstallerSvc;
import ca.uhn.fhir.jpa.packages.PackageInstallationSpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Map;


public class IgLoaderConfig implements SmartInitializingSingleton {

    private static final Logger log = LoggerFactory.getLogger(IgLoaderConfig.class);

    @Autowired
    private IPackageInstallerSvc myPackageInstaller;

    @Autowired
    private HapiIgProperties hapiIgProperties;

    @Override
    public void afterSingletonsInstantiated() {
        Map<String, HapiIgProperties.ImplementationGuide> guides =
                hapiIgProperties.getImplementationguides();

        if (guides == null || guides.isEmpty()) {
            log.debug("No implementation guides configured under hapi.fhir.implementationguides — skipping.");
            return;
        }

        for (Map.Entry<String, HapiIgProperties.ImplementationGuide> entry : guides.entrySet()) {
            String key = entry.getKey();
            HapiIgProperties.ImplementationGuide ig = entry.getValue();

            if (ig.getName() == null || ig.getName().isBlank()) {
                log.warn("IG [{}]: 'name' is required — skipping.", key);
                continue;
            }

            PackageInstallationSpec spec = new PackageInstallationSpec();
            spec.setName(ig.getName());
            spec.setVersion(ig.getVersion());
            spec.setInstallMode(ig.getInstallMode());
            spec.setReloadExisting(Boolean.TRUE.equals(ig.getReloadExisting()));

            String packageUrl = ig.getPackageUrl();
            if (packageUrl != null && !packageUrl.isBlank()) {
                try {
                    byte[] bytes = loadBytes(packageUrl);
                    spec.setPackageContents(bytes);
                    log.info("IG [{}] '{}' v{}: loading from {}", key, ig.getName(), ig.getVersion(), packageUrl);
                } catch (Exception e) {
                    log.error("IG [{}] '{}': cannot read packageUrl '{}': {}",
                            key, ig.getName(), packageUrl, e.getMessage(), e);
                    continue;
                }
            } else {
                log.info("IG [{}] '{}' v{}: downloading from packages.fhir.org",
                        key, ig.getName(), ig.getVersion());
            }

            try {
                myPackageInstaller.install(spec);
                log.info("IG [{}] '{}' v{}: installed successfully.", key, ig.getName(), ig.getVersion());
            } catch (Exception e) {
                log.error("IG [{}] '{}' v{}: installation failed: {}",
                        key, ig.getName(), ig.getVersion(), e.getMessage(), e);
            }
        }
    }

    private byte[] loadBytes(String url) throws Exception {
        URI uri = new URI(url);
        if ("file".equalsIgnoreCase(uri.getScheme())) {
            return Files.readAllBytes(Paths.get(uri));
        }
        try (InputStream is = uri.toURL().openStream()) {
            return is.readAllBytes();
        }
    }
}
