package com.example.realestate.modules.transaction.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.realestate.modules.transaction.model.entity.TransactionStatusLog;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface TransactionStatusLogMapper extends BaseMapper<TransactionStatusLog> {
}
