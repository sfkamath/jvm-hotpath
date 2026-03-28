package io.github.sfkamath.jvmhotpath.gradle;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedHashSet;
import java.util.Properties;
import java.util.Set;
import org.gradle.api.JavaVersion;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.file.FileCollection;
import org.gradle.api.tasks.testing.Test;
import org.gradle.process.JavaForkOptions;

/**
 * Native Gradle plugin for JVM Hotpath.
 *
 * <p>Automatically attaches the JVM Hotpath agent to JavaExec tasks (and optionally Test tasks),
 * enabling real-time line-level execution frequency analysis.
 */
public class JvmHotpathPlugin implements Plugin<Project> {

  /** Creates a new instance of the plugin. */
  public JvmHotpathPlugin() {}

  @Override
  public void apply(Project project) {
    JvmHotpathExtension extension =
        project.getExtensions().create("jvmHotpath", JvmHotpathExtension.class);

    configureDefaults(project, extension);

    Configuration agentConfig = project.getConfigurations().create("jvmHotpathAgent");
    String agentVersion = loadPluginVersion();
    project
        .getDependencies()
        .add(agentConfig.getName(), "io.github.sfkamath:jvm-hotpath-agent:" + agentVersion);

    project
        .getTasks()
        .register(
            "hotpathHelp",
            task -> {
              task.setGroup("Help");
              task.setDescription(
                  "Prints a jq query for analysing execution-report.json from the terminal.");
              task.doLast(
                  t -> {
                    String reportPath = extension.getOutput().getOrElse("");
                    String examplePath =
                        reportPath.isEmpty()
                            ? "path/to/execution-report.json"
                            : reportPath.replace(".html", ".json");
                    t.getLogger()
                        .lifecycle(
                            "\nJVM Hotpath — CLI Analysis"
                                + "\n--------------------------"
                                + "\nTop 20 hottest lines across all instrumented files:\n"
                                + "\n  jq '[.files[] | select((.counts | length) > 0)"
                                + " | {path: .path, counts: .counts | to_entries}"
                                + " | .counts[] as $c | {file: .path, line: $c.key|tonumber, hits: $c.value}]"
                                + " | sort_by(.hits) | reverse | .[0:20]' "
                                + examplePath
                                + "\n\nFor a full breakdown of the query see:"
                                + "\n  https://github.com/sfkamath/jvm-hotpath#cli-analysis-with-jq\n");
                  });
            });

    project.afterEvaluate(
        p -> {
          if (extension.getSkip().get()) {
            p.getLogger().info("JVM Hotpath is skipped.");
            return;
          }

          FileCollection agentClasspath = agentConfig;
          File agentJar = resolveAgentJar(agentClasspath);

          if (agentJar == null || !agentJar.exists()) {
            throw new IllegalStateException(
                "Could not find jvm-hotpath-agent JAR. Ensure the dependency is resolvable.");
          }

          String agentArgs = buildAgentArgs(project, extension);
          String fullAgentArg =
              "-javaagent:"
                  + agentJar.getAbsolutePath()
                  + (agentArgs.isEmpty() ? "" : "=" + agentArgs);

          p.getLogger().info("JVM Hotpath configured.");
          p.getLogger().info("Agent String: " + fullAgentArg);
          if (extension.getVerbose().get()) {
            p.getLogger().info("Packages: " + extension.getPackages().get());
            p.getLogger().info("Sourcepath: " + collectSourcePaths(project, extension));
          }

          if (extension.getInstrumentTests().get()) {
            configureTestTasks(p, extension, fullAgentArg);
          }
          configureJavaExecTasks(p, extension, fullAgentArg);
          warnIfJmhAsmIncompatible(p);
        });
  }

