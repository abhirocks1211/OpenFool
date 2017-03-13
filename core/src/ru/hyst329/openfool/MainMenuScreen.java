package ru.hyst329.openfool;

import com.badlogic.gdx.Game;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Sprite;

import java.util.Locale;

/**
 * Created by hyst329 on 13.03.2017.
 * Licensed under MIT License.
 */

public class MainMenuScreen implements Screen {
    final OpenFoolGame game;

    public MainMenuScreen(OpenFoolGame game) {
        this.game = game;
    }

    @Override
    public void show() {

    }

    @Override
    public void render(float delta) {
        Gdx.gl.glClearColor(0.5f, 1, 0, 1);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        game.batch.begin();
        if (game.assetManager.update()) {
            Sprite king = new Sprite(game.assetManager.get("rus/13h.png", Texture.class));
            Sprite queen = new Sprite(game.assetManager.get("rus/12c.png", Texture.class));
            Sprite jack = new Sprite(game.assetManager.get("rus/11d.png", Texture.class));
            king.setScale(0.4f);
            queen.setScale(0.4f);
            jack.setScale(0.4f);
            king.setCenter(320, 240);
            queen.setCenter(400, 270);
            jack.setCenter(480, 240);
            king.setRotation(20);
            queen.setRotation(0);
            jack.setRotation(-20);
            king.draw(game.batch);
            queen.draw(game.batch);
            jack.draw(game.batch);
            game.font.draw(game.batch, "OpenFool", 400, 50);
        } else {
            game.font.draw(game.batch, String.format(Locale.ENGLISH, "Loading assets %d%%",
                    Math.round(game.assetManager.getProgress() * 100)), 20, 20);
        }
        game.batch.end();
    }

    @Override
    public void resize(int width, int height) {

    }

    @Override
    public void pause() {

    }

    @Override
    public void resume() {

    }

    @Override
    public void hide() {

    }

    @Override
    public void dispose() {

    }

    public void newGame() {
        game.setScreen(new GameScreen());
        dispose();
    }
}
