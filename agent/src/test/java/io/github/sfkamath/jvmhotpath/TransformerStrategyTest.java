package io.github.sfkamath.jvmhotpath;

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/**
 * Tests that verify the COMPUTE_MAXS + EXPAND_FRAMES instrumentation strategy.
 *
 * <p>The core problem: COMPUTE_FRAMES forces ASM to call {@code getCommonSuperClass()} at every
 * branch merge point. The default implementation uses {@code Class.forName()}, which triggers JVM
 * class loading. If that loading happens <em>inside</em> {@code ClassFileTransformer.transform()},
 * the JVM's re-entrancy guard silently skips the transformer for those classes — they load
 * uninstrumented with no warning.
 *
 * <p>COMPUTE_MAXS never calls {@code getCommonSuperClass()} — it only recomputes max stack/locals
 * and relies on the stack map frames already present in the class file (expanded to full form by
 * EXPAND_FRAMES). Since our instrumentation only inserts a static call at line-number boundaries
 * (which does not alter control flow or local-variable types), the existing frames remain valid
 * after instrumentation.
 */
class TransformerStrategyTest {

  @BeforeEach
  void resetCounters() {
    ExecutionCountStore.reset();
  }

  // ── Bytecode generation helpers ─────────────────────────────────────────────

  /**
   * Abstract {@code fixture.Base} class that TypeA and TypeB extend.
   */
  private byte[] generateBase() {
    ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
    cw.visit(
        Opcodes.V11,
        Opcodes.ACC_PUBLIC | Opcodes.ACC_ABSTRACT,
        "fixture/Base",
        null,
        "java/lang/Object",
        null);
    MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
    mv.visitCode();
    mv.visitVarInsn(Opcodes.ALOAD, 0);
    mv.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
    mv.visitInsn(Opcodes.RETURN);
    mv.visitMaxs(1, 1);
    mv.visitEnd();
    cw.visitEnd();
    return cw.toByteArray();
  }

  /** Concrete {@code fixture.TypeA extends fixture.Base}. */
  private byte[] generateTypeA() {
    ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
    cw.visit(Opcodes.V11, Opcodes.ACC_PUBLIC, "fixture/TypeA", null, "fixture/Base", null);
    MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
    mv.visitCode();
    mv.visitVarInsn(Opcodes.ALOAD, 0);
    mv.visitMethodInsn(Opcodes.INVOKESPECIAL, "fixture/Base", "<init>", "()V", false);
    mv.visitInsn(Opcodes.RETURN);
    mv.visitMaxs(1, 1);
    mv.visitEnd();
    cw.visitEnd();
    return cw.toByteArray();
  }

  /** Concrete {@code fixture.TypeB extends fixture.Base}. */
  private byte[] generateTypeB() {
    ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
    cw.visit(Opcodes.V11, Opcodes.ACC_PUBLIC, "fixture/TypeB", null, "fixture/Base", null);
    MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
    mv.visitCode();
    mv.visitVarInsn(Opcodes.ALOAD, 0);
    mv.visitMethodInsn(Opcodes.INVOKESPECIAL, "fixture/Base", "<init>", "()V", false);
    mv.visitInsn(Opcodes.RETURN);
    mv.visitMaxs(1, 1);
    mv.visitEnd();
    cw.visitEnd();
    return cw.toByteArray();
  }

