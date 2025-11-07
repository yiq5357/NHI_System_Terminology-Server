package com.hitstdio.fhir.server.util;

import org.hl7.fhir.r4.model.*;

public class OperationOutcomeHelper {
    
    /**
     * 建立 CodeSystem 版本找不到的 Issue
     */
    public static OperationOutcome.OperationOutcomeIssueComponent createCodeSystemVersionNotFoundIssue(
            ValidationContext context) {
        
        var issue = new OperationOutcomeIssueBuilder()
            .setSeverity(OperationOutcome.IssueSeverity.ERROR)
            .setCode(OperationOutcome.IssueType.NOTFOUND)
            .setMessageId(OperationOutcomeMessageId.UNKNOWN_CODESYSTEM_VERSION)
            .setDetails("not-found", buildCodeSystemVersionErrorMessage(context))
            .build();
        
        // 設置 location
        if ("codeableConcept".equals(context.parameterSource())) {
            issue.addLocation("CodeableConcept.coding[0].system");
            issue.addExpression("CodeableConcept.coding[0].system");
        } else {
            issue.addLocation("system");
            issue.addExpression("system");
        }
        
        return issue;
    }
    
    /**
     * 建立 ValueSet 驗證警告 Issue
     */
    public static OperationOutcome.OperationOutcomeIssueComponent createValueSetValidationWarningIssue(
            ValidationContext context) {
        
        return new OperationOutcomeIssueBuilder()
            .setSeverity(OperationOutcome.IssueSeverity.WARNING)
            .setCode(OperationOutcome.IssueType.NOTFOUND)
            .setMessageId(OperationOutcomeMessageId.UNABLE_TO_CHECK_CODES_IN_VS)
            .setDetails("vs-invalid", buildValueSetWarningMessage(context))
            .build();
    }
    
    /**
     * 建立一般驗證錯誤 Issue
     */
    public static OperationOutcome.OperationOutcomeIssueComponent createGeneralValidationErrorIssue(
            ValidationContext context) {
        
        return new OperationOutcomeIssueBuilder()
            .setSeverity(OperationOutcome.IssueSeverity.ERROR)
            .setCode(OperationOutcome.IssueType.CODEINVALID)
            .setMessageId(OperationOutcomeMessageId.TX_GENERAL_CC_ERROR)
            .setDetails("not-in-vs", buildValueSetIssueMessage(context))
            .build();
    }
    
    /**
     * 建立 Code 不在 CodeSystem 中的 Issue
     */
    public static OperationOutcome.OperationOutcomeIssueComponent createCodeNotInCodeSystemIssue(
            ValidationContext context, CodeSystem codeSystem) {
        
        var issue = new OperationOutcomeIssueBuilder()
            .setSeverity(OperationOutcome.IssueSeverity.ERROR)
            .setCode(OperationOutcome.IssueType.CODEINVALID)
            .setMessageId(OperationOutcomeMessageId.UNKNOWN_CODE_IN_VERSION)
            .setDetails("invalid-code", buildCodeSystemIssueMessage(context, codeSystem))
            .build();
        
        // 根據參數來源設置 location
        if ("codeableConcept".equals(context.parameterSource())) {
            issue.addLocation("CodeableConcept.coding[0].code");
            issue.addExpression("CodeableConcept.coding[0].code");
        } else if ("coding".equals(context.parameterSource())) {
            issue.addLocation("Coding.code");
            issue.addExpression("Coding.code");
        } else {
            issue.addLocation("code");
            issue.addExpression("code");
        }
        
        return issue;
    }
    
    /**
     * 建立 Code 不在 ValueSet 中的 Issue
     */
    public static OperationOutcome.OperationOutcomeIssueComponent createCodeNotInValueSetIssue(
            ValidationContext context) {
        
        var issue = new OperationOutcomeIssueBuilder()
            .setSeverity(OperationOutcome.IssueSeverity.INFORMATION)
            .setCode(OperationOutcome.IssueType.CODEINVALID)
            .setMessageId(OperationOutcomeMessageId.NONE_OF_CODES_IN_VALUE_SET_ONE)
            .setDetails("this-code-not-in-vs", buildValueSetIssueMessage(context))
            .build();
        
        // 根據參數來源設置 location
        if ("codeableConcept".equals(context.parameterSource())) {
            issue.addLocation("CodeableConcept.coding[0].code");
            issue.addExpression("CodeableConcept.coding[0].code");
        } else if ("coding".equals(context.parameterSource())) {
            issue.addLocation("Coding.code");
            issue.addExpression("Coding.code");
        } else {
            issue.addLocation("code");
            issue.addExpression("code");
        }
        
        return issue;
    }
    
    /**
     * 建立 Inactive Code 錯誤 Issue
     */
    public static OperationOutcome.OperationOutcomeIssueComponent createInactiveCodeIssue(
            String codeValue) {
        
        var issue = new OperationOutcomeIssueBuilder()
            .setSeverity(OperationOutcome.IssueSeverity.ERROR)
            .setCode(OperationOutcome.IssueType.BUSINESSRULE)
            .setMessageId(OperationOutcomeMessageId.STATUS_CODE_WARNING)
            .setDetails("code-rule", String.format("The code '%s' is valid but is not active", codeValue))
            .build();
        
        issue.addLocation("Coding.code");
        issue.addExpression("Coding.code");
        
        return issue;
    }
    
