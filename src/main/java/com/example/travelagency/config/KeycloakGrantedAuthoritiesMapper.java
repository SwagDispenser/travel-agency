package com.example.travelagency.config;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.mapping.GrantedAuthoritiesMapper;
import org.springframework.security.oauth2.core.oidc.user.OidcUserAuthority;
import org.springframework.security.oauth2.core.user.OAuth2UserAuthority;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

public class KeycloakGrantedAuthoritiesMapper implements GrantedAuthoritiesMapper {

    @Override
    public Collection<? extends GrantedAuthority> mapAuthorities(Collection<? extends GrantedAuthority> authorities) {
        List<String> roles = authorities.stream()
                .flatMap(this::extractRoles)
                .distinct()
                .toList();

        return SecurityConfig.toRoleAuthorities(roles);
    }

    private Stream<String> extractRoles(GrantedAuthority authority) {
        Map<String, Object> claims = new HashMap<>();
        if (authority instanceof OidcUserAuthority oidcAuthority) {
            claims.putAll(oidcAuthority.getIdToken().getClaims());
            if (oidcAuthority.getUserInfo() != null) {
                claims.putAll(oidcAuthority.getUserInfo().getClaims());
            }
        } else if (authority instanceof OAuth2UserAuthority oauthAuthority) {
            claims.putAll(oauthAuthority.getAttributes());
        }
        return SecurityConfig.extractRealmRoles(claims).stream();
    }
}
