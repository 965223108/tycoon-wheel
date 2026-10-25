package com.tycoon.api.exception;

import java.io.Serializable;

/**
 * 跨服务业务异常。Dubbo 会把异常信息带回调用方。
 */
public class RpcBusinessException extends RuntimeException implements Serializable {

    private static final long serialVersionUID = 1L;

    private final String code;

    public RpcBusinessException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }

    public static RpcBusinessException of(String code, String message) {
        return new RpcBusinessException(code, message);
    }
}
