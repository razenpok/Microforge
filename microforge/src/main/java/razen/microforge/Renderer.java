package razen.microforge;

import com.fs.starfarer.api.Global;
import org.lwjgl.LWJGLException;
import org.lwjgl.Sys;
import org.lwjgl.opengl.Display;
import org.lwjgl.opengl.GL11;
import org.lwjgl.util.vector.Vector2f;

import java.awt.Color;
import java.awt.Toolkit;
import java.util.prefs.Preferences;

final class Renderer {
    private static final int GL_CLAMP_TO_EDGE = 33071;
    private static final long TIMER_RESOLUTION = Sys.getTimerResolution();
    private static float maxPixelScaleFactor = 1.0F;
    private static int maxScaledDisplayWidth = 0;
    private static int maxScaledDisplayHeight = 0;

    private Renderer() {
    }

    static void useClampToEdgeTextureWrap() {
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
    }

    static void useRepeatingTextureWrap() {
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL11.GL_REPEAT);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL11.GL_REPEAT);
    }

    static void setColor(Color color) {
        GL11.glColor4ub(channelByte(color.getRed()), channelByte(color.getGreen()),
                channelByte(color.getBlue()), channelByte(color.getAlpha()));
    }

    static void setColor(Color color, float alphaScale) {
        GL11.glColor4ub(channelByte(color.getRed()), channelByte(color.getGreen()),
                channelByte(color.getBlue()), channelByte((int) (color.getAlpha() * alphaScale)));
    }

    static void setColor(Color color, int alpha) {
        GL11.glColor4ub(channelByte(color.getRed()), channelByte(color.getGreen()),
                channelByte(color.getBlue()), channelByte(alpha));
    }

    static Color add(Color first, Color second) {
        return new Color(first.getRed() + second.getRed(),
                first.getGreen() + second.getGreen(),
                first.getBlue() + second.getBlue(),
                first.getAlpha() + second.getAlpha());
    }

    static Color multiply(Color first, Color second) {
        var red = (int) (first.getRed() * (second.getRed() / 255.0F));
        var green = (int) (first.getGreen() * (second.getGreen() / 255.0F));
        var blue = (int) (first.getBlue() * (second.getBlue() / 255.0F));
        var alpha = (int) (first.getAlpha() * (second.getAlpha() / 255.0F));

        return new Color(clampChannel(red), clampChannel(green), clampChannel(blue), clampChannel(alpha));
    }

    static Color interpolate(Color from, Color to, float progress) {
        if (progress <= 0.0F) {
            return from;
        }
        if (progress >= 1.0F) {
            return to;
        }

        var red = (int) (from.getRed() + (to.getRed() - from.getRed()) * progress);
        var green = (int) (from.getGreen() + (to.getGreen() - from.getGreen()) * progress);
        var blue = (int) (from.getBlue() + (to.getBlue() - from.getBlue()) * progress);
        var alpha = (int) (from.getAlpha() + (to.getAlpha() - from.getAlpha()) * progress);

        return new Color(clampChannel(red), clampChannel(green), clampChannel(blue), clampChannel(alpha));
    }

    static long getTimeMillis() {
        return Sys.getTime() * 1000L / TIMER_RESOLUTION;
    }

    static float logicalScreenWidth() {
        var width = Global.getSettings().getScreenWidth();
        if (width > 0.0F) {
            return width;
        }

        return displayWidth() / fallbackScreenScale();
    }

    static float logicalScreenHeight() {
        var height = Global.getSettings().getScreenHeight();
        if (height > 0.0F) {
            return height;
        }

        return displayHeight() / fallbackScreenScale();
    }

    static void restoreDisplayAfterExternalUi() throws LWJGLException {
        if (Display.isCreated() && !Display.isCurrent()) {
            Display.makeCurrent();
        }
        Display.processMessages();
    }

    static void clearFrame() {
        GL11.glDisable(GL11.GL_SCISSOR_TEST);
        GL11.glClearColor(0.0F, 0.0F, 0.0F, 0.0F);
        GL11.glColorMask(true, true, true, true);
        GL11.glClear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT | GL11.GL_STENCIL_BUFFER_BIT);
        GL11.glColorMask(true, true, true, false);
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glLoadIdentity();
    }

    static void beginScreenProjection(float width, float height) {
        beginScreenProjection(0.0F, width, 0.0F, height);
    }

    static void beginScreenProjection(float left, float right, float bottom, float top) {
        var depth = 4000.0F;

        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        GL11.glPushMatrix();
        GL11.glLoadIdentity();

        GL11.glMatrixMode(GL11.GL_PROJECTION);
        GL11.glPushMatrix();
        GL11.glLoadIdentity();
        GL11.glOrtho(left, right, bottom, top, -depth, depth);

        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glPushMatrix();
        GL11.glLoadIdentity();
        GL11.glTranslatef(0.01F, 0.01F, 0.0F);
        GL11.glViewport(0, 0, scaledDisplayWidth(), scaledDisplayHeight());
    }

    static void endScreenProjection() {
        GL11.glPopMatrix();
        GL11.glMatrixMode(GL11.GL_PROJECTION);
        GL11.glPopMatrix();
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glPopMatrix();
        GL11.glPopAttrib();
    }

    static void enableScissor(int x, int y, int width, int height) {
        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        GL11.glScissor(x, y, width, height);
    }

    static void disableScissor() {
        GL11.glDisable(GL11.GL_SCISSOR_TEST);
    }

    static void disableStencil() {
        GL11.glDisable(GL11.GL_STENCIL_TEST);
    }

    static float angleDegrees(float fromX, float fromY, float toX, float toY) {
        var direction = new Vector2f(toX - fromX, fromY - toY);
        if (direction.x != 0.0F || direction.y != 0.0F) {
            direction.normalise();
        }

        var angle = (float) Math.toDegrees(Math.acos(direction.getX()));
        if (fromY - toY < 0.0F) {
            angle = -angle;
        }

        return angle;
    }

    static String leftPad(String value, int length, String pad) {
        if (pad.length() != 1) {
            throw new IllegalArgumentException("pad string length must be 1");
        }

        var valueBuilder = new StringBuilder(value);
        while (valueBuilder.length() < length) {
            valueBuilder.insert(0, pad);
        }

        return valueBuilder.toString();
    }

    static Color scale(Color color, float multiplier) {
        return new Color((int) (color.getRed() * multiplier),
                (int) (color.getGreen() * multiplier),
                (int) (color.getBlue() * multiplier),
                (int) (color.getAlpha() * multiplier));
    }

    static Color scaleRgb(Color color, float multiplier) {
        var red = (int) (color.getRed() * multiplier);
        var green = (int) (color.getGreen() * multiplier);
        var blue = (int) (color.getBlue() * multiplier);

        return new Color(clampChannel(red), clampChannel(green), clampChannel(blue), color.getAlpha());
    }

    static Color scaleRgbUnclamped(Color color, float multiplier) {
        return new Color((int) (color.getRed() * multiplier),
                (int) (color.getGreen() * multiplier),
                (int) (color.getBlue() * multiplier),
                color.getAlpha());
    }

    static Color scaleAlpha(Color color, float multiplier) {
        var alpha = clampChannel((int) (color.getAlpha() * multiplier));
        return new Color(color.getRed(), color.getGreen(), color.getBlue(), alpha);
    }

    static Color withAlpha(Color color, int alpha) {
        return new Color(color.getRed(), color.getGreen(), color.getBlue(), clampChannel(alpha));
    }

    static Color whiteWithAlpha(float alpha) {
        return new Color(255, 255, 255, clampChannel((int) (255.0F * alpha)));
    }

    static Color scaleClamped(Color color, float multiplier) {
        var red = Math.min((int) (color.getRed() * multiplier), 255);
        var green = Math.min((int) (color.getGreen() * multiplier), 255);
        var blue = Math.min((int) (color.getBlue() * multiplier), 255);
        var alpha = Math.min((int) (color.getAlpha() * multiplier), 255);

        return new Color(red, green, blue, alpha);
    }

    static Color readableTextColor(Color backgroundColor) {
        if (backgroundColor == null) {
            return Color.white;
        }

        var color = Color.white;
        float red = backgroundColor.getRed();
        float green = backgroundColor.getGreen();
        float blue = backgroundColor.getBlue();
        float alpha = backgroundColor.getAlpha();
        var transparency = (255.0F - red + 255.0F - green + 255.0F - blue + 255.0F - alpha) / 765.0F;
        var brightness = luminance(backgroundColor);

        if (transparency > 1.0F) {
            transparency = 1.0F;
        }
        if (transparency > 0.0F) {
            color = interpolate(color, backgroundColor, 0.8F * transparency);
            color = scaleRgbUnclamped(color, 0.4F + 0.6F * brightness * alpha / 255.0F);
        }

        return color;
    }

    static Color withMaxChannel(Color color, int maxChannel) {
        float brightestChannel = Math.max(color.getRed(), Math.max(color.getGreen(), color.getBlue()));
        var multiplier = maxChannel / brightestChannel;
        return scaleClamped(color, multiplier);
    }

    static float luminance(Color color) {
        var luminance = (0.299F * color.getRed() + 0.587F * color.getGreen() + 0.114F * color.getBlue()) / 255.0F;
        if (luminance < 0.0F) {
            return 0.0F;
        }
        if (luminance > 1.0F) {
            return 1.0F;
        }
        return luminance;
    }

    static float maxBrightness(Color color) {
        var brightness = Math.max(color.getRed(), Math.max(color.getGreen(), color.getBlue())) / 255.0F;
        if (brightness < 0.0F) {
            return 0.0F;
        }
        if (brightness > 1.0F) {
            return 1.0F;
        }
        return brightness;
    }

    static void renderCornerShadow(float x, float y, float width, float height, float edgeFraction, float alphaScale, float alpha) {
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

        var inset = width * edgeFraction;
        var shadowAlpha = alpha * alphaScale;

        GL11.glBegin(GL11.GL_TRIANGLE_STRIP);
        setColor(Color.black, shadowAlpha);
        GL11.glVertex2f(x, y);
        setColor(Color.black, 0.0F);
        GL11.glVertex2f(x + inset, y + inset);
        setColor(Color.black, shadowAlpha);
        GL11.glVertex2f(x, y + height);
        setColor(Color.black, 0.0F);
        GL11.glVertex2f(x + inset, y + height - inset);
        setColor(Color.black, shadowAlpha);
        GL11.glVertex2f(x + width, y + height);
        setColor(Color.black, 0.0F);
        GL11.glVertex2f(x + width - inset, y + height - inset);
        setColor(Color.black, shadowAlpha);
        GL11.glVertex2f(x + width, y);
        setColor(Color.black, 0.0F);
        GL11.glVertex2f(x + width - inset, y + inset);
        setColor(Color.black, shadowAlpha);
        GL11.glVertex2f(x, y);
        setColor(Color.black, 0.0F);
        GL11.glVertex2f(x + inset, y + inset);
        GL11.glEnd();
    }

    private static int scaledDisplayWidth() {
        var width = Math.round(displayWidth() * effectivePixelScaleFactor());
        if (width > maxScaledDisplayWidth) {
            maxScaledDisplayWidth = width;
        }

        return maxScaledDisplayWidth > 0 ? maxScaledDisplayWidth : width;
    }

    private static int scaledDisplayHeight() {
        var height = Math.round(displayHeight() * effectivePixelScaleFactor());
        if (height > maxScaledDisplayHeight) {
            maxScaledDisplayHeight = height;
        }

        return maxScaledDisplayHeight > 0 ? maxScaledDisplayHeight : height;
    }

    private static float effectivePixelScaleFactor() {
        var pixelScaleFactor = Display.getPixelScaleFactor();
        if (pixelScaleFactor > maxPixelScaleFactor) {
            maxPixelScaleFactor = pixelScaleFactor;
        }

        return maxPixelScaleFactor;
    }

    private static int displayWidth() {
        var width = Display.getWidth();
        if (width > 0) {
            return width;
        }

        var mode = Display.getDisplayMode();
        if (mode != null && mode.getWidth() > 0) {
            return mode.getWidth();
        }

        return 1024;
    }

    private static int displayHeight() {
        var height = Display.getHeight();
        if (height > 0) {
            return height;
        }

        var mode = Display.getDisplayMode();
        if (mode != null && mode.getHeight() > 0) {
            return mode.getHeight();
        }

        return 768;
    }

    private static float fallbackScreenScale() {
        return Math.min(screenScaleForResolution(displayWidth(), displayHeight()), preferredScreenScale());
    }

    private static float screenScaleForResolution(int width, int height) {
        var scale = Math.min(height / 768.0F, width / 1280.0F);
        scale = (float) Math.floor(scale * 20.0F) / 20.0F;
        return Math.max(1.0F, scale);
    }

    private static float preferredScreenScale() {
        var savedScale = savedScreenScale();
        if (savedScale != null && savedScale > 0.0F) {
            return savedScale;
        }

        return defaultScreenScaleForDesktop();
    }

    private static Float savedScreenScale() {
        try {
            var value = Preferences.userRoot().node("/com/fs/starfarer").get("screenScale", null);
            if (value == null || value.trim().isEmpty()) {
                return null;
            }
            return Float.parseFloat(value);
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static float defaultScreenScaleForDesktop() {
        try {
            var screenSize = Toolkit.getDefaultToolkit().getScreenSize();
            if (screenSize.height < 1300) {
                return 1.0F;
            }
            if (screenSize.height < 2160) {
                return Math.round(screenSize.height / 1080.0F * 20.0F) / 20.0F;
            }
            return Math.round(screenSize.height / 1080.0F);
        } catch (RuntimeException e) {
            return 1.0F;
        }
    }

    private static byte channelByte(int value) {
        return (byte) value;
    }

    private static int clampChannel(int value) {
        if (value < 0) {
            return 0;
        }
        if (value > 255) {
            return 255;
        }
        return value;
    }
}
