package ru.hyst329.openfool

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Screen
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.Sprite
import com.badlogic.gdx.scenes.scene2d.Action
import com.badlogic.gdx.scenes.scene2d.Event
import com.badlogic.gdx.scenes.scene2d.EventListener
import com.badlogic.gdx.scenes.scene2d.Group
import com.badlogic.gdx.scenes.scene2d.InputEvent
import com.badlogic.gdx.scenes.scene2d.InputListener
import com.badlogic.gdx.scenes.scene2d.Stage
import com.badlogic.gdx.scenes.scene2d.Touchable
import com.badlogic.gdx.scenes.scene2d.actions.Actions
import com.badlogic.gdx.utils.viewport.FitViewport
import mu.KotlinLogging

import java.security.SecureRandom
import java.util.ArrayList
import java.util.HashMap

import ru.hyst329.openfool.GameScreen.GameState.BEATEN
import ru.hyst329.openfool.GameScreen.GameState.BEATING
import ru.hyst329.openfool.GameScreen.GameState.DRAWING
import ru.hyst329.openfool.GameScreen.GameState.FINISHED
import ru.hyst329.openfool.GameScreen.GameState.READY
import ru.hyst329.openfool.GameScreen.GameState.THROWING
import ru.hyst329.openfool.GameScreen.GameState.THROWN
import ru.hyst329.openfool.ResultScreen.Result.TEAM_DRAW
import ru.hyst329.openfool.ResultScreen.Result.TEAM_LOST
import ru.hyst329.openfool.ResultScreen.Result.TEAM_PARTNER_LOST
import ru.hyst329.openfool.ResultScreen.Result.TEAM_WON
import ru.hyst329.openfool.ResultScreen.Result.DRAW
import ru.hyst329.openfool.ResultScreen.Result.LOST
import ru.hyst329.openfool.ResultScreen.Result.WON
import kotlin.math.min


/**
 * Created by main on 13.03.2017.
 * Licensed under MIT License.
 */

private val logger = KotlinLogging.logger {}

class GameScreen(private val game: OpenFoolGame) : Screen, EventListener {
    private val background: Texture
    private val backgroundColor: Color
    private val suitSymbols: Map<Suit, Sprite> = mapOf(
            Suit.SPADES to Sprite(game.assetManager.get("suits/spades.png", Texture::class.java)),
            Suit.DIAMONDS to Sprite(game.assetManager.get("suits/diamonds.png", Texture::class.java)),
            Suit.CLUBS to Sprite(game.assetManager.get("suits/clubs.png", Texture::class.java)),
            Suit.HEARTS to Sprite(game.assetManager.get("suits/hearts.png", Texture::class.java))
    )

    internal enum class GameState {
        READY,
        DRAWING,
        THROWING,
        THROWN,
        BEATING,
        BEATEN,
        FINISHED
    }

    private inner class GameStateChangedAction internal constructor(private val newState: GameState) : Action() {

        override fun act(delta: Float): Boolean {
            gameState = newState
            return true
        }
    }

    private inner class SortAction internal constructor(private val playerIndex: Int) : Action() {

        override fun act(delta: Float): Boolean {
            if (playerIndex == 0)
                sortPlayerCards()
            return true
        }
    }

    private val stage: Stage = Stage(FitViewport(800f, 480f))
    private val discardPileGroup: Group
    private val tableGroup: Group
    private val playerGroups: Array<Group>
    internal lateinit var trumpSuit: Suit
    internal val players: Array<Player>
    internal var attackCards = arrayOfNulls<Card>(DEAL_LIMIT)
        private set
    internal var defenseCards = arrayOfNulls<Card>(DEAL_LIMIT)
        private set
    private val cardActors = HashMap<Card, CardActor>()
    internal var ruleSet = RuleSet(game.preferences)
        private set
    private val deck = Deck(ruleSet.lowestRank)
    private var currentAttackerIndex: Int = 0
    private var currentThrowerIndex: Int = 0
    private var playersSaidDone: Int = 0
    private var isPlayerTaking: Boolean = false
    private val random = SecureRandom()
    private val outOfPlay: BooleanArray
    private val discardPile = ArrayList<Card>()
    private var gameState = DRAWING
    private var oldGameState = FINISHED
    private val sortingMode: Player.SortingMode
    private var throwLimit = DEAL_LIMIT
    private var playerDoneStatuses: BooleanArray
    internal var places = IntArray(DEAL_LIMIT) { 0 }

