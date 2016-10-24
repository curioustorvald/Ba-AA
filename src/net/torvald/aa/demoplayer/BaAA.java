package net.torvald.aa.demoplayer;


import net.torvald.aa.*;
import org.lwjgl.opengl.GL11;
import org.newdawn.slick.*;
import org.newdawn.slick.imageout.ImageOut;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.util.Properties;

/**
 * Crudely-written Ascii Art video player for Bad Apple
 *
 *
 *
 * Created by minjaesong on 16-08-10.
 */
public class BaAA extends BasicGame {

    static int w;
    static int h;

    static AppGameContainer appgc;
    private FrameFetcher frameLoader;
    static AAFrame aaframe;

    static String appname;
    static final String appnameDefault = "AA Player";

    private boolean precalcDone = false;
    private boolean displayedLoading = false;
    private boolean playbackComplete = false;
    private boolean musicFired = false;

    private Music bamusic;
    private boolean preloadMode;
    private String filePrefix;
    private static String fontFileName;
    private static String audioFileName;
    private static boolean inverted;
    private int preCalcRate;
    private String framesDir;
    private static String framename;
    private double gamma;
    private static boolean moreGrey;
    private static int ditherAlgo;
    private String testImageRef;
    private Image testImage;
    private static boolean singleColour;
    private static boolean fullCodePage;
    private static int algorithm;
    private int monitorCol;
    private boolean showCredit;
    private int colourAlgo;
    private Color customFilter;
    private boolean recordMode;
    private boolean replayMode = false;
    private String replayFileRef;

    private static Image screenBuffer;
    private static Graphics screenG;

    public static Color[] colors;

    public static final int[] hexadecaGrey = {
            0x00, 0xFF, 0x55, 0xAA, 0x11, 0x22, 0x33, 0x44,
            0x66, 0x77, 0x88, 0x99, 0xBB, 0xCC, 0xDD, 0xEE
    };
    public static final int[] hexadecaGreyInv = {
            0xFF, 0x00, 0xAA, 0x55, 0xEE, 0xDD, 0xCC, 0xBB,
            0x99, 0x88, 0x77, 0x66, 0x44, 0x33, 0x22, 0x11
    };

    public static final int [] colcga = {
            0x00, 0xFF, 0x55, 0xAA
    };

    public static final int [] colcgaInv = {
            0xFF, 0x00, 0xAA, 0x55
    };

    public static final int RANGE_CGA = colcga.length;
    public static final int RANGE_EXT = hexadecaGrey.length;

    private static int fontW, fontH;

    public static int framerate;
    private static double frameLen;

    private Font font;
    private Font drawFont;

    public static AsciiAlgo imageToAA;

    private boolean makeScreenRec;

    public BaAA() {
        super(appname);
    }

