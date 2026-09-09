package com.recallai.security;

/**
 * The principal placed in the security context once a JWT has been verified and the
 * user confirmed to exist. Controllers receive it via {@code @AuthenticationPrincipal}.
 */
public record AuthenticatedUser(Long id, String email, String name) {
}
