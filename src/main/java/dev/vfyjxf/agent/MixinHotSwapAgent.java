package dev.vfyjxf.agent;

import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.transformer.ext.IHotSwap;

import java.lang.instrument.Instrumentation;

public class MixinHotSwapAgent implements IHotSwap {

    private static Instrumentation instrumentation;

    public static void premain(Instrumentation instrumentation) {

    }

    public static void agentmain(Instrumentation instrumentation) {

    }

    @Override
    public void registerMixinClass(String name) {

    }

    @Override
    public void registerTargetClass(String name, ClassNode classNode) {

    }
}
