package com.ragstudy.knowledge.framework;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Component
public class DocumentParser {

    public String parse(MultipartFile file, String mimeType, String fileName) {
        byte[] content = readBytes(file);
        String normalizedMimeType = normalize(mimeType);
        String normalizedFileName = normalize(fileName);

        if (isPdf(normalizedMimeType, normalizedFileName)) {
            return parsePdf(content, fileName);
        }

        if (isDocx(normalizedMimeType, normalizedFileName)) {
            return parseDocx(content, fileName);
        }

        if (isHtml(normalizedMimeType, normalizedFileName)) {
            return parseHtml(readUtf8(content));
        }

        if (isReadableTextFile(normalizedMimeType, normalizedFileName)) {
            return readUtf8(content);
        }

        throw new IllegalArgumentException("当前版本暂不支持解析该文件类型：" + fileName);
    }

    private byte[] readBytes(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException exception) {
            throw new IllegalStateException("读取上传文件内容失败", exception);
        }
    }

    private String parsePdf(byte[] content, String fileName) {
        try (PDDocument document = Loader.loadPDF(content)) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            String text = stripper.getText(document);
            return ensureParsedText(text, fileName);
        } catch (IOException exception) {
            throw new IllegalStateException("解析 PDF 文件失败：" + fileName, exception);
        }
    }

    private String parseDocx(byte[] content, String fileName) {
        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(content))) {
            StringBuilder markdown = new StringBuilder();

            for (XWPFParagraph paragraph : document.getParagraphs()) {
                String text = paragraph.getText();

                if (!StringUtils.hasText(text)) {
                    continue;
                }

                appendDocxParagraph(markdown, paragraph, text.trim());
            }

            for (XWPFTable table : document.getTables()) {
                appendDocxTable(markdown, table);
            }

            return ensureParsedText(markdown.toString(), fileName);
        } catch (IOException exception) {
            throw new IllegalStateException("解析 DOCX 文件失败：" + fileName, exception);
        }
    }

    private void appendDocxParagraph(StringBuilder markdown, XWPFParagraph paragraph, String text) {
        String style = paragraph.getStyle();
        int headingLevel = docxHeadingLevel(style);

        if (headingLevel > 0) {
            appendBlock(markdown, "#".repeat(headingLevel) + " " + text);
            return;
        }

        appendBlock(markdown, text);
    }

    private int docxHeadingLevel(String style) {
        if (!StringUtils.hasText(style)) {
            return 0;
        }

        String normalizedStyle = style.toLowerCase();

        for (int level = 1; level <= 6; level += 1) {
            if (normalizedStyle.equals("heading" + level)
                    || normalizedStyle.equals("heading " + level)
                    || normalizedStyle.equals(String.valueOf(level))) {
                return level;
            }
        }

        return 0;
    }

    private void appendDocxTable(StringBuilder markdown, XWPFTable table) {
        List<XWPFTableRow> rows = table.getRows();

        if (rows.isEmpty()) {
            return;
        }

        StringBuilder tableMarkdown = new StringBuilder();

        for (int rowIndex = 0; rowIndex < rows.size(); rowIndex += 1) {
            List<XWPFTableCell> cells = rows.get(rowIndex).getTableCells();

            if (cells.isEmpty()) {
                continue;
            }

            tableMarkdown.append("| ");

            for (XWPFTableCell cell : cells) {
                tableMarkdown.append(escapeTableCell(cell.getText())).append(" | ");
            }

            tableMarkdown.append("\n");

            if (rowIndex == 0) {
                tableMarkdown.append("| ");
                cells.forEach(cell -> tableMarkdown.append("--- | "));
                tableMarkdown.append("\n");
            }
        }

        appendBlock(markdown, tableMarkdown.toString().trim());
    }

    private String parseHtml(String html) {
        org.jsoup.nodes.Document document = Jsoup.parse(html);
        document.select("script,style,noscript,svg,canvas,iframe,form,button,input,select,textarea").remove();
        StringBuilder markdown = new StringBuilder();

        if (StringUtils.hasText(document.title())) {
            appendBlock(markdown, "# " + document.title().trim());
        }

        Element body = document.body();

        if (body != null) {
            appendHtmlChildren(markdown, body);
        }

        return ensureParsedText(markdown.toString(), "HTML");
    }

    private void appendHtmlChildren(StringBuilder markdown, Element element) {
        for (Node child : element.childNodes()) {
            if (child instanceof Element childElement) {
                appendHtmlElement(markdown, childElement);
            }
        }
    }

    private void appendHtmlElement(StringBuilder markdown, Element element) {
        String tagName = element.tagName().toLowerCase();

        switch (tagName) {
            case "h1", "h2", "h3", "h4", "h5", "h6" -> appendBlock(
                    markdown,
                    "#".repeat(Integer.parseInt(tagName.substring(1))) + " " + element.text().trim()
            );
            case "p" -> appendBlock(markdown, element.text().trim());
            case "pre" -> appendBlock(markdown, "```\n" + element.text().strip() + "\n```");
            case "blockquote" -> appendBlock(markdown, "> " + element.text().trim());
            case "ul" -> appendHtmlList(markdown, element, false);
            case "ol" -> appendHtmlList(markdown, element, true);
            case "table" -> appendHtmlTable(markdown, element);
            case "br" -> markdown.append("\n");
            default -> {
                if (element.children().isEmpty()) {
                    String text = inlineText(element);

                    if (StringUtils.hasText(text)) {
                        appendBlock(markdown, text);
                    }
                    return;
                }

                appendHtmlChildren(markdown, element);
            }
        }
    }

    private void appendHtmlList(StringBuilder markdown, Element list, boolean ordered) {
        StringBuilder listMarkdown = new StringBuilder();
        int index = 1;

        for (Element item : list.children()) {
            if (!"li".equalsIgnoreCase(item.tagName())) {
                continue;
            }

            listMarkdown.append(ordered ? index + ". " : "- ")
                    .append(item.text().trim())
                    .append("\n");
            index += 1;
        }

        appendBlock(markdown, listMarkdown.toString().trim());
    }

    private void appendHtmlTable(StringBuilder markdown, Element table) {
        StringBuilder tableMarkdown = new StringBuilder();
        int rowIndex = 0;

        for (Element row : table.select("tr")) {
            List<Element> cells = row.select("th,td");

            if (cells.isEmpty()) {
                continue;
            }

            tableMarkdown.append("| ");

            for (Element cell : cells) {
                tableMarkdown.append(escapeTableCell(cell.text())).append(" | ");
            }

            tableMarkdown.append("\n");

            if (rowIndex == 0) {
                tableMarkdown.append("| ");
                cells.forEach(cell -> tableMarkdown.append("--- | "));
                tableMarkdown.append("\n");
            }

            rowIndex += 1;
        }

        appendBlock(markdown, tableMarkdown.toString().trim());
    }

    private String inlineText(Element element) {
        StringBuilder text = new StringBuilder();

        for (Node child : element.childNodes()) {
            if (child instanceof TextNode textNode) {
                text.append(textNode.text());
            } else if (child instanceof Element childElement) {
                text.append(childElement.text());
            }
        }

        return text.toString().replaceAll("\\s+", " ").trim();
    }

    private String readUtf8(byte[] content) {
        String text = new String(content, StandardCharsets.UTF_8);

        if (text.startsWith("\uFEFF")) {
            return text.substring(1);
        }

        return text;
    }

    private String ensureParsedText(String text, String fileName) {
        String normalizedText = text == null ? "" : text.replace("\u0000", "").trim();

        if (!StringUtils.hasText(normalizedText)) {
            throw new IllegalArgumentException("未能从文件中解析到文本内容：" + fileName);
        }

        return normalizedText;
    }

    private void appendBlock(StringBuilder markdown, String text) {
        if (!StringUtils.hasText(text)) {
            return;
        }

        if (!markdown.isEmpty()) {
            markdown.append("\n\n");
        }

        markdown.append(text.trim());
    }

    private String escapeTableCell(String text) {
        return (text == null ? "" : text)
                .replace("\r", " ")
                .replace("\n", " ")
                .replace("|", "\\|")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private boolean isPdf(String mimeType, String fileName) {
        return mimeType.equals("application/pdf") || fileName.endsWith(".pdf");
    }

    private boolean isDocx(String mimeType, String fileName) {
        return mimeType.equals("application/vnd.openxmlformats-officedocument.wordprocessingml.document")
                || fileName.endsWith(".docx");
    }

    private boolean isHtml(String mimeType, String fileName) {
        return mimeType.contains("html") || fileName.endsWith(".html") || fileName.endsWith(".htm");
    }

    private boolean isReadableTextFile(String mimeType, String fileName) {
        return mimeType.startsWith("text/")
                || mimeType.contains("json")
                || fileName.endsWith(".md")
                || fileName.endsWith(".markdown")
                || fileName.endsWith(".txt")
                || fileName.endsWith(".json")
                || fileName.endsWith(".csv");
    }

    private String normalize(String value) {
        return StringUtils.hasText(value) ? value.trim().toLowerCase() : "";
    }
}