  private void configureDefaults(Project project, JvmHotpathExtension extension) {
    var providers = project.getProviders();
    extension.getPackages().convention(
        providers.systemProperty("jvm-hotpath.packages").orElse(""));
    extension.getExclude().convention(
        providers.systemProperty("jvm-hotpath.exclude").orElse(""));
    extension.getFlushInterval().convention(
        providers.systemProperty("jvm-hotpath.flushInterval").map(Integer::parseInt).orElse(0));
    extension.getOutput().convention(
        providers.systemProperty("jvm-hotpath.output").orElse(""));
    extension.getSourcepath().convention(
        providers.systemProperty("jvm-hotpath.sourcepath").orElse(""));
    extension.getVerbose().convention(
        providers.systemProperty("jvm-hotpath.verbose").map(Boolean::parseBoolean).orElse(false));
    extension.getKeepAlive().convention(
        providers.systemProperty("jvm-hotpath.keepAlive").map(Boolean::parseBoolean).orElse(false));
    extension.getAppend().convention(
        providers.systemProperty("jvm-hotpath.append").map(Boolean::parseBoolean).orElse(false));
    extension.getInstrumentTests().convention(
        providers.systemProperty("jvm-hotpath.instrumentTests").map(Boolean::parseBoolean).orElse(false));
    extension.getSkip().convention(
        providers.systemProperty("jvm-hotpath.skip").map(Boolean::parseBoolean).orElse(false));
  }

  private File resolveAgentJar(FileCollection classpath) {
    Set<File> resolved = classpath.getFiles();
    for (File f : resolved) {
      if (f.getName().startsWith("jvm-hotpath-agent") && f.getName().endsWith(".jar")) {
        return f;
      }
    }
    if (!resolved.isEmpty()) {
      return resolved.iterator().next();
    }
    return null;
  }

  private String buildAgentArgs(Project project, JvmHotpathExtension extension) {
    StringBuilder args = new StringBuilder();

    String packages = buildPackages(project, extension);
    if (!packages.isEmpty()) {
      args.append("packages=").append(packages);
    }

    String exclude = extension.getExclude().get();
    if (exclude != null && !exclude.isEmpty()) {
      if (args.length() > 0) {
        args.append(",");
      }
      args.append("exclude=").append(exclude);
    }

    int flushInterval = extension.getFlushInterval().get();
    if (flushInterval > 0) {
      if (args.length() > 0) {
        args.append(",");
      }
      args.append("flushInterval=").append(flushInterval);
    }

    String output = extension.getOutput().get();
    if (output != null && !output.isEmpty()) {
      if (args.length() > 0) {
        args.append(",");
      }
      args.append("output=").append(output);
    }

    String sourcepath = collectSourcePaths(project, extension);
    if (!sourcepath.isEmpty()) {
      if (args.length() > 0) {
        args.append(",");
      }
      args.append("sourcepath=").append(sourcepath);
    }

    if (extension.getVerbose().get()) {
      if (args.length() > 0) {
        args.append(",");
      }
      args.append("verbose=true");
    }

    if (extension.getKeepAlive().get()) {
      if (args.length() > 0) {
        args.append(",");
      }
      args.append("keepAlive=true");
    }

    if (extension.getAppend().get()) {
      if (args.length() > 0) {
        args.append(",");
      }
      args.append("append=true");
    }

    return args.toString();
  }

  private String buildPackages(Project project, JvmHotpathExtension extension) {
    Set<String> packageList = new LinkedHashSet<>();

    if (project.getGroup() != null && !project.getGroup().toString().isEmpty()) {
      packageList.add(project.getGroup().toString());
    }

    String extPackages = extension.getPackages().get();
    if (extPackages != null && !extPackages.isEmpty()) {
      for (String p : extPackages.split(",")) {
        String trimmed = p.trim();
        if (!trimmed.isEmpty()) {
          packageList.add(trimmed);
        }
      }
    }

    return String.join(",", packageList);
  }

  private String collectSourcePaths(Project project, JvmHotpathExtension extension) {
    Set<String> sourcePaths = new LinkedHashSet<>();

    for (Project p : project.getAllprojects()) {
      String buildDirPath = p.getLayout().getBuildDirectory().getAsFile().get().getAbsolutePath();
      p.getPlugins()
          .withType(
              org.gradle.api.plugins.JavaPlugin.class,
              javaPlugin -> {
                org.gradle.api.tasks.SourceSet mainSourceSet =
                    p.getExtensions()
                        .findByType(org.gradle.api.plugins.JavaPluginExtension.class)
                        .getSourceSets()
                        .findByName("main");
                if (mainSourceSet != null) {
                  mainSourceSet
                      .getAllJava()
                      .getSrcDirs()
                      .forEach(
                          dir -> {
                            if (dir.exists() && !dir.getAbsolutePath().startsWith(buildDirPath)) {
                              sourcePaths.add(dir.getAbsolutePath());
                            }
                          });
                }
              });
    }

    String extSourcepath = extension.getSourcepath().get();
    if (extSourcepath != null && !extSourcepath.isEmpty()) {
      String separator = System.getProperty("path.separator");
      for (String s : extSourcepath.split(separator)) {
        String trimmed = s.trim();
        if (!trimmed.isEmpty()) {
          sourcePaths.add(trimmed);
        }
      }
    }

    return String.join(System.getProperty("path.separator"), sourcePaths);
  }

