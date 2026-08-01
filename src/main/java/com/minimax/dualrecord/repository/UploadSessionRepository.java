package com.minimax.dualrecord.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.minimax.dualrecord.domain.UploadSession;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface UploadSessionRepository extends BaseMapper<UploadSession> {}
