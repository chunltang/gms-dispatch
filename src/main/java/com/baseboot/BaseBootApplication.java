package com.baseboot;

import com.baseboot.common.service.RedisService;
import com.baseboot.common.utils.BaseUtil;
import com.baseboot.common.utils.DateUtil;
import com.baseboot.common.utils.PropertyUtil;
import com.baseboot.entry.global.BaseConstant;
import com.baseboot.entry.global.RedisKeyPool;
import com.baseboot.entry.global.Version;
import com.baseboot.interfaces.send.CommSend;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.util.Scanner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


@Slf4j
@SpringBootApplication
@EnableScheduling
@Order(value = Ordered.HIGHEST_PRECEDENCE)
public class BaseBootApplication implements CommandLineRunner {

    public static void main(String[] args) {
        System.setProperty(RedisKeyPool.SERVER_VERSION, "1.1.15");
        System.out.println("===============================================");
        System.out.println("          base system start:" + System.getProperty(RedisKeyPool.SERVER_VERSION) + "              ");
        System.out.println("===============================================");

       /* System.setProperty("REDIS_SERVER_HOST","172.18.0.3");
        System.setProperty("RABBITMQ_SERVER_HOST","172.18.0.2");
        System.setProperty("MONGO_SERVER_HOST","172.18.0.13");*/

        System.setProperty("REDIS_SERVER_HOST", "192.168.43.96");
        System.setProperty("RABBITMQ_SERVER_HOST", "192.168.43.96");
        System.setProperty("MONGO_SERVER_HOST","192.168.43.96");

        /*System.setProperty("REDIS_SERVER_HOST", "192.168.1.121");
        System.setProperty("RABBITMQ_SERVER_HOST", "192.168.1.121");*/

        /*System.setProperty("REDIS_SERVER_HOST","10.22.22.106");
        System.setProperty("RABBITMQ_SERVER_HOST","10.22.22.106");
        System.setProperty("MONGO_SERVER_HOST","10.22.22.106");*/


        /*System.setProperty("REDIS_SERVER_HOST","192.168.2.100");
        System.setProperty("RABBITMQ_SERVER_HOST","192.168.2.100");*/

        /*System.setProperty("REDIS_SERVER_HOST","172.20.10.2");
        System.setProperty("RABBITMQ_SERVER_HOST","172.20.10.2");*/

        System.setProperty("SERVER_PWD", "123456");
        SpringApplication.run(BaseBootApplication.class, args);
    }

    @Override
    public void run(String... args) throws Exception {
        createVersionInfo();
        new Thread(this::consoleThread).start();
    }

    /**
     * 创建版本信息
     */
    private void createVersionInfo() {
        Version version = PropertyUtil.propertiesToObj("properties/version.properties", Version.class);
        if (null != version) {
            version.setServiceVersion(System.getProperty(RedisKeyPool.SERVER_VERSION));
            version.setStartTime(DateUtil.formatLongToString(BaseUtil.getCurTime()));
            RedisService.asyncSet(BaseConstant.KEEP_DB, RedisKeyPool.SERVER_VERSION, BaseUtil.toJson(version));
        }
    }

    /**
     * 接收控制台命令
     */
    private void consoleThread() {
        Scanner scanner = new Scanner(System.in);
        while (true) {
            try {
                String str = scanner.nextLine();
                if(Pattern.matches("v[0-9]",str)){
                    Pattern compile = Pattern.compile("v([0-9])");
                    Matcher matcher = compile.matcher(str);
                    if(matcher.find()){
                        String group = matcher.group(1);
                        CommSend.sendAnyCommand(Integer.valueOf(group));
                    }
                }
            }catch (Exception e){
                log.error("控制台命令异常",e);
            }
        }
    }
}
