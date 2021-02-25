package com.quorum.tessera.server;

import com.quorum.tessera.config.AppType;

import java.net.URI;

public interface TesseraServer {

    void start() throws Exception;

    void stop() throws Exception;

    default URI getUri() {
        return null;
    }

    default AppType getAppType() {
        return null;
    }
}
