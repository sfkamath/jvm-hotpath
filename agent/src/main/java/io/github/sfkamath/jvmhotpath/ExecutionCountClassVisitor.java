package io.github.sfkamath.jvmhotpath;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/** ASM ClassVisitor that instruments methods to record line executions. */
public class ExecutionCountClassVisitor extends ClassVisitor {

  private String className;

  public ExecutionCountClassVisitor(ClassVisitor cv) {
    super(Opcodes.ASM9, cv);
  }

  public ExecutionCountClassVisitor(ClassVisitor cv, String className) {
    super(Opcodes.ASM9, cv);
    this.className = className.replace('/', '.');
  }

  @Override
  public void visit(
      int version,
      int access,
      String name,
      String signature,
      String superName,
      String[] interfaces) {
    if (this.className == null) {
      this.className = name.replace('/', '.');
    }
    super.visit(version, access, name, signature, superName, interfaces);
  }

  @Override
  public MethodVisitor visitMethod(
      int access, String name, String descriptor, String signature, String[] exceptions) {
    MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);
    if (mv == null) {
      return null;
    }
    return new ExecutionCountMethodVisitor(mv, className);
  }

  /** MethodVisitor that injects execution counting code at each line. */
  private static class ExecutionCountMethodVisitor extends MethodVisitor {

    private final String className;

    public ExecutionCountMethodVisitor(MethodVisitor mv, String className) {
      super(Opcodes.ASM9, mv);
      this.className = className;
    }

    @Override
    public void visitLineNumber(int line, Label start) {
      // Inject call to ExecutionCountStore.recordExecution(className, lineNumber)
      // Push className
      mv.visitLdcInsn(className);

      // Push lineNumber
      mv.visitLdcInsn(line);

      // Call ExecutionCountStore.recordExecution(String, int)
      mv.visitMethodInsn(
          Opcodes.INVOKESTATIC,
          "io/github/sfkamath/jvmhotpath/ExecutionCountStore",
          "recordExecution",
          "(Ljava/lang/String;I)V",
          false);

      // Original line number instruction
      super.visitLineNumber(line, start);
    }
  }
}
