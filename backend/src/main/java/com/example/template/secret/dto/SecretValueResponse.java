package com.example.template.secret.dto;

/** The decrypted value and type-specific fields of one secret, returned only by the explicit reveal endpoint. */
public record SecretValueResponse(String value, SecretFields fields) {

    public SecretValueResponse(String value) {
        this(value, SecretFields.EMPTY);
    }
}