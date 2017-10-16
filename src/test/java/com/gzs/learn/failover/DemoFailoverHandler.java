package com.gzs.learn.failover;

import java.util.Random;

import com.gzs.learn.failover.FailoverHandler;

public class DemoFailoverHandler implements FailoverHandler {
    @Override
    public Object handle(Object obj) {
        final int param = (Integer) obj;
        final Random r = new Random();
        final int p = r.nextInt(100);
        if (p % 2 == 0) {
            // 模拟随机失败情形
            throw new IllegalArgumentException();
        } else {
            return param;
        }
    }

    @Override
    public void onFail(Object obj) {
        final int originParam = (Integer) obj;
        System.out.println("origin data:" + originParam);
        System.out.println("retry service failed after max times");
    }
}
