package com.cdwy.permission.vo.identity;

/**
 * 接入应用的登录结果；不含用户资料，角色与权限由 /auth/me 查询。
 *
 * @param accessToken 访问令牌，aud 为登录时的 systems
 * @param tokenType 令牌类型，固定为 Bearer
 * @param expiresIn 有效期秒数
 */
public record LoginVO(String accessToken, String tokenType, long expiresIn) {}
