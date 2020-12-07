package com.baseboot.common.exception;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class BaseException extends  RuntimeException {

    private static final long serialVersionUID = -8981002709409574689L;

    public BaseException(String message){
        super(message);
    }

    public BaseException(String message, Throwable e){
        super(message,e);
    }
}
