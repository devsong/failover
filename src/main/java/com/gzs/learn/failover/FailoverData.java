package com.gzs.learn.failover;

import java.io.Serializable;

import com.alibaba.fastjson.JSON;

import lombok.Data;

@Data
public class FailoverData implements Serializable {
    private static final long serialVersionUID = 1L;
    private int key;
    private String json;
    private Class<?> cls;

    public FailoverData() {

    }

    public FailoverData(int key, Object data) {
        this.key = key;
        json = JSON.toJSONString(data);
        cls = data.getClass();
    }
}
