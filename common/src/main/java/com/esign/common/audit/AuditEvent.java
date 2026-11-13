package com.esign.common.audit;

public record AuditEvent(String at, String action, String txn, String detail) {
}
