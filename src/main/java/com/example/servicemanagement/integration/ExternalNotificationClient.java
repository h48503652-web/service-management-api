package com.example.servicemanagement.integration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

@Component
public class ExternalNotificationClient {

    private static final Logger log = LoggerFactory.getLogger(ExternalNotificationClient.class);

    private final RestTemplate restTemplate;
    private final String baseUrl;

    public ExternalNotificationClient(@Value("${integration.notification.base-url:http://localhost:9090}") String baseUrl) {
        this.baseUrl = baseUrl;
        this.restTemplate = new RestTemplate();
        var factory = new org.springframework.http.client.SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(2000);
        factory.setReadTimeout(2000);
        this.restTemplate.setRequestFactory(factory);
    }

    public boolean sendTicketNotification(Long ticketId, String status) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setAccept(java.util.List.of(MediaType.APPLICATION_JSON));

            NotificationRequest request = new NotificationRequest(ticketId, status);
            ResponseEntity<Void> response = restTemplate.postForEntity(
                    baseUrl + "/notifications",
                    new HttpEntity<>(request, headers),
                    Void.class
            );

            boolean successful = response.getStatusCode().is2xxSuccessful();
            log.info("Notification sent for ticket {} with status {} -> {}", ticketId, status, response.getStatusCode());
            return successful;
        } catch (RestClientException ex) {
            log.warn("Notification delivery failed for ticket {}: {}", ticketId, ex.getMessage());
            return false;
        }
    }

    record NotificationRequest(Long ticketId, String status) {
    }
}
