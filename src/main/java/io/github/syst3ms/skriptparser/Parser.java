package io.github.syst3ms.skriptparser;

import io.github.syst3ms.skriptparser.log.LogEntry;
import io.github.syst3ms.skriptparser.log.LogType;
import io.github.syst3ms.skriptparser.parsing.ScriptLoader;
import io.github.syst3ms.skriptparser.registration.DefaultRegistration;
import io.github.syst3ms.skriptparser.registration.SkriptAddon;
import io.github.syst3ms.skriptparser.registration.SkriptRegistration;
import io.github.syst3ms.skriptparser.util.ConsoleColors;
import org.jetbrains.annotations.Nullable;
import org.reflections.Reflections;
import org.reflections.scanners.Scanners;
import org.reflections.util.ConfigurationBuilder;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.List;
import java.util.jar.JarFile;

public class Parser {

    public static final String CONSOLE_FORMAT = "[%tT] %s: %s%n";

    private static SkriptRegistration registration;
    private static List<LogEntry> logs;

    public static void main(String[] args) {
        boolean debug = false;
        boolean tipsEnabled = true;
        String scriptName = "";
        String[] programArgs = new String[0];
        if (args.length == 0) {
            System.err.println("You need to provide a script name!");
            System.exit(1);
        } else {
            int j = 0;
            for (int i = 0; i < args.length; i++) {
                String s = args[i];
                if (s.equalsIgnoreCase("--debug")) {
                    debug = true;
                } else if (s.equalsIgnoreCase("--no-tips") || s.equalsIgnoreCase("--nt")) {
                    tipsEnabled = false;
                } else {
                    j = i;
                    break;
                }
            }
            scriptName = args[j];
            programArgs = Arrays.copyOfRange(args, j + 1, args.length);
        }
        init(new String[0], new String[0], programArgs, true, null, null);
        run(scriptName, debug, tipsEnabled);
    }

    /**
     * Starts the parser.
     * @param mainPackages packages inside which all subpackages containing classes to load may be present. Doesn't need
     *                     to contain Skript's own main packages.
     * @param subPackages the subpackages inside which classes to load may be present. Doesn't need to contain Skript's
     *                    own subpackages.
     * @param programArgs any other program arguments (typically from the command line)
     * @param standalone whether the parser tries to load addons (standalone) or not (library)
     */
    public static void init(String[] mainPackages, String[] subPackages, String[] programArgs, boolean standalone) {
        init(mainPackages, subPackages, programArgs, standalone, null, null);
    }

