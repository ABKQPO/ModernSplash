package gkappa.modernsplash;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL12.*;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.lang.management.ManagementFactory;
import java.nio.IntBuffer;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Iterator;
import java.util.Properties;
import java.util.concurrent.Semaphore;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.client.resources.FileResourcePack;
import net.minecraft.client.resources.FolderResourcePack;
import net.minecraft.client.resources.IResourcePack;
import net.minecraft.crash.CrashReport;
import net.minecraft.launchwrapper.Launch;
import net.minecraft.util.ResourceLocation;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.Level;
import org.embeddedt.archaicfix.config.ArchaicConfig;
import org.lwjgl.BufferUtils;
import org.lwjgl.LWJGLException;
import org.lwjgl.opengl.Display;
import org.lwjgl.opengl.Drawable;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.SharedDrawable;
import org.lwjgl.util.glu.GLU;

import cpw.mods.fml.client.FMLClientHandler;
import cpw.mods.fml.common.EnhancedRuntimeException;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.FMLLog;
import cpw.mods.fml.common.ICrashCallable;
import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.ModContainer;
import cpw.mods.fml.common.ProgressManager;
import cpw.mods.fml.common.ProgressManager.ProgressBar;
import cpw.mods.fml.common.asm.FMLSanityChecker;

/**
 * @deprecated not a stable API, will break, don't use this yet
 */

public class CustomSplash {

    public static Drawable d;
    public static volatile boolean pause = false;
    public static volatile boolean done = false;
    public static Thread thread;
    public static volatile Throwable threadError;
    public static int angle = 0;
    public static int Frame = 120;
    public static final Lock lock = new ReentrantLock(true);
    public static SplashFontRenderer fontRenderer;

    public static final IResourcePack mcPack = Minecraft.getMinecraft().mcDefaultResourcePack;
    public static final IResourcePack fmlPack = createResourcePack(FMLSanityChecker.fmlLocation);
    public static IResourcePack miscPack;

    public static Texture fontTexture;
    public static Texture logoTexture;
    public static Texture forgeTexture;

    public static Properties config;

    public static boolean enabled;
    public static boolean forgeLogo;
    public static boolean rotate;
    public static int logoOffset;
    public static int backgroundColor;
    public static int fontColor;
    public static int logoColor;
    public static int barBorderColor;
    public static int barColor;
    public static int barBackgroundColor;
    public static boolean showMemory;
    public static boolean showArchFixMemory;
    public static boolean showTotalMemoryLine;

    public static boolean displayStartupTimeOnMainMenu = true;
    public static boolean enableTimer = true;
    public static int memoryGoodColor;
    public static int memoryWarnColor;
    public static int memoryLowColor;
    public static float memoryColorPercent;
    public static long memoryColorChangeTime;
    public static final Semaphore mutex = new Semaphore(1);

    public static String getString(String name, String def) {
        String value = config.getProperty(name, def);
        config.setProperty(name, value);
        return value;
    }

    public static boolean getBool(String name, boolean def) {
        return Boolean.parseBoolean(getString(name, Boolean.toString(def)));
    }

    public static int getInt(String name, int def) {
        return Integer.decode(getString(name, Integer.toString(def)));
    }

    public static int getHex(String name, int def) {
        return Integer.decode(
            getString(
                name,
                "0x" + Integer.toString(def, 16)
                    .toUpperCase()));
    }