  /**
   * {@code fixture.Subject} class with a branch that merges TypeA and TypeB:
   *
   * <pre>{@code
   * // line 10
   * Base pick(boolean flag) {
   *   Base result;
   *   if (flag) {
   *     result = new TypeA();  // line 10
   *   } else {
   *     result = new TypeB();  // line 13
   *   }
   *   return result;           // line 16  ← merge point: TypeA ⊔ TypeB = Base
   * }
   * }</pre>
   *
   * <p>The stack map at the return label declares the merge type as {@code fixture/Base}.
   * COMPUTE_FRAMES would re-derive this by calling {@code getCommonSuperClass("fixture/TypeA",
   * "fixture/TypeB")}. COMPUTE_MAXS keeps the existing frame untouched.
   */
  private byte[] generateSubject() {
    // COMPUTE_FRAMES is used here only to generate the original (pre-instrumentation) bytecode with
    // correct stack map frames. The getCommonSuperClass override prevents Class.forName on fixture
    // types that are not on the system classpath.
    ClassWriter cw =
        new ClassWriter(ClassWriter.COMPUTE_FRAMES) {
          @Override
          protected String getCommonSuperClass(String type1, String type2) {
            if (isFixtureType(type1) || isFixtureType(type2)) {
              return "fixture/Base";
            }
            return super.getCommonSuperClass(type1, type2);
          }

          private boolean isFixtureType(String t) {
            return t.startsWith("fixture/");
          }
        };

    cw.visit(
        Opcodes.V11, Opcodes.ACC_PUBLIC, "fixture/Subject", null, "java/lang/Object", null);

    // Constructor
    MethodVisitor init = cw.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
    init.visitCode();
    init.visitVarInsn(Opcodes.ALOAD, 0);
    init.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
    init.visitInsn(Opcodes.RETURN);
    init.visitMaxs(1, 1);
    init.visitEnd();

    // Base pick(boolean flag)
    // locals: this(0), flag(1), result(2)
    MethodVisitor mv =
        cw.visitMethod(Opcodes.ACC_PUBLIC, "pick", "(Z)Lfixture/Base;", null, null);
    mv.visitCode();

    Label branchFalse = new Label(); // TypeB branch
    Label merge = new Label();       // return point

    // Line 10: evaluate condition and take TypeA branch
    Label l10 = new Label();
    mv.visitLabel(l10);
    mv.visitLineNumber(10, l10);
    mv.visitVarInsn(Opcodes.ILOAD, 1);
    mv.visitJumpInsn(Opcodes.IFEQ, branchFalse);

    mv.visitTypeInsn(Opcodes.NEW, "fixture/TypeA");
    mv.visitInsn(Opcodes.DUP);
    mv.visitMethodInsn(Opcodes.INVOKESPECIAL, "fixture/TypeA", "<init>", "()V", false);
    mv.visitVarInsn(Opcodes.ASTORE, 2);
    mv.visitJumpInsn(Opcodes.GOTO, merge);

    // Line 13: TypeB branch
    mv.visitLabel(branchFalse);
    mv.visitLineNumber(13, branchFalse);
    mv.visitTypeInsn(Opcodes.NEW, "fixture/TypeB");
    mv.visitInsn(Opcodes.DUP);
    mv.visitMethodInsn(Opcodes.INVOKESPECIAL, "fixture/TypeB", "<init>", "()V", false);
    mv.visitVarInsn(Opcodes.ASTORE, 2);

    // Line 16: return — merge point where TypeA ⊔ TypeB = Base
    mv.visitLabel(merge);
    mv.visitLineNumber(16, merge);
    mv.visitVarInsn(Opcodes.ALOAD, 2);
    mv.visitInsn(Opcodes.ARETURN);

    mv.visitMaxs(2, 3);
    mv.visitEnd();

    cw.visitEnd();
    return cw.toByteArray();
  }

  // ── ClassLoader ─────────────────────────────────────────────────────────────

  /**
   * ClassLoader that serves fixture classes from in-memory byte arrays. Exposes
   * {@link #defineSubject(byte[])} so the test can inject instrumented bytes for Subject.
   */
  private static class FixtureClassLoader extends ClassLoader {

    private final byte[] baseBytes;
    private final byte[] typeABytes;
    private final byte[] typeBBytes;

    FixtureClassLoader(byte[] baseBytes, byte[] typeABytes, byte[] typeBBytes) {
      super(TransformerStrategyTest.class.getClassLoader());
      this.baseBytes = baseBytes;
      this.typeABytes = typeABytes;
      this.typeBBytes = typeBBytes;
    }

    Class<?> defineSubject(byte[] bytes) {
      return defineClass("fixture.Subject", bytes, 0, bytes.length);
    }

    @Override
    protected Class<?> findClass(String name) throws ClassNotFoundException {
      switch (name) {
        case "fixture.Base":
          return defineClass(name, baseBytes, 0, baseBytes.length);
        case "fixture.TypeA":
          return defineClass(name, typeABytes, 0, typeABytes.length);
        case "fixture.TypeB":
          return defineClass(name, typeBBytes, 0, typeBBytes.length);
        default:
          throw new ClassNotFoundException(name);
      }
    }

    @Override
    public InputStream getResourceAsStream(String name) {
      switch (name) {
        case "fixture/Base.class":
          return new ByteArrayInputStream(baseBytes);
        case "fixture/TypeA.class":
          return new ByteArrayInputStream(typeABytes);
        case "fixture/TypeB.class":
          return new ByteArrayInputStream(typeBBytes);
        default:
          return super.getResourceAsStream(name);
      }
    }
  }

  // ── Instrumentation helpers ──────────────────────────────────────────────────

  private byte[] instrumentWithComputeMaxs(byte[] classBytes, String className) {
    ClassReader cr = new ClassReader(classBytes);
    ClassWriter cw = new ClassWriter(cr, ClassWriter.COMPUTE_MAXS);
    cr.accept(new ExecutionCountClassVisitor(cw, className), ClassReader.EXPAND_FRAMES);
    return cw.toByteArray();
  }

  // ── Tests ────────────────────────────────────────────────────────────────────

