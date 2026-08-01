package com.minimax.dualrecord.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.minimax.dualrecord.domain.SagaStep;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface SagaStepRepository extends BaseMapper<SagaStep> {
}
