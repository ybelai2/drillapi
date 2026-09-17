package com.os439.drillapi;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.security.Principal;
import java.util.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@RestController @RequestMapping("/api/courses")
class LibraryController {
    private final Courses courses;
    private final StudyClasses classes;
    private final Decks decks;
    private final SlideController slides;
    LibraryController(Courses courses, StudyClasses classes, Decks decks, SlideController slides) {
        this.courses=courses; this.classes=classes; this.decks=decks; this.slides=slides;
    }
    record CourseInput(@NotBlank @Size(max=160) String title, @NotNull @Size(max=40) String code,
        @NotNull @Size(max=80) String semester, @NotNull @Size(max=2000) String description) {}
    record ClassInput(@NotBlank @Size(max=160) String title, @NotNull @Size(max=10000) String notes, boolean studied) {}
    record DeckInput(@NotBlank @Size(max=160) String title, @NotBlank @Size(max=2000000) String content) {}
    private Course course(UUID id, Principal p) {
        return courses.findByIdAndOwnerId(id,UUID.fromString(p.getName())).orElseThrow(LibraryController::missing);
    }
    private StudyClass lesson(UUID courseId,UUID classId,Principal p) {
        course(courseId,p);
        return classes.findByIdAndCourseId(classId,courseId).orElseThrow(LibraryController::missing);
    }
    private Deck deck(UUID courseId,UUID classId,UUID deckId,Principal p) {
        lesson(courseId,classId,p);
        return decks.findByIdAndClassId(deckId,classId).orElseThrow(LibraryController::missing);
    }
    private static ResponseStatusException missing() { return new ResponseStatusException(HttpStatus.NOT_FOUND,"Record not found."); }
    @GetMapping List<Course> list(Principal p) { return courses.findByOwnerIdOrderByTitleAsc(UUID.fromString(p.getName())); }
    @GetMapping("/{courseId}") Course get(@PathVariable UUID courseId,Principal p) { return course(courseId,p); }
    @PostMapping @ResponseStatus(HttpStatus.CREATED)
    Course create(@Valid @RequestBody CourseInput input,Principal p) {
        Course c=new Course(); c.id=UUID.randomUUID(); c.ownerId=UUID.fromString(p.getName());
        return save(c,input);
    }
    @PutMapping("/{courseId}") Course update(@PathVariable UUID courseId,@Valid @RequestBody CourseInput input,Principal p) {
        return save(course(courseId,p),input);
    }
    private Course save(Course c,CourseInput input) {
        c.title=input.title().trim(); c.code=input.code().trim(); c.semester=input.semester().trim();
        c.description=input.description().trim(); return courses.save(c);
    }
    @DeleteMapping("/{courseId}") @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@PathVariable UUID courseId,Principal p) { courses.delete(course(courseId,p)); }
    @GetMapping("/{courseId}/classes") List<StudyClass> classes(@PathVariable UUID courseId,Principal p) {
        course(courseId,p); return classes.findByCourseIdOrderByTitleAsc(courseId);
    }
    @GetMapping("/{courseId}/classes/{classId}") StudyClass getClass(@PathVariable UUID courseId,@PathVariable UUID classId,Principal p) {
        return lesson(courseId,classId,p);
    }
    @PostMapping("/{courseId}/classes") @ResponseStatus(HttpStatus.CREATED)
    StudyClass createClass(@PathVariable UUID courseId,@Valid @RequestBody ClassInput input,Principal p) {
        course(courseId,p); StudyClass c=new StudyClass(); c.id=UUID.randomUUID(); c.courseId=courseId; return saveClass(c,input);
    }
    @PutMapping("/{courseId}/classes/{classId}") StudyClass updateClass(@PathVariable UUID courseId,@PathVariable UUID classId,
        @Valid @RequestBody ClassInput input,Principal p) { return saveClass(lesson(courseId,classId,p),input); }
    private StudyClass saveClass(StudyClass c,ClassInput input) {
        c.title=input.title().trim(); c.notes=input.notes(); c.studied=input.studied(); return classes.save(c);
    }
    @DeleteMapping("/{courseId}/classes/{classId}") @ResponseStatus(HttpStatus.NO_CONTENT)
    void deleteClass(@PathVariable UUID courseId,@PathVariable UUID classId,Principal p) { classes.delete(lesson(courseId,classId,p)); }
    @GetMapping("/{courseId}/classes/{classId}/decks") List<Deck> decks(@PathVariable UUID courseId,@PathVariable UUID classId,Principal p) {
        lesson(courseId,classId,p); return decks.findByClassIdOrderByTitleAsc(classId);
    }
    @GetMapping("/{courseId}/classes/{classId}/decks/{deckId}") Deck getDeck(@PathVariable UUID courseId,@PathVariable UUID classId,@PathVariable UUID deckId,Principal p) {
        return deck(courseId,classId,deckId,p);
    }
    @PostMapping("/{courseId}/classes/{classId}/decks") @ResponseStatus(HttpStatus.CREATED)
    Deck createDeck(@PathVariable UUID courseId,@PathVariable UUID classId,@Valid @RequestBody DeckInput input,Principal p) {
        lesson(courseId,classId,p); Deck d=new Deck(); d.id=UUID.randomUUID(); d.classId=classId; return saveDeck(d,input);
    }
    @PutMapping("/{courseId}/classes/{classId}/decks/{deckId}") Deck updateDeck(@PathVariable UUID courseId,@PathVariable UUID classId,@PathVariable UUID deckId,
        @Valid @RequestBody DeckInput input,Principal p) { return saveDeck(deck(courseId,classId,deckId,p),input); }
    @DeleteMapping("/{courseId}/classes/{classId}/decks/{deckId}") @ResponseStatus(HttpStatus.NO_CONTENT)
    void deleteDeck(@PathVariable UUID courseId,@PathVariable UUID classId,@PathVariable UUID deckId,Principal p) { decks.delete(deck(courseId,classId,deckId,p)); }
    private Deck saveDeck(Deck d,DeckInput input) {
        validateContent(input.content()); d.title=input.title().trim(); d.content=input.content(); return decks.save(d);
    }
    static void validateContent(String content) {
        try {
            var root=new ObjectMapper().readTree(content);
            if(root==null || !root.path("flashcards").isArray() || !root.path("questions").isArray()
                || root.path("questions").isEmpty() || content.length()>2000000) throw new IllegalArgumentException();
            for(var card:root.path("flashcards"))
                if(!card.path("front").isTextual() || !card.path("back").isTextual()) throw new IllegalArgumentException();
            for(var q:root.path("questions")) {
                if(!q.path("question").isTextual() || !q.path("options").isArray() || !q.path("explanation").isTextual()
                    || !q.path("answerText").isTextual() || !q.path("answerIndex").isIntegralNumber()) throw new IllegalArgumentException();
                String type=q.path("type").asText().toLowerCase(Locale.ROOT).replaceAll("[^a-z]", "");
                boolean fill=Set.of("fill","fillintheblank","fillinblank","fillintheblanks").contains(type);
                boolean mc=Set.of("mc","multiplechoice").contains(type);
                boolean tf=Set.of("tf","truefalse","trueorfalse").contains(type);
                if(!fill && !mc && !tf) throw new IllegalArgumentException();
                if(fill && (q.path("answerText").asText().isBlank() || q.path("options").size()!=0)) throw new IllegalArgumentException();
                if(!fill && (q.path("options").size()!=(tf ? 2 : 4) || q.path("answerIndex").asInt()<0
                    || q.path("answerIndex").asInt()>=q.path("options").size())) throw new IllegalArgumentException();
                for(var option:q.path("options")) if(!option.isTextual()) throw new IllegalArgumentException();
            }
        } catch(Exception e) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Study content must contain valid flashcards and questions."); }
    }
    @PostMapping("/{courseId}/classes/{classId}/generate") @ResponseStatus(HttpStatus.CREATED)
    Deck generate(@PathVariable UUID courseId,@PathVariable UUID classId,@RequestParam("files") MultipartFile[] files,
        @RequestParam("title") String title,Principal p) throws Exception {
        var c=course(courseId,p); lesson(courseId,classId,p);
        if(title.isBlank() || title.length()>160) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Deck title must be 1–160 characters.");
        String content=slides.generateForCourse(files,c.code+" "+c.title);
        try { validateContent(content); }
        catch(ResponseStatusException e) { throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,"AI returned invalid study content. Please try again."); }
        // Recheck after the potentially long AI request in case the class was deleted.
        lesson(courseId,classId,p);
        Deck d=new Deck(); d.id=UUID.randomUUID(); d.classId=classId; return saveDeck(d,new DeckInput(title,content));
    }
}
