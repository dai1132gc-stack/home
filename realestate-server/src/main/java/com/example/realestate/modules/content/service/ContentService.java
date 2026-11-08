package com.example.realestate.modules.content.service;

import com.example.realestate.common.api.PageResponse;
import com.example.realestate.modules.content.model.dto.GuideCreateRequest;
import com.example.realestate.modules.content.model.dto.GuideUpdateRequest;
import com.example.realestate.modules.content.model.dto.PolicyCreateRequest;
import com.example.realestate.modules.content.model.dto.PolicyUpdateRequest;
import com.example.realestate.modules.content.model.query.ContentQuery;
import com.example.realestate.modules.content.model.vo.GuideDetailVO;
import com.example.realestate.modules.content.model.vo.GuideListItemVO;
import com.example.realestate.modules.content.model.vo.PolicyDetailVO;
import com.example.realestate.modules.content.model.vo.PolicyListItemVO;
import org.springframework.web.multipart.MultipartFile;

/**
 * 内容与政策服务：公开只读已发布；ADMIN 可见全部并维护；
 * 编辑仅 DRAFT/OFFLINE，已发布需先下架；正文按纯文本存储与输出。
 */
public interface ContentService {

    PageResponse<GuideListItemVO> listGuides(ContentQuery query);

    GuideDetailVO getGuide(Long id);

    GuideDetailVO createGuide(GuideCreateRequest request);

    GuideDetailVO updateGuide(Long id, GuideUpdateRequest request);

    GuideDetailVO publishGuide(Long id);

    GuideDetailVO offlineGuide(Long id);

    void deleteGuide(Long id);

    PageResponse<PolicyListItemVO> listPolicies(ContentQuery query);

    PolicyDetailVO getPolicy(Long id);

    PolicyDetailVO createPolicy(PolicyCreateRequest request);

    PolicyDetailVO updatePolicy(Long id, PolicyUpdateRequest request);

    PolicyDetailVO publishPolicy(Long id);

    PolicyDetailVO offlinePolicy(Long id);

    void deletePolicy(Long id);

    PolicyDetailVO uploadPolicyAttachment(Long id, MultipartFile file);

    PolicyAttachment downloadPolicyAttachment(Long id);

    /** 政策附件下载结果：鉴权后由 Controller 以 attachment 流式返回 */
    record PolicyAttachment(byte[] content, String filename) {
    }
}
