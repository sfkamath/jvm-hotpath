package io.github.sfkamath.jvmhotpath.maven;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Properties;
import java.util.stream.Stream;
import org.apache.maven.execution.DefaultMavenExecutionRequest;
import org.apache.maven.execution.DefaultMavenExecutionResult;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.project.MavenProject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class PrepareAgentMojoMainClassResolutionTest {

  private static final String POM_MAIN_CLASS =
      "io.github.sfkamath.jvmhotpath.sample.MainClassProbe";
  private static final String OVERRIDE_MAIN_CLASS =
      "io.github.sfkamath.jvmhotpath.sample.micronaut.MainClassProbe";

  @ParameterizedTest(name = "{0}")
  @MethodSource("resolutionCases")
  void shouldResolveExecMainClassFromSupportedSources(
      String caseName, String configuredMainClass, Properties userProperties, String expectedValue)
      throws Throwable {
    PrepareAgentMojo mojo =
        newMojo(List.of("jvm-hotpath:prepare-agent"), configuredMainClass, userProperties);

    invokePrivateMethod(mojo, "populateExecMainClass");

    assertEquals(
        expectedValue,
        readProjectProperties(mojo).getProperty("exec.mainClass"),
        "Unexpected exec.mainClass for case " + caseName);
  }

  private static Stream<Arguments> resolutionCases() {
    return Stream.of(
        Arguments.of("default-pom-mainClass", POM_MAIN_CLASS, props(), POM_MAIN_CLASS),
        Arguments.of(
            "exec.mainClass",
            POM_MAIN_CLASS,
            props("exec.mainClass", OVERRIDE_MAIN_CLASS),
            OVERRIDE_MAIN_CLASS),
        Arguments.of(
            "jvm-hotpath.mainClass",
            POM_MAIN_CLASS,
            props("jvm-hotpath.mainClass", OVERRIDE_MAIN_CLASS),
            OVERRIDE_MAIN_CLASS),
        Arguments.of(
            "main.class",
            POM_MAIN_CLASS,
            props("jvm-hotpath.mainClass", "", "main.class", OVERRIDE_MAIN_CLASS),
            OVERRIDE_MAIN_CLASS),
        Arguments.of(
            "mainClass",
            POM_MAIN_CLASS,
            props("jvm-hotpath.mainClass", "", "mainClass", OVERRIDE_MAIN_CLASS),
            OVERRIDE_MAIN_CLASS),
        Arguments.of(
            "start-class",
            POM_MAIN_CLASS,
            props("jvm-hotpath.mainClass", "", "start-class", OVERRIDE_MAIN_CLASS),
            OVERRIDE_MAIN_CLASS),
        Arguments.of(
            "spring-boot.run.main-class",
            POM_MAIN_CLASS,
            props("jvm-hotpath.mainClass", "", "spring-boot.run.main-class", OVERRIDE_MAIN_CLASS),
            OVERRIDE_MAIN_CLASS));
  }

  @Test
  void shouldFailFastWhenExecGoalRequestedAndMainClassCannotBeDetermined() throws Throwable {
    PrepareAgentMojo mojo =
        newMojo(List.of("exec:exec"), POM_MAIN_CLASS, props("jvm-hotpath.mainClass", ""));
    invokePrivateMethod(mojo, "populateExecMainClass");

    MojoExecutionException error =
        assertThrows(
            MojoExecutionException.class,
            () -> invokePrivateMethod(mojo, "validateExecMainClassIfNeeded"));
    assertTrue(error.getMessage().contains("Could not determine exec.mainClass for exec:exec"));
  }

  @Test
  void shouldFailFastForFullyQualifiedExecGoalWhenMainClassCannotBeDetermined() throws Throwable {
    PrepareAgentMojo mojo =
        newMojo(
            List.of("org.codehaus.mojo:exec-maven-plugin:3.5.0:exec"),
            POM_MAIN_CLASS,
            props("jvm-hotpath.mainClass", ""));
    invokePrivateMethod(mojo, "populateExecMainClass");

    MojoExecutionException error =
        assertThrows(
            MojoExecutionException.class,
            () -> invokePrivateMethod(mojo, "validateExecMainClassIfNeeded"));
    assertTrue(error.getMessage().contains("Could not determine exec.mainClass for exec:exec"));
  }

  @Test
  void shouldNotRequireMainClassWhenExecGoalIsNotRequested() throws Throwable {
    PrepareAgentMojo mojo =
        newMojo(
            List.of("jvm-hotpath:prepare-agent"),
            POM_MAIN_CLASS,
            props("jvm-hotpath.mainClass", ""));
    invokePrivateMethod(mojo, "populateExecMainClass");

    assertDoesNotThrow(() -> invokePrivateMethod(mojo, "validateExecMainClassIfNeeded"));
  }

  private static PrepareAgentMojo newMojo(
      List<String> goals, String configuredMainClass, Properties userProperties)
      throws ReflectiveOperationException {
    PrepareAgentMojo mojo = new PrepareAgentMojo();
    MavenProject project = new MavenProject();
    MavenSession session = session(goals, userProperties);

    setField(mojo, "project", project);
    setField(mojo, "session", session);
    setField(mojo, "mainClass", configuredMainClass);
    return mojo;
  }

  private static MavenSession session(List<String> goals, Properties userProperties) {
    DefaultMavenExecutionRequest request = new DefaultMavenExecutionRequest();
    request.setGoals(goals);
    request.setUserProperties(userProperties == null ? new Properties() : userProperties);
    request.setSystemProperties(new Properties());
    return new MavenSession(null, null, request, new DefaultMavenExecutionResult());
  }

  private static Properties readProjectProperties(PrepareAgentMojo mojo)
      throws ReflectiveOperationException {
    Field projectField = PrepareAgentMojo.class.getDeclaredField("project");
    projectField.setAccessible(true);
    MavenProject project = (MavenProject) projectField.get(mojo);
    return project.getProperties();
  }

  private static void invokePrivateMethod(PrepareAgentMojo mojo, String methodName)
      throws Throwable {
    Method method = PrepareAgentMojo.class.getDeclaredMethod(methodName);
    method.setAccessible(true);
    try {
      method.invoke(mojo);
    } catch (InvocationTargetException ex) {
      throw ex.getCause();
    }
  }

  private static void setField(PrepareAgentMojo mojo, String fieldName, Object value)
      throws ReflectiveOperationException {
    Field field = PrepareAgentMojo.class.getDeclaredField(fieldName);
    field.setAccessible(true);
    field.set(mojo, value);
  }

  private static Properties props(String... entries) {
    Properties properties = new Properties();
    for (int i = 0; i < entries.length; i += 2) {
      properties.setProperty(entries[i], entries[i + 1]);
    }
    return properties;
  }
}
