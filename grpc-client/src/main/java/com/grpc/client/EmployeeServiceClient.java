package com.grpc.client;

import com.google.protobuf.ByteString;
import com.grpc.messages.*;
import io.grpc.Channel;
import io.grpc.ClientInterceptors;
import io.grpc.ManagedChannel;
import io.grpc.Metadata;
import io.grpc.netty.GrpcSslContexts;
import io.grpc.netty.NettyChannelBuilder;
import io.grpc.stub.MetadataUtils;
import io.grpc.stub.StreamObserver;
import io.netty.handler.ssl.SslContext;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

public class EmployeeServiceClient {
    public static void main(String[] args) throws Exception {

        Properties appProps = loadProperties();
        String openSslCertFile = appProps.getProperty("openssl.cert");
        String serverHost = appProps.getProperty("server.host");
        int serverPort = Integer.parseInt(appProps.getProperty("server.port"));

        SslContext sslContext = GrpcSslContexts
                .forClient()
                .trustManager(new File(openSslCertFile))
                .build();

        ManagedChannel channel = NettyChannelBuilder
                .forAddress((serverHost), serverPort)
                .sslContext(sslContext)
                .build();

        EmployeeServiceGrpc.EmployeeServiceBlockingStub blockingClient = EmployeeServiceGrpc.newBlockingStub(channel);
        EmployeeServiceGrpc.EmployeeServiceStub nonBlockingClient = EmployeeServiceGrpc.newStub(channel);

        int cliArgument = Integer.parseInt(args[0]);

        switch (cliArgument) {
            case 1: // example: send metadata AND get an error response
                sendMetadata(blockingClient);
                break;
            case 2: // example: unary request with blocking client
                getByBadgeNumber(blockingClient);
                break;
            case 3: // example: server streaming with blocking client
                getAll(blockingClient);
                break;
            case 4: // example: client side streaming (with non-blocking client only)
                addPhoto(nonBlockingClient);
                break;
            case 5: // example: bidirectional streaming (with non-blocking client only)
                saveAll(nonBlockingClient);
                break;
            default:
                System.err.println("Unknown cli argument: " + cliArgument);
                break;
        }

        Thread.sleep(500);
        channel.shutdown();
        channel.awaitTermination(1, TimeUnit.SECONDS);
    }

    private static Properties loadProperties() throws IOException {
        try (InputStream input = EmployeeServiceClient.class.getClassLoader().getResourceAsStream("application.properties")) {
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

    private static void sendMetadata(EmployeeServiceGrpc.EmployeeServiceBlockingStub blockingClient) {
        Metadata metadata = new Metadata();
        metadata.put(Metadata.Key.of("username", Metadata.ASCII_STRING_MARSHALLER), "lorenzo");
        metadata.put(Metadata.Key.of("password", Metadata.ASCII_STRING_MARSHALLER), "password1");

        Channel channel = ClientInterceptors.intercept(blockingClient.getChannel(), MetadataUtils.newAttachHeadersInterceptor(metadata));

        // info: withChannel is deprecated, use withInterceptors instead.... https://grpc.github.io/grpc-java/javadoc/io/grpc/stub/AbstractStub.html#withChannel-io.grpc.Channel-
        blockingClient.withChannel(channel).getByBadgeNumber(
                GetByBadgeNumberRequest
                        .newBuilder()
                        .build());
    }

    private static void getByBadgeNumber(EmployeeServiceGrpc.EmployeeServiceBlockingStub blockingClient) {
        System.out.println("channel:");
        System.out.println(blockingClient.getChannel());

        EmployeeResponse response =
                blockingClient.getByBadgeNumber(
                        GetByBadgeNumberRequest
                                .newBuilder()
                                .setBadgeNumber(2080)
                                .build());
        System.out.println(response.getEmployee());
    }

    private static void getAll(EmployeeServiceGrpc.EmployeeServiceBlockingStub blockingClient) {
        Iterator<EmployeeResponse> iterator = blockingClient.getAll(GetAllRequest.newBuilder().build());

        while (iterator.hasNext()) {
            System.out.println(iterator.next());
        }
    }

    private static void addPhoto(EmployeeServiceGrpc.EmployeeServiceStub nonBlockingClient) {
        try {
            StreamObserver<AddPhotoRequest> streamObserver =
                    nonBlockingClient.addPhoto(new StreamObserver<AddPhotoResponse>() {
                        @Override
                        public void onNext(AddPhotoResponse addPhotoResponse) {
                            System.out.println(addPhotoResponse.getIsOk());
                        }

                        @Override
                        public void onError(Throwable throwable) {
                            System.err.println(throwable);
                        }

                        @Override
                        public void onCompleted() {
                            // do nothing
                        }
                    });

            Path path = Files.createTempFile("Penguins-", ".jpg");
            Files.copy(EmployeeServiceClient.class.getClassLoader().getResourceAsStream("Penguins.jpg"), path, StandardCopyOption.REPLACE_EXISTING);
            FileInputStream fs = new FileInputStream(path.toFile());

            while (true) {
                byte[] dataBuffer = new byte[64 * 1024];
                int bytesRead = fs.read(dataBuffer);

                // all data read from file
                if (bytesRead == -1) {
                    break;
                }

                // last cycle read less than 64KB
                if (bytesRead < dataBuffer.length) {
                    byte[] lastCycleDataBuffer = new byte[bytesRead];
                    System.arraycopy(dataBuffer, 0, lastCycleDataBuffer, 0, bytesRead);
                    dataBuffer = lastCycleDataBuffer;
                }

                ByteString data = ByteString.copyFrom(dataBuffer);

                AddPhotoRequest request =
                        AddPhotoRequest
                                .newBuilder()
                                .setData(data)
                                .build();

                streamObserver.onNext(request);
            }
            streamObserver.onCompleted();
        } catch (Exception ex) {
            System.err.println(ex);
        }
    }

    private static void saveAll(EmployeeServiceGrpc.EmployeeServiceStub nonBlockingClient) {
        List<Employee> employees = new ArrayList<Employee>();
        employees.add(Employee.newBuilder()
                .setBadgeNumber(123)
                .setFirstName("Lorenzo")
                .setLastName("Schmid")
                .setVacationAccrualRate(2)
                .setVacationAccrued(30)
                .build());
        employees.add(Employee.newBuilder()
                .setBadgeNumber(234)
                .setFirstName("Lisa")
                .setLastName("Furrer")
                .setVacationAccrualRate(2.3f)
                .setVacationAccrued(23.4f)
                .build());

        StreamObserver<EmployeeRequest> streamObserver =
                nonBlockingClient.saveAll(new StreamObserver<EmployeeResponse>() {
                    @Override
                    public void onNext(EmployeeResponse employeeResponse) {
                        System.out.println(employeeResponse);
                    }

                    @Override
                    public void onError(Throwable throwable) {
                        System.err.println(throwable);
                    }

                    @Override
                    public void onCompleted() {
                        // do nothing
                    }
                });

        for (Employee e : employees) {
            EmployeeRequest request =
                    EmployeeRequest
                            .newBuilder()
                            .setEmployee(e)
                            .build();
            streamObserver.onNext(request);
        }
        streamObserver.onCompleted();


    }
}
