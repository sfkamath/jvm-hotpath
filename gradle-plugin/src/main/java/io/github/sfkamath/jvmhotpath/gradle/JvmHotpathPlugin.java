package io.github.sfkamath.jvmhotpath.gradle;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedHashSet;
import java.util.Properties;
import java.util.Set;
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

  @Override
  public void apply(Project project) {
    JvmHotpathExtension extension =
        project.getExtensions().create("jvmHotpath", JvmHotpathExtension.class);

    configureDefaults(extension);

    Configuration agentConfig = project.getConfigurations().create("jvmHotpathAgent");
    String agentVersion = loadPluginVersion();
    project
        .getDependencies()
        .add(agentConfig.getName(), "io.github.sfkamath:jvm-hotpath-agent:" + agentVersion);

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
        });
  }

  private void configureDefaults(JvmHotpathExtension extension) {
    extension.getPackages().convention("");
    extension.getExclude().convention("");
    extension.getFlushInterval().convention(0);
    extension.getOutput().convention("");
    extension.getSourcepath().convention("");
    extension.getVerbose().convention(false);
    extension.getKeepAlive().convention(false);
    extension.getAppend().convention(false);
    extension.getInstrumentTests().convention(false);
    extension.getSkip().convention(false);
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
