package com.example.template.secret.dto;

/** The decrypted value of one secret, returned only by the explicit reveal endpoint. */
public record SecretValueResponse(String value) {}