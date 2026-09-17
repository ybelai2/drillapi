package com.os439.drillapi;

import org.apache.poi.sl.usermodel.Shape;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFSlide;
import org.apache.poi.xslf.usermodel.XSLFTextShape;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

@RestController
public class SlideController {

    private final GeminiService gemini;

    public SlideController(GeminiService gemini) {
        this.gemini = gemini;
    }

    @GetMapping("/api/test")
    public String test() {
        return "CORS TEST";
    }

    @PostMapping("/api/extract")
    public List<String> extract(@RequestParam("files") MultipartFile[] files) throws Exception {
        validateFiles(files);
        List<String> allSlides = new ArrayList<>();

        for (MultipartFile file : files) {
            allSlides.addAll(extractSlides(file));
        }

        return allSlides;
    }

    @PostMapping("/api/generate")
    public String generate(@RequestParam("files") MultipartFile[] files) throws Exception {
        return generateForCourse(files, null);
    }

    String generateForCourse(MultipartFile[] files, String context) throws Exception {
        validateFiles(files);
        List<String> allSlides = new ArrayList<>();
        for (MultipartFile file : files) allSlides.addAll(extractSlides(file));
        try {
            return gemini.generateQuestions(String.join("\n", allSlides), context);
        } catch (Exception e) {
            if(e instanceof InterruptedException) Thread.currentThread().interrupt();
            throw new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.BAD_GATEWAY, "Study generation is unavailable. Please try again later.");
        }
    }

    private void validateFiles(MultipartFile[] files) {
        if (files.length == 0 || files.length > 10)
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST,"Upload 1–10 PowerPoint files.");
        for (var file : files) {
            String name=file.getOriginalFilename();
            if(file.isEmpty() || name==null || !name.toLowerCase(java.util.Locale.ROOT).endsWith(".pptx"))
                throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST,"Only nonempty .pptx files are supported.");
        }
    }

    private List<String> extractSlides(MultipartFile file) throws Exception {
        List<String> slides = new ArrayList<>();

        try (
                InputStream is = file.getInputStream();
                XMLSlideShow ppt = new XMLSlideShow(is)
        ) {
            int n = 1;

            for (XSLFSlide slide : ppt.getSlides()) {
                StringBuilder sb = new StringBuilder("=== Slide " + n + " ===\n");

                for (Shape<?, ?> shape : slide.getShapes()) {
                    if (shape instanceof XSLFTextShape textShape) {
                        String text = textShape.getText();

                        if (text != null && !text.isBlank()) {
                            sb.append(text).append("\n");
                        }
                    }
                }

                slides.add(sb.toString());
                n++;
            }
        } catch (Exception e) {
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST,"Could not read this PowerPoint. Upload a valid .pptx file.");
        }

        return slides;
    }
}
