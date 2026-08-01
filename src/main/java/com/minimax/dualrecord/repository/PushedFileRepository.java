package com.minimax.dualrecord.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.minimax.dualrecord.domain.PushedFile;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface PushedFileRepository extends BaseMapper<PushedFile> {
}