    init {
        // Initialise the stage
        Gdx.input.inputProcessor = stage
        // Get background color
        backgroundColor = Color(game.preferences.getInteger(SettingsScreen.BACKGROUND_COLOR, 0x33cc4dff))
        val backgroundNo = game.preferences.getInteger(SettingsScreen.BACKGROUND, 1)
        background = game.assetManager.get("backgrounds/background$backgroundNo.png", Texture::class.java)
        background.setWrap(Texture.TextureWrap.Repeat, Texture.TextureWrap.Repeat)
        // Initialise suit symbols
        for ((suit, sprite) in suitSymbols) {
            sprite.setCenter(Gdx.graphics.width / 20f, Gdx.graphics.height / 3.2f)
            sprite.setScale(0.5f)
        }
        // Initialise player info
        playerDoneStatuses = BooleanArray(ruleSet.playerCount)
        outOfPlay = BooleanArray(ruleSet.playerCount)
        val deckStyle = game.preferences.getString(SettingsScreen.DECK, "rus")
        sortingMode = Player.SortingMode.fromInt(game.preferences.getInteger(SettingsScreen.SORTING_MODE, 0))
        // Initialise groups
        tableGroup = Group()
        stage.addActor(tableGroup)
        val deckGroup = Group()
        stage.addActor(deckGroup)
        discardPileGroup = Group()
        stage.addActor(discardPileGroup)
        playerGroups = Array(ruleSet.playerCount) { Group() }
        for (i in 0 until ruleSet.playerCount) {
            playerGroups[i] = Group()
            stage.addActor(playerGroups[i])
        }
        // Add done/take listener
        stage.addListener(object : InputListener() {
            override fun touchDown(event: InputEvent?, x: Float, y: Float, pointer: Int, button: Int): Boolean {
                cardActors.values
                        .filter { it.x <= x && x <= it.x + it.width * it.scaleX && it.y <= y && y <= it.y + it.height * it.scaleY }
                        .forEach {
                            // We're clicked on a card
                            return true
                        }
                if (currentThrower.index == 0 && attackCards[0] != null) {
                    currentThrower.sayDone()
                    return true
                }
                if (currentDefender.index == 0) {
                    currentDefender.sayTake()
                    return true
                }
                return true
            }
        })
        // Initialise players
        // TODO: Replace with settings
        val playerNames = arrayOf("South", "West", "North", "East", "Center")
        players = Array(ruleSet.playerCount) { i -> Player(ruleSet, playerNames[i], i) }
        for (i in 0 until ruleSet.playerCount) {
            players[i].addListener(this)
            stage.addActor(players[i])
        }
        // Initialise card actors
        val cards = deck.cards
        val listener = object : InputListener() {
            override fun touchDown(event: InputEvent?, x: Float, y: Float, pointer: Int, button: Int): Boolean {
                // don't allow to act while throwing/beating
                if (gameState == THROWING || gameState == BEATING) return true
                if (event!!.target is CardActor) {
                    val cardActor = event.target as CardActor
                    logger.debug("Trying to click ${cardActor.card}\n")
                    val card = cardActor.card
                    val user = players[0]
                    if (!user.hand.contains(card)) {
                        System.out.printf("$card is not a user's card\n")
                        return true
                    }
                    if (currentThrower === user) {
                        user.throwCard(card, attackCards, defenseCards, trumpSuit)
                        return true
                    }
                    if (currentDefender === user) {
                        val nextPlayerHandSize = nextDefender.hand.size
                        if (user.cardCanBePassed(card, attackCards, defenseCards, nextPlayerHandSize))
                            user.passWithCard(card, attackCards, defenseCards, nextPlayerHandSize)
                        else user.beatWithCard(card, attackCards, defenseCards, trumpSuit)
                        return true
                    }
                    return false
                }
                return super.touchDown(event, x, y, pointer, button)
            }
        }
        for (i in cards!!.indices) {
            val c = cards[i]
            val cardActor = CardActor(game, c, deckStyle)
            cardActors.put(c, cardActor)
            cardActor.addListener(this)
            cardActor.addListener(listener)
            cardActor.touchable = Touchable.enabled
            deckGroup.addActor(cardActor)
            cardActor.zIndex = i
        }
        // Starting the game
        for (cardActor in cardActors.values) {
            cardActor.isFaceUp = false
            cardActor.setScale(CARD_SCALE_TABLE)
            cardActor.setPosition(DECK_POSITION[0], DECK_POSITION[1])
            // cardActor.setDebug(true);
        }
        // Determine trump
        val trumpCard = deck.cards?.get(0)
        val trump = cardActors[trumpCard]
        trump?.rotation = -90.0f
        trump?.isFaceUp = true
        trump?.moveBy(90 * CARD_SCALE_TABLE, 0f)
        trumpSuit = trumpCard!!.suit
        logger.debug("Trump suit is $trumpSuit")
        // Draw cards
        for (i in 0 until ruleSet.playerCount) {
            drawCardsToPlayer(i, DEAL_LIMIT)
        }
        // Determine the first attacker and thrower
        var lowestTrump = Rank.ACE
        var lowestTrumpCard = Card(Suit.SPADES, Rank.ACE)
        var firstAttacker = 0
        for (p in players) {
            for (c in p.hand) {
                if (c.suit === trumpSuit && (c.rank !== Rank.ACE && c.rank.value < lowestTrump.value || lowestTrump === Rank.ACE)) {
                    firstAttacker = p.index
                    lowestTrump = c.rank
                    lowestTrumpCard = c
                }
            }
        }

        if (firstAttacker != 0) {
            val showingTrump = cardActors[lowestTrumpCard]
            val z = showingTrump?.zIndex ?: 0
            showingTrump?.addAction(Actions.sequence(object : Action() {
                override fun act(delta: Float): Boolean {
                    showingTrump.isFaceUp = true
                    showingTrump.zIndex = 100
                    return true
                }
            }, Actions.delay(1.5f), object : Action() {
                override fun act(delta: Float): Boolean {
                    showingTrump.isFaceUp = false
                    showingTrump.zIndex = z
                    return true
                }
            }))
        }
        logger.debug(players[firstAttacker].name +
                " (${players[firstAttacker].index})" +
                " has the lowest trump $lowestTrump")
        currentAttackerIndex = firstAttacker
        currentThrowerIndex = firstAttacker
    }

