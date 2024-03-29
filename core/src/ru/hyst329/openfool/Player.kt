package ru.hyst329.openfool

import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.Event

import java.util.ArrayList
import java.util.Arrays
import java.util.Collections
import java.util.Comparator

/**
 * Created by main on 13.03.2017.
 * Licensed under MIT License.
 */

import mu.KotlinLogging

private val logger = KotlinLogging.logger {}

class Player internal constructor(private val ruleSet: RuleSet, private var name: String?, val index: Int) : Actor() {
    val hand = ArrayList<Card>()

    internal inner class CardThrownEvent(val card: Card) : Event()

    internal inner class CardBeatenEvent(val card: Card) : Event()

    internal inner class CardPassedEvent(val card: Card) : Event()

    internal inner class DoneEvent : Event()

    internal inner class TakeEvent : Event()

    enum class SortingMode constructor(val value: Int) {
        UNSORTED(0),
        SUIT_ASCENDING(1),
        SUIT_DESCENDING(2),
        RANK_ASCENDING(3),
        RANK_DESCENDING(4);


        companion object {

            fun fromInt(value: Int, default: SortingMode = UNSORTED): SortingMode {
                for (type in SortingMode.values()) {
                    if (type.value == value) {
                        return type
                    }
                }
                return default
            }
        }
    }

    private fun handValue(hand: ArrayList<Card>, trumpSuit: Suit, cardsRemaining: Int, playerHands: Array<Int>): Int {

        if (cardsRemaining == 0 && hand.size == 0) {
            return OUT_OF_PLAY
        }
        val bonuses = doubleArrayOf(0.0, 0.0, 0.5, 0.75, 1.25) // for cards of same rank
        var res = 0
        val countsByRank = IntArray(13)
        val countsBySuit = IntArray(4)
        for (c in hand) {
            val r = c.rank
            val s = c.suit
            res += ((relativeCardValue(r.value)) * RANK_MULTIPLIER).toInt()
            if (s === trumpSuit)
                res += 13 * RANK_MULTIPLIER
            countsByRank[r.value - 1]++
            countsBySuit[s.value]++
        }
        for (i in 1..13) {
            res += (Math.max(relativeCardValue(i), 1.0) * bonuses[countsByRank[i - 1]]).toInt()
        }
        var avgSuit = 0.0
        for (c in hand) {
            if (c.suit !== trumpSuit)
                avgSuit++
        }
        avgSuit /= 3.0
        for (s in Suit.values()) {
            if (s !== trumpSuit) {
                val dev = Math.abs((countsBySuit[s.value] - avgSuit) / avgSuit)
                res -= (UNBALANCED_HAND_PENALTY * dev).toInt()
            }
        }
        var cardsInPlay = cardsRemaining
        for (p in playerHands)
            cardsInPlay += p
        cardsInPlay -= hand.size
        val cardRatio = if (cardsInPlay != 0) (hand.size / cardsInPlay).toDouble() else 10.0
        res += ((0.25 - cardRatio) * MANY_CARDS_PENALTY).toInt()
        return res
    }

    fun currentHandValue(trumpSuit: Suit, cardsRemaining: Int, playerHands: Array<Int>): Int {
        return handValue(hand, trumpSuit, cardsRemaining, playerHands)
    }


    override fun getName(): String {
        return name ?: ""
    }

    override fun setName(name: String) {
        this.name = name
    }

    fun addCard(c: Card) {
        hand.add(c)
    }

    fun startTurn(trumpSuit: Suit,
                  cardsRemaining: Int,
                  playerHands: Array<Int>) {
        val bonuses = doubleArrayOf(0.0, 0.0, 1.0, 1.5, 2.5)
        val countsByRank = IntArray(13)
        for (c in hand) {
            countsByRank[c.rank.value - 1]++
        }
        var maxVal = Integer.MIN_VALUE
        var cardIdx = -1
        for (i in hand.indices) {
            val newHand = ArrayList(hand)
            val c = hand[i]
            newHand.removeAt(i)
            val r = c.rank
            val newVal = handValue(newHand, trumpSuit, cardsRemaining, playerHands) + Math.round(bonuses[countsByRank[r.value - 1]] * (if (r === Rank.ACE) 6 else r.value - 8).toDouble() * RANK_MULTIPLIER.toDouble()).toInt()
            if (newVal > maxVal) {
                maxVal = newVal
                cardIdx = i
            }
        }
        val c = hand[cardIdx]
        hand.removeAt(cardIdx)
        fire(CardThrownEvent(c))
    }

