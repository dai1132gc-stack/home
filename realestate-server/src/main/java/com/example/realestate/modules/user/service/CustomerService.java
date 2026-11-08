package com.example.realestate.modules.user.service;

import com.example.realestate.common.api.PageResponse;
import com.example.realestate.modules.user.model.query.CustomerQuery;
import com.example.realestate.modules.user.model.vo.CustomerVO;

/** 客户账号查询（仅 ADMIN），供合同起草选择承租方 */
public interface CustomerService {

    PageResponse<CustomerVO> list(CustomerQuery query);
}
