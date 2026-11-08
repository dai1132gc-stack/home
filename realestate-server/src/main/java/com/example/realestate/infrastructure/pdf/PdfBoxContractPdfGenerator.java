package com.example.realestate.infrastructure.pdf;

import com.example.realestate.modules.contract.model.ContractSnapshot;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * 基于 PDFBox 的合同 PDF 渲染：
 *  - 中文字体使用随项目分发的 Noto Sans SC（SIL OFL 1.1），以子集方式嵌入，避免乱码与方块；
 *  - 金额同时输出小写与人民币大写；
 *  - 长地址按字符宽度换行，表格跨页时重复表头；
 *  - 页脚输出「第 X 页 / 共 N 页」。
 * 仅在生成时读取快照，不访问数据库，便于单元测试。
 */
@Component
public class PdfBoxContractPdfGenerator implements ContractPdfGenerator {

    private static final String FONT_PATH = "classpath:fonts/NotoSansSC-Regular.ttf";

    private final byte[] fontBytes;

    public PdfBoxContractPdfGenerator(ResourceLoader resourceLoader) {
        try (InputStream in = resourceLoader.getResource(FONT_PATH).getInputStream()) {
            this.fontBytes = in.readAllBytes();
        } catch (IOException e) {
            throw new IllegalStateException("合同 PDF 中文字体加载失败：" + FONT_PATH, e);
        }
    }

