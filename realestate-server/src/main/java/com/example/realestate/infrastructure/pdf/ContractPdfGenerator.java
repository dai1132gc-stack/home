package com.example.realestate.infrastructure.pdf;

import com.example.realestate.modules.contract.model.ContractSnapshot;

/**
 * 合同 PDF 生成适配：业务层只依赖本接口，具体实现（PDFBox）可替换。
 * 生成失败由调用方决定是否阻塞（草稿不阻塞，生效前必须成功）。
 */
public interface ContractPdfGenerator {

    /**
     * @param snapshot   合同创建时快照（房源、双方、条款、金额）
     * @param contractNo 合同编号（页眉展示，唯一）
     * @param title      合同标题
     * @return PDF 字节内容
     */
    byte[] generate(ContractSnapshot snapshot, String contractNo, String title);
}
