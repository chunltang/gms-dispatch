package com.baseboot.entry.dispatch.path;

import com.baseboot.common.service.DelayedService;
import com.baseboot.common.service.RedisService;
import com.baseboot.common.utils.BaseUtil;
import com.baseboot.common.utils.IOUtil;
import com.baseboot.entry.dispatch.TimerCommand;
import com.baseboot.entry.global.BaseConstant;
import com.baseboot.entry.global.Export;
import com.baseboot.entry.global.MongoKeyPool;
import com.baseboot.entry.global.RedisKeyPool;
import com.baseboot.service.BaseCacheUtil;
import com.baseboot.service.dispatch.input.InputCache;
import com.baseboot.service.dispatch.task.MongoStore;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
@Slf4j
public class GlobalPath extends Path {

    @JsonIgnore
    private WorkPathInfo workPathInfo;


    /**
     * 生成全局路径
     */
    public static GlobalPath createGlobalPath(byte[] bytes) {
        GlobalPath globalPath = new GlobalPath();
        globalPath.setId(BaseUtil.getCurTime());
        globalPath.parseBytes2Path(bytes);
        globalPath.initWorkPathInfo();
        //DelayedService.addTask(() -> GlobalPath.toFile(globalPath, ""), 500).withDesc("全局路径写入txt文件");
        //DelayedService.addTask(() -> GlobalPath.writeToExcel(globalPath, ""), 1000).withDesc("全局路径写入excel文件");
        MongoStore.addToMongo(MongoKeyPool.VEHICLE_GLOBAL_PATH_PREFIX + globalPath.getVehicleId(), (Path) globalPath);
        return globalPath;
    }

    /**
     * 生成拟合全局路径，替换原数据
     */
    public static void createFittingGlobalPath(byte[] bytes) {
        GlobalPath globalPath = new GlobalPath();
        globalPath.parseBytes2Path(bytes);
        String id = TimerCommand.getTemporaryKey(globalPath.getVehicleId(), TimerCommand.FITTING_GLOBAL_PATH_COMMAND);
        if (globalPath.getStatus() != 0) {
            log.error("【{}】全局路径拟合失败", globalPath.getVehicleId());
            BaseUtil.execTaskAtOnce(id);
            return;
        }
        if (!BaseUtil.isExistLikeTask(id)) {
            log.error("【{}】拟合全局路径反馈过期", globalPath.getVehicleId());
            return;
        }
        GlobalPath path = BaseCacheUtil.getGlobalPath(globalPath.getVehicleId());
        if (null != path) {
            log.debug("【{}】生成拟合全局路径,原点数:{},更新点数:{}", path.getVehicleId(), path.getVertexNum(), globalPath.getVertexNum());
            path.setLen(globalPath.getLen());
            path.setVertexNum(globalPath.getVertexNum());
            path.setVertexs(globalPath.getVertexs());
            path.getWorkPathInfo().setPathPointNum(globalPath.getVertexNum());
            BaseUtil.cancelDelayTask(id);
            MongoStore.addToMongo(MongoKeyPool.VEHICLE_GLOBAL_PATH_PREFIX + globalPath.getVehicleId(), (Path) globalPath);
            RedisService.asyncSet(BaseConstant.MONITOR_DB, RedisKeyPool.VAP_PATH_PREFIX + globalPath.getVehicleId(), globalPath.toDataString());
            //DelayedService.addTask(() -> GlobalPath.toFile(globalPath, "fitting"), 500).withDesc("全局拟合路径写入txt文件");
            //DelayedService.addTask(() -> GlobalPath.writeToExcel(globalPath, "fitting"), 1000).withDesc("全局拟合路径写入excel文件");
        }
    }

    /**
     * 写入文件
     */
    private static void toFile(GlobalPath globalPath, String suffix) {
        List<Vertex> vertexs = globalPath.getVertexs();
        if (BaseUtil.CollectionNotNull(vertexs)) {
            StringBuilder sb = new StringBuilder();
            sb.append("起点位置:").append(InputCache.getStartPoint(globalPath.getVehicleId())).append("\r\n");
            sb.append("终点位置:").append(InputCache.getEndPoint(globalPath.getVehicleId())).append("\r\n");
            calculateMaxSpeed(vertexs);
            for (Vertex vertex : vertexs) {
                sb.append(vertex.getX()).append(",").append(vertex.getY()).append(",").append(vertex.getZ()).append(",").append(vertex.getMaxSpeed()).append("\r\n");
            }
            String dir = BaseUtil.getAppPath() + "temp/" + globalPath.getVehicleId() + "-txt";
            IOUtil.delDir(new File(dir));
            BaseUtil.writeToFile(dir + File.separator + "globalPath-" + suffix + ".txt", sb.toString());
        }
    }

    /**
     * 计算最大速度
     */
    private static void calculateMaxSpeed(List<Vertex> vertexs) {
        double expectSpeed = 0;
        double maxSpeed = 2.777;
        double acce = 0.2;
        for (int i = 0; i < vertexs.size(); i++) {
            expectSpeed = Math.sqrt(acce / (vertexs.get(i).getCurvature())); // 期望速度
            if (expectSpeed > maxSpeed) {
                vertexs.get(i).setMaxSpeed(maxSpeed);
            } else {
                vertexs.get(i).setMaxSpeed(expectSpeed);
            }
            if (vertexs.get(i).getCurvature() > 0.05) {
                vertexs.get(i).setMaxSpeed(5 / 3.6);
            } else if (vertexs.get(i).getCurvature() > 0.04) {
                vertexs.get(i).setMaxSpeed(6 / 3.6);
            } else if (vertexs.get(i).getCurvature() > 0.03) {
                vertexs.get(i).setMaxSpeed(7 / 3.6);
            }
            if (vertexs.get(i).isReverse()) {
                vertexs.get(i).setMaxSpeed(5 / 3.6);
            }
        }
    }

    /**
     * 初始化工作路径信息
     */
    private void initWorkPathInfo() {
        this.workPathInfo = new WorkPathInfo();
        workPathInfo.setVehicleId(this.getVehicleId());
        workPathInfo.setNearestId(0);
        workPathInfo.setPathPointNum(this.getVertexNum());
        workPathInfo.setTrailEndId(this.getVertexNum());
        workPathInfo.setPath(this);
        workPathInfo.initHelper();
        BaseCacheUtil.addWorkPathInfo(workPathInfo);
    }

    /**
     * 写入excel
     */
    private static void writeToExcel(GlobalPath globalPath, String suffix) {
        Export export = new Export();
        String dir = BaseUtil.getAppPath() + "temp/" + globalPath.getVehicleId() + "-excel";
        IOUtil.delDir(new File(dir));
        String fileName = "globalPath-" + suffix;
        export.setFileName(fileName);
        export.setType(Export.Type.xlsx);
        export.setSheetName("全局路径");
        globalPath.writeToExcel(export, dir);
    }
}
