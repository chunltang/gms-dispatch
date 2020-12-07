package com.baseboot.common.utils;

import lombok.extern.slf4j.Slf4j;

import java.io.*;
import java.nio.channels.FileChannel;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

@Slf4j
public class IOUtil {

    /**
     * @param file    为要压缩的文件或目录
     * @param zos     为压缩后的zip文件
     * @param dirPath 为文件压缩前去掉前置目录字符串
     */
    public static void fileCompress(File file, ZipOutputStream zos, String dirPath) throws Exception {
        File[] files = file.listFiles();
        ZipEntry zipEntry;
        FileInputStream fileInputStream;
        for (File f : files) {
            if (f.isDirectory()) {
                fileCompress(f, zos, dirPath);
                continue;
            }
            zipEntry = new ZipEntry(f.getPath().replace(dirPath + File.separator, ""));
            zos.putNextEntry(zipEntry);
            fileInputStream = new FileInputStream(f);
            IOUtil.ioCopy(fileInputStream, zos);
            fileInputStream.close();
        }
    }

    /**
     * @param files 指定文件对象压缩
     */
    public static void fileCompress(List<File> files, ZipOutputStream zos, String dirPath) throws Exception {
        ZipEntry zipEntry;
        FileInputStream fileInputStream;
        for (File f : files) {
            zipEntry = new ZipEntry(f.getPath().replace(dirPath + File.separator, ""));
            zos.putNextEntry(zipEntry);
            fileInputStream = new FileInputStream(f);
            IOUtil.ioCopy(fileInputStream, zos);
            fileInputStream.close();
        }
    }

    /**
     * 查找所有文件对象
     */
    public static void listFiles(File file, List<File> files, String... excludeDir) {
        if (file.isDirectory()) {
            if (Arrays.asList(excludeDir).contains(file.getName())) {
                return;
            }
            File[] fs = file.listFiles();
            for (File f : fs) {
                listFiles(f, files, excludeDir);
            }
            return;
        }
        files.add(file);
    }

    /**
     * 删除目录
     */
    public static void delDir(File file) {
        List<File> files = new ArrayList<>();
        listFiles(file, files);
        for (File f : files) {
            f.delete();
        }
        file.delete();
    }

    /**
     * zip解压
     *
     * @param path 解压路径，以斜杠结尾
     */
    public static void unZip(InputStream is, String path) {
        createDir(path, true);
        ZipEntry entry;
        FileOutputStream fos = null;
        ZipInputStream zis = new ZipInputStream(is, Charset.forName("GBK"));
        try {
            while ((entry = zis.getNextEntry()) != null) {
                String name = entry.getName();
                File target = new File(path + name);
                if (entry.isDirectory()) {
                    if (!target.exists()) {
                        target.mkdirs();
                    }
                    continue;
                }
                fos = new FileOutputStream(target);
                ioCopy(zis, fos);
                fos.close();
            }
            zis.close();
            is.close();
        } catch (IOException e) {
            log.error("zip文件解压异常", e);
        }
    }


    /**
     * 创建目录
     *
     * @param delFlag 存在是否删除，true未删除重建
     */
    public static void createDir(String dirPath, boolean delFlag) {
        File file = new File(dirPath);
        if (!file.exists() || !file.isDirectory()) {
            file.mkdirs();
        } else if (delFlag) {
            file.delete();
            file.mkdirs();
        }
    }

    /**
     * 创建文件
     */
    public static File createFile(String filePath) {
        File file = new File(filePath);
        if (!file.exists() || file.isDirectory()) {
            try {
                file.createNewFile();
            } catch (IOException e) {
                log.error("创建文件失败", e);
            }
        }
        return file;
    }

    /**
     * 创建多级文件
     *
     * @param filePath 最后以文件名结尾
     */
    public static File createMultiFile(String filePath) {
        File file = new File(filePath);
        if (!file.getParentFile().exists()) {
            file.getParentFile().mkdirs();
        }
        return createFile(filePath);
    }


    /**
     * 流数据拷贝
     */
    public static void ioCopy(InputStream in, OutputStream os) {
        int len = 0;
        byte[] bytes = new byte[1024];
        try {
            while ((len = in.read(bytes)) != -1) {
                os.write(bytes, 0, len);
            }
        } catch (Exception e) {
            log.error("流数据拷贝异常", e);
        }
    }


    /**
     * 文件拷贝
     *
     * @param file 源文件
     * @param path 路径,加file的名字获得复制后的文件
     */
    public static void copy(File file, String path) {
        File dir = new File(path);
        if (!dir.exists()) {
            dir.mkdirs();
        }
        String name = path + File.separator + file.getName();
        try (FileOutputStream target = new FileOutputStream(new File(name));
             FileInputStream source = new FileInputStream(file)) {
            FileChannel inChannel = source.getChannel();
            FileChannel outChannel = target.getChannel();
            inChannel.transferTo(0, inChannel.size(), outChannel);
        } catch (IOException e) {
            log.error("文件复制异常", e);
        }
    }

    /**
     * 写入文件
     */
    public static void writeToFile(String fileName, String str, boolean isAppend) {
        createMultiFile(fileName);
        try (FileWriter fileWriter = new FileWriter(fileName, isAppend)) {
            fileWriter.write(str);
        } catch (Exception e) {
            log.error("写入文件异常", e);
        }
    }

    /**
     * 获取文件行数
     */
    public static long getFileLines(String filePath) {
        try {
            return Files.lines(Paths.get(filePath)).count();
        } catch (IOException e) {
            log.error("获取文件行数失败，文件不存在");
            return 0;
        }
    }

    /**
     * 获取文件行数，这个速度快,占用内存大
     */
    public static int getFileNumbers(String filePath) {
        try (LineNumberReader lnr = new LineNumberReader(new FileReader(filePath))) {
            lnr.skip(Integer.MAX_VALUE);
            return lnr.getLineNumber();
        } catch (Exception e) {
            log.error("获取文件行数失败，文件不存在");
        }
        return 0;
    }

    /**
     * 获取文件的后指定行数
     */
    public static String getFileBackLineString(String filePath, long lines) {
        try (LineNumberReader lnr = new LineNumberReader(new FileReader(filePath))) {
            long nums = getFileLines(filePath);
            StringBuilder sb = new StringBuilder();
            String str = null;
            while (nums - lines > 0) {
                nums--;
                lnr.readLine();
            }
            while ((str = lnr.readLine()) != null) {
                sb.append(str).append("\r\n");
            }
            return sb.toString();
        } catch (Exception e) {
            log.error("截取文件异常", e);
        }
        return "";
    }

    /**
     * 获取文件的所有行内容
     */
    public static List<String> getFileLineList(File file) {
        List<String> strings = new ArrayList<>();
        try (LineNumberReader lnr = new LineNumberReader(new FileReader(file))) {
            String line;
            while ((line = lnr.readLine()) != null) {
                strings.add(line);
            }
        } catch (Exception e) {
            log.error("截取文件异常", e);
        }
        return strings;
    }

    /**
     * 读取流数据，转为string
     */
    public static String inputStreamToString(InputStream is) {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader bfr = new BufferedReader(new InputStreamReader(is))) {
            String line;
            while ((line = bfr.readLine()) != null) {
                sb.append(line);
            }
            return sb.toString();
        } catch (Exception e) {
            log.error("解析流数据错误!", e);
        }
        return null;
    }
}
