package com.team7.rental.global.security;

/**
 * Access Token에서 꺼낸 값. claim은 sub=memberId, role만 둔다(docs/domains/member.md REQ-002).
 */
public record AccessTokenClaims(Long memberId, String role) {
}
