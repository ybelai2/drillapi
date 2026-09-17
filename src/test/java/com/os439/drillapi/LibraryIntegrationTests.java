package com.os439.drillapi;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import com.fasterxml.jackson.databind.ObjectMapper;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest @AutoConfigureMockMvc
class LibraryIntegrationTests {
    @Autowired MockMvc mvc;
    @Autowired Accounts accounts;
    @Autowired AuthSessions sessions;
    @Autowired Courses courses;
    @Autowired StudyClasses classes;
    @Autowired Decks decks;
    private final ObjectMapper json=new ObjectMapper();
    private String signup(String email) throws Exception {
        String response=mvc.perform(post("/api/auth/signup").contentType("application/json")
            .content(json.writeValueAsString(Map.of("email",email,"password","correct-horse-123","name","Test Student"))))
            .andExpect(status().isCreated()).andExpect(jsonPath("user.email").value(email.toLowerCase()))
            .andExpect(jsonPath("user.passwordHash").doesNotExist()).andReturn().getResponse().getContentAsString();
        return json.readTree(response).path("token").asText();
    }
    private String create(String path,String token,Object body) throws Exception {
        var response=mvc.perform(post(path).header("Authorization","Bearer "+token).contentType("application/json")
            .content(json.writeValueAsString(body))).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return json.readTree(response).path("id").asText();
    }
    @Test void authenticationLifecycle() throws Exception {
        String email=UUID.randomUUID()+"@example.com";
        String token=signup(email);
        assertNotEquals("correct-horse-123",accounts.findByEmail(email).orElseThrow().passwordHash);
        assertTrue(sessions.findById(AuthController.hash(token)).isPresent());
        assertFalse(sessions.findById(token).isPresent());
        mvc.perform(post("/api/auth/signup").contentType("application/json").content(json.writeValueAsString(
            Map.of("email",email.toUpperCase(),"password","correct-horse-123","name","Another")))).andExpect(status().isConflict());
        mvc.perform(post("/api/auth/signup").contentType("application/json").content("{\"email\":\"bad\",\"password\":\"short\",\"name\":\"\"}")).andExpect(status().isBadRequest());
        mvc.perform(post("/api/auth/login").contentType("application/json").content(json.writeValueAsString(
            Map.of("email",email,"password","incorrect")))).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/login").contentType("application/json").content(json.writeValueAsString(
            Map.of("email",email.toUpperCase(),"password","correct-horse-123")))).andExpect(status().isOk()).andExpect(jsonPath("token").isString());
        mvc.perform(get("/api/auth/me").header("Authorization","Bearer "+token)).andExpect(status().isOk());
        mvc.perform(post("/api/auth/logout").header("Authorization","Bearer "+token)).andExpect(status().isNoContent());
        mvc.perform(get("/api/courses").header("Authorization","Bearer "+token)).andExpect(status().isUnauthorized());
        String expired=signup(UUID.randomUUID()+"@example.com");
        var s=sessions.findById(AuthController.hash(expired)).orElseThrow(); s.expiresAt=Instant.now().minusSeconds(1); sessions.save(s);
        mvc.perform(get("/api/courses").header("Authorization","Bearer "+expired)).andExpect(status().isUnauthorized());
    }
    @Test void libraryCrudIsPersistentAndIsolated() throws Exception {
        String owner=signup(UUID.randomUUID()+"@example.com"), stranger=signup(UUID.randomUUID()+"@example.com");
        var course=Map.of("title","Networks","code","COSC 350","semester","Fall 2026","description","Networking course");
        String courseId=create("/api/courses",owner,course), coursePath="/api/courses/"+courseId;
        assertTrue(courses.existsById(UUID.fromString(courseId)));
        mvc.perform(get("/api/courses").header("Authorization","Bearer "+stranger)).andExpect(status().isOk()).andExpect(content().json("[]"));
        var lesson=Map.of("title","Transport","notes","Review TCP","studied",false);
        String classId=create(coursePath+"/classes",owner,lesson), classPath=coursePath+"/classes/"+classId;
        String content="{\"flashcards\":[{\"front\":\"TCP?\",\"back\":\"Transport protocol\"}],\"questions\":[{\"type\":\"tf\",\"question\":\"TCP is reliable\",\"options\":[\"True\",\"False\"],\"answerIndex\":0,\"answerText\":\"\",\"explanation\":\"Uses acknowledgments\"}]}";
        var deck=Map.of("title","TCP practice","content",content);
        String deckId=create(classPath+"/decks",owner,deck), deckPath=classPath+"/decks/"+deckId;
        for(String path:new String[]{coursePath,classPath,deckPath}) {
            mvc.perform(get(path).header("Authorization","Bearer "+owner)).andExpect(status().isOk());
            mvc.perform(get(path).header("Authorization","Bearer "+stranger)).andExpect(status().isNotFound());
            mvc.perform(delete(path).header("Authorization","Bearer "+stranger)).andExpect(status().isNotFound());
        }
        mvc.perform(put(coursePath).header("Authorization","Bearer "+stranger).contentType("application/json").content(json.writeValueAsString(course))).andExpect(status().isNotFound());
        mvc.perform(put(classPath).header("Authorization","Bearer "+stranger).contentType("application/json").content(json.writeValueAsString(lesson))).andExpect(status().isNotFound());
        mvc.perform(put(deckPath).header("Authorization","Bearer "+stranger).contentType("application/json").content(json.writeValueAsString(deck))).andExpect(status().isNotFound());
        mvc.perform(post(classPath+"/decks").header("Authorization","Bearer "+stranger).contentType("application/json").content(json.writeValueAsString(deck))).andExpect(status().isNotFound());
        mvc.perform(put(coursePath).header("Authorization","Bearer "+owner).contentType("application/json").content(json.writeValueAsString(course))).andExpect(status().isOk());
        mvc.perform(put(classPath).header("Authorization","Bearer "+owner).contentType("application/json").content(json.writeValueAsString(
            Map.of("title","Transport revised","notes","Reviewed","studied",true)))).andExpect(status().isOk()).andExpect(jsonPath("studied").value(true));
        mvc.perform(put(deckPath).header("Authorization","Bearer "+owner).contentType("application/json").content(json.writeValueAsString(
            Map.of("title","Renamed deck","content",content)))).andExpect(status().isOk()).andExpect(jsonPath("title").value("Renamed deck"));
        mvc.perform(put(deckPath).header("Authorization","Bearer "+owner).contentType("application/json").content("{\"title\":\"Bad\",\"content\":\"{}\"}")).andExpect(status().isBadRequest());
        mvc.perform(delete(deckPath).header("Authorization","Bearer "+owner)).andExpect(status().isNoContent());
        assertFalse(decks.existsById(UUID.fromString(deckId)));
        String cascadeDeck=create(classPath+"/decks",owner,deck);
        mvc.perform(delete(classPath).header("Authorization","Bearer "+owner)).andExpect(status().isNoContent());
        assertFalse(decks.existsById(UUID.fromString(cascadeDeck)));
        String cascadeClass=create(coursePath+"/classes",owner,lesson);
        mvc.perform(delete(coursePath).header("Authorization","Bearer "+owner)).andExpect(status().isNoContent());
        assertFalse(classes.existsById(UUID.fromString(cascadeClass)));
    }
    @Test void corsAndAnonymousAccess() throws Exception {
        mvc.perform(get("/api/courses")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/generate")).andExpect(status().isUnauthorized());
        mvc.perform(get("/ping")).andExpect(status().isOk());
        mvc.perform(options("/api/courses").header("Origin","http://localhost:5173")
            .header("Access-Control-Request-Method","DELETE").header("Access-Control-Request-Headers","Authorization"))
            .andExpect(status().isOk()).andExpect(header().string("Access-Control-Allow-Origin","http://localhost:5173"));
        mvc.perform(options("/api/courses").header("Origin","https://untrusted.example")
            .header("Access-Control-Request-Method","GET")).andExpect(status().isForbidden());
    }
}
