package com.minimax.dualrecord.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.minimax.dualrecord.domain.ForbiddenPhrase;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ForbiddenPhraseRepository extends BaseMapper<ForbiddenPhrase> {
}
