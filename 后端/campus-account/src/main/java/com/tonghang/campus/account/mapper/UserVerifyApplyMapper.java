package com.tonghang.campus.account.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.tonghang.campus.account.entity.UserVerifyApply;
import org.apache.ibatis.annotations.Mapper;

/**
 * UserVerifyApply Mapper（M1 账号域）。
 *
 * <p>写入权限：仅 campus-account 模块内 Service 可访问（《11》§2.3 数据访问边界）。
 */
@Mapper
public interface UserVerifyApplyMapper extends BaseMapper<UserVerifyApply> {
}
