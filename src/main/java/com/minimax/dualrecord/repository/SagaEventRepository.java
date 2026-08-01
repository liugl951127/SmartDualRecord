package com.minimax.dualrecord.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.minimax.dualrecord.domain.SagaEvent;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface SagaEventRepository extends BaseMapper<SagaEvent> {
}
