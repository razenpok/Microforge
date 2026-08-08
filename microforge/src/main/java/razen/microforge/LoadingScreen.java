package razen.microforge;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.graphics.SpriteAPI;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.Fonts;
import com.fs.starfarer.api.ui.LabelAPI;
import org.lwjgl.opengl.Display;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.io.IOException;

final class LoadingScreen {
    private static final String BAR_BACKGROUND = "graphics/ui/loading_widget.png";
    private static final String BAR = "graphics/ui/loading_widget_glow.png";
    private static final String TITLE = "graphics/ui/starsector_title_alpha.png";
    private static final String STATUS_FONT = Fonts.ORBITRON_20AA;

    private SpriteAPI barBackground;
    private SpriteAPI title;
    private SpriteAPI bar;
    private LabelAPI status;

    void initialize() throws IOException {
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

    void render(Float progress, String statusText) {
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
