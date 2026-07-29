package razen.microforge;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.graphics.SpriteAPI;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.Fonts;
import com.fs.starfarer.api.ui.LabelAPI;
import com.fs.state.AppState;

import java.awt.Color;
import java.io.IOException;
import java.util.Map;
import javax.imageio.ImageIO;

import org.lwjgl.opengl.Display;

public class MicroforgeLoaderState implements AppState {
    public static final String STATE_ID = MicroforgeLoaderState.class.getCanonicalName();
    public static final String BAR_BG = "graphics/ui/loading_widget.png";
    public static final String BAR = "graphics/ui/loading_widget_glow.png";
    private static final String TITLE = "graphics/ui/starsector_title_alpha.png";
    private static final String STATUS_FONT = Fonts.ORBITRON_20AA;
    private SpriteAPI barBg;
    private SpriteAPI title;
    private SpriteAPI bar;
    private LabelAPI status;

    public String getID() {
        return STATE_ID;
    }

    public void init(Map session) throws Exception {
        Display.setVSyncEnabled(false);
        ImageIO.setUseCache(false);
        this.barBg = loadSprite(BAR_BG);
        this.title = loadSprite(TITLE);
        this.bar = loadSprite(BAR);
        Global.getSettings().loadFont(STATUS_FONT);
        this.status = Global.getSettings().createLabel("", STATUS_FONT);
        this.status.setAlignment(Alignment.MID);
        this.status.setColor(new Color(160, 220, 255, 230));
        this.renderBg();

        var buildProcessor = ModProcessor.fromRuntime();
        var targets = buildProcessor.findTargets();
        if (targets.isEmpty()) {
            return;
        }

        this.renderProgress(0.0F);
        buildProcessor.compileAll(targets, (target, completed, total) ->
                this.renderProgress((float) completed / total, target == null ? null : compilingMessage(target)));
    }

    public String traverse() {
        return "Title Screen State";
    }

    private void renderBg() {
        var titleYOffset = 48.0F;
        Renderer.clearFrame();
        var screenWidth = Renderer.logicalScreenWidth();
        var screenHeight = Renderer.logicalScreenHeight();
        Renderer.beginScreenProjection(0.0F, screenWidth, 0.0F, screenHeight);
        this.title.renderAtCenter(screenWidth / 2.0F, screenHeight / 2.0F + titleYOffset + 5.0F);
        this.barBg.renderAtCenter(screenWidth / 2.0F, screenHeight / 2.0F);
        Renderer.endScreenProjection();
        Display.update();
    }

    private void renderProgress(float progress) {
        renderProgress(progress, null);
    }

    private void renderProgress(float progress, String statusText) {
        var titleYOffset = 48.0F;
        Renderer.clearFrame();
        var screenWidth = Renderer.logicalScreenWidth();
        var screenHeight = Renderer.logicalScreenHeight();
        Renderer.beginScreenProjection(0.0F, screenWidth, 0.0F, screenHeight);
        this.title.renderAtCenter(screenWidth / 2.0F, screenHeight / 2.0F + titleYOffset + 5.0F);
        this.barBg.renderAtCenter(screenWidth / 2.0F, screenHeight / 2.0F);
        this.bar.renderRegionAtCenter(screenWidth / 2.0F, screenHeight / 2.0F, 0.0F, 0.0F, progress, 1.0F);
        this.renderStatus(statusText, screenWidth, screenHeight);
        Renderer.endScreenProjection();
        Display.update();
    }

    private void renderStatus(String text, float screenWidth, float screenHeight) {
        if (this.status == null || text == null || text.isBlank()) {
            return;
        }

        var maxWidth = Math.max(200.0F, screenWidth - 96.0F);
        this.status.setText(text);
        this.status.autoSizeToWidth(maxWidth);

        var width = this.status.getPosition().getWidth();
        var height = this.status.getPosition().getHeight();
        this.status.getPosition().setLocation((screenWidth - width) / 2.0F,
                screenHeight / 2.0F - 50.0F - height / 2.0F);
        this.status.render(1.0F);
    }

    private static String compilingMessage(ModBuildTarget target) {
        return "Compiling " + target.displayName() + "...";
    }

    private static SpriteAPI loadSprite(String path) throws IOException {
        Global.getSettings().loadTexture(path);
        return Global.getSettings().getSprite(path);
    }

    public void goToState(String stateId) {
    }
}
