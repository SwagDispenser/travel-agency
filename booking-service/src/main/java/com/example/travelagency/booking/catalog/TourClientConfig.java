package com.example.travelagency.booking.catalog;

import com.example.travelagency.booking.catalog.grpc.GrpcTourCatalog;
import com.example.travelagency.booking.catalog.http.HttpTourCatalog;
import com.example.travelagency.booking.catalog.http.TourHttpClient;
import com.example.travelagency.contract.grpc.TourCatalogGrpc;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.support.RestClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(TourClientProperties.class)
public class TourClientConfig {
    public TourClientConfig(org.springframework.core.env.Environment environment) {
        String protocol = environment.getProperty("travel.tour-client.protocol", "http");
        if (!"http".equalsIgnoreCase(protocol) && !"grpc".equalsIgnoreCase(protocol)) {
            throw new IllegalArgumentException("travel.tour-client.protocol must be http or grpc");
        }
    }

    public static TourHttpClient httpClient(TourClientProperties properties) {
        var requests = new SimpleClientHttpRequestFactory();
        requests.setConnectTimeout(properties.connectTimeout());
        requests.setReadTimeout(properties.readTimeout());
        var rest = RestClient.builder().baseUrl(properties.restUrl().toString())
                .requestFactory(requests).build();
        return HttpServiceProxyFactory.builderFor(RestClientAdapter.create(rest)).build()
                .createClient(TourHttpClient.class);
    }

    @Bean
    @ConditionalOnProperty(prefix = "travel.tour-client", name = "protocol", havingValue = "http", matchIfMissing = true)
    TourCatalog httpTourCatalog(TourClientProperties properties) {
        return new HttpTourCatalog(httpClient(properties));
    }

    @Bean(destroyMethod = "shutdownNow")
    @ConditionalOnProperty(prefix = "travel.tour-client", name = "protocol", havingValue = "grpc")
    ManagedChannel tourChannel(TourClientProperties properties) {
        var builder = ManagedChannelBuilder.forAddress(properties.grpcHost(), properties.grpcPort());
        if (properties.grpcPlaintext()) builder.usePlaintext();
        else builder.useTransportSecurity();
        return builder.build();
    }

    @Bean
    @ConditionalOnProperty(prefix = "travel.tour-client", name = "protocol", havingValue = "grpc")
    TourCatalog grpcTourCatalog(ManagedChannel tourChannel, TourClientProperties properties) {
        return new GrpcTourCatalog(TourCatalogGrpc.newBlockingStub(tourChannel), properties.deadline());
    }
}
