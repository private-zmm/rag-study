package com.ragstudy.clipper.service;

import com.ragstudy.clipper.controller.dto.ClipperPreviewDto;
import com.ragstudy.clipper.controller.dto.ClipperPreviewRequest;
import com.ragstudy.clipper.controller.dto.ClipperRequest;
import com.ragstudy.clipper.controller.dto.ClipperResultDto;
import com.ragstudy.clipper.controller.dto.WebClipDto;
import com.ragstudy.clipper.convert.WebClipConvert;
import com.ragstudy.clipper.dal.dataobject.WebClipEntity;
import com.ragstudy.clipper.dal.repository.WebClipRepository;
import com.ragstudy.knowledge.controller.dto.KnowledgeDocumentDto;
import com.ragstudy.knowledge.framework.MinioStorageService;
import com.ragstudy.knowledge.service.KnowledgeDocumentService;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;
import org.jsoup.select.Elements;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.net.InetAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class ClipperService {

    private static final int MAX_RESPONSE_BYTES = 12 * 1024 * 1024;
    private static final int MAX_CONTENT_CHARS = 120_000;
    private static final int MAX_IMAGE_BYTES = 8 * 1024 * 1024;
    private static final int MAX_IMAGES_PER_PAGE = 60;
    private static final String DEFAULT_USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
            + "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36";
    private static final String WECHAT_USER_AGENT = "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) "
            + "AppleWebKit/605.1.15 (KHTML, like Gecko) Mobile/15E148 MicroMessenger/8.0.50";
    private static final Pattern CHARSET_PATTERN = Pattern.compile("(?i)charset=([^;\\s]+)");
    private static final Pattern LOCAL_ASSET_PATTERN = Pattern.compile("/api/note-assets\\?objectName=([^\\s)\"']+)");
    private static final Pattern MARKDOWN_IMAGE_PATTERN = Pattern.compile("!\\[[^\\]]*]\\(");
    private static final Pattern BACKGROUND_IMAGE_PATTERN = Pattern.compile("(?i)background(?:-image)?\\s*:[^;]*url\\((['\"]?)(.*?)\\1\\)");

    private final HttpClient httpClient;
    private final KnowledgeDocumentService knowledgeDocumentService;
    private final WebClipRepository webClipRepository;
    private final MinioStorageService storageService;

    public ClipperService(
            KnowledgeDocumentService knowledgeDocumentService,
            WebClipRepository webClipRepository,
            MinioStorageService storageService
    ) {
        this.knowledgeDocumentService = knowledgeDocumentService;
        this.webClipRepository = webClipRepository;
        this.storageService = storageService;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(8))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    @Transactional(readOnly = true)
    public ClipperPreviewDto preview(String userId, ClipperPreviewRequest request) {
        URI uri = parseAndValidateUri(request.url());
        ParsedPage parsedPage = parsePage(userId, fetchPage(uri), uri.toString(), normalizeMode(request.mode()));
        WebClipDto existingClip = webClipRepository
                .findFirstByUserIdAndCanonicalUrlOrderByUpdatedAtDesc(userId, canonicalizeUrl(uri))
                .map(WebClipConvert::toDto)
                .orElse(null);

        return new ClipperPreviewDto(
                uri.toString(),
                parsedPage.title(),
                parsedPage.content(),
                parsedPage.excerpt(),
                parsedPage.siteName(),
                parsedPage.contentType(),
                countWords(parsedPage.content()),
                existingClip
        );
    }

    @Transactional(readOnly = true)
    public List<WebClipDto> listHistory(String userId, int limit) {
        int safeLimit = Math.min(100, Math.max(1, limit));
        return webClipRepository.findAllByUserIdOrderByUpdatedAtDesc(userId, PageRequest.of(0, safeLimit))
                .stream()
                .map(WebClipConvert::toDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public WebClipDto getHistory(String userId, String clipId) {
        WebClipEntity clip = requireOwnedClip(userId, clipId);
        return WebClipConvert.toDto(clip, resolveClipContent(userId, clip));
    }

    @Transactional
    public ClipperResultDto clip(String userId, ClipperRequest request) {
        String target = normalizeTarget(request.target());

        if (!"clip".equals(target) && !"knowledge".equals(target)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "不支持的保存目标");
        }

        if ("knowledge".equals(target) && !StringUtils.hasText(request.knowledgeBaseId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请选择要保存到的知识库");
        }

        URI uri = parseAndValidateUri(request.url());
        String title = sanitizeTitle(request.title());
        String content = sanitizeContent(request.content());

        if (!StringUtils.hasText(title) || !StringUtils.hasText(content)) {
            ParsedPage parsedPage = parsePage(userId, fetchPage(uri), uri.toString(), normalizeMode(request.mode()));
            title = StringUtils.hasText(title) ? title : parsedPage.title();
            content = StringUtils.hasText(content) ? content : parsedPage.content();
        }

        if ("clip".equals(target)) {
            WebClipEntity clip = saveHistory(userId, null, null, uri, title, content);
            return new ClipperResultDto(
                    clip.getId(),
                    uri.toString(),
                    clip.getTitle(),
                    target,
                    normalizeMode(request.mode()),
                    "saved"
            );
        }

        KnowledgeDocumentDto document = knowledgeDocumentService.saveWebDocument(
                userId,
                request.knowledgeBaseId(),
                uri.toString(),
                title,
                content
        );
        saveHistory(userId, request.knowledgeBaseId(), document, uri, title, content);

        return new ClipperResultDto(
                document.id(),
                uri.toString(),
                document.title(),
                "knowledge",
                normalizeMode(request.mode()),
                "saved"
        );
    }

    @Transactional
    public void deleteHistory(String userId, String clipId) {
        WebClipEntity clip = requireOwnedClip(userId, clipId);
        String rawContent = resolveClipContent(userId, clip);
        deleteLocalClipAssets(userId, rawContent);
        deleteLinkedDocument(userId, clip);
        webClipRepository.delete(clip);
    }

    private WebClipEntity requireOwnedClip(String userId, String clipId) {
        return webClipRepository.findByIdAndUserId(clipId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "剪藏历史不存在"));
    }

    private String resolveClipContent(String userId, WebClipEntity clip) {
        if (StringUtils.hasText(clip.getContent())) {
            return clip.getContent();
        }

        if (!hasLinkedDocument(clip)) {
            return "";
        }

        try {
            return knowledgeDocumentService
                    .getDocument(userId, clip.getKnowledgeBaseId(), clip.getDocumentId())
                    .rawContent();
        } catch (ResponseStatusException exception) {
            if (exception.getStatusCode() == HttpStatus.NOT_FOUND) {
                return "";
            }

            throw exception;
        }
    }

    private void deleteLinkedDocument(String userId, WebClipEntity clip) {
        if (!hasLinkedDocument(clip)) {
            return;
        }

        try {
            knowledgeDocumentService.deleteDocument(userId, clip.getKnowledgeBaseId(), clip.getDocumentId());
        } catch (ResponseStatusException exception) {
            if (exception.getStatusCode() != HttpStatus.NOT_FOUND) {
                throw exception;
            }
        }
    }

    private boolean hasLinkedDocument(WebClipEntity clip) {
        return StringUtils.hasText(clip.getKnowledgeBaseId()) && StringUtils.hasText(clip.getDocumentId());
    }

    private void deleteLocalClipAssets(String userId, String rawContent) {
        for (String objectName : extractLocalClipAssets(userId, rawContent)) {
            try {
                storageService.delete(objectName);
            } catch (Exception ignored) {
                // 资源清理不阻断历史和文档删除，避免单张图片异常导致剪藏无法移除。
            }
        }
    }

    private Set<String> extractLocalClipAssets(String userId, String rawContent) {
        Set<String> objectNames = new LinkedHashSet<>();

        if (!StringUtils.hasText(rawContent)) {
            return objectNames;
        }

        Matcher matcher = LOCAL_ASSET_PATTERN.matcher(rawContent);

        while (matcher.find()) {
            String objectName = decodeAssetObjectName(matcher.group(1));

            if (isSafeClipAssetObject(userId, objectName)) {
                objectNames.add(objectName);
            }
        }

        return objectNames;
    }

    private String decodeAssetObjectName(String encodedObjectName) {
        try {
            return URLDecoder.decode(encodedObjectName, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException exception) {
            return "";
        }
    }

    private boolean isSafeClipAssetObject(String userId, String objectName) {
        String expectedPrefix = "notes/assets/" + userId + "/web-clips/";
        return StringUtils.hasText(objectName)
                && objectName.startsWith(expectedPrefix)
                && !objectName.contains("..")
                && !objectName.contains("\\");
    }

    private WebClipEntity saveHistory(
            String userId,
            String knowledgeBaseId,
            KnowledgeDocumentDto document,
            URI uri,
            String title,
            String content
    ) {
        LocalDateTime now = LocalDateTime.now();
        WebClipEntity clip = new WebClipEntity(
                UUID.randomUUID().toString(),
                userId,
                knowledgeBaseId,
                document == null ? null : document.id(),
                uri.toString(),
                canonicalizeUrl(uri),
                document == null ? title : document.title(),
                uri.getHost(),
                buildExcerpt(content),
                content,
                hashContent(content),
                "saved",
                now,
                now
        );
        return webClipRepository.save(clip);
    }

    private URI parseAndValidateUri(String url) {
        URI uri;

        try {
            uri = URI.create(url.trim());
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "URL 格式不正确");
        }

        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);

        if (!"http".equals(scheme) && !"https".equals(scheme)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "仅支持 http 或 https 链接");
        }

        if (!StringUtils.hasText(uri.getHost())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "URL 缺少有效域名");
        }

        rejectPrivateHost(uri.getHost());
        return uri;
    }

    private void rejectPrivateHost(String host) {
        try {
            for (InetAddress address : InetAddress.getAllByName(host)) {
                if (address.isAnyLocalAddress()
                        || address.isLoopbackAddress()
                        || address.isLinkLocalAddress()
                        || address.isSiteLocalAddress()
                        || address.isMulticastAddress()) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "不允许抓取内网或本机地址");
                }
            }
        } catch (IOException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "域名解析失败");
        }
    }

    private FetchedPage fetchPage(URI uri) {
        return fetchPage(uri, 0);
    }

    private FetchedPage fetchPage(URI uri, int redirectCount) {
        HttpRequest httpRequest = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(15))
                .header("Accept", "text/html,text/plain;q=0.9,*/*;q=0.1")
                .header("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.7")
                .header("User-Agent", userAgentFor(uri))
                .GET()
                .build();

        HttpResponse<byte[]> response;

        try {
            response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofByteArray());
        } catch (IOException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "网页抓取失败：" + describeException(exception), exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "网页抓取被中断");
        }

        if (response.statusCode() >= 300 && response.statusCode() < 400) {
            if (redirectCount >= 3) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "网页重定向次数过多");
            }

            String location = response.headers().firstValue("location")
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_GATEWAY, "网页重定向缺少 Location"));
            URI nextUri = parseAndValidateUri(uri.resolve(location).toString());
            return fetchPage(nextUri, redirectCount + 1);
        }

        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "网页返回状态码：" + response.statusCode());
        }

        byte[] body = response.body();

        if (body.length > MAX_RESPONSE_BYTES) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "网页内容过大，暂不支持剪藏超过 12MB 的原始页面");
        }

        String contentType = response.headers().firstValue("content-type").orElse("text/html");
        String normalizedContentType = contentType.toLowerCase(Locale.ROOT);

        if (!normalizedContentType.contains("text/html") && !normalizedContentType.startsWith("text/plain")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "当前版本仅支持网页或纯文本内容");
        }

        return new FetchedPage(uri.toString(), decodeBody(body, contentType), contentType);
    }

    private String decodeBody(byte[] body, String contentType) {
        Charset charset = extractCharset(contentType);

        try {
            return charset.newDecoder().decode(ByteBuffer.wrap(body)).toString();
        } catch (CharacterCodingException exception) {
            return StandardCharsets.UTF_8.decode(ByteBuffer.wrap(body)).toString();
        }
    }

    private Charset extractCharset(String contentType) {
        Matcher matcher = CHARSET_PATTERN.matcher(contentType);

        if (matcher.find()) {
            try {
                return Charset.forName(matcher.group(1).replace("\"", ""));
            } catch (Exception ignored) {
                return StandardCharsets.UTF_8;
            }
        }

        return StandardCharsets.UTF_8;
    }

    private ParsedPage parsePage(String userId, FetchedPage page, String requestedUrl, String mode) {
        if (page.contentType().toLowerCase(Locale.ROOT).startsWith("text/plain")) {
            String title = titleFromUrl(requestedUrl);
            String content = toMarkdown(title, requestedUrl, truncate(page.html().trim(), MAX_CONTENT_CHARS));
            return new ParsedPage(title, content, buildExcerpt(content), hostFromUrl(requestedUrl), page.contentType());
        }

        Document document = Jsoup.parse(page.html(), page.url());
        removeNoise(document);

        String title = extractTitle(document, requestedUrl);
        String siteName = extractSiteName(document, requestedUrl);
        Element contentElement = "full".equalsIgnoreCase(mode) ? document.body() : findMainContent(document);
        ImageLocalizeContext imageContext = new ImageLocalizeContext(userId, page.url());
        String contentMarkdown = markdownFromElement(contentElement, imageContext);
        String coverImage = localizeImage(imageContext, extractCoverImage(document));
        String markdown = toMarkdown(title, requestedUrl, prependCoverImage(contentMarkdown, coverImage));

        if (!StringUtils.hasText(markdown)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "未能解析到可保存的网页正文");
        }

        return new ParsedPage(
                title,
                truncate(markdown, MAX_CONTENT_CHARS),
                buildExcerpt(markdown),
                siteName,
                page.contentType()
        );
    }

    private void removeNoise(Document document) {
        document.select("script,style,noscript,svg,canvas,iframe,form,nav,aside,footer,header,button,input,select,textarea").remove();
        document.select("[aria-hidden=true],.advertisement,.ads,.ad,.sidebar,.menu,.breadcrumb,.share,.social,.comment,.comments").remove();
    }

    private Element findMainContent(Document document) {
        Elements candidates = document.select(
                "article, main, [role=main], #article-root, [itemprop=articleBody], "
                        + ".article-viewer, .markdown-body, .article, .post, .content, .entry-content, .post-content"
        );
        Element best = null;
        int bestScore = 0;

        for (Element candidate : candidates) {
            int score = contentScore(candidate);

            if (score > bestScore) {
                best = candidate;
                bestScore = score;
            }
        }

        if (best != null && bestScore > 200) {
            return best;
        }

        return document.body();
    }

    private int contentScore(Element element) {
        int textLength = element.text().length();
        int paragraphScore = element.select("p").size() * 35;
        int headingScore = element.select("h1,h2,h3").size() * 12;
        int linkPenalty = element.select("a").stream()
                .mapToInt(link -> link.text().length())
                .sum() / 2;

        return textLength + paragraphScore + headingScore - linkPenalty;
    }

    private String extractTitle(Document document, String url) {
        String title = firstNonBlank(
                metaContent(document, "meta[property=og:title]"),
                metaContent(document, "meta[name=twitter:title]"),
                document.title(),
                document.selectFirst("h1") == null ? "" : document.selectFirst("h1").text(),
                titleFromUrl(url)
        );

        return truncate(title, 160);
    }

    private String extractSiteName(Document document, String url) {
        return firstNonBlank(
                metaContent(document, "meta[property=og:site_name]"),
                hostFromUrl(url)
        );
    }

    private String extractCoverImage(Document document) {
        return firstNonBlankOrEmpty(
                resolveUrl(document.baseUri(), metaContent(document, "meta[property=og:image]")),
                resolveUrl(document.baseUri(), metaContent(document, "meta[name=twitter:image]")),
                resolveUrl(document.baseUri(), metaContent(document, "meta[itemprop=image]")),
                resolveUrl(document.baseUri(), metaContent(document, "meta[property=og:image:url]")),
                resolveUrl(document.baseUri(), metaContent(document, "meta[name=msg_cdn_url]"))
        );
    }

    private String metaContent(Document document, String selector) {
        Element element = document.selectFirst(selector);
        return element == null ? "" : element.attr("content").trim();
    }

    private String markdownFromElement(Element root, ImageLocalizeContext imageContext) {
        StringBuilder markdown = new StringBuilder();

        for (Node child : root.childNodes()) {
            appendMarkdown(child, markdown, 0, imageContext);
        }

        return normalizeMarkdown(markdown.toString());
    }

    private void appendMarkdown(Node node, StringBuilder markdown, int listDepth, ImageLocalizeContext imageContext) {
        if (node instanceof TextNode textNode) {
            appendText(markdown, textNode.text());
            return;
        }

        if (!(node instanceof Element element)) {
            return;
        }

        String tagName = element.tagName().toLowerCase(Locale.ROOT);

        switch (tagName) {
            case "h1" -> appendBlock(markdown, "# " + element.text());
            case "h2" -> appendBlock(markdown, "## " + element.text());
            case "h3" -> appendBlock(markdown, "### " + element.text());
            case "h4" -> appendBlock(markdown, "#### " + element.text());
            case "h5" -> appendBlock(markdown, "##### " + element.text());
            case "h6" -> appendBlock(markdown, "###### " + element.text());
            case "p" -> appendBlock(markdown, inlineMarkdown(element, imageContext));
            case "blockquote" -> appendBlock(markdown, "> " + element.text());
            case "pre" -> appendBlock(markdown, "```\n" + element.text().strip() + "\n```");
            case "ul" -> appendList(element, markdown, listDepth, false, imageContext);
            case "ol" -> appendList(element, markdown, listDepth, true, imageContext);
            case "table" -> appendBlock(markdown, tableMarkdown(element));
            case "img" -> appendImage(element, markdown, imageContext);
            default -> {
                boolean appendedImage = appendImageLikeElement(element, markdown, imageContext, true);

                if (appendedImage && element.childNodeSize() == 0) {
                    return;
                }

                for (Node child : element.childNodes()) {
                    appendMarkdown(child, markdown, listDepth, imageContext);
                }
            }
        }
    }

    private String inlineMarkdown(Element element, ImageLocalizeContext imageContext) {
        StringBuilder inline = new StringBuilder();

        for (Node child : element.childNodes()) {
            appendInline(child, inline, imageContext);
        }

        return inline.toString().replaceAll("[ \\t]+", " ").trim();
    }

    private void appendInline(Node node, StringBuilder inline, ImageLocalizeContext imageContext) {
        if (node instanceof TextNode textNode) {
            appendText(inline, textNode.text());
            return;
        }

        if (!(node instanceof Element element)) {
            return;
        }

        String tagName = element.tagName().toLowerCase(Locale.ROOT);
        String text = element.text().replaceAll("\\s+", " ").trim();

        switch (tagName) {
            case "strong", "b" -> {
                if (StringUtils.hasText(text)) {
                    inline.append("**").append(text).append("**");
                }
            }
            case "em", "i" -> {
                if (StringUtils.hasText(text)) {
                    inline.append("*").append(text).append("*");
                }
            }
            case "code" -> {
                if (StringUtils.hasText(text)) {
                    inline.append('`').append(text.replace("`", "\\`")).append('`');
                }
            }
            case "a" -> {
                if (!StringUtils.hasText(text)) {
                    for (Node child : element.childNodes()) {
                        appendInline(child, inline, imageContext);
                    }
                    return;
                }

                String href = element.absUrl("href");
                inline.append('[').append(text).append(']');
                inline.append(StringUtils.hasText(href) ? "(" + href + ")" : "");
            }
            case "img" -> {
                appendImageLikeElement(element, inline, imageContext, false);
            }
            case "br" -> inline.append("\n");
            default -> {
                boolean appendedImage = appendImageLikeElement(element, inline, imageContext, false);

                if (appendedImage && element.childNodeSize() == 0) {
                    return;
                }

                for (Node child : element.childNodes()) {
                    appendInline(child, inline, imageContext);
                }
            }
        }
    }

    private void appendList(
            Element list,
            StringBuilder markdown,
            int listDepth,
            boolean ordered,
            ImageLocalizeContext imageContext
    ) {
        int index = 1;

        for (Element item : list.children()) {
            if (!"li".equalsIgnoreCase(item.tagName())) {
                continue;
            }

            String prefix = "  ".repeat(Math.max(0, listDepth)) + (ordered ? index + ". " : "- ");
            appendBlock(markdown, prefix + inlineMarkdown(item, imageContext));

            for (Element nestedList : item.select("> ul, > ol")) {
                appendList(nestedList, markdown, listDepth + 1, "ol".equalsIgnoreCase(nestedList.tagName()), imageContext);
            }

            index += 1;
        }
    }

    private String tableMarkdown(Element table) {
        Elements rows = table.select("tr");
        StringBuilder markdown = new StringBuilder();
        int rowIndex = 0;

        for (Element row : rows) {
            Elements cells = row.select("th,td");

            if (cells.isEmpty()) {
                continue;
            }

            markdown.append("| ");

            for (Element cell : cells) {
                markdown.append(cell.text().replace("|", "\\|")).append(" | ");
            }

            markdown.append("\n");

            if (rowIndex == 0) {
                markdown.append("| ");
                cells.forEach(cell -> markdown.append("--- | "));
                markdown.append("\n");
            }

            rowIndex += 1;
        }

        return markdown.toString().trim();
    }

    private void appendImage(Element image, StringBuilder markdown, ImageLocalizeContext imageContext) {
        appendImageLikeElement(image, markdown, imageContext, true);
    }

    private boolean appendImageLikeElement(
            Element element,
            StringBuilder markdown,
            ImageLocalizeContext imageContext,
            boolean block
    ) {
        String imageUrl = resolveElementImageUrl(element);

        if (!StringUtils.hasText(imageUrl) || isLikelyAdImage(element, imageUrl)) {
            return false;
        }

        String source = localizeImage(imageContext, imageUrl);

        if (!StringUtils.hasText(source)) {
            return false;
        }

        String imageMarkdown = "![" + imageAlt(element) + "](" + source + ")";

        if (block) {
            appendBlock(markdown, imageMarkdown);
        } else {
            markdown.append(imageMarkdown);
        }

        return true;
    }

    private String resolveElementImageUrl(Element element) {
        if ("img".equalsIgnoreCase(element.tagName())) {
            return resolveImageUrl(element);
        }

        if (!element.select("img,picture").isEmpty()) {
            return "";
        }

        return firstUsableImageUrl(
                element,
                firstSrcSetUrl(element),
                element.absUrl("data-src"),
                element.absUrl("data-original"),
                element.absUrl("data-original-src"),
                element.absUrl("data-lazy-src"),
                element.absUrl("data-url"),
                element.absUrl("data-actualsrc"),
                element.absUrl("data-large_image"),
                backgroundImageUrl(element)
        );
    }

    private String resolveImageUrl(Element image) {
        String source = firstUsableImageUrl(
                image,
                firstSrcSetUrl(image),
                image.absUrl("data-src"),
                image.absUrl("data-original"),
                image.absUrl("data-original-src"),
                image.absUrl("data-lazy-src"),
                image.absUrl("data-url"),
                image.absUrl("data-actualsrc"),
                image.absUrl("data-large_image"),
                image.absUrl("src")
        );

        if (StringUtils.hasText(source)) {
            return source;
        }

        return firstUsableImageUrl(image, image.attr("data-src"), image.attr("data-original"), image.attr("src"));
    }

    private String imageAlt(Element element) {
        return firstNonBlankOrEmpty(
                element.attr("alt"),
                element.attr("aria-label"),
                element.attr("title")
        ).replace("[", "").replace("]", "");
    }

    private String backgroundImageUrl(Element element) {
        Matcher matcher = BACKGROUND_IMAGE_PATTERN.matcher(element.attr("style"));

        if (!matcher.find()) {
            return "";
        }

        return resolveUrl(element.baseUri(), matcher.group(2));
    }

    private boolean isLikelyAdImage(Element element, String imageUrl) {
        StringBuilder context = new StringBuilder(imageUrl);
        Element current = element;
        int depth = 0;

        while (current != null && depth < 5) {
            context.append(' ')
                    .append(current.tagName())
                    .append(' ')
                    .append(current.id())
                    .append(' ')
                    .append(current.className())
                    .append(' ')
                    .append(current.attr("alt"))
                    .append(' ')
                    .append(current.attr("title"))
                    .append(' ')
                    .append(current.attr("aria-label"))
                    .append(' ')
                    .append(current.attr("href"))
                    .append(' ')
                    .append(current.attr("data-src"));
            current = current.parent();
            depth += 1;
        }

        String normalized = context.toString().toLowerCase(Locale.ROOT);
        return containsAny(
                normalized,
                "advert",
                "advertisement",
                "sponsor",
                "sponsored",
                "promotion",
                "promote",
                "qrcode",
                "qr_code",
                "qr-code",
                "mp_qrcode",
                "pc_qr",
                "js_pc_qr_code",
                "reward",
                "donate",
                "广告",
                "推广",
                "赞助",
                "二维码",
                "扫码",
                "赞赏",
                "打赏",
                "关注公众号",
                "关注我们",
                "联系客服",
                "加群"
        );
    }

    private boolean containsAny(String value, String... fragments) {
        for (String fragment : fragments) {
            if (value.contains(fragment)) {
                return true;
            }
        }

        return false;
    }

    private String firstUsableImageUrl(Element image, String... candidates) {
        for (String candidate : candidates) {
            String resolved = resolveUrl(image.baseUri(), candidate);

            if (StringUtils.hasText(resolved) && !resolved.startsWith("data:")) {
                return resolved;
            }
        }

        return "";
    }

    private String firstSrcSetUrl(Element image) {
        String srcSet = firstNonBlankOrEmpty(
                image.attr("data-srcset"),
                image.attr("srcset"),
                image.parent() == null ? "" : image.parent().select("source[data-srcset], source[srcset]").stream()
                        .map(source -> firstNonBlankOrEmpty(source.attr("data-srcset"), source.attr("srcset")))
                        .filter(StringUtils::hasText)
                        .findFirst()
                        .orElse("")
        );

        if (!StringUtils.hasText(srcSet)) {
            return "";
        }

        String bestUrl = "";
        int bestWidth = -1;

        for (String candidate : srcSet.split(",")) {
            String[] parts = candidate.trim().split("\\s+");

            if (parts.length == 0 || !StringUtils.hasText(parts[0])) {
                continue;
            }

            int width = parseSrcSetWidth(parts.length > 1 ? parts[1] : "");

            if (width >= bestWidth) {
                bestWidth = width;
                bestUrl = parts[0];
            }
        }

        return resolveUrl(image.baseUri(), bestUrl);
    }

    private int parseSrcSetWidth(String descriptor) {
        if (!StringUtils.hasText(descriptor) || !descriptor.endsWith("w")) {
            return 0;
        }

        try {
            return Integer.parseInt(descriptor.substring(0, descriptor.length() - 1));
        } catch (NumberFormatException exception) {
            return 0;
        }
    }

    private String resolveUrl(String baseUri, String url) {
        if (!StringUtils.hasText(url)) {
            return "";
        }

        try {
            return URI.create(baseUri).resolve(url.trim()).toString();
        } catch (IllegalArgumentException exception) {
            return url.trim();
        }
    }

    private String localizeImage(ImageLocalizeContext context, String imageUrl) {
        if (!StringUtils.hasText(imageUrl) || context.downloadedImages >= MAX_IMAGES_PER_PAGE) {
            return imageUrl;
        }

        URI imageUri;

        try {
            imageUri = parseAndValidateUri(imageUrl);
        } catch (ResponseStatusException exception) {
            return imageUrl;
        }

        HttpRequest imageRequest = HttpRequest.newBuilder(imageUri)
                .timeout(Duration.ofSeconds(12))
                .header("Accept", "image/avif,image/webp,image/png,image/jpeg,image/gif,image/svg+xml,image/*;q=0.8,*/*;q=0.1")
                .header("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.7")
                .header("User-Agent", userAgentFor(imageUri))
                .header("Referer", context.pageUrl)
                .GET()
                .build();

        try {
            HttpResponse<byte[]> response = httpClient.send(imageRequest, HttpResponse.BodyHandlers.ofByteArray());

            if (response.statusCode() < 200 || response.statusCode() >= 300 || response.body().length > MAX_IMAGE_BYTES) {
                return imageUrl;
            }

            String contentType = response.headers().firstValue("content-type").orElse("application/octet-stream");

            if (!isImageResponse(contentType, imageUri)) {
                return imageUrl;
            }

            String fileName = normalizeImageFileName(imageUri, contentType);
            String objectName = "notes/assets/" + context.userId + "/web-clips/" + UUID.randomUUID() + "/" + fileName;
            storageService.upload(objectName, response.body(), contentType);
            context.downloadedImages += 1;
            return "/api/note-assets?objectName=" + URLEncoder.encode(objectName, StandardCharsets.UTF_8);
        } catch (Exception exception) {
            return imageUrl;
        }
    }

    private boolean isImageResponse(String contentType, URI uri) {
        if (StringUtils.hasText(contentType) && contentType.toLowerCase(Locale.ROOT).startsWith("image/")) {
            return true;
        }

        String path = uri.getPath() == null ? "" : uri.getPath().toLowerCase(Locale.ROOT);
        return path.endsWith(".png")
                || path.endsWith(".jpg")
                || path.endsWith(".jpeg")
                || path.endsWith(".gif")
                || path.endsWith(".webp")
                || path.endsWith(".svg")
                || path.endsWith(".bmp")
                || path.contains("awebp")
                || path.contains(".image")
                || path.contains("tplv-");
    }

    private String normalizeImageFileName(URI uri, String contentType) {
        String path = uri.getPath();
        String fileName = StringUtils.hasText(path) ? path.substring(path.lastIndexOf('/') + 1) : "";

        if (!StringUtils.hasText(fileName) || !fileName.contains(".")) {
            fileName = "image" + imageExtension(contentType);
        }

        if (fileName.contains("~tplv-") && !fileName.matches(".*\\.(png|jpg|jpeg|gif|webp|svg|bmp)$")) {
            fileName = fileName + imageExtension(contentType);
        }

        return fileName.replaceAll("[\\\\/:*?\"<>|]", "_");
    }

    private String imageExtension(String contentType) {
        String normalizedContentType = contentType.toLowerCase(Locale.ROOT);

        if (normalizedContentType.contains("png")) {
            return ".png";
        }

        if (normalizedContentType.contains("jpeg") || normalizedContentType.contains("jpg")) {
            return ".jpg";
        }

        if (normalizedContentType.contains("gif")) {
            return ".gif";
        }

        if (normalizedContentType.contains("webp")) {
            return ".webp";
        }

        if (normalizedContentType.contains("svg")) {
            return ".svg";
        }

        return ".img";
    }

    private void appendBlock(StringBuilder markdown, String text) {
        if (!StringUtils.hasText(text)) {
            return;
        }

        markdown.append("\n\n").append(text.trim()).append("\n");
    }

    private void appendText(StringBuilder builder, String text) {
        if (!StringUtils.hasText(text)) {
            return;
        }

        if (!builder.isEmpty() && !Character.isWhitespace(builder.charAt(builder.length() - 1))) {
            builder.append(' ');
        }

        builder.append(text.replaceAll("\\s+", " ").trim());
    }

    private String normalizeMarkdown(String markdown) {
        return markdown
                .replaceAll("(?im)^\\s*cover_image\\s*$\\n?", "")
                .replaceAll("[\\t\\x0B\\f\\r ]+\\n", "\n")
                .replaceAll("\\n{3,}", "\n\n")
                .trim();
    }

    private String prependCoverImage(String markdown, String coverImage) {
        if (!StringUtils.hasText(coverImage) || MARKDOWN_IMAGE_PATTERN.matcher(markdown).find()) {
            return markdown;
        }

        return "![](" + coverImage + ")\n\n" + markdown;
    }

    private String toMarkdown(String title, String url, String content) {
        return "# " + title + "\n\n"
                + "来源：" + url + "\n\n"
                + content.strip();
    }

    private String buildExcerpt(String markdown) {
        String text = markdown
                .replaceAll("(?m)^#+\\s+", "")
                .replaceAll("(?m)^来源：.*$", "")
                .replaceAll("[`*_#>\\[\\]()!|]", " ")
                .replaceAll("\\s+", " ")
                .trim();

        return truncate(text, 220);
    }

    private int countWords(String value) {
        String normalized = value.replaceAll("\\s+", "").trim();
        return normalized.length();
    }

    private String sanitizeTitle(String title) {
        return title == null ? "" : truncate(title.replace("\u0000", "").trim(), 160);
    }

    private String sanitizeContent(String content) {
        return content == null ? "" : truncate(content.replace("\u0000", "").trim(), MAX_CONTENT_CHARS);
    }

    private String titleFromUrl(String url) {
        URI uri = URI.create(url);
        String path = uri.getPath();

        if (StringUtils.hasText(path) && !"/".equals(path)) {
            String[] parts = path.split("/");
            String lastPart = parts[parts.length - 1].trim();

            if (StringUtils.hasText(lastPart)) {
                return truncate(lastPart.replace('-', ' ').replace('_', ' '), 120);
            }
        }

        return hostFromUrl(url);
    }

    private String hostFromUrl(String url) {
        return URI.create(url).getHost();
    }

    private String canonicalizeUrl(URI uri) {
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
        int port = uri.getPort();
        String path = StringUtils.hasText(uri.getPath()) ? uri.getPath() : "/";
        String query = uri.getQuery();

        if (("http".equals(scheme) && port == 80) || ("https".equals(scheme) && port == 443)) {
            port = -1;
        }

        try {
            return new URI(scheme, null, host, port, path, query, null).toString();
        } catch (Exception exception) {
            return uri.toString();
        }
    }

    private String hashContent(String content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(content.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 不可用", exception);
        }
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }

        return "未命名网页";
    }

    private String firstNonBlankOrEmpty(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }

        return "";
    }

    private String describeException(Exception exception) {
        String message = exception.getMessage();

        if (StringUtils.hasText(message)) {
            return message;
        }

        Throwable cause = exception.getCause();

        if (cause != null && StringUtils.hasText(cause.getMessage())) {
            return cause.getMessage();
        }

        return exception.getClass().getSimpleName();
    }

    private String truncate(String value, int maxLength) {
        if (value.length() <= maxLength) {
            return value;
        }

        return value.substring(0, maxLength);
    }

    private String normalizeMode(String mode) {
        return StringUtils.hasText(mode) ? mode : "auto";
    }

    private String normalizeTarget(String target) {
        return StringUtils.hasText(target) ? target.toLowerCase(Locale.ROOT) : "clip";
    }

    private String userAgentFor(URI uri) {
        String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);

        if (host.endsWith("mp.weixin.qq.com") || host.endsWith("mmbiz.qpic.cn")) {
            return WECHAT_USER_AGENT;
        }

        return DEFAULT_USER_AGENT;
    }

    private record FetchedPage(String url, String html, String contentType) {
    }

    private record ParsedPage(String title, String content, String excerpt, String siteName, String contentType) {
    }

    private static class ImageLocalizeContext {

        private final String userId;
        private final String pageUrl;
        private int downloadedImages;

        private ImageLocalizeContext(String userId, String pageUrl) {
            this.userId = userId;
            this.pageUrl = pageUrl;
        }
    }
}