    @Override
    public void init(GameContainer gameContainer) throws SlickException {
        try {
            Properties prop = new Properties();
            prop.load(new FileInputStream("./config.properties"));

            w = new Integer(decodeAxB(prop.getProperty("sTerminalSize"))[0]);
            h = new Integer(decodeAxB(prop.getProperty("sTerminalSize"))[1]);
            framerate = new Integer(prop.getProperty("iVideoFramerate"));
            preloadMode = new Boolean(prop.getProperty("bPreloadFrames"));
            filePrefix = prop.getProperty("sFramesPrefix");
            fontFileName = prop.getProperty("sFontFamilyName");
            audioFileName = prop.getProperty("sAudioFileName");
            fontW = new Integer(decodeAxB(prop.getProperty("sFontSize"))[0]);
            fontH = new Integer(decodeAxB(prop.getProperty("sFontSize"))[1]);
            inverted = new Boolean(prop.getProperty("bInverted"));
            preCalcRate = new Integer(prop.getProperty("iPreCalcRate"));
            framename = prop.getProperty("sFramesDir");
            framesDir = "./assets/" + framename;
            gamma = intNullSafe(prop.getProperty("iGamma"), 220) * 0.01;
            moreGrey = new Boolean(prop.getProperty("b16Tones"));
            ditherAlgo = new Integer(prop.getProperty("iDitherAlgo"));
            testImageRef = prop.getProperty("sTestDisplayImage");
            singleColour = new Boolean(prop.getProperty("bSingleTone"));
            fullCodePage = new Boolean(prop.getProperty("bFullCodePage"));
            algorithm = new Integer(prop.getProperty("iAsciiAlgo"));
            monitorCol = new Integer(prop.getProperty("iMonitorType"));
            showCredit = new Boolean(prop.getProperty("bDemoCredit"));
            colourAlgo = new Integer(prop.getProperty("iColourMode"));
            recordMode = new Boolean(prop.getProperty("bIsRecordMode"));
            makeScreenRec = new Boolean(prop.getProperty("bMakeScreenRec"));
            replayFileRef = prop.getProperty("sRecordFileName");
            if (recordMode && replayFileRef != null && replayFileRef.length() > 0)
                throw new IllegalStateException("Cannot record and play from the same file! " +
                                                        "Please check your configuration.");
            if (replayFileRef != null && replayFileRef.length() > 0) replayMode = true;

            String customCol = prop.getProperty("sCustomFilterColour");
            if (customCol == null || customCol.length() < 5) customCol = "255,79,0";
            String[] customColSplit = customCol.split("[,.]");
            customFilter = new Color(
                    new Integer(customColSplit[0]),
                    new Integer(customColSplit[1]),
                    new Integer(customColSplit[2]));

            if (testImageRef != null && testImageRef.length() > 0)
                testImage = new Image("./assets/" + testImageRef);

            updateFramerate(framerate);

            if (moreGrey && singleColour)
                throw new IllegalStateException("Cannot be both 16-tone and black-white mode! " +
                                                        "Please check your configuration.");
            if (replayMode) {
                moreGrey = true;
                singleColour = false;
            }// they're somewhat compatible 'cause I made them to be, just fix it to 16 so that
            // the user don't have to care about config adjustment

            updateColours((moreGrey) ? 16 : (singleColour) ? 2 : 4);

            System.out.println("Current pallet: ");
            for (Color color : colors) {
                System.out.println("   " + color);
            }
        }
        catch (IOException e) {
            e.printStackTrace();
        }

        SpriteSheet fontSheet = new SpriteSheet("./assets/" + fontFileName, fontW, fontH);
        font = new SpriteSheetFont(fontSheet, (char) 0);

        drawFont = new ColouredFastFont("./assets/" + fontFileName, fontW, fontH);

        updateDisplayMode(w, h, fontW, fontH);

        bamusic = new Music("./assets/" + audioFileName);

        // it doesn't have to be square (3x3, 4x4, 5x5, ...), but the quality generally sucks if not.
        if (!replayMode) {
            if (algorithm == 0) {
                imageToAA = new ImageToAA();
                ((ImageToAA) imageToAA).setProp(w, h, fontSheet, fontW, fontH, inverted, gamma,
                        ditherAlgo, fullCodePage, colourAlgo);
            }
            else if (algorithm == 1) {
                imageToAA = new ImageToAASubGlyph4();
                ((ImageToAASubGlyph4) imageToAA).setProp(w, h, fontSheet, fontW, fontH, inverted, gamma,
                        ditherAlgo, fullCodePage, colourAlgo);
            }
            else if (algorithm == 2) {
                imageToAA = new ImageToAASubGlyphArb(3, 3);
                ((ImageToAASubGlyphArb) imageToAA).setProp(w, h, fontSheet, fontW, fontH, inverted, gamma,
                        ditherAlgo, fullCodePage, colourAlgo);
            }
            else if (algorithm == 3) {
                imageToAA = new ImageToAASubGlyphArb(4, 4);
                ((ImageToAASubGlyphArb) imageToAA).setProp(w, h, fontSheet, fontW, fontH, inverted, gamma,
                        ditherAlgo, fullCodePage, colourAlgo);
            }
            else if (algorithm == 4) {
                imageToAA = new ImageToAASubGlyphArb(5, 5);
                ((ImageToAASubGlyphArb) imageToAA).setProp(w, h, fontSheet, fontW, fontH, inverted, gamma,
                        ditherAlgo, fullCodePage, colourAlgo);
            }
            else if (algorithm == 5) {
                imageToAA = new ImageToAASubGlyphArb(8, 8);
                ((ImageToAASubGlyphArb) imageToAA).setProp(w, h, fontSheet, fontW, fontH, inverted, gamma,
                        ditherAlgo, fullCodePage, colourAlgo);
            }
            else {
                throw new IllegalStateException("Unknown antialiasing option: " + algorithm);
            }
        }

        if (recordMode) {
            frameLoader = new FrameRecorder(framesDir, w, h, filePrefix, preCalcRate);
        }
        else if (replayMode) {
            frameLoader = new FrameRecordPreloader(new File("./" + replayFileRef).getAbsoluteFile());
        }
        else if (preloadMode) {
            frameLoader = new FramePreloader(framesDir, w, h, filePrefix, preCalcRate);
        }
        else {
            frameLoader = new FrameStreamer(framesDir, w, h, filePrefix);
        }

        frameLoader.init();
    }

