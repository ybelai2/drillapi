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
@CrossOrigin(
        origins = {
                "https://os439-frontend.vercel.app",
                "http://localhost:5173"
        },
        allowedHeaders = "*",
        methods = {
                RequestMethod.GET,
                RequestMethod.POST,
                RequestMethod.OPTIONS
        }
)
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
        List<String> allSlides = new ArrayList<>();

        for (MultipartFile file : files) {
            allSlides.addAll(extractSlides(file));
        }

        return allSlides;
    }

    @PostMapping("/api/generate")
    public String generate(@RequestParam("files") MultipartFile[] files) throws Exception {
        List<String> allSlides = new ArrayList<>();

        for (MultipartFile file : files) {
            allSlides.addAll(extractSlides(file));
        }

        String text = String.join("\n", allSlides);

        return gemini.generateQuestions(text);
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
        }

        return slides;
    }
}
