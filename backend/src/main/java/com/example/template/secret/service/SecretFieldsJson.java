package com.example.template.secret.service;

import com.example.template.secret.dto.SecretFields;
import tools.jackson.databind.json.JsonMapper;

/**
 * JSON form of {@link SecretFields} — the plaintext that is encrypted into
 * {@code secrets.encrypted_fields}. Only ever used right before encrypting or right after
 * decrypting; never logged.
 */
final class SecretFieldsJson {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private SecretFieldsJson() {}

    static String write(SecretFields fields) {
        return JSON.writeValueAsString(fields);
    }

    static SecretFields read(String json) {
        return JSON.readValue(json, SecretFields.class);
    }
}