    private int frameCount = 0;
    private int deltaCount = 0;

    private String loadingmsg = "Precalculating . . . .";

    private String nowplayng  = "Now playing . . . .                            ";
    private String howtopause = "Hit SPACE to pause/resume video.";
    private String protip = "- Protip: it always looks better on milky.png";

    private boolean isPaused = false;

    @Override
    public void update(GameContainer gameContainer, int delta) throws SlickException {
        if (!precalcDone) {
            aaframe.drawString(loadingmsg, 3, 2, colors.length - 1);
            aaframe.drawString(howtopause, 3, 7, colors.length - 1);
            aaframe.drawString(protip, 3, 9, colors.length - 1);

            if (!replayMode) imageToAA.precalcFont();

            //preload frames (if applicable)
            frameLoader.preJob(aaframe);
            precalcDone = frameLoader.preJobDone();

            if (precalcDone) {
                if (recordMode) { // record done
                    System.gc();
                    playbackComplete = true;
                }
                else {
                    aaframe.drawString(nowplayng, 3, 2, colors.length - 1);
                }
            }
        }
        else if (!playbackComplete) {
            // count up frame
            if (!isPaused) {
                deltaCount += delta;

                if (!makeScreenRec)
                    frameCount = (int) Math.floor(deltaCount / frameLen) - 2 * framerate;
                else
                    frameCount += 1;
            }

            appgc.setTitle(
                    appname
                    + " — S: " + String.valueOf(gameContainer.getFPS())
                    + "("
                    + String.valueOf(framerate)
                    + ") — M: "
                    + String.valueOf(ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getUsed() >>> 20)
                    + "M/"
                    + String.valueOf(Runtime.getRuntime().maxMemory() >>> 20)
                    + "M"
                    + " — F: "
                    + frameCount
                    + "/"
                    + frameLoader.getFrameCount()
                    + " "
                    + ((replayMode) ? "RECD" : ((preloadMode) ? "PRLD" : "STRM"))
                    + " — C: " + ((!replayMode) ? String.valueOf(colors.length)
                                                : String.valueOf(((FrameRecordPreloader) frameLoader).getNColour()))
                    + " — A: " + ((!replayMode) ? String.valueOf(algorithm) + String.valueOf(ditherAlgo)
                                                : String.valueOf(((FrameRecordPreloader) frameLoader).getNAlgo()))
            );

            if (frameCount >= frameLoader.getFrameCount() - 1 && testImage == null)
                playbackComplete = true;
        }
        else {
            if (recordMode) { // record done
                appgc.setTitle(appname + " — Record done");
                aaframe.clear();
                aaframe.drawString("Record done. You may close the application.", 3, 2, colors.length - 1);
            }
            else {
                appgc.setTitle(appname + " — Playback complete");
                aaframe.clear();
                aaframe.drawString("Playback completed.", 3, 2, colors.length - 1);
                if (bamusic.playing()) bamusic.stop();

                if (showCredit) displayCredits();
            }
        }
    }

    @Override
    public void render(GameContainer gc, Graphics g) throws SlickException {
        screenG.clear();

        if (precalcDone && !playbackComplete) {
            if (testImage != null) {
                imageToAA.toAscii(testImage.getScaledCopy(w, h), aaframe);
            }
            else {
                if (frameCount >= 0)
                    frameLoader.setFrameBuffer(aaframe, (frameCount) < 0 ? 0 : frameCount);

                // fire music if not
                if (!musicFired && frameCount > 8) { // 8: audio sync hack
                    bamusic.play();
                    musicFired = true;
                }
            }
        }

        g.translate(FRAMESIZE, FRAMESIZE);
        renderFrame(g);
    }

    private final int IBM_GREEN = 1;
    private final int IBM_AMBER = 2;

    private final Color IBMGREEN = new Color(74, 255, 0);
    private final Color IBMAMBER = new Color(255, 183, 0);
    private final Color MONITOR_BASE = new Color(24, 24, 24);

    private static final int FRAMESIZE = 12;

