package com.github.ompc.greys.core.command;

import com.github.ompc.greys.core.command.annotation.Cmd;
import com.github.ompc.greys.core.command.annotation.IndexArg;
import com.github.ompc.greys.core.decompile.ClassDumpTransformer;
import com.github.ompc.greys.core.decompile.Decompiler;
import com.github.ompc.greys.core.manager.ReflectManager;
import com.github.ompc.greys.core.server.Session;
import com.github.ompc.greys.core.textui.TKv;
import com.github.ompc.greys.core.textui.TTable;
import com.github.ompc.greys.core.util.GaClassUtils;
import com.github.ompc.greys.core.util.LogUtil;
import com.github.ompc.greys.core.util.SimpleDateFormatHolder;
import com.github.ompc.greys.core.util.matcher.ClassMatcher;
import com.github.ompc.greys.core.util.matcher.PatternMatcher;
import com.taobao.text.lang.LangRenderUtil;
import org.apache.commons.lang3.ClassUtils;
import org.slf4j.Logger;

import java.io.File;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.Instrumentation;
import java.lang.management.ClassLoadingMXBean;
import java.lang.management.CompilationMXBean;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryManagerMXBean;
import java.lang.management.OperatingSystemMXBean;
import java.lang.management.RuntimeMXBean;
import java.lang.management.ThreadMXBean;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import static com.github.ompc.greys.core.textui.TTable.Align.LEFT;
import static com.github.ompc.greys.core.textui.TTable.Align.RIGHT;

/**
 * 执行反汇编
 *
 * @author hp.he
 * @date 2020/1/10 16:16
 */

@Cmd(name = "jad", sort = 10, summary = "DeCompile class",
        eg = {
                "  jad java.lang.String\n" +
                        "  jad java.lang.String toString\n" +
                        "  jad --source-only java.lang.String\n" +
                        "  jad -c 39eb305e org/apache/log4j/Logger\n" +
                        "  jad -c 39eb305e -E org\\\\.apache\\\\.*\\\\.StringUtils\n"
        }
)
public class JadCmmand implements Command {

    private static final Logger logger = LogUtil.getLogger();

    private static Pattern pattern = Pattern.compile("(?m)^/\\*\\s*\\*/\\s*$" + System.getProperty("line.separator"));

    /**
     * Jad command 实现：反编译类
     */

    @IndexArg(index = 0, name = "class-pattern", summary = "Path and classname of Pattern Matching")
    private String classPattern;


    private final ReflectManager reflectManager = ReflectManager.Factory.getInstance();

    private final RuntimeMXBean runtimeMXBean = ManagementFactory.getRuntimeMXBean();
    private final ClassLoadingMXBean classLoadingMXBean = ManagementFactory.getClassLoadingMXBean();
    private final CompilationMXBean compilationMXBean = ManagementFactory.getCompilationMXBean();
    private final Collection<GarbageCollectorMXBean> garbageCollectorMXBeans = ManagementFactory.getGarbageCollectorMXBeans();
    private final Collection<MemoryManagerMXBean> memoryManagerMXBeans = ManagementFactory.getMemoryManagerMXBeans();
    private final MemoryMXBean memoryMXBean = ManagementFactory.getMemoryMXBean();
    private final OperatingSystemMXBean operatingSystemMXBean = ManagementFactory.getOperatingSystemMXBean();
    private final ThreadMXBean threadMXBean = ManagementFactory.getThreadMXBean();

