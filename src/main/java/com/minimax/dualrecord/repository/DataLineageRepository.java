package com.minimax.dualrecord.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.minimax.dualrecord.domain.DataLineage;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface DataLineageRepository extends BaseMapper<DataLineage> {
}
