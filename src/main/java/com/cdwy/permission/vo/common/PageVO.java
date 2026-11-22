package com.cdwy.permission.vo.common;

import java.util.List;

/**
 * 分页结果，页码从 1 开始。
 *
 * @param page 页码
 * @param pageSize 每页条数
 * @param total 总条数
 * @param items 本页数据
 * @param <T> 数据项类型
 */
public record PageVO<T>(int page, int pageSize, long total, List<T> items) {}
