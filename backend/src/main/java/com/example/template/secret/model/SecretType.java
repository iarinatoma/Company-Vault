package com.example.template.secret.model;

/**
 * The kind of secret (spec 4.3). Every type has one main value: the password of a LOGIN, the
 * key or token of an API_KEY, the text of a NOTE, the main value of a CUSTOM secret. A LOGIN also
 * has a username and a URL, and every type can carry custom key-value fields. The type itself is
 * plain metadata; the value and the fields are stored only encrypted.
 */
public enum SecretType {
    LOGIN,
    API_KEY,
    NOTE,
    CUSTOM
}