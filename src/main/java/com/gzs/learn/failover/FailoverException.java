package com.gzs.learn.failover;

/**
 * 异常类
 * 
 * @author guanzhisong
 * @date 2017年6月14日
 */
public class FailoverException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public FailoverException(String message) {
        super(message);
    }

    public FailoverException(Throwable cause) {
        super(cause);
    }

    public FailoverException(String message, Throwable cause) {
        super(message, cause);
    }

    public FailoverException(String message, Throwable cause, boolean enableSuppression,
            boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }
}
