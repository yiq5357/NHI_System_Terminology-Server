package com.hitstdio.fhir.server.config;

import ca.uhn.fhir.jpa.packages.PackageInstallationSpec;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.LinkedHashMap;
import java.util.Map;

@ConfigurationProperties(prefix = "hapi.fhir")
public class HapiIgProperties {

    private Map<String, ImplementationGuide> implementationguides = new LinkedHashMap<>();

    public Map<String, ImplementationGuide> getImplementationguides() {
        return implementationguides;
    }

    public void setImplementationguides(Map<String, ImplementationGuide> implementationguides) {
        this.implementationguides = implementationguides;
    }

    public static class ImplementationGuide {
        private String name;
        private String version;
        private Boolean reloadExisting = false;
        private PackageInstallationSpec.InstallModeEnum installMode =
                PackageInstallationSpec.InstallModeEnum.STORE_AND_INSTALL;
        private String packageUrl;

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }

        public String getVersion() { return version; }
        public void setVersion(String version) { this.version = version; }

        public Boolean getReloadExisting() { return reloadExisting; }
        public void setReloadExisting(Boolean reloadExisting) { this.reloadExisting = reloadExisting; }

        public PackageInstallationSpec.InstallModeEnum getInstallMode() { return installMode; }
        public void setInstallMode(PackageInstallationSpec.InstallModeEnum installMode) {
            this.installMode = installMode;
        }

        public String getPackageUrl() { return packageUrl; }
        public void setPackageUrl(String packageUrl) { this.packageUrl = packageUrl; }
    }
}