    @Override
    public byte[] generate(ContractSnapshot snapshot, String contractNo, String title) {
        if (snapshot == null) {
            throw new IllegalArgumentException("合同快照不能为空");
        }
        try (PDDocument document = new PDDocument()) {
            PDType0Font font = PDType0Font.load(document, new ByteArrayInputStream(fontBytes), true);
            Layout layout = new Layout(document, font);
            layout.render(snapshot, contractNo, title);
            layout.finish();
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            document.save(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("合同 PDF 生成失败", e);
        }
    }

    /** 单次生成使用的排版上下文（非线程共享） */
    private static final class Layout {

        private static final float MARGIN = 56f;
        private static final float CONTENT_WIDTH = PDRectangle.A4.getWidth() - MARGIN * 2;
        private static final float BOTTOM = 72f;
        private static final float PAD = 5f;
        private static final DateTimeFormatter CN_DATE = DateTimeFormatter.ofPattern("yyyy年M月d日");

        private final PDDocument document;
        private final PDType0Font font;
        private final List<PDPage> pages = new ArrayList<>();

        private PDPageContentStream stream;
        private float y;

        Layout(PDDocument document, PDType0Font font) {
            this.document = document;
            this.font = font;
        }

        void render(ContractSnapshot snapshot, String contractNo, String title) throws IOException {
            newPage();

            drawCentered(safe(title) + "（" + safe(snapshot.templateVersion()) + "）", 19f, 26f);
            drawCentered("合同编号：" + safe(contractNo), 9.5f, 14f);

            section("一、合同双方");
            table(new String[]{"身份", "名称", "联系电话", "证件号码"},
                    new float[]{96f, 150f, 110f, CONTENT_WIDTH - 356f},
                    partyRows(snapshot));

            section("二、房屋信息");
            ContractSnapshot.House house = snapshot.house() == null
                    ? new ContractSnapshot.House(null, "", "", "", "", "", null, null, null, null)
                    : snapshot.house();
            table(new String[]{"项目", "内容"},
                    new float[]{100f, CONTENT_WIDTH - 100f},
                    List.of(
                            new String[]{"房屋坐落", safe(house.address())},
                            new String[]{"小区/区域", safe(house.community()) + " / " + safe(house.region())},
                            new String[]{"户型", nullSafe(house.rooms(), "0") + " 室 " + nullSafe(house.bathrooms(), "0") + " 卫"},
                            new String[]{"建筑面积", decimal(house.area()) + " ㎡"},
                            new String[]{"楼层", blankToDash(house.floor())}
                    ));

            section("三、租赁条款");
            ContractSnapshot.Terms terms = snapshot.terms();
            table(new String[]{"条款", "约定"},
                    new float[]{100f, CONTENT_WIDTH - 100f},
                    List.of(
                            new String[]{"租金（大写）", safe(terms == null ? null : terms.amountInWords())},
                            new String[]{"租金（小写）", money(terms == null ? null : terms.amount())},
                            new String[]{"押金", money(terms == null ? null : terms.deposit())},
                            new String[]{"付款方式", paymentCycleLabel(terms == null ? null : terms.paymentCycle())},
                            new String[]{"租赁期限", termRange(terms)},
                            new String[]{"合同模板", safe(snapshot.templateVersion())}
                    ));

            section("四、合同条款");
            renderClauses(snapshot);

            ensureSpace(60f);
            y -= 6f;
            drawCentered("（以下无正文）", 10f, 14f);
            y -= 18f;
            ensureSpace(110f);
            drawSignature(snapshot);
        }

        void finish() throws IOException {
            closeStream();
            int total = pages.size();
            for (int i = 0; i < total; i++) {
                try (PDPageContentStream footer = new PDPageContentStream(
                        document, pages.get(i), PDPageContentStream.AppendMode.APPEND, true, true)) {
                    footer.setStrokingColor(0.78f, 0.78f, 0.78f);
                    footer.setLineWidth(0.5f);
                    footer.moveTo(MARGIN, 52f);
                    footer.lineTo(PDRectangle.A4.getWidth() - MARGIN, 52f);
                    footer.stroke();
                    footer.setNonStrokingColor(0.4f, 0.4f, 0.4f);
                    drawFooterText(footer, "第 " + (i + 1) + " 页 / 共 " + total + " 页", 9f, 36f);
                }
            }
        }

        // ---------- 区块渲染 ----------

        private List<String[]> partyRows(ContractSnapshot snapshot) {
            List<String[]> rows = new ArrayList<>();
            rows.add(partyRow("出租方（甲方）", snapshot, "LESSOR"));
            rows.add(partyRow("承租方（乙方）", snapshot, "LESSEE"));
            rows.add(partyRow("居间方（丙方）", snapshot, "AGENT"));
            return rows;
        }

        private String[] partyRow(String label, ContractSnapshot snapshot, String type) {
            if (snapshot.parties() != null) {
                for (ContractSnapshot.Party party : snapshot.parties()) {
                    if (type.equals(party.type())) {
                        return new String[]{label, safe(party.name()), blankToDash(party.phone()),
                                blankToDash(party.idCardMasked())};
                    }
                }
            }
            return new String[]{label, "—", "—", "—"};
        }

        private void renderClauses(ContractSnapshot snapshot) throws IOException {
            String clauses = snapshot.clauses() == null ? "" : snapshot.clauses();
            String resolved = fillPlaceholders(clauses, snapshot);
            float leading = 17f;
            for (String paragraph : resolved.split("\n", -1)) {
                String line = paragraph.trim();
                if (line.isEmpty()) {
                    y -= 6f;
                    continue;
                }
                boolean isHeading = line.startsWith("第") && line.contains("条");
                List<String> lines = wrap(line, 10.5f, CONTENT_WIDTH - (isHeading ? 0f : 21f));
                ensureSpace(lines.size() * leading + 4f);
                float indent = isHeading ? 0f : 21f;
                for (int i = 0; i < lines.size(); i++) {
                    drawText(lines.get(i), 10.5f, MARGIN + indent, y);
                    y -= leading;
                }
                y -= 2f;
            }
        }

        private String fillPlaceholders(String clauses, ContractSnapshot snapshot) {
            ContractSnapshot.Terms terms = snapshot.terms();
            ContractSnapshot.House house = snapshot.house();
            String lessorName = "";
            String lesseeName = "";
            if (snapshot.parties() != null) {
                for (ContractSnapshot.Party party : snapshot.parties()) {
                    if ("LESSOR".equals(party.type())) {
                        lessorName = safe(party.name());
                    } else if ("LESSEE".equals(party.type())) {
                        lesseeName = safe(party.name());
                    }
                }
            }
            return clauses
                    .replace("{address}", safe(house == null ? null : house.address()))
                    .replace("{amount}", terms == null ? "" : safe(terms.amountInWords()))
                    .replace("{deposit}", terms == null ? "" : ChineseAmountConverter.toChinese(terms.deposit()))
                    .replace("{paymentCycle}", terms == null ? "" : paymentCycleLabel(terms.paymentCycle()))
                    .replace("{startDate}", terms == null ? "" : date(terms.startDate()))
                    .replace("{endDate}", terms == null ? "" : date(terms.endDate()))
                    .replace("{lessorName}", lessorName)
                    .replace("{lesseeName}", lesseeName);
        }

        private void drawSignature(ContractSnapshot snapshot) throws IOException {
            String lessor = "";
            String lessee = "";
            if (snapshot.parties() != null) {
                for (ContractSnapshot.Party party : snapshot.parties()) {
                    if ("LESSOR".equals(party.type())) {
                        lessor = safe(party.name());
                    } else if ("LESSEE".equals(party.type())) {
                        lessee = safe(party.name());
                    }
                }
            }
            drawText("甲方（出租方）签字：____________________", 10.5f, MARGIN, y);
            drawText("日期：______年____月____日", 10.5f, MARGIN + 300f, y);
            y -= 30f;
            drawText("乙方（承租方）签字：____________________", 10.5f, MARGIN, y);
            drawText("日期：______年____月____日", 10.5f, MARGIN + 300f, y);
            y -= 30f;
            drawText("居间方（盖章）：____________________", 10.5f, MARGIN, y);
            drawText("日期：______年____月____日", 10.5f, MARGIN + 300f, y);
            y -= 24f;
            drawText("甲方姓名：" + blankToDash(lessor) + "    乙方姓名：" + blankToDash(lessee),
                    9f, MARGIN, y);
        }

        // ---------- 基础绘制 ----------

        private void section(String title) throws IOException {
            ensureSpace(46f);
            y -= 12f;
            drawText(title, 12.5f, MARGIN, y);
            y -= 7f;
            stream.setStrokingColor(0.55f, 0.55f, 0.55f);
            stream.setLineWidth(0.6f);
            stream.moveTo(MARGIN, y);
            stream.lineTo(MARGIN + CONTENT_WIDTH, y);
            stream.stroke();
            y -= 12f;
        }

        /**
         * 表格：按行写入，当前页放不下时换页并重复表头；
         * 单元格文本先按列宽换行，行高由最多行数决定。
         */
        private void table(String[] headers, float[] widths, List<String[]> rows) throws IOException {
            this.lastHeaders = headers;
            this.lastWidths = widths;
            drawTableRow(headers, widths, true);
            for (String[] row : rows) {
                drawTableRow(row, widths, false);
            }
        }

        private String[] lastHeaders;
        private float[] lastWidths;

        private void drawTableRow(String[] cells, float[] widths, boolean header) throws IOException {
            float fontSize = header ? 10f : 9.8f;
            float leading = fontSize + 5f;
            List<List<String>> wrapped = new ArrayList<>();
            int maxLines = 1;
            for (int i = 0; i < cells.length; i++) {
                float inner = widths[i] - PAD * 2;
                List<String> lines = wrap(safe(cells[i]), fontSize, inner);
                if (lines.isEmpty()) {
                    lines = List.of("");
                }
                wrapped.add(lines);
                maxLines = Math.max(maxLines, lines.size());
            }
            float rowHeight = maxLines * leading + PAD * 2;

            // 整行放不下则换页并重复表头（表头本身除外）
            if (y - rowHeight < BOTTOM) {
                newPage();
                if (!header && lastHeaders != null) {
                    drawTableRow(lastHeaders, lastWidths, true);
                }
            }
            float top = y;
            float x = MARGIN;
            for (int i = 0; i < cells.length; i++) {
                if (header) {
                    stream.setNonStrokingColor(0.93f, 0.93f, 0.93f);
                    stream.addRect(x, top - rowHeight, widths[i], rowHeight);
                    stream.fill();
                    stream.setNonStrokingColor(0f, 0f, 0f);
                }
                stream.setStrokingColor(0.65f, 0.65f, 0.65f);
                stream.setLineWidth(0.5f);
                stream.addRect(x, top - rowHeight, widths[i], rowHeight);
                stream.stroke();

                float baseline = top - PAD - fontSize;
                for (String line : wrapped.get(i)) {
                    drawText(line, fontSize, x + PAD, baseline);
                    baseline -= leading;
                }
                x += widths[i];
            }
            y = top - rowHeight;
        }

        private void newPage() throws IOException {
            closeStream();
            PDPage page = new PDPage(PDRectangle.A4);
            document.addPage(page);
            pages.add(page);
            stream = new PDPageContentStream(document, page);
            y = PDRectangle.A4.getHeight() - MARGIN;
        }

        private void ensureSpace(float height) throws IOException {
            if (y - height < BOTTOM) {
                newPage();
            }
        }

        private void closeStream() throws IOException {
            if (stream != null) {
                stream.close();
                stream = null;
            }
        }

        private void drawText(String text, float size, float x, float baseline) throws IOException {
            if (text == null || text.isEmpty()) {
                return;
            }
            stream.beginText();
            stream.setFont(font, size);
            stream.newLineAtOffset(x, baseline);
            stream.showText(sanitize(text));
            stream.endText();
        }

        private void drawCentered(String text, float size, float leading) throws IOException {
            ensureSpace(leading + 6f);
            float width = widthOf(text, size);
            drawText(text, size, MARGIN + (CONTENT_WIDTH - width) / 2f, y);
            y -= leading;
        }

        private void drawFooterText(PDPageContentStream footer, String text, float size, float baseline)
                throws IOException {
            float width = font.getStringWidth(sanitize(text)) / 1000f * size;
            footer.beginText();
            footer.setFont(font, size);
            footer.newLineAtOffset((PDRectangle.A4.getWidth() - width) / 2f, baseline);
            footer.showText(sanitize(text));
            footer.endText();
        }

        private float widthOf(String text, float size) throws IOException {
            return font.getStringWidth(sanitize(text)) / 1000f * size;
        }

        /**
         * 按字符宽度换行：ASCII 连续串（编号、金额、英文）视为不可拆分 token，
         * 中文按字断行，避免长地址撑出页面。
         */
        private List<String> wrap(String text, float size, float maxWidth) throws IOException {
            List<String> lines = new ArrayList<>();
            if (text == null || text.isEmpty()) {
                return lines;
            }
            for (String paragraph : text.split("\n", -1)) {
                if (paragraph.isEmpty()) {
                    continue;
                }
                List<String> tokens = tokenize(sanitize(paragraph));
                StringBuilder line = new StringBuilder();
                float width = 0f;
                for (String token : tokens) {
                    float tokenWidth = widthOf(token, size);
                    if (width + tokenWidth > maxWidth && line.length() > 0) {
                        lines.add(line.toString());
                        line.setLength(0);
                        width = 0f;
                    }
                    if (tokenWidth > maxWidth) {
                        for (int i = 0; i < token.length(); i++) {
                            String ch = String.valueOf(token.charAt(i));
                            float charWidth = widthOf(ch, size);
                            if (width + charWidth > maxWidth && line.length() > 0) {
                                lines.add(line.toString());
                                line.setLength(0);
                                width = 0f;
                            }
                            line.append(ch);
                            width += charWidth;
                        }
                        continue;
                    }
                    line.append(token);
                    width += tokenWidth;
                }
                if (line.length() > 0) {
                    lines.add(line.toString().stripTrailing());
                }
            }
            return lines;
        }

        private List<String> tokenize(String text) {
            List<String> tokens = new ArrayList<>();
            StringBuilder word = new StringBuilder();
            for (int i = 0; i < text.length(); i++) {
                char ch = text.charAt(i);
                boolean asciiWord = (ch >= '0' && ch <= '9') || (ch >= 'A' && ch <= 'Z')
                        || (ch >= 'a' && ch <= 'z') || ch == '-' || ch == '.' || ch == '/' || ch == '%'
                        || ch == ',' || ch == '¥' || ch == ':';
                if (asciiWord) {
                    word.append(ch);
                } else {
                    if (word.length() > 0) {
                        tokens.add(word.toString());
                        word.setLength(0);
                    }
                    tokens.add(String.valueOf(ch));
                }
            }
            if (word.length() > 0) {
                tokens.add(word.toString());
            }
            return tokens;
        }

        /** 非 BMP 字符（极少见生僻字）Noto Sans SC 无字形，替换为占位符避免整份 PDF 生成失败 */
        private String sanitize(String text) {
            StringBuilder sb = new StringBuilder(text.length());
            for (int i = 0; i < text.length(); i++) {
                char ch = text.charAt(i);
                if (Character.isHighSurrogate(ch) || Character.isLowSurrogate(ch)) {
                    sb.append('?');
                    if (Character.isHighSurrogate(ch) && i + 1 < text.length()) {
                        i++;
                    }
                } else if (ch == '\u0000') {
                    sb.append(' ');
                } else {
                    sb.append(ch);
                }
            }
            return sb.toString();
        }

        // ---------- 格式化 ----------

        private static String safe(String value) {
            return value == null ? "" : value;
        }

        private static String blankToDash(String value) {
            return value == null || value.isBlank() ? "—" : value;
        }

        private static String nullSafe(Integer value, String fallback) {
            return value == null ? fallback : String.valueOf(value);
        }

        private static String decimal(BigDecimal value) {
            return value == null ? "—" : value.stripTrailingZeros().toPlainString();
        }

        private static String money(BigDecimal value) {
            if (value == null) {
                return "—";
            }
            return "¥" + String.format("%,.2f", value);
        }

        private static String date(LocalDate value) {
            return value == null ? "—" : value.format(CN_DATE);
        }

        private static String termRange(ContractSnapshot.Terms terms) {
            if (terms == null || terms.startDate() == null || terms.endDate() == null) {
                return "—";
            }
            return date(terms.startDate()) + " 至 " + date(terms.endDate());
        }

        private static String paymentCycleLabel(String cycle) {
            if (cycle == null) {
                return "—";
            }
            return switch (cycle) {
                case "MONTHLY" -> "月付";
                case "QUARTERLY" -> "季付";
                case "YEARLY" -> "年付";
                default -> cycle;
            };
        }
    }
}
