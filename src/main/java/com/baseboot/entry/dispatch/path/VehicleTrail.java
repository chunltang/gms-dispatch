package com.baseboot.entry.dispatch.path;

import com.baseboot.common.service.DelayedService;
import com.baseboot.common.service.MongoService;
import com.baseboot.common.utils.BaseUtil;
import com.baseboot.common.utils.DateUtil;
import com.baseboot.entry.global.Export;
import com.baseboot.entry.global.MongoKeyPool;
import com.baseboot.service.BaseCacheUtil;
import com.baseboot.service.dispatch.task.MongoStore;
import com.mongodb.Mongo;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.time.LocalDateTime;
import java.util.List;

@Slf4j
public class VehicleTrail extends Path {

    public static VehicleTrail createVehicleTrail(byte[] bytes) {
        VehicleTrail vehicleTrail = new VehicleTrail();
        vehicleTrail.parseBytes2Path(bytes);
        GlobalPath globalPath = BaseCacheUtil.getGlobalPath(vehicleTrail.getVehicleId());
        if (null == globalPath) {
            log.error("【{}】全局路径不存在，不能创建轨迹!", vehicleTrail.getVehicleId());
            return null;
        }
        vehicleTrail.setId(globalPath.getId());
        VehicleTrail oldTrail = BaseCacheUtil.getVehicleTrail(vehicleTrail.getVehicleId());
        vehicleTrail.setNo(null != oldTrail ? oldTrail.getNo() + 1 : 0);
        //MongoStore.addToMongo(MongoKeyPool.VEHICLE_TRAIL_PREFIX + vehicleTrail.getVehicleId(), vehicleTrail);
        //DelayedService.addTask(() -> VehicleTrail.toFile(vehicleTrail), 1000).withDesc("轨迹写入txt文件");
        //DelayedService.addTask(() -> VehicleTrail.writeToExcel(vehicleTrail), 1000).withDesc("轨迹写入excel文件");
        return vehicleTrail;
    }

    /**
     * 写入文件
     */
    private static void toFile(VehicleTrail vehicleTrail) {
        List<Vertex> vertexs = vehicleTrail.getVertexs();
        if (BaseUtil.CollectionNotNull(vertexs)) {
            WorkPathInfo info = BaseCacheUtil.getWorkPathInfo(vehicleTrail.getVehicleId());
            StringBuilder sb = new StringBuilder();
            StringBuilder indexSb = new StringBuilder();
            if (null != info) {
                sb.append(info.getNearestId()).append(",").append(info.getTrailEndId()).append(",").append(info.getPlanCurSpeed()).append("\r\n");
                indexSb.append(info.getNearestId()).append(",").append(info.getTrailEndId()).append(",").append(info.getPlanCurSpeed()).append("\r\n");
            }

            for (Vertex vertex : vertexs) {
                sb.append(vertex.getNo()).append(" | ")
                        .append(vertex.getX()).append(" | ")
                        .append(vertex.getY()).append(" | ")
                        .append(vertex.getZ()).append(" | ")
                        .append(vertex.getType()).append(" | ")
                        .append(vertex.getDirection()).append(" | ")
                        .append(vertex.getSlope()).append(" | ")
                        .append(vertex.getCurvature()).append(" | ")
                        .append(vertex.getLeftDistance()).append(" | ")
                        .append(vertex.getRightDistance()).append(" | ")
                        .append(vertex.getMaxSpeed()).append(" | ")
                        .append(vertex.getSpeed()).append(" | ")
                        .append(vertex.getS()).append(" | ")
                        .append(vertex.isReverse()).append("\r\n");
            }
            String appPath = BaseUtil.getAppPath();
            BaseUtil.writeToFile(appPath + "temp/" + vehicleTrail.getVehicleId() + "-txt" + File.separator + DateUtil.formatFullTime(LocalDateTime.now()) + "-vehicleTrail.txt", sb.toString());
            if (indexSb.length() > 0) {
                BaseUtil.writeAppendToFile(appPath + "temp/" + vehicleTrail.getVehicleId() + "-txt" + File.separator + "index.txt", indexSb.toString());
            }
        }
    }


    /**
     * 写入excel
     */
    private static void writeToExcel(VehicleTrail vehicleTrail) {
        Export export = new Export();
        String dir = BaseUtil.getAppPath() + "temp/" + vehicleTrail.getVehicleId() + "-excel";
        String fileName = DateUtil.formatFullTime(LocalDateTime.now()) + "-vehicleTrail";
        export.setFileName(fileName);
        export.setType(Export.Type.xlsx);
        export.setSheetName("轨迹");
        vehicleTrail.writeToExcel(export, dir);
    }
}
