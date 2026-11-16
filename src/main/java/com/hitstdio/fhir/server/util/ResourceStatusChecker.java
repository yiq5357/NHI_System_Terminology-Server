package com.hitstdio.fhir.server.util;

import java.util.List;

import org.hl7.fhir.r4.model.CodeType;
import org.hl7.fhir.r4.model.Enumerations.PublicationStatus;
import org.hl7.fhir.r4.model.MetadataResource;
import org.hl7.fhir.r4.model.OperationOutcome;
import org.hl7.fhir.r4.model.OperationOutcome.OperationOutcomeIssueComponent;

/**
 * 依資源本身的 status、experimental 與 standards-status 產生 status-check issue。
 */
public final class ResourceStatusChecker {

    public static final String EXT_STANDARDS_STATUS =
            "http://hl7.org/fhir/StructureDefinition/structuredefinition-standards-status";

    private static final String TX_ISSUE_STATUS_CHECK = "status-check";

    private ResourceStatusChecker() {
    }

    /**
     * 檢查 resource 的狀態，有需要提醒時將 issue 加入 issues。
     *
     * @param version 訊息中顯示的 resource 版本
     * @param source  引用 resource 的資源；experimental 與 draft 只有在 source 不具相同狀態時才提醒，
     *                為 null 時不檢查這兩項
     */
    public static void collect(List<OperationOutcomeIssueComponent> issues, MetadataResource resource,
                               String version, MetadataResource source) {
        String standardsStatus = getStandardsStatus(resource);

        if ("deprecated".equals(standardsStatus)) {
            issues.add(buildIssue("MSG_DEPRECATED", "deprecated", resource, version));
        } else if ("withdrawn".equals(standardsStatus)) {
            issues.add(buildIssue("MSG_WITHDRAWN", "withdrawn", resource, version));
        } else if (resource.getStatus() == PublicationStatus.RETIRED) {
            issues.add(buildIssue("MSG_RETIRED", "retired", resource, version));
        } else if (source != null) {
            if (resource.getExperimental() && !source.getExperimental()) {
                issues.add(buildIssue("MSG_EXPERIMENTAL", "experimental", resource, version));
            } else if (isDraft(resource) && !isDraft(source)) {
                issues.add(buildIssue("MSG_DRAFT", "draft", resource, version));
            }
        }
    }

    /** 組成 url|version，沒有版本時只回傳 url。 */
    public static String canonical(String url, String version) {
        return version == null || version.isEmpty() ? url : url + "|" + version;
    }

    private static boolean isDraft(MetadataResource resource) {
        return resource.getStatus() == PublicationStatus.DRAFT
                || "draft".equals(getStandardsStatus(resource));
    }

    private static String getStandardsStatus(MetadataResource resource) {
        return resource.getExtension().stream()
                .filter(ext -> EXT_STANDARDS_STATUS.equals(ext.getUrl()) && ext.getValue() instanceof CodeType)
                .map(ext -> ((CodeType) ext.getValue()).getCode())
                .findFirst()
                .orElse(null);
    }

    private static OperationOutcomeIssueComponent buildIssue(String messageId, String statusText,
                                                             MetadataResource resource, String version) {
        String text = String.format("Reference to %s %s %s",
                statusText, resource.fhirType(), canonical(resource.getUrl(), version));

        return new OperationOutcomeIssueBuilder()
                .setSeverity(OperationOutcome.IssueSeverity.INFORMATION)
                .setCode(OperationOutcome.IssueType.BUSINESSRULE)
                .setMessageId(messageId)
                .setDetails(TX_ISSUE_STATUS_CHECK, text)
                .build();
    }
}
