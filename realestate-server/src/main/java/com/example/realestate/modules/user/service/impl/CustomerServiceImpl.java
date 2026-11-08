package com.example.realestate.modules.user.service.impl;

import com.example.realestate.common.api.PageResponse;
import com.example.realestate.common.enums.ErrorCode;
import com.example.realestate.common.exception.BusinessException;
import com.example.realestate.modules.user.mapper.SysUserMapper;
import com.example.realestate.modules.user.model.query.CustomerQuery;
import com.example.realestate.modules.user.model.vo.CustomerVO;
import com.example.realestate.modules.user.service.CustomerService;
import org.springframework.stereotype.Service;

import java.util.List;

/** 客户账号列表：条件与分页全部下推 SQL */
@Service
public class CustomerServiceImpl implements CustomerService {

    private final SysUserMapper sysUserMapper;

    public CustomerServiceImpl(SysUserMapper sysUserMapper) {
        this.sysUserMapper = sysUserMapper;
    }

    @Override
    public PageResponse<CustomerVO> list(CustomerQuery query) {
        CustomerQuery safe = query == null ? new CustomerQuery() : query;
        if (safe.getKeyword() != null) {
            String trimmed = safe.getKeyword().trim();
            if (trimmed.length() > 64) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "搜索关键字过长");
            }
            safe.setKeyword(trimmed.isEmpty() ? null : trimmed);
        }
        long total = sysUserMapper.countCustomers(safe);
        long size = safe.normalizedSize();
        List<CustomerVO> items = total == 0 ? List.of()
                : sysUserMapper.selectCustomers(safe, safe.offset(), size);
        return PageResponse.of(items, total, safe.normalizedPage(), size);
    }
}