    @Override
    public Action getAction() {
        return new SilentAction() {
            @Override
            public void action(Session session, Instrumentation inst, Printer printer) throws Throwable {
                logger.info("jad class pattern={}", classPattern);
                // 执行类匹配
                final ClassMatcher classMatcher = new ClassMatcher(new PatternMatcher(false, classPattern));
                final Collection<Class<?>> matchedClassSet = reflectManager.searchClassWithSubClass(classMatcher);

                if (null == matchedClassSet || matchedClassSet.size() <= 0) {
                    // 未找到匹配的类的log输出
                    printer.print("empty class matcher").finish();
                    return;
                }

                // 执行反汇编
                String sourceCode = doDecompile(session, inst, matchedClassSet);

                // 执行结果输出
                final TTable tTable = new TTable(new TTable.ColumnDefine[]{
                        new TTable.ColumnDefine(RIGHT),
                        new TTable.ColumnDefine(LEFT)
                })
                        .addRow("CATEGORY", "INFO")
                        .padding(1);

                tTable.addRow("RUNTIME", drawRuntimeTable());
                tTable.addRow("CLASS-LOADING", drawClassLoadingTable());
                tTable.addRow("COMPILATION", drawCompilationTable());

                if (!garbageCollectorMXBeans.isEmpty()) {
                    tTable.addRow("GARBAGE-COLLECTORS", drawGarbageCollectorsTable());
                }

                if (!memoryManagerMXBeans.isEmpty()) {
                    tTable.addRow("MEMORY-MANAGERS", drawMemoryManagersTable());
                }

                tTable.addRow("MEMORY", drawMemoryTable());
                tTable.addRow("OPERATING-SYSTEM", drawOperatingSystemMXBeanTable());
                tTable.addRow("THREAD", drawThreadTable());

                //开始输出
                printer.print(LangRenderUtil.render(sourceCode)).finish();
            }
        };
    }


    private String doDecompile(Session session, Instrumentation inst, Collection<Class<?>> matchedClassSet) {
        // only one class
        Class<?> c = matchedClassSet.iterator().next();

        Set<Class<?>> allClasses = new HashSet<Class<?>>() {{
            add(c);
        }};

        ClassDumpTransformer transformer = new ClassDumpTransformer(allClasses);
        retransformClasses(inst, transformer, allClasses);


        Map<Class<?>, File> classFiles = transformer.getDumpResult();
        File classFile = classFiles.get(c);

        String source = Decompiler.decompile(classFile.getAbsolutePath(), null);
        if (source != null) {
            source = pattern.matcher(source).replaceAll("");
        } else {
            source = "unknown";
        }

        return source;

    }


    public static void retransformClasses(Instrumentation inst, ClassFileTransformer transformer, Set<Class<?>> classes) {
        try {
            inst.addTransformer(transformer, true);

            for (Class<?> clazz : classes) {
                try {
                    inst.retransformClasses(clazz);
                } catch (Throwable e) {
                    String errorMsg = "retransformClasses class error, name: " + clazz.getName();
                    if (GaClassUtils.isLambdaClass(clazz) && e instanceof VerifyError) {
                        errorMsg += ", Please ignore lambda class VerifyError: https://github.com/alibaba/arthas/issues/675";
                    }
                    logger.error("jad", errorMsg, e);
                }
            }
        } finally {
            inst.removeTransformer(transformer);
        }
    }


    private String toCol(Collection<String> strings) {
        final StringBuilder colSB = new StringBuilder();
        if (strings.isEmpty()) {
            colSB.append("[]");
        } else {
            for (String str : strings) {
                colSB.append(str).append("\n");
            }
        }
        return colSB.toString();
    }

    private String toCol(String... stringArray) {
        final StringBuilder colSB = new StringBuilder();
        if (null == stringArray
                || stringArray.length == 0) {
            colSB.append("[]");
        } else {
            for (String str : stringArray) {
                colSB.append(str).append("\n");
            }
        }
        return colSB.toString();
    }

    private TKv createKVView() {
        return new TKv(
                new TTable.ColumnDefine(25, false, RIGHT),
                new TTable.ColumnDefine(70, false, LEFT)
        );
    }

    private String drawRuntimeTable() {
        final TKv view = createKVView()
                .add("MACHINE-NAME", runtimeMXBean.getName())
                .add("JVM-START-TIME", SimpleDateFormatHolder.getInstance().format(runtimeMXBean.getStartTime()))
                .add("MANAGEMENT-SPEC-VERSION", runtimeMXBean.getManagementSpecVersion())
                .add("SPEC-NAME", runtimeMXBean.getSpecName())
                .add("SPEC-VENDOR", runtimeMXBean.getSpecVendor())
                .add("SPEC-VERSION", runtimeMXBean.getSpecVersion())
                .add("VM-NAME", runtimeMXBean.getVmName())
                .add("VM-VENDOR", runtimeMXBean.getVmVendor())
                .add("VM-VERSION", runtimeMXBean.getVmVersion())
                .add("INPUT-ARGUMENTS", toCol(runtimeMXBean.getInputArguments()))
                .add("CLASS-PATH", runtimeMXBean.getClassPath())
                .add("BOOT-CLASS-PATH", runtimeMXBean.isBootClassPathSupported() ?
                        runtimeMXBean.getBootClassPath() :
                        "This JVM does not support boot class path.")
                //TODO: add "MODULE-PATH" for JDK 9
                .add("LIBRARY-PATH", runtimeMXBean.getLibraryPath());

        return view.rendering();
    }

