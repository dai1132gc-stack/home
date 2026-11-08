package com.example.realestate.infrastructure.pdf;

import com.example.realestate.modules.contract.model.ContractSnapshot;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("合同 PDF：中文、金额大写、长地址与跨页")
class ContractPdfGeneratorTest {

    private final ContractPdfGenerator generator =
            new PdfBoxContractPdfGenerator(new DefaultResourceLoader());

    @Test
    @DisplayName("生成含中文、金额大写、长地址的多页 PDF，可提取中文文本")
    void rendersChineseContract() throws Exception {
        String longAddress = "上海市浦东新区张江镇科苑路 88 弄 12 号楼 3 单元 1802 室（近地铁 2 号线张江高科站，"
                + "小区东门进入后直行约 200 米，物业服务中心旁，房屋为南北通透户型，附带地下车位 B2-118 号）";
        ContractSnapshot snapshot = new ContractSnapshot(
                "STANDARD_LEASE-v1",
                Instant.parse("2026-11-08T00:00:00Z"),
                new ContractSnapshot.House(1L, "整租·张江人才公寓 2 室 1 厅", "RENT",
                        "张江人才公寓", "浦东新区", longAddress,
                        new BigDecimal("89.50"), 2, 1, "18/28"),
                List.of(
                        new ContractSnapshot.Party("LESSOR", "王建国", "13812345678", "310101********1234"),
                        new ContractSnapshot.Party("LESSEE", "李小明", "13987654321", "310105********5678"),
                        new ContractSnapshot.Party("AGENT", "安居中介", "021-58880000", null)
                ),
                new ContractSnapshot.Terms(new BigDecimal("4500.50"), new BigDecimal("9000.00"),
                        "MONTHLY", LocalDate.of(2026, 12, 1), LocalDate.of(2027, 11, 30),
                        ChineseAmountConverter.toChinese(new BigDecimal("4500.50"))),
                "第一条 租赁房屋\n出租方将坐落于{long}的房屋出租给承租方居住使用。\n".replace("{long}", longAddress)
                        + "第二条 租赁期限\n租赁期限自2026年12月1日起至2027年11月30日止。\n"
                        + "第三条 租金与押金\n月租金为人民币肆仟伍佰元伍角，押金为人民币玖仟元整。\n"
        );

        byte[] pdf = generator.generate(snapshot, "HT20261108-000001", "房屋租赁合同");

        assertThat(pdf).isNotEmpty();
        assertThat(new String(pdf, 0, 4)).isEqualTo("%PDF");
        try (PDDocument document = Loader.loadPDF(pdf)) {
            assertThat(document.getNumberOfPages()).isGreaterThanOrEqualTo(2);
            String text = new PDFTextStripper().getText(document);
            assertThat(text).contains("房屋租赁合同");
            assertThat(text).contains("合同编号：HT20261108-000001");
            assertThat(text).contains("肆仟伍佰元伍角");
            assertThat(text).contains("王建国");
            assertThat(text).contains("李小明");
            // 长地址换行后仍完整保留首尾内容
            assertThat(text).contains("上海市浦东新区张江镇科苑路");
            assertThat(text).contains("地下车位");
            // 页脚页码
            assertThat(text).contains("第 1 页 / 共");
        }
    }

    @Test
    @DisplayName("空快照字段不抛异常（占位符显示为 —）")
    void handlesMissingFields() throws Exception {
        ContractSnapshot snapshot = new ContractSnapshot(
                "STANDARD_LEASE-v1", Instant.now(),
                null,
                List.of(),
                new ContractSnapshot.Terms(new BigDecimal("1000.00"), BigDecimal.ZERO,
                        "MONTHLY", LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31),
                        ChineseAmountConverter.toChinese(new BigDecimal("1000.00"))),
                "第一条 测试条款"
        );
        byte[] pdf = generator.generate(snapshot, "HT-TEST-1", "房屋租赁合同");
        assertThat(pdf).isNotEmpty();
        try (PDDocument document = Loader.loadPDF(pdf)) {
            assertThat(document.getNumberOfPages()).isGreaterThanOrEqualTo(1);
        }
    }
}
