package com.heraim.eco.service;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

@Service
public class PdfExtractionService {

    private static final Logger log = LoggerFactory.getLogger(PdfExtractionService.class);

    private final int minTextLength;
    private final String tesseractExecutable;
    private final String tesseractLanguage;
    private final String tesseractDatapath;

    public PdfExtractionService(
            @Value("${pdf.extraction.min-text-length:10}") int minTextLength,
            @Value("${tesseract.executable:}") String tesseractExecutable,
            @Value("${tesseract.language:eng}") String tesseractLanguage,
            @Value("${tesseract.datapath:}") String tesseractDatapath) {
        this.minTextLength = minTextLength;
        this.tesseractExecutable = (tesseractExecutable != null && !tesseractExecutable.isBlank())
                ? tesseractExecutable
                : findTesseractExecutable();
        this.tesseractLanguage = (tesseractLanguage != null && !tesseractLanguage.isBlank())
                ? tesseractLanguage
                : "eng";
        this.tesseractDatapath = resolveDatapath(tesseractDatapath);
    }

    public String extractText(MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            return "";
        }
        return extractText(file.getBytes());
    }

    public String extractText(InputStream inputStream) throws IOException {
        if (inputStream == null) {
            return "";
        }
        return extractText(inputStream.readAllBytes());
    }

    public String extractText(byte[] pdfBytes) throws IOException {
        if (pdfBytes == null || pdfBytes.length == 0) {
            return "";
        }

        try (PDDocument document = Loader.loadPDF(pdfBytes)) {
            PDFTextStripper stripper = new PDFTextStripper();
            String digitalText = stripper.getText(document).trim();

            if (digitalText.length() >= minTextLength) {
                log.debug("Extracted {} characters using digital text stripper (PDFBox)", digitalText.length());
                return digitalText;
            }

            log.info("Digital text length ({}) is below threshold ({}). Falling back to Tesseract OCR CLI",
                    digitalText.length(), minTextLength);
            return extractWithOcr(document);
        }
    }

    private String extractWithOcr(PDDocument document) {
        if (tesseractExecutable == null) {
            throw new IllegalStateException("Tesseract OCR executable not found on system. " +
                    "Install Tesseract (e.g., 'brew install tesseract' or 'apt-get install tesseract-ocr') " +
                    "or set 'tesseract.executable' property.");
        }

        try {
            PDFRenderer renderer = new PDFRenderer(document);
            StringBuilder ocrBuilder = new StringBuilder();

            for (int page = 0; page < document.getNumberOfPages(); page++) {
                BufferedImage pageImage = renderer.renderImageWithDPI(page, 300, ImageType.RGB);
                File tempImageFile = File.createTempFile("pdf_page_ocr_", ".png");
                try {
                    ImageIO.write(pageImage, "png", tempImageFile);
                    String pageText = runTesseractCli(tempImageFile);
                    if (pageText != null && !pageText.isBlank()) {
                        ocrBuilder.append(pageText.trim()).append("\n");
                    }
                } finally {
                    try {
                        Files.deleteIfExists(tempImageFile.toPath());
                    } catch (Exception ignored) {
                    }
                }
            }

            return ocrBuilder.toString().trim();
        } catch (Exception e) {
            log.error("OCR extraction failed: {}", e.getMessage(), e);
            throw new RuntimeException("OCR text extraction failed: " + e.getMessage(), e);
        }
    }

    private String runTesseractCli(File imageFile) throws IOException, InterruptedException {
        List<String> command = new ArrayList<>();
        command.add(tesseractExecutable);
        command.add(imageFile.getAbsolutePath());
        command.add("stdout");

        if (tesseractLanguage != null && !tesseractLanguage.isBlank()) {
            command.add("-l");
            command.add(tesseractLanguage);
        }

        if (tesseractDatapath != null && !tesseractDatapath.isBlank()) {
            command.add("--tessdata-dir");
            command.add(tesseractDatapath);
        }

        ProcessBuilder pb = new ProcessBuilder(command);
        pb.redirectErrorStream(true);
        Process process = pb.start();

        StringBuilder output = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
            }
        }

        int exitCode = process.waitFor();
        if (exitCode != 0) {
            log.warn("Tesseract CLI failed with exit code {}: {}", exitCode, output);
            throw new RuntimeException("Tesseract CLI process exited with code " + exitCode + ": " + output);
        }

        return output.toString().trim();
    }

    private static String findTesseractExecutable() {
        String[] candidatePaths = {
                "/opt/homebrew/bin/tesseract",
                "/usr/local/bin/tesseract",
                "/usr/bin/tesseract"
        };
        for (String path : candidatePaths) {
            File f = new File(path);
            if (f.exists() && f.canExecute()) {
                return path;
            }
        }
        return "tesseract";
    }

    private static String resolveDatapath(String configuredDatapath) {
        if (configuredDatapath != null && !configuredDatapath.isBlank()) {
            return configuredDatapath;
        }
        String envPrefix = System.getenv("TESSDATA_PREFIX");
        if (envPrefix != null && new File(envPrefix).exists()) {
            return envPrefix;
        }
        String[] candidateDirs = {
                "/opt/homebrew/share/tessdata",
                "/usr/local/share/tessdata",
                "/usr/share/tessdata"
        };
        for (String dir : candidateDirs) {
            if (new File(dir).exists()) {
                return dir;
            }
        }
        return null;
    }
}
