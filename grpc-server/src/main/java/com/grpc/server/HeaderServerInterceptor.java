package com.grpc.server;

import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;

public class HeaderServerInterceptor implements ServerInterceptor {
    @Override
    public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
            ServerCall<ReqT, RespT> serverCall,
            Metadata metadata,
            ServerCallHandler<ReqT, RespT> serverCallHandler) {
        if (serverCall.getMethodDescriptor().getFullMethodName().equalsIgnoreCase("EmployeeService/GetByBadgeNumber")) {
            for (String key : metadata.keys()) {
                String value = metadata.get(Metadata.Key.of(key, Metadata.ASCII_STRING_MARSHALLER));
                System.out.println(key + ": " + value);
            }
        }

        return serverCallHandler.startCall(serverCall, metadata);
    }
}
