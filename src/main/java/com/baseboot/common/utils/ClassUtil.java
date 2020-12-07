package com.baseboot.common.utils;

import com.baseboot.service.calculate.Calculate;
import com.baseboot.service.dispatch.vehicle.commandHandle.MonitorAttrCommand;
import com.baseboot.service.dispatch.vehicle.commandHandle.MonitorAttrHandle;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.net.JarURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

@Slf4j
public class ClassUtil {

    private final static List<Class> CLASSLIST = new ArrayList<>();

    /**
     * 获取接口的所有实现类
     */
    public static List<Class> getAllClassByInterface(Class clazz) {
        List<Class> list = new ArrayList<>();
        // 判断是否是一个接口
        if (clazz.isInterface()) {
            try {
                setGlobalClassList();
                //循环判断路径下的所有类是否实现了指定的接口 并且排除接口类自己
                for (int i = 0; i < CLASSLIST.size(); i++) {
                    if (clazz.isAssignableFrom(CLASSLIST.get(i))) {
                        if (!clazz.equals(CLASSLIST.get(i))) {
                            // 自身并不加进去
                            list.add(CLASSLIST.get(i));
                        }
                    }
                }
            } catch (Exception e) {
                log.error("获取接口的所有实现类:{}", e.getMessage());
            }
        }
        return list;
    }

    /**
     * 获取所有指定注解的类
     */
    public static List<Class> getAllClassByAnnotation(Class<? extends Annotation> annotationClass) {
        List<Class> list = new ArrayList<>();
        try {
            setGlobalClassList();
            for (int i = 0; i < CLASSLIST.size(); i++) {
                Annotation annotation = CLASSLIST.get(i).getAnnotation(annotationClass);
                if (null != annotation) {
                    list.add(CLASSLIST.get(i));
                }
            }
        } catch (Exception e) {
            log.error("获取所有指定注解的类:{}", e.getMessage());
        }
        return list;
    }

    private static void setGlobalClassList() {
        if (CLASSLIST.isEmpty()) {
            synchronized (CLASSLIST) {
                if (CLASSLIST.isEmpty()) {
                    ArrayList<Class> allClass = getAllClass(ClassUtil.class.getPackage().getName());
                    CLASSLIST.addAll(allClass);
                }
            }
        }
    }

    public static void main(String[] args) {
        long runTime = BaseUtil.getRunTime(() -> {
                    List<Class> classes = ClassUtil.getAllClassByInterface(MonitorAttrHandle.class);
                    System.out.println(classes);
                }
        );
        System.out.println(runTime);

        long runTime1 = BaseUtil.getRunTime(() -> {
                    List<Class> classes = ClassUtil.getAllClassByInterface(Calculate.class);
                    System.out.println(classes);
                }
        );
        System.out.println(runTime1);

        long runTime2 = BaseUtil.getRunTime(() -> {
                    List<Class> classes = getAllClassByAnnotation(MonitorAttrCommand.class);
                    System.out.println(classes);
                }
        );
        System.out.println(runTime2);

    }


    /**
     * 从一个指定路径下查找所有的类
     */
    private static ArrayList<Class> getAllClass(String packageName) {
        List<String> classNameList = getClassName(packageName);
        ArrayList<Class> list = new ArrayList<>();
        for (String className : classNameList) {
            try {
                Class<?> aClass = Class.forName(className, false, ClassUtil.class.getClassLoader());
                list.add(aClass);
            } catch (Exception e) {
                log.error("加载类文件异常:{}", className);
            }
        }
        log.info("加载类文件个数 :{}", list.size());
        return list;
    }


    /**
     * 获取某包下所有类
     *
     * @param packageName 包名
     * @return 类的完整名称
     */
    private static List<String> getClassName(String packageName) {
        List<String> fileNames = null;
        ClassLoader loader = Thread.currentThread().getContextClassLoader();
        String packagePath = packageName.replace(".", "/");
        URL url = loader.getResource(packagePath);
        if (url != null) {
            String type = url.getProtocol();
            log.debug("查找文件类型: {}", type);
            if (type.equals("file")) {
                String fileSearchPath = url.getPath();
                fileSearchPath = fileSearchPath.substring(0, fileSearchPath.indexOf("/classes"));
                log.debug("搜索路径:{} ", fileSearchPath);
                fileNames = getClassNameByFile(fileSearchPath);
            } else if (type.equals("jar")) {
                try {
                    JarURLConnection jarURLConnection = (JarURLConnection) url.openConnection();
                    JarFile jarFile = jarURLConnection.getJarFile();
                    fileNames = getClassNameByJar(jarFile, packagePath);
                } catch (IOException e) {
                    log.error("读取jar文件异常!", e);
                }

            } else {
                throw new RuntimeException("不支持文件的文件格式!");
            }
        }
        return fileNames;
    }

    /**
     * 从项目文件获取某包下所有类
     *
     * @param filePath 文件路径
     * @return 类的完整名称
     */
    private static List<String> getClassNameByFile(String filePath) {
        List<String> myClassName = new ArrayList<String>();
        File file = new File(filePath);
        File[] childFiles = file.listFiles();
        for (File childFile : childFiles) {
            if (childFile.isDirectory()) {
                myClassName.addAll(getClassNameByFile(childFile.getPath()));
            } else {
                String childFilePath = childFile.getPath();
                if (childFilePath.endsWith(".class")) {
                    childFilePath = childFilePath.substring(childFilePath.indexOf("\\classes") + 9, childFilePath.lastIndexOf("."));
                    childFilePath = childFilePath.replace("\\", ".");
                    myClassName.add(childFilePath);
                }
            }
        }

        return myClassName;
    }

    /**
     * 从jar获取某包下所有类
     *
     * @return 类的完整名称
     */
    private static List<String> getClassNameByJar(JarFile jarFile, String packagePath) {
        List<String> myClassName = new ArrayList<String>();
        try {
            Enumeration<JarEntry> entrys = jarFile.entries();
            while (entrys.hasMoreElements()) {
                JarEntry jarEntry = entrys.nextElement();
                String entryName = jarEntry.getName();
                if (entryName.endsWith(".class")) {
                    entryName = entryName.replace("/", ".").substring(0, entryName.lastIndexOf("."));
                    myClassName.add(entryName);
                }
            }
        } catch (Exception e) {
            log.error("发生异常:", e);
        }
        return myClassName;
    }

}
