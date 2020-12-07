package com.baseboot.entry.dispatch.path;

import com.baseboot.common.utils.*;
import com.baseboot.entry.global.Export;
import com.baseboot.entry.global.MongoKeyPool;
import com.baseboot.service.dispatch.task.MongoStore;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.time.LocalDateTime;
import java.util.*;

@Data
@Slf4j
public class Path {
    private long id;
    private int endian;
    private int len;
    private int no;
    private int status;
    private int vehicleId;
    private int vertexNum;
    List<Vertex> vertexs;
    private long endTime;//结束时间

    /**
     * @param beginPos 读取数据开始位置
     * @param readLen  生成数据的行数
     * @param lens     每个行的字节数
     */
    public byte[][] convert2ByteArr(byte[] bytes, int beginPos, int readLen, int... lens) {
        int index = 0;
        int lenPos = 0;
        byte[][] b2 = new byte[readLen][];
        for (int i = 0; i < readLen; i++) {
            if (lenPos >= lens.length) {
                lenPos = 0;
            }
            byte[] copy = Arrays.copyOfRange(bytes, beginPos, beginPos + lens[lenPos] > bytes.length ? bytes.length : beginPos + lens[lenPos]);
            b2[index] = copy;
            beginPos += lens[lenPos];
            index++;
            lenPos++;
        }
        return b2;
    }

    public Path parseBytes2Path(byte[] bytes) {
        if (bytes.length <= 6) {
            log.error("解析全局路径,数据异常!");
            log.debug("车辆[{}]路劲数据采用json解析,message={}", vehicleId, new String(bytes));
            return null;
        }
        byte[][] byteArr = convert2ByteArr(bytes, 0, 6, 4);
        int index = 0;
        this.setEndian(ByteUtil.bytes2IntBigEndian(byteArr[index++]));
        this.setLen(ByteUtil.bytes2IntBigEndian(byteArr[index++]));
        this.setNo(ByteUtil.bytes2IntBigEndian(byteArr[index++]));
        this.setStatus(ByteUtil.bytes2IntBigEndian(byteArr[index++]));
        this.setVehicleId(ByteUtil.bytes2IntBigEndian(byteArr[index++]));
        this.setVertexNum(ByteUtil.bytes2IntBigEndian(byteArr[index++]));
        int vertexNum = this.getVertexNum();
        int status = this.getStatus();
        if (status == 0) {
            index = 0;
            byte[][] byteArr1 = convert2ByteArr(bytes, 24, vertexNum * 13, 8, 8, 8, 4, 4, 4, 4, 8, 8, 8, 8, 8, 1);
            Vertex vertex;
            ArrayList<Vertex> vertexs = new ArrayList<>(vertexNum);
            this.setVertexs(vertexs);
            for (int i = 0; i < vertexNum; i++) {
                vertex = new Vertex();
                vertex.setX(ByteUtil.bytes2DoubleBigEndian(byteArr1[index++]));
                vertex.setY(ByteUtil.bytes2DoubleBigEndian(byteArr1[index++]));
                vertex.setZ(ByteUtil.bytes2DoubleBigEndian(byteArr1[index++]));
                vertex.setType(ByteUtil.bytes2IntBigEndian(byteArr1[index++]));
                vertex.setDirection(ByteUtil.bytes2FloatBigEndian(byteArr1[index++]));
                vertex.setSlope(ByteUtil.bytes2FloatBigEndian(byteArr1[index++]));
                vertex.setCurvature(ByteUtil.bytes2FloatBigEndian(byteArr1[index++]));
                vertex.setLeftDistance(ByteUtil.bytes2DoubleBigEndian(byteArr1[index++]));
                vertex.setRightDistance(ByteUtil.bytes2DoubleBigEndian(byteArr1[index++]));
                vertex.setMaxSpeed(ByteUtil.bytes2DoubleBigEndian(byteArr1[index++]));
                vertex.setSpeed(ByteUtil.bytes2DoubleBigEndian(byteArr1[index++]));
                vertex.setS(ByteUtil.bytes2DoubleBigEndian(byteArr1[index++]));
                byte bool = byteArr1[index++][0];
                vertex.setReverse(bool != 0x00);
                vertexs.add(vertex);
            }
        }
        BaseUtil.execTaskAtOnce(MongoKeyPool.MONGO_STORE_REFRESH);
        return this;
    }