    public static void start() {
        File configFile = new File(Minecraft.getMinecraft().mcDataDir, "config/splash.properties");
        FileReader r = null;
        config = new Properties();
        try {
            r = new FileReader(configFile);
            config.load(r);
        } catch (IOException e) {
            FMLLog.info("Could not load splash.properties, will create a default one");
        } finally {
            IOUtils.closeQuietly(r);
        }

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("HHmm");
        int now = Integer.parseInt(formatter.format(LocalDateTime.now()));

        // Enable if we have the flag, and there's either no optifine, or optifine has added a key to the blackboard
        // ("optifine.ForgeSplashCompatible")
        // Optifine authors - add this key to the blackboard if you feel your modifications are now compatible with this
        // code.
        enabled = getBool("enabled", true) && ((!FMLClientHandler.instance()
            .hasOptifine()) || Launch.blackboard.containsKey("optifine.ForgeSplashCompatible"));
        rotate = getBool("rotate", false);
        forgeLogo = getBool("forgeLogo", false);
        showMemory = getBool("showMemory", true);
        showArchFixMemory = getBool("showArchFixMemory", true);
        showTotalMemoryLine = getBool("showTotalMemoryLine", false);
        enableTimer = getBool("enableTimer", true);

        logoOffset = getInt("logoOffset", 0);

        backgroundColor = getHex("background", 0xEF323D);
        fontColor = getHex("font", 0xFFFFFF);
        logoColor = getHex("logo", 0xFFFFFF);
        barBorderColor = getHex("barBorder", 0xFFFFFF);
        barColor = getHex("bar", 0xFFFFFF);
        barBackgroundColor = getHex("barBackground", 0xEF323D);
        memoryGoodColor = getHex("memoryGood", 0x337D23);
        memoryWarnColor = getHex("memoryWarn", 0x337D23);
        memoryLowColor = getHex("memoryLow", 0x337D23);

        displayStartupTimeOnMainMenu = getBool("timeOnMainMenu", true);

        boolean darkModeOnly = getBool("darkModeOnly", false);

        int darkStartTime = getInt("darkStartTime", 2300);
        int darkEndTime = getInt("darkEndTime", 600);

        int backgroundColorNight = getHex("backgroundDark", 0x202020);
        int fontColorNight = getHex("fontDark", 0x606060);
        int logoColorNight = getHex("logoDark", 0x999999);
        int barBorderColorNight = getHex("barBorderDark", 0x4E4E4E);
        int barColorNight = getHex("barDark", 0x4E4E4E);
        int barBackgroundColorNight = getHex("barBackgroundDark", 0x202020);
        int memoryGoodColorNight = getHex("memoryGoodDark", 0x4E4E4E);
        int memoryWarnColorNight = getHex("memoryWarnDark", 0x4E4E4E);
        int memoryLowColorNight = getHex("memoryLowDark", 0x4E4E4E);

        if (darkModeOnly || (darkEndTime >= darkStartTime ? (now >= darkStartTime && now < darkEndTime)
            : (now >= darkStartTime || now <= darkEndTime))) {
            backgroundColor = backgroundColorNight;
            fontColor = fontColorNight;
            logoColor = logoColorNight;
            barBorderColor = barBorderColorNight;
            barColor = barColorNight;
            barBackgroundColor = barBackgroundColorNight;
            memoryGoodColor = memoryGoodColorNight;
            memoryWarnColor = memoryWarnColorNight;
            memoryLowColor = memoryLowColorNight;
        }
        final ResourceLocation fontLoc = new ResourceLocation(getString("fontTexture", "textures/font/ascii.png"));
        final ResourceLocation logoLoc = new ResourceLocation(
            getString("logoTexture", "modernsplash:textures/gui/title/mojang.png"));
        final ResourceLocation forgeLoc = new ResourceLocation(getString("forgeTexture", "fml:textures/gui/forge.gif"));

        File miscPackFile = new File(Minecraft.getMinecraft().mcDataDir, getString("resourcePackPath", "resources"));

        FileWriter w = null;
        try {
            w = new FileWriter(configFile);
            config.store(w, "Splash screen properties");
        } catch (IOException e) {
            FMLLog.log(Level.ERROR, e, "Could not save the splash.properties file");
        } finally {
            IOUtils.closeQuietly(w);
        }

        miscPack = createResourcePack(miscPackFile);

        if (!enabled) return;
        // getting debug info out of the way, while we still can
        FMLCommonHandler.instance()
            .registerCrashCallable(new ICrashCallable() {

                public String call() {
                    return "' Vendor: '" + glGetString(GL_VENDOR)
                        + "' Version: '"
                        + glGetString(GL_VERSION)
                        + "' Renderer: '"
                        + glGetString(GL_RENDERER)
                        + "'";
                }

                public String getLabel() {
                    return "GL info";
                }
            });
        CrashReport report = CrashReport.makeCrashReport(new Throwable() {

            @Override
            public String getMessage() {
                return "This is just a prompt for computer specs to be printed. THIS IS NOT A ERROR";
            }

            @Override
            public void printStackTrace(final PrintWriter s) {
                s.println(getMessage());
            }

            @Override
            public void printStackTrace(final PrintStream s) {
                s.println(getMessage());
            }
        }, "Loading screen debug info");
        System.out.println(report.getCompleteReport());

        try {
            d = new SharedDrawable(Display.getDrawable());
            Display.getDrawable()
                .releaseContext();
            d.makeCurrent();
        } catch (LWJGLException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
        Thread mainThread = Thread.currentThread();
        thread = new Thread(new Runnable() {

            public final int barWidth = 400;
            public final int barHeight = 20;
            public final int textHeight2 = 20;
            public final int barOffset = 45;

            public void run() {
                setGL();
                fontTexture = new Texture(fontLoc);
                logoTexture = new Texture(logoLoc);
                forgeTexture = new Texture(forgeLoc);
                glEnable(GL_TEXTURE_2D);
                fontRenderer = new SplashFontRenderer();
                glDisable(GL_TEXTURE_2D);
                while (!done) {
                    ProgressBar first = null, penult = null, last = null;
                    Iterator<ProgressBar> i = ProgressManager.barIterator();
                    while (i.hasNext()) {
                        if (first == null) first = i.next();
                        else {
                            penult = last;
                            last = i.next();
                        }
                    }

                    glClear(GL_COLOR_BUFFER_BIT);

                    int w = Display.getWidth();
                    int h = Display.getHeight();
                    float scale = Math.min(w / 640f, h / 480f);
                    float centerX = w / 2f;
                    float centerY = h / 2f;

                    glViewport(0, 0, w, h);
                    glMatrixMode(GL_PROJECTION);
                    glLoadIdentity();
                    glOrtho(0, w, h, 0, -1, 1);
                    glMatrixMode(GL_MODELVIEW);
                    glLoadIdentity();

                    float logoSize = 256 * scale;
                    setColor(logoColor);
                    glEnable(GL_TEXTURE_2D);
                    logoTexture.bind();
                    glBegin(GL_QUADS);
                    logoTexture.texCoord(0, 0, 0);
                    glVertex2f(centerX - logoSize, centerY - logoSize);
                    logoTexture.texCoord(0, 0, 1);
                    glVertex2f(centerX - logoSize, centerY + logoSize);
                    logoTexture.texCoord(0, 1, 1);
                    glVertex2f(centerX + logoSize, centerY + logoSize);
                    logoTexture.texCoord(0, 1, 0);
                    glVertex2f(centerX + logoSize, centerY - logoSize);
                    glEnd();
                    glDisable(GL_TEXTURE_2D);

                    if (showMemory) {
                        glPushMatrix();
                        glTranslatef(centerX - (400 * scale) / 2, 20 * scale, 0);
                        glScalef(scale, scale, 1);
                        drawMemoryBar();
                        glPopMatrix();
                    }

                    if (enableTimer) {
                        glPushMatrix();
                        setColor(fontColor);
                        glTranslatef(4 * scale, h - 20 * scale, 0);
                        glScalef(2 * scale, 2 * scale, 1);
                        glEnable(GL_TEXTURE_2D);
                        fontRenderer.drawString(getString(), 0, 0, fontColor);
                        glDisable(GL_TEXTURE_2D);
                        glPopMatrix();
                    }

                    if (first != null) {
                        glPushMatrix();
                        glTranslatef(centerX - (400 * scale) / 2, h - 180 * scale, 0);
                        glScalef(scale, scale, 1);
                        drawBar(first);
                        if (penult != null) {
                            glTranslatef(0, 45, 0);
                            drawBar(penult);
                        }
                        if (last != null) {
                            glTranslatef(0, 45, 0);
                            drawBar(last);
                        }
                        glPopMatrix();
                    }

                    if (forgeLogo) {
                        float fw = (float) forgeTexture.getWidth() / 2f * scale;
                        float fh = (float) forgeTexture.getHeight() / 2f * scale;

                        float yOffset = 20 * scale;

                        glPushMatrix();
                        if (rotate) {
                            float sh = Math.max(fw, fh);
                            glTranslatef(w - sh - logoOffset * scale, h - sh - logoOffset * scale - yOffset, 0);
                            glRotatef(angle, 0, 0, 1);
                        } else {
                            glTranslatef(w - fw - logoOffset * scale, h - fh - logoOffset * scale - yOffset, 0);
                        }
                        int f = (int) (angle * ((float)forgeTexture.getFrames() / Frame) % forgeTexture.getFrames());
                        glEnable(GL_TEXTURE_2D);
                        forgeTexture.bind();
                        glBegin(GL_QUADS);
                        forgeTexture.texCoord(f, 0, 0);
                        glVertex2f(-fw, -fh);
                        forgeTexture.texCoord(f, 0, 1);
                        glVertex2f(-fw, fh);
                        forgeTexture.texCoord(f, 1, 1);
                        glVertex2f(fw, fh);
                        forgeTexture.texCoord(f, 1, 0);
                        glVertex2f(fw, -fh);
                        glEnd();
                        glDisable(GL_TEXTURE_2D);
                        glPopMatrix();
                    }

                    glPushMatrix();
                    setColor(fontColor);
                    float textPadding = fontRenderer.getStringWidth(getForgeVersionString()) * 2 * scale + 4 * scale;
                    glTranslatef(w - textPadding, h - 20 * scale, 0);
                    glScalef(2 * scale, 2 * scale, 1);
                    glEnable(GL_TEXTURE_2D);
                    fontRenderer.drawString(getForgeVersionString(), 0, 0, fontColor);
                    glDisable(GL_TEXTURE_2D);
                    glPopMatrix();

                    angle += 1;

                    mutex.acquireUninterruptibly();
                    Display.update();
                    mutex.release();

                    if (pause) {
                        clearGL();
                        setGL();
                    }
                    Display.sync(Frame);
                }
                clearGL();
            }

            public String getForgeVersionString() {
                String mcVersion = Loader.instance()
                    .getMinecraftModContainer()
                    .getVersion();

                String forgeVersion = Loader.instance()
                    .getModList()
                    .stream()
                    .filter(
                        mod -> mod.getModId()
                            .equals("Forge"))
                    .map(ModContainer::getVersion)
                    .findFirst()
                    .orElse("Unknown");

                return mcVersion + "-" + forgeVersion;
            }

            public String getString() {
                long startupTime = ManagementFactory.getRuntimeMXBean()
                    .getUptime();

                if (ModernSplash.doneTime > 0) startupTime = ModernSplash.doneTime;

                long minutes = (startupTime / 1000) / 60;
                long seconds = (startupTime / 1000) % 60;

                String str = "Startup: " + minutes + "m " + seconds + "s";

                if (MSLoadingPlugin.expectedTime > 0) {
                    long ex_minutes = (MSLoadingPlugin.expectedTime / 1000) / 60;
                    long ex_seconds = (MSLoadingPlugin.expectedTime / 1000) % 60;

                    str += " / ~" + ex_minutes + "m " + ex_seconds + "s";
                }

                return str;
            }

            public void setColor(int color) {
                glColor3ub((byte) ((color >> 16) & 0xFF), (byte) ((color >> 8) & 0xFF), (byte) (color & 0xFF));
            }

            public void drawBox(int w, int h) {
                glBegin(GL_QUADS);
                glVertex2f(0, 0);
                glVertex2f(0, h);
                glVertex2f(w, h);
                glVertex2f(w, 0);
                glEnd();
            }

            public void drawBar(ProgressBar b) {
                String progress = b.getStep() + "/" + b.getSteps();
                glPushMatrix();
                // title - message
                setColor(fontColor);
                glScalef(2, 2, 1);
                glEnable(GL_TEXTURE_2D);
                fontRenderer.drawString(b.getTitle() + " " + progress + " - " + b.getMessage(), 0, 0, fontColor);
                glDisable(GL_TEXTURE_2D);
                glPopMatrix();
                // border
                glPushMatrix();
                glTranslatef(0, textHeight2, 0);
                setColor(barBorderColor);
                drawBox(barWidth, barHeight);
                // interior
                setColor(barBackgroundColor);
                glTranslatef(2, 2, 0);
                drawBox(barWidth - 4, barHeight - 4);
                // slidy part
                setColor(barColor);
                glTranslatef(2, 2, 0);
                drawBox((barWidth - 8) * (b.getStep() + 1) / (b.getSteps() + 1), barHeight - 8); // Step can sometimes
                                                                                                 // be 0.
                // progress text
                /*
                 * glTranslatef(((float)barWidth - 2) / 2 - fontRenderer.getStringWidth(progress), 2, 0);
                 * setColor(fontColor);
                 * glScalef(2, 2, 1);
                 * glEnable(GL_TEXTURE_2D);
                 * fontRenderer.drawString(progress, 0, 0, 0x000000);
                 */
                glPopMatrix();
            }

            private void drawMemoryBar() {
                int cpuUsage = getSystemCpuUsage();
                String cpuText = cpuUsage >= 0 ? ("CPU: " + getCpuString(cpuUsage)) : "CPU: N/A";

                int maxMemory = bytesToMb(
                    Runtime.getRuntime()
                        .maxMemory());
                int totalMemory = bytesToMb(
                    Runtime.getRuntime()
                        .totalMemory());
                int freeMemory = bytesToMb(
                    Runtime.getRuntime()
                        .freeMemory());
                int usedMemory = totalMemory - freeMemory;
                float usedMemoryPercent = usedMemory / (float) maxMemory;
                String progress = getMemoryString(usedMemory) + " / " + getMemoryString(maxMemory);

                boolean useArchaic = showArchFixMemory && Loader.isModLoaded("archaicfix")
                    && ArchaicConfig.showSplashMemoryBar;

                glPushMatrix();
                setColor(fontColor);
                glScalef(2, 2, 1);
                glEnable(GL_TEXTURE_2D);
                if (useArchaic) {
                    // title - separate line
                    fontRenderer.drawString("Memory Used / Total" + "  " + cpuText, 0, 0, fontColor);
                    glDisable(GL_TEXTURE_2D);
                    glPopMatrix();

                    // border
                    glPushMatrix();
                    glTranslatef(0, textHeight2, 0);
                    setColor(barBorderColor);
                    drawBox(barWidth, barHeight);

                    // interior
                    setColor(barBackgroundColor);
                    glTranslatef(2, 2, 0);
                    drawBox(barWidth - 4, barHeight - 4);

                    // update memory color
                    long time = System.currentTimeMillis();
                    if (usedMemoryPercent > memoryColorPercent || (time - memoryColorChangeTime > 1000)) {
                        memoryColorChangeTime = time;
                        memoryColorPercent = usedMemoryPercent;
                    }

                    int memoryBarColor;
                    if (memoryColorPercent < 0.75f) memoryBarColor = memoryGoodColor;
                    else if (memoryColorPercent < 0.85f) memoryBarColor = memoryWarnColor;
                    else memoryBarColor = memoryLowColor;

                    // total memory line
                    setColor(memoryLowColor);
                    glPushMatrix();
                    glTranslatef((float) ((barWidth - 2) * (totalMemory)) / (maxMemory) - 2, 0, 0);
                    drawBox(2, barHeight - 4);
                    glPopMatrix();

                    // used memory bar
                    setColor(memoryBarColor);
                    glTranslatef(2, 2, 0);
                    drawBox((barWidth - 8) * (usedMemory) / (maxMemory), barHeight - 8);

                    // progress text centered on bar
                    glTranslatef(((float) barWidth - 2) / 2 - fontRenderer.getStringWidth(progress), -1, 0);
                    setColor(fontColor);
                    glScalef(2, 2, 1);
                    glEnable(GL_TEXTURE_2D);
                    fontRenderer.drawString(progress, 0, 0, fontColor);

                } else {
                    // title and progress in one line
                    String text = "Memory Usage : " + progress + "  " + cpuText;
                    int textWidth = fontRenderer.getStringWidth(text);
                    int textX = (barWidth - textWidth * 2) / 4;

                    fontRenderer.drawString(text, textX, 0, fontColor);
                    glDisable(GL_TEXTURE_2D);
                    glPopMatrix();

                    // border
                    glPushMatrix();
                    glTranslatef(0, textHeight2, 0);
                    setColor(barBorderColor);
                    drawBox(barWidth, barHeight);

                    // interior
                    setColor(barBackgroundColor);
                    glTranslatef(2, 2, 0);
                    drawBox(barWidth - 4, barHeight - 4);

                    // update memory color
                    long time = System.currentTimeMillis();
                    if (usedMemoryPercent > memoryColorPercent || (time - memoryColorChangeTime > 1000)) {
                        memoryColorChangeTime = time;
                        memoryColorPercent = usedMemoryPercent;
                    }

                    int memoryBarColor;
                    if (memoryColorPercent < 0.75f) memoryBarColor = memoryGoodColor;
                    else if (memoryColorPercent < 0.85f) memoryBarColor = memoryWarnColor;
                    else memoryBarColor = memoryLowColor;

                    // optional total memory line
                    if (showTotalMemoryLine) {
                        setColor(memoryLowColor);
                        glPushMatrix();
                        glTranslatef((float) ((barWidth - 8) * (totalMemory)) / (maxMemory) - 2, 2, 0);
                        drawBox(2, barHeight - 8);
                        glPopMatrix();
                    }

                    // used memory bar
                    setColor(memoryBarColor);
                    glTranslatef(2, 2, 0);
                    drawBox((barWidth - 8) * (usedMemory) / (maxMemory), barHeight - 8);
                }
                glPopMatrix();
            }

            public String getMemoryString(int memory) {
                return StringUtils.leftPad(Integer.toString(memory), 4, ' ') + " MB";
            }

            public String getCpuString(int cpu) {
                return StringUtils.leftPad(Integer.toString(cpu), 3, ' ') + " %";
            }

            public int getSystemCpuUsage() {
                com.sun.management.OperatingSystemMXBean os = (com.sun.management.OperatingSystemMXBean) java.lang.management.ManagementFactory
                    .getOperatingSystemMXBean();

                double load = os.getSystemCpuLoad();
                if (load < 0) return -1;

                return (int) (load * 100);
            }

            public void setGL() {
                lock.lock();
                try {
                    Display.getDrawable()
                        .makeCurrent();
                } catch (LWJGLException e) {
                    e.printStackTrace();
                    throw new RuntimeException(e);
                }
                glClearColor(
                    (float) ((backgroundColor >> 16) & 0xFF) / 0xFF,
                    (float) ((backgroundColor >> 8) & 0xFF) / 0xFF,
                    (float) (backgroundColor & 0xFF) / 0xFF,
                    1);
                glDisable(GL_LIGHTING);
                glDisable(GL_DEPTH_TEST);
                glEnable(GL_BLEND);
                glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
            }

            public void clearGL() {
                Minecraft mc = Minecraft.getMinecraft();
                mc.displayWidth = Display.getWidth();
                mc.displayHeight = Display.getHeight();
                mc.resize(mc.displayWidth, mc.displayHeight);
                glClearColor(1, 1, 1, 1);
                glEnable(GL_DEPTH_TEST);
                glDepthFunc(GL_LEQUAL);
                glEnable(GL_ALPHA_TEST);
                glAlphaFunc(GL_GREATER, .1f);
                try {
                    Display.getDrawable()
                        .releaseContext();
                } catch (LWJGLException e) {
                    e.printStackTrace();
                    throw new RuntimeException(e);
                } finally {
                    lock.unlock();
                }
            }
        });
        thread.setUncaughtExceptionHandler((t, e) -> {
            FMLLog.log(Level.ERROR, e, "Splash thread Exception");
            threadError = e;
        });
        thread.start();
        checkThreadState();
    }

    public static void checkThreadState() {
        if (thread.getState() == Thread.State.TERMINATED || threadError != null) {
            throw new IllegalStateException("Splash thread", threadError);
        }
    }

    /**
     * Call before you need to explicitly modify GL context state during loading.
     * Resource loading doesn't usually require this call.
     * Call {@link #resume()} when you're done.
     *
     * @deprecated not a stable API, will break, don't use this yet
     */
    @Deprecated
    public static void pause() {
        if (!enabled) return;
        checkThreadState();
        pause = true;
        lock.lock();
        try {
            d.releaseContext();
            Display.getDrawable()
                .makeCurrent();
        } catch (LWJGLException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }

    /**
     * @deprecated not a stable API, will break, don't use this yet
     */
    @Deprecated
    public static void resume() {
        if (!enabled) return;
        checkThreadState();
        pause = false;
        try {
            Display.getDrawable()
                .releaseContext();
            d.makeCurrent();
        } catch (LWJGLException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
        lock.unlock();
    }

    public static void finish() {
        if (!enabled) return;
        try {
            checkThreadState();
            done = true;
            thread.join();
            d.releaseContext();
            Display.getDrawable()
                .makeCurrent();
            fontTexture.delete();
            logoTexture.delete();
            forgeTexture.delete();
        } catch (Exception e) {
            e.printStackTrace();
            if (disableSplash()) {
                throw new EnhancedRuntimeException(e) {

                    @Override
                    protected void printStackTrace(WrappedPrintStream stream) {
                        stream.println("SplashProgress has detected a error loading Minecraft.");
                        stream.println("This can sometimes be caused by bad video drivers.");
                        stream.println(
                            "We have automatically disabeled the new Splash Screen in config/splash.properties.");
                        stream.println("Try reloading minecraft before reporting any errors.");
                    }
                };
            } else {
                throw new EnhancedRuntimeException(e) {

                    @Override
                    protected void printStackTrace(WrappedPrintStream stream) {
                        stream.println("SplashProgress has detected a error loading Minecraft.");
                        stream.println("This can sometimes be caused by bad video drivers.");
                        stream.println("Please try disabeling the new Splash Screen in config/splash.properties.");
                        stream.println("After doing so, try reloading minecraft before reporting any errors.");
                    }
                };
            }
        }
    }

    public static boolean disableSplash() {
        File configFile = new File(Minecraft.getMinecraft().mcDataDir, "config/splash.properties");
        File parent = configFile.getParentFile();
        if (!parent.exists()) parent.mkdirs();

        enabled = false;
        config.setProperty("enabled", "false");

        FileWriter w = null;
        try {
            w = new FileWriter(configFile);
            config.store(w, "Splash screen properties");
        } catch (IOException e) {
            FMLLog.log(Level.ERROR, e, "Could not save the splash.properties file");
            return false;
        } finally {
            IOUtils.closeQuietly(w);
        }
        return true;
    }

    public static IResourcePack createResourcePack(File file) {
        if (file.isDirectory()) {
            return new FolderResourcePack(file);
        } else {
            return new FileResourcePack(file);
        }
    }

    public static final IntBuffer buf = BufferUtils.createIntBuffer(4 * 1024 * 1024);

    public static class Texture {

        public final ResourceLocation location;
        public final int name;
        public final int width;
        public final int height;
        public final int frames;
        public final int size;

        public Texture(ResourceLocation location) {
            InputStream s = null;
            try {
                this.location = location;
                s = open(location);
                ImageInputStream stream = ImageIO.createImageInputStream(s);
                Iterator<ImageReader> readers = ImageIO.getImageReaders(stream);
                if (!readers.hasNext()) throw new IOException("No suitable reader found for image" + location);
                ImageReader reader = readers.next();
                reader.setInput(stream);
                frames = reader.getNumImages(true);
                BufferedImage[] images = new BufferedImage[frames];
                for (int i = 0; i < frames; i++) {
                    images[i] = reader.read(i);
                }
                reader.dispose();
                int size = 1;
                width = images[0].getWidth();
                height = images[0].getHeight();
                while ((size / width) * (size / height) < frames) size *= 2;
                this.size = size;
                glEnable(GL_TEXTURE_2D);
                synchronized (CustomSplash.class) {
                    name = glGenTextures();
                    glBindTexture(GL_TEXTURE_2D, name);
                }
                glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
                glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
                glTexImage2D(
                    GL_TEXTURE_2D,
                    0,
                    GL_RGBA,
                    size,
                    size,
                    0,
                    GL_BGRA,
                    GL_UNSIGNED_INT_8_8_8_8_REV,
                    (IntBuffer) null);
                checkGLError("Texture creation");
                for (int i = 0; i * (size / width) < frames; i++) {
                    for (int j = 0; i * (size / width) + j < frames && j < size / width; j++) {
                        buf.clear();
                        BufferedImage image = images[i * (size / width) + j];
                        for (int k = 0; k < height; k++) {
                            for (int l = 0; l < width; l++) {
                                buf.put(image.getRGB(l, k));
                            }
                        }
                        buf.position(0)
                            .limit(width * height);
                        glTexSubImage2D(
                            GL_TEXTURE_2D,
                            0,
                            j * width,
                            i * height,
                            width,
                            height,
                            GL_BGRA,
                            GL_UNSIGNED_INT_8_8_8_8_REV,
                            buf);
                        checkGLError("Texture uploading");
                    }
                }
                glBindTexture(GL_TEXTURE_2D, 0);
                glDisable(GL_TEXTURE_2D);
            } catch (IOException e) {
                e.printStackTrace();
                throw new RuntimeException(e);
            } finally {
                IOUtils.closeQuietly(s);
            }
        }

        public ResourceLocation getLocation() {
            return location;
        }

        public int getName() {
            return name;
        }

        public int getWidth() {
            return width;
        }

        public int getHeight() {
            return height;
        }

        public int getFrames() {
            return frames + 1;
        }

        public int getSize() {
            return size;
        }

        public void bind() {
            glBindTexture(GL_TEXTURE_2D, name);
        }

        public void delete() {
            glDeleteTextures(name);
        }

        public float getU(int frame, float u) {
            return width * (frame % ((float) size / width) + u) / size;
            //return u;
        }

        public float getV(int frame, float v) {
            int currentFrame = frame % frames * width / size;
            return height * ( (int) (currentFrame % ((float) size / width)) + v) / size;
            //return v;
        }

        public void texCoord(int frame, float u, float v) {
            glTexCoord2f(getU(frame, u), getV(frame, v));
        }
    }

    public static class SplashFontRenderer extends FontRenderer {

        public SplashFontRenderer() {
            super(Minecraft.getMinecraft().gameSettings, fontTexture.getLocation(), null, false);
            super.onResourceManagerReload(null);
        }

        @Override
        protected void bindTexture(ResourceLocation location) {
            if (location != locationFontTexture) throw new IllegalArgumentException();
            fontTexture.bind();
        }

        @Override
        protected InputStream getResourceInputStream(ResourceLocation location) throws IOException {
            return Minecraft.getMinecraft().mcDefaultResourcePack.getInputStream(location);
        }
    }

    public static void drawVanillaScreen() throws LWJGLException {
        if (!enabled) {
            Minecraft.getMinecraft()
                .loadScreen();
        }
    }

    public static void clearVanillaResources(TextureManager renderEngine, ResourceLocation mojangLogo) {
        if (!enabled) {
            renderEngine.deleteTexture(mojangLogo);
        }
    }

    public static void checkGLError(String where) {
        int err = GL11.glGetError();
        if (err != 0) {
            throw new IllegalStateException(where + ": " + GLU.gluErrorString(err));
        }
    }

    public static InputStream open(ResourceLocation loc) throws IOException {
        if (miscPack.resourceExists(loc)) {
            return miscPack.getInputStream(loc);
        } else if (fmlPack.resourceExists(loc)) {
            return fmlPack.getInputStream(loc);
        }
        return mcPack.getInputStream(loc);
    }

    public static int bytesToMb(long bytes) {
        return (int) (bytes / 1024L / 1024L);
    }

}
