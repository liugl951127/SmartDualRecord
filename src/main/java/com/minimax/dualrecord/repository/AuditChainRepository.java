package com.minimax.dualrecord.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.minimax.dualrecord.domain.AuditChainEntry;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AuditChainRepository extends BaseMapper<AuditChainEntry> {
}
