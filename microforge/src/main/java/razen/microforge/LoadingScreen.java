package razen.microforge;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.graphics.SpriteAPI;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.Fonts;
import com.fs.starfarer.api.ui.LabelAPI;
import org.apache.log4j.Logger;
import org.lwjgl.opengl.Display;

import javax.imageio.ImageIO;
import javax.swing.BorderFactory;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Frame;
import java.io.IOException;

final class LoadingScreen implements AutoCloseable {
    private static final Logger LOG = Logger.getLogger(LoadingScreen.class);

    private Backend backend;

    void initialize() throws Exception {
        // Fast Rendering does some crazy shenanigans with OpenGL, so fall back to Swing
        // if OpenGL is not available to us.
        if (hasCurrentOpenGlContext()) {
            backend = new OpenGlBackend();
        } else {
            LOG.info("The current thread has no OpenGL context; using the Swing loading dialog.");
            backend = new SwingBackend();
        }
        backend.initialize();
    }

    void render(Float progress, String statusText) {
        if (backend == null) {
            throw new IllegalStateException("loading screen is not initialized");
        }
        backend.render(progress, statusText);
    }

    @Override
    public void close() {
        if (backend != null) {
            backend.close();
        }
    }

    private static boolean hasCurrentOpenGlContext() {
        try {
            return Display.isCreated() && Display.isCurrent();
        } catch (Exception e) {
            LOG.warn("Could not inspect the current OpenGL context; using the Swing loading dialog.", e);
            return false;
        }
    }

    private interface Backend {
        void initialize() throws Exception;

        void render(Float progress, String statusText);

        default void close() {
        }
    }

    private static final class OpenGlBackend implements Backend {
        private static final String BAR_BACKGROUND = "graphics/ui/loading_widget.png";
        private static final String BAR = "graphics/ui/loading_widget_glow.png";
        private static final String TITLE = "graphics/ui/starsector_title_alpha.png";
        private static final String STATUS_FONT = Fonts.ORBITRON_20AA;

        private SpriteAPI barBackground;
        private SpriteAPI title;
        private SpriteAPI bar;
        private LabelAPI status;

        @Override
        public void initialize() throws IOException {
            Display.setVSyncEnabled(false);
            ImageIO.setUseCache(false);
            barBackground = loadSprite(BAR_BACKGROUND);
            title = loadSprite(TITLE);
            bar = loadSprite(BAR);
            Global.getSettings().loadFont(STATUS_FONT);
            status = Global.getSettings().createLabel("", STATUS_FONT);
            status.setAlignment(Alignment.MID);
            status.setColor(new Color(160, 220, 255, 230));
            render(null, null);
        }

        @Override
        public void render(Float progress, String statusText) {
            Renderer.clearFrame();
            var screenWidth = Renderer.logicalScreenWidth();
            var screenHeight = Renderer.logicalScreenHeight();
            Renderer.beginScreenProjection(0.0F, screenWidth, 0.0F, screenHeight);
            title.renderAtCenter(screenWidth / 2.0F, screenHeight / 2.0F + 53.0F);
            barBackground.renderAtCenter(screenWidth / 2.0F, screenHeight / 2.0F);
            if (progress != null) {
                bar.renderRegionAtCenter(screenWidth / 2.0F, screenHeight / 2.0F,
                        0.0F, 0.0F, progress, 1.0F);
            }
            renderStatus(statusText, screenWidth, screenHeight);
            Renderer.endScreenProjection();
            Display.update();
        }

        private void renderStatus(String text, float screenWidth, float screenHeight) {
            if (text == null || text.isBlank()) {
                return;
            }
            status.setText(text);
            status.autoSizeToWidth(Math.max(200.0F, screenWidth - 96.0F));
            var position = status.getPosition();
            position.setLocation((screenWidth - position.getWidth()) / 2.0F,
                    screenHeight / 2.0F - 50.0F - position.getHeight() / 2.0F);
            status.render(1.0F);
        }

        private static SpriteAPI loadSprite(String path) throws IOException {
            Global.getSettings().loadTexture(path);
            return Global.getSettings().getSprite(path);
        }
    }

    private static final class SwingBackend implements Backend {
        private static final int PROGRESS_SCALE = 1000;

        private JDialog dialog;
        private JLabel status;
        private JProgressBar progressBar;

        @Override
        public void initialize() throws Exception {
            runOnEventDispatchThreadAndWait(() -> {
                status = new JLabel("Preparing enabled mods...", SwingConstants.CENTER);

                progressBar = new JProgressBar(0, PROGRESS_SCALE);
                progressBar.setIndeterminate(true);
                progressBar.setPreferredSize(new Dimension(420, 24));

                var content = new JPanel(new BorderLayout(0, 12));
                content.setBorder(BorderFactory.createEmptyBorder(16, 18, 16, 18));
                content.add(status, BorderLayout.CENTER);
                content.add(progressBar, BorderLayout.SOUTH);

                dialog = new JDialog((Frame) null, "Microforge", false);
                dialog.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
                dialog.setAlwaysOnTop(true);
                dialog.setResizable(false);
                dialog.setContentPane(content);
                dialog.pack();
                dialog.setLocationRelativeTo(null);
                dialog.setVisible(true);
            });
        }

        @Override
        public void render(Float progress, String statusText) {
            SwingUtilities.invokeLater(() -> {
                if (dialog == null || !dialog.isDisplayable()) {
                    return;
                }
                if (progress != null) {
                    var clamped = Math.max(0.0F, Math.min(1.0F, progress));
                    progressBar.setIndeterminate(false);
                    progressBar.setValue(Math.round(clamped * PROGRESS_SCALE));
                    progressBar.setStringPainted(true);
                    progressBar.setString(Math.round(clamped * 100.0F) + "%");
                }
                if (statusText != null && !statusText.isBlank()) {
                    status.setText(statusText);
                }
            });
        }

        @Override
        public void close() {
            SwingUtilities.invokeLater(() -> {
                if (dialog != null) {
                    dialog.dispose();
                    dialog = null;
                }
            });
        }

        private static void runOnEventDispatchThreadAndWait(Runnable task) throws Exception {
            if (SwingUtilities.isEventDispatchThread()) {
                task.run();
            } else {
                SwingUtilities.invokeAndWait(task);
            }
        }
    }
}
