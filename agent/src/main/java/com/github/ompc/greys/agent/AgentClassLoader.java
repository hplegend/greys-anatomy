package com.github.ompc.greys.agent;

import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;

/**
 * 注意classLoader的双亲委派模型
 * Agent ClassLoader
 * Created by vlinux on 2016/11/7.
 */
public class AgentClassLoader extends URLClassLoader {


    /**
     * 传入路径：方便找到jar的位置
     */
    public AgentClassLoader(final String agentJar) throws MalformedURLException {
        super(new URL[]{new URL("file:" + agentJar)});
    }

    /**
     * 重写load逻辑，但是并未重写find。
     * 主要是修改类的加载过程，但是并不修改类的双亲委派模型
     */
    @Override
    protected synchronized Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        final Class<?> loadedClass = findLoadedClass(name);
        if (loadedClass != null) {
            return loadedClass;
        }

        try {
            Class<?> aClass = findClass(name);
            if (resolve) {
                resolveClass(aClass);
            }
            return aClass;
        } catch (Exception e) {
            return super.loadClass(name, resolve);
        }
    }

}