  /**
   * COMPUTE_FRAMES must call {@code getCommonSuperClass} to re-derive the stack-map frame at the
   * TypeA/TypeB branch merge. This is the root cause of the re-entrancy issue: if those types are
   * not yet loaded, the default impl uses {@code Class.forName()}, which triggers their class
   * loading inside {@code transform()} — where the JVM re-entrancy guard prevents them from being
   * instrumented.
   */
  @Test
  void computeFrames_callsGetCommonSuperClassAtBranchMerge() {
    byte[] subject = generateSubject();
    AtomicBoolean queried = new AtomicBoolean(false);

    ClassReader cr = new ClassReader(subject);
    ClassWriter cw =
        new ClassWriter(cr, ClassWriter.COMPUTE_FRAMES) {
          @Override
          protected String getCommonSuperClass(String type1, String type2) {
            if (type1.startsWith("fixture") || type2.startsWith("fixture")) {
              queried.set(true);
            }
            return "java/lang/Object"; // safe fallback — not verifying output here
          }
        };
    cr.accept(new ExecutionCountClassVisitor(cw, "fixture/Subject"), ClassReader.EXPAND_FRAMES);
    cw.toByteArray();

    assertTrue(
        queried.get(),
        "COMPUTE_FRAMES must call getCommonSuperClass for the TypeA/TypeB merge point");
  }

  /**
   * COMPUTE_MAXS never calls {@code getCommonSuperClass} — it only recalculates stack/local sizes
   * and keeps existing stack-map frames. This eliminates the re-entrancy class-loading hazard.
   */
  @Test
  void computeMaxs_neverCallsGetCommonSuperClass() {
    byte[] subject = generateSubject();
    AtomicBoolean queried = new AtomicBoolean(false);

    ClassReader cr = new ClassReader(subject);
    ClassWriter cw =
        new ClassWriter(cr, ClassWriter.COMPUTE_MAXS) {
          @Override
          protected String getCommonSuperClass(String type1, String type2) {
            queried.set(true);
            return super.getCommonSuperClass(type1, type2);
          }
        };
    cr.accept(new ExecutionCountClassVisitor(cw, "fixture/Subject"), ClassReader.EXPAND_FRAMES);
    cw.toByteArray();

    assertFalse(
        queried.get(),
        "COMPUTE_MAXS must not call getCommonSuperClass during instrumentation");
  }

  /**
   * The instrumented Subject class must load and run without a {@link VerifyError}. The existing
   * stack-map frame at the merge label declares local 2 as {@code fixture/Base}, which remains
   * valid after instrumentation because our injected static call does not alter the frame state.
   */
  @Test
  void computeMaxs_instrumentedBranchMergeClassLoadsWithoutVerifyError() throws Exception {
    byte[] instrumented = instrumentWithComputeMaxs(generateSubject(), "fixture/Subject");

    FixtureClassLoader loader =
        new FixtureClassLoader(generateBase(), generateTypeA(), generateTypeB());
    Class<?> subjectClass = loader.defineSubject(instrumented);
    Object instance = subjectClass.getDeclaredConstructor().newInstance();

    Method pick = subjectClass.getMethod("pick", boolean.class);

    // Both branches must execute without VerifyError or any runtime error
    Object resultTrue = pick.invoke(instance, true);
    Object resultFalse = pick.invoke(instance, false);

    assertEquals("fixture.TypeA", resultTrue.getClass().getName());
    assertEquals("fixture.TypeB", resultFalse.getClass().getName());
  }

  /**
   * After running the instrumented class, {@link ExecutionCountStore} must contain counts for all
   * three instrumented lines. Verifies end-to-end that the injected bytecode records correctly.
   */
  @Test
  void computeMaxs_recordsCorrectExecutionCountsOnBothBranches() throws Exception {
    byte[] instrumented = instrumentWithComputeMaxs(generateSubject(), "fixture/Subject");

    FixtureClassLoader loader =
        new FixtureClassLoader(generateBase(), generateTypeA(), generateTypeB());
    Class<?> subjectClass = loader.defineSubject(instrumented);
    Object instance = subjectClass.getDeclaredConstructor().newInstance();

    Method pick = subjectClass.getMethod("pick", boolean.class);
    pick.invoke(instance, true);  // call 1: TypeA branch
    pick.invoke(instance, true);  // call 2: TypeA branch
    pick.invoke(instance, false); // call 3: TypeB branch

    // Line 10 (condition + TypeA branch): executed on all 3 calls
    assertEquals(3, ExecutionCountStore.getCount("fixture.Subject", 10),
        "condition line hit on every call");
    // Line 13 (TypeB branch): only the third call
    assertEquals(1, ExecutionCountStore.getCount("fixture.Subject", 13),
        "TypeB branch hit once");
    // Line 16 (return / merge): every call
    assertEquals(3, ExecutionCountStore.getCount("fixture.Subject", 16),
        "return line hit on every call");
  }
}
