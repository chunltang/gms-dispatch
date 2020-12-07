package com.baseboot.entry.global;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.BeanProperty;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.deser.ContextualDeserializer;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.lang.reflect.Method;

@Data
@Slf4j
public class IEnumDeserializer extends JsonDeserializer<Enum> implements ContextualDeserializer {

    private Class clazz;

    @Override
    public  Enum deserialize(JsonParser p, DeserializationContext ctxt) throws IOException, JsonProcessingException {
        Class<?> rawClass = clazz;
        String source = p.getText();
        Method method= null;
        try {
            method = rawClass.getMethod("name",null);
            for (Object enumObj : rawClass.getEnumConstants()) {
                IEnum e=(IEnum)enumObj;
                String name=(String)method.invoke(enumObj);
                if (source.equals(String.valueOf(e.getValue()))||source.equals(name)) {
                    return (Enum)e;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        log.error("没有匹配枚举类型!");
        return null;
    }

    public JsonDeserializer createContextual(DeserializationContext ctx, BeanProperty property) throws JsonMappingException {
        Class rawCls = ctx.getContextualType().getRawClass();
        IEnumDeserializer clone = new IEnumDeserializer();
        clone.setClazz(rawCls);
        return clone;
    }
}
