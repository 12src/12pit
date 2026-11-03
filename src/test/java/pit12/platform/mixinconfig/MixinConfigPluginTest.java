/*
 * This file is part of 12pit.
 *
 * Copyright (C) 2026 The 12pit Authors and contributors <https://github.com/12src/12pit>
 *
 * 12pit is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * 12pit is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with 12pit. If not, see <https://www.gnu.org/licenses/>.
 */
package pit12.platform.mixinconfig;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.lang.annotation.Annotation;
import java.lang.reflect.Proxy;
import java.util.List;
import org.junit.Test;
import org.spongepowered.asm.lib.Opcodes;
import org.spongepowered.asm.lib.tree.ClassNode;
import org.spongepowered.asm.lib.tree.MethodInsnNode;
import org.spongepowered.asm.lib.tree.MethodNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfig;
import org.spongepowered.asm.mixin.extensibility.IMixinErrorHandler.ErrorAction;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.util.Annotations;

public final class MixinConfigPluginTest {
    @Test
    public void aMergedHandlerWithoutAnInjectedCallRecordsAFailure() {
        Fixture fixture = new Fixture("MissingCall");
        fixture.addHandler(Inject.class, "onTick", "()V");
        fixture.plugin.postApply("example.Target", fixture.target,
                fixture.source.name.replace('/', '.'), fixture.info);
        assertTrue(MixinConfigPlugin.failedMixinNames().contains(fixture.name));
        assertEquals(fixture.failureCount + 1, MixinConfigPlugin.failedMixinCount());
    }

    @Test
    public void successfulInjectionsDoNotRecordFailuresAfterHandlersAreRenamed() {
        Fixture fixture = new Fixture("SuccessfulCalls");
        fixture.addHandler(Inject.class, "onTick", "()V");
        fixture.addHandler(Redirect.class, "redirectColor", "(I)I");
        fixture.addHandler(ModifyArg.class, "modifyColor", "(I)I");
        fixture.addHandler(ModifyVariable.class, "modifyDepth", "(Z)Z");
        fixture.addCall("runTick", fixture.target.name, "onTick", "()V");
        fixture.addCall("render", fixture.target.name, "redirectColor", "(I)I");
        fixture.addCall("render", fixture.target.name, "modifyColor", "(I)I");
        fixture.addCall("render", fixture.target.name, "modifyDepth", "(Z)Z");
        fixture.plugin.postApply("example.Target", fixture.target,
                fixture.source.name.replace('/', '.'), fixture.info);
        assertEquals(fixture.failures, MixinConfigPlugin.failedMixinNames());
        assertEquals(fixture.failureCount, MixinConfigPlugin.failedMixinCount());
    }

    @Test
    public void oneSuccessfulInjectorDoesNotHideAnotherMissingInjector() {
        Fixture fixture = new Fixture("PartlyAppliedMixin");
        fixture.addHandler(Inject.class, "onTick", "()V");
        fixture.addHandler(Redirect.class, "redirectColor", "(I)I");
        fixture.addCall("runTick", fixture.target.name, "onTick", "()V");
        fixture.plugin.postApply("example.Target", fixture.target,
                fixture.source.name.replace('/', '.'), fixture.info);
        assertTrue(MixinConfigPlugin.failedMixinNames().contains(fixture.name));
    }

    @Test
    public void optionalInjectorsAndAccessorsDoNotProduceFalseWarnings() {
        Fixture fixture = new Fixture("OptionalInjection");
        fixture.addHandler(Inject.class, "optionalHook", "()V", "require", 0);
        fixture.addHandler(Accessor.class, "getColor", "()I");
        fixture.addHandler(Invoker.class, "render", "()V");
        fixture.plugin.postApply("example.Target", fixture.target,
                fixture.source.name.replace('/', '.'), fixture.info);
        assertEquals(fixture.failures, MixinConfigPlugin.failedMixinNames());
        assertEquals(fixture.failureCount, MixinConfigPlugin.failedMixinCount());
    }

    @Test
    public void explicitRequiredAndExpectedCountsAreChecked() {
        for (String count : new String[] {"require", "expect"}) {
            Fixture fixture = new Fixture("MissingCount_" + count);
            fixture.addHandler(Redirect.class, "redirectColor", "(I)I", count, 2);
            fixture.addCall("render", fixture.target.name, "redirectColor", "(I)I");
            fixture.plugin.postApply("example.Target", fixture.target,
                    fixture.source.name.replace('/', '.'), fixture.info);
            assertTrue(MixinConfigPlugin.failedMixinNames().contains(fixture.name));
        }
    }

    @Test
    public void callsAcrossTargetMethodsCanMeetTheExpectedCount() {
        Fixture fixture = new Fixture("MultipleTargets");
        fixture.addHandler(Inject.class, "onClick", "()V", "expect", 2);
        fixture.addCall("clickMouse", fixture.target.name, "onClick", "()V");
        fixture.addCall("rightClickMouse", fixture.target.name, "onClick", "()V");
        fixture.plugin.postApply("example.Target", fixture.target,
                fixture.source.name.replace('/', '.'), fixture.info);
        assertEquals(fixture.failures, MixinConfigPlugin.failedMixinNames());
    }

