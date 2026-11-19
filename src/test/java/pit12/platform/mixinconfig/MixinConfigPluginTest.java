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

import static java.util.Collections.singleton;
import static java.util.Collections.singletonList;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.lang.annotation.Annotation;
import java.util.List;
import java.util.Set;
import org.junit.Test;
import org.spongepowered.asm.lib.ClassWriter;
import org.spongepowered.asm.lib.Opcodes;
import org.spongepowered.asm.lib.tree.ClassNode;
import org.spongepowered.asm.lib.tree.MethodInsnNode;
import org.spongepowered.asm.lib.tree.MethodNode;
import org.spongepowered.asm.mixin.MixinEnvironment;
import org.spongepowered.asm.mixin.MixinEnvironment.Phase;
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
import pit12.platform.mixinconfig.MixinConfigPlugin.FailureLog;

public final class MixinConfigPluginTest {
    @Test
    public void aMergedHandlerWithoutAnInjectedCallRecordsAFailure() {
        Fixture fixture = new Fixture();
        fixture.addHandler(Inject.class, "onTick", "()V");
        fixture.plugin.postApply("example.Target", fixture.target, fixture.getClassName(), fixture);
        assertEquals(singletonList(fixture.getName()), fixture.failures.names());
        assertEquals(1, fixture.failures.count());
    }

    @Test
    public void successfulInjectionsDoNotRecordFailuresAfterHandlersAreRenamed() {
        Fixture fixture = new Fixture();
        fixture.addHandler(Inject.class, "onTick", "()V");
        fixture.addHandler(Redirect.class, "redirectColor", "(I)I");
        fixture.addHandler(ModifyArg.class, "modifyColor", "(I)I");
        fixture.addHandler(ModifyVariable.class, "modifyDepth", "(Z)Z");
        fixture.addCall("runTick", fixture.target.name, "onTick", "()V");
        fixture.addCall("render", fixture.target.name, "redirectColor", "(I)I");
        fixture.addCall("render", fixture.target.name, "modifyColor", "(I)I");
        fixture.addCall("render", fixture.target.name, "modifyDepth", "(Z)Z");
        fixture.plugin.postApply("example.Target", fixture.target, fixture.getClassName(), fixture);
        assertTrue(fixture.failures.names().isEmpty());
        assertEquals(0, fixture.failures.count());
    }

    @Test
    public void oneSuccessfulInjectorDoesNotHideAnotherMissingInjector() {
        Fixture fixture = new Fixture();
        fixture.addHandler(Inject.class, "onTick", "()V");
        fixture.addHandler(Redirect.class, "redirectColor", "(I)I");
        fixture.addCall("runTick", fixture.target.name, "onTick", "()V");
        fixture.plugin.postApply("example.Target", fixture.target, fixture.getClassName(), fixture);
        assertEquals(singletonList(fixture.getName()), fixture.failures.names());
    }

    @Test
    public void optionalInjectorsAndAccessorsDoNotProduceFalseWarnings() {
        Fixture fixture = new Fixture();
        fixture.addHandler(Inject.class, "optionalHook", "()V", "require", 0);
        fixture.addHandler(Accessor.class, "getColor", "()I");
        fixture.addHandler(Invoker.class, "render", "()V");
        fixture.plugin.postApply("example.Target", fixture.target, fixture.getClassName(), fixture);
        assertTrue(fixture.failures.names().isEmpty());
    }

    @Test
    public void explicitRequiredAndExpectedCountsAreChecked() {
        for (String count : new String[] {"require", "expect"}) {
            Fixture fixture = new Fixture();
            fixture.addHandler(Redirect.class, "redirectColor", "(I)I", count, 2);
            fixture.addCall("render", fixture.target.name, "redirectColor", "(I)I");
            fixture.plugin.postApply("example.Target", fixture.target, fixture.getClassName(),
                    fixture);
            assertEquals(singletonList(fixture.getName()), fixture.failures.names());
        }
    }

    @Test
    public void callsAcrossTargetMethodsCanMeetTheExpectedCount() {
        Fixture fixture = new Fixture();
        fixture.addHandler(Inject.class, "onClick", "()V", "expect", 2);
        fixture.addCall("clickMouse", fixture.target.name, "onClick", "()V");
        fixture.addCall("rightClickMouse", fixture.target.name, "onClick", "()V");
        fixture.plugin.postApply("example.Target", fixture.target, fixture.getClassName(), fixture);
        assertTrue(fixture.failures.names().isEmpty());
    }

