package com.baseboot.common.utils;

import java.io.Serializable;
import java.util.function.Function;

@FunctionalInterface
public interface BFunction<T,R> extends Function<T,R>, Serializable {

}
