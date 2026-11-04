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
package pit12.feature.render.focus;

import java.nio.IntBuffer;
import net.minecraft.client.renderer.OpenGlHelper;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.ARBFramebufferObject;
import org.lwjgl.opengl.ARBShaderObjects;
import org.lwjgl.opengl.ContextCapabilities;
import org.lwjgl.opengl.EXTFramebufferBlit;
import org.lwjgl.opengl.EXTFramebufferMultisample;
import org.lwjgl.opengl.EXTFramebufferObject;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL21;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GLContext;

final class FocusDepthBuffer {
    private final ContextCapabilities capabilities = GLContext.getCapabilities();
    private final boolean blit = capabilities.OpenGL30 || capabilities.GL_ARB_framebuffer_object
            || capabilities.GL_EXT_framebuffer_blit;
    private final IntBuffer viewport = BufferUtils.createIntBuffer(16);
    private IntBuffer pixels;
    private int framebuffer;
    private int renderbuffer;
    private int width;
    private int height;
    private int format;
    private int samples;
    private int source;
    private int sourceType;
    private int sourceObject;
    private int sourceLevel;
    private int sourceWidth;
    private int sourceHeight;
    private int stencilLevel;
    private int x;
    private int y;
    private boolean swapStencil;
    private boolean swapped;
    private boolean captured;

