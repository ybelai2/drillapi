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

    @PostMapping("/api/extract")
    public List<String> extract(@RequestParam("file") MultipartFile file) throws Exception {
        return extractSlides(file);
    }

    @PostMapping("/api/generate")
    public String generate(@RequestParam("file") MultipartFile file) throws Exception {
        String text = String.join("\n", extractSlides(file));
        return gemini.generateQuestions(text);
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