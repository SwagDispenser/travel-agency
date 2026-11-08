package com.example.travelagency.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;

@Configuration
@Profile("docker")
public class DockerOidcConfig {
    @Bean
    ClientRegistrationRepository dockerClientRegistration(
            @Value("${OIDC_CLIENT_ID:travel-agency-ui}") String clientId,
            @Value("${OIDC_CLIENT_SECRET:travel-agency-secret}") String clientSecret,
            @Value("${OIDC_PUBLIC_BASE_URL:http://localhost:8081/realms/travel-agency}") String publicBase,
            @Value("${OIDC_INTERNAL_BASE_URL:http://keycloak:8080/realms/travel-agency}") String internalBase) {
        return new InMemoryClientRegistrationRepository(ClientRegistration.withRegistrationId("keycloak")
                .clientId(clientId).clientSecret(clientSecret)
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}").scope("openid", "profile", "email")
                .issuerUri(publicBase).authorizationUri(publicBase + "/protocol/openid-connect/auth")
                .tokenUri(internalBase + "/protocol/openid-connect/token")
                .jwkSetUri(internalBase + "/protocol/openid-connect/certs")
                .userInfoUri(internalBase + "/protocol/openid-connect/userinfo").userNameAttributeName("sub").build());
    }
}
