package com.baseboot.entry.global;

import java.util.LinkedList;

/**
 * 先进先出的队列
 */
public class FiFoQueue<T> {

    private LinkedList<T> list = new LinkedList<>();
    private final Object lock = new Object();

    public void put(T t) { //加入数据
        synchronized (lock) {
            list.addFirst(t);
        }
    }

    public T get() { //取出先加入的数据
        synchronized (lock) {
            if (list.size() > 0) {
                return list.removeLast();
            }
            return null;
        }
    }

    public void clear() {
        synchronized (lock) {
            list.clear();
        }
    }

    public boolean isEmpty() {
        return list.isEmpty();
    }

    public int getSize() {
        return list.size();
    }
}
