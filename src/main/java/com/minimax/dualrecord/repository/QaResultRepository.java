package com.minimax.dualrecord.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.minimax.dualrecord.domain.QaResult;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface QaResultRepository extends BaseMapper<QaResult> {
}
