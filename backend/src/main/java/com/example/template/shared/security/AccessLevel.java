package com.example.template.shared.security;

/**
 * Access levels on a vault, project, folder or secret (spec, section 3), from least to most:
 * <ul>
 *   <li>{@code NO_ACCESS} – the resource is invisible</li>
 *   <li>{@code READ} – view and copy</li>
 *   <li>{@code WRITE} – edit</li>
 *   <li>{@code MANAGE} – share and delete</li>
 * </ul>
 * Every level includes all lower ones, so MANAGE may also write and read. The declaration
 * order of the constants IS the order of the levels — never reorder them.
 */
public enum AccessLevel {
    NO_ACCESS,
    READ,
    WRITE,
    MANAGE;

    /** True if this level allows everything {@code required} allows. */
    public boolean includes(AccessLevel required) {
        return compareTo(required) >= 0;
    }
}