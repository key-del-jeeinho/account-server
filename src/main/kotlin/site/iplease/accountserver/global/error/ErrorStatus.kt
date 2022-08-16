package site.iplease.accountserver.global.error

enum class ErrorStatus {
    JWT_ERROR,
    POLICY_VIOLATION_ERROR,
    RULE_VIOLATION_ERROR,
    UNKNOWN_ACCOUNT_ERROR,
    WRONG_EMAIL_TOKEN_ERROR,
    CONSTRAINT_VIOLATION_ERROR,
    WRONG_AUTH_CODE,
}
