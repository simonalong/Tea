package com.simon.tea.util;

import com.simon.tea.Print;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;
import lombok.experimental.UtilityClass;

import static com.simon.tea.Print.*;

/**
 * @author zhouzhenyong
 * @since 2018/7/15 下午5:31
 */
public class PackageScanner {
    private String basePackage;
    private ClassLoader cl;
    private TeaCup teaCup = TeaCup.getInstance();

    PackageScanner(String basePackage) {
        this.basePackage = basePackage;
        this.cl = getClass().getClassLoader();
        showLn("");
    }

    List<String> scan() {
        List<String> classes = new ArrayList<>();
        try {
            classes = doScan(basePackage, classes);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return classes;
    }

    Class<?> loadClass(String classFullName) throws ClassNotFoundException {
        return teaCup.loadClass(classFullName);
    }

    /**
     * Actually perform the scanning procedure.
     *
     * @param nameList A list to contain the result.
     * @return A list of fully qualified names.
     */
    private List<String> doScan(String basePackage, List<String> nameList) throws IOException {
        String splashPath = basePackage.replaceAll("\\.", "/");
        URL url = cl.getResource(splashPath);
        assert url != null;
        teaCup.loadPath(url.toString());
        this.urlClassLoader = getUrlClassLoader(url);

        List<String> names;
        String filePath = StringUtil.getJarPath(url);
        if (isJarFile(filePath)) {
            names = readFromJarFile(filePath, splashPath);
        } else {
            names = readFromDirectory(filePath);
        }
//
//        try {
//            showLn("record = "+this.urlClassLoader.loadClass("me.zzp.am.Record").getSimpleName());
//        } catch (ClassNotFoundException e) {
//            e.printStackTrace();
//        }

        for (String name : names) {
            if (isClassFile(name)) {
                showLn("classFile = "+name);
                nameList.add(toFullyQualifiedName(name, basePackage));
            } else {
                showLn(" name = "+name);
                doScan(basePackage + "." + name, nameList);
            }
        }

        return nameList;
    }

    /**
     * Convert short class name to fully qualified name. e.g., String -> java.lang.String
     */
    private String toFullyQualifiedName(String shortName, String basePackage) {
        StringBuilder sb = new StringBuilder(basePackage);
        sb.append('.');
        sb.append(StringUtil.trimExtension(basePackage, shortName));

        return sb.toString();
    }

    private List<String> readFromJarFile(String jarPath, String splashedPackageName) throws IOException {
        showLn("从JAR包中读取类: " + jarPath);

        JarInputStream jarIn = new JarInputStream(new FileInputStream(jarPath));
        JarEntry entry = jarIn.getNextJarEntry();

        List<String> nameList = new ArrayList<>();
        while (null != entry) {
            String name = entry.getName();
            if (name.startsWith(splashedPackageName) && isClassFile(name)) {
                nameList.add(name);
            }

            entry = jarIn.getNextJarEntry();
        }

        return nameList;
    }

    private List<String> readFromDirectory(String path) {
        File file = new File(path);
        String[] names = file.list();

        if (null == names) {
            return null;
        }

        return Arrays.asList(names);
    }

    private boolean isClassFile(String name) {
        return name.endsWith(".class");
    }

    private boolean isJarFile(String name) {
        return name.endsWith(".jar");
    }

    public URLClassLoader getUrlClassLoader(URL url) {
        try {
            showLn("teaPath = " + url.toString());
            String rootPath = StringUtil.getRootPath(url);
            showLn("rootPath = " + rootPath);
            String activeMapPath = rootPath + "active-map-2.3.5.jar";

            URL activeMapUrl = new File(activeMapPath).toURI().toURL();

            return new URLClassLoader(new URL[]{url, activeMapUrl});
        } catch (MalformedURLException e) {
            e.printStackTrace();
        }
        return null;
    }
}