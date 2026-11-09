package com.example.realestate.modules.ai.util;

import java.util.ArrayList;
import java.util.List;

/** float[] 与 JSON 数组字符串互转（不引入额外 JSON 依赖，手工拼接足够且可控）。 */
public final class EmbeddingCodec {

    private EmbeddingCodec() {
    }

    public static String encode(float[] vector) {
        if (vector == null) {
            return null;
        }
        StringBuilder sb = new StringBuilder(vector.length * 10 + 2);
        sb.append('[');
        for (int i = 0; i < vector.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(vector[i]);
        }
        sb.append(']');
        return sb.toString();
    }

    public static float[] decode(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        String trimmed = json.trim();
        if (trimmed.length() < 2 || trimmed.charAt(0) != '[' || trimmed.charAt(trimmed.length() - 1) != ']') {
            return null;
        }
        String body = trimmed.substring(1, trimmed.length() - 1).trim();
        if (body.isEmpty()) {
            return new float[0];
        }
        String[] parts = body.split(",");
        List<Float> values = new ArrayList<>(parts.length);
        for (String part : parts) {
            try {
                values.add(Float.parseFloat(part.trim()));
            } catch (NumberFormatException e) {
                return null;
            }
        }
        float[] result = new float[values.size()];
        for (int i = 0; i < values.size(); i++) {
            result[i] = values.get(i);
        }
        return result;
    }

    /** 余弦相似度；维度不一致或零向量返回 -1 */
    public static double cosine(float[] a, float[] b) {
        if (a == null || b == null || a.length != b.length || a.length == 0) {
            return -1;
        }
        double dot = 0, na = 0, nb = 0;
        for (int i = 0; i < a.length; i++) {
            dot += (double) a[i] * b[i];
            na += (double) a[i] * a[i];
            nb += (double) b[i] * b[i];
        }
        if (na == 0 || nb == 0) {
            return -1;
        }
        return dot / (Math.sqrt(na) * Math.sqrt(nb));
    }
}
