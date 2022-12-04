package com.grpc.server;


import com.google.protobuf.ByteString;
import com.grpc.messages.*;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.ServerInterceptors;
import io.grpc.ServerServiceDefinition;
import io.grpc.stub.StreamObserver;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public class EmployeeService extends EmployeeServiceGrpc.EmployeeServiceImplBase {

    public static Server createAndStartEmployeeService() throws IOException {
        Properties appProps = loadProperties();
        int serverPort = Integer.parseInt(appProps.getProperty("server.port"));

        // create ServiceDefinition for service and  interceptor
        EmployeeService service = new EmployeeService();
        ServerServiceDefinition serviceDefinition =
                ServerInterceptors.interceptForward(service, new HeaderServerInterceptor());

        // build and start server
        Server server = ServerBuilder
                .forPort(serverPort)
                .useTransportSecurity(
                        new File(appProps.getProperty("openssl.cert")),
                        new File(appProps.getProperty("openssl.key")))
//                .addService(new EmployeeService()) // add service without interceptor
                .addService(serviceDefinition) // add service with interceptor
                .build().start();

        System.out.println("gRPC-server listening on port " + serverPort);

        return server;
    }

    private static Properties loadProperties() throws IOException {
        try (InputStream input = EmployeeServiceServer.class.getClassLoader().getResourceAsStream("application.properties")) {
            if (input == null) {
                throw new IOException("Sorry, unable to find config.properties");
            }
            Properties appProps = new Properties();
            appProps.load(input);
            return appProps;
        } catch (IOException exception) {
            System.err.println(exception);
            throw exception;
        }
    }

    // unary request
    @Override
    public void getByBadgeNumber(GetByBadgeNumberRequest request,
                                 StreamObserver<EmployeeResponse> responseObserver) {
        System.out.println("called getByBadgeNumber with badge number: " + request.getBadgeNumber());
        for (Employee e : Employees.getInstance()) {
            if (request.getBadgeNumber() == e.getBadgeNumber()) {
                System.out.println("Found employee: " + e.getFirstName());
                EmployeeResponse response = EmployeeResponse.newBuilder().setEmployee(e).build();
                responseObserver.onNext(response);
                responseObserver.onCompleted();
                return;
            }
        }

        responseObserver.onError(new Exception("Employee not found with badge number: " + request.getBadgeNumber()));
    }

    // server side streaming request
    @Override
    public void getAll(GetAllRequest request,
                       StreamObserver<EmployeeResponse> responseObserver) {

        for (Employee e : Employees.getInstance()) {
            EmployeeResponse response = EmployeeResponse.newBuilder().setEmployee(e).build();
            responseObserver.onNext(response);
        }
        responseObserver.onCompleted();
    }

    // client side streaming request
    @Override
    public StreamObserver<AddPhotoRequest> addPhoto(StreamObserver<AddPhotoResponse> responseObserver) {
        return new StreamObserver<AddPhotoRequest>() {
            private ByteString result;

            @Override
            public void onNext(AddPhotoRequest addPhotoRequest) {
                ByteString data = addPhotoRequest.getData();
                if (result == null) {
                    result = data;
                } else {
                    result = result.concat(data);
                }
                System.out.println("Received message with " + data.size() + " bytes");
            }

            @Override
            public void onError(Throwable throwable) {
                System.err.println(throwable);
            }

            @Override
            public void onCompleted() {
                System.out.println("Total bytes received: " + result.size());
                responseObserver.onNext(
                        AddPhotoResponse.newBuilder().setIsOk(true).build()
                );
                responseObserver.onCompleted();
            }
        };
    }

    // bidirectional streaming request
    @Override
    public StreamObserver<EmployeeRequest> saveAll(
            StreamObserver<EmployeeResponse> responseObserver) {
        return new StreamObserver<EmployeeRequest>() {
            @Override
            public void onNext(EmployeeRequest employeeRequest) {
                Employees.getInstance().add(employeeRequest.getEmployee());
                responseObserver.onNext(EmployeeResponse.newBuilder()
                        .setEmployee(employeeRequest.getEmployee())
                        .build()
                );
            }

            @Override
            public void onError(Throwable throwable) {
                System.err.println(throwable);
            }

            @Override
            public void onCompleted() {
                for (Employee e : Employees.getInstance()) {
                    System.out.println(e);
                }
                responseObserver.onCompleted();
            }
        };
    }
}