    /**
     * 建立 Abstract Code 不允許的 Issue
     */
    public static OperationOutcome.OperationOutcomeIssueComponent createAbstractCodeNotAllowedIssue() {
        var issue = new OperationOutcomeIssueBuilder()
            .setSeverity(OperationOutcome.IssueSeverity.ERROR)
            .setCode(OperationOutcome.IssueType.BUSINESSRULE)
            .setMessageId(OperationOutcomeMessageId.ABSTRACT_CODE_NOT_ALLOWED)
            .setDetails("abstract-code", "Abstract codes are not allowed in this context")
            .build();
        
        issue.addLocation("code");
        issue.addExpression("code");
        
        return issue;
    }
    
    /**
     * 建立 Invalid Display Issue
     */
    public static OperationOutcome.OperationOutcomeIssueComponent createInvalidDisplayIssue(
            ValidationContext context) {
        
        var issue = new OperationOutcomeIssueBuilder()
            .setSeverity(OperationOutcome.IssueSeverity.ERROR)
            .setCode(OperationOutcome.IssueType.INVALID)
            .setMessageId(OperationOutcomeMessageId.INVALID_DISPLAY)
            .setDetails("invalid-display", "The display value does not match the code")
            .build();
        
        // 根據參數來源設置 location
        if ("coding".equals(context.parameterSource())) {
            issue.addLocation("coding.display");
            issue.addExpression("coding.display");
        } else if (context.parameterSource().startsWith("codeableConcept")) {
            String location = context.parameterSource() + ".display";
            issue.addLocation(location);
            issue.addExpression(location);
        } else {
            issue.addLocation("display");
            issue.addExpression("display");
        }
        
        return issue;
    }
    
    /**
     * 建立 ValueSet 找不到的 OperationOutcome
     */
    public static OperationOutcome createValueSetNotFoundOutcome(String valueSetUrl, String errorMessage) {
        OperationOutcome outcome = new OperationOutcome();
        
        // Issue 1: ValueSet not found
        var notFoundIssue = new OperationOutcomeIssueBuilder()
            .setSeverity(OperationOutcome.IssueSeverity.ERROR)
            .setCode(OperationOutcome.IssueType.NOTFOUND)
            .setMessageId(OperationOutcomeMessageId.UNABLE_TO_RESOLVE_VALUE_SET)
            .setDetails("not-found", valueSetUrl != null ? 
                String.format("$external:1:%s$", valueSetUrl) : 
                "$external:1:ValueSet$")
            .build();
        
        outcome.addIssue(notFoundIssue);
        
        // Issue 2: Informational issue
        var infoIssue = new OperationOutcomeIssueBuilder()
            .setSeverity(OperationOutcome.IssueSeverity.INFORMATION)
            .setCode(OperationOutcome.IssueType.INFORMATIONAL)
            .setDiagnostics("$fragments:X-Request-Id:$")
            .build();
        
        outcome.addIssue(infoIssue);
        
        return outcome;
    }
    
    /**
     * 建立無效請求的 OperationOutcome
     */
    public static OperationOutcome createInvalidRequestOutcome(String errorMessage) {
        OperationOutcome outcome = new OperationOutcome();
        
        var issue = new OperationOutcomeIssueBuilder()
            .setSeverity(OperationOutcome.IssueSeverity.ERROR)
            .setCode(OperationOutcome.IssueType.REQUIRED)
            .setMessageId(OperationOutcomeMessageId.MISSING_REQUIRED_PARAMETER)
            .setDetails("invalid-data", errorMessage)
            .build();
        
        outcome.addIssue(issue);
        
        return outcome;
    }
    
    // ==================== 私有輔助方法 ====================
    
    private static String buildCodeSystemVersionErrorMessage(ValidationContext context) {
        if (context.system() != null && context.systemVersion() != null) {
            return String.format("%s|%s", 
                context.system().getValue(),
                context.systemVersion().getValue());
        }
        return "CodeSystem with specified version not found";
    }
    
    private static String buildValueSetWarningMessage(ValidationContext context) {
        if (context.valueSet() != null && context.valueSet().hasUrl()) {
            String version = context.valueSet().hasVersion() ? 
                context.valueSet().getVersion() : "unknown";
            return String.format("%s|%s", context.valueSet().getUrl(), version);
        }
        return "Unable to validate against ValueSet";
    }
    
    private static String buildValueSetIssueMessage(ValidationContext context) {
        if (context.valueSet() != null && context.valueSet().hasUrl()) {
            String version = context.valueSet().hasVersion() ? 
                context.valueSet().getVersion() : "5.0.0";
            return String.format("$external:1:%s|%s$", context.valueSet().getUrl(), version);
        }
        return "$external:1:ValueSet$";
    }
    
    private static String buildCodeSystemIssueMessage(ValidationContext context, CodeSystem codeSystem) {
        String systemUrl = null;

        if (context.system() != null) {
            systemUrl = context.system().getValue();
        } else if (codeSystem != null && codeSystem.hasUrl()) {
            systemUrl = codeSystem.getUrl();
        }

        if (systemUrl != null) {
            return String.format("$external:2:%s$", systemUrl);
        }

        return "$external:2:CodeSystem$";
    }
    
    private OperationOutcomeHelper() {
        // 防止實例化
    }
}
