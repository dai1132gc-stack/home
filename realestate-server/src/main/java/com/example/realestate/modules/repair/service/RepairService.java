package com.example.realestate.modules.repair.service;

import com.example.realestate.common.api.PageResponse;
import com.example.realestate.modules.repair.model.dto.RepairCreateRequest;
import com.example.realestate.modules.repair.model.query.RepairQuery;
import com.example.realestate.modules.repair.model.vo.RepairDetailVO;
import com.example.realestate.modules.repair.model.vo.RepairListItemVO;
import com.example.realestate.modules.repair.model.vo.RepairMediaVO;
import org.springframework.web.multipart.MultipartFile;

/**
 * 报修服务：状态机、数据权限（本人或 ADMIN）、私有图片与状态日志。
 * 非法迁移 409；他人资源 404；游客在接口层 401。
 */
public interface RepairService {

    PageResponse<RepairListItemVO> list(RepairQuery query);

    RepairDetailVO detail(Long id);

    RepairDetailVO create(RepairCreateRequest request);

    RepairDetailVO accept(Long id, String reason);

    RepairDetailVO start(Long id, String reason);

    RepairDetailVO resolve(Long id, String reason);

    RepairDetailVO close(Long id, String reason);

    RepairDetailVO reopen(Long id, String reason);

    RepairDetailVO cancel(Long id, String reason);

    RepairMediaVO addMedia(Long id, MultipartFile file);

    RepairMediaContent downloadMedia(Long ticketId, Long mediaId);

    void deleteMedia(Long ticketId, Long mediaId);

    /** 私有图片下载结果：由 Controller 以 private,no-store 流式返回 */
    record RepairMediaContent(byte[] content, String contentType, String filename) {
    }
}
