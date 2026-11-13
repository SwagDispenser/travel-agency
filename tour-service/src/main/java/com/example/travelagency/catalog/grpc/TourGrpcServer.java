package com.example.travelagency.catalog.grpc;

import io.grpc.Server;
import org.springframework.context.SmartLifecycle;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

public class TourGrpcServer implements SmartLifecycle {
    private final Server server;
    private volatile boolean running;

    public TourGrpcServer(Server server) {
        this.server = server;
    }

    @Override
    public void start() {
        if (running) return;
        try {
            server.start();
            running = true;
        } catch (IOException exception) {
            server.shutdownNow();
            throw new IllegalStateException("Cannot start catalog gRPC server", exception);
        }
    }

    @Override
    public void stop() {
        server.shutdown();
        try {
            if (!server.awaitTermination(5, TimeUnit.SECONDS)) {
                server.shutdownNow();
            }
        } catch (InterruptedException exception) {
            server.shutdownNow();
            Thread.currentThread().interrupt();
        } finally {
            running = false;
        }
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    public int port() {
        return server.getPort();
    }
}
