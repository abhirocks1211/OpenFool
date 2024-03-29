package ru.hyst329.openfool

import com.badlogic.gdx.Game
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Preferences
import com.badlogic.gdx.assets.AssetManager
import com.badlogic.gdx.assets.loaders.I18NBundleLoader
import com.badlogic.gdx.assets.loaders.TextureLoader
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator.FreeTypeFontParameter
import com.badlogic.gdx.scenes.scene2d.ui.Skin

import com.badlogic.gdx.utils.I18NBundle
import com.kotcrab.vis.ui.VisUI
import com.kotcrab.vis.usl.USL

import java.util.Locale
import kotlin.properties.Delegates
import java.nio.file.Files.size

/**
 * Created by hyst329 on 12.03.2017.
 * Licensed under MIT License.
 */

class OpenFoolGame : Game() {
    internal var batch: SpriteBatch by Delegates.notNull()
    internal var assetManager: AssetManager by Delegates.notNull()
    internal var font: BitmapFont by Delegates.notNull()
    internal var smallFont: BitmapFont by Delegates.notNull()
    internal var preferences: Preferences by Delegates.notNull()
    internal var localeBundle: I18NBundle by Delegates.notNull()

    override fun create() {
        batch = SpriteBatch()
        assetManager = AssetManager()
        val generator = FreeTypeFontGenerator(Gdx.files.internal("fonts/8835.otf"))
        val parameter = FreeTypeFontParameter()
        var chars: String = ""
        for (i in 0x21..0x2040) {
            chars += i.toChar()
        }
        parameter.characters = chars
        parameter.size = 24
        font = generator.generateFont(parameter)
        parameter.size = 12
        smallFont = generator.generateFont(parameter)

        VisUI.load(Gdx.files.internal("ui/temp-skin.json"))
        preferences = Gdx.app.getPreferences("OpenFool")
        Gdx.input.isCatchBackKey = true
        // Deal with localisation
        var localeString: String? = preferences.getString("Language", null)
        val locale = if (localeString == null) Locale.getDefault() else Locale(localeString)
        assetManager.load("i18n/OpenFool", I18NBundle::class.java,
                I18NBundleLoader.I18NBundleParameter(locale))
        if (localeString == null) {
            localeString = locale.language
            preferences.putString("Language", localeString)
            preferences.flush()
        }
        assetManager.finishLoadingAsset<I18NBundle>("i18n/OpenFool")
        localeBundle = assetManager.get("i18n/OpenFool", I18NBundle::class.java)
        val param: TextureLoader.TextureParameter = TextureLoader.TextureParameter()
        param.minFilter = Texture.TextureFilter.MipMap
        param.genMipMaps = true
        val decks = arrayOf("fra", "int", "rus", "psu")
        val suits = "cdhs"
        val fullSuits = arrayOf("clubs", "diamonds", "hearts", "spades")
        for (d in decks) {
            for (i in 1..13) {
                for (s in suits.toCharArray()) {
                    assetManager.load("decks/$d/$i$s.png", Texture::class.java, param)
                }
            }
            assetManager.load("decks/$d/back.png", Texture::class.java, param)
        }
        for (i in 1..2) {
            assetManager.load("backgrounds/background$i.png", Texture::class.java, param)
        }
        for (s in fullSuits) {
            assetManager.load("suits/$s.png", Texture::class.java, param)
        }
        assetManager.load("holidays/hammersickle.png", Texture::class.java, param)
        assetManager.load("holidays/santahat.png", Texture::class.java, param)
        this.setScreen(MainMenuScreen(this))
    }

    override fun dispose() {
        batch.dispose()
        font.dispose()
        VisUI.dispose()
    }
}
