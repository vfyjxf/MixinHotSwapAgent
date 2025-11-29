package dev.vfyjxf.agent;

import java.lang.instrument.Instrumentation;

public class AgentEntryPoint {

    public static void premain(String arg, Instrumentation instrumentation) {
        MixinHotSwapAgent.instrumentation = instrumentation;
        System.setProperty("mixin.hotSwap", "true");
        instrumentation.addTransformer(Patches.createTransformer());
    }

    public static void agentmain(String arg, Instrumentation instrumentation) {
        MixinHotSwapAgent.instrumentation = instrumentation;
    }

}
