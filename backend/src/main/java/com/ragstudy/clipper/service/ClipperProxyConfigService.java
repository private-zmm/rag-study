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
import java.net.SocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
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
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "代理连接测试失败：" + describeException(exception), exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "代理连接测试被中断");
        }
    }

    public Optional<HttpClient> createClipperProxyClient() {
        return proxyConfigRepository.findById(CONFIG_ID).map(this::createHttpClient);
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

    private ClipperProxyConfigDto toDto(ClipperProxyConfigEntity config) {
        return new ClipperProxyConfigDto(
                config.getProtocol(),
                config.getHost(),
                config.getPort(),
                Optional.ofNullable(config.getUsername()).orElse(""),
                StringUtils.hasText(config.getPassword())
        );
    }

    private String normalizeProtocol(String protocol) {
        String normalizedProtocol = protocol.trim().toUpperCase(Locale.ROOT);

        if (!"HTTP".equals(normalizedProtocol) && !"HTTPS".equals(normalizedProtocol) && !"SOCKS5".equals(normalizedProtocol)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "代理协议仅支持 HTTP、HTTPS、SOCKS5");
        }

        return normalizedProtocol;
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