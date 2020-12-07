package com.baseboot.entry.global;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

/**
 * 固定数据大小队列
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class LimitQueue<T> extends LinkedList<T> {

    private Integer limit;

    @JsonCreator
    public LimitQueue(Integer limit) {
        this.limit = limit;
    }

    @Override
    public boolean add(T element) {
        super.add(element);
        while (size() > limit) {
            synchronized (this) {
                super.remove();//删除第一个元素
            }
        }
        return true;
    }


    /**
     * 判断最新的num个元素中是否包含指定元素
     */
    public boolean limitContains(int numLimit, T element) {
        List<T> subList;
        if (this.size() >= numLimit) {
            subList = this.subList(this.size() - numLimit, this.size());
        } else {
            subList = this;
        }
        return subList.contains(element);
    }

    /**
     * 判断最新的num个元素中是否包含指定元素自定次数
     */
    public boolean limitContainsNum(int numLimit, T element, int nums) {
        List<T> subList;
        if (this.size() >= numLimit) {
            subList = this.subList(this.size() - numLimit, this.size());
        } else {
            subList = this;
        }
        int num = Collections.frequency(subList, element);
        return num >= nums;
    }
}
