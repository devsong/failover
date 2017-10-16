package com.gzs.learn.failover;

/**
 * 重试逻辑
 *
 * @author guanzhisong
 * @date 2017年6月14日
 * @param <T>
 * @param <R>
 */
public interface FailoverHandler {
    // 处理方法
    Object handle(Object obj);

    // 处理失败
    void onFail(Object obj);
}