    override fun show() {

    }

    override fun render(delta: Float) {
        Gdx.gl.glClearColor(backgroundColor.r, backgroundColor.g, backgroundColor.b, backgroundColor.a)
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT)
        val opponents =
                when {
                    ruleSet.teamPlay -> (if (outOfPlay[currentAttackerIndex]) 0 else 1) + if (outOfPlay[(currentAttackerIndex + 2) % ruleSet.playerCount]) 0 else 1
                    (outOfPlay.map { if (it) 0 else 1 }.fold(initial = 0) { total, current -> total + current }) > 2 -> 2
                    else -> 1
                }
        if (playersSaidDone == opponents && gameState != DRAWING
                && gameState != BEATING && gameState != THROWING) {
            logger.debug("Done - all players said done!")
            gameState = FINISHED
        }
        if (oldGameState != gameState) {
            logger.debug("Game state is $gameState")
            tintCards()
            oldGameState = gameState
        }
        when (gameState) {
            READY -> if (currentAttacker.index != 0) {
                throwLimit = min((if (ruleSet.loweredFirstDiscardLimit
                        && discardPile.isEmpty())
                    DEAL_LIMIT else DEAL_LIMIT - 1), currentDefender.hand.size)
                currentAttacker.startTurn(trumpSuit, cardsRemaining(), players.map { it.hand.size }.toTypedArray())
            }
            DRAWING -> {
            }
            THROWING -> {
            }
            THROWN -> {
                if (currentDefender.index != 0) {
                    if (!isPlayerTaking) {
                        currentDefender.tryBeat(attackCards, defenseCards, trumpSuit, cardsRemaining(), players.map { it.hand.size }.toTypedArray())
                    } else {
                        currentDefender.sayTake()
                    }
                }
                if (isPlayerTaking)
                    gameState = BEATEN
            }
            BEATING -> {
            }
            BEATEN -> {
                val forcedFinish =
                        if (currentDefender.hand.size == 0 || attackCards[throwLimit - 1] != null) {
                            logger.debug("Forced to finish the turn")
                            gameState = FINISHED
                            true
                        } else false
                if (currentThrower.index != 0) {
                    if (!forcedFinish)
                        currentThrower.throwOrDone(attackCards, defenseCards, trumpSuit, cardsRemaining(), players.map { it.hand.size }.toTypedArray())
                    else
                        currentThrower.sayDone()
                }
            }
            FINISHED -> {
                val playerTook = isPlayerTaking
                val currentDefenderIndex = currentDefender.index
                endTurn(if (isPlayerTaking) currentDefender.index else -1)
                if (isGameOver) {
                    logger.debug("GAME OVER")
                } else {
                    currentAttackerIndex += if (playerTook) 2 else 1
                    currentAttackerIndex %= ruleSet.playerCount
                    if (!ruleSet.teamPlay)
                    // Defender who took cannot attack anyway!
                        while (outOfPlay[currentAttackerIndex] ||
                                (playerTook && currentAttackerIndex == currentDefenderIndex)) {
                            currentAttackerIndex++
                            if (currentAttackerIndex == ruleSet.playerCount)
                                currentAttackerIndex = 0
                            if (isGameOver) break
                        }
                    currentThrowerIndex = currentAttackerIndex
                    logger.debug("${currentAttacker.name} (${currentAttacker.index}) -> ${currentDefender.name} (${currentDefender.index})")
                }
            }
        }
        // Draw background
        game.batch.begin()
        game.batch.color = backgroundColor
        game.batch.draw(background, 0f, 0f, 0, 0, Gdx.graphics.width, Gdx.graphics.height)
        game.batch.color = Color(Color.WHITE)
        game.batch.end()
        // Draw stage
        stage.act(delta)
        stage.draw()
        // Draw player labels
        game.batch.begin()
        for (i in 0 until ruleSet.playerCount) {
            val position = (if (i == 0) PLAYER_POSITION else AI_POSITION).clone()
            if (i > 0 && ruleSet.playerCount > 2)
                position[0] += ((i - 1) * 640 / (ruleSet.playerCount - 2)).toFloat()
            position[1] += 640 * if (i == 0) CARD_SCALE_PLAYER else CARD_SCALE_AI
            position[0] *= (Gdx.graphics.width / 800f)
            position[1] *= (Gdx.graphics.height / 480f)
            var playerFormat = "${players[i].name}: ${players[i].hand.size}"
            if (playerDoneStatuses[i]) playerFormat += "   " + game.localeBundle["PlayerDone"]
            if (isPlayerTaking && currentDefender.index == i)
                playerFormat += "   " + game.localeBundle["PlayerTakes"]
            game.font.draw(game.batch, playerFormat,
                    position[0], position[1])

        }

