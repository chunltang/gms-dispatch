package com.baseboot.entry.global;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public abstract class AbstractEventPublisher implements EventPublisher {

    private Map<String, Listener> listenerMap = new ConcurrentHashMap<>();

    /**
     * 添加监听器
     */
    @Override
    public void addListener(String name, Listener listener) {
        if (null != listener) {
            this.listenerMap.put(name, listener);
        }
    }

    public Collection<Listener> getListeners() {
        return listenerMap.values();
    }

    @Override
    public void removeListener(Listener listener) {
        String name = "";
        for (Map.Entry<String, Listener> entry : listenerMap.entrySet()) {
            if (entry.getValue().equals(listener)) {
                name = entry.getKey();
            }
        }
        listenerMap.remove(name);
    }
}
