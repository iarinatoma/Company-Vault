package com.example.template.user.event;

import java.util.UUID;

public record UserCreatedEvent(UUID userId, String email) {}
