package com.baseboot.entry.dispatch.path;

import com.baseboot.enums.TaskCodeEnum;
import com.baseboot.enums.VehicleCommandEnum;
import com.baseboot.service.BaseCacheUtil;
import com.baseboot.service.dispatch.helpers.HelpClazz;
import com.baseboot.service.dispatch.helpers.VehicleTaskHelper;
import com.baseboot.service.dispatch.helpers.WorkPathInfoHelper;
import lombok.extern.slf4j.Slf4j;

/**
 * 当前工作路径信息,路径生成时赋值
 */
@Slf4j
public class WorkPathInfo implements HelpClazz<WorkPathInfoHelper> {

    private Integer vehicleId;

    //估计长度（米）,默认发送轨迹长度
    private double trailLen = 100.0;

    //上次轨迹起点,path中的索引,查找最近点时赋值
    private int nearestId = 0;

    //轨迹终点,只有在计算所有轨迹最近点时赋值
    private int trailEndId = 0;

    //路径分段终点，初始化时计算，当到达分段终点为到最终点时重新计算
    private int sectionPathEndId = 0;

    //路径点数，初始化全局路径时赋值
    private int pathPointNum = 0;

    //是否达到终点  ### 只能在当前类中赋值
    private boolean arrive = false;

    //是否达到分段终点，### 只能在当前类中赋值
    private boolean arriveSection = false;

    //是否下发轨迹,true允许下发轨迹   ### 只能在WorkPathInfoHelper赋值
    private boolean sendTrail = false;

    //最近点行驶方向（前进false、后退true）
    private boolean reverse = false;

    //到达分段终点后是否允许运行,等待上报待机编号
    private boolean selectionRun = false;

    private WorkPathInfoHelper helper;

    //当前规划的速度
    private double planCurSpeed;

    private Path path;

    /**
     * 是否允许下发轨迹
     */
    public boolean permitIsSendTrail() {
        return this.isSendTrail();
    }

    /**
     * 收到待机指令，且自身判断已到达分段终点
     */
    public void permitSelectionRun() {
        if (arriveSection) {
            selectionRun = true;
        }

    }

    @Override
    public WorkPathInfoHelper getHelper() {
        return this.helper;
    }

    @Override
    public void initHelper() {
        this.helper = new WorkPathInfoHelper();
        this.helper.initHelpClazz(this);
    }

    public void setHelper(WorkPathInfoHelper helper) {
        this.helper = helper;
    }


    /************************ mapGet and asyncSet **************************/

    public Path getPath() {
        return path;
    }

    public void setPath(Path path) {
        this.path = path;
    }

    public Integer getVehicleId() {
        return vehicleId;
    }

    public void setVehicleId(Integer vehicleId) {
        this.vehicleId = vehicleId;
    }

    public double getTrailLen() {
        return trailLen;
    }

    public void setTrailLen(double trailLen) {
        this.trailLen = trailLen;
    }

    public int getNearestId() {
        return nearestId;
    }

    public void setNearestId(int nearestId) {
        this.nearestId = nearestId;
    }

    public int getTrailEndId() {
        return trailEndId;
    }

    public void setTrailEndId(int trailEndId) {
        this.trailEndId = trailEndId;
    }

    public int getSectionPathEndId() {
        return sectionPathEndId;
    }

    public void setSectionPathEndId(int sectionPathEndId) {
        this.sectionPathEndId = sectionPathEndId;
    }

    public int getPathPointNum() {
        return pathPointNum;
    }

    public void setPathPointNum(int pathPointNum) {
        this.pathPointNum = pathPointNum;
    }

    public boolean isArrive() {
        return arrive;
    }

    public boolean isArriveSection() {
        return arriveSection;
    }

    public void setArriveSection(boolean arriveSection) {
        this.arriveSection = arriveSection;
    }

    public boolean isSendTrail() {
        return sendTrail;
    }

    public boolean isReverse() {
        return reverse;
    }

    public void setReverse(boolean reverse) {
        this.reverse = reverse;
    }

    public void setSendTrail(boolean sendTrail) {
        this.sendTrail = sendTrail;
    }

    public void setArrive(boolean arrive) {
        this.arrive = arrive;
    }

    public double getPlanCurSpeed() {
        return planCurSpeed;
    }

    public void setPlanCurSpeed(double planCurSpeed) {
        this.planCurSpeed = planCurSpeed;
    }

    public boolean isSelectionRun() {
        if (arriveSection) {
            VehicleTaskHelper helper = BaseCacheUtil.getVehicleTask(vehicleId).getHelper();
            return TaskCodeEnum.TASKSTANDBY.equals(helper.getVehicleMonitorManager().getTaskCode()) &&
                    (helper.getCommandStateManager().isContains(10, VehicleCommandEnum.VEHAUTOTRAILFOLLOWING) ||
                            helper.getTaskCodeCommand().isContains(10, TaskCodeEnum.TASKDRIVING));
        }
        return selectionRun;
    }

    public void setSelectionRun(boolean selectionRun) {
        this.selectionRun = selectionRun;
    }
}
