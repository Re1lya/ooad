package com.cdwy.permission.vo.auth;

import com.cdwy.permission.vo.user.UserVO;

/**
 * 控制台登录结果。
 *
 * @param accessToken 访问令牌
 * @param tokenType 令牌类型，固定为 Bearer
 * @param expiresIn 有效期秒数
 * @param user 登录用户
 */
public record TokenVO(String accessToken, String tokenType, long expiresIn, UserVO user) {}
