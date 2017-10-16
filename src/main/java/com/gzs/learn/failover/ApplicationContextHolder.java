package com.gzs.learn.failover;

import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Component;

/**
 * spring applicationContext 工具类
 *
 * @author guanzhisong
 * @date 2017年2月14日
 */
@Component
public class ApplicationContextHolder implements ApplicationContextAware {

    private static ApplicationContext ctx;

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        ApplicationContextHolder.ctx = applicationContext;
    }

    public static <T> T getBean(Class<T> cls) {
        return ctx == null ? null : ctx.getBean(cls);
    }

    @SuppressWarnings("unchecked")
    public static <T> T getBean(String beanName) {
        return ctx == null ? null : (T) ctx.getBean(beanName);
    }
}
