package com.github.ompc.greys.agent;

import java.lang.instrument.Instrumentation;
import java.util.jar.JarFile;

/**
 * 代理启动类
 * Created by oldmanpushcart@gmail.com on 15/5/19.
 */
public class AgentLauncher {

    // 全局持有classloader用于隔离greys实现
    private static volatile ClassLoader greysClassLoader;


    /**
     * 两个agent的入口函数： loadAgent方法调用的就是这里的agentMain。对于Premain，就是在系统启动的时候，直接用javaagentlib指定时调用的main。
     * 两者实现的效果一样，只是进入的时机不一样。agentMain是jvm启动后，通过jvm提供的工具主动连接；premain则是在jvm启动的时候连接。
     */
    public static void premain(String args, Instrumentation inst) {
        System.out.println("premain链接上jvm");
        main(args, inst);
    }

    public static void agentmain(String args, Instrumentation inst) {
        System.out.println("agent链接上jvm");
        main(args, inst);
    }


    /**
     * 重置greys的classloader<br/>
     * 让下次再次启动时有机会重新加载
     */
    public synchronized static void resetGreysClassLoader() {
        greysClassLoader = null;
    }

    private static ClassLoader loadOrDefineClassLoader(String agentJar) throws Throwable {

        final ClassLoader classLoader;

        // 如果已经被启动则返回之前启动的classloader
        if (null != greysClassLoader) {
            classLoader = greysClassLoader;
        }

        // 如果未启动则重新加载
        else {
            // 这里是core的jar包url，而不是agent的jar url
            classLoader = new AgentClassLoader(agentJar);

            // 获取各种Hook
            // 这里的Hook实际上就是对字节码的修改visitor（AMS的methodVisitor）
            final Class<?> adviceWeaverClass = classLoader.loadClass("com.github.ompc.greys.core.advisor.AdviceWeaver");

            // 初始化全局间谍
            Spy.initForAgentLauncher(
                    classLoader,

                    // 通过加载的类获取方法签名signature
                    adviceWeaverClass.getMethod("methodOnBegin",
                            int.class,
                            ClassLoader.class,
                            String.class,
                            String.class,
                            String.class,
                            Object.class,
                            Object[].class),
                    adviceWeaverClass.getMethod("methodOnReturnEnd",
                            Object.class,
                            int.class),
                    adviceWeaverClass.getMethod("methodOnThrowingEnd",
                            Throwable.class,
                            int.class),
                    adviceWeaverClass.getMethod("methodOnInvokeBeforeTracing",
                            int.class,
                            Integer.class,
                            String.class,
                            String.class,
                            String.class),
                    adviceWeaverClass.getMethod("methodOnInvokeAfterTracing",
                            int.class,
                            Integer.class,
                            String.class,
                            String.class,
                            String.class),
                    adviceWeaverClass.getMethod("methodOnInvokeThrowTracing",
                            int.class,
                            Integer.class,
                            String.class,
                            String.class,
                            String.class,
                            String.class),
                    AgentLauncher.class.getMethod("resetGreysClassLoader")
            );
        }

        return greysClassLoader = classLoader;
    }

    private static synchronized void main(final String args, final Instrumentation inst) {
        try {

            // 传递的args参数分两个部分:agentJar路径和agentArgs
            // 分别是Agent的JAR包路径和期望传递到服务端的参数
            final int index = args.indexOf(';');
            final String agentJar = args.substring(0, index);
            final String agentArgs = args.substring(index, args.length());

            // 将Spy添加到BootstrapClassLoader
            inst.appendToBootstrapClassLoaderSearch(
                    new JarFile(AgentLauncher.class.getProtectionDomain().getCodeSource().getLocation().getFile())
            );

            // 构造自定义的类加载器，尽量减少Greys对现有工程的侵蚀
            final ClassLoader agentLoader = loadOrDefineClassLoader(agentJar);

            // Configure类定义
            final Class<?> classOfConfigure = agentLoader.loadClass("com.github.ompc.greys.core.Configure");

            // GaServer类定义
            final Class<?> classOfGaServer = agentLoader.loadClass("com.github.ompc.greys.core.server.GaServer");

            // 反序列化成Configure类实例
            // 构造实例
            final Object objectOfConfigure = classOfConfigure.getMethod("toConfigure", String.class)
                    .invoke(null, agentArgs);

            // JavaPid
            final int javaPid = (Integer) classOfConfigure.getMethod("getJavaPid").invoke(objectOfConfigure);

            // 获取GaServer单例
            // 这里获取单例过程中有很多的操作，比如说把jvm所有的类都托管给反射管理器
            final Object objectOfGaServer = classOfGaServer
                    .getMethod("getInstance", int.class, Instrumentation.class)
                    .invoke(null, javaPid, inst);

            // gaServer.isBind()
            // 开始执行绑定
            final boolean isBind = (Boolean) classOfGaServer.getMethod("isBind").invoke(objectOfGaServer);

            if (!isBind) {
                try {
                    System.out.println("bind Success");
                    classOfGaServer.getMethod("bind", classOfConfigure).invoke(objectOfGaServer, objectOfConfigure);
                } catch (Throwable t) {
                    classOfGaServer.getMethod("destroy").invoke(objectOfGaServer);
                    throw t;
                }

            }

        } catch (Throwable t) {
            t.printStackTrace();
        }

    }

}
