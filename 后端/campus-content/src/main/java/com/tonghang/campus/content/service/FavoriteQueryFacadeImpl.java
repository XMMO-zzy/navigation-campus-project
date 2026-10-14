package com.tonghang.campus.content.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.tonghang.campus.common.favorite.FavoriteQueryFacade;
import com.tonghang.campus.content.entity.Favorite;
import com.tonghang.campus.content.mapper.FavoriteMapper;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 收藏关系只读门面实现（M2 提供给 M5/M6 的活动提醒使用）。
 *
 * <p>来源：《10》FR-M3-05 活动与赛事提醒 / 《12》§2.2 favorite（target_type 3 活动 / 4 竞赛）。
 *
 * <p>依赖方向：campus-content → campus-common（仅接口），无循环（《11》§2.1）。
 */
@Service
public class FavoriteQueryFacadeImpl implements FavoriteQueryFacade {

    private final FavoriteMapper favoriteMapper;

    public FavoriteQueryFacadeImpl(FavoriteMapper favoriteMapper) {
        this.favoriteMapper = favoriteMapper;
    }

    @Override
    public List<Long> usersWhoFavorited(int targetType, Long targetId) {
        if (targetId == null) {
            return Collections.emptyList();
        }
        List<Favorite> list = favoriteMapper.selectList(new LambdaQueryWrapper<Favorite>()
                .eq(Favorite::getTargetType, targetType)
                .eq(Favorite::getTargetId, targetId));
        Set<Long> ids = new LinkedHashSet<>(list.size());
        for (Favorite f : list) {
            if (f.getUserId() != null) {
                ids.add(f.getUserId());
            }
        }
        return new ArrayList<>(ids);
    }
}
