package com.tycoon.admin.service;

import com.tycoon.admin.domain.GameSegment;
import com.tycoon.admin.mapper.SegmentMapper;
import com.tycoon.api.dto.GameSegmentDto;
import com.tycoon.api.game.GameConfigDubboService;
import org.apache.dubbo.config.annotation.DubboService;

import java.util.List;

/**
 * 游戏配置 Dubbo 提供者：wheel-service 通过它实时拿到后台配置的转盘商品。
 */
@DubboService(interfaceClass = GameConfigDubboService.class)
public class GameConfigServiceImpl implements GameConfigDubboService {

    private final SegmentMapper segmentMapper;

    public GameConfigServiceImpl(SegmentMapper segmentMapper) {
        this.segmentMapper = segmentMapper;
    }

    @Override
    public List<GameSegmentDto> listEnabledSegments() {
        return segmentMapper.listEnabled().stream().map(GameConfigServiceImpl::toDto).toList();
    }

    @Override
    public List<GameSegmentDto> listAllSegments() {
        return segmentMapper.listAll().stream().map(GameConfigServiceImpl::toDto).toList();
    }

    public static GameSegmentDto toDto(GameSegment s) {
        GameSegmentDto d = new GameSegmentDto();
        d.setId(s.getId());
        d.setIdx(s.getIdx());
        d.setKey(s.getSegKey());
        d.setLabel(s.getLabel());
        d.setIcon(s.getIcon());
        d.setType(s.getType());
        d.setDesc(s.getDescription());
        d.setPercent(s.getPercent());
        d.setPoints(s.getPoints());
        d.setEnabled(s.getEnabled());
        return d;
    }
}