    private String drawClassLoadingTable() {
        final TKv view = createKVView()
                .add("LOADED-CLASS-COUNT", classLoadingMXBean.getLoadedClassCount())
                .add("TOTAL-LOADED-CLASS-COUNT", classLoadingMXBean.getTotalLoadedClassCount())
                .add("UNLOADED-CLASS-COUNT", classLoadingMXBean.getUnloadedClassCount())
                .add("IS-VERBOSE", classLoadingMXBean.isVerbose());
        return view.rendering();
    }

    private String drawCompilationTable() {
        final TKv view = createKVView()
                .add("NAME", compilationMXBean.getName());

        if (compilationMXBean.isCompilationTimeMonitoringSupported()) {
            view.add("TOTAL-COMPILE-TIME", compilationMXBean.getTotalCompilationTime() + "(ms)");
        }
        return view.rendering();
    }

    private String drawGarbageCollectorsTable() {
        final TKv view = createKVView();

        for (GarbageCollectorMXBean garbageCollectorMXBean : garbageCollectorMXBeans) {
            view.add(garbageCollectorMXBean.getName() + "\n[count/time]",
                    garbageCollectorMXBean.getCollectionCount() + "/" + garbageCollectorMXBean.getCollectionTime() + "(ms)");
        }

        return view.rendering();
    }

    private String drawMemoryManagersTable() {
        final TKv view = createKVView();

        for (final MemoryManagerMXBean memoryManagerMXBean : memoryManagerMXBeans) {
            if (memoryManagerMXBean.isValid()) {
                final String name = memoryManagerMXBean.isValid()
                        ? memoryManagerMXBean.getName()
                        : memoryManagerMXBean.getName() + "(Invalid)";


                view.add(name, toCol(memoryManagerMXBean.getMemoryPoolNames()));
            }
        }

        return view.rendering();
    }

    private String drawMemoryTable() {
        final TKv view = createKVView();

        view.add("HEAP-MEMORY-USAGE\n[committed/init/max/used]",
                memoryMXBean.getHeapMemoryUsage().getCommitted()
                        + "/" + memoryMXBean.getHeapMemoryUsage().getInit()
                        + "/" + memoryMXBean.getHeapMemoryUsage().getMax()
                        + "/" + memoryMXBean.getHeapMemoryUsage().getUsed()
        );

        view.add("NO-HEAP-MEMORY-USAGE\n[committed/init/max/used]",
                memoryMXBean.getNonHeapMemoryUsage().getCommitted()
                        + "/" + memoryMXBean.getNonHeapMemoryUsage().getInit()
                        + "/" + memoryMXBean.getNonHeapMemoryUsage().getMax()
                        + "/" + memoryMXBean.getNonHeapMemoryUsage().getUsed()
        );

        view.add("PENDING-FINALIZE-COUNT", memoryMXBean.getObjectPendingFinalizationCount());
        return view.rendering();
    }


    private String drawOperatingSystemMXBeanTable() {
        final TKv view = createKVView();
        view
                .add("OS", operatingSystemMXBean.getName())
                .add("ARCH", operatingSystemMXBean.getArch())
                .add("PROCESSORS-COUNT", operatingSystemMXBean.getAvailableProcessors())
                .add("LOAD-AVERAGE", operatingSystemMXBean.getSystemLoadAverage())
                .add("VERSION", operatingSystemMXBean.getVersion());
        return view.rendering();
    }

    private String drawThreadTable() {
        final TKv view = createKVView();

        view
                .add("COUNT", threadMXBean.getThreadCount())
                .add("DAEMON-COUNT", threadMXBean.getDaemonThreadCount())
                .add("LIVE-COUNT", threadMXBean.getPeakThreadCount())
                .add("STARTED-COUNT", threadMXBean.getTotalStartedThreadCount())
        ;
        return view.rendering();
    }
}

