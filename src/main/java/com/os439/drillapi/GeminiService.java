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
            You are building a study package for a cumulative Operating Systems final
            (COSC 439, Chapters 1-14). The exam rewards two very different skills, so you
            must generate two distinct kinds of content instead of generic definitional recall.

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
            These test mechanism and trade-off, not just definitions. A good concept
            question forces the student to explain WHAT the OS does, WHY it does it that
            way, and WHAT the cost/trade-off is (performance, security, complexity).
            Prioritize contrast pairs explicitly named in the course material, e.g.:
            process vs thread, kernel vs shell, logical vs physical address, blocking vs
            asynchronous I/O, protection vs security, mutex vs semaphore, ACL vs capability
            list, symmetric vs asymmetric encryption.
            Weight concept questions toward FINAL-WEEK material (Ch 12 I/O Systems,
            Ch 13 Protection, Ch 14 Security) since it is the least-drilled material:
            least privilege, access matrices, ACLs, capability lists, revocation,
            common attacks (masquerading, replay, man-in-the-middle, session hijacking,
            buffer overflow, worms, port scans, DoS), authentication, hashes, MACs,
            digital signatures, certificates, firewalls, defense in depth.
            At least 40%% of concept questions must come from Ch 12-14.

            --- CALCULATION questions ---
            These must be genuine numeric/worked problems, not vocabulary. Generate
            calculation questions covering these exact categories whenever the slide
            content supports it:
              - CPU scheduling Gantt charts (FCFS, SJF, SRTF, Priority, Round Robin) —
                give a specific process/burst-time table and ask for waiting time,
                turnaround time, or completion order.
              - Address translation: physical address = logical address + relocation/MMU value.
              - Demand paging effective access time, given memory access time and
                page-fault rate.
              - Page replacement fault counts (FIFO, LRU, second-chance) given a
                reference string.
              - Disk scheduling total head movement (FCFS, SSTF, SCAN, C-SCAN, LOOK, C-LOOK)
                given a starting head position and request queue.
              - Disk I/O average access time = seek time + rotational latency + transfer
                time + controller overhead.
              - Unix/Linux permission conversion: octal value to owner/group/others rwx.
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
