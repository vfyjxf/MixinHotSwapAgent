package dev.vfyjxf.agent;

import cpw.mods.bootstraplauncher.BootstrapLauncher;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.logging.Level;
import org.spongepowered.asm.mixin.transformer.IMixinTransformer;
import org.spongepowered.asm.mixin.transformer.ext.IHotSwap;
import org.spongepowered.asm.mixin.transformer.throwables.MixinReloadException;
import org.spongepowered.asm.transformers.MixinClassReader;
import org.spongepowered.asm.util.asm.ASM;
import org.spongepowered.tools.agent.MixinAgent;

import java.lang.instrument.ClassDefinition;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.Instrumentation;
import java.lang.reflect.Constructor;
import java.security.ProtectionDomain;
import java.util.List;

public class MixinHotSwapAgent implements IHotSwap {

    public static Instrumentation instrumentation;
    public static ClassLoader transformingLoader;

    static final MixinAgentClassLoader agentLoader = new MixinAgentClassLoader();

    private final Instrumentation memberInstrumentation;
    private final IMixinTransformer transformer;
    private final ClassLoader loader;

    public static void setTransformingLoader(ClassLoader loader) {
        try {
            Class<?> clazz = Class.forName("dev.vfyjxf.agent.MixinHotSwapAgent", true, BootstrapLauncher.class.getClassLoader());
            clazz.getField("transformingLoader").set(null, loader);
        } catch (ClassNotFoundException | IllegalAccessException | NoSuchFieldException e) {
            throw new RuntimeException(e);
        }
    }

    public static Object createInstance(Constructor<?> unused, Object[] parm) {
        try {
            Class<?> clazz = Class.forName("dev.vfyjxf.agent.MixinHotSwapAgent", true, BootstrapLauncher.class.getClassLoader());
            Instrumentation instrumentation = (Instrumentation) clazz.getField("instrumentation").get(null);
            ClassLoader loader = (ClassLoader) clazz.getField("transformingLoader").get(null);
            return new MixinHotSwapAgent(instrumentation, loader, (IMixinTransformer) parm[0]);
        } catch (ClassNotFoundException | NoSuchFieldException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    public MixinHotSwapAgent(Instrumentation instrumentation, ClassLoader loader, IMixinTransformer transformer) {
        this.memberInstrumentation = instrumentation;
        this.transformer = transformer;
        this.loader = loader;
        if (instrumentation != null) {
            instrumentation.addTransformer(new Transformer(), true);
        }
    }

    @Override
    public void registerMixinClass(String name) {
        agentLoader.addMixinClass(name);
    }

    @Override
    public void registerTargetClass(String name, ClassNode classNode) {
        agentLoader.addTargetClass(name, classNode);
    }

    class Transformer implements ClassFileTransformer {

        @Override
        public byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined, ProtectionDomain domain, byte[] classfileBuffer) {
            if (classBeingRedefined == null) {
                return null;
            }

            byte[] mixinBytecode = agentLoader.getFakeMixinBytecode(classBeingRedefined);
            if (mixinBytecode != null) {
                ClassNode classNode = new ClassNode(ASM.API_VERSION);
                ClassReader cr = new MixinClassReader(classfileBuffer, className);
                cr.accept(classNode, ClassReader.EXPAND_FRAMES);

                List<String> targets = this.reloadMixin(className, classNode);
                if (targets == null || !this.reApplyMixins(targets)) {
                    return MixinAgent.ERROR_BYTECODE;
                }
                return mixinBytecode;
            }

            try {
                MixinAgent.log(Level.INFO, "Redefining class {}", className);
                return transformer.transformClassBytes(null, className.replace('/', '.'), classfileBuffer);
            } catch (Throwable th) {
                MixinAgent.log(Level.ERROR, "Error while re-transforming class {}", className, th);
                return MixinAgent.ERROR_BYTECODE;
            }
        }

        private List<String> reloadMixin(String className, ClassNode classNode) {
            MixinAgent.log(Level.INFO, "Redefining mixin {}", className);
            try {
                return transformer.reload(className.replace('/', '.'), classNode);
            } catch (MixinReloadException e) {
                MixinAgent.log(Level.ERROR, "Mixin {} cannot be reloaded, needs a restart to be applied: {} ", e.getMixinInfo(), e.getMessage());
            } catch (Throwable th) {
                // catch everything as otherwise it is ignored
                MixinAgent.log(Level.ERROR, "Error while finding targets for mixin {}", className, th);
            }
            return null;
        }

        private boolean reApplyMixins(List<String> targets) {
            for (String target : targets) {
                String targetName = target.replace('/', '.');
                MixinAgent.log(Level.DEBUG, "Re-transforming target class {}", target);
                try {
                    Class<?> targetClass = findClass(targetName);
                    byte[] targetBytecode = agentLoader.getOriginalTargetBytecode(targetName);
                    if (targetBytecode == null) {
                        MixinAgent.log(Level.ERROR, "Target class {} bytecode is not registered", targetName);
                        return false;
                    }
                    targetBytecode = transformer.transformClassBytes(null, targetName, targetBytecode);
                    memberInstrumentation.redefineClasses(new ClassDefinition(targetClass, targetBytecode));
                } catch (Throwable th) {
                    MixinAgent.log(Level.ERROR, "Error while re-transforming target class {}", target, th);
                    return false;
                }
            }
            return true;
        }

        private Class<?> findClass(String name) throws ClassNotFoundException {
            if (loader != null) {
                return Class.forName(name, true, loader);
            }
            return Class.forName(name);
        }
    }

}