    fun throwOrDone(attackCards: Array<Card?>,
                    defenseCards: Array<Card?>,
                    trumpSuit: Suit,
                    cardsRemaining: Int,
                    playerHands: Array<Int>
    ) {
        val ranksPresent = BooleanArray(13)
        for (c in attackCards) {
            if (c != null)
                ranksPresent[c.rank.value - 1] = true
        }
        for (c in defenseCards) {
            if (c != null)
                ranksPresent[c.rank.value - 1] = true
        }
        // TODO: Remove duplication
        val bonuses = doubleArrayOf(0.0, 0.0, 1.0, 1.5, 2.5)
        val countsByRank = IntArray(13)
        for (c in hand) {
            countsByRank[c.rank.value - 1]++
        }
        var maxVal = Integer.MIN_VALUE
        var cardIdx = -1
        for (i in hand.indices) {
            val c = hand[i]
            val r = c.rank
            if (!ranksPresent[r.value - 1])
                continue
            val newHand = ArrayList(hand)
            newHand.removeAt(i)
            val newVal = handValue(newHand, trumpSuit, cardsRemaining, playerHands) + Math.round(bonuses[countsByRank[r.value - 1]] * (if (r === Rank.ACE) 6 else r.value - 8).toDouble() * RANK_MULTIPLIER.toDouble()).toInt()
            if (newVal > maxVal) {
                maxVal = newVal
                cardIdx = i
            }
        }
        val PENALTY_BASE = 1200
        val PENALTY_DELTA = 50
        if (currentHandValue(trumpSuit, cardsRemaining, playerHands) - maxVal <
                PENALTY_BASE - PENALTY_DELTA * cardsRemaining && cardIdx >= 0) {
            val c = hand[cardIdx]
            hand.removeAt(cardIdx)
            fire(CardThrownEvent(c))
        } else {
            fire(DoneEvent())
        }
    }


    fun tryBeat(attackCards: Array<Card?>,
                defenseCards: Array<Card?>,
                trumpSuit: Suit,
                cardsRemaining: Int,
                playerHands: Array<Int>) {
        val RANK_PRESENT_BONUS = 300
        val ranksPresent = BooleanArray(13)
        val handIfTake = ArrayList(hand)
        for (c in attackCards) {
            if (c != null) {
                ranksPresent[c.rank.value - 1] = true
                handIfTake.add(c)
            }
        }
        for (c in defenseCards) {
            if (c != null) {
                ranksPresent[c.rank.value - 1] = true
                handIfTake.add(c)
            }
        }
        val canPass = hand.any {
            cardCanBePassed(it, attackCards, defenseCards, playerHands[(index + 1) % playerHands.size])
        }
        logger.debug("Can${if(canPass) "" else "not"} pass")
        var maxVal = Integer.MIN_VALUE
        var cardIdx = -1
        print("Attack cards: ")
        for (i in 0..attackCards.size - 1) {
            val card = attackCards[i]
            logger.debug("${card ?: "null"} ")
        }
        val index = Arrays.asList<Card>(*defenseCards).indexOf(null)
        val attack = attackCards[index]
        logger.debug("Index = ${index} attack is ${attack ?: "null"}")
        for (i in hand.indices) {
            val c = hand[i]
            if (c.beats(attack!!, trumpSuit, ruleSet.deuceBeatsAce)) {
                val r = c.rank
                val newHand = ArrayList(hand)
                newHand.removeAt(i)
                val newVal = handValue(newHand, trumpSuit, cardsRemaining, playerHands) + RANK_PRESENT_BONUS * if (ranksPresent[r.value - 1]) 1 else 0
                if (newVal > maxVal) {
                    maxVal = newVal
                    cardIdx = i
                }
            }
        }
        val PENALTY = 800
        val TAKE_PENALTY_BASE = 2000
        val TAKE_PENALTY_DELTA = 40
        val PASS_PENALTY = -400

        if (canPass) {
            // try to pass if can
            val handIfPass = ArrayList(hand.filter { it.rank != attack?.rank })
            val handIfPassNoTrump = ArrayList(hand.filter { it.rank != attack?.rank || it.suit == trumpSuit })
            val betterHandValue = Math.max(handValue(handIfPass, trumpSuit, cardsRemaining, playerHands),
                    handValue(handIfPassNoTrump, trumpSuit, cardsRemaining, playerHands))
            if(betterHandValue > currentHandValue(trumpSuit, cardsRemaining, playerHands) + PASS_PENALTY) {
                // pass with non-trump if possible
                val passableCards = hand.filter { it.rank == attack?.rank }
                val passedCard = passableCards.firstOrNull { it.suit != trumpSuit } ?: passableCards.first()
                hand.remove(passedCard)
                fire(CardPassedEvent(passedCard))
                return
            }
        }
        if ((currentHandValue(trumpSuit, cardsRemaining, playerHands) - maxVal < PENALTY
                        || handValue(handIfTake, trumpSuit, cardsRemaining, playerHands) - maxVal < TAKE_PENALTY_BASE - TAKE_PENALTY_DELTA * cardsRemaining || cardsRemaining == 0) && cardIdx >= 0) {
            val c = hand[cardIdx]
            hand.removeAt(cardIdx)
            fire(CardBeatenEvent(c))
        } else {
            fire(TakeEvent())
        }
    }

    fun clearHand() {
        hand.clear()
    }

