package com.example.servicemanagement;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Sends real HTTP requests to the embedded server and checks the status code
 * and problem-detail body returned for client errors and unexpected failures.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(HttpErrorHandlingIntegrationTest.FailingController.class)
@ExtendWith(OutputCaptureExtension.class)
class HttpErrorHandlingIntegrationTest {

    private static final String FAILURE_PATH = "/test/unexpected-failure";
    private static final String FAILURE_MESSAGE = "simulated unexpected failure";

    private final HttpClient httpClient = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .build();

    @LocalServerPort
    private int port;

    @Test
    void unknownEndpointReturns404() throws Exception {
        HttpResponse<String> response = send(request("/api/does-not-exist").GET());

        assertProblem(response, 404, "Not Found", "/api/does-not-exist");
    }

    @Test
    void unsupportedHttpMethodReturns405() throws Exception {
        HttpResponse<String> response = send(request("/api/users").DELETE());

        assertProblem(response, 405, "Method Not Allowed", "/api/users");
        assertThat(response.headers().firstValue("Allow").orElse("")).contains("GET", "POST");
    }

    @Test
    void missingRequiredParameterReturns400() throws Exception {
        HttpResponse<String> response = send(request("/api/tickets/1/status")
                .method("PATCH", HttpRequest.BodyPublishers.noBody()));

        assertProblem(response, 400, "Bad Request", "/api/tickets/1/status");
        assertThat(JsonPath.<String>read(response.body(), "$.detail")).contains("status");
    }

    @Test
    void unsupportedContentTypeReturns415() throws Exception {
        HttpResponse<String> response = send(request("/api/users")
                .header("Content-Type", "text/plain")
                .POST(HttpRequest.BodyPublishers.ofString("not json")));

        assertProblem(response, 415, "Unsupported Media Type", "/api/users");
    }

    @Test
    void unexpectedExceptionReturns500AndIsLogged(CapturedOutput output) throws Exception {
        HttpResponse<String> response = send(request(FAILURE_PATH).GET());

        assertProblem(response, 500, "Internal server error", FAILURE_PATH);
        assertThat(JsonPath.<String>read(response.body(), "$.detail")).isEqualTo("An unexpected error occurred");
        assertThat(response.body()).doesNotContain(FAILURE_MESSAGE);
        assertThat(output)
                .contains("Unexpected error while handling GET " + FAILURE_PATH)
                .contains(FAILURE_MESSAGE);
    }

    private HttpRequest.Builder request(String path) {
        return HttpRequest.newBuilder(URI.create("http://localhost:" + port + path));
    }

    private HttpResponse<String> send(HttpRequest.Builder request) throws Exception {
        return httpClient.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    private void assertProblem(HttpResponse<String> response, int status, String title, String path) {
        assertThat(response.statusCode()).isEqualTo(status);
        assertThat(response.headers().firstValue("Content-Type").orElse("")).startsWith("application/problem+json");

        String body = response.body();
        assertThat(JsonPath.<Integer>read(body, "$.status")).isEqualTo(status);
        assertThat(JsonPath.<String>read(body, "$.title")).isEqualTo(title);
        assertThat(JsonPath.<String>read(body, "$.instance")).isEqualTo(path);
        assertThat(JsonPath.<String>read(body, "$.timestamp")).isNotBlank();
    }

    /**
     * Test-only endpoint that fails with an exception no handler maps to a client error.
     * It is registered for this test class only.
     */
    @RestController
    static class FailingController {

        @GetMapping(FAILURE_PATH)
        public String fail() {
            throw new UnsupportedOperationException(FAILURE_MESSAGE);
        }
    }
}
