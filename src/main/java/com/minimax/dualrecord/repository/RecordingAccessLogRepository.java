package com.minimax.dualrecord.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.minimax.dualrecord.domain.RecordingAccessLog;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface RecordingAccessLogRepository extends BaseMapper<RecordingAccessLog> {}
