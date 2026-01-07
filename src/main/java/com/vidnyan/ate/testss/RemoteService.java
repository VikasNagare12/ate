package com.vidnyan.ate.testss;

import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.kafka.core.KafkaTemplate;
import java.net.http.HttpClient;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class RemoteService {

    private final RestTemplate restTemplate = new RestTemplate();
    private final WebClient webClient = WebClient.builder().build();
    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final KafkaTemplate<String, String> kafkaTemplate = null; // Mocked

    public void callViaRestTemplate() {
        restTemplate.getForObject("https://api.example.com", String.class);
    }

    public void callViaWebClient() {
        webClient.get().uri("https://api.example.com").retrieve().bodyToMono(String.class).block();
    }

    public void callViaHttpClient() throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(new URI("https://api.example.com"))
                .GET()
                .build();
        httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    public void sendKafkaMessage() {
        if (kafkaTemplate != null) {
            kafkaTemplate.send("topic", "message");
        } else {
            log.info("Mocked Kafka message send");
        }
    }
}
