package com.example.template.auth.dto;

/**
 * The outcome of a login attempt — sealed so the controller's switch over it is exhaustive
 * by compilation, the same philosophy as the ScopeAccessGuard role switches.
 */
public sealed interface LoginResult permits TokenPairResponse, TwoFactorChallengeResponse {}
