package com.tycoon.api.game;

import com.tycoon.api.dto.GameSegmentDto;

import java.util.List;

/**
 * 游戏配置 Dubbo 服务（提供者：admin-service）。
 *
 * <p>wheel-service 通过它拿到最新的转盘商品与概率，从而让后台改动实时生效。
 */
public interface GameConfigDubboService {

    /** 启用中的扇区，按 idx 升序 */
    List<GameSegmentDto> listEnabledSegments();

    /** 全部扇区（后台用） */
    List<GameSegmentDto> listAllSegments();
}