    public byte[] toByteArray() {
        ByteBuf buf = Unpooled.buffer(24);
        byte[] bytes = new byte[this.len];
        buf.writeInt(this.endian);
        buf.writeInt(this.len);
        buf.writeInt(this.no);
        buf.writeInt(this.status);
        buf.writeInt(this.vehicleId);
        buf.writeInt(this.vertexNum);
        byte[] array = buf.array();
        ByteUtil.byteRangeCopy(bytes, array, 0);
        byte[] vertexs = vertexsToByteArray();
        ByteUtil.byteRangeCopy(bytes, vertexs, 24);
        return bytes;
    }

    private byte[] vertexsToByteArray() {
        byte[] bytes = new byte[this.len - 24];
        if (BaseUtil.CollectionNotNull(this.vertexs)) {
            int index = 0;
            for (Vertex vertex : this.vertexs) {
                byte[] array = vertex.toByteArray();
                ByteUtil.byteRangeCopy(bytes, array, index);
                index += array.length;
            }
        }
        return bytes;
    }


    @Override
    public String toString() {
        String sb = endian + "," +
                len + "," +
                no + "," +
                status + "," +
                vehicleId + "," +
                vertexNum;
        return sb;
    }


    public String toDataString() {
        StringBuilder sb = new StringBuilder();
        sb.append(no).append(",");
        sb.append(vehicleId).append(",");
        sb.append(status).append(",");
        sb.append(vertexNum).append(",");
        if (null != vertexs) {
            for (Vertex vertex : vertexs) {
                sb.append(vertex.getNo()).append(",");
                sb.append(vertex.getX()).append(",");
                sb.append(vertex.getY()).append(",");
                sb.append(vertex.getZ()).append(",");
                sb.append(vertex.getType()).append(",");
                sb.append(vertex.getDirection()).append(",");
                sb.append(vertex.getSlope()).append(",");
                sb.append(vertex.getCurvature()).append(",");
                sb.append(vertex.getLeftDistance()).append(",");
                sb.append(vertex.getRightDistance()).append(",");
                sb.append(vertex.getMaxSpeed()).append(",");
                sb.append(vertex.getSpeed()).append(",");
                sb.append(vertex.getS()).append(",");
                sb.append(vertex.isReverse() ? 1 : 0).append(",");
            }
        }
        return sb.toString();
    }

    /**
     * 写入excel
     */
    public void writeToExcel(Export export, String dir) {
        if (!BaseUtil.CollectionNotNull(this.getVertexs())) {
            return;
        }
        Map<String, String> headers = new HashMap<>();
        headers.put("x", "x坐标");
        headers.put("y", "y坐标");
        headers.put("z", "z坐标");
        headers.put("type", "类型");
        headers.put("direction", "横摆角");
        headers.put("slope", "坡度");
        headers.put("curvature", "曲率");
        headers.put("leftDistance", "左边距");
        headers.put("rightDistance", "右边距");
        headers.put("maxSpeed", "最大速度");
        headers.put("speed", "当前速度");
        headers.put("s", "离轨迹起点距离");
        headers.put("reverse", "是否倒车");
        export.setHeaders(headers);
        List<Map<String, Object>> dataMap = new ArrayList<>();
        for (Vertex vertex : this.getVertexs()) {
            Map<String, Object> dataObj = new HashMap<>();
            dataObj.put("x", vertex.getX());
            dataObj.put("y", vertex.getY());
            dataObj.put("z", vertex.getZ());
            dataObj.put("type", vertex.getType());
            dataObj.put("direction", vertex.getDirection());
            dataObj.put("slope", vertex.getSlope());
            dataObj.put("curvature", vertex.getCurvature());
            dataObj.put("leftDistance", vertex.getLeftDistance());
            dataObj.put("rightDistance", vertex.getRightDistance());
            dataObj.put("maxSpeed", vertex.getMaxSpeed());
            dataObj.put("speed", vertex.getSpeed());
            dataObj.put("s", vertex.getS());
            dataObj.put("reverse", vertex.isReverse());
            dataMap.add(dataObj);
        }
        export.setExportData(dataMap);
        ExportUtil.export(export, dir);
    }
}
