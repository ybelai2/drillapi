package com.os439.drillapi;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;

@Service
public class GeminiService {

    @Value("${gemini.api.key:}")
    private String apiKey;

    @Value("${gemini.model:gemini-2.5-flash}")
    private String model;

    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient http = HttpClient.newHttpClient();

    public String generateQuestions(String slideText) throws Exception {
        String prompt = """
            You are a computer science professor creating exam practice questions.
            From the following lecture slide text, write 8 multiple-choice questions.
            Each question must have exactly 4 options, exactly one correct answer,
            and a one-sentence explanation of why the answer is correct.
            Cover the most important, testable concepts. Do not invent facts not in the slides.

            SLIDE TEXT:
            """ + slideText;

        Map<String, Object> schema = Map.of(
            "type", "ARRAY",
            "items", Map.of(
                "type", "OBJECT",
                "properties", Map.of(
                    "question", Map.of("type", "STRING"),
                    "options", Map.of("type", "ARRAY", "items", Map.of("type", "STRING")),
                    "answerIndex", Map.of("type", "INTEGER"),
                    "explanation", Map.of("type", "STRING")
                ),
                "required", List.of("question", "options", "answerIndex", "explanation")
            )
        );

        Map<String, Object> body = Map.of(
            "contents", List.of(Map.of("parts", List.of(Map.of("text", prompt)))),
            "generationConfig", Map.of(
                "responseMimeType", "application/json",
                "responseSchema", schema
            )
        );

        String url = "https://generativelanguage.googleapis.com/v1beta/models/"
                     + model + ":generateContent";

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("x-goog-api-key", apiKey)
            .header("Content-Type", "application/json")
            .timeout(Duration.ofSeconds(60))
            .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)))
            .build();

        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new RuntimeException("Gemini error " + response.statusCode() + ": " + response.body());
        }

        JsonNode root = mapper.readTree(response.body());
        return root.path("candidates").path(0)
                   .path("content").path("parts").path(0)
                   .path("text").asText();
    }
}