package com.tonghang.campus.content.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.tonghang.campus.content.entity.PostTopicRel;
import org.apache.ibatis.annotations.Mapper;

/**
 * PostTopicRel Mapper。
 *
 * <p>仅本模块 Service 可访问（《11》§2.3 数据访问边界）。
 */
@Mapper
public interface PostTopicRelMapper extends BaseMapper<PostTopicRel> {
}
