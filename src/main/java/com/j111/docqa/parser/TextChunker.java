package com.j111.docqa.parser;

import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 文本切分（chunking）。
 * <p>
 * 策略：**按长度切、优先在语义边界断开、相邻块留一点重叠**。
 * <ul>
 *   <li>块太大：检索出来的片段里混着无关内容，模型容易被带偏</li>
 *   <li>块太小：一句话被切两半，语义断掉，召回质量更差</li>
 *   <li>重叠（overlap）：防止答案正好卡在两个块的交界处被切碎</li>
 * </ul>
 * 所以先在 maxChars 附近往回找换行/句号，找不到才硬切。
 */
@Component
public class TextChunker {

    /** 单块最大字符数 */
    private final int maxChars;

    /** 相邻块重叠字符数 */
    private final int overlap;

    public TextChunker(@Value("${docqa.chunk.max-chars:500}") int maxChars,
                       @Value("${docqa.chunk.overlap:50}") int overlap) {
        this.maxChars = Math.max(maxChars, 100);
        this.overlap = Math.max(0, Math.min(overlap, this.maxChars / 2));
    }

    /**
     * @return 切好的块，按原文顺序；空文本返回空列表
     */
    public List<ChunkDraft> split(String text) {
        List<ChunkDraft> chunks = new ArrayList<>();
        if (text == null || text.isBlank()) {
            return chunks;
        }

        String content = normalize(text);
        int index = 0;
        StringBuilder buffer = new StringBuilder();
        int bufferStart = 0;

        // 以"段落"为最小拼装单位：一段一段往里塞，塞不下就先落盘成一个块。
        // 这样块边界落在段落之间，不会把两个不相关的主题硬塞进同一个块（这是检索变不准的主因）。
        for (String paragraph : content.split("\n+")) {
            String p = paragraph.strip();
            if (p.isEmpty()) {
                continue;
            }

            for (String piece : hardSplit(p, maxChars)) {
                boolean overflow = buffer.length() > 0 && buffer.length() + 1 + piece.length() > maxChars;
                if (overflow) {
                    chunks.add(new ChunkDraft(index++, buffer.toString(), bufferStart, buffer.length()));
                    // 重叠：下一块开头重复上一块末尾 overlap 个字符，避免答案卡在边界被切碎
                    String tail = tail(buffer.toString(), overlap);
                    buffer.setLength(0);
                    buffer.append(tail);
                    bufferStart = Math.max(bufferStart + buffer.length() - tail.length(), 0);
                }

                int nextStart = content.indexOf(piece, Math.max(bufferStart, 0));
                if (buffer.length() == 0 && nextStart >= 0) {
                    bufferStart = nextStart;
                }
                if (buffer.length() > 0) {
                    buffer.append('\n');
                }
                buffer.append(piece);
            }
        }

        if (buffer.length() > 0) {
            chunks.add(new ChunkDraft(index, buffer.toString(), bufferStart, buffer.length()));
        }
        return chunks;
    }

    /** 单段超过 maxChars 时，先在语义边界断开，实在没有就硬切 */
    private List<String> hardSplit(String paragraph, int limit) {
        List<String> pieces = new ArrayList<>();
        int cursor = 0;
        while (cursor < paragraph.length()) {
            int end = Math.min(cursor + limit, paragraph.length());
            if (end < paragraph.length()) {
                end = findBoundary(paragraph, cursor, end);
            }
            String piece = paragraph.substring(cursor, end).strip();
            if (!piece.isEmpty()) {
                pieces.add(piece);
            }
            if (end >= paragraph.length()) {
                break;
            }
            int next = end - overlap;
            cursor = Math.max(next, cursor + 1);
        }
        return pieces;
    }

    private String tail(String text, int count) {
        if (count <= 0 || text.length() <= count) {
            return count <= 0 ? "" : text;
        }
        return text.substring(text.length() - count);
    }

    /**
     * 在 [from, to) 里从后往前找一个合适的断点；找不到就返回 to（硬切）。
     */
    private int findBoundary(String content, int from, int to) {
        int lowerBound = from + maxChars / 2;
        for (int i = to; i > lowerBound; i--) {
            char c = content.charAt(i - 1);
            if (c == '\n') {
                return i;
            }
        }
        for (int i = to; i > lowerBound; i--) {
            char c = content.charAt(i - 1);
            if (c == '。' || c == '！' || c == '？' || c == '；' || c == '.' || c == '!' || c == '?' || c == ';') {
                return i;
            }
        }
        return to;
    }

    /** 统一换行符、压缩连续空白，避免同一份文档因为格式差异切出不同结果 */
    private String normalize(String text) {
        return text.replace("\r\n", "\n")
                .replace('\r', '\n')
                .replaceAll("[ \\t\\x0B\\f]+", " ")
                .replaceAll("\n{3,}", "\n\n")
                .strip();
    }

    public int getMaxChars() {
        return maxChars;
    }

    public int getOverlap() {
        return overlap;
    }

    /** 切分结果，还没落库 */
    public record ChunkDraft(int index, String content, int startOffset, int charCount) {
    }
}
