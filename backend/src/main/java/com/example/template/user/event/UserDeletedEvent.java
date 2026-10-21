package com.example.template.user.event;

import java.util.UUID;

/**
 * Published inside the deleting transaction, before the users row is removed: other features
 * clean up the data they keep for this user (the references are plain UUID columns).
 */
public record UserDeletedEvent(UUID userId, String email) {}
