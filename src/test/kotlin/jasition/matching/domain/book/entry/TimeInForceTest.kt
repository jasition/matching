package jasition.matching.domain.book.entry

import io.kotlintest.data.forall
import io.kotlintest.shouldBe
import io.kotlintest.specs.StringSpec
import io.kotlintest.tables.row
import io.vavr.collection.List
import io.vavr.kotlin.list
import jasition.cqrs.Event
import jasition.cqrs.EventId
import jasition.cqrs.Transaction
import jasition.matching.domain.*
import jasition.matching.domain.book.BookId
import jasition.matching.domain.book.Books
import jasition.matching.domain.book.entry.TimeInForce.*
import jasition.matching.domain.book.event.EntryAddedToBookEvent
import jasition.matching.domain.trade.MatchingResult
import kotlin.random.Random

internal class TimeInForceTest : StringSpec({
    forall(
        row(GOOD_TILL_CANCEL, randomSize(from = 1, until = 1000))
    ) { timeInForce, size ->
        "$timeInForce allows entries to stay on book if available size is positive"{
            timeInForce.canStayOnBook(EntrySizes(available = size)) shouldBe true
        }
    }
    forall(
        row(GOOD_TILL_CANCEL)
    ) { timeInForce ->
        "$timeInForce disallows entries to stay on book if available size is zero"{
            timeInForce.canStayOnBook(EntrySizes(available = 0)) shouldBe false
        }
    }
    forall(
        row(GOOD_TILL_CANCEL, randomSize(from = 1, until = 1000))
    ) { timeInForce, size ->
        "$timeInForce adds entries to book if available size is positive"{
            val aggressor = aBookEntry(
                timeInForce = timeInForce,
                sizes = EntrySizes(available = size)
            )
            val books = aBooks(aBookId())
            val existingEvents = List.of<Event<BookId, Books>>(anOrderPlacedEvent())
            val result = MatchingResult(aggressor, Transaction(books, existingEvents))

            timeInForce.finalise(aggressor, books, result) shouldBe Transaction(
                aggregate = books.addBookEntry(aggressor),
                events = existingEvents.append(
                    EntryAddedToBookEvent(
                        bookId = books.bookId,
                        eventId = EventId(1),
                        entry = aggressor
                    )
                )
            )
        }
    }
    forall(
        row(GOOD_TILL_CANCEL)
    ) { timeInForce ->
        "$timeInForce does not add entries to book if available size is zero"{
            val aggressor = aBookEntry(
                timeInForce = timeInForce,
                sizes = EntrySizes(available = 0)
            )
            val books = aBooks(aBookId())
            val existingEvents = List.of<Event<BookId, Books>>(anOrderPlacedEvent())
            val result = MatchingResult(aggressor, Transaction(books, existingEvents))

            timeInForce.finalise(aggressor, books, result) shouldBe Transaction(
                aggregate = books,
                events = existingEvents
            )
        }
    }
    forall(
        row(FILL_OR_KILL, Random.nextInt(1, 1000)),
        row(FILL_OR_KILL, 0),
        row(IMMEDIATE_OR_CANCEL, Random.nextInt(1, 1000)),
        row(IMMEDIATE_OR_CANCEL, 0)
    ) { timeInForce, availableSize ->
        "$timeInForce disallows entries to stay on book if available size is $availableSize"{
            timeInForce.canStayOnBook(EntrySizes(available = availableSize)) shouldBe false
        }
    }
    "IMMEDIATE_OR_CANCEL cancels remaining size if available size is positive"{
        val originalAggressor = aBookEntry(
            timeInForce = IMMEDIATE_OR_CANCEL,
            sizes = EntrySizes(available = 10, traded = 0, cancelled = 0)
        )
        val aggressor = originalAggressor.copy(
            sizes = EntrySizes(available = 1, traded = 9, cancelled = 0),
            status = EntryStatus.PARTIAL_FILL
        )
        val books = aBooks(aBookId())
        val existingEvents = List.of<Event<BookId, Books>>(anOrderPlacedEvent())
        val result = MatchingResult(aggressor, Transaction(books, existingEvents))

        IMMEDIATE_OR_CANCEL.finalise(originalAggressor, books, result) shouldBe Transaction(
            aggregate = books.copy(lastEventId = books.lastEventId.inc()),
            events = existingEvents.append(
                expectedOrderCancelledEvent(
                    entry = aggressor,
                    eventId = books.lastEventId.inc(), bookId = books.bookId
                )
            )
        )
    }
    "FILL_OR_KILL reverts matching result and cancels full order size if available size is positive"{
        val originalAggressor = aBookEntry(
            timeInForce = FILL_OR_KILL,
            sizes = EntrySizes(available = 10, traded = 0, cancelled = 0)
        )
        val aggressor = originalAggressor.copy(
            sizes = EntrySizes(available = 1, traded = 9, cancelled = 0),
            status = EntryStatus.PARTIAL_FILL
        )
        val books = aBooks(aBookId())
        val existingEvents = List.of<Event<BookId, Books>>(anOrderPlacedEvent())
        val result = MatchingResult(aggressor, Transaction(books, existingEvents))

        FILL_OR_KILL.finalise(originalAggressor, books, result) shouldBe Transaction(
            aggregate = books.copy(lastEventId = books.lastEventId.inc()),
            events = list<Event<BookId, Books>>(
                expectedOrderCancelledEvent(
                    entry = originalAggressor,
                    eventId = books.lastEventId.inc(), bookId = books.bookId
                )
            )
        )
    }
    forall(
        row(FILL_OR_KILL),
        row(IMMEDIATE_OR_CANCEL)
    ) { timeInForce ->
        "$timeInForce does nothing if available size is zero"{
            val aggressor = aBookEntry(
                timeInForce = timeInForce,
                sizes = EntrySizes(available = 0)
            )
            val books = aBooks(aBookId())
            val existingEvents = List.of<Event<BookId, Books>>(anOrderPlacedEvent())
            val result = MatchingResult(aggressor, Transaction(books, existingEvents))

            timeInForce.finalise(aggressor, books, result) shouldBe result.transaction
        }
    }
})