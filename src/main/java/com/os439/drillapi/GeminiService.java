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

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .build();

    public String generateQuestions(String slideText) throws Exception {

        // Prevent gigantic prompts from overwhelming Gemini
        final int MAX_INPUT_CHARS = 120000;

        if (slideText.length() > MAX_INPUT_CHARS) {
            slideText = slideText.substring(0, MAX_INPUT_CHARS);
        }

        String prompt = """
            You are a computer science professor building a COMPLETE study package from lecture slides.

            From the SLIDE TEXT below, return JSON with two parts:
            "flashcards" and "questions".

            FLASHCARDS:
            - Extract EVERY important concept, definition, process,
              algorithm, acronym, formula, architecture component,
              and key fact.
            - "front" = active recall question
            - "back" = concise answer
            - Do NOT cap the number of cards.
            - Generate enough cards to thoroughly cover all content.

            QUESTIONS:
            - Generate enough questions to test mastery of all material.
            - Minimum 25 questions.
            - For large slide decks generate 40-75 questions.
            - Mix:
                40%% Multiple Choice
                30%% True/False
                30%% Fill In Blank

            Rules:

            MC:
            - Exactly 4 options
            - answerIndex = 0-3
            - answerText = ""

            TF:
            - options = ["True","False"]
            - answerIndex = 0 if true, 1 if false
            - answerText = ""

            Fill:
            - question contains _____
            - options = []
            - answerIndex = -1
            - answerText = missing phrase

            Include a one-sentence explanation for every question.

            Use ONLY information contained in the slides.

            Return VALID JSON ONLY.

            SLIDE TEXT:
            """ + slideText;

        Map<String, Object> str = Map.of(
                "type", "STRING"
        );

        Map<String, Object> flashItem = Map.of(
                "type", "OBJECT",
                "properties", Map.of(
                        "front", str,
                        "back", str
                ),
                "required", List.of(
                        "front",
                        "back"
                )
        );

        Map<String, Object> qItem = Map.of(
                "type", "OBJECT",
                "properties", Map.of(
                        "type", str,
                        "question", str,
                        "options", Map.of(
                                "type", "ARRAY",
                                "items", str
                        ),
                        "answerIndex", Map.of(
                                "type", "INTEGER"
                        ),
                        "answerText", str,
                        "explanation", str
                ),
                "required", List.of(
                        "type",
                        "question",
                        "options",
                        "answerIndex",
                        "answerText",
                        "explanation"
                )
        );

        Map<String, Object> schema = Map.of(
                "type", "OBJECT",
                "properties", Map.of(
                        "flashcards", Map.of(
                                "type", "ARRAY",
                                "items", flashItem
                        ),
                        "questions", Map.of(
                                "type", "ARRAY",
                                "items", qItem
                        )
                ),
                "required", List.of(
                        "flashcards",
                        "questions"
                )
        );

        Map<String, Object> body = Map.of(
                "contents", List.of(
                        Map.of(
                                "parts", List.of(
                                        Map.of(
                                                "text", prompt
                                        )
                                )
                        )
                ),
                "generationConfig", Map.of(
                        "responseMimeType", "application/json",
                        "responseSchema", schema,
                        "maxOutputTokens", 65536
                )
        );

        String url =
                "https://generativelanguage.googleapis.com/v1beta/models/"
                        + model
                        + ":generateContent";

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("x-goog-api-key", apiKey)
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(240))
                .POST(
                        HttpRequest.BodyPublishers.ofString(
                                mapper.writeValueAsString(body)
                        )
                )
                .build();

        HttpResponse<String> response = callGeminiWithRetries(request);

        JsonNode root = mapper.readTree(response.body());

        JsonNode textNode =
                root.path("candidates")
                        .path(0)
                        .path("content")
                        .path("parts")
                        .path(0)
                        .path("text");

        if (textNode.isMissingNode()) {
            throw new RuntimeException(
                    "Gemini returned an unexpected response format."
            );
        }

        return textNode.asText();
    }

    private HttpResponse<String> callGeminiWithRetries(
            HttpRequest request
    ) throws Exception {

        int maxRetries = 3;

        for (int attempt = 1; attempt <= maxRetries; attempt++) {

            HttpResponse<String> response =
                    http.send(
                            request,
                            HttpResponse.BodyHandlers.ofString()
                    );

            int code = response.statusCode();

            if (code == 200) {
                return response;
            }

            if (code == 503) {

                if (attempt == maxRetries) {
                    throw new RuntimeException(
                            "Gemini is currently experiencing high demand. Please try again in a few minutes."
                    );
                }

                long waitSeconds = attempt * 5L;

                System.out.println(
                        "Gemini 503 received. Retrying in "
                                + waitSeconds
                                + " seconds..."
                );

                Thread.sleep(waitSeconds * 1000);

                continue;
            }

            throw new RuntimeException(
                    "Gemini error "
                            + code
                            + ": "
                            + response.body()
            );
        }

        throw new RuntimeException(
                "Failed to contact Gemini after multiple attempts."
        );
    }
}
