package io.github.sfkamath.jvmhotpath;

import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.Instrumentation;
import java.nio.file.Path;
import java.security.ProtectionDomain;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.jar.JarFile;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;

/**
 * Java agent that instruments classes to count line executions.
 *
 * <p>Usage: java -javaagent:jvm-hotpath-agent.jar=packages=com.example -jar your-app.jar
 *
 * <p>Agent arguments: - packages=com.example,org.myapp (comma-separated list of packages to
 * instrument) - exclude=com.example.test (comma-separated list of packages to exclude) -
 * output=report.html (output file path, default: execution-report.html)
 */
public final class ExecutionCounterAgent {

  private static String[] includePackages = new String[0];
  private static String[] excludePackages = new String[0];
  private static String outputFile = "execution-report.html";
  private static String sourcePath = "";
  private static int flushInterval;
  private static boolean verbose; // Add verbose flag
  private static boolean keepAlive = true;

  /** Main entry point for standalone usage (regenerating reports). */
  public static void main(String[] args) {
    if (args.length == 0) {
      System.out.println(
          "Usage: java -jar jvm-hotpath-agent.jar --data=<data.json> --output=<report.html>");
      return;
    }

    String dataPath = null;
    String outputPath = "execution-report.html";

    for (String arg : args) {
      if (arg.startsWith("--data=")) {
        dataPath = arg.substring(7);
      } else if (arg.startsWith("--output=")) {
        outputPath = arg.substring(9);
      }
    }

    if (dataPath == null) {
      System.err.println("Error: --data argument is required.");
      return;
    }

    try {
      System.out.println("Regenerating report...");
      System.out.println("Data: " + dataPath);
      System.out.println("Output: " + outputPath);
      ReportGenerator.regenerateReport(dataPath, outputPath);
      System.out.println("Done.");
    } catch (Exception e) {
      System.err.println("Error regenerating report: " + e.getMessage());
      e.printStackTrace();
    }
  }

  public static void premain(String agentArgs, Instrumentation inst) {
    System.out.println("=== JVM Hotpath Agent Starting ===");

    // Append agent JAR to system class loader search so instrumented classes can
    // find
    // ExecutionCountStore
    try {
      Path jarPath =
          Path.of(
              ExecutionCounterAgent.class
                  .getProtectionDomain()
                  .getCodeSource()
                  .getLocation()
                  .toURI());
      inst.appendToSystemClassLoaderSearch(new JarFile(jarPath.toFile()));
      System.out.println("Agent JAR appended to system class loader: " + jarPath);
    } catch (Exception e) {
      System.err.println("Failed to append agent JAR to system class loader: " + e.getMessage());
    }

    // Parse agent arguments
    parseArguments(agentArgs);

    System.out.println("Instrumenting packages: " + String.join(", ", includePackages));
    if (excludePackages.length > 0) {
      System.out.println("Excluding packages: " + String.join(", ", excludePackages));
    }
    System.out.println("Output file: " + outputFile);
    if (!sourcePath.isEmpty()) {
      System.out.println("Source path: " + sourcePath);
    }

    if (flushInterval > 0) {
      System.out.println("Auto-flush enabled. Interval: " + flushInterval + " seconds");
      Thread flushThread =
          new Thread(
              () -> {
                while (true) {
                  try {
                    Thread.sleep(flushInterval * 1000L);
                    System.out.println("[FLUSH] Generating report at " + new Date());
                    ReportGenerator.generateHtmlReport(outputFile, sourcePath);
                    System.out.println("[FLUSH] Report generated successfully");
                  } catch (InterruptedException e) {
                    System.out.println("Flush thread interrupted.");
                    break;
                  } catch (Throwable t) {
                    // CRITICAL: Catch Throwable to prevent thread death
                    System.err.println(
                        "[FLUSH ERROR] " + t.getClass().getName() + ": " + t.getMessage());
                    t.printStackTrace();
                    // Continue running despite errors
                  }
                }
              },
              "JvmHotpath-Flush-Thread");
      flushThread.setDaemon(true);
      flushThread.start();

      // Heartbeat thread (optionally keeps JVM alive)
      Thread heartbeat =
          new Thread(
              () -> {
                while (true) {
                  try {
                    Thread.sleep(30000);
                    System.out.println("=== Agent Heartbeat: JVM is still alive ===");
                  } catch (InterruptedException e) {
                    break;
                  }
                }
              },
              "JvmHotpath-Heartbeat");
      heartbeat.setDaemon(!keepAlive);
      heartbeat.start();
    }

    if (keepAlive && flushInterval <= 0) {
      Thread heartbeat =
          new Thread(
              () -> {
                while (true) {
                  try {
                    Thread.sleep(30000);
                    System.out.println("=== Agent Heartbeat: JVM is still alive ===");
                  } catch (InterruptedException e) {
                    break;
                  }
                }
              },
              "JvmHotpath-Heartbeat");
      heartbeat.setDaemon(false);
      heartbeat.start();
    }

    // Add transformer
    inst.addTransformer(new ExecutionCountTransformer());

    // Add shutdown hook to generate report
    Runtime.getRuntime()
        .addShutdownHook(
            new Thread(
                () -> {
                  System.out.println("\n=== Generating JVM Hotpath Report ===");
                  try {
                    ReportGenerator.generateHtmlReport(outputFile, sourcePath);
                    System.out.println("Report generated: " + outputFile);
                  } catch (Exception e) {
                    System.err.println("Error generating report: " + e.getMessage());
                    e.printStackTrace();
                  }
                }));

    System.out.println("=== JVM Hotpath Agent Ready ===\n");
  }