    private void renderFrame(Graphics gg) {
        //g.setFont(font);
        screenG.setFont(drawFont);
        screenG.setBackground(colors[0]);

        blendNormal();

        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                char ch = aaframe.getChar(x, y);

                // regular char
                if (ch != 0 && ch != 32) {
                    screenG.setColor(getColor(aaframe.getColorKey(x, y)));
                    screenG.drawString(
                            Character.toString(ch),
                            fontW * x, fontH * y
                    );
                }
                else {
                    screenG.setColor(getColor(aaframe.getColorKey(x, y)));
                    screenG.fillRect(fontW * x, fontH * y, fontW, fontH);
                }
            }
        }

        // colour base
        if (monitorCol > 0) {
            screenG.setColor(MONITOR_BASE);
            blendScreen();
            screenG.fillRect(0f, 0f, fontW * w, fontH * h);
        }

        // colour overlay
        if (monitorCol > 0) {
            if (monitorCol == 1)
                screenG.setColor(IBMGREEN);
            else if (monitorCol == 2)
                screenG.setColor(IBMAMBER);
            else if (monitorCol == 3)
                screenG.setColor(customFilter);
            else
                throw new IllegalArgumentException("Unknown monitor mode: " + String.valueOf(monitorCol));

            blendMul();

            screenG.fillRect(0f, 0f, fontW * w, fontH * h);

        }

        blendNormal();

        gg.drawImage(screenBuffer, 0, 0);

        if (makeScreenRec) {
            try {
                ImageOut.write(screenBuffer, "./framerec/" + framename + String.format("%05d", frameCount) + ".png");
            }
            catch (Exception e) {
                System.err.print("An error occured while exporting hardcopy: ");
                e.printStackTrace();
                System.exit(1);
            }
        }

        screenG.flush();
    }

    public static void main(String[] args) {
        System.setProperty("java.library.path", "lib");
        System.setProperty("org.lwjgl.librarypath", new File("lib").getAbsolutePath());

        appname = (args.length > 0 && args[0] != null && args[0].length() > 0) ? args[0] : appnameDefault;

        try {
            appgc = new AppGameContainer(new BaAA());
            appgc.setShowFPS(false);
            appgc.setAlwaysRender(true);
            appgc.setUpdateOnlyWhenVisible(false);

            appgc.start();
        }
        catch (SlickException e) {
            e.printStackTrace();
        }
    }

    static int getBrightness(Color col) {
        return Math.max(Math.max(col.getRedByte(), col.getGreenByte()), col.getBlueByte());
    }

    public static Color getColor(int i) {
        return colors[i];
    }

    @Override
    public void keyPressed(int key, char c) {
        // pause
        if (key == 57 && precalcDone && !playbackComplete) { // SPACE
            isPaused = !isPaused;

            // pause music
            if (musicFired && isPaused) {
                bamusic.pause();
            }
            // resume paused music
            else if (musicFired) {
                bamusic.resume();
            }
        }

        // colour filters
        if (key == 59) // F1
            monitorCol = 0;
        else if (key == 60) // F2
            monitorCol = 1;
        else if (key == 61) // F3
            monitorCol = 2;
        else if (key == 62) // F4
            monitorCol = 3;

        if (!inverted)
            colors[0] = new Color(colcga[0], colcga[0], colcga[0]);
        else
            colors[1] = new Color(colcgaInv[1], colcgaInv[1], colcgaInv[1]);

        // capture
        if (key == 35) { // H
            try {
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-ddTHH-mm-ss");
                ImageOut.write(screenBuffer, "./" + sdf.toString() + ".png");
                System.out.println("Hardcopy exported as " + sdf.toString() + ".png");
            }
            catch (Exception e) {
                System.err.print("An error occured while exporting hardcopy: ");
                e.printStackTrace();
            }
        }
        else if (key == 20 && singleColour) { // T
            try {
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-ddTHH-mm-ss");
                String filename = "./" + sdf.toString() + ".txt";

                FileWriter writer = new FileWriter(new File(filename).getAbsoluteFile());
                for (int i = 0; i < aaframe.getSizeof() >>> 1; i++) {
                    if (i % w == 0 && i > 0){
                        writer.write("\n");
                        writer.flush();
                    }

                    writer.write(aaframe.getChar(i % w, i / w) & 0xFF);
                    writer.flush();
                }

                writer.close();

                System.out.println("Hardcopy (text) exported as " + sdf.toString() + ".txt");
            }
            catch (Exception e) {
                System.err.print("An error occured while exporting hardcopy (text): ");
                e.printStackTrace();
            }
        }
    }

    public static String getFontFileName() {
        return fontFileName;
    }

    public static String getAudioFileName() {
        return audioFileName;
    }

    public static String getFramename() {
        return framename;
    }

    public static int getColorsCount() {
        return colors.length;
    }

    public static int getDitherAlgo() {
        return ditherAlgo;
    }

    public static int getAlgorithm() {
        return algorithm;
    }

    public static boolean isFullCodePage() {
        return fullCodePage;
    }

    private void blendNormal() {
        // blend: NORMAL
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glColorMask(true, true, true, true);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
    }

    private void blendMul() {
        // blend: MULTIPLY
        // (protip: do NOT use "g.setDrawMode(Graphics.MODE_COLOR_MULTIPLY)"; it NEVER work as it should be!)
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glColorMask(true, true, true, true);
        GL11.glBlendFunc(GL11.GL_DST_COLOR, GL11.GL_ONE_MINUS_SRC_ALPHA);
    }

    private void blendScreen() {
        // blend: SCREEN
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glColorMask(true, true, true, true);
        GL11.glBlendFunc(GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_COLOR);
    }

    private int intNullSafe(String string, int i) {
        try {
            return new Integer(string);
        }
        catch (NumberFormatException e) {
            return i;
        }
    }

    /**
     * only useful if i := true
     * @param string
     * @param i
     * @return
     */
    private boolean booleanNullSafe(String string, boolean i) {
        if (string == null || string.length() < 1) return i;
        else return new Boolean(string);
    }

    private void displayCredits() {
        aaframe.drawString("Ba-AA", 3, 5, colors.length - 1);
        aaframe.drawString("Code by Torvald, 2016", 3, 7, colors.length - 1);
        aaframe.drawString("Please refer to ABOUT.md for the information.", 3, 8, colors.length - 1);
        aaframe.drawString("...and don't try to find out what \"BA\" means.", 3, 10, colors.length - 1);
    }

    public static void updateFramerate(int newrate) {
        framerate = newrate;
        frameLen = 1000.0 / framerate;
        appgc.setTargetFrameRate(newrate);
    }

    public static void updateDisplayMode(int width, int height, int glyphWidth, int glyphHeight) throws SlickException {
        appgc.setDisplayMode(width * glyphWidth + 2 * FRAMESIZE, height * glyphHeight + 2 * FRAMESIZE, false);
        aaframe = new AAFrame(width, height);
        w = width;
        h = height;
        fontW = glyphWidth;
        fontH = glyphHeight;
        screenBuffer = new Image(w * fontW, h * fontH);
        screenG = screenBuffer.getGraphics();

        appgc.getGraphics().clear(); // clear graphics context so that the frame draws on the correct position
        // (to future myself: actually I have no idea how it works, it just does the job)
    }

    public static void updateColours(int colourCount) {
        if (colourCount == 16) {
            singleColour = false;
            moreGrey = true;
            int size = hexadecaGrey.length;
            colors = new Color[size];
            for (int c = 0; c < size; c++) {
                if (!inverted)
                    colors[c] = new Color(hexadecaGrey[c], hexadecaGrey[c], hexadecaGrey[c]);
                else
                    colors[c] = new Color(hexadecaGreyInv[c], hexadecaGreyInv[c], hexadecaGreyInv[c]);
            }
        }
        else if (colourCount == 2) {
            singleColour = true;
            moreGrey = false;
            int size = 2;
            colors = new Color[size];
            for (int c = 0; c < size; c++) {
                if (!inverted)
                    colors[c] = new Color(colcga[c], colcga[c], colcga[c]);
                else
                    colors[c] = new Color(colcgaInv[c], colcgaInv[c], colcgaInv[c]);
            }
        }
        else {
            singleColour = false;
            moreGrey = false;
            int size = colcga.length;
            colors = new Color[size];
            for (int c = 0; c < size; c++) {
                if (!inverted)
                    colors[c] = new Color(colcga[c], colcga[c], colcga[c]);
                else
                    colors[c] = new Color(colcgaInv[c], colcgaInv[c], colcgaInv[c]);
            }
        }
    }

    private String[] decodeAxB(String string) {
        if (!string.contains("x")) throw new IllegalArgumentException("Wrong format! valid representation: 1337x6969");
        return string.split("x");
    }
}
