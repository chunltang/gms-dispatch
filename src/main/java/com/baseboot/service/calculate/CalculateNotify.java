package com.baseboot.service.calculate;

import com.baseboot.entry.dispatch.monitor.Location;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * 计算通知信息
 */
@Slf4j
@CalculateClass
@Component
public class CalculateNotify implements Calculate {

    @Override
    public CalculateResult calculateEndPoint(Integer vehicleId, Set<Location> locations) {
        CalculateResult result = new CalculateResult();
        result.setType(getCalculateType());
        result.setSourceId(vehicleId);
        result.setTargetId(vehicleId);
        return result;
    }

    @Override
    public CalculateTypeEnum getCalculateType() {
        return CalculateTypeEnum.VEHICLE_NOTIFY_STOP_LIMITING;
    }
}
