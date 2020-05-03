package flashcards

import java.io.File
import java.io.InputStream
import java.io.PrintStream
import java.util.Scanner

/** The data class models a card tuple. */
data class Card(val term: String, val definition: String)

/** The enum models all the possible actions for the program. */
enum class Commands {
    ADD,
    REMOVE,
    IMPORT,
    EXPORT,
    ASK,
    EXIT,
    LOG,
    HARDEST_CARD,
    RESET_STATS
}

/** The class models an interface medium between the in/out stream (the user) and the program. */
class Logger(input: InputStream = System.`in`, private val output: PrintStream = System.out) {
    private val history: MutableList<String> = mutableListOf()

    val logHistory: List<String> get() = history

    private val scanner = Scanner(input)

    private fun printOut(what: String) = output.println(what)

    fun readString(): String {
        val line = scanner.nextLine()
        history += line
        return line
    }

    fun readInt(): Int = readString().toInt()

    fun log(what: String) {
        history += what
        printOut(what)
    }

    fun clear() = history.clear()
}

/** The card wraps the mutable map of [Card]s. */
class CardCollection {
    private val cards = mutableMapOf<String, Card>()
    private val errors = mutableMapOf<String, Int>()

    fun getRandomCards(times: Int): List<Card> =
            (0 until times).map { cards.values.random() }

    fun checkDefinition(card: Card, definition: String): Boolean =
            definition.toLowerCase() == card.definition.toLowerCase()

    fun markError(term: String) {
        errors[term] = errors[term]?.plus(1) ?: 1
    }

    fun getByDefinition(definition: String): Card? =
            cards.values.find { it.definition.toLowerCase() == definition.toLowerCase() }

    fun import(data: List<String>): Int {
        var count = 0
        errors.clear()
        data.map { it.split(";") }
                .forEach { (term, definition, errorCount) ->
                    count++
                    cards += term.toLowerCase() to Card(term, definition)
                    // errors[term.toLowerCase()] = errors[term.toLowerCase()]?.plus(errorCount.toInt()) ?: errorCount.toInt()
                    errors[term.toLowerCase()] = errorCount.toInt()
                }
        return count
    }

    fun export(): Pair<String, Int> =
            cards.values.joinToString("\n") { (term, definition) -> "$term;$definition;${errors[term] ?: 0}" } to cards.size

    fun remove(term: String): Boolean =
            cards.remove(term.toLowerCase())
                    ?.also { errors.remove(term.toLowerCase()) } != null

    fun reset() = errors.clear()

    fun existsTerm(term: String): Boolean = cards.contains(term.toLowerCase())

    fun existsDefinition(definition: String): Boolean =
            cards.values.find { it.definition.toLowerCase() == definition.toLowerCase() } == null

    fun addCard(card: Card) {
        cards += card.term.toLowerCase() to card
    }

    fun getHardest(): Pair<Set<String>, Int> {
        val max = errors.values.max() ?: 0
        return errors.filterValues { it == max }.keys to max
    }
}

class Program(
        private val logger: Logger,
        private val cards: CardCollection,
        var exportFile: String? = null) {

    fun exit() {
        logger.log("Bye bye!")
        exportFile?.let { exportTo(File(it)) }
    }

    fun ask() {
        logger.log("How many times to ask?")
        val times = logger.readInt()
        val collection = cards.getRandomCards(times)
        for (c in collection) {
            logger.log("Print the definition of \"${c.term}\":")
            val d = logger.readString()
            if (cards.checkDefinition(c, d)) {
                logger.log("Correct answer.")
            } else {
                cards.markError(c.term)
                val otherCard = cards.getByDefinition(d)
                if (otherCard == null) {
                    logger.log("Wrong answer. The correct one is \"${c.definition}\".")
                } else {
                    logger.log("Wrong answer. The correct one is \"${c.definition}\", " +
                            "you've just written the definition of \"${otherCard.term}\".")
                }
            }
        }
    }

    fun export() {
        logger.log("File name:")
        val f = File(logger.readString())
        exportTo(f)
    }

    private fun exportTo(f: File) {
        val (export, count) = cards.export()
        f.writeText(export)
        logger.log("$count cards have been saved.")
    }

    fun import() {
        logger.log("File name:")
        val f = File(logger.readString())
        importFrom(f)
    }

    fun importFrom(f: File) {
        if (f.exists()) {
            val count = cards.import(f.readLines())
            logger.log("$count cards have been loaded.")
        } else {
            logger.log("File not found.")
        }
    }

    fun remove() {
        logger.log("The card:")
        val term = logger.readString()
        if (!cards.remove(term)) {
            logger.log("Can't remove \"$term\": there is no such card.")
        } else {
            logger.log("The card has been removed.")
        }
    }

    fun add() {
        logger.log("The card:")
        val term: String = logger.readString()

        if (!cards.existsTerm(term)) {
            logger.log("The definition of the card:")
            val definition: String = logger.readString()

            if (cards.existsDefinition(definition)) {
                cards.addCard(Card(term, definition))
                logger.log("The pair (\"$term\":\"$definition\") has been added.")
            } else {
                logger.log("The definition \"$definition\" already exists.")
            }
        } else {
            logger.log("The card \"$term\" already exists.")
        }
    }

    fun log() {
        logger.log("File name:")
        val f = File(logger.readString())
        f.writeText(logger.logHistory.joinToString("\n"))
        logger.log("The log has been saved.")
    }

    fun hardest() {
        val (hardest, max) = cards.getHardest()
        when (hardest.size) {
            0 -> logger.log("There are no cards with errors.")
            1 -> logger.log("The hardest card is ${hardest.first()}. You have $max errors answering them.")
            else -> logger.log(
                    "The hardest cards are ${hardest.joinToString(", ") { "\"$it\"" }}. " +
                            "You have $max errors answering them."
            )
        }
    }

    fun reset() {
        cards.reset()
        logger.log("Card statistics has been reset.")
    }
}

fun main(vararg args: String) {
    val logger = Logger()
    val cards = CardCollection()
    val cc = Program(logger, cards)

    for (i in args.indices step 2) {
        if (args[i] == "-import") {
            cc.importFrom(File(args[i + 1]))
        } else if (args[i] == "-export") {
            cc.exportFile = args[i + 1]
        }
    }
    logger.clear()

    do {
        logger.log("Input the action (add, remove, import, export, ask, exit, log, hardest card, reset stats):")
        val command = Commands.valueOf(logger.readString().replace(' ', '_').toUpperCase())
        when (command) {
            Commands.ADD -> cc.add()
            Commands.ASK -> cc.ask()
            Commands.EXIT -> cc.exit()
            Commands.EXPORT -> cc.export()
            Commands.HARDEST_CARD -> cc.hardest()
            Commands.IMPORT -> cc.import()
            Commands.LOG -> cc.log()
            Commands.REMOVE -> cc.remove()
            Commands.RESET_STATS -> cc.reset()
        }
    } while (command != Commands.EXIT)
}


