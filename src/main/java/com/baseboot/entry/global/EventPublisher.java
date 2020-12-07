package com.baseboot.entry.global;

/**
 * 事件发布者
 * */
public interface EventPublisher {

    void addListener(String name,Listener listener);

    void removeListener(Listener listener);

    void eventPublisher(EventType eventType,Object value);
}