    @Test
    public void unrelatedCallsDoNotHideAMissingInjection() {
        Fixture fixture = new Fixture();
        fixture.addHandler(ModifyArg.class, "modifyColor", "(I)I");
        fixture.addCall("render", "other/Target", "modifyColor", "(I)I");
        fixture.addCall("render", fixture.target.name, "modifyColor", "(F)F");
        fixture.plugin.postApply("example.Target", fixture.target, fixture.getClassName(), fixture);
        assertEquals(singletonList(fixture.getName()), fixture.failures.names());
    }

    @Test
    public void repeatedReportsOfTheSameMixinCountAsOneFailure() {
        Fixture fixture = new Fixture();
        fixture.addHandler(Inject.class, "onTick", "()V");
        fixture.plugin.postApply("example.Target", fixture.target, fixture.getClassName(), fixture);
        fixture.plugin.postApply("example.Target", fixture.target, fixture.getClassName(), fixture);
        fixture.plugin.onApplyError("example.Target", new IllegalStateException("Apply failed"),
                fixture, ErrorAction.WARN);
        assertEquals(1, fixture.failures.count());
        assertEquals(singletonList(fixture.getName()), fixture.failures.names());
    }

    @Test
    public void prepareErrorsAreRecordedWithoutChangingTheErrorAction() {
        for (ErrorAction action : ErrorAction.values()) {
            Fixture fixture = new Fixture();
            assertSame(action, fixture.plugin.onPrepareError(fixture.config,
                    new IllegalStateException("Prepare failed"), fixture, action));
            assertEquals(singletonList(fixture.getName()), fixture.failures.names());
        }
    }

    @Test
    public void applyErrorsAreRecordedWithoutChangingTheErrorAction() {
        for (ErrorAction action : ErrorAction.values()) {
            Fixture fixture = new Fixture();
            assertSame(action, fixture.plugin.onApplyError("example.Target",
                    new IllegalStateException("Apply failed"), fixture, action));
            assertEquals(singletonList(fixture.getName()), fixture.failures.names());
        }
    }

    private static final class Fixture implements IMixinInfo {
        final ClassNode source = new ClassNode();
        final ClassNode target = new ClassNode();
        final FailureLog failures = new FailureLog();
        final MixinConfigPlugin plugin = new MixinConfigPlugin(
                (mixinClass, method) -> "prepared$" + method.name + method.desc, failures);
        final IMixinConfig config = new IMixinConfig() {
            @Override
            public MixinEnvironment getEnvironment() {
                return null;
            }

            @Override
            public String getName() {
                return "example.mixins.json";
            }

            @Override
            public String getMixinPackage() {
                return "example.mixins";
            }

            @Override
            public int getPriority() {
                return DEFAULT_PRIORITY;
            }

            @Override
            public MixinConfigPlugin getPlugin() {
                return plugin;
            }

            @Override
            public boolean isRequired() {
                return true;
            }

            @Override
            public Set<String> getTargets() {
                return singleton(target.name.replace('/', '.'));
            }
        };

        Fixture() {
            source.version = Opcodes.V1_8;
            source.name = "example/mixins/" + getName();
            source.superName = "java/lang/Object";
            target.name = "example/Target";
        }

        @Override
        public IMixinConfig getConfig() {
            return config;
        }

        @Override
        public String getName() {
            return "TestMixin";
        }

        @Override
        public String getClassName() {
            return source.name.replace('/', '.');
        }

        @Override
        public String getClassRef() {
            return source.name;
        }

        @Override
        public byte[] getClassBytes() {
            ClassWriter writer = new ClassWriter(0);
            source.accept(writer);
            return writer.toByteArray();
        }

        @Override
        public boolean isDetachedSuper() {
            return false;
        }

        @Override
        public ClassNode getClassNode(int flags) {
            return source;
        }

        @Override
        public List<String> getTargetClasses() {
            return singletonList(target.name);
        }

        @Override
        public int getPriority() {
            return IMixinConfig.DEFAULT_PRIORITY;
        }

        @Override
        public Phase getPhase() {
            return Phase.DEFAULT;
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
