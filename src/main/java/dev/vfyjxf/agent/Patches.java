package dev.vfyjxf.agent;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

import java.lang.instrument.ClassFileTransformer;
import java.security.ProtectionDomain;

public class Patches {

    public static ClassFileTransformer createTransformer() {
        return new PatchTransformer();
    }

    private static class PatchTransformer implements ClassFileTransformer {
        @Override
        public byte[] transform(
            ClassLoader loader, String className,
            Class<?> classBeingRedefined, ProtectionDomain protectionDomain, byte[] classfileBuffer
        ) {
            if (classBeingRedefined != null) return null;

            if (className.equals("cpw/mods/modlauncher/Launcher")) {
                ClassNode node = getNode(classfileBuffer);
                for (MethodNode method : node.methods) {
                    if (!method.name.equals("run")) continue;
                    AbstractInsnNode target = null;
                    for (var ins : method.instructions) {
                        if (!(ins instanceof MethodInsnNode mis)) continue;
                        if (
                            mis.getOpcode() == Opcodes.INVOKEVIRTUAL &&
                            mis.owner.equals("java/lang/Thread") &&
                            mis.name.equals("setContextClassLoader")
                            && mis.desc.equals("(Ljava/lang/ClassLoader;)V")
                        ) {
                            target = ins;
                            break;
                        }
                    }
                    if (target == null) return null;
                    InsnList ops = new InsnList();
                    ops.add(new VarInsnNode(Opcodes.ALOAD, 0));
                    ops.add(new FieldInsnNode(Opcodes.GETFIELD, "cpw/mods/modlauncher/Launcher", "classLoader", "Lcpw/mods/modlauncher/TransformingClassLoader;"));
                    ops.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "dev/vfyjxf/agent/MixinHotSwapAgent", "setTransformingLoader", "(Ljava/lang/ClassLoader;)V"));
                    method.instructions.insert(target, ops);
                    ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
                    node.accept(cw);
                    return cw.toByteArray();
                }
            }
            if (className.equals("org/spongepowered/asm/mixin/transformer/MixinTransformer")) {
                ClassNode node = getNode(classfileBuffer);
                for (MethodNode method : node.methods) {
                    if (!method.name.equals("initHotSwapper")) continue;
                    MethodInsnNode target = null;
                    for (var ins : method.instructions) {
                        if (!(ins instanceof MethodInsnNode mis)) continue;
                        if (mis.getOpcode() == Opcodes.INVOKEVIRTUAL &&
                            mis.owner.equals("java/lang/reflect/Constructor") &&
                            mis.name.equals("newInstance") &&
                            mis.desc.equals("([Ljava/lang/Object;)Ljava/lang/Object;")
                        ) {
                            target = mis;
                            break;
                        }
                    }
                    if (target == null) return null;
                    target.setOpcode(Opcodes.INVOKESTATIC);
                    target.owner = "dev/vfyjxf/agent/MixinHotSwapAgent";
                    target.name = "createInstance";
                    target.desc = "(Ljava/lang/reflect/Constructor;[Ljava/lang/Object;)Ljava/lang/Object;";
                    ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
                    node.accept(cw);
                    return cw.toByteArray();
                }
            }
            return null;
        }
    }


    private static ClassNode getNode(byte[] bytes) {
        ClassNode node = new ClassNode();
        new ClassReader(bytes).accept(node, 0);
        return node;
    }
}
