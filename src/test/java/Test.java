import com.baseboot.common.service.ForkJoinService;
import com.baseboot.entry.dispatch.monitor.vehicle.Monitor;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class Test {

    /*public static void main(String[] args) {
        System.setProperty("MONGO_SERVER_HOST", "10.22.22.106");
        boolean user = MongoService.collectionExists("user1");
        Map<String, Object> params = new HashMap<>();
        params.put("_id", 100213213212120L);
        params.put("name", "小明");
        params.put("age", 12.121232);
        Map<String, Object> paramsInner = new HashMap<>();
        paramsInner.put("class", "雪");
        paramsInner.put("num", 101);
        params.put("inner",paramsInner);

        Object[] arr=new Object[2];
        arr[0]=12323;
        arr[1]="wqe去请求请求群群";
        params.put("arr",arr);

        MongoService.insertOne("monitor", JSONObject.toJSONString(params));

        Map<String, Object> queryMap = new HashMap<>();
        queryMap.put("_id", 12321321321312L);
        List<String> strings = MongoService.queryList("monitor", JSONObject.toJSONString(queryMap));
        System.out.println(strings);

        *//*Map<String, Object> paramsFilter = new HashMap<>(params);
        params.put("class", "雪");
        params.put("num", 101);
        params.put("inner",paramsInner);
        MongoService.updateOne("monitor", JSONObject.toJSONString(paramsFilter), JSONObject.toJSONString(paramsInner));*//*
    }*/

    /*public static void main(String[] args) {
        System.setProperty("MONGO_SERVER_HOST", "10.22.22.106");
        Map<String, Object> params = new HashMap<>();
        params.put("inner.class", "雪");
        List<String> strings = MongoService.queryList("monitor", JSONObject.toJSONString(params));
        System.out.println(strings);
    }*/

    /**
     * #asyncSet
     */
    /*public static void main(String[] args) {
        System.setProperty("MONGO_SERVER_HOST", "10.22.22.106");
        Map<String, Object> params = new HashMap<>();
        params.put("arr", 12323);
        List<String> strings = MongoService.queryList("monitor", params);
        System.out.println(strings);

        Map<String, Object> update = new HashMap<>();
        update.put("name", "324");

        MongoService.updateOne("monitor",params,update);
    }*/

    /*public static void main(String[] args) {
        System.setProperty("MONGO_SERVER_HOST", "10.22.22.106");
        Map<String, Object> params = new HashMap<>();
        params.put("arr", 12323);

        MongoService.removeMany("monitor",params);
    }*/
    public static void main(String[] args) throws InterruptedException {


        List<Integer> integers = new ArrayList<>();
        int sum = 0;
        for (int i = 0; i < 10000000; i++) {
            integers.add(i);
            sum += i;
        }

        List<Integer> list = new CopyOnWriteArrayList<>();

        ForkJoinService<Integer> joinService = new ForkJoinService<Integer>(integers, 0, integers.size(), (dataList) -> {
            System.out.println(Thread.currentThread().getName() + ",size=" + dataList.size());
            list.addAll(dataList);
        });

        ForkJoinService.submit(joinService);
        ForkJoinService.closePool(20000);

        int sum1 = 0;
        for (Integer integer : list) {
            sum1 += integer;
        }
        System.out.println(sum+" "+sum1);


    }
}
