package com.baseboot.common.service;

import com.baseboot.common.utils.BFunction;
import com.baseboot.common.utils.BaseUtil;
import com.baseboot.common.utils.LambdaUtil;
import com.mongodb.MongoClient;
import com.mongodb.MongoClientOptions;
import com.mongodb.MongoCredential;
import com.mongodb.ServerAddress;
import com.mongodb.client.*;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.util.JSON;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.bson.types.ObjectId;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
public class MongoService {

    private final static MongoClient client;
    private final static String dbName = "admin";
    private final static String userName = "gms";
    private final static String password = "123456";

    public final static String GTE = "$gte";
    public final static String LT = "$lte";
    public final static String SET = "$set";
    public final static String IN = "$in";//包含
    public final static String NIN = "$nin";//不包含
    public final static String NOT = "$not";//不是
    public final static String ALL = "$all";//都包含
    public final static String OR = "$or";
    public final static String NE = "$ne";//不等于
    public final static String RE = "$regex";//正则匹配
    public final static String EX = "$exists";//判断字段是否存在
    public final static String MOD = "$mod";//取模运算
    public final static String SIZE = "$size";//匹配数组长度

    static {
        String serverHost = System.getProperty("MONGO_SERVER_HOST");
        client = createMongoClient(serverHost, userName, password, dbName);
    }

    /**
     * 创建连接
     */
    public static MongoClient createMongoClient(String host, String userName, String password, String dbName) {
        MongoClientOptions build = MongoClientOptions.builder().build();
        MongoCredential credential = MongoCredential.createScramSha1Credential(userName, dbName, password.toCharArray());
        return new MongoClient(new ServerAddress(host, 27017), credential, build);
    }


    /**
     * 添加json数据
     */
    public static void insertOne(String tableName, Map<String, Object> save) {
        MongoDatabase db = client.getDatabase(dbName);
        MongoCollection<Document> collection = db.getCollection(tableName);
        collection.insertOne(new Document(save));
    }


    /**
     * 批量新增
     */
    public static <T> void insertMany(String tableName, List<T> save) {
        MongoDatabase db = client.getDatabase(dbName);
        MongoCollection<Document> collection = db.getCollection(tableName);
        collection.insertMany(save.stream().map(BaseUtil::toJson).map(Document::parse).collect(Collectors.toList()));
    }

    /**
     * 数据过期设置
     */
    public static <T> void insertManyExpire(String tableName, List<T> save) {
        MongoDatabase db = client.getDatabase(dbName);
        MongoCollection<Document> collection = db.getCollection(tableName);
        collection.insertMany(save.stream().map(BaseUtil::toJson).map(Document::parse).peek(doc -> doc.put("expireAt", new Date())).collect(Collectors.toList()));
    }

    /**
     * 查询
     * skip=0,limit=0时，查全部
     */
    public static <T> List<T> queryList(String tableName, Document query, Class<T> tClass, int skip, int limit) {
        MongoDatabase db = client.getDatabase(dbName);
        MongoCollection<Document> collection = db.getCollection(tableName);
        FindIterable<Document> documents = collection.find(query).skip(skip).limit(limit).batchSize(100000);
        MongoCursor<Document> iterator = documents.iterator();
        List<T> result = new ArrayList<>();
        while (iterator.hasNext()) {
            result.add(BaseUtil.toObj(JSON.serialize(iterator.next()), tClass));
        }
        return result;
    }

    /**
     * 根据objectId过滤
     *
     * @param condition $gte,$lte
     */
    public static <T> List<T> queryListById(String tableName, Class<T> tClass, long timeStamp, String condition) {
        MongoDatabase db = client.getDatabase(dbName);
        MongoCollection<Document> collection = db.getCollection(tableName);
        Document query = new Document().append("_id", new Document(condition, MongoService.getObjectIdByTime(timeStamp)));
        FindIterable<Document> documents = collection.find(query);
        MongoCursor<Document> iterator = documents.iterator();
        List<T> result = new ArrayList<>();
        while (iterator.hasNext()) {
            result.add(BaseUtil.toObj(JSON.serialize(iterator.next()), tClass));
        }
        return result;
    }

    /**
     * 根据objectId过滤,查询指定字段
     *
     * @param condition $gte,$lte
     */
    public static <T,R> List<Document> queryFieldListById(String tableName, long timeStamp, String condition, BFunction<T, R>... functions) {
        MongoDatabase db = client.getDatabase(dbName);
        MongoCollection<Document> collection = db.getCollection(tableName);
        Document query = new Document().append("_id", new Document(condition, getObjectIdByTime(timeStamp)));
        Document field = new Document();
        for (BFunction<T, R> function : functions) {
            field.put(LambdaUtil.getName(function), 1);
        }
        field.put("_id", 0);

        FindIterable<Document> documents = collection.find(query).projection(field).sort(new Document().append("_id",1));
        MongoCursor<Document> iterator = documents.iterator();
        List<Document> result = new ArrayList<>();
        while (iterator.hasNext()) {
            result.add(iterator.next());
        }
        return result;
    }

