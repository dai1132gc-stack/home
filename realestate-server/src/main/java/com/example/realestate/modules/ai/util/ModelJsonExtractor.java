package com.example.realestate.modules.ai.util;

/**
 * 模型输出 JSON 提取：剥离 ```json 围栏与前后说明文字，仅截取首个 { 到末个 }。
 * 只做文本截取，解析仍走 Jackson，禁止 eval/脚本执行。
 */
public final class ModelJsonExtractor {

    private ModelJsonExtractor() {
    }

    public static String extractJsonObject(String raw) {
        if (raw == null) {
            return null;
        }
        String text = raw.trim();
        if (text.startsWith("```")) {
            int firstNewline = text.indexOf('\n');
            if (firstNewline > 0) {
                text = text.substring(firstNewline + 1);
            }
            if (text.endsWith("```")) {
                text = text.substring(0, text.length() - 3);
            }
            text = text.trim();
        }
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start < 0 || end <= start) {
            return null;
        }
        return text.substring(start, end + 1);
    }
}
