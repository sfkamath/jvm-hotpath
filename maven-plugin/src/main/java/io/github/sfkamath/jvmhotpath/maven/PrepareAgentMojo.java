package io.github.sfkamath.jvmhotpath.maven;

import java.io.File;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.resolution.ArtifactRequest;
import org.eclipse.aether.resolution.ArtifactResult;

@Mojo(
    name = "prepare-agent",
    defaultPhase = LifecyclePhase.INITIALIZE,
    requiresDependencyResolution = ResolutionScope.TEST)
public class PrepareAgentMojo extends AbstractMojo {

  @Parameter(defaultValue = "${project}", readonly = true)
  private MavenProject project;

  @Parameter(defaultValue = "${plugin.artifacts}", readonly = true)
  private List<Artifact> pluginArtifacts;

  @Component private RepositorySystem repoSystem;

  @Parameter(defaultValue = "${repositorySystemSession}", readonly = true)
  private RepositorySystemSession repoSession;

  @Parameter(defaultValue = "${project.remoteProjectRepositories}", readonly = true)
  private List<RemoteRepository> remoteRepos;

  /**
   * Comma-separated list of additional packages to instrument. These are appended to the default
   * (project groupId).
   */
  @Parameter(property = "jvm-hotpath.packages")
  private String packages;

  /** Comma-separated list of packages/classes to exclude. */
  @Parameter(property = "jvm-hotpath.exclude")
  private String exclude;

  /** Interval in seconds to regenerate the report while running. Default is 0 (no auto-flush). */
  @Parameter(property = "jvm-hotpath.flushInterval", defaultValue = "0")
  private int flushInterval;

  /** Path to the generated HTML report. */
  @Parameter(
      property = "jvm-hotpath.output",
      defaultValue = "${project.build.directory}/execution-report.html")
  private File output;

  /** Additional source paths. These are appended to the project's compile source roots. */
  @Parameter(property = "jvm-hotpath.sourcepath")
  private String sourcepath;

  @Parameter(property = "jvm-hotpath.verbose", defaultValue = "false")
  private boolean verbose;

  /** Keep JVM alive (heartbeat). Default false for tests. */
  @Parameter(property = "jvm-hotpath.keepAlive", defaultValue = "false")
  private boolean keepAlive;

  /** Name of the property to set. Default is "argLine" (used by Surefire/Failsafe). */
  @Parameter(property = "jvm-hotpath.propertyName", defaultValue = "argLine")
  private String propertyName;

  @Parameter(property = "jvm-hotpath.skip", defaultValue = "false")
  private boolean skip;

  /** List of dependencies to include (resolve sources and optionally instrument packages). */
  @Parameter private List<DependencyConfig> includes;

  public static class DependencyConfig {
    @Parameter(required = true)
    String groupId;

    @Parameter(required = true)
    String artifactId;

    @Parameter String version;

    @Parameter String packageName;
  }

