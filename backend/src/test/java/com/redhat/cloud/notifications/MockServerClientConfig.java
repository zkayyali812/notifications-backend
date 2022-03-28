package com.redhat.cloud.notifications;

import com.redhat.cloud.notifications.openbridge.Bridge;
import org.apache.commons.io.IOUtils;
import org.mockserver.client.MockServerClient;
import org.mockserver.model.ClearType;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;

import java.io.InputStream;
import java.util.Map;

import static com.redhat.cloud.notifications.Constants.X_RH_IDENTITY_HEADER;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;

public class MockServerClientConfig {

    private final MockServerClient mockServerClient;

    public enum RbacAccess {
        FULL_ACCESS(getFileAsString("rbac-examples/rbac_example_full_access.json")),
        NOTIFICATIONS_READ_ACCESS_ONLY(getFileAsString("rbac-examples/rbac_example_events_notifications_read_access_only.json")),
        NOTIFICATIONS_ACCESS_ONLY(getFileAsString("rbac-examples/rbac_example_events_notifications_access_only.json")),
        READ_ACCESS(getFileAsString("rbac-examples/rbac_example_read_access.json")),
        NO_ACCESS(getFileAsString("rbac-examples/rbac_example_no_access.json"));

        private final String payload;

        RbacAccess(String payload) {
            this.payload = payload;
        }

        public String getPayload() {
            return payload;
        }
    }

    public MockServerClientConfig(String containerIpAddress, Integer serverPort) {
        mockServerClient = new MockServerClient(containerIpAddress, serverPort);
    }

    public void addMockRbacAccess(String xRhIdentity, RbacAccess access) {
        this.mockServerClient
                .when(request()
                        .withPath("/api/rbac/v1/access/")
                        .withQueryStringParameter("application", "notifications,integrations")
                        .withHeader(X_RH_IDENTITY_HEADER, xRhIdentity)
                )
                .respond(response()
                        .withStatusCode(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(access.getPayload()));
    }

    public void addHttpTestEndpoint(HttpRequest request, HttpResponse response, boolean secure) {
        this.mockServerClient
                .withSecure(secure)
                .when(request)
                .respond(response);
    }

    public void clearRbac() {
        this.mockServerClient.clear(request()
                .withPath("/api/rbac/v1/access/"),
                ClearType.EXPECTATIONS
        );
    }

    public void addOpenBridgeEndpoints(Map<String, String> auth, Bridge bridge, Map<String, String> processor) {
        String authString = Json.encode(auth);
        String bridgeString = Json.encode(bridge);
        String processorString = Json.encode(processor);

        this.mockServerClient
                .when(request()
                        .withPath("/auth/realms/event-bridge-fm/protocol/openid-connect/token"))
                .respond(response()
                        .withStatusCode(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(authString));

        this.mockServerClient
                .when(request()
                        .withPath("/api/v1/bridges/.*")
                        .withMethod("GET")
                )
                .respond(response()
                        .withStatusCode(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(bridgeString));

        this.mockServerClient
                .when(request()
                        .withPath("/api/v1/bridges/.*/processors")
                        .withMethod("POST")
                )
                .respond(response()
                        .withStatusCode(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(processorString));

        this.mockServerClient
                .when(request()
                        .withPath("/api/v1/bridges/.*/processors/" + processor.get("id"))
                        .withMethod("DELETE")
                )
                .respond(response()
                        .withStatusCode(202));

    }


    public void clearOpenBridgeEndpoints(Bridge bridge) {
        this.mockServerClient.clear(request()
                .withPath("/auth/realms/event-bridge-fm/protocol/openid-connect/token"),
                ClearType.EXPECTATIONS);

        this.mockServerClient.clear(request()
                .withPath("/api/v1/bridges/" + bridge.getId()),
                ClearType.EXPECTATIONS);

        this.mockServerClient.clear(request()
                .withPath("/api/v1/bridges/" + bridge.getId() + "/processors"),
                ClearType.EXPECTATIONS);
    }

    public void removeHttpTestEndpoint(HttpRequest request) {
        this.mockServerClient.clear(request);
    }

    public MockServerClient getMockServerClient() {
        return mockServerClient;
    }

    public String getRunningAddress() {
        return String.format("%s:%d", mockServerClient.remoteAddress().getHostName(), mockServerClient.remoteAddress().getPort());
    }

    private static String getFileAsString(String filename) {
        try {
            InputStream is = MockServerClientConfig.class.getClassLoader().getResourceAsStream(filename);
            return IOUtils.toString(is, UTF_8);
        } catch (Exception e) {
            fail("Failed to read rhid example file: " + e.getMessage());
            return "";
        }
    }
}
