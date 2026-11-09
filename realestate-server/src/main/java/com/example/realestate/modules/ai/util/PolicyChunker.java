package com.example.realestate.modules.ai.util;

import java.util.ArrayList;
import java.util.List;

/**
 * 政策正文分块（W6）：规范化空白后按 400~600 字切分，重叠 80 字。
 * 纯函数实现，便于单测边界。
 */
public final class PolicyChunker {

    public static final int TARGET = 500;
    public static final int MAX = 600;
    public static final int OVERLAP = 80;

    private PolicyChunker() {
    }

    /** 规范化：压缩连续空白为单个换行/空格，去首尾 */
    public static String normalize(String content) {
        if (content == null) {
            return "";
        }
        return content.replaceAll("\\r\\n?", "\n")
                .replaceAll("[ \\t]+", " ")
                .replaceAll("\\n{3,}", "\n\n")
                .trim();
    }

    public static List<String> chunk(String content) {
        String text = normalize(content);
        List<String> chunks = new ArrayList<>();
        if (text.isEmpty()) {
            return chunks;
        }
        int length = text.length();
        int start = 0;
        while (start < length) {
            int end = Math.min(start + TARGET, length);
            if (end < length) {
                // 在 TARGET~MAX 范围内优先于换行/句号处截断
                int window = Math.min(start + MAX, length);
                int cut = -1;
                for (int i = end; i < window; i++) {
                    char c = text.charAt(i);
                    if (c == '\n' || c == '。' || c == '；' || c == ';') {
                        cut = i + 1;
                        break;
                    }
                }
                if (cut > 0) {
                    end = cut;
                }
            }
            chunks.add(text.substring(start, end));
            if (end >= length) {
                break;
            }
            start = Math.max(end - OVERLAP, start + 1);
        }
        return chunks;
    }
}
