package org.hazelcast.msfdemo.acctsvc.service;

import io.grpc.BindableService;
import io.grpc.Server;
import io.grpc.ServerBuilder;

import java.io.IOException;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

public class GrpcServer {
    private static final Logger logger = Logger.getLogger(org.example.grpc.GrpcServer.class.getName());
    private final Server server;

    public GrpcServer(BindableService service, int port) {
        this.server = ServerBuilder.forPort(port)
                // Added executor - otherwise runaway thread creation seen!
                .executor(Executors.newFixedThreadPool(16))
                .addService(service).build();

        try {
            this.server.start();
            logger.info("GrpcServer started, listening on " + port);
        } catch (IOException var4) {
            var4.printStackTrace();
        }

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.err.println("*** shutting down gRPC server since JVM is shutting down");

            try {
                this.stop();
            } catch (InterruptedException var2) {
                var2.printStackTrace(System.err);
            }

            System.err.println("*** server shut down");
        }));
    }

    private void stop() throws InterruptedException {
        if (this.server != null) {
            this.server.shutdown().awaitTermination(30L, TimeUnit.SECONDS);
        }

    }

    public void blockUntilShutdown() throws InterruptedException {
        if (this.server != null) {
            this.server.awaitTermination();
        }

    }
}
