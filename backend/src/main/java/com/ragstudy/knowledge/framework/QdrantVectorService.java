package com.ragstudy.knowledge.framework;

import com.ragstudy.embedding.service.EmbeddingProviderService;
import com.ragstudy.knowledge.dal.dataobject.KnowledgeDocumentChunkEntity;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.filter.Filter;
import dev.langchain4j.store.embedding.filter.comparison.IsEqualTo;
import dev.langchain4j.store.embedding.filter.logical.And;
import dev.langchain4j.store.embedding.qdrant.QdrantEmbeddingStore;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class QdrantVectorService {

    private static final String PAYLOAD_TEXT_KEY = "content";

    private final QdrantVectorProperties properties;
    private final EmbeddingProviderService embeddingProviderService;
    private final RestClient restClient;

    public QdrantVectorService(
            QdrantVectorProperties properties,
            EmbeddingProviderService embeddingProviderService,
            RestClient.Builder restClientBuilder
    ) {
        this.properties = properties;
        this.embeddingProviderService = embeddingProviderService;
        this.restClient = restClientBuilder.build();
    }

    public void ensureCollection() {
        if (collectionExists()) {
            return;
        }

        restClient.put()
                .uri(collectionUrl())
                .body(new CreateCollectionRequest(
                        Map.of("size", properties.getVectorSize(), "distance", "Cosine")
                ))
                .retrieve()
                .onStatus(HttpStatusCode::isError, (request, response) -> {
                    throw new IllegalStateException("Qdrant collection 创建失败：" + response.getStatusCode());
                })
                .toBodilessEntity();
    }

    private boolean collectionExists() {
        try {
            restClient.get()
                    .uri(collectionUrl())
                    .retrieve()
                    .toBodilessEntity();
            return true;
        } catch (Exception exception) {
            return false;
        }
    }

    public String upsertChunk(KnowledgeDocumentChunkEntity chunk) {
        ensureCollection();
        return upsertChunkWithModel(chunk).vectorId();
    }

    public UpsertResult upsertChunkWithModel(KnowledgeDocumentChunkEntity chunk) {
        ensureCollection();
        EmbeddingProviderService.EmbeddingVector embeddingVector = embeddingProviderService.embed(chunk.getUserId(), chunk.getContent());
        String vectorId = embeddingStore().add(
                Embedding.from(embeddingVector.vector()),
                TextSegment.from(chunk.getContent(), chunkMetadata(chunk))
        );

        return new UpsertResult(vectorId, embeddingVector.model());
    }

    public List<VectorSearchResult> search(String userId, String knowledgeBaseId, String query, int limit) {
        ensureCollection();
        EmbeddingProviderService.EmbeddingVector embeddingVector = embeddingProviderService.embed(userId, query);
        EmbeddingSearchRequest searchRequest = EmbeddingSearchRequest.builder()
                .queryEmbedding(Embedding.from(embeddingVector.vector()))
                .maxResults(limit)
                .filter(ownedKnowledgeBaseFilter(userId, knowledgeBaseId))
                .build();
        EmbeddingSearchResult<TextSegment> searchResult = embeddingStore().search(searchRequest);
        List<VectorSearchResult> results = new ArrayList<>();

        for (EmbeddingMatch<TextSegment> match : searchResult.matches()) {
            TextSegment segment = match.embedded();

            if (segment == null) {
                continue;
            }

            Metadata metadata = segment.metadata();
            results.add(new VectorSearchResult(
                    metadata.getString("chunkId"),
                    metadata.getString("documentId"),
                    segment.text(),
                    match.score()
            ));
        }

        return results;
    }

    private EmbeddingStore<TextSegment> embeddingStore() {
        QdrantConnection connection = resolveQdrantConnection();
        QdrantEmbeddingStore.Builder builder = QdrantEmbeddingStore.builder()
                .collectionName(properties.getCollection())
                .host(connection.host())
                .port(connection.port())
                .useTls(connection.useTls())
                .payloadTextKey(PAYLOAD_TEXT_KEY);

        if (StringUtils.hasText(properties.getApiKey())) {
            builder.apiKey(properties.getApiKey());
        }

        return builder.build();
    }

    private Filter ownedKnowledgeBaseFilter(String userId, String knowledgeBaseId) {
        return new And(
                new IsEqualTo("userId", userId),
                new IsEqualTo("knowledgeBaseId", knowledgeBaseId)
        );
    }

    private Metadata chunkMetadata(KnowledgeDocumentChunkEntity chunk) {
        return new Metadata()
                .put("chunkId", chunk.getId())
                .put("documentId", chunk.getDocumentId())
                .put("knowledgeBaseId", chunk.getKnowledgeBaseId())
                .put("userId", chunk.getUserId())
                .put("chunkIndex", chunk.getChunkIndex());
    }

    private String collectionUrl() {
        if (!StringUtils.hasText(properties.getUrl()) || !StringUtils.hasText(properties.getCollection())) {
            throw new IllegalStateException("Qdrant url 或 collection 未配置");
        }

        return properties.getUrl().replaceAll("/+$", "") + "/collections/" + properties.getCollection();
    }

    private QdrantConnection resolveQdrantConnection() {
        if (!StringUtils.hasText(properties.getUrl())) {
            throw new IllegalStateException("Qdrant url 未配置");
        }

        URI uri = URI.create(properties.getUrl());
        String host = uri.getHost();
        int httpPort = uri.getPort();
        int port = properties.getGrpcPort() != null
                ? properties.getGrpcPort()
                : (httpPort > 0 ? httpPort + 1 : 6334);
        boolean useTls = "https".equalsIgnoreCase(uri.getScheme()) || "grpcs".equalsIgnoreCase(uri.getScheme());

        return new QdrantConnection(host, port, useTls);
    }

    private record CreateCollectionRequest(Map<String, Object> vectors) {
    }

    private record QdrantConnection(String host, int port, boolean useTls) {
    }

    public record VectorSearchResult(String chunkId, String documentId, String content, double score) {
    }

    public record UpsertResult(String vectorId, String embeddingModel) {
    }
}
