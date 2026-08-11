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

    /**
     * Generates study materials (flashcards and questions) for any course slides.
     *
     * @param slideText     the content from course slides
     * @param courseContext optional course description (e.g., "Introduction to Biology",
     *                      "COSC 439 Operating Systems"). If null or empty, generates
     *                      generic study materials.
     * @return JSON string containing flashcards and questions
     * @throws Exception if Gemini API call fails
     */
    public String generateQuestions(String slideText, String courseContext) throws Exception {

        // Prevent gigantic prompts from overwhelming Gemini
        final int MAX_INPUT_CHARS = 120000;

        if (slideText.length() > MAX_INPUT_CHARS) {
            slideText = slideText.substring(0, MAX_INPUT_CHARS);
        }

        // Build course-aware prompt preamble
        String courseContextPrompt = buildCourseContextPrompt(courseContext);

        String prompt = courseContextPrompt + """

            From the SLIDE TEXT below, return JSON with two parts: "flashcards" and "questions".

            ============================================================
            FLASHCARDS
            ============================================================
            - Extract EVERY important concept, definition, process, algorithm, acronym,
              formula, architecture component, and key fact.
            - "front" = active recall question
            - "back" = concise answer
            - Do NOT cap the number of cards. Generate enough to thoroughly cover all content.

            ============================================================
            QUESTIONS - TWO CATEGORIES REQUIRED
            ============================================================
            Every question object must be tagged with a "category" field: either
            "concept" or "calculation". Do not blur the two.

            --- CONCEPT questions ---
            These test mechanism, trade-offs, and deep understanding, not just definitions.
            A good concept question forces the student to explain WHAT happens, WHY it happens
            that way, and WHAT the cost/trade-off is (performance, security, complexity,
            usability, maintainability). Prioritize contrast pairs and relationships found
            in the course material.
            Weight concept questions toward material that is more conceptually challenging
            and less drilled through repetition.

            --- CALCULATION questions ---
            These must be genuine numeric/worked problems, not vocabulary. Generate
            calculation questions covering these categories whenever the slide content supports:
              - Quantitative computations (formulas, math problems)
              - Step-by-step procedures requiring numerical outputs
              - Data conversions (binary, decimal, hexadecimal, etc.)
              - Performance calculations and analysis
              - Any domain-specific numeric problem-solving
            For calculation questions, the "explanation" field must show the actual
            worked steps (the numbers plugged in, not just the final answer), so a
            student who gets it wrong can see exactly where their math diverged.

            ============================================================
            FORMAT RULES
            ============================================================
            - Generate enough questions to test mastery of all material.
            - Minimum 25 questions. For large slide decks generate 40-75 questions.
            - Across ALL questions, mix format types:
                40%% Multiple Choice
                30%% True/False
                30%% Fill In Blank
            - Calculation questions should mostly be Multiple Choice or Fill In Blank
              (a specific numeric answer) rather than True/False, since True/False
              can't meaningfully test whether the math was done correctly.

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
            - answerText = missing phrase or computed number

            Include an explanation for every question. For concept questions this is
            the mechanism/why/cost reasoning. For calculation questions this is the
            worked-out steps.

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
                        "category", str,
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
                        "category",
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

    /**
     * Backward-compatible overload for course-specific generation (e.g., COSC 439).
     *
     * @param slideText the content from course slides
     * @return JSON string containing flashcards and questions
     * @throws Exception if Gemini API call fails
     */
    public String generateQuestions(String slideText) throws Exception {
        // Default to course-agnostic generation
        return generateQuestions(slideText, null);
    }

    /**
     * Builds a course-context-aware prompt preamble.
     * If courseContext is provided, customizes the prompt for that course.
     * Otherwise, generates a generic prompt.
     *
     * @param courseContext course description or null/empty for generic mode
     * @return prompt preamble string
     */
    private String buildCourseContextPrompt(String courseContext) {
        if (courseContext == null || courseContext.isBlank()) {
            return "You are building a comprehensive study package for the given course material. "
                    + "Generate study materials that test both conceptual understanding and practical application.";
        }

        // For well-known courses, provide specialized guidance
        if (courseContext.toLowerCase().contains("operating system")
                || courseContext.toLowerCase().contains("cosc 439")
                || courseContext.toLowerCase().contains("os")) {
            return """
                    You are building a study package for a course on Operating Systems.
                    The course emphasizes both theoretical concepts and practical system design.
                    Pay special attention to:
                    - Process/thread management and synchronization
                    - Memory management (paging, segmentation, virtual memory)
                    - I/O systems and disk scheduling
                    - File systems and protection/security mechanisms
                    - Trade-offs between performance, security, and complexity""";
        }

        if (courseContext.toLowerCase().contains("algorithm")
                || courseContext.toLowerCase().contains("data structure")) {
            return """
                    You are building a study package for a course on Algorithms and Data Structures.
                    The course emphasizes both algorithmic thinking and implementation.
                    Pay special attention to:
                    - Time and space complexity analysis (Big O notation)
                    - Algorithm design paradigms (greedy, divide-and-conquer, dynamic programming)
                    - Data structure properties and trade-offs
                    - Practical implementation and edge cases""";
        }

        if (courseContext.toLowerCase().contains("database")) {
            return """
                    You are building a study package for a course on Databases.
                    The course emphasizes both relational theory and practical database design.
                    Pay special attention to:
                    - Relational model and normalization
                    - SQL and query optimization
                    - Transaction management and concurrency control
                    - Indexing and performance tuning""";
        }

        // Generic fallback with course name
        return "You are building a comprehensive study package for " + courseContext.trim() + ". "
                + "Generate study materials that test both conceptual understanding and practical application.";
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