    fun cardCanBeThrown(c: Card,
                        attackCards: Array<Card?>,
                        defenseCards: Array<Card?>): Boolean {
        val ranksPresent = BooleanArray(13)
        for (card in attackCards) {
            if (card != null)
                ranksPresent[card.rank.value - 1] = true
        }
        for (card in defenseCards) {
            if (card != null)
                ranksPresent[card.rank.value - 1] = true
        }
        return hand.contains(c) && (ranksPresent[c.rank.value - 1] || Arrays.equals(attackCards, arrayOfNulls<Card>(6)))
    }

    fun throwCard(c: Card,
                  attackCards: Array<Card?>,
                  defenseCards: Array<Card?>,
                  trumpSuit: Suit) {
        if (cardCanBeThrown(c, attackCards, defenseCards)) {
            hand.remove(c)
            fire(CardThrownEvent(c))
        }
    }

    fun cardCanBeBeaten(c: Card,
                        attackCards: Array<Card?>,
                        defenseCards: Array<Card?>,
                        trumpSuit: Suit): Boolean {
        val attack = attackCards[Arrays.asList<Card>(*defenseCards).indexOf(null)] ?: return false
        return hand.contains(c) && c.beats(attack, trumpSuit, ruleSet.deuceBeatsAce)
    }

    fun beatWithCard(c: Card,
                     attackCards: Array<Card?>,
                     defenseCards: Array<Card?>,
                     trumpSuit: Suit) {
        if (cardCanBeBeaten(c, attackCards, defenseCards, trumpSuit)) {
            hand.remove(c)
            fire(CardBeatenEvent(c))
        }
    }

    fun cardCanBePassed(c: Card,
                        attackCards: Array<Card?>,
                        defenseCards: Array<Card?>,
                        nextPlayerHandSize: Int): Boolean {
        val ranks = attackCards.map { it?.rank }.filter { it != null }.toSet()
//        if (!ruleSet.allowPass) logger.debug("Passing disabled by rules")
//        else if (!defenseCards.all { it == null }) logger.debug("Passing impossible because started to defend")
//        else if (!attackCards.any { it != null }) logger.debug("Passing impossible because no attack cards")
//        else if (ranks.size != 1) logger.debug("Passing impossible because more than one attacking rank")
//        else if (c.rank != ranks.first()) logger.debug("Passing impossible due to unsuitable rank")
//        else if (attackCards.count { it == null } >= nextPlayerHandSize) logger.debug("Passing impossible due to card excess")
        return ruleSet.allowPass
                && defenseCards.all { it == null }
                && attackCards.any { it != null }
                && ranks.size == 1
                && c.rank == ranks.first()
                && attackCards.count { it == null } < nextPlayerHandSize
    }

    fun passWithCard(c: Card,
                     attackCards: Array<Card?>,
                     defenseCards: Array<Card?>,
                     nextPlayerHandSize: Int) {
        if (cardCanBePassed(c, attackCards, defenseCards, nextPlayerHandSize)) {
            hand.remove(c)
            fire(CardPassedEvent(c))
        }
    }

    fun sayDone() {
        fire(DoneEvent())
    }

    fun sayTake() {
        fire(TakeEvent())
    }

    fun sortCards(sortingMode: SortingMode,
                  trumpSuit: Suit) {
        if (sortingMode == SortingMode.UNSORTED) {
            return
        }
        Collections.sort(hand, Comparator<Card> { c1, c2 ->
            if (c1 === c2) {
                return@Comparator 0
            } else {
                val v1 = (c1.suit.value + (3 - trumpSuit.value)) % 4
                val v2 = (c2.suit.value + (3 - trumpSuit.value)) % 4
                val r1 = (c1.rank.value + 11) % 13
                val r2 = (c2.rank.value + 11) % 13
                when (sortingMode) {
                    Player.SortingMode.SUIT_ASCENDING -> {
                        if (v1 < v2) return@Comparator -1
                        if (v1 > v2) return@Comparator 1
                        return@Comparator if (r1 < r2) -1 else 1
                    }
                    Player.SortingMode.SUIT_DESCENDING -> {
                        if (v2 < v1) return@Comparator -1
                        if (v2 > v1) return@Comparator 1
                        return@Comparator if (r2 < r1) -1 else 1
                    }
                    Player.SortingMode.RANK_ASCENDING -> {
                        if (r1 < r2) return@Comparator -1
                        if (r1 > r2) return@Comparator 1
                        return@Comparator if (v1 < v2) -1 else 1
                    }
                    Player.SortingMode.RANK_DESCENDING -> {
                        if (r2 < r1) return@Comparator -1
                        if (r2 > r1) return@Comparator 1
                        return@Comparator if (v2 < v1) -1 else 1
                    }
                }
            }
            0
        })
    }

    companion object {

        private const val RANK_MULTIPLIER = 100
        private const val UNBALANCED_HAND_PENALTY = 200
        private const val MANY_CARDS_PENALTY = 600
        private const val OUT_OF_PLAY = 30000
    }

    fun relativeCardValue(rankValue: Int): Double {
        val ranksInPlay = (14 - ruleSet.lowestRank.value) % 13 + 1
        val maxValue = ranksInPlay / 2.0
        return if (rankValue == 1) maxValue else (rankValue + maxValue - 14)
    }

}
