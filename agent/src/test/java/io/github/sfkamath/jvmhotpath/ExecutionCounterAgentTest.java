package io.github.sfkamath.jvmhotpath;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.instrument.ClassDefinition;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.Instrumentation;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarFile;
import org.junit.jupiter.api.Test;

class ExecutionCounterAgentTest {

  @Test
  void testArgumentParsing() {
    ExecutionCounterAgent agent = new ExecutionCounterAgent();
    String args =
        "packages=com.example,com.test,exclude=com.example.Exclude,flushInterval=10,output=target/test.html,sourcepath=src/main/java,verbose=true";

    agent.parseArguments(args);

    List<String> includes = agent.getIncludePackages();
    assertEquals(2, includes.size());
    assertTrue(includes.contains("com/example"), "Found: " + includes);
    assertTrue(includes.contains("com/test"), "Found: " + includes);

    List<String> excludes = agent.getExcludePackages();
    assertEquals(1, excludes.size());
    assertTrue(excludes.contains("com/example/Exclude"));

    assertEquals(10, agent.getFlushInterval());
    assertEquals("target/test.html", agent.getOutputFile());
    assertEquals("src/main/java", agent.getSourcePath());
    assertTrue(agent.isVerbose());
  }

  @Test
  void testMainMethod() throws Exception {
    Path dataFile = Files.createTempFile("data", ".json");
    Path reportFile = Files.createTempFile("report", ".html");
    try {
      // Write a valid empty report payload
      Files.writeString(dataFile, "{\"generatedAt\":0, \"files\":[]}");

      // Should show usage
      ExecutionCounterAgent.main(new String[0]);

      // Should fail with missing data
      ExecutionCounterAgent.main(new String[]{"--output=target/test.html"});

      // Should succeed regenerating
      ExecutionCounterAgent.main(
          new String[]{
            "--data=" + dataFile.toAbsolutePath(), "--output=" + reportFile.toAbsolutePath()
          });

      assertTrue(Files.exists(reportFile));
    } finally {
      Files.deleteIfExists(dataFile);
      Files.deleteIfExists(reportFile);
    }
  }

  @Test
  void testArgumentParsingComplex() {
    ExecutionCounterAgent agent = new ExecutionCounterAgent();
    // Test comma-separated packages and verbose flag
    agent.parseArguments(
        "packages=com.example,io.app,verbose=true,exclude=com.example.internal,flushInterval=5");

    List<String> includes = agent.getIncludePackages();
    assertEquals(2, includes.size());
    assertTrue(includes.contains("com/example"));
    assertTrue(includes.contains("io/app"));

    List<String> excludes = agent.getExcludePackages();
    assertEquals(1, excludes.size());
    assertTrue(excludes.contains("com/example/internal"));

    assertTrue(agent.isVerbose());
    assertEquals(5, agent.getFlushInterval());
  }

  @Test
  void testArgumentParsingEdgeCases() {
    ExecutionCounterAgent agent = new ExecutionCounterAgent();
    // Null and empty strings
    agent.parseArguments(null);
    agent.parseArguments("");
    agent.parseArguments("   ");

    // Malformed keys or values
    agent.parseArguments("invalid,packages=com.foo,=bar,verbose=notboolean");
    List<String> includes = agent.getIncludePackages();
    assertTrue(includes.contains("com/foo"));
    assertFalse(agent.isVerbose()); // Boolean.parseBoolean("notboolean") is false
  }