  @Override
  public void execute() throws MojoExecutionException {
    if (skip) {
      getLog().info("JVM Hotpath is skipped.");
      return;
    }

    File agentJar = findAgentJar();
    if (agentJar == null) {
      throw new MojoExecutionException(
          "Could not find jvm-hotpath-agent JAR in plugin dependencies.");
    }

    // --- Calculate Packages ---
    Set<String> packageList = new HashSet<>();
    // 1. Default: Project groupId
    if (project.getGroupId() != null && !project.getGroupId().isEmpty()) {
      packageList.add(project.getGroupId());
    }
    // 2. Add configured packages
    if (packages != null && !packages.isEmpty()) {
      for (String p : packages.split(",")) {
        packageList.add(p.trim());
      }
    }
    // 3. Add packages from includes
    if (includes != null) {
      for (DependencyConfig dep : includes) {
        if (dep.packageName != null && !dep.packageName.isEmpty()) {
          packageList.add(dep.packageName);
        }
      }
    }

    // --- Calculate Source Path ---
    Set<String> sourcePathList = new HashSet<>();
    // 1. Default: Project compile source roots
    List<String> roots = project.getCompileSourceRoots();
    if (roots != null) {
      sourcePathList.addAll(roots);
    }
    // 2. Add configured sourcepath
    if (sourcepath != null && !sourcepath.isEmpty()) {
      for (String s : sourcepath.split(File.pathSeparator)) {
        sourcePathList.add(s.trim());
      }
    }
    // 3. Resolve sources for includes
    if (includes != null && !includes.isEmpty()) {
      resolveDependencySources(sourcePathList);
    }

    // --- Build Agent Argument String ---
    StringBuilder args = new StringBuilder();

    String finalPackages = String.join(",", packageList);
    if (!finalPackages.isEmpty()) {
      args.append("packages=").append(finalPackages);
    }

    if (exclude != null && !exclude.isEmpty()) {
      if (args.length() > 0) {
        args.append(",");
      }
      args.append("exclude=").append(exclude);
    }

    if (flushInterval > 0) {
      if (args.length() > 0) {
        args.append(",");
      }
      args.append("flushInterval=").append(flushInterval);
    }

    if (output != null) {
      if (args.length() > 0) {
        args.append(",");
      }
      args.append("output=").append(output.getAbsolutePath());
    }

    String finalSourcepath = String.join(File.pathSeparator, sourcePathList);
    if (!finalSourcepath.isEmpty()) {
      if (args.length() > 0) {
        args.append(",");
      }
      args.append("sourcepath=").append(finalSourcepath);
    }

    if (verbose) {
      if (args.length() > 0) {
        args.append(",");
      }
      args.append("verbose=true");
    }

    if (keepAlive) {
      if (args.length() > 0) {
        args.append(",");
      }
      args.append("keepAlive=true");
    }

    String agentString = "-javaagent:" + agentJar.getAbsolutePath();
    if (args.length() > 0) {
      agentString += "=" + args.toString();
    }

    // --- Set Property ---
    String existing = project.getProperties().getProperty(propertyName);
    if (existing != null && !existing.isEmpty()) {
      agentString = agentString + " " + existing;
    }

    project.getProperties().setProperty(propertyName, agentString);
    getLog().info("JVM Hotpath configured.");
    getLog().info("Agent String: " + agentString);
    if (verbose) {
      getLog().info("Packages: " + finalPackages);
      getLog().info("Sourcepath: " + finalSourcepath);
    }
    getLog().debug("Set " + propertyName + " to: " + agentString);
  }

  private void resolveDependencySources(Set<String> sourcePathList) {
    for (DependencyConfig dep : includes) {
      String version = dep.version;

      // If version is missing, try to find it in the project's artifacts
      if (version == null || version.isEmpty()) {
        for (Artifact a : project.getArtifacts()) {
          if (a.getGroupId().equals(dep.groupId) && a.getArtifactId().equals(dep.artifactId)) {
            version = a.getVersion();
            break;
          }
        }
      }

      if (version == null) {
        getLog()
            .warn(
                "Could not determine version for included dependency: "
                    + dep.groupId
                    + ":"
                    + dep.artifactId);
        continue;
      }

      try {
        // Request the sources jar
        org.eclipse.aether.artifact.Artifact requestArtifact =
            new DefaultArtifact(dep.groupId, dep.artifactId, "sources", "jar", version);

        ArtifactRequest request = new ArtifactRequest();
        request.setArtifact(requestArtifact);
        request.setRepositories(remoteRepos);

        getLog().debug("Resolving sources for: " + requestArtifact);
        ArtifactResult result = repoSystem.resolveArtifact(repoSession, request);

        if (result.isResolved()) {
          File sourceJar = result.getArtifact().getFile();
          if (sourceJar != null) {
            sourcePathList.add(sourceJar.getAbsolutePath());
            getLog().info("Included sources: " + sourceJar.getName());
          }
        }
      } catch (Exception e) {
        getLog()
            .warn(
                "Failed to resolve sources for "
                    + dep.groupId
                    + ":"
                    + dep.artifactId
                    + " ("
                    + e.getMessage()
                    + ")");
      }
    }
  }

  private File findAgentJar() {
    for (Artifact artifact : pluginArtifacts) {
      if ("jvm-hotpath-agent".equals(artifact.getArtifactId())
          && "io.github.sfkamath".equals(artifact.getGroupId())) {
        return artifact.getFile();
      }
    }
    return null;
  }
}
