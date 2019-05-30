package jasition.matching.domain.book

import io.kotlintest.data.forall
import io.kotlintest.shouldBe
import io.kotlintest.shouldThrow
import io.kotlintest.specs.StringSpec
import io.kotlintest.tables.row
import io.mockk.every
import io.mockk.spyk
import io.vavr.kotlin.list
import jasition.cqrs.EventId
import jasition.matching.domain.*
import jasition.matching.domain.book.entry.EntryStatus
import jasition.matching.domain.book.entry.Side
import java.util.function.Function
import java.util.function.Predicate


internal class BooksTest : StringSpec({
    val buyEntry = aBookEntry(
        eventId = EventId(1),
        side = Side.BUY
    )
    val sellEntry = buyEntry.copy(side = Side.SELL)
    val excludedEntry =
        sellEntry.copy(key = sellEntry.key.copy(price = randomPrice()), whoRequested = anotherFirmWithClient())

    val buyLimitBook = spyk(LimitBook(Side.BUY))
    val sellLimitBook = spyk(LimitBook(Side.SELL))
    val newBuyLimitBook = spyk(LimitBook(Side.BUY))
    val newSellLimitBook = spyk(LimitBook(Side.SELL))
    val books = Books(
        bookId = aBookId(),
        buyLimitBook = buyLimitBook,
        sellLimitBook = sellLimitBook,
        tradingStatuses = aTradingStatuses(),
        lastEventId = EventId(0)
    )
    val newEventId = EventId(10)

    every { buyLimitBook.add(buyEntry) } returns newBuyLimitBook
    every { sellLimitBook.add(sellEntry) } returns newSellLimitBook

    "Aggregate ID of a Books is the Book ID" {
        books.aggregateId() shouldBe books.bookId
    }
    "Able to assign the next event ID to the book" {
        var eventId = books.lastEventId
        books.ofEventId(++eventId) shouldBe books.copy(
            lastEventId = eventId
        )
    }
    "Adding BUY entry updates the BUY Limit Book and Last event ID only" {
        books.addBookEntry(buyEntry) shouldBe books.copy(
            buyLimitBook = newBuyLimitBook,
            lastEventId = buyEntry.key.eventId
        )
    }
    "Adding SELL entry updates the SELL Limit Book and Last event ID only" {
        books.addBookEntry(sellEntry) shouldBe books.copy(
            sellLimitBook = newSellLimitBook,
            lastEventId = sellEntry.key.eventId
        )
    }
    "Accepts event ID (last event ID + 1)" {
        books.copy(lastEventId = EventId(4)) verifyEventId EventId(5) shouldBe EventId(5)
    }
    "Rejects event IDs other than (last event ID + 1)" {
        val newBooks = books.copy(lastEventId = EventId(4))
        forall(
            row(0L),
            row(2L),
            row(3L),
            row(4L),
            row(6L),
            row(7L)
        ) { nextEventId ->
            shouldThrow<IllegalArgumentException> { newBooks verifyEventId EventId(nextEventId) }
        }
    }
    "Able to find the entries fulfilling the predicate" {
        Books(aBookId())
            .addBookEntry(buyEntry)
            .addBookEntry(sellEntry)
            .addBookEntry(excludedEntry)
            .findBookEntries(
                Predicate { sellEntry.whoRequested == it.whoRequested }
            ) shouldBe list(buyEntry, sellEntry)
    }
    "Able to find the entries fulfilling the predicate on the BUY side" {
        Books(aBookId())
            .addBookEntry(buyEntry)
            .addBookEntry(sellEntry)
            .addBookEntry(excludedEntry)
            .findBookEntries(
                predicate = Predicate { buyEntry.whoRequested == it.whoRequested },
                side = Side.BUY
            ) shouldBe list(buyEntry)
    }
    "Able to find the entries fulfilling the predicate on the SELL side" {
        Books(aBookId())
            .addBookEntry(buyEntry)
            .addBookEntry(sellEntry)
            .addBookEntry(excludedEntry)
            .findBookEntries(
                predicate = Predicate { sellEntry.whoRequested == it.whoRequested },
                side = Side.SELL
            ) shouldBe list(sellEntry)
    }
    "Updating BUY does not affect SELL side" {
        Books(aBookId())
            .addBookEntry(buyEntry)
            .addBookEntry(sellEntry)
            .updateBookEntry(newEventId, buyEntry.side, buyEntry.key, Function {
                it.copy(status = EntryStatus.CANCELLED)
            }) shouldBe books.copy(
            buyLimitBook = LimitBook(Side.BUY).add(buyEntry.copy(status = EntryStatus.CANCELLED)),
            sellLimitBook = LimitBook(Side.SELL).add(sellEntry),
            lastEventId = newEventId
        )
    }
    "Updating SELL does not affect BUY side" {
        Books(aBookId())
            .addBookEntry(buyEntry)
            .addBookEntry(sellEntry)
            .updateBookEntry(newEventId, sellEntry.side, sellEntry.key, Function {
                it.copy(status = EntryStatus.CANCELLED)
            }) shouldBe books.copy(
            buyLimitBook = LimitBook(Side.BUY).add(buyEntry),
            sellLimitBook = LimitBook(Side.SELL).add(sellEntry.copy(status = EntryStatus.CANCELLED)),
            lastEventId = newEventId
        )
    }
    "Removing BUY does not affect SELL side" {
        Books(aBookId())
            .addBookEntry(buyEntry)
            .addBookEntry(sellEntry)
            .removeBookEntry(newEventId, buyEntry.side, buyEntry.key) shouldBe books.copy(
            buyLimitBook = LimitBook(Side.BUY),
            sellLimitBook = LimitBook(Side.SELL).add(sellEntry),
            lastEventId = newEventId
        )
    }
    "Removing SELL does not affect BUY side" {
        Books(aBookId())
            .addBookEntry(buyEntry)
            .addBookEntry(sellEntry)
            .removeBookEntry(newEventId, sellEntry.side, sellEntry.key) shouldBe books.copy(
            buyLimitBook = LimitBook(Side.BUY).add(buyEntry),
            sellLimitBook = LimitBook(Side.SELL),
            lastEventId = newEventId
        )
    }
    "Removing BUY and SELL entries in one go" {
        Books(aBookId())
            .addBookEntry(buyEntry)
            .addBookEntry(sellEntry)
            .addBookEntry(excludedEntry)
            .removeBookEntries(list(buyEntry, sellEntry), excludedEntry.key.eventId) shouldBe books.copy(
            buyLimitBook = LimitBook(Side.BUY),
            sellLimitBook = LimitBook(Side.SELL).add(excludedEntry),
            lastEventId = excludedEntry.key.eventId
        )
    }
    "Removing BUY entries by a predicate" {
        Books(aBookId())
            .addBookEntry(buyEntry)
            .addBookEntry(sellEntry)
            .removeBookEntries(sellEntry.key.eventId, Side.BUY, Predicate { true }) shouldBe books.copy(
            buyLimitBook = LimitBook(Side.BUY),
            sellLimitBook = LimitBook(Side.SELL).add(sellEntry),
            lastEventId = sellEntry.key.eventId
        )
    }
    "Removing SELL entries by a predicate" {
        Books(aBookId())
            .addBookEntry(buyEntry)
            .addBookEntry(sellEntry)
            .removeBookEntries(sellEntry.key.eventId, Side.SELL, Predicate { true }) shouldBe books.copy(
            buyLimitBook = LimitBook(Side.BUY).add(buyEntry),
            sellLimitBook = LimitBook(Side.SELL),
            lastEventId = sellEntry.key.eventId
        )
    }
})