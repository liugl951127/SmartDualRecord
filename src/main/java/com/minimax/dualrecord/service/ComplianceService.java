package com.minimax.dualrecord.service;

import com.minimax.dualrecord.domain.ForbiddenPhrase;
import com.minimax.dualrecord.repository.ForbiddenPhraseRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 合规引擎 · 禁播词扫描
 *
 * 命中即触发：
 *  - HIGH: 立即阻断 + 弹窗告警
 *  - MEDIUM: 标红 + 坐席纠正
 *  - LOW: 记录 + 事后质检
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ComplianceService {

    private final ForbiddenPhraseRepository repository;
    private final Map<String, ForbiddenPhrase> phraseCache = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        refreshCache();
    }

    public void refreshCache() {
        phraseCache.clear();
        List<ForbiddenPhrase> all = repository.selectList(null);
        for (ForbiddenPhrase p : all) {
            phraseCache.put(p.getPhrase(), p);
        }
        log.info("禁播词缓存刷新: {} 条", phraseCache.size());
    }

    /**
     * 扫描一段文字中的禁播词
     * @return 命中列表
     */
    public List<Hit> scan(String text) {
        if (text == null || text.isEmpty()) return List.of();
        List<Hit> hits = new ArrayList<>();
        for (Map.Entry<String, ForbiddenPhrase> entry : phraseCache.entrySet()) {
            String phrase = entry.getKey();
            if (text.contains(phrase)) {
                ForbiddenPhrase p = entry.getValue();
                hits.add(new Hit(phrase, p.getSeverity(), p.getRegulationRef()));
            }
        }
        return hits;
    }

    /**
     * 判断是否阻断
     */
    public boolean isBlocking(List<Hit> hits) {
        return hits.stream().anyMatch(h -> "HIGH".equals(h.severity()));
    }

    public record Hit(String phrase, String severity, String regulationRef) {}
}
