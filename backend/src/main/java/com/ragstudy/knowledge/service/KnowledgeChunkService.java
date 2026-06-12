package com.ragstudy.knowledge.service;

import com.ragstudy.knowledge.dal.dataobject.KnowledgeDocumentChunkEntity;
import com.ragstudy.knowledge.dal.dataobject.KnowledgeDocumentEntity;
import com.ragstudy.knowledge.dal.repository.KnowledgeDocumentChunkRepository;
import com.ragstudy.knowledge.framework.RagProperties;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class KnowledgeChunkService {

    private static final Pattern MARKDOWN_HEADING_PATTERN = Pattern.compile("^(#{1,6})\\s+(.+?)\\s*#*\\s*$");

    private final KnowledgeDocumentChunkRepository chunkRepository;
    private final RagProperties ragProperties;

    public KnowledgeChunkService(KnowledgeDocumentChunkRepository chunkRepository, RagProperties ragProperties) {
        this.chunkRepository = chunkRepository;
        this.ragProperties = ragProperties;
    }

    @Transactional
    public void rebuildChunks(KnowledgeDocumentEntity document) {
        deleteDocumentChunks(document.getId(), document.getUserId());
        List<StructuredChunk> chunks = splitContent(document.getTitle(), document.getRawContent());

        for (int index = 0; index < chunks.size(); index += 1) {
            StructuredChunk chunk = chunks.get(index);
            chunkRepository.save(new KnowledgeDocumentChunkEntity(
                    UUID.randomUUID().toString(),
                    document.getId(),
                    document.getKnowledgeBaseId(),
                    document.getUserId(),
                    index,
                    chunk.titlePath(),
                    chunk.heading(),
                    chunk.startOffset(),
                    chunk.endOffset(),
                    chunk.content(),
                    estimateTokenCount(chunk.content()),
                    null,
                    LocalDateTime.now()
            ));
        }
    }

    @Transactional(readOnly = true)
    public long countDocumentChunks(String documentId, String userId) {
        return chunkRepository.countByDocumentIdAndUserId(documentId, userId);
    }

    @Transactional(readOnly = true)
    public long countKnowledgeBaseChunks(String knowledgeBaseId, String userId) {
        return chunkRepository.countByKnowledgeBaseIdAndUserId(knowledgeBaseId, userId);
    }

    @Transactional
    public void deleteDocumentChunks(String documentId, String userId) {
        chunkRepository.deleteAllByDocumentIdAndUserId(documentId, userId);
    }

    @Transactional
    public void deleteKnowledgeBaseChunks(String knowledgeBaseId, String userId) {
        chunkRepository.deleteAllByKnowledgeBaseIdAndUserId(knowledgeBaseId, userId);
    }

    private List<StructuredChunk> splitContent(String documentTitle, String rawContent) {
        String content = rawContent == null ? "" : rawContent.replace("\r\n", "\n").replace('\r', '\n');

        if (!StringUtils.hasText(content)) {
            return List.of();
        }

        List<DocumentBlock> blocks = parseBlocks(content);

        if (blocks.isEmpty()) {
            return List.of();
        }

        return buildChunks(normalizeTitle(documentTitle), blocks);
    }

    private List<DocumentBlock> parseBlocks(String content) {
        List<DocumentBlock> blocks = new ArrayList<>();
        List<Line> lines = readLines(content);
        List<String> headingStack = new ArrayList<>();
        int index = 0;

        while (index < lines.size()) {
            Line line = lines.get(index);
            String lineText = line.text();

            if (!StringUtils.hasText(lineText)) {
                index += 1;
                continue;
            }

            Heading heading = parseHeading(lineText);

            if (heading != null) {
                applyHeading(headingStack, heading);
                index += 1;
                continue;
            }

            if (isCodeFenceStart(lineText)) {
                BlockRange range = collectCodeBlock(lines, index);
                blocks.add(toBlock(content, range, headingStack));
                index = range.nextIndex();
                continue;
            }

            if (isTableStart(lines, index)) {
                BlockRange range = collectTable(lines, index);
                blocks.add(toBlock(content, range, headingStack));
                index = range.nextIndex();
                continue;
            }

            BlockRange range = collectParagraph(lines, index);
            blocks.add(toBlock(content, range, headingStack));
            index = range.nextIndex();
        }

        return blocks;
    }

    private List<StructuredChunk> buildChunks(String documentTitle, List<DocumentBlock> blocks) {
        List<StructuredChunk> chunks = new ArrayList<>();
        List<DocumentBlock> currentBlocks = new ArrayList<>();
        int currentTokens = 0;

        for (DocumentBlock block : blocks) {
            int blockTokens = estimateTokenCount(block.content());

            if (blockTokens > ragProperties.getChunkSize()) {
                flushChunk(documentTitle, chunks, currentBlocks);
                currentBlocks.clear();
                currentTokens = 0;
                chunks.addAll(splitLargeBlock(documentTitle, block));
                continue;
            }

            if (!currentBlocks.isEmpty() && currentTokens + blockTokens > ragProperties.getChunkSize()) {
                flushChunk(documentTitle, chunks, currentBlocks);
                currentBlocks = overlapTail(currentBlocks);
                currentTokens = currentBlocks.stream().mapToInt(blockItem -> estimateTokenCount(blockItem.content())).sum();
            }

            currentBlocks.add(block);
            currentTokens += blockTokens;
        }

        flushChunk(documentTitle, chunks, currentBlocks);
        return chunks;
    }

    private void flushChunk(String documentTitle, List<StructuredChunk> chunks, List<DocumentBlock> blocks) {
        if (blocks.isEmpty()) {
            return;
        }

        String content = joinBlocks(blocks);

        if (!StringUtils.hasText(content)) {
            return;
        }

        List<String> titleParts = buildTitleParts(documentTitle, blocks.get(blocks.size() - 1).headingPath());
        chunks.add(new StructuredChunk(
                String.join(" / ", titleParts),
                titleParts.get(titleParts.size() - 1),
                blocks.get(0).startOffset(),
                blocks.get(blocks.size() - 1).endOffset(),
                content
        ));
    }

    private List<StructuredChunk> splitLargeBlock(String documentTitle, DocumentBlock block) {
        List<StructuredChunk> chunks = new ArrayList<>();
        int chunkSize = ragProperties.getChunkSize();
        int chunkOverlap = ragProperties.getChunkOverlap();
        String content = block.content();
        int start = 0;

        while (start < content.length()) {
            int end = Math.min(start + chunkSize, content.length());
            int boundary = findTextBoundary(content, start, end, chunkSize);
            String chunkContent = content.substring(start, boundary).trim();

            if (StringUtils.hasText(chunkContent)) {
                List<String> titleParts = buildTitleParts(documentTitle, block.headingPath());
                chunks.add(new StructuredChunk(
                        String.join(" / ", titleParts),
                        titleParts.get(titleParts.size() - 1),
                        block.startOffset() + start,
                        block.startOffset() + boundary,
                        chunkContent
                ));
            }

            if (boundary >= content.length()) {
                break;
            }

            start = Math.max(boundary - chunkOverlap, start + 1);
        }

        return chunks;
    }

    private List<DocumentBlock> overlapTail(List<DocumentBlock> blocks) {
        int overlapTokens = ragProperties.getChunkOverlap();

        if (overlapTokens <= 0) {
            return new ArrayList<>();
        }

        List<DocumentBlock> tail = new ArrayList<>();
        int tokenCount = 0;

        for (int index = blocks.size() - 1; index >= 0; index -= 1) {
            DocumentBlock block = blocks.get(index);
            int blockTokens = estimateTokenCount(block.content());

            if (!tail.isEmpty() && tokenCount + blockTokens > overlapTokens) {
                break;
            }

            tail.add(0, block);
            tokenCount += blockTokens;
        }

        return tail;
    }

    private DocumentBlock toBlock(String content, BlockRange range, List<String> headingStack) {
        return new DocumentBlock(
                content.substring(range.startOffset(), range.endOffset()).trim(),
                range.startOffset(),
                range.endOffset(),
                List.copyOf(headingStack)
        );
    }

    private BlockRange collectCodeBlock(List<Line> lines, int startIndex) {
        String fence = codeFenceMarker(lines.get(startIndex).text());
        int index = startIndex + 1;

        while (index < lines.size()) {
            if (lines.get(index).text().trim().startsWith(fence)) {
                index += 1;
                break;
            }

            index += 1;
        }

        return rangeFromLines(lines, startIndex, index);
    }

    private BlockRange collectTable(List<Line> lines, int startIndex) {
        int index = startIndex;

        while (index < lines.size() && isTableLine(lines.get(index).text())) {
            index += 1;
        }

        return rangeFromLines(lines, startIndex, index);
    }

    private BlockRange collectParagraph(List<Line> lines, int startIndex) {
        int index = startIndex + 1;

        while (index < lines.size()) {
            String text = lines.get(index).text();

            if (!StringUtils.hasText(text)
                    || parseHeading(text) != null
                    || isCodeFenceStart(text)
                    || isTableStart(lines, index)) {
                break;
            }

            index += 1;
        }

        return rangeFromLines(lines, startIndex, index);
    }

    private BlockRange rangeFromLines(List<Line> lines, int startIndex, int nextIndex) {
        Line startLine = lines.get(startIndex);
        Line endLine = lines.get(nextIndex - 1);
        return new BlockRange(startLine.startOffset(), endLine.endOffset(), nextIndex);
    }

    private List<Line> readLines(String content) {
        List<Line> lines = new ArrayList<>();
        int start = 0;

        while (start < content.length()) {
            int lineEnd = content.indexOf('\n', start);
            int end = lineEnd >= 0 ? lineEnd : content.length();
            int nextStart = lineEnd >= 0 ? lineEnd + 1 : content.length();
            lines.add(new Line(content.substring(start, end), start, nextStart));
            start = nextStart;
        }

        return lines;
    }

    private Heading parseHeading(String line) {
        Matcher matcher = MARKDOWN_HEADING_PATTERN.matcher(line.trim());

        if (!matcher.matches()) {
            return null;
        }

        return new Heading(matcher.group(1).length(), matcher.group(2).trim());
    }

    private void applyHeading(List<String> headingStack, Heading heading) {
        int levelIndex = heading.level() - 1;

        while (headingStack.size() > levelIndex) {
            headingStack.remove(headingStack.size() - 1);
        }

        headingStack.add(heading.text());
    }

    private boolean isCodeFenceStart(String line) {
        String trimmedLine = line.trim();
        return trimmedLine.startsWith("```") || trimmedLine.startsWith("~~~");
    }

    private String codeFenceMarker(String line) {
        return line.trim().startsWith("~~~") ? "~~~" : "```";
    }

    private boolean isTableStart(List<Line> lines, int index) {
        if (index + 1 >= lines.size()) {
            return false;
        }

        return isTableLine(lines.get(index).text()) && isTableDivider(lines.get(index + 1).text());
    }

    private boolean isTableLine(String line) {
        String trimmedLine = line.trim();
        return trimmedLine.startsWith("|") && trimmedLine.endsWith("|");
    }

    private boolean isTableDivider(String line) {
        String normalizedLine = line.trim().replace(" ", "");

        if (!normalizedLine.startsWith("|") || !normalizedLine.endsWith("|")) {
            return false;
        }

        for (String cell : normalizedLine.substring(1, normalizedLine.length() - 1).split("\\|")) {
            if (!cell.matches(":?-{3,}:?")) {
                return false;
            }
        }

        return true;
    }

    private int findTextBoundary(String content, int start, int end, int chunkSize) {
        if (end >= content.length()) {
            return content.length();
        }

        for (int index = end; index > start + chunkSize / 2; index -= 1) {
            char currentChar = content.charAt(index - 1);

            if (currentChar == '。' || currentChar == '！' || currentChar == '？' || currentChar == '\n' || currentChar == '.') {
                return index;
            }
        }

        return end;
    }

    private List<String> buildTitleParts(String documentTitle, List<String> headingPath) {
        List<String> titleParts = new ArrayList<>();
        titleParts.add(normalizeTitle(documentTitle));
        titleParts.addAll(headingPath);
        return titleParts;
    }

    private String normalizeTitle(String title) {
        if (!StringUtils.hasText(title)) {
            return "未命名文档";
        }

        return title.trim();
    }

    private String joinBlocks(List<DocumentBlock> blocks) {
        StringBuilder builder = new StringBuilder();

        for (DocumentBlock block : blocks) {
            if (!builder.isEmpty()) {
                builder.append("\n\n");
            }

            builder.append(block.content());
        }

        return builder.toString().trim();
    }

    private int estimateTokenCount(String content) {
        if (!StringUtils.hasText(content)) {
            return 1;
        }

        int tokenCount = 0;
        boolean inAsciiWord = false;

        for (int index = 0; index < content.length(); index += 1) {
            char currentChar = content.charAt(index);

            if (Character.isWhitespace(currentChar)) {
                inAsciiWord = false;
                continue;
            }

            if (currentChar < 128 && Character.isLetterOrDigit(currentChar)) {
                if (!inAsciiWord) {
                    tokenCount += 1;
                    inAsciiWord = true;
                }
                continue;
            }

            inAsciiWord = false;
            tokenCount += 1;
        }

        return Math.max(1, tokenCount);
    }

    private record Line(String text, int startOffset, int endOffset) {
    }

    private record Heading(int level, String text) {
    }

    private record BlockRange(int startOffset, int endOffset, int nextIndex) {
    }

    private record DocumentBlock(String content, int startOffset, int endOffset, List<String> headingPath) {
    }

    private record StructuredChunk(
            String titlePath,
            String heading,
            int startOffset,
            int endOffset,
            String content
    ) {
    }
}
