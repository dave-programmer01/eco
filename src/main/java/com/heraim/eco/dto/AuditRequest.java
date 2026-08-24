package com.heraim.eco.dto;

public record AuditRequest (
    String contractId,
    String contractText
){
}
