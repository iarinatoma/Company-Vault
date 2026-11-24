package com.example.template.shared.security;

/**
 * The kinds of resource an access level can be granted on. A new kind of resource (from E4
 * vaults/projects/folders or E5 secrets) gets one constant here and is then protected by the
 * same central check — {@code AccessControl} in the access feature.
 */
public enum ResourceType {
    VAULT,
    PROJECT,
    FOLDER,
    SECRET
}