  /**
   * When JMH is present, resolves its runtime classpath to find the bundled ASM version and warns
   * if it is too old to instrument the current JVM's class file format. JMH ships its own ASM
   * copy; if it is older than what the running JDK requires, the agent will throw
   * "Unsupported class file major version" inside benchmark forks.
   *
   * <p>ASM 9.x support matrix: minor version N supports up to Java (16 + N).
   * e.g. ASM 9.0 → Java 16, ASM 9.1 → Java 17, ASM 9.5 → Java 21.
   */
  private void warnIfJmhAsmIncompatible(Project project) {
    if (!project.getPlugins().hasPlugin("me.champeau.jmh")) {
      return;
    }

    Configuration jmhRuntime = project.getConfigurations().findByName("jmhRuntimeClasspath");
    if (jmhRuntime == null) {
      return;
    }

    try {
      for (File f : jmhRuntime) {
        String name = f.getName();
        // Match core ASM jar only: asm-9.0.jar, asm-9.9.1.jar — not asm-tree-*, asm-commons-*, etc.
        if (!name.matches("asm-\\d.*\\.jar")) {
          continue;
        }

        // Strip "asm-" prefix and ".jar" suffix to get the version string
        String version = name.substring(4, name.length() - 4);
        String[] parts = version.split("\\.");
        if (parts.length < 2) {
          return;
        }

        int currentJava = Integer.parseInt(JavaVersion.current().getMajorVersion());
        int maxJava = maxJavaSupportedByAsm(version);

        if (maxJava >= 0 && currentJava > maxJava) {
          project.getLogger().warn(
              "[jvm-hotpath] JMH is using ASM {} (supports up to Java {}) but you are running"
                  + " Java {}. The jvm-hotpath agent will throw 'Unsupported class file major"
                  + " version' inside JMH benchmark forks. Force ASM 9.{}+ in your jmh"
                  + " dependencies — see https://github.com/sfkamath/jvm-hotpath#jmh-integration",
              version, maxJava, currentJava, (currentJava - 16));
        }
        return; // only need the core ASM jar
      }
    } catch (Exception e) {
      project.getLogger().debug("[jvm-hotpath] Could not check JMH ASM compatibility: {}", e.getMessage());
    }
  }

  /**
   * Returns the maximum Java version supported by the given ASM version string, or -1 if the
   * version cannot be parsed. ASM 9.x supports Java (16 + minor): e.g. 9.0 → 16, 9.1 → 17.
   * Pre-9.x ASM is treated as supporting at most Java 15.
   */
  static int maxJavaSupportedByAsm(String asmVersion) {
    String[] parts = asmVersion.split("\\.");
    if (parts.length < 2) {
      return -1;
    }
    try {
      int major = Integer.parseInt(parts[0]);
      int minor = Integer.parseInt(parts[1]);
      if (major == 9) {
        return 16 + minor;
      }
      return major < 9 ? 15 : Integer.MAX_VALUE;
    } catch (NumberFormatException e) {
      return -1;
    }
  }

  private String loadPluginVersion() {
    Properties props = new Properties();
    try (InputStream is = getClass().getResourceAsStream("/jvm-hotpath-plugin.properties")) {
      if (is != null) {
        props.load(is);
        return props.getProperty("version", "+");
      }
    } catch (IOException e) {
      // fall through
    }
    return "+";
  }

  private void configureTestTasks(Project project, JvmHotpathExtension extension, String agentArg) {
    project.getTasks().withType(Test.class).configureEach(test -> test.jvmArgs(agentArg));
  }

  private void configureJavaExecTasks(
      Project project, JvmHotpathExtension extension, String agentArg) {
    project
        .getTasks()
        .matching(
            task -> {
              String taskName = task.getName().toLowerCase();
              return "run".equals(taskName)
                  || "bootrun".equals(taskName)
                  || "applicationrun".equals(taskName)
                  || task.getExtensions().findByType(JavaForkOptions.class) != null;
            })
        .configureEach(
            task -> {
              if (task instanceof JavaForkOptions) {
                ((JavaForkOptions) task).jvmArgs(agentArg);
              }
            });
  }
}
