package io.github.sfkamath.jvmhotpath;

import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.Instrumentation;
import java.nio.file.Path;
import java.security.ProtectionDomain;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.jar.JarFile;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;

/**
 * Java agent that instruments classes to count line executions.
 */
public final class ExecutionCounterAgent {

  private static final Logger logger = Logger.getLogger(ExecutionCounterAgent.class.getName());

  public String[] includePackages = new String[0];
  public String[] excludePackages = new String[0];
  public String outputFile = "execution-report.html";
  public String sourcePath = "";
  public int flushInterval;
  public boolean verbose;
  public boolean keepAlive = true;

  public static void main(String[] args) {
    if (args.length == 0) {
      logger.info("Usage: java -jar jvm-hotpath-agent.jar --data=<data.json> --output=<report.html>");
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
      logger.severe("Error: --data argument is required.");
      return;
    }

    try {
      logger.info("Regenerating report...");
      ReportGenerator.regenerateReport(dataPath, outputPath);
    } catch (Exception e) {
      logger.log(Level.SEVERE, "Error regenerating report: " + e.getMessage(), e);
    }
  }

  public static void premain(String agentArgs, Instrumentation inst) {
    new ExecutionCounterAgent().init(agentArgs, inst);
  }

  void init(String agentArgs, Instrumentation inst) {
    logger.info("=== JVM Hotpath Agent Starting ===");

    try {
      Path jarPath = 
          Path.of(
              ExecutionCounterAgent.class
                  .getProtectionDomain()
                  .getCodeSource()
                  .getLocation()
                  .toURI());
      if (java.nio.file.Files.isRegularFile(jarPath)) {
        inst.appendToSystemClassLoaderSearch(new JarFile(jarPath.toFile()));
      }
    } catch (Exception e) {
      logger.log(Level.SEVERE, "Failed to append agent JAR to system class loader: " + e.getMessage(), e);
    }

    parseArguments(agentArgs);

    if (flushInterval > 0) {
      Thread flushThread = new Thread(() -> {
        while (true) {
          try {
            Thread.sleep(flushInterval * 1000L);
            if (verbose) logger.info("[FLUSH] Generating report...");
            ReportGenerator.generateHtmlReport(outputFile, sourcePath, verbose);
          } catch (InterruptedException e) {
            break;
          } catch (Throwable t) {
            if (verbose) logger.log(Level.WARNING, "Error in flush thread", t);
          }
        }
      }, "JvmHotpath-Flush-Thread");
      flushThread.setDaemon(true);
      flushThread.start();
    }

    inst.addTransformer(new ExecutionCountTransformer());

    Runtime.getRuntime().addShutdownHook(new Thread(() -> {
      try {
        ReportGenerator.generateHtmlReport(outputFile, sourcePath, verbose);
      } catch (Exception e) {
        logger.log(Level.SEVERE, "Error generating report during shutdown", e);
      }
    }));

    logger.info("=== JVM Hotpath Agent Ready ===\n");
  }

  void parseArguments(String agentArgs) {
    if (agentArgs == null || agentArgs.trim().isEmpty()) return;

    String[] rawArgs = agentArgs.split(",");
    List<String> args = new ArrayList<>();
    StringBuilder current = null;
    for (String rawArg : rawArgs) {
      String token = rawArg.trim();
      if (token.isEmpty()) continue;
      if (token.contains("=")) {
        if (current != null) args.add(current.toString());
        current = new StringBuilder(token);
      } else {
        if (current == null) current = new StringBuilder(token);
        else current.append(",").append(token);
      }
    }
    if (current != null) args.add(current.toString());

    for (String arg : args) {
      String[] parts = arg.split("=", 2);
      if (parts.length != 2) continue;
      String key = parts[0].trim();
      String value = parts[1].trim();

      switch (key) {
        case "packages":
          includePackages = value.split(",");
          for (int i = 0; i < includePackages.length; i++) {
            includePackages[i] = includePackages[i].trim().replace('.', '/');
            // Allow matching prefix without trailing slash for broader matching
          }
          break;
        case "exclude":
          excludePackages = value.split(",");
          for (int i = 0; i < excludePackages.length; i++) {
            excludePackages[i] = excludePackages[i].trim().replace('.', '/');
          }
          break;
        case "output": outputFile = value; break;
        case "sourcepath": sourcePath = value; break;
        case "flushInterval": flushInterval = Integer.parseInt(value); break;
        case "verbose": verbose = Boolean.parseBoolean(value); break;
        case "keepAlive": keepAlive = Boolean.parseBoolean(value); break;
      }
    }
  }

  ClassFileTransformer getTransformer() {
    return new ExecutionCountTransformer();
  }

  List<String> getIncludePackages() { return Arrays.asList(includePackages); }
  List<String> getExcludePackages() { return Arrays.asList(excludePackages); }
  int getFlushInterval() { return flushInterval; }
  String getOutputFile() { return outputFile; }
  String getSourcePath() { return sourcePath; }
  boolean isVerbose() { return verbose; }

  private class ExecutionCountTransformer implements ClassFileTransformer {
    @Override
    public byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined,
                            ProtectionDomain protectionDomain, byte[] classfileBuffer) {
      if (className == null) return null;
            // Don't instrument our own core classes
            if (className.startsWith("io/github/sfkamath/jvmhotpath/Execution") ||
                className.startsWith("io/github/sfkamath/jvmhotpath/Report")) {
              return null;
            }      if (className.startsWith("java/") || className.startsWith("javax/") || 
          className.startsWith("sun/") || className.startsWith("jdk/") || 
          className.startsWith("com/sun/")) return null;
      if (className.startsWith("io/micronaut/") || className.startsWith("jakarta/") || 
          className.startsWith("org/slf4j/") || className.startsWith("ch/qos/logback/") || 
          className.startsWith("io/netty/")) return null;
      if (className.contains("$Definition") || 
          className.contains("$Introspection") || className.contains("$Intercepted")) return null;

      for (String exclude : excludePackages) {
        if (className.startsWith(exclude)) {
          logger.log(Level.FINEST, "[INSTRUMENT] Skipped (exclude): {0}", className);
          return null;
        }
      }

      if (includePackages.length > 0) {
        boolean shouldInclude = false;
        for (String include : includePackages) {
          if (className.startsWith(include)) {
            shouldInclude = true;
            break;
          }
        }
        if (!shouldInclude) {
          logger.log(Level.FINEST, "[INSTRUMENT] Skipped (no match): {0}", className);
          return null;
        }
      }

      try {
        if (verbose) {
          logger.log(Level.INFO, "[INSTRUMENT] Attempting: {0}", className);
        }
        ClassReader cr = new ClassReader(classfileBuffer);
        ClassWriter cw = new ClassWriter(cr, ClassWriter.COMPUTE_FRAMES);
        cr.accept(new ExecutionCountClassVisitor(cw, className), ClassReader.EXPAND_FRAMES);
        byte[] result = cw.toByteArray();
        if (verbose) {
          logger.log(Level.INFO, "[INSTRUMENT] Success: {0}", className);
        }
        return result;
      } catch (Throwable t) {
        if (verbose) {
          logger.log(Level.SEVERE, "[INSTRUMENT] Failed: " + className, t);
        }
        return null;
      }
    }
  }

  public ExecutionCounterAgent() {}
}
