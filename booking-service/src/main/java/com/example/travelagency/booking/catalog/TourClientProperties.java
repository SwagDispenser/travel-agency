package com.example.travelagency.booking.catalog;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.net.URI;
import java.time.Duration;

@ConfigurationProperties("travel.tour-client")
public record TourClientProperties(
        @DefaultValue("HTTP") Protocol protocol,
        @DefaultValue("http://localhost:8080") URI restUrl,
        @DefaultValue("localhost") String grpcHost,
        @DefaultValue("9090") int grpcPort,
        @DefaultValue("true") boolean grpcPlaintext,
        @DefaultValue("500ms") Duration connectTimeout,
        @DefaultValue("1s") Duration readTimeout,
        @DefaultValue("1s") Duration deadline) {

    public enum Protocol { HTTP, GRPC }

    public TourClientProperties {
        if (protocol == null) throw new IllegalArgumentException("Tour client protocol is required");
        if (restUrl == null || restUrl.getHost() == null
                || !("http".equals(restUrl.getScheme()) || "https".equals(restUrl.getScheme()))) {
            throw new IllegalArgumentException("Tour REST URL must be an absolute HTTP(S) URL");
        }
        if (grpcHost == null || grpcHost.isBlank() || grpcPort < 1 || grpcPort > 65535) {
            throw new IllegalArgumentException("Invalid tour gRPC host or port");
        }
        validateTimeout(connectTimeout);
        validateTimeout(readTimeout);
        validateTimeout(deadline);
    }

    private static void validateTimeout(Duration value) {
        if (value == null || value.toMillis() < 1 || value.toMillis() > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Tour client timeouts must be between 1ms and 2147483647ms");
        }
    }
}