    void capture() {
        if (captured)
            return;
        viewport.clear();
        GL11.glGetInteger(GL11.GL_VIEWPORT, viewport);
        x = viewport.get(0);
        y = viewport.get(1);
        int nextWidth = viewport.get(2);
        int nextHeight = viewport.get(3);
        if (blit) {
            source = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
            int previousRead = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
            int previousRenderbuffer = GL11.glGetInteger(GL30.GL_RENDERBUFFER_BINDING);
            boolean scissor = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);
            try {
                sourceWidth = nextWidth;
                sourceHeight = nextHeight;
                int nextFormat = depthFormat();
                int nextSamples = GL11.glGetInteger(GL13.GL_SAMPLES);
                if (framebuffer == 0) {
                    framebuffer = OpenGlHelper.glGenFramebuffers();
                    renderbuffer = OpenGlHelper.glGenRenderbuffers();
                }
                OpenGlHelper.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, framebuffer);
                if (width != sourceWidth || height != sourceHeight || format != nextFormat
                        || samples != nextSamples) {
                    OpenGlHelper.glBindRenderbuffer(GL30.GL_RENDERBUFFER, renderbuffer);
                    if (nextSamples == 0) {
                        OpenGlHelper.glRenderbufferStorage(GL30.GL_RENDERBUFFER, nextFormat,
                                sourceWidth, sourceHeight);
                    } else if (capabilities.OpenGL30) {
                        GL30.glRenderbufferStorageMultisample(GL30.GL_RENDERBUFFER, nextSamples,
                                nextFormat, sourceWidth, sourceHeight);
                    } else if (capabilities.GL_ARB_framebuffer_object) {
                        ARBFramebufferObject.glRenderbufferStorageMultisample(GL30.GL_RENDERBUFFER,
                                nextSamples, nextFormat, sourceWidth, sourceHeight);
                    } else {
                        EXTFramebufferMultisample.glRenderbufferStorageMultisampleEXT(
                                GL30.GL_RENDERBUFFER, nextSamples, nextFormat, sourceWidth,
                                sourceHeight);
                    }
                    OpenGlHelper.glFramebufferRenderbuffer(GL30.GL_DRAW_FRAMEBUFFER,
                            GL30.GL_DEPTH_ATTACHMENT, GL30.GL_RENDERBUFFER, renderbuffer);
                    boolean packed = nextFormat == GL30.GL_DEPTH24_STENCIL8
                            || nextFormat == GL30.GL_DEPTH32F_STENCIL8;
                    OpenGlHelper.glFramebufferRenderbuffer(GL30.GL_DRAW_FRAMEBUFFER,
                            GL30.GL_STENCIL_ATTACHMENT, GL30.GL_RENDERBUFFER,
                            packed ? renderbuffer : 0);
                    GL11.glDrawBuffer(GL11.GL_NONE);
                    OpenGlHelper.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, framebuffer);
                    GL11.glReadBuffer(GL11.GL_NONE);
                    int status = OpenGlHelper.glCheckFramebufferStatus(GL30.GL_DRAW_FRAMEBUFFER);
                    if (status != OpenGlHelper.GL_FRAMEBUFFER_COMPLETE)
                        throw new IllegalStateException("Could not save Focus depth: " + status);
                    width = sourceWidth;
                    height = sourceHeight;
                    format = nextFormat;
                    samples = nextSamples;
                }
                OpenGlHelper.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, source);
                GL11.glDisable(GL11.GL_SCISSOR_TEST);
                int targetX = source == 0 ? 0 : x;
                int targetY = source == 0 ? 0 : y;
                copy(x, y, x + nextWidth, y + nextHeight, targetX, targetY, targetX + nextWidth,
                        targetY + nextHeight);
                if (source != 0) {
                    OpenGlHelper.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, source);
                    captured = true;
                    swapped = true;
                    OpenGlHelper.glFramebufferRenderbuffer(GL30.GL_DRAW_FRAMEBUFFER,
                            GL30.GL_DEPTH_ATTACHMENT, GL30.GL_RENDERBUFFER, renderbuffer);
                    if (swapStencil)
                        OpenGlHelper.glFramebufferRenderbuffer(GL30.GL_DRAW_FRAMEBUFFER,
                                GL30.GL_STENCIL_ATTACHMENT, GL30.GL_RENDERBUFFER, renderbuffer);
                }
            } finally {
                OpenGlHelper.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, previousRead);
                OpenGlHelper.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, source);
                OpenGlHelper.glBindRenderbuffer(GL30.GL_RENDERBUFFER, previousRenderbuffer);
                if (scissor)
                    GL11.glEnable(GL11.GL_SCISSOR_TEST);
            }
        } else {
            width = nextWidth;
            height = nextHeight;
            if (pixels == null || pixels.capacity() < width * height)
                pixels = BufferUtils.createIntBuffer(width * height);
            pixels.clear();
            GL11.glPushAttrib(GL11.GL_PIXEL_MODE_BIT);
            GL11.glPushClientAttrib(GL11.GL_CLIENT_PIXEL_STORE_BIT);
            try {
                pixelStore(true);
                GL11.glPixelTransferf(GL11.GL_DEPTH_SCALE, 1.0F);
                GL11.glPixelTransferf(GL11.GL_DEPTH_BIAS, 0.0F);
                GL11.glReadPixels(x, y, width, height, GL11.GL_DEPTH_COMPONENT,
                        GL11.GL_UNSIGNED_INT, pixels);
            } finally {
                GL11.glPopClientAttrib();
                GL11.glPopAttrib();
            }
        }
        captured = true;
    }

    void restore() {
        if (!captured)
            return;
        captured = false;
        if (swapped) {
            swapped = false;
            int previousDraw = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
            try {
                OpenGlHelper.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, source);
                attach(GL30.GL_DEPTH_ATTACHMENT, sourceLevel);
                if (swapStencil)
                    attach(GL30.GL_STENCIL_ATTACHMENT, stencilLevel);
            } finally {
                OpenGlHelper.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, previousDraw);
            }
        } else if (blit) {
            int previousRead = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
            int previousDraw = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
            boolean scissor = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);
            try {
                OpenGlHelper.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, framebuffer);
                OpenGlHelper.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, source);
                GL11.glDisable(GL11.GL_SCISSOR_TEST);
                copy(0, 0, width, height, x, y, x + width, y + height);
            } finally {
                OpenGlHelper.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, previousRead);
                OpenGlHelper.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, previousDraw);
                if (scissor)
                    GL11.glEnable(GL11.GL_SCISSOR_TEST);
            }
        } else {
            // Minecraft also supports EXT framebuffers without the optional blit extension.
            int program = 0;
            if (OpenGlHelper.shadersSupported) {
                program = capabilities.OpenGL20 ? GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM)
                        : ARBShaderObjects.glGetHandleARB(ARBShaderObjects.GL_PROGRAM_OBJECT_ARB);
                OpenGlHelper.glUseProgram(0);
            }
            GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
            GL11.glPushClientAttrib(GL11.GL_CLIENT_PIXEL_STORE_BIT);
            GL11.glMatrixMode(GL11.GL_PROJECTION);
            GL11.glPushMatrix();
            GL11.glLoadIdentity();
            GL11.glMatrixMode(GL11.GL_MODELVIEW);
            GL11.glPushMatrix();
            GL11.glLoadIdentity();
            try {
                pixelStore(false);
                GL11.glDisable(GL11.GL_SCISSOR_TEST);
                GL11.glDisable(GL11.GL_STENCIL_TEST);
                GL11.glDisable(GL11.GL_ALPHA_TEST);
                for (int plane = 0; plane < 6; plane++)
                    GL11.glDisable(GL11.GL_CLIP_PLANE0 + plane);
                GL11.glEnable(GL11.GL_DEPTH_TEST);
                GL11.glDepthMask(true);
                GL11.glDepthFunc(GL11.GL_ALWAYS);
                GL11.glColorMask(false, false, false, false);
                GL11.glPixelZoom(1.0F, 1.0F);
                GL11.glPixelTransferf(GL11.GL_DEPTH_SCALE, 1.0F);
                GL11.glPixelTransferf(GL11.GL_DEPTH_BIAS, 0.0F);
                GL11.glRasterPos2f(-1.0F, -1.0F);
                GL11.glDrawPixels(width, height, GL11.GL_DEPTH_COMPONENT, GL11.GL_UNSIGNED_INT,
                        pixels);
            } finally {
                GL11.glPopMatrix();
                GL11.glMatrixMode(GL11.GL_PROJECTION);
                GL11.glPopMatrix();
                GL11.glPopClientAttrib();
                GL11.glPopAttrib();
                if (OpenGlHelper.shadersSupported)
                    OpenGlHelper.glUseProgram(program);
            }
        }
    }

    void delete() {
        if (framebuffer != 0)
            OpenGlHelper.glDeleteFramebuffers(framebuffer);
        if (renderbuffer != 0)
            OpenGlHelper.glDeleteRenderbuffers(renderbuffer);
    }

    private int depthFormat() {
        swapStencil = false;
        if (source == 0) {
            int bits = GL11.glGetInteger(GL11.GL_DEPTH_BITS);
            if (GL11.glGetInteger(GL11.GL_STENCIL_BITS) > 0)
                return GL30.GL_DEPTH24_STENCIL8;
            return bits == 16 ? GL14.GL_DEPTH_COMPONENT16
                    : bits == 24 ? GL14.GL_DEPTH_COMPONENT24 : GL14.GL_DEPTH_COMPONENT32;
        }
        sourceType =
                attachment(GL30.GL_DEPTH_ATTACHMENT, GL30.GL_FRAMEBUFFER_ATTACHMENT_OBJECT_TYPE);
        sourceObject =
                attachment(GL30.GL_DEPTH_ATTACHMENT, GL30.GL_FRAMEBUFFER_ATTACHMENT_OBJECT_NAME);
        swapStencil = attachment(GL30.GL_STENCIL_ATTACHMENT,
                GL30.GL_FRAMEBUFFER_ATTACHMENT_OBJECT_TYPE) == sourceType
                && attachment(GL30.GL_STENCIL_ATTACHMENT,
                        GL30.GL_FRAMEBUFFER_ATTACHMENT_OBJECT_NAME) == sourceObject;
        if (sourceType == GL30.GL_RENDERBUFFER) {
            OpenGlHelper.glBindRenderbuffer(GL30.GL_RENDERBUFFER, sourceObject);
            sourceWidth = renderbufferParameter(GL30.GL_RENDERBUFFER_WIDTH);
            sourceHeight = renderbufferParameter(GL30.GL_RENDERBUFFER_HEIGHT);
            return renderbufferParameter(GL30.GL_RENDERBUFFER_INTERNAL_FORMAT);
        }
        int texture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, sourceObject);
        try {
            sourceLevel = attachment(GL30.GL_DEPTH_ATTACHMENT,
                    GL30.GL_FRAMEBUFFER_ATTACHMENT_TEXTURE_LEVEL);
            if (swapStencil)
                stencilLevel = attachment(GL30.GL_STENCIL_ATTACHMENT,
                        GL30.GL_FRAMEBUFFER_ATTACHMENT_TEXTURE_LEVEL);
            sourceWidth = GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, sourceLevel,
                    GL11.GL_TEXTURE_WIDTH);
            sourceHeight = GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, sourceLevel,
                    GL11.GL_TEXTURE_HEIGHT);
            int internalFormat = GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, sourceLevel,
                    GL11.GL_TEXTURE_INTERNAL_FORMAT);
            // OptiFine requests an unsized depth texture, but renderbuffers need a sized format.
            if (internalFormat == GL11.GL_DEPTH_COMPONENT) {
                int bits = GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, sourceLevel,
                        GL14.GL_TEXTURE_DEPTH_SIZE);
                return bits == 16 ? GL14.GL_DEPTH_COMPONENT16
                        : bits == 24 ? GL14.GL_DEPTH_COMPONENT24 : GL14.GL_DEPTH_COMPONENT32;
            }
            return internalFormat;
        } finally {
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
        }
    }

    private int renderbufferParameter(int parameter) {
        if (capabilities.OpenGL30)
            return GL30.glGetRenderbufferParameteri(GL30.GL_RENDERBUFFER, parameter);
        if (capabilities.GL_ARB_framebuffer_object)
            return ARBFramebufferObject.glGetRenderbufferParameteri(GL30.GL_RENDERBUFFER,
                    parameter);
        return EXTFramebufferObject.glGetRenderbufferParameteriEXT(GL30.GL_RENDERBUFFER, parameter);
    }

    private void attach(int attachment, int level) {
        if (sourceType == GL30.GL_RENDERBUFFER)
            OpenGlHelper.glFramebufferRenderbuffer(GL30.GL_DRAW_FRAMEBUFFER, attachment,
                    GL30.GL_RENDERBUFFER, sourceObject);
        else
            OpenGlHelper.glFramebufferTexture2D(GL30.GL_DRAW_FRAMEBUFFER, attachment,
                    GL11.GL_TEXTURE_2D, sourceObject, level);
    }

    private int attachment(int attachment, int parameter) {
        if (capabilities.OpenGL30)
            return GL30.glGetFramebufferAttachmentParameteri(GL30.GL_DRAW_FRAMEBUFFER, attachment,
                    parameter);
        if (capabilities.GL_ARB_framebuffer_object)
            return ARBFramebufferObject.glGetFramebufferAttachmentParameteri(
                    GL30.GL_DRAW_FRAMEBUFFER, attachment, parameter);
        return EXTFramebufferObject.glGetFramebufferAttachmentParameteriEXT(
                GL30.GL_DRAW_FRAMEBUFFER, attachment, parameter);
    }

    private void copy(int x0, int y0, int x1, int y1, int targetX0, int targetY0, int targetX1,
            int targetY1) {
        int mask = GL11.GL_DEPTH_BUFFER_BIT | (swapStencil ? GL11.GL_STENCIL_BUFFER_BIT : 0);
        if (capabilities.OpenGL30) {
            GL30.glBlitFramebuffer(x0, y0, x1, y1, targetX0, targetY0, targetX1, targetY1, mask,
                    GL11.GL_NEAREST);
        } else if (capabilities.GL_ARB_framebuffer_object) {
            ARBFramebufferObject.glBlitFramebuffer(x0, y0, x1, y1, targetX0, targetY0, targetX1,
                    targetY1, mask, GL11.GL_NEAREST);
        } else {
            EXTFramebufferBlit.glBlitFramebufferEXT(x0, y0, x1, y1, targetX0, targetY0, targetX1,
                    targetY1, mask, GL11.GL_NEAREST);
        }
    }

    private void pixelStore(boolean pack) {
        if (capabilities.OpenGL21 || capabilities.GL_ARB_pixel_buffer_object)
            OpenGlHelper.glBindBuffer(
                    pack ? GL21.GL_PIXEL_PACK_BUFFER : GL21.GL_PIXEL_UNPACK_BUFFER, 0);
        GL11.glPixelStorei(pack ? GL11.GL_PACK_ROW_LENGTH : GL11.GL_UNPACK_ROW_LENGTH, 0);
        GL11.glPixelStorei(pack ? GL11.GL_PACK_SKIP_ROWS : GL11.GL_UNPACK_SKIP_ROWS, 0);
        GL11.glPixelStorei(pack ? GL11.GL_PACK_SKIP_PIXELS : GL11.GL_UNPACK_SKIP_PIXELS, 0);
        GL11.glPixelStorei(pack ? GL11.GL_PACK_ALIGNMENT : GL11.GL_UNPACK_ALIGNMENT, 4);
        GL11.glPixelStorei(pack ? GL11.GL_PACK_SWAP_BYTES : GL11.GL_UNPACK_SWAP_BYTES,
                GL11.GL_FALSE);
    }
}
