package io.github.sfkamath.jvmhotpath;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.*;

import java.lang.reflect.Method;

class ExecutionCountClassVisitorTest {

    @Test
    void testInstrumentationInjected() throws Exception {
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
        cw.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, "TestClass", null, "java/lang/Object", null);
        
        // Constructor
        MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
        mv.visitCode();
        Label l0 = new Label();
        mv.visitLabel(l0);
        mv.visitLineNumber(10, l0); // Line 10
        mv.visitVarInsn(Opcodes.ALOAD, 0);
        mv.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
        mv.visitInsn(Opcodes.RETURN);
        mv.visitMaxs(1, 1);
        mv.visitEnd();

        // Method with logic
        mv = cw.visitMethod(Opcodes.ACC_PUBLIC, "run", "()V", null, null);
        mv.visitCode();
        Label l1 = new Label();
        mv.visitLabel(l1);
        mv.visitLineNumber(20, l1); // Line 20
        mv.visitFieldInsn(Opcodes.GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;");
        mv.visitLdcInsn("Hello");
        mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/io/PrintStream", "println", "(Ljava/lang/String;)V", false);
        
        Label l2 = new Label();
        mv.visitLabel(l2);
        mv.visitLineNumber(21, l2); // Line 21
        mv.visitInsn(Opcodes.RETURN);
        mv.visitMaxs(2, 1);
        mv.visitEnd();
        cw.visitEnd();

        byte[] originalBytecode = cw.toByteArray();

        ClassReader cr = new ClassReader(originalBytecode);
        ClassWriter cwInstrumented = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
        ExecutionCountClassVisitor visitor = new ExecutionCountClassVisitor(cwInstrumented, "TestClass");
        cr.accept(visitor, 0);
        byte[] instrumentedBytecode = cwInstrumented.toByteArray();

        ExecutionCountStore.reset();
        
        ClassLoader loader = new ClassLoader() {
            @Override
            protected Class<?> findClass(String name) throws ClassNotFoundException {
                if ("TestClass".equals(name)) {
                    return defineClass(name, instrumentedBytecode, 0, instrumentedBytecode.length);
                }
                return super.findClass(name);
            }
        };
        Class<?> clazz = loader.loadClass("TestClass");
        
        // Use reflection to instantiate and run
        Object instance = clazz.getDeclaredConstructor().newInstance();
        Method runMethod = clazz.getMethod("run");
        runMethod.invoke(instance);

        // Constructor line 10
        assertEquals(1, ExecutionCountStore.getCount("TestClass", 10));
        // Run method lines 20 and 21
        assertEquals(1, ExecutionCountStore.getCount("TestClass", 20));
        assertEquals(1, ExecutionCountStore.getCount("TestClass", 21));
    }
}
