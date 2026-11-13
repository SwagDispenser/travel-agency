package com.example.travelagency.catalog.grpc;

import com.example.travelagency.catalog.TourCatalogService;
import io.grpc.ServerBuilder;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(TourGrpcProperties.class)
public class TourGrpcConfig {
    @Bean
    @ConditionalOnProperty(prefix = "travel.catalog.grpc", name = "enabled", havingValue = "true")
    TourGrpcServer tourGrpcServer(TourCatalogService catalog, TourGrpcProperties properties) {
        return new TourGrpcServer(ServerBuilder.forPort(properties.port())
                .addService(new TourCatalogGrpcService(catalog)).build());
    }
}
