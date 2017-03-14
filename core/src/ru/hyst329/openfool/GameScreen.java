package ru.hyst329.openfool;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.scenes.scene2d.Event;
import com.badlogic.gdx.scenes.scene2d.EventListener;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.actions.Actions;
import com.badlogic.gdx.scenes.scene2d.actions.MoveToAction;
import com.badlogic.gdx.utils.viewport.ScreenViewport;

import java.util.ArrayList;
import java.util.HashMap;

/**
 * Created by main on 13.03.2017.
 * Licensed under MIT License.
 */

public class GameScreen implements Screen, EventListener {
    private Stage stage;
    private OpenFoolGame game;
    private Suit trumpSuit;
    private Player[] players;
    private Card[] attackCards = new Card[DEAL_LIMIT], defenseCards = new Card[DEAL_LIMIT];
    private HashMap<Card, CardActor> cardActors = new HashMap<Card, CardActor>();
    private Deck deck = new Deck();

    private static final int DEAL_LIMIT = 6;
    private static final int PLAYER_COUNT = 4;
    private static final float CARD_SCALE_TABLE = 0.25f;
    private static final float CARD_SCALE_AI = 0.18f;
    private static final float CARD_SCALE_PLAYER = 0.3f;

    private static final float[] DECK_POSITION = {60, 240};
    private static final float[] PLAYER_POSITION = {240, 80};
    private static final float[] AI_POSITION = {60, 400};
    private static final float[] PLAYER_DELTA = {60, 0};
    private static final float[] AI_DELTA = {5, -5};

    public GameScreen(OpenFoolGame game) {
        this.game = game;
        // Initialise the stage
        stage = new Stage(new ScreenViewport());
        Gdx.input.setInputProcessor(stage);
        // Initialise card actors
        ArrayList<Card> cards = deck.getCards();
        for (int i = 0; i < cards.size(); i++) {
            Card c = cards.get(i);
            CardActor cardActor = new CardActor(game, c, "rus");
            cardActors.put(c, cardActor);
            stage.addActor(cardActor);
            cardActor.setZIndex(i);
        }
        // Initialise players
        // TODO: Replace with settings
        String[] playerNames = {"South", "West", "North", "East"};
        players = new Player[PLAYER_COUNT];
        for (int i = 0; i < PLAYER_COUNT; i++) {
            players[i] = new Player(this, playerNames[i], i);
            stage.addActor(players[i]);
        }
        // Starting the game
        for (CardActor cardActor : cardActors.values()) {
            cardActor.setFaceUp(false);
            cardActor.setScale(CARD_SCALE_TABLE);
            cardActor.setPosition(DECK_POSITION[0], DECK_POSITION[1]);
        }
        for (int i = 0; i < PLAYER_COUNT; i++) {
            drawCardsToPlayer(i, DEAL_LIMIT);
        }
        CardActor trump = cardActors.get(deck.getCards().get(0));
        trump.setRotation(-90.0f);
        trump.setFaceUp(true);
    }

    @Override
    public void show() {

    }

    @Override
    public void render(float delta) {
        Gdx.gl.glClearColor(0.5f, 1, 0, 1);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        stage.act(delta);
        stage.draw();
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

    public Suit getTrumpSuit() {
        return trumpSuit;
    }

    public int cardsRemaining() {
        return 0;
    }

    public Player[] getPlayers() {
        return players;
    }

    @Override
    public boolean handle(Event event) {
        if (event.getClass().isAssignableFrom(Player.CardThrownEvent.class)) {
            // TODO: Handle when card is thrown
        }
        if (event.getClass().isAssignableFrom(Player.CardBeatenEvent.class)) {
            // TODO: Handle when card is beaten
        }
        if (event.getClass().isAssignableFrom(Player.TakeEvent.class)) {
            // TODO: Handle when player takes
        }
        if (event.getClass().isAssignableFrom(Player.DoneEvent.class)) {
            // TODO: Handle when player says done
        }
        return true;
    }

    public Card[] getAttackCards() {
        return attackCards;
    }

    public Card[] getDefenseCards() {
        return defenseCards;
    }

    public void drawCardsToPlayer(int playerIndex, int cardCount) {
        for (int i = 0; i < cardCount; i++) {
            if (deck.getCards().isEmpty())
                break;
            Card card = deck.draw();
            Player player = players[playerIndex];
            player.addCard(card);
            CardActor cardActor = cardActors.get(card);
            cardActor.setFaceUp(playerIndex == 0);
            float[] position = playerIndex == 0 ? PLAYER_POSITION : AI_POSITION;
            if (playerIndex > 0)
                position[0] += (playerIndex - 1) * 640 / (PLAYER_COUNT - 2);
            float[] delta = playerIndex == 0 ? PLAYER_DELTA : AI_DELTA;
            int index = player.getHand().size() - 1;
            float posX = position[0] + index * delta[0];
            float posY = position[1] + index * delta[1];
            cardActor.addAction(Actions.moveTo(posX, posY, 0.4f));
            cardActor.setScale(playerIndex == 0 ? CARD_SCALE_PLAYER : CARD_SCALE_AI);
            cardActor.setZIndex(index);
        }
    }
}