  private static void parseArguments(String agentArgs) {
    if (agentArgs == null || agentArgs.trim().isEmpty()) {
      return;
    }

    String[] rawArgs = agentArgs.split(",");
    List<String> args = new ArrayList<>();
    StringBuilder current = null;
    for (String rawArg : rawArgs) {
      String token = rawArg.trim();
      if (token.isEmpty()) {
        continue;
      }
      if (token.contains("=")) {
        if (current != null) {
          args.add(current.toString());
        }
        current = new StringBuilder(token);
      } else {
        if (current == null) {
          current = new StringBuilder(token);
        } else {
          current.append(",").append(token);
        }
      }
    }
    if (current != null) {
      args.add(current.toString());
    }

    for (String arg : args) {
      String[] parts = arg.split("=", 2);
      if (parts.length != 2) {
        continue;
      }

      String key = parts[0].trim();
      String value = parts[1].trim();

      switch (key) {
        case "packages":
          includePackages = value.split(",");
          for (int i = 0; i < includePackages.length; i++) {
            includePackages[i] = includePackages[i].trim().replace('.', '/');
          }
          break;
        case "exclude":
          excludePackages = value.split(",");
          for (int i = 0; i < excludePackages.length; i++) {
            excludePackages[i] = excludePackages[i].trim().replace('.', '/');
          }
          break;
        case "output":
          outputFile = value;
          break;
        case "sourcepath":
          sourcePath = value;
          break;
        case "flushInterval":
          try {
            flushInterval = Integer.parseInt(value);
          } catch (NumberFormatException e) {
            System.err.println("Invalid flushInterval: " + value);
          }
          break;
        case "verbose":
          verbose = Boolean.parseBoolean(value);
          break;
        case "keepAlive":
          keepAlive = Boolean.parseBoolean(value);
          break;
        case "keepalive":
          keepAlive = Boolean.parseBoolean(value);
          break;
        default:
          if (verbose) {
            System.out.println("[ARGS] Ignoring unknown argument: " + key);
          }
          break;
      }
    }
  }

  /** ClassFileTransformer that applies execution counting instrumentation. */
  private static class ExecutionCountTransformer implements ClassFileTransformer {

    @Override
    public byte[] transform(
        ClassLoader loader,
        String className,
        Class<?> classBeingRedefined,
        ProtectionDomain protectionDomain,
        byte[] classfileBuffer) {

      // Don't instrument our own classes
      if (className.startsWith("io/github/sfkamath/jvmhotpath/")) {
        return null;
      }

      // Don't instrument JDK classes
      if (className.startsWith("java/")
          || className.startsWith("javax/")
          || className.startsWith("sun/")
          || className.startsWith("jdk/")
          || className.startsWith("com/sun/")) {
        return null;
      }

      // Don't instrument Micronaut framework classes (CRITICAL for Micronaut apps)
      if (className.startsWith("io/micronaut/")
          || className.startsWith("jakarta/")
          || className.startsWith("org/slf4j/")
          || className.startsWith("ch/qos/logback/")
          || className.startsWith("io/netty/")) {
        return null;
      }

      // Don't instrument Micronaut generated classes (CRITICAL)
      if (className.contains("$Definition")
          || className.contains("$Proxy")
          || className.contains("$Introspection")
          || className.contains("$Intercepted")
          || className.contains("$InterceptorChain")
          || className.contains("$$")) { // Micronaut uses $$
        return null;
      }

      // REMOVED: Overly broad "Config" exclusion that was blocking legitimate app
      // classes
      // REMOVED: Application class exclusion - we want to instrument this

      // Check if class should be excluded
      for (String exclude : excludePackages) {
        if (className.startsWith(exclude)) {
          return null;
        }
      }

      // Check if class should be included
      if (includePackages.length > 0) {
        boolean shouldInclude = false;
        for (String include : includePackages) {
          if (className.startsWith(include)) {
            shouldInclude = true;
            break;
          }
        }
        if (!shouldInclude) {
          return null;
        }
      }

      try {
        if (verbose) {
          System.out.println("[INSTRUMENT] " + className.replace('/', '.'));
        }

        ClassReader cr = new ClassReader(classfileBuffer);
        ClassWriter cw = new ClassWriter(cr, ClassWriter.COMPUTE_FRAMES);
        ClassVisitor cv = new ExecutionCountClassVisitor(cw);

        cr.accept(cv, ClassReader.EXPAND_FRAMES);

        return cw.toByteArray();
      } catch (Throwable t) {
        // CRITICAL: Catch Throwable (not just Exception) to prevent class loader
        // crashes
        System.err.println(
            "[AGENT ERROR] Failed to instrument class "
                + className
                + ": "
                + t.getClass().getName()
                + ": "
                + t.getMessage());
        // Return null to use original bytecode - DO NOT let exceptions propagate
        return null;
      }
    }
  }

  private ExecutionCounterAgent() {}
}
