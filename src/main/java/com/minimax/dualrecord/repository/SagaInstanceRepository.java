package com.minimax.dualrecord.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.minimax.dualrecord.domain.SagaInstance;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface SagaInstanceRepository extends BaseMapper<SagaInstance> {
}
