package com.gzs.learn.failover;

import java.util.Random;
import java.util.concurrent.TimeUnit;

import org.junit.Before;
import org.junit.Test;
import org.springframework.context.ApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

import com.gzs.learn.failover.FailoverHandler;
import com.gzs.learn.failover.FailoverService;

import redis.clients.jedis.JedisSentinelPool;

public class DemoTest {
    ApplicationContext ctx = null;

    @Before
    public void Before() {
        ctx = new ClassPathXmlApplicationContext("spring-test.xml");
    }

    @Test
    public void testHandle() throws InterruptedException {
        final FailoverService service = ctx.getBean(FailoverService.class);
        final JedisSentinelPool pool = ctx.getBean(JedisSentinelPool.class);
        System.out.println(pool);
        final FailoverHandler handler = new DemoFailoverHandler();
        final Random random = new Random();
        for (int i = 0; i < 100; i++) {
            final int param = random.nextInt(1000);
            // System.out.println(param);
            service.doFailOver(handler, param);
        }
        TimeUnit.SECONDS.sleep(10);
    }
    
    @Test
    public void simpleSplitProxyClass(){
        String className = "com.tmg.jikexiu.gj.service.handler.NotifyOrderHandler$$EnhancerBySpringCGLIB$$bc3121e5";
        System.out.println(className.substring(0, className.indexOf('$')));
    }
}
