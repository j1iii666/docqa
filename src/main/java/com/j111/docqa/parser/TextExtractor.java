package com.j111.docqa.parser;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.hwpf.HWPFDocument;
import org.apache.poi.hwpf.extractor.WordExtractor;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.stereotype.Component;

import com.j111.docqa.common.BizException;
import com.j111.docqa.common.ResultCode;

/**
 * 把 pdf / docx / doc / txt 抽取成纯文本。
 * <p>
 * 只做"抽取"，不做清洗和切分：切分是下一步（RAG 的 chunking），职责分开才好测。
 */
@Component
public class TextExtractor {

    /** 支持的文件后缀 */
    public static final java.util.Set<String> SUPPORTED = java.util.Set.of("pdf", "docx", "doc", "txt");

    /**
     * @param file         磁盘上的文件
     * @param originalName 原始文件名，用来判断后缀
     */
    public String extract(Path file, String originalName) throws IOException {
        String ext = extensionOf(originalName);
        return switch (ext) {
            case "pdf" -> extractPdf(file);
            case "docx" -> extractDocx(file);
            case "doc" -> extractDoc(file);
            case "txt" -> Files.readString(file, StandardCharsets.UTF_8);
            default -> throw new BizException(ResultCode.UNSUPPORTED_FILE);
        };
    }

    public static String extensionOf(String filename) {
        if (filename == null || !filename.contains(".")) {
            return "";
        }
        return filename.substring(filename.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
    }

    private String extractPdf(Path file) throws IOException {
        try (PDDocument document = Loader.loadPDF(file.toFile())) {
            // 简单版：整篇抽。后面要按页存元数据（页码用于引用来源）时再改成逐页抽
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            return stripper.getText(document);
        }
    }

    private String extractDocx(Path file) throws IOException {
        try (InputStream in = Files.newInputStream(file);
             XWPFDocument document = new XWPFDocument(in);
             XWPFWordExtractor extractor = new XWPFWordExtractor(document)) {
            return extractor.getText();
        }
    }

    @SuppressWarnings("resource")
    private String extractDoc(Path file) throws IOException {
        try (InputStream in = Files.newInputStream(file);
             HWPFDocument document = new HWPFDocument(in);
             WordExtractor extractor = new WordExtractor(document)) {
            return extractor.getText();
        }
    }
}
