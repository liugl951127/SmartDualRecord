package com.minimax.dualrecord.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.minimax.dualrecord.domain.PreservationRecord;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface PreservationRecordRepository extends BaseMapper<PreservationRecord> {}
