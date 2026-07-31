package com.minimax.dualrecord.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.minimax.dualrecord.domain.Recording;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface RecordingRepository extends BaseMapper<Recording> {
}
