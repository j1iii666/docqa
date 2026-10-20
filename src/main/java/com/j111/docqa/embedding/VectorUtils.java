package com.j111.docqa.embedding;

/**
 * 向量工具：序列化、反序列化、余弦/点积。
 * <p>
 * 向量在库里存成逗号分隔字符串，别用 Java 的 ObjectOutputStream ——
 * 那个格式跟 Java 版本和类结构绑定，几个月后自己都读不出来。
 */
public final class VectorUtils {

    private VectorUtils() {
    }

    public static String serialize(float[] vector) {
        StringBuilder sb = new StringBuilder(vector.length * 8);
        for (int i = 0; i < vector.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(vector[i]);
        }
        return sb.toString();
    }

    public static float[] deserialize(String raw) {
        if (raw == null || raw.isEmpty()) {
            return new float[0];
        }
        String[] parts = raw.split(",");
        float[] vector = new float[parts.length];
        for (int i = 0; i < parts.length; i++) {
            vector[i] = Float.parseFloat(parts[i]);
        }
        return vector;
    }

    /** 点积。两个向量都已归一化时，点积 == 余弦相似度 */
    public static float dot(float[] a, float[] b) {
        int n = Math.min(a.length, b.length);
        float sum = 0;
        for (int i = 0; i < n; i++) {
            sum += a[i] * b[i];
        }
        return sum;
    }
}
