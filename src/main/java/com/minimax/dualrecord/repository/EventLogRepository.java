package com.minimax.dualrecord.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.minimax.dualrecord.domain.EventLog;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface EventLogRepository extends BaseMapper<EventLog> {
}
