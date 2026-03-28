package io.github.sfkamath.jvmhotpath.maven;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class AsmVersionTest {

  // --- Known good versions ---

  @Test
  void asm90_supportsJava16() {
    assertEquals(16, PrepareAgentMojo.maxJavaSupportedByAsm("9.0"));
  }

  @Test
  void asm91_supportsJava17() {
    assertEquals(17, PrepareAgentMojo.maxJavaSupportedByAsm("9.1"));
  }

  @Test
  void asm95_supportsJava21() {
    assertEquals(21, PrepareAgentMojo.maxJavaSupportedByAsm("9.5"));
  }

  @Test
  void asm97_supportsJava23() {
    assertEquals(23, PrepareAgentMojo.maxJavaSupportedByAsm("9.7"));
  }

  @Test
  void asm991_supportsJava25() {
    // patch segment ignored — only major.minor matter
    assertEquals(25, PrepareAgentMojo.maxJavaSupportedByAsm("9.9.1"));
  }

  // --- Pre-9.x ---

  @Test
  void asm8_supportsAtMostJava15() {
    assertEquals(15, PrepareAgentMojo.maxJavaSupportedByAsm("8.0"));
  }

  @Test
  void asm7_supportsAtMostJava15() {
    assertEquals(15, PrepareAgentMojo.maxJavaSupportedByAsm("7.0"));
  }

  // --- Future major (10+) ---

  @Test
  void asm10_treatedAsUnbounded() {
    assertEquals(Integer.MAX_VALUE, PrepareAgentMojo.maxJavaSupportedByAsm("10.0"));
  }

  // --- Unparseable ---

  @Test
  void missingMinor_returnsNegative() {
    assertEquals(-1, PrepareAgentMojo.maxJavaSupportedByAsm("9"));
  }

  @Test
  void nonNumeric_returnsNegative() {
    assertEquals(-1, PrepareAgentMojo.maxJavaSupportedByAsm("unknown"));
  }

  @Test
  void empty_returnsNegative() {
    assertEquals(-1, PrepareAgentMojo.maxJavaSupportedByAsm(""));
  }
}
