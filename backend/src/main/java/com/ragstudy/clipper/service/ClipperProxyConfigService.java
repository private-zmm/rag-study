package com.ragstudy.clipper.service;

import com.ragstudy.clipper.controller.dto.ClipperProxyConfigDto;
import com.ragstudy.clipper.controller.dto.ClipperProxyConfigRequest;
import com.ragstudy.clipper.dal.dataobject.ClipperProxyConfigEntity;
import com.ragstudy.clipper.dal.repository.ClipperProxyConfigRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.net.Authenticator;
import java.net.InetSocketAddress;
import java.net.PasswordAuthentication;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Service
public class ClipperProxyConfigService {

    private static final String CONFIG_ID = "default";
    private static final String TEST_URL = "https://example.com";

    private final ClipperProxyConfigRepository proxyConfigRepository;

    public ClipperProxyConfigService(ClipperProxyConfigRepository proxyConfigRepository) {
        this.proxyConfigRepository = proxyConfigRepository;
    }

    public ClipperProxyConfigDto getConfig() {
        return proxyConfigRepository.findById(CONFIG_ID)
                .map(this::toDto)
                .orElseGet(() -> new ClipperProxyConfigDto("HTTP", "", null, "", false));
    }

    @Transactional
    public ClipperProxyConfigDto saveConfig(ClipperProxyConfigRequest request) {
        ClipperProxyConfigEntity config = proxyConfigRepository.findById(CONFIG_ID).orElseGet(() -> {
            ClipperProxyConfigEntity created = new ClipperProxyConfigEntity();
            created.setId(CONFIG_ID);
            created.setCreatedAt(Instant.now());
            return created;
        });

        config.setProtocol(normalizeProtocol(request.protocol()));
        config.setHost(normalizeHost(request.host()));
        config.setPort(request.port());
        config.setUsername(normalizeOptional(request.username()));

        if (StringUtils.hasText(request.password())) {
            config.setPassword(request.password().trim());
        }

        config.setUpdatedAt(Instant.now());
        return toDto(proxyConfigRepository.save(config));
    }

