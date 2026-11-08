package com.example.realestate.modules.contract.service;

import com.example.realestate.common.api.PageResponse;
import com.example.realestate.modules.contract.model.dto.ContractCreateRequest;
import com.example.realestate.modules.contract.model.dto.ContractTerminateRequest;
import com.example.realestate.modules.contract.model.dto.ContractUpdateRequest;
import com.example.realestate.modules.contract.model.query.ContractQuery;
import com.example.realestate.modules.contract.model.vo.ContractDetailVO;
import com.example.realestate.modules.contract.model.vo.ContractListItemVO;

/**
 * 合同服务：草稿 → 待签 → 生效 → 终止/到期，含 PDF 与房源状态联动。
 * 数据权限：ADMIN 全部，CUSTOMER 仅本人，他人合同返回 404。
 */
public interface ContractService {

    PageResponse<ContractListItemVO> list(ContractQuery query);

    /** CUSTOMER 我的合同：强制 customerId 为当前用户 */
    PageResponse<ContractListItemVO> listMine(ContractQuery query);

    ContractDetailVO detail(Long id);

    ContractDetailVO create(ContractCreateRequest request);

    /** 仅 DRAFT 可编辑；version 冲突返回 409 */
    ContractDetailVO update(Long id, ContractUpdateRequest request);

    /** DRAFT → PENDING_SIGN，提交前必须成功生成 PDF */
    ContractDetailVO submit(Long id);

    /** CUSTOMER 本人确认：PENDING_SIGN → ACTIVE，房源置 CONTRACTED（行锁 + 唯一键双重保护） */
    ContractDetailVO confirm(Long id);

    /** ADMIN 终止：DRAFT → CANCELLED，PENDING_SIGN/ACTIVE → TERMINATED */
    ContractDetailVO terminate(Long id, ContractTerminateRequest request);

    /** 鉴权下载：仅本人或 ADMIN，流式返回，不产生公开 URL */
    ContractPdf downloadPdf(Long id);

    record ContractPdf(byte[] content, String filename) {
    }
}
