package com.example.travelagency.config;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.OidcUserInfo;
import org.springframework.security.oauth2.core.oidc.user.OidcUserAuthority;
import org.springframework.security.oauth2.core.user.OAuth2UserAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SecurityRoleMappingTest {

    @Test
    void toRoleAuthorities_mixedNames_normalizesAndDeduplicates() {
        Collection<GrantedAuthority> authorities = SecurityConfig.toRoleAuthorities(
                List.of("admin", "ROLE_ADMIN", "user", "", "USER"));

        assertEquals(Set.of("ROLE_ADMIN", "ROLE_USER"), names(authorities));
    }

    @Test
    void extractRealmRoles_missingOrMalformedClaims_returnsEmptyList() {
        assertTrue(SecurityConfig.extractRealmRoles(Map.of()).isEmpty());
        assertTrue(SecurityConfig.extractRealmRoles(Map.of("realm_access", "invalid")).isEmpty());
        assertTrue(SecurityConfig.extractRealmRoles(Map.of("realm_access", Map.of("roles", "ADMIN"))).isEmpty());
    }

    @Test
    void extractRealmRoles_mixedValues_keepsOnlyStrings() {
        Collection<String> roles = SecurityConfig.extractRealmRoles(
                Map.of("realm_access", Map.of("roles", List.of("ADMIN", 123, "USER"))));

        assertEquals(List.of("ADMIN", "USER"), roles);
    }

    @Test
    void jwtConverter_realmRolesAndScopes_mapsBothKindsOfAuthorities() {
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "none")
                .claim("realm_access", Map.of("roles", List.of("ADMIN", "USER")))
                .claim("scope", List.of("openid", "profile"))
                .build();

        Collection<GrantedAuthority> authorities = new SecurityConfig.KeycloakJwtRoleConverter().convert(jwt);

        assertEquals(Set.of("ROLE_ADMIN", "ROLE_USER", "SCOPE_openid", "SCOPE_profile"), names(authorities));
    }

    @Test
    void jwtConverter_noRolesOrScopes_returnsEmptyAuthorities() {
        Jwt jwt = Jwt.withTokenValue("token").header("alg", "none").claim("sub", "guest").build();

        Collection<GrantedAuthority> authorities = new SecurityConfig.KeycloakJwtRoleConverter().convert(jwt);

        assertTrue(authorities.isEmpty());
    }

    @Test
    void oidcMapper_idTokenRole_mapsAdminAuthority() {
        OidcIdToken token = new OidcIdToken("token", Instant.parse("2030-01-01T00:00:00Z"),
                Instant.parse("2030-01-01T01:00:00Z"),
                Map.of("sub", "admin", "realm_access", Map.of("roles", List.of("ADMIN"))));

        Collection<? extends GrantedAuthority> authorities = new KeycloakGrantedAuthoritiesMapper()
                .mapAuthorities(List.of(new OidcUserAuthority(token)));

        assertEquals(Set.of("ROLE_ADMIN"), names(authorities));
    }

    @Test
    void oidcMapper_userInfoClaims_overrideIdTokenRoles() {
        OidcIdToken token = new OidcIdToken("token", Instant.parse("2030-01-01T00:00:00Z"),
                Instant.parse("2030-01-01T01:00:00Z"),
                Map.of("sub", "user", "realm_access", Map.of("roles", List.of("USER"))));
        OidcUserInfo info = new OidcUserInfo(
                Map.of("sub", "user", "realm_access", Map.of("roles", List.of("ADMIN"))));

        Collection<? extends GrantedAuthority> authorities = new KeycloakGrantedAuthoritiesMapper()
                .mapAuthorities(List.of(new OidcUserAuthority(token, info)));

        assertEquals(Set.of("ROLE_ADMIN"), names(authorities));
    }

    @Test
    void oauthMapper_duplicateRolesAndUnrelatedAuthority_returnsUniqueRole() {
        OAuth2UserAuthority user = new OAuth2UserAuthority(
                Map.of("realm_access", Map.of("roles", List.of("USER", "USER"))));

        Collection<? extends GrantedAuthority> authorities = new KeycloakGrantedAuthoritiesMapper()
                .mapAuthorities(List.of(user, new SimpleGrantedAuthority("unrelated")));

        assertEquals(Set.of("ROLE_USER"), names(authorities));
    }

    private static Set<String> names(Collection<? extends GrantedAuthority> authorities) {
        return authorities.stream().map(GrantedAuthority::getAuthority).collect(Collectors.toSet());
    }
}