    public void testConfig(ClipperProxyConfigRequest request) {
        ClipperProxyConfigEntity config = fromRequestForTest(request);
        rejectLoopbackProxyInContainer(config);
        HttpClient client = createHttpClient(config);
        HttpRequest httpRequest = HttpRequest.newBuilder(URI.create(TEST_URL))
                .timeout(Duration.ofSeconds(12))
                .GET()
                .build();

        try {
            HttpResponse<Void> response = client.send(httpRequest, HttpResponse.BodyHandlers.discarding());

            if (response.statusCode() < 200 || response.statusCode() >= 400) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "代理测试返回状态码：" + response.statusCode());
            }
        } catch (IOException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "代理连接测试失败：" + describeException(exception) + buildProxyHint(config), exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "代理连接测试被中断");
        }
    }

    public Optional<HttpClient> createClipperProxyClient() {
        return proxyConfigRepository.findById(CONFIG_ID).map(this::createHttpClient);
    }

    public Optional<Proxy> createClipperSocketProxy() {
        return proxyConfigRepository.findById(CONFIG_ID)
                .filter(config -> "SOCKS5".equals(normalizeStoredProtocol(config.getProtocol())))
                .map(config -> new Proxy(Proxy.Type.SOCKS, new InetSocketAddress(config.getHost(), config.getPort())));
    }

    public String currentProxyHint() {
        return proxyConfigRepository.findById(CONFIG_ID)
                .map(this::buildProxyHint)
                .orElse("");
    }

    public String currentProxyFailureHint() {
        return proxyConfigRepository.findById(CONFIG_ID)
                .map(config -> buildProxyHint(config) + probeProxyPort(config))
                .orElse("。当前已勾选“使用代理抓取”，但未读取到剪藏代理配置。");
    }

    public HttpClient createHttpClient(ClipperProxyConfigEntity config) {
        HttpClient.Builder builder = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(8))
                .followRedirects(HttpClient.Redirect.NEVER)
                .proxy(proxySelectorFor(config));

        if (StringUtils.hasText(config.getUsername()) && StringUtils.hasText(config.getPassword())) {
            builder.authenticator(new Authenticator() {
                @Override
                protected PasswordAuthentication getPasswordAuthentication() {
                    return new PasswordAuthentication(config.getUsername(), config.getPassword().toCharArray());
                }
            });
        }

        return builder.build();
    }

    private ProxySelector proxySelectorFor(ClipperProxyConfigEntity config) {
        InetSocketAddress address = new InetSocketAddress(config.getHost(), config.getPort());
        String protocol = Optional.ofNullable(config.getProtocol()).orElse("HTTP").toUpperCase(Locale.ROOT);

        if ("SOCKS5".equals(protocol)) {
            return new ProxySelector() {
                @Override
                public List<Proxy> select(URI uri) {
                    return List.of(new Proxy(Proxy.Type.SOCKS, address));
                }

                @Override
                public void connectFailed(URI uri, SocketAddress sa, IOException ioe) {
                    // 交给 HttpClient 报错处理。
                }
            };
        }

        return ProxySelector.of(address);
    }

    private ClipperProxyConfigEntity fromRequestForTest(ClipperProxyConfigRequest request) {
        ClipperProxyConfigEntity config = new ClipperProxyConfigEntity();
        config.setProtocol(normalizeProtocol(request.protocol()));
        config.setHost(normalizeHost(request.host()));
        config.setPort(request.port());
        config.setUsername(normalizeOptional(request.username()));

        if (StringUtils.hasText(request.password())) {
            config.setPassword(request.password().trim());
        } else {
            proxyConfigRepository.findById(CONFIG_ID).ifPresent((savedConfig) -> config.setPassword(savedConfig.getPassword()));
        }

        return config;
    }

    private void rejectLoopbackProxyInContainer(ClipperProxyConfigEntity config) {
        if (!isLikelyContainerEnvironment() || !isLoopbackHost(config.getHost())) {
            return;
        }

        throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "后端运行在 Docker 容器内，代理地址不能填 " + config.getHost()
                        + "。请改填 host.docker.internal，并确认代理软件允许局域网连接。"
        );
    }

    private ClipperProxyConfigDto toDto(ClipperProxyConfigEntity config) {
        return new ClipperProxyConfigDto(
                normalizeStoredProtocol(config.getProtocol()),
                config.getHost(),
                config.getPort(),
                Optional.ofNullable(config.getUsername()).orElse(""),
                StringUtils.hasText(config.getPassword())
        );
    }

    private String normalizeProtocol(String protocol) {
        String normalizedProtocol = protocol.trim().toUpperCase(Locale.ROOT);

        if ("HTTPS".equals(normalizedProtocol)) {
            return "HTTP";
        }

        if (!"HTTP".equals(normalizedProtocol) && !"SOCKS5".equals(normalizedProtocol)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "代理协议仅支持 HTTP、SOCKS5");
        }

        return normalizedProtocol;
    }

    private String normalizeStoredProtocol(String protocol) {
        if (!StringUtils.hasText(protocol)) {
            return "HTTP";
        }

        String normalizedProtocol = protocol.trim().toUpperCase(Locale.ROOT);
        return "HTTPS".equals(normalizedProtocol) ? "HTTP" : normalizedProtocol;
    }

    private String normalizeHost(String host) {
        String normalizedHost = host.trim();

        if (!StringUtils.hasText(normalizedHost)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请输入代理地址");
        }

        if (normalizedHost.contains("://")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "代理地址只填写主机名或 IP，不要包含协议");
        }

        return normalizedHost;
    }

    private String normalizeOptional(String value) {
        return StringUtils.hasText(value) ? value.trim() : "";
    }

    private String buildProxyHint(ClipperProxyConfigEntity config) {
        String endpoint = Optional.ofNullable(config.getProtocol()).orElse("HTTP").toUpperCase(Locale.ROOT)
                + " " + config.getHost() + ":" + config.getPort();
        String hint = "。当前剪藏代理：" + endpoint + "。请确认代理地址和端口能被后端访问";

        if (isLoopbackHost(config.getHost())) {
            hint += "；如果后端在 Docker 容器内运行，不要填 " + config.getHost()
                    + "，请改用 host.docker.internal，并让代理软件监听 0.0.0.0 或允许局域网连接";
        }

        return hint;
    }

    private String probeProxyPort(ClipperProxyConfigEntity config) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(config.getHost(), config.getPort()), 3000);
            return "；后端到该代理端口的 TCP 连接正常，超时更可能发生在代理连接目标网页阶段。"
                    + "请用同一代理测试目标 URL，或切换为 HTTP 192.168.0.198:7890 再试。";
        } catch (IOException exception) {
            return "；后端到该代理端口的 TCP 连接失败：" + describeException(exception)
                    + "。如果后端运行在 Docker 中，请检查代理软件是否允许来自 Docker 网段的连接，以及防火墙是否放行该端口。";
        }
    }

    private boolean isLoopbackHost(String host) {
        String normalizedHost = Optional.ofNullable(host).orElse("").trim().toLowerCase(Locale.ROOT);
        return "localhost".equals(normalizedHost)
                || "127.0.0.1".equals(normalizedHost)
                || "::1".equals(normalizedHost)
                || "0:0:0:0:0:0:0:1".equals(normalizedHost);
    }

    private boolean isLikelyContainerEnvironment() {
        return Files.exists(Path.of("/.dockerenv"))
                || StringUtils.hasText(System.getenv("KUBERNETES_SERVICE_HOST"));
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
}