    /**
     * Starts the parser.
     * @param mainPackages packages inside which all subpackages containing classes to load may be present. Doesn't need
     *                     to contain Skript's own main packages.
     * @param subPackages the subpackages inside which classes to load may be present. Doesn't need to contain Skript's
     *                    own subpackages.
     * @param programArgs any other program arguments (typically from the command line)
     * @param standalone whether the parser tries to load addons (standalone) or not (library)
     * @param parserPath the main path to the parser, will be used to get the addons folder. If null, the path will be inferred from the parser's location.
     */
    public static void init(Object[] mainPackages, String[] subPackages, String[] programArgs, boolean standalone, @Nullable Path parserPath, @Nullable ClassLoader classLoader) {
        Skript skript = new Skript(programArgs);
        registration = new SkriptRegistration(skript);
        DefaultRegistration.register();

        List<Object> allPackages = new ArrayList<>();

        // Add default subpackages
        List<String> defaultSubPackages = Arrays.asList("expressions", "effects", "event", "lang", "sections", "structures", "tags");
        for (String subPackage : defaultSubPackages) {
            allPackages.add("io.github.syst3ms.skriptparser." + subPackage);
        }

        // Add user-defined subpackages
        for (Object mainPackage : mainPackages) {
            if (!(mainPackage instanceof String)) {
                allPackages.add(mainPackage);
                continue;
            }
            for (String subPackage : subPackages) {
                allPackages.add(mainPackage + "." + subPackage);
            }
        }

        try {
            if (classLoader == null) {
                classLoader = Parser.class.getClassLoader();
            }

            // Load all classes in the specified packages
            ClassLoader finalClassLoader = classLoader;
            new Reflections(allPackages, Scanners.SubTypes.filterResultsBy(s -> true))
                    .getSubTypesOf(Object.class)
                    .forEach(clazz -> {
                        try {
                            Class.forName(clazz.getName(), true, finalClassLoader);
                        } catch (ClassNotFoundException e) {
                            e.printStackTrace();
                        }
                    });

            // Load addons if standalone mode is enabled
            if (standalone) {
                if (parserPath == null) {
                    parserPath = Paths.get(Parser.class.getProtectionDomain().getCodeSource().getLocation().toURI()).getParent();
                }
                Path addonFolderPath = parserPath.getParent().resolve("addons");
                if (Files.isDirectory(addonFolderPath)) {
                    Files.walk(addonFolderPath)
                        .filter(Files::isRegularFile)
                        .filter(filePath -> filePath.toString().endsWith(".jar"))
                        .forEach(addonPath -> {
                            try (JarFile jar = new JarFile(addonPath.toString())) {
                                URLClassLoader child = new URLClassLoader(new URL[]{addonPath.toUri().toURL()}, Parser.class.getClassLoader());
                                String mainClassName = jar.getManifest().getMainAttributes().getValue("Main-Class");
                                if (mainClassName != null) {
                                    try {
                                        Class<?> mainClass = Class.forName(mainClassName, true, child);
                                        Method init = mainClass.getDeclaredMethod("initAddon");
                                        init.invoke(mainClass.getConstructor().newInstance());
                                    } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException | IllegalArgumentException | InstantiationException | SecurityException ignored) {
                                    } finally {
                                        jar.close();
                                    }
                                }
                            } catch (IOException | ClassNotFoundException e) {
                                System.err.println("Error while loading classes:");
                                e.printStackTrace();
                            }
                        });
                }
            }
        } catch (IOException | URISyntaxException e) {
            System.err.println("Error while loading classes:");
            e.printStackTrace();
        }

        // Log registration results
        Calendar time = Calendar.getInstance();
        logs = registration.register();
        if (!logs.isEmpty()) {
            System.out.print(ConsoleColors.PURPLE);
            System.out.println("Registration log:");
            printLogs(logs, time, true);
            System.out.println();
        }
    }

    public static void run(String scriptName, boolean debug, boolean tipsEnabled) {
        Calendar time = Calendar.getInstance();
        Path scriptPath = Paths.get(scriptName);
        logs = ScriptLoader.loadScript(scriptPath, debug);
        if (!logs.isEmpty()) {
            System.out.print(ConsoleColors.PURPLE);
            System.out.println("Parsing log:");
        }
        printLogs(logs, time, tipsEnabled);
        SkriptAddon.getAddons().forEach(SkriptAddon::finishedLoading);
    }

    public static void printLogs(List<LogEntry> logs, Calendar time, boolean tipsEnabled) {
        for (LogEntry log : logs) {
            ConsoleColors color = ConsoleColors.WHITE;
            if (log.getType() == LogType.WARNING) {
                color = ConsoleColors.YELLOW;
            } else if (log.getType() == LogType.ERROR) {
                color = ConsoleColors.RED;
            } else if (log.getType() == LogType.INFO) {
                color = ConsoleColors.BLUE;
            } else if (log.getType() == LogType.DEBUG) {
                color = ConsoleColors.PURPLE;
            }
            System.out.printf(color + CONSOLE_FORMAT + ConsoleColors.RESET, time, log.getType().name(), log.getMessage());
            if (tipsEnabled && log.getTip().isPresent())
                System.out.printf(ConsoleColors.BLUE_BRIGHT + CONSOLE_FORMAT + ConsoleColors.RESET, time, "TIP", log.getTip().get());
        }
    }

    public static SkriptRegistration getMainRegistration() {
        return registration;
    }

}
