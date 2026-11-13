package com.example.travelagency.catalog.grpc;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("travel.catalog.grpc")
public record TourGrpcProperties(@DefaultValue("false") boolean enabled, @DefaultValue("9090") int port) {
    public TourGrpcProperties {
        // Zero is useful for an ephemeral port in integration tests.
        if (port < 0 || port > 65535) throw new IllegalArgumentException("Invalid catalog gRPC port");
    }
}
