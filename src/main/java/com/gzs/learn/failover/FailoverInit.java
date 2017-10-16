package com.gzs.learn.failover;

import java.util.concurrent.TimeUnit;

import javax.annotation.PostConstruct;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class FailoverInit {
    private static final int THREAD_SLEEP_INTERVAL = 20;
    @Autowired
    private FailoverService failoverService;

    @PostConstruct
    public void initFailover() {
        failoverService.initHandlers();
        new FailoverThread(failoverService).start();
        // 注册jvm关闭钩子(kill -9 pid 并不会调用shutdown hook)
        // Runtime.getRuntime().addShutdownHook(new Thread(() -> {
        // failoverService.dumpHandlers();
        // }));
    }

    class FailoverThread extends Thread {
        private final FailoverService failoverService;

        public FailoverThread(FailoverService service) {
            failoverService = service;
        }

        @Override
        public void run() {
            while (true) {
                try {
                    failoverService.doRetry();
                    TimeUnit.MILLISECONDS.sleep(THREAD_SLEEP_INTERVAL);
                } catch (final InterruptedException e) {
                    log.warn("retry thread interrupt");
                }
            }
        }
    }
}