        game.font.draw(game.batch, "${cardsRemaining()}", Gdx.graphics.width / 10f, Gdx.graphics.height / 3f)
        var turnString = "${currentAttacker.name} -> ${currentDefender.name}"
        if (currentAttacker.index == 0)
            turnString += "\n" + game.localeBundle["YourTurn"]
        if (currentDefender.index == 0)
            turnString += "\n" + game.localeBundle["Defend"]
        suitSymbols[trumpSuit]?.draw(game.batch)
        game.font.draw(game.batch, turnString, Gdx.graphics.width / 40f, Gdx.graphics.height / 4.8f)
        game.batch.end()
        // Check if the game is over
        if (isGameOver) {
            var gameResult: ResultScreen.Result = if (outOfPlay[0]) WON else LOST
            if (outOfPlay.all { it })
                gameResult = DRAW
            if (ruleSet.teamPlay) gameResult = if (outOfPlay[0]) TEAM_WON else TEAM_LOST
            if (ruleSet.teamPlay && outOfPlay[1] && outOfPlay[3] && gameResult == TEAM_WON)
                gameResult = if (outOfPlay[2]) TEAM_DRAW else TEAM_PARTNER_LOST
            val playersPlaces: Map<Int, String> = players.map { (if (places[it.index] == 0) players.size else places[it.index]) to it.name }.toMap()
            game.screen = ResultScreen(game, gameResult, playersPlaces)
            dispose()
        }
    }

    override fun resize(width: Int, height: Int) {
        stage.viewport.update(width, height, true)
    }

    override fun pause() {

    }

    override fun resume() {

    }

    override fun hide() {

    }

    override fun dispose() {

    }

    private fun endTurn(playerIndex: Int) {
        playersSaidDone = 0
        playerDoneStatuses = BooleanArray(ruleSet.playerCount)
        val tableCards = ArrayList<Card>()
        for (i in attackCards.indices) {
            if (attackCards[i] != null) {
                tableCards.add(attackCards[i] as Card)
                //attackCards[i] = null;
            }
            if (defenseCards[i] != null) {
                tableCards.add(defenseCards[i] as Card)
                //defenseCards[i] = null;
            }
        }
        attackCards = arrayOfNulls(DEAL_LIMIT)
        defenseCards = arrayOfNulls(DEAL_LIMIT)
        if (playerIndex < 0) {
            for (card in tableCards) {
                val cardActor = cardActors[card]
                discardPile.add(card)
                discardPileGroup.addActor(cardActor)
                cardActor?.isFaceUp = false
                cardActor?.zIndex = discardPile.size - 1
                cardActor?.rotation = random.nextFloat() * 20 - 10
                val dx = random.nextFloat() * 20 - 10
                val dy = random.nextFloat() * 20 - 10
                cardActor?.addAction(
                        Actions.moveTo(DISCARD_PILE_POSITION[0] + dx, DISCARD_PILE_POSITION[1] + dy, 0.6f))
            }
        } else {
            val player = players[playerIndex]
            for (card in tableCards) {
                player.addCard(card)
                val cardActor = cardActors[card]
                cardActor?.isFaceUp = (playerIndex == 0)
                val position = (if (playerIndex == 0) PLAYER_POSITION else AI_POSITION).clone()
                if (playerIndex > 0 && ruleSet.playerCount > 2)
                    position[0] += ((playerIndex - 1) * 640 / (ruleSet.playerCount - 2)).toFloat()
                val delta = (if (playerIndex == 0) PLAYER_DELTA else AI_DELTA).clone()
                val index = player.hand.size - 1
                val posX = position[0] + index * delta[0]
                val posY = position[1] + index * delta[1]
                cardActor?.addAction(Actions.moveTo(posX, posY, 0.4f))
                cardActor?.rotation = 0.0f
                cardActor?.setScale(if (playerIndex == 0) CARD_SCALE_PLAYER else CARD_SCALE_AI)
                playerGroups[playerIndex].addActor(cardActor)
                cardActor?.zIndex = index
            }
            player.addAction(Actions.sequence(
                    Actions.delay(0.39f),
                    SortAction(playerIndex)
            ))
        }
        if (deck.cards?.isEmpty() == false) {
            for (i in 0 until ruleSet.playerCount) {
                val cardsToDraw = DEAL_LIMIT - players[i].hand.size
                if (cardsToDraw > 0) {
                    drawCardsToPlayer(i, cardsToDraw)
                }
                if (deck.cards?.isEmpty() != false)
                    break
            }
        }
        // Check if someone is out of play
        if (deck.cards?.isEmpty() != false) {
            for (i in 0 until ruleSet.playerCount) {
                val oldOutOfPlay = outOfPlay[i]
                outOfPlay[i] = players[i].hand.size == 0
                if (outOfPlay[i] && !oldOutOfPlay) {
                    places[i] = outOfPlay.count { it }
                }
            }
        }
        isPlayerTaking = false
        gameState = READY
    }

    fun tintCards() {
        // Tint the available cards for throwing/beating
        for (a in cardActors.values) {
            a.untint()
        }
        when {
            currentThrower === players[0] && attackCards[0] != null -> {
                for (c in players[0].hand) {
                    val actor = cardActors[c]
                    // If the card cannot be thrown, grey it out
                    if (!players[0].cardCanBeThrown(c, attackCards, defenseCards))
                        actor?.tint(Color.GRAY)
                }
            }
            currentDefender === players[0] -> {
                for (c in players[0].hand) {
                    val actor = cardActors[c]
                    // If the card cannot beat, grey it out
                    if (!players[0].cardCanBeBeaten(c, attackCards, defenseCards, trumpSuit))
                        actor?.tint(Color.GRAY)
                    // If the card can be passed, tint it green
                    if (players[0].cardCanBePassed(c, attackCards, defenseCards, nextDefender.hand.size))
                        actor?.tint(Color.GREEN)
                }
            }
            else -> {
            }
        }
    }

    private // TODO: Generalise
    val isGameOver: Boolean
        get() {
            if (ruleSet.teamPlay)
                return outOfPlay[0] && outOfPlay[2] || outOfPlay[1] && outOfPlay[3]
            else {
                // Simply check if only one player remains
                return (outOfPlay.map { if (it) 0 else 1 }.fold(initial = 0) { total, current -> total + current }) <= 1
            }
        }

    val areAllCardsBeaten: Boolean
        get() {
            return (0 until DEAL_LIMIT).map { (attackCards[it] != null) == (defenseCards[it] != null) }
                    .fold(initial = true) { total, current -> total && current }
        }

    internal fun cardsRemaining(): Int {
        return deck.cards?.size ?: 0
    }

    override fun handle(event: Event): Boolean {
        if (event is Player.CardThrownEvent) {
            // Handle when card is thrown
            playersSaidDone = 0
            playerDoneStatuses = BooleanArray(ruleSet.playerCount)
            var throwIndex = 0
            while (attackCards[throwIndex] != null) throwIndex++
            val throwCard = event.card
            attackCards[throwIndex] = throwCard
            val throwCardActor = cardActors[throwCard]
            throwCardActor?.isFaceUp = true
            tableGroup.addActor(throwCardActor)
            throwCardActor?.zIndex = 2 * throwIndex
            throwCardActor?.setScale(CARD_SCALE_TABLE)
            val throwPos = TABLE_POSITION.clone()
            throwPos[0] += (90 * throwIndex).toFloat()
            throwCardActor?.addAction(Actions.sequence(
                    GameStateChangedAction(GameState.THROWING),
                    Actions.moveTo(throwPos[0], throwPos[1], 0.4f),
                    Actions.delay(0.2f),
                    GameStateChangedAction(GameState.THROWN)))
            val thrower = event.getTarget() as Player
            logger.debug("${thrower.name} (${thrower.index}) throws $throwCard")
            for (i in 0 until thrower.hand.size) {
                val cardActor = cardActors[thrower.hand[i]]
                val position = (if (thrower.index == 0) PLAYER_POSITION else AI_POSITION).clone()
                val delta = (if (thrower.index == 0) PLAYER_DELTA else AI_DELTA).clone()
                if (thrower.index > 0 && ruleSet.playerCount > 2)
                    position[0] += ((thrower.index - 1) * 640 / (ruleSet.playerCount - 2)).toFloat()
                val posX = position[0] + i * delta[0]
                val posY = position[1] + i * delta[1]
                //cardActor.addAction(Actions.moveTo(posX, posY, 0.1f));
                cardActor?.setPosition(posX, posY)
                cardActor?.rotation = 0.0f
                cardActor?.setScale(if (thrower.index == 0) CARD_SCALE_PLAYER else CARD_SCALE_AI)
                cardActor?.zIndex = i
            }
            return true
        }
        if (event is Player.CardBeatenEvent) {
            // Handle when card is beaten
            playersSaidDone = 0
            playerDoneStatuses = BooleanArray(ruleSet.playerCount)
            var beatIndex = 0
            while (defenseCards[beatIndex] != null) beatIndex++
            val beatCard = event.card
            defenseCards[beatIndex] = beatCard
            val beatCardActor = cardActors[beatCard]
            beatCardActor?.isFaceUp = true
            tableGroup.addActor(beatCardActor)
            beatCardActor?.zIndex = 2 * beatIndex + 1
            beatCardActor?.setScale(CARD_SCALE_TABLE)
            val beatPos = TABLE_POSITION.clone()
            beatPos[0] += (90 * beatIndex).toFloat()
            beatCardActor?.addAction(Actions.sequence(
                    GameStateChangedAction(BEATING),
                    Actions.moveTo(beatPos[0] + TABLE_DELTA[0], beatPos[1] + TABLE_DELTA[1], 0.4f),
                    Actions.delay(0.2f),
                    GameStateChangedAction(if (areAllCardsBeaten) BEATEN else THROWN)))
            val beater = event.getTarget() as Player
            logger.debug("${beater.name} (${beater.index}) beats with $beatCard\n")
            for (i in 0 until beater.hand.size) {
                val cardActor = cardActors[beater.hand[i]]
                val position = (if (beater.index == 0) PLAYER_POSITION else AI_POSITION).clone()
                val delta = (if (beater.index == 0) PLAYER_DELTA else AI_DELTA).clone()
                if (beater.index > 0 && ruleSet.playerCount > 2)
                    position[0] += ((beater.index - 1) * 640 / (ruleSet.playerCount - 2)).toFloat()
                val posX = position[0] + i * delta[0]
                val posY = position[1] + i * delta[1]
                //cardActor.addAction(Actions.moveTo(posX, posY, 0.1f));
                cardActor?.setPosition(posX, posY)
                cardActor?.rotation = 0.0f
                cardActor?.setScale(if (beater.index == 0) CARD_SCALE_PLAYER else CARD_SCALE_AI)
                cardActor?.zIndex = i
            }
            return true
        }
        if (event is Player.CardPassedEvent) {
            //TODO("Passing not yet implemented")
            // Handle when player passes
            playersSaidDone = 0
            playerDoneStatuses = BooleanArray(ruleSet.playerCount)
            var passIndex = 0
            while (attackCards[passIndex] != null) passIndex++
            val passCard = event.card
            attackCards[passIndex] = passCard
            val passCardActor = cardActors[passCard]
            passCardActor?.isFaceUp = true
            tableGroup.addActor(passCardActor)
            passCardActor?.zIndex = 2 * passIndex
            passCardActor?.setScale(CARD_SCALE_TABLE)
            val passPos = TABLE_POSITION.clone()
            passPos[0] += (90 * passIndex).toFloat()
            passCardActor?.addAction(Actions.sequence(
                    GameStateChangedAction(GameState.THROWING),
                    Actions.moveTo(passPos[0], passPos[1], 0.4f),
                    Actions.delay(0.2f),
                    GameStateChangedAction(GameState.THROWN)))
            val thrower = event.getTarget() as Player
            logger.debug("${thrower.name} (${thrower.index}) passes with $passCard\n")
            for (i in 0 until thrower.hand.size) {
                val cardActor = cardActors[thrower.hand[i]]
                val position = (if (thrower.index == 0) PLAYER_POSITION else AI_POSITION).clone()
                val delta = (if (thrower.index == 0) PLAYER_DELTA else AI_DELTA).clone()
                if (thrower.index > 0 && ruleSet.playerCount > 2)
                    position[0] += ((thrower.index - 1) * 640 / (ruleSet.playerCount - 2)).toFloat()
                val posX = position[0] + i * delta[0]
                val posY = position[1] + i * delta[1]
                //cardActor.addAction(Actions.moveTo(posX, posY, 0.1f));
                cardActor?.setPosition(posX, posY)
                cardActor?.rotation = 0.0f
                cardActor?.setScale(if (thrower.index == 0) CARD_SCALE_PLAYER else CARD_SCALE_AI)
                cardActor?.zIndex = i
            }
            do {
                currentAttackerIndex++
                if (currentAttackerIndex == players.size) currentAttackerIndex = 0
            } while (outOfPlay[currentAttackerIndex])
            currentThrowerIndex = currentAttackerIndex
            logger.debug("Passed to: ${currentAttacker.name} -> ${currentDefender.name}")
        }
        if (event is Player.TakeEvent) {
            // Handle when player takes
            playersSaidDone = 0
            playerDoneStatuses = BooleanArray(ruleSet.playerCount)
            isPlayerTaking = true
            val player = event.getTarget() as Player
            logger.debug("${player.name} (${player.index}) decides to take")
            return true

        }
        if (event is Player.DoneEvent) {
            // Handle when player says done
            playersSaidDone++
            currentThrowerIndex += 2
            currentThrowerIndex %= ruleSet.playerCount
            val player = event.getTarget() as Player
            playerDoneStatuses[player.index] = true
            logger.debug("${player.name} (${player.index}) says done\n")
            return true
        }
        return false
    }

    private fun drawCardsToPlayer(playerIndex: Int, cardCount: Int) {
        val player = players[playerIndex]
        if (!deck.cards!!.isEmpty()) {
            player.addAction(Actions.sequence(
                    GameStateChangedAction(GameState.DRAWING),
                    Actions.delay(0.39f),
                    GameStateChangedAction(READY),
                    SortAction(playerIndex)
            ))
        }
        for (i in 0 until cardCount) {
            if (deck.cards!!.isEmpty())
                break
            val card = deck.draw() as Card
            player.addCard(card)
            val cardActor = cardActors[card]
            cardActor?.isFaceUp = (playerIndex == 0)
            val position = (if (playerIndex == 0) PLAYER_POSITION else AI_POSITION).clone()
            if (playerIndex > 0 && ruleSet.playerCount > 2)
                position[0] += ((playerIndex - 1) * 640 / (ruleSet.playerCount - 2)).toFloat()
            val delta = (if (playerIndex == 0) PLAYER_DELTA else AI_DELTA).clone()
            val index = player.hand.size - 1
            val posX = position[0] + index * delta[0]
            val posY = position[1] + index * delta[1]
            cardActor?.addAction(Actions.moveTo(posX, posY, 0.4f))
            cardActor?.rotation = 0.0f
            cardActor?.setScale(if (playerIndex == 0) CARD_SCALE_PLAYER else CARD_SCALE_AI)
            playerGroups[playerIndex].addActor(cardActor)
            cardActor?.zIndex = index
        }
    }

    private val currentAttacker: Player
        get() {
            if (outOfPlay[currentAttackerIndex]) {
                return players[(currentAttackerIndex + 2) % ruleSet.playerCount]
            }
            return players[currentAttackerIndex]
        }

    private val currentDefender: Player
        get() {
            var currentDefenderIndex = (currentAttackerIndex + 1) % ruleSet.playerCount
            if (!ruleSet.teamPlay)
                while (outOfPlay[currentDefenderIndex]) {
                    currentDefenderIndex++
                    if (currentDefenderIndex == ruleSet.playerCount)
                        currentDefenderIndex = 0
                }
            else if (outOfPlay[currentDefenderIndex]) {
                return players[(currentDefenderIndex + 2) % ruleSet.playerCount]
            }
            return players[currentDefenderIndex]
        }

    private val nextDefender: Player
        get() {
            var nextDefenderIndex = (currentDefender.index + 1) % ruleSet.playerCount
            if (!ruleSet.teamPlay)
                while (outOfPlay[nextDefenderIndex]) {
                    nextDefenderIndex++
                    if (nextDefenderIndex == ruleSet.playerCount)
                        nextDefenderIndex = 0
                }
            else if (outOfPlay[nextDefenderIndex]) {
                return players[(nextDefenderIndex + 2) % ruleSet.playerCount]
            }
            return players[nextDefenderIndex]
        }


    private val currentThrower: Player
        get() {
            if (outOfPlay[currentThrowerIndex]) {
                return players[(currentThrowerIndex + 2) % ruleSet.playerCount]
            }
            return players[currentThrowerIndex]
        }

    private fun sortPlayerCards() {
        // TODO: Generalise to other players
        val player = players[0]
        player.sortCards(sortingMode, trumpSuit)
        // Reposition all cards
        for (i in 0 until player.hand.size) {
            val cardActor = cardActors[player.hand[i]]
            val position = (if (player.index == 0) PLAYER_POSITION else AI_POSITION).clone()
            val delta = (if (player.index == 0) PLAYER_DELTA else AI_DELTA).clone()
            if (player.index > 0)
                position[0] += ((player.index - 1) * 640 / (ruleSet.playerCount - 2)).toFloat()
            val posX = position[0] + i * delta[0]
            val posY = position[1] + i * delta[1]
            cardActor?.setPosition(posX, posY)
            cardActor?.rotation = 0.0f
            cardActor?.setScale(if (player.index == 0) CARD_SCALE_PLAYER else CARD_SCALE_AI)
            cardActor?.zIndex = i
        }
    }

    companion object {

        private val DEAL_LIMIT = 6
        private val CARD_SCALE_TABLE = 0.24f
        private val CARD_SCALE_AI = 0.18f
        private val CARD_SCALE_PLAYER = 0.28f
        // Half-widths and half-heights
        private val HW_TABLE = CARD_SCALE_TABLE * 180
        private val HW_AI = CARD_SCALE_AI * 180
        private val HW_PLAYER = CARD_SCALE_PLAYER * 180
        private val HH_TABLE = CARD_SCALE_TABLE * 270
        private val HH_AI = CARD_SCALE_AI * 270
        private val HH_PLAYER = CARD_SCALE_PLAYER * 270

        private val DECK_POSITION = floatArrayOf(60 - HW_TABLE, 240 - HH_TABLE)
        private val DISCARD_PILE_POSITION = floatArrayOf(680 - HW_TABLE, 180 - HH_TABLE)
        private val PLAYER_POSITION = floatArrayOf(240 - HW_PLAYER, 80 - HH_PLAYER)
        private val AI_POSITION = floatArrayOf(60 - HW_AI, 400 - HH_AI)
        private val TABLE_POSITION = floatArrayOf(200 - HW_TABLE, 280 - HH_TABLE)
        private val TABLE_DELTA = floatArrayOf(10f, -10f)
        private val PLAYER_DELTA = floatArrayOf(40f, 0f)
        private val AI_DELTA = floatArrayOf(5f, -5f)
    }
}
