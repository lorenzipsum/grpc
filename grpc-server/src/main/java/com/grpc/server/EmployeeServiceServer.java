package com.grpc.server;

import io.grpc.Server;

public class EmployeeServiceServer {

    public static void main(String args[]) {
        try {

            EmployeeServiceServer server = new EmployeeServiceServer();
            server.start();
        } catch (Exception ex) {
            System.err.println(ex);
        }
    }

    private Server server;

    private void start() throws Exception {
        // create and start server
        server = EmployeeService.createAndStartEmployeeService();

        // graceful shutdown
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("Shutting down server");
            EmployeeServiceServer.this.stop();
        }));

        // wait for server termination
        server.awaitTermination();
    }

    private void stop() {
        if (server != null) {
            // stop receiving request and finish processing open requests
            server.shutdown();
        }
    }
}