  @Test
  void testTransformationExclusions() throws Exception {
    ExecutionCounterAgent agent = new ExecutionCounterAgent();
    agent.parseArguments("packages=com.app,exclude=com.app.ignored");
    ClassFileTransformer transformer = agent.getTransformer();

    // Should skip null classname
    assertNull(transformer.transform(null, null, null, null, new byte[0]));

    // Should skip JVM/internal classes
    assertNull(transformer.transform(null, "java/lang/String", null, null, new byte[0]));
    assertNull(transformer.transform(null, "jdk/internal/Foo", null, null, new byte[0]));

    // Should skip excluded package
    assertNull(transformer.transform(null, "com/app/ignored/Secret", null, null, new byte[0]));

    // Should skip if not in included packages
    assertNull(transformer.transform(null, "org/other/App", null, null, new byte[0]));

    // Should skip Micronaut/logging internals
    assertNull(transformer.transform(null, "io/micronaut/Context", null, null, new byte[0]));
    assertNull(transformer.transform(null, "org/slf4j/Logger", null, null, new byte[0]));
  }

  @Test
  void testTransformationErrorHandling() throws Exception {
    ExecutionCounterAgent agent = new ExecutionCounterAgent();
    agent.parseArguments("packages=com.app,verbose=false");
    ClassFileTransformer transformer = agent.getTransformer();

    // Providing garbage bytes that ASM can't parse should trigger catch block
    assertNull(transformer.transform(null, "com/app/Logic", null, null, new byte[]{1, 2, 3}));
  }

  @Test
  void testPremainAndInit() {
    Instrumentation dummyInst = new DummyInstrumentation();
    // Use target/ to ensure shutdown hook output is ignored by git
    ExecutionCounterAgent.premain(
        "packages=com.test,flushInterval=0,output=target/shutdown-test.html", dummyInst);
  }

  @Test
  void testFlushThread() throws Exception {
    ExecutionCounterAgent agent = new ExecutionCounterAgent();
    // Set short interval and valid output
    agent.parseArguments("flushInterval=1,output=target/flush-test.html,verbose=true");

    Instrumentation dummyInst = new DummyInstrumentation();
    agent.init("flushInterval=1,output=target/flush-test.html", dummyInst);

    // Give it a moment to run at least one iteration
    Thread.sleep(3000);

    // Check if output was created by the thread
    assertTrue(Files.exists(Path.of("target/flush-test.html")));
    Files.deleteIfExists(Path.of("target/flush-test.html"));
    Files.deleteIfExists(Path.of("target/flush-test.json"));
    Files.deleteIfExists(Path.of("target/flush-test.js"));
  }

  private static class DummyInstrumentation implements Instrumentation {
    @Override
    public void addTransformer(ClassFileTransformer transformer, boolean canRetransform) {}

    @Override
    public void addTransformer(ClassFileTransformer transformer) {}

    @Override
    public boolean removeTransformer(ClassFileTransformer transformer) {
      return false;
    }

    @Override
    public boolean isRetransformClassesSupported() {
      return false;
    }

    @Override
    public void retransformClasses(Class<?>... classes) {}

    @Override
    public boolean isRedefineClassesSupported() {
      return false;
    }

    @Override
    public void redefineClasses(ClassDefinition... definitions) {}

    @Override
    public boolean isModifiableClass(Class<?> theClass) {
      return false;
    }

    @Override
    public Class[] getAllLoadedClasses() {
      return new Class[0];
    }

    @Override
    public Class[] getInitiatedClasses(ClassLoader loader) {
      return new Class[0];
    }

    @Override
    public long getObjectSize(Object objectToSize) {
      return 0;
    }

    @Override
    public void appendToBootstrapClassLoaderSearch(JarFile jarfile) {}

    @Override
    public void appendToSystemClassLoaderSearch(JarFile jarfile) {}

    @Override
    public boolean isNativeMethodPrefixSupported() {
      return false;
    }

    @Override
    public void setNativeMethodPrefix(ClassFileTransformer transformer, String prefix) {}

    // Java 9+ methods
    @Override
    public void redefineModule(
        Module module,
        Set<Module> extraReads,
        Map<String, Set<Module>> extraExports,
        Map<String, Set<Module>> extraOpens,
        Set<Class<?>> extraUses,
        Map<Class<?>, List<Class<?>>> extraProvides) {}

    @Override
    public boolean isModifiableModule(Module module) {
      return false;
    }
  }
}
