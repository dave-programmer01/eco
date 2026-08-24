package com.heraim.eco.service;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PdfExtractionServiceTest {

    private PdfExtractionService pdfExtractionService;

    @BeforeEach
    void setUp() {
        pdfExtractionService = new PdfExtractionService(10, null, "eng", null);
    }

    private byte[] createDigitalPdf(String text) throws IOException {
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage();
            document.addPage(page);
            try (PDPageContentStream contentStream = new PDPageContentStream(document, page)) {
                contentStream.beginText();
                contentStream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                contentStream.newLineAtOffset(50, 700);
                contentStream.showText(text);
                contentStream.endText();
            }
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            document.save(baos);
            return baos.toByteArray();
        }
    }

    private byte[] createScannedPdf(String text) throws IOException {
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage();
            document.addPage(page);

            BufferedImage image = new BufferedImage(600, 200, BufferedImage.TYPE_INT_RGB);
            Graphics2D g2d = image.createGraphics();
            g2d.setColor(Color.WHITE);
            g2d.fillRect(0, 0, 600, 200);
            g2d.setColor(Color.BLACK);
            g2d.setFont(new Font("Arial", Font.BOLD, 28));
            g2d.drawString(text, 20, 100);
            g2d.dispose();

            PDImageXObject pdImage = LosslessFactory.createFromImage(document, image);
            try (PDPageContentStream contentStream = new PDPageContentStream(document, page)) {
                contentStream.drawImage(pdImage, 50, 500, 500, 166);
            }
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            document.save(baos);
            return baos.toByteArray();
        }
    }

    @Test
    void testDigitalPdfExtraction() throws IOException {
        String expectedClause = "Party A shall indemnify Party B for standard liabilities.";
        byte[] pdfBytes = createDigitalPdf(expectedClause);
        MockMultipartFile file = new MockMultipartFile("file", "contract.pdf", "application/pdf", pdfBytes);

        String extracted = pdfExtractionService.extractText(file);
        assertEquals(expectedClause, extracted.trim());
    }

    @Test
    void testScannedPdfFallbackToOcr() throws IOException {
        String scannedText = "CONFIDENTIAL COMPLIANCE AUDIT";
        byte[] pdfBytes = createScannedPdf(scannedText);
        MockMultipartFile file = new MockMultipartFile("file", "scanned.pdf", "application/pdf", pdfBytes);

        String extracted = pdfExtractionService.extractText(file);
        assertTrue(extracted.toUpperCase().contains("CONFIDENTIAL"), "Extracted text should contain 'CONFIDENTIAL', got: " + extracted);
        assertTrue(extracted.toUpperCase().contains("AUDIT"), "Extracted text should contain 'AUDIT', got: " + extracted);
    }

    @Test
    void testExtractResourcePdf() throws IOException {
        try (var is = getClass().getResourceAsStream("/test-contract.pdf")) {
            if (is != null) {
                byte[] bytes = is.readAllBytes();
                MockMultipartFile file = new MockMultipartFile("file", "test-contract.pdf", "application/pdf", bytes);
                String extracted = pdfExtractionService.extractText(file);
                assertTrue(extracted.length() > 0, "Extracted text from test-contract.pdf should not be empty");
            }
        }
    }

    @Test
    void testEmptyAndNullFiles() throws IOException {
        assertEquals("", pdfExtractionService.extractText((MockMultipartFile) null));
        assertEquals("", pdfExtractionService.extractText(new MockMultipartFile("file", new byte[0])));
        assertEquals("", pdfExtractionService.extractText((byte[]) null));
    }
}
