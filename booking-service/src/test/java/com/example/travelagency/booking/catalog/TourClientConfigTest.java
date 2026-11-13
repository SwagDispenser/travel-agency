package com.example.travelagency.booking.catalog;

import com.example.travelagency.booking.catalog.grpc.GrpcTourCatalog;
import com.example.travelagency.booking.catalog.http.HttpTourCatalog;
import io.grpc.ManagedChannel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.*;

class TourClientConfigTest {
    final ApplicationContextRunner context = new ApplicationContextRunner().withUserConfiguration(TourClientConfig.class);

    @Test
    void httpIsDefaultAndAllThreePortsResolveToSameAdapter() {
        context.run(c -> {
            assertThat(c).hasSingleBean(TourCatalog.class).doesNotHaveBean(ManagedChannel.class);
            assertThat(c.getBean(TourCatalog.class)).isInstanceOf(HttpTourCatalog.class);
            assertThat(c.getBean(TourPriceLookup.class)).isSameAs(c.getBean(TourAvailabilityLookup.class))
                    .isSameAs(c.getBean(TourItineraryLookup.class));
        });
    }

    @Test
    void grpcSelectionCreatesChannelAndClosesItWithContext() {
        var channel = new java.util.concurrent.atomic.AtomicReference<ManagedChannel>();
        context.withPropertyValues("travel.tour-client.protocol=grpc").run(c -> {
            assertThat(c).hasSingleBean(TourCatalog.class).hasSingleBean(ManagedChannel.class);
            assertThat(c.getBean(TourCatalog.class)).isInstanceOf(GrpcTourCatalog.class);
            channel.set(c.getBean(ManagedChannel.class));
        });
        assertThat(channel.get().isShutdown()).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"protocol=typo", "protocol=", "connect-timeout=0ms", "read-timeout=-1s",
            "deadline=0ms", "rest-url=ftp://localhost", "grpc-host=", "grpc-port=99999"})
    void invalidConfigurationFailsAtStartup(String property) {
        context.withPropertyValues("travel.tour-client." + property).run(c -> assertThat(c).hasFailed());
    }
}