    @Test
    public void unrelatedCallsDoNotHideAMissingInjection() {
        Fixture fixture = new Fixture("UnrelatedCalls");
        fixture.addHandler(ModifyArg.class, "modifyColor", "(I)I");
        fixture.addCall("render", "other/Target", "modifyColor", "(I)I");
        fixture.addCall("render", fixture.target.name, "modifyColor", "(F)F");
        fixture.plugin.postApply("example.Target", fixture.target,
                fixture.source.name.replace('/', '.'), fixture.info);
        assertTrue(MixinConfigPlugin.failedMixinNames().contains(fixture.name));
    }

    @Test
    public void repeatedReportsOfTheSameMixinCountAsOneFailure() {
        Fixture fixture = new Fixture("RepeatedFailure");
        fixture.addHandler(Inject.class, "onTick", "()V");
        fixture.plugin.postApply("example.Target", fixture.target,
                fixture.source.name.replace('/', '.'), fixture.info);
        fixture.plugin.postApply("example.Target", fixture.target,
                fixture.source.name.replace('/', '.'), fixture.info);
        fixture.plugin.onApplyError("example.Target", new IllegalStateException("Apply failed"),
                fixture.info, ErrorAction.WARN);
        assertEquals(fixture.failureCount + 1, MixinConfigPlugin.failedMixinCount());
        assertEquals(1,
                MixinConfigPlugin.failedMixinNames().stream().filter(fixture.name::equals).count());
    }

    @Test
    public void prepareErrorsAreRecordedWithoutChangingTheErrorAction() {
        for (ErrorAction action : ErrorAction.values()) {
            Fixture fixture = new Fixture("PrepareFailure_" + action);
            assertSame(action, fixture.plugin.onPrepareError(fixture.config,
                    new IllegalStateException("Prepare failed"), fixture.info, action));
            assertTrue(MixinConfigPlugin.failedMixinNames().contains(fixture.name));
        }
    }

    @Test
    public void applyErrorsAreRecordedWithoutChangingTheErrorAction() {
        for (ErrorAction action : ErrorAction.values()) {
            Fixture fixture = new Fixture("ApplyFailure_" + action);
            assertSame(action, fixture.plugin.onApplyError("example.Target",
                    new IllegalStateException("Apply failed"), fixture.info, action));
            assertTrue(MixinConfigPlugin.failedMixinNames().contains(fixture.name));
        }
    }

    private static final class Fixture {
        final String name;
        final ClassNode source = new ClassNode();
        final ClassNode target = new ClassNode();
        final MixinConfigPlugin plugin = new MixinConfigPlugin(
                (mixinClass, method) -> "prepared$" + method.name + method.desc);
        final List<String> failures = MixinConfigPlugin.failedMixinNames();
        final int failureCount = MixinConfigPlugin.failedMixinCount();
        final IMixinConfig config;
        final IMixinInfo info;

        Fixture(String name) {
            this.name = name;
            source.name = "example/mixins/" + name;
            source.superName = "java/lang/Object";
            target.name = "example/Target";
            config = (IMixinConfig) Proxy.newProxyInstance(IMixinConfig.class.getClassLoader(),
                    new Class<?>[] {IMixinConfig.class}, (proxy, method, arguments) -> {
                        if (method.getName().equals("getPlugin")) {
                            return plugin;
                        }
                        throw new AssertionError("Unexpected config call: " + method.getName());
                    });
            info = (IMixinInfo) Proxy.newProxyInstance(IMixinInfo.class.getClassLoader(),
                    new Class<?>[] {IMixinInfo.class}, (proxy, method, arguments) -> {
                        switch (method.getName()) {
                            case "getName":
                                return name;
                            case "getClassNode":
                                return source;
                            case "getConfig":
                                return config;
                            default:
                                throw new AssertionError(
                                        "Unexpected mixin call: " + method.getName());
                        }
                    });
        }

        void addHandler(Class<? extends Annotation> annotation, String name, String descriptor,
                Object... values) {
            MethodNode sourceMethod =
                    new MethodNode(Opcodes.ACC_PRIVATE, name, descriptor, null, null);
            Annotations.setVisible(sourceMethod, annotation, values);
            source.methods.add(sourceMethod);
            target.methods.add(new MethodNode(Opcodes.ACC_PRIVATE, "prepared$" + name, descriptor,
                    null, null));
        }

        void addCall(String methodName, String owner, String handler, String descriptor) {
            MethodNode method = null;
            for (MethodNode candidate : target.methods) {
                if (candidate.name.equals(methodName) && candidate.desc.equals("()V")) {
                    method = candidate;
                    break;
                }
            }
            if (method == null) {
                method = new MethodNode(Opcodes.ACC_PUBLIC, methodName, "()V", null, null);
                target.methods.add(method);
            }
            method.instructions.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, owner,
                    "prepared$" + handler, descriptor, false));
        }
    }
}
