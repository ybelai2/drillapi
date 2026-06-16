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
            You are a computer science professor building a COMPLETE study package from lecture slides.
            From the SLIDE TEXT below, return JSON with two parts: "flashcards" and "questions".

            FLASHCARDS:
            - Extract EVERY important concept, definition, process, algorithm, acronym, formula,
              architecture component, and key fact in the slides.
            - "front" = an active-recall prompt (prefer "How does X differ from Y?" or
              "Why does X happen?" over "What is X?"). "back" = a concise, correct answer.
            - Do NOT cap the count. Scale to the material: a small deck may need 15-25 cards,
              a large or combined deck may need 40-100+. Aim for thorough coverage, not a fixed number.

            QUESTIONS:
            - Generate enough to test mastery of ALL the material (minimum 25; for large or
              combined decks, 40-75). Scale to the volume of slide content provided.
            - Mix three types in roughly 40% mc / 30% tf / 30% fill proportion.
            - Include definition, conceptual, application, and comparison questions.
            - type "mc"  : multiple choice. Exactly 4 entries in "options",
                           "answerIndex" = correct option (0-3), "answerText" = "".
            - type "tf"  : true/false. "options" = ["True","False"],
                           "answerIndex" = 0 if true or 1 if false, "answerText" = "".
            - type "fill": fill in the blank. "question" contains the blank as _____ ,
                           "answerText" = the missing word/phrase, "options" = [], "answerIndex" = -1.

            Give every question a one-sentence "explanation".
            Use ONLY facts present in the slides. Do not invent content beyond the slides.
            When the slides contain multiple decks (marked with "########## DECK ... "),
            cover every deck proportionally.

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
                "responseSchema", schema,
                // Big study sets produce big JSON. Without a high cap the response is
                // truncated mid-array and JSON parsing fails. 65536 is the model max.
                "maxOutputTokens", 65536
            )
        );

        String url = "https://generativelanguage.googleapis.com/v1beta/models/"
                     + model + ":generateContent";

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("x-goog-api-key", apiKey)
            .header("Content-Type", "application/json")
            // Larger generations take longer; 60s was too tight for big multi-deck sets.
            .timeout(Duration.ofSeconds(180))
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
