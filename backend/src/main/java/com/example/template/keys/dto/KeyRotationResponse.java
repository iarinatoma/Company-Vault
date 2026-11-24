package com.example.template.keys.dto;

/** How many values a master key rotation re-encrypted with the current key. */
public record KeyRotationResponse(int dataKeys, int totpSecrets) {}