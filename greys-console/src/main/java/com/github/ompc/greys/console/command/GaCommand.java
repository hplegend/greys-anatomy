package com.github.ompc.greys.console.command;

import com.github.ompc.greys.console.command.impl.VersionGaCommand;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import picocli.CommandLine;

import java.io.PrintWriter;
import java.io.Writer;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Set;

/**
 * 命令
 */
public interface GaCommand {

    /**
     * 命令执行
     *
     * @param writer 执行结果终端输出
     * @throws Throwable 执行发生异常
     */
    void execute(PrintWriter writer) throws Throwable;




    class GaCommandNotFoundException extends Exception {
        GaCommandNotFoundException(final String name) {
            super(String.format("command: %s not found.", name));
        }
    }

    class GaCommandInitializationException extends Exception {
        GaCommandInitializationException(final String name,
                                         final Throwable cause) {
            super(
                    String.format("command: %s initial error: %s", name, cause.getMessage()),
                    cause
            );
        }
    }

    class GaCommands {

        private final LinkedHashMap<String, Class<? extends GaCommand>> gaCommandMap
                = new LinkedHashMap<String, Class<? extends GaCommand>>();

        GaCommands put(Class<? extends GaCommand> classOfGaCommand) {
            final CommandLine.Command command = classOfGaCommand.getAnnotation(CommandLine.Command.class);
            if (null != command
                    && StringUtils.isNotBlank(command.name())) {
                gaCommandMap.put(command.name(), classOfGaCommand);
            }
            return this;
        }

        /**
         * 列出所有命令名称
         *
         * @return 所有命令名称
         */
        public Set<String> names() {
            return gaCommandMap.keySet();
        }

        /**
         * 根据参数解析并构造好Ga命令
         *
         * @param nameWithArgumentArray 带命令名的参数数组,第一个为命令名
         * @return 初始化好的Ga命令实例
         * @throws GaCommandNotFoundException       命令不存在
         * @throws GaCommandInitializationException 命令初始化失败
         */
        public GaCommand parseGaCommand(String[] nameWithArgumentArray) throws GaCommandNotFoundException, GaCommandInitializationException {

            final String name = nameWithArgumentArray[0];
            if (!gaCommandMap.containsKey(name)) {
                throw new GaCommandNotFoundException(name);
            }

            try {
                final GaCommand gaCommand = gaCommandMap.get(name).getConstructor().newInstance();
                new CommandLine(gaCommand)
                        .setStopAtUnmatched(true)
                        .parse(ArrayUtils.subarray(
                                nameWithArgumentArray,
                                1,
                                nameWithArgumentArray.length
                        ));
                return gaCommand;
            } catch (Throwable cause) {
                throw new GaCommandInitializationException(name, cause);
            }

        }

        private GaCommands(Class<? extends GaCommand>... classOfGaCommandArray) {
            if (null != classOfGaCommandArray) {
                for (final Class<? extends GaCommand> classOfGaCommand : classOfGaCommandArray) {
                    put(classOfGaCommand);
                }
            }
        }

        public static final GaCommands instance = new GaCommands(
                VersionGaCommand.class
        );

    }

}