package io.github.sfkamath.jvmhotpath;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

class ExecutionCountClassVisitorTest {

    @Test
    void testVisitorWithLineNumbers() {
        ClassWriter cw = new ClassWriter(0);
        ExecutionCountClassVisitor visitor = new ExecutionCountClassVisitor(cw, "com/example/Test");
        
        visitor.visit(Opcodes.V11, Opcodes.ACC_PUBLIC, "com/example/Test", null, "java/lang/Object", null);
        
        MethodVisitor mv = visitor.visitMethod(Opcodes.ACC_PUBLIC, "run", "()V", null, null);
        mv.visitCode();
        Label l0 = new Label();
        mv.visitLabel(l0);
        mv.visitLineNumber(10, l0); // Should trigger instrumentation
        mv.visitInsn(Opcodes.RETURN);
        mv.visitMaxs(1, 1);
        mv.visitEnd();
        
        visitor.visitEnd();
        
        byte[] instrumented = cw.toByteArray();
        assertTrue(instrumented.length > 0);
        // The fact it didn't crash is good, but we could theoretically inspect bytecode here
    }

    @Test
    void testVisitorWithoutLineNumbers() {
        ClassWriter cw = new ClassWriter(0);
        ExecutionCountClassVisitor visitor = new ExecutionCountClassVisitor(cw, "com/example/NoLines");
        
        visitor.visit(Opcodes.V11, Opcodes.ACC_PUBLIC, "com/example/NoLines", null, "java/lang/Object", null);
        MethodVisitor mv = visitor.visitMethod(Opcodes.ACC_PUBLIC, "run", "()V", null, null);
        mv.visitCode();
        mv.visitInsn(Opcodes.RETURN);
        mv.visitMaxs(1, 1);
        mv.visitEnd();
        visitor.visitEnd();

        assertNotNull(cw.toByteArray());
    }
}