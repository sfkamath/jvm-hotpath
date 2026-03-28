package io.github.sfkamath.jvmhotpath;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Handle;
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
    /**
     * Line number deferred from visitLineNumber, waiting to be emitted after any stackmap frame
     * that may follow at the same bytecode position. -1 means no counter is pending.
     *
     * <p>Background: javac can emit LineNumberTable entries at the same bytecode offset as a
     * StackMapTable frame, in either order. When EXPAND_FRAMES feeds visitLineNumber before
     * visitFrame, naively inserting counter bytecode in visitLineNumber displaces the frame by 9
     * bytes — causing a "Expecting a stackmap frame at branch target N" VerifyError. By deferring
     * emission until visitFrame (flushed after super.visitFrame) or the next real instruction, the
     * counter always lands after the frame regardless of order.
     */
    private int pendingLine = -1;

    public ExecutionCountMethodVisitor(MethodVisitor mv, String className) {
      super(Opcodes.ASM9, mv);
      this.className = className;
    }

    @Override
    public void visitLineNumber(int line, Label start) {
      pendingLine = line;
      super.visitLineNumber(line, start);
    }

    @Override
    public void visitFrame(
        int type, int numLocal, Object[] local, int numStack, Object[] stack) {
      super.visitFrame(type, numLocal, local, numStack, stack);
      // Emit pending counter AFTER the frame so it doesn't displace the frame's bytecode offset.
      emitPendingCounter();
    }

    private void emitPendingCounter() {
      if (pendingLine >= 0) {
        int line = pendingLine;
        pendingLine = -1; // clear before emitting to prevent re-entrancy
        mv.visitLdcInsn(className);
        mv.visitLdcInsn(line);
        mv.visitMethodInsn(
            Opcodes.INVOKESTATIC,
            "io/github/sfkamath/jvmhotpath/ExecutionCountStore",
            "recordExecution",
            "(Ljava/lang/String;I)V",
            false);
      }
    }

    // ── Real instruction overrides ─────────────────────────────────────────────
    // Each flushes the pending counter before delegating, ensuring the counter
    // always precedes the first instruction of each line.

    @Override
    public void visitInsn(int opcode) {
      emitPendingCounter();
      super.visitInsn(opcode);
    }

    @Override
    public void visitIntInsn(int opcode, int operand) {
      emitPendingCounter();
      super.visitIntInsn(opcode, operand);
    }

    @Override
    public void visitVarInsn(int opcode, int var) {
      emitPendingCounter();
      super.visitVarInsn(opcode, var);
    }

    @Override
    public void visitTypeInsn(int opcode, String type) {
      emitPendingCounter();
      super.visitTypeInsn(opcode, type);
    }

    @Override
    public void visitFieldInsn(int opcode, String owner, String name, String descriptor) {
      emitPendingCounter();
      super.visitFieldInsn(opcode, owner, name, descriptor);
    }

    @Override
    public void visitMethodInsn(
        int opcode, String owner, String name, String descriptor, boolean isInterface) {
      emitPendingCounter();
      super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
    }

    @Override
    public void visitInvokeDynamicInsn(
        String name,
        String descriptor,
        Handle bootstrapMethodHandle,
        Object... bootstrapMethodArguments) {
      emitPendingCounter();
      super.visitInvokeDynamicInsn(
          name, descriptor, bootstrapMethodHandle, bootstrapMethodArguments);
    }

    @Override
    public void visitJumpInsn(int opcode, Label label) {
      emitPendingCounter();
      super.visitJumpInsn(opcode, label);
    }

    @Override
    public void visitLdcInsn(Object value) {
      emitPendingCounter();
      super.visitLdcInsn(value);
    }

    @Override
    public void visitIincInsn(int var, int increment) {
      emitPendingCounter();
      super.visitIincInsn(var, increment);
    }

    @Override
    public void visitTableSwitchInsn(int min, int max, Label dflt, Label... labels) {
      emitPendingCounter();
      super.visitTableSwitchInsn(min, max, dflt, labels);
    }

    @Override
    public void visitLookupSwitchInsn(Label dflt, int[] keys, Label[] labels) {
      emitPendingCounter();
      super.visitLookupSwitchInsn(dflt, keys, labels);
    }

    @Override
    public void visitMultiANewArrayInsn(String descriptor, int numDimensions) {
      emitPendingCounter();
      super.visitMultiANewArrayInsn(descriptor, numDimensions);
    }
  }
}
