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
            You are a computer science professor creating study material from lecture slides.
            From the SLIDE TEXT below, return JSON with two parts:

            1. "flashcards": 8-10 key term/definition pairs. "front" = the term or concept,
               "back" = a concise 1-2 sentence definition.

            2. "questions": 10-12 quiz questions mixing THREE types, roughly evenly:
               - type "mc"  : multiple choice. Put exactly 4 entries in "options",
                              set "answerIndex" to the correct option (0-3), set "answerText" to "".
               - type "tf"  : true/false. Set "options" to ["True","False"], set "answerIndex"
                              to 0 if the statement is true or 1 if false, set "answerText" to "".
               - type "fill": fill in the blank. Write "question" with the blank shown as _____ ,
                              put the missing word or short phrase in "answerText",
                              set "options" to [] and "answerIndex" to -1.

            Give every question a one-sentence "explanation".
            Use ONLY facts present in the slides. Do not invent content.

            SLIDE TEXT:
            """ + slideText;

        Map<String, Object> str = Map.of("type", "STRING");

        Map<String, Object> flashItem = Map.of(
            "type", "OBJECT",
            "properties", Map.of("front", str, "back", str),
            "required", List.of("front", "back")
        );

        Map<String, Object> qItem = Map.of(
            "type", "OBJECT",
            "properties", Map.of(
                "type", str,
                "question", str,
                "options", Map.of("type", "ARRAY", "items", str),
                "answerIndex", Map.of("type", "INTEGER"),
                "answerText", str,
                "explanation", str
            ),
            "required", List.of("type", "question", "options", "answerIndex", "answerText", "explanation")
        );

        Map<String, Object> schema = Map.of(
            "type", "OBJECT",
            "properties", Map.of(
                "flashcards", Map.of("type", "ARRAY", "items", flashItem),
                "questions", Map.of("type", "ARRAY", "items", qItem)
            ),
            "required", List.of("flashcards", "questions")
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