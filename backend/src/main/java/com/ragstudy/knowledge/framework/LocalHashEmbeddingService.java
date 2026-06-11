package com.ragstudy.knowledge.framework;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class LocalHashEmbeddingService {

    private final QdrantVectorProperties properties;

    public LocalHashEmbeddingService(QdrantVectorProperties properties) {
        this.properties = properties;
    }

    public float[] embed(String content) {
        int vectorSize = properties.getVectorSize();
        float[] vector = new float[vectorSize];

        if (!StringUtils.hasText(content)) {
            return vector;
        }

        String normalizedContent = content.toLowerCase().replaceAll("\\s+", " ").trim();
        String[] tokens = normalizedContent.split("[\\p{Punct}\\s]+");

        for (String token : tokens) {
            if (!StringUtils.hasText(token)) {
                continue;
            }

            int hash = token.hashCode();
            int index = Math.floorMod(hash, vectorSize);
            vector[index] += hash < 0 ? -1.0F : 1.0F;
        }

        normalize(vector);
        return vector;
    }

    private void normalize(float[] vector) {
        double sum = 0;

        for (float value : vector) {
            sum += value * value;
        }

        if (sum == 0) {
            return;
        }

        float norm = (float) Math.sqrt(sum);

        for (int index = 0; index < vector.length; index += 1) {
            vector[index] = vector[index] / norm;
        }
    }
}
