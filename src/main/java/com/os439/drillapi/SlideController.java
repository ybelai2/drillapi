package com.os439.drillapi;

import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFSlide;
import org.apache.poi.xslf.usermodel.XSLFTextShape;
import org.apache.poi.sl.usermodel.Shape;
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

    // Accepts one OR many files. A single upload still works (1-element array),
    // so the old single-file curl and the old frontend remain compatible.
    @PostMapping("/api/extract")
    public List<String> extract(@RequestParam("file") MultipartFile[] files) throws Exception {
        List<String> all = new ArrayList<>();
        for (MultipartFile f : files) {
            all.addAll(extractSlides(f));
        }
        return all;
    }

    @PostMapping("/api/generate")
    public String generate(@RequestParam("file") MultipartFile[] files) throws Exception {
        StringBuilder combined = new StringBuilder();
        for (MultipartFile f : files) {
            String name = (f.getOriginalFilename() == null) ? "deck" : f.getOriginalFilename();
            combined.append("\n########## DECK: ").append(name).append(" ##########\n");
            combined.append(String.join("\n", extractSlides(f)));
        }
        return gemini.generateQuestions(combined.toString());
    }

    private List<String> extractSlides(MultipartFile file) throws Exception {
        List<String> slides = new ArrayList<>();
        try (InputStream is = file.getInputStream();
             XMLSlideShow ppt = new XMLSlideShow(is)) {
            int n = 1;
            for (XSLFSlide slide : ppt.getSlides()) {
                StringBuilder sb = new StringBuilder("=== Slide " + n + " ===\n");
                for (Shape<?, ?> shape : slide.getShapes()) {
                    if (shape instanceof XSLFTextShape textShape) {
                        String t = textShape.getText();
                        if (t != null && !t.isBlank()) {
                            sb.append(t).append("\n");
                        }
                    }
                }
                slides.add(sb.toString());
                n++;
            }
        }
        return slides;
    }
}