    /**
     * 获取最后插入的数据
     */
    public static <T> T queryLast(String tableName, Class<T> tClass) {
        MongoDatabase db = client.getDatabase(dbName);
        MongoCollection<Document> collection = db.getCollection(tableName);
        FindIterable<Document> documents = collection.find(new Document()).sort(new Document("_id", -1)).limit(1);
        MongoCursor<Document> iterator = documents.iterator();
        while (iterator.hasNext()) {
            return BaseUtil.toObj(JSON.serialize(iterator.next()), tClass);
        }
        return null;
    }

    /**
     * 跟新
     */
    public static void updateOne(String tableName, Document filter, Map<String, Object> update) {
        try {
            Map<String, Object> updateParam = new HashMap<>();
            updateParam.put(SET, update);
            MongoDatabase db = client.getDatabase(dbName);
            MongoCollection<Document> collection = db.getCollection(tableName);
            collection.findOneAndUpdate(filter, new Document(updateParam));
        }catch (Exception e){
            log.error("mongo update error",e);
        }
    }

    /**
     * 删除
     */
    public static void removeMany(String tableName, Map<String, Object> filter) {
        MongoDatabase db = client.getDatabase(dbName);
        MongoCollection<Document> collection = db.getCollection(tableName);
        collection.deleteMany(new Document(filter));
    }

    /**
     * 判断表是否存在
     */
    public static boolean collectionExists(String tableName) {
        MongoDatabase db = client.getDatabase(dbName);
        MongoCursor<String> mongoCursor = db.listCollectionNames().iterator();
        String name;
        do {
            if (!mongoCursor.hasNext()) {
                return false;
            }
            name = mongoCursor.next();
        } while (!name.equals(tableName));
        return true;
    }

    /**
     * 获取所有指定模糊的表
     */
    public static List<String> getLikeTables(String tableLikeName) {
        MongoDatabase db = client.getDatabase(dbName);
        MongoCursor<String> mongoCursor = db.listCollectionNames().iterator();
        List<String> tableNames = new ArrayList<>();
        String name;
        while (mongoCursor.hasNext()) {
            name = mongoCursor.next();
            if (name.contains(tableLikeName)) {
                tableNames.add(name);
            }
        }
        return tableNames;
    }

    /**
     * 创建表
     */
    public static void createTable(String tableName) {
        if (!collectionExists(tableName)) {
            MongoDatabase db = client.getDatabase(dbName);
            db.createCollection(tableName);
        }
    }

    /**
     * 创建索引
     */
    public static void createIndex(String tableName, List<String> fields) {
        MongoDatabase db = client.getDatabase(dbName);
        MongoCollection<Document> collection = db.getCollection(tableName);
        IndexOptions options = new IndexOptions();
        options.background(true);
        Document doc = new Document();
        fields.forEach((val) -> {
            {
                doc.put(val, 1);
            }
        });
        collection.createIndex(doc, options);
    }

    /**
     * 创建过期索引
     *
     * @param field      过期字段
     * @param expireTime 过期时间
     */
    public static void createExpireIndex(String tableName, String field, long expireTime, TimeUnit unit) {
        MongoDatabase db = client.getDatabase(dbName);
        MongoCollection<Document> collection = db.getCollection(tableName);
        Document doc = new Document();
        doc.put(field, 1);
        IndexOptions options = new IndexOptions();
        options.expireAfter(expireTime, unit);
        collection.createIndex(doc, options);
    }

    /**
     * 修改过期索引的过期时间
     *
     * @param expireTime 秒
     *                   MongoService.updateExpireIndex("monitor_10006","expireAt",86500L);
     */
    public static void updateExpireIndex(String tableName, String field, long expireTime) {
        MongoDatabase db = client.getDatabase(dbName);
        Document doc = new Document();
        doc.put("collMod", tableName);
        doc.put("index", new Document().append("keyPattern", new Document().append(field, 1)).append("expireAfterSeconds", expireTime));
        db.runCommand(doc);
    }

    /**
     * 列出所有索引
     */
    public static List<String> listIndex(String tableName) {
        MongoDatabase db = client.getDatabase(dbName);
        MongoCollection<Document> collection = db.getCollection(tableName);
        ListIndexesIterable<Document> indexes = collection.listIndexes();
        MongoCursor<Document> iterator = indexes.iterator();
        List<String> indexNames = new ArrayList<>();
        while (iterator.hasNext()) {
            indexNames.add(iterator.next().toJson());
        }
        return indexNames;
    }

    /**
     * 生成objectId,时间戳为毫秒
     */
    public static String getObjectId() {
        long millis = System.currentTimeMillis();
        String toHex = Long.toString(millis, 16);//将时间戳转为16进制
        return toHex + ObjectId.get().toString().substring(8);
    }

    /**
     * 指定时间生成objectId
     */
    public static String getObjectIdByTime(long timeStamp) {
        String toHex = Long.toString(timeStamp, 16);
        return toHex + ObjectId.get().toString().substring(8);
    }

    /**
     * 获取getObjectId方法生成id的时间戳
     */
    public static long getTimeStamp(String objectId) {
        return Long.parseLong(objectId.substring(0, 11), 16);
    }
}
