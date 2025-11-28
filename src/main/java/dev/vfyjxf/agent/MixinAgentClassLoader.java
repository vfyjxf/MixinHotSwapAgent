package dev.vfyjxf.agent;

import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.logging.Level;
import org.spongepowered.asm.mixin.MixinEnvironment;
import org.spongepowered.asm.service.MixinService;
import org.spongepowered.asm.service.ServiceNotAvailableError;
import org.spongepowered.asm.util.Constants;

import java.util.HashMap;
import java.util.Map;

/**
 * Copied from org.spongepowered.tools.agent.MixinAgentClassLoader
 * Class loader that is used to load fake mixin classes so that they can be
 * re-defined.
 */
class MixinAgentClassLoader extends ClassLoader {

    /**
     * Mapping of mixin mixin classes to their fake classes
     */
    private Map<Class<?>, byte[]> mixins = new HashMap<>();

    /**
     * Mapping that keep track of bytecode for classes that are targeted by
     * mixins
     */
    private Map<String, byte[]> targets = new HashMap<>();

    /**
     * Add a fake mixin class
     *
     * @param name Name of the fake class
     */
    void addMixinClass(String name) {
        log(Level.DEBUG, "Mixin class {} added to class loader", name);
        try {
            byte[] bytes = this.materialise(name);
            Class<?> clazz = this.defineClass(name, bytes, 0, bytes.length);
            // apparently the class needs to be instantiated at least once
            // to be including in list returned by allClasses() method in jdi api
            clazz.getDeclaredConstructor().newInstance();
            this.mixins.put(clazz, bytes);
        } catch (Throwable e) {
            log(Level.ERROR, "Catching {}", e);
        }
    }

    /**
     * Registers the bytecode for a class targeted by a mixin
     *
     * @param name      Name of the target clas
     * @param classNode ASM tree node of the target class
     */
    void addTargetClass(String name, ClassNode classNode) {
        synchronized (this.targets) {
            if (this.targets.containsKey(name)) {
                return;
            }
            try {
                ClassWriter cw = new ClassWriter(0);
                classNode.accept(cw);
                this.targets.put(name, cw.toByteArray());
            } catch (Exception ex) {
                log(Level.ERROR, "Error storing original class bytecode for {} in mixin hotswap agent. {}: {}",
                    name, ex.getClass().getName(), ex.getMessage());
                log(Level.DEBUG, ex.toString());
            }
        }
    }

    /**
     * Gets the bytecode for a fake mixin class
     *
     * @param clazz Mixin class
     * @return Bytecode of the fake mixin class
     */
    byte[] getFakeMixinBytecode(Class<?> clazz) {
        return this.mixins.get(clazz);
    }

    /**
     * Gets the original bytecode for a target class
     *
     * @param name Name of the target class
     * @return Original bytecode
     */
    byte[] getOriginalTargetBytecode(String name) {
        synchronized (this.targets) {
            return this.targets.get(name);
        }
    }

    /**
     * Generates the simplest possible class that is instantiable
     *
     * @param name Name of the generated class
     * @return Bytecode of the generated class
     */
    private byte[] materialise(String name) {
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
        cw.visit(MixinEnvironment.getCompatibilityLevel().getClassVersion(), Opcodes.ACC_PUBLIC, name.replace('.', '/'), null,
            Type.getInternalName(Object.class), null);

        // create init method
        MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PUBLIC, Constants.CTOR, "()V", null, null);
        mv.visitCode();
        mv.visitVarInsn(Opcodes.ALOAD, 0);
        mv.visitMethodInsn(Opcodes.INVOKESPECIAL, Type.getInternalName(Object.class), Constants.CTOR, "()V", false);
        mv.visitInsn(Opcodes.RETURN);
        mv.visitMaxs(1, 1);
        mv.visitEnd();

        cw.visitEnd();
        return cw.toByteArray();
    }

    /**
     * Wrapper for logger since we can't access the log abstraction in premain
     *
     * @param level   the logging level
     * @param message the message to log
     * @param params  parameters to the message
     */
    static void log(Level level, String message, Object... params) {
        try {
            MixinService.getService().getLogger("mixin.agent").log(level, message, params);
        } catch (ServiceNotAvailableError err) {
            System.err.printf("MixinAgent: %s: %s", level.name(), String.format(message, params));
        }
    }

}

