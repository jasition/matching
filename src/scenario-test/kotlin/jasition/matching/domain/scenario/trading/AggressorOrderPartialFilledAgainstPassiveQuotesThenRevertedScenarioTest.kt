package jasition.matching.domain.scenario.trading

import arrow.core.Tuple2
import arrow.core.Tuple4
import arrow.core.Tuple5
import io.kotlintest.data.forall
import io.kotlintest.shouldBe
import io.kotlintest.specs.StringSpec
import io.kotlintest.tables.row
import io.vavr.collection.List
import io.vavr.kotlin.list
import jasition.cqrs.Event
import jasition.cqrs.EventId
import jasition.cqrs.commitOrThrow
import jasition.matching.domain.*
import jasition.matching.domain.book.BookId
import jasition.matching.domain.book.Books
import jasition.matching.domain.book.entry.EntryType.LIMIT
import jasition.matching.domain.book.entry.EntryType.MARKET
import jasition.matching.domain.book.entry.Price
import jasition.matching.domain.book.entry.Side.BUY
import jasition.matching.domain.book.entry.Side.SELL
import jasition.matching.domain.book.entry.TimeInForce.FILL_OR_KILL
import jasition.matching.domain.client.Client

internal class `Aggressor order partial filled against passive quotes then reverted` : StringSpec({
    val bookId = aBookId()

    forall(
        /**
         * 1. Passive  : bid size, bid price, offer size, offer price
         * 2. Aggressor: side, type, time in force, size, price
         *
         * Parameter dimensions
         * 1. Buy / Sell of aggressor order
         * 2. Single / Multiple fills
         * 3. Exact / Better price executions (embedded in multiple fill cases)
         * 4. Stop matching when prices do not match (embedded in single fill cases)
         */

        row(
            list(Tuple4(6, 11L, 6, 12L), Tuple4(7, 10L, 7, 13L)),
            Tuple5(BUY, LIMIT, FILL_OR_KILL, 16, 12L)
        ),
        row(
            list(Tuple4(6, 11L, 6, 12L), Tuple4(7, 10L, 7, 13L)),
            Tuple5(SELL, LIMIT, FILL_OR_KILL, 16, 11L)
        ),
        row(
            list(Tuple4(6, 11L, 6, 12L), Tuple4(7, 10L, 7, 13L)),
            Tuple5(BUY, LIMIT, FILL_OR_KILL, 16, 13L)
        ),
        row(
            list(Tuple4(6, 11L, 6, 12L), Tuple4(7, 10L, 7, 13L)),
            Tuple5(SELL, LIMIT, FILL_OR_KILL, 16, 10L)
        ),
        row(
            list(Tuple4(6, 11L, 6, 12L), Tuple4(7, 10L, 7, 13L)),
            Tuple5(BUY, MARKET, FILL_OR_KILL, 16, null)
        ),
        row(
            list(Tuple4(6, 11L, 6, 12L), Tuple4(7, 10L, 7, 13L)),
            Tuple5(SELL, MARKET, FILL_OR_KILL, 16, null)
        )
    ) { oldEntries, new ->
        "Given a book has existing quote entries of (${quoteEntriesAsString(
            oldEntries
        )}) of the same firm, when a ${new.a} ${new.b} ${new.c.code} order ${new.d} at ${new.e} is placed, then no trade is executed and the full quantity of the order is cancelled" {
            val oldCommand = randomPlaceMassQuoteCommand(
                bookId = bookId, entries = oldEntries,
                whoRequested = Client(firmId = "firm1", firmClientId = null)
            )
            val repo = aRepoWithABooks(bookId = bookId, commands = list(oldCommand))
            val command = randomPlaceOrderCommand(
                bookId = bookId,
                side = new.a,
                entryType = new.b,
                timeInForce = new.c,
                size = new.d,
                price = new.e?.let { Price(it) },
                whoRequested = Client(firmId = "firm2", firmClientId = randomFirmClientId())
            )

            val result = command.execute(repo.read(bookId)) commitOrThrow repo

            val oldBookEntries = list(
                Tuple2(0, BUY),
                Tuple2(0, SELL),
                Tuple2(1, BUY),
                Tuple2(1, SELL)
            ).map {
                expectedBookEntry(command = oldCommand, entryIndex = it.a, eventId = EventId(1), side = it.b)
            }

            val orderPlacedEventId = EventId(6)
            val expectedAggressorBookEntry = expectedBookEntry(command, orderPlacedEventId)
            var eventId = orderPlacedEventId

            with(result) {
                events shouldBe List.of<Event<BookId, Books>>(
                    expectedOrderPlacedEvent(command, orderPlacedEventId),
                    expectedOrderCancelledEvent(
                        bookId = bookId,
                        eventId = ++eventId,
                        entry = expectedAggressorBookEntry
                    )
                )
            }

            repo.read(bookId).let {
                with(command) {
                    side.sameSideBook(it).entries.values() shouldBe oldBookEntries.filter { entry ->
                        entry.side == side
                    }
                    side.oppositeSideBook(it).entries.values() shouldBe oldBookEntries.filter { entry ->
                        entry.side.oppositeSide() == side
                    }
                }
            }
        }
    }
})