package com.cdwy.permission.config;

import java.lang.annotation.*;

/**
 * 统一身份目录接口（查询人员、部门）的访问规则：调用者须在被调用的应用内拥有该接口的 API 资源授权。
 *
 * <p>应用编码取自路径 {@code /api/apps/{应用编码}/...}；匹配 API 资源时使用去掉该前缀后的相对路径，例如接入应用把 {@code GET
 * /auth/departments} 登记为 API 资源，并绑定到“添加教师”按钮，只有拥有该按钮的用户才能逐层浏览部门。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface AppAccess {}
