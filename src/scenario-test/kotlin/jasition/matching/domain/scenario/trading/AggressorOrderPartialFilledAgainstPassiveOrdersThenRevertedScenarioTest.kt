package jasition.matching.domain.scenario.trading

import arrow.core.Tuple5
import io.kotlintest.data.forall
import io.kotlintest.shouldBe
import io.kotlintest.specs.StringSpec
import io.kotlintest.tables.row
import io.vavr.collection.List
import io.vavr.kotlin.list
import jasition.cqrs.Command
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
import jasition.matching.domain.book.entry.TimeInForce.GOOD_TILL_CANCEL
import jasition.matching.domain.client.Client
import jasition.matching.domain.order.command.PlaceOrderCommand

internal class `Aggressor order partial filled against passive orders then trade reverted` : StringSpec({
    val bookId = aBookId()

    forall(
        /**
         * 1. Passive  : side, type, time in force, size, price
         * 2. Aggressor: side, type, time in force, size, price
         *
         * Parameter dimensions
         * 1. Buy / Sell of aggressor order
         * 2. Single / Multiple fills
         * 3. Exact / Better price executions (embedded in multiple fill cases)
         * 4. Stop matching when prices do not match (embedded in single fill cases)
         */

        row(
            list(
                Tuple5(SELL, LIMIT, GOOD_TILL_CANCEL, 6, 12L),
                Tuple5(SELL, LIMIT, GOOD_TILL_CANCEL, 7, 13L)
            ),
            Tuple5(BUY, LIMIT, FILL_OR_KILL, 16, 12L)
        ),
        row(
            list(
                Tuple5(SELL, LIMIT, GOOD_TILL_CANCEL, 6, 12L),
                Tuple5(SELL, LIMIT, GOOD_TILL_CANCEL, 7, 13L)
            ),
            Tuple5(BUY, LIMIT, FILL_OR_KILL, 16, 13L)
        ),
        row(
            list(
                Tuple5(BUY, LIMIT, GOOD_TILL_CANCEL, 6, 11L),
                Tuple5(BUY, LIMIT, GOOD_TILL_CANCEL, 7, 10L)
            ),
            Tuple5(SELL, LIMIT, FILL_OR_KILL, 16, 11L)
        ),
        row(
            list(
                Tuple5(BUY, LIMIT, GOOD_TILL_CANCEL, 6, 11L),
                Tuple5(BUY, LIMIT, GOOD_TILL_CANCEL, 7, 10L)
            ),
            Tuple5(SELL, LIMIT, FILL_OR_KILL, 16, 10L)
        ),
        row(
            list(
                Tuple5(SELL, LIMIT, GOOD_TILL_CANCEL, 6, 12L),
                Tuple5(SELL, LIMIT, GOOD_TILL_CANCEL, 7, 13L)
            ),
            Tuple5(BUY, MARKET, FILL_OR_KILL, 16, null)
        ),
        row(
            list(
                Tuple5(BUY, LIMIT, GOOD_TILL_CANCEL, 6, 11L),
                Tuple5(BUY, LIMIT, GOOD_TILL_CANCEL, 7, 10L)
            ),
            Tuple5(SELL, MARKET, FILL_OR_KILL, 16, null)
        )
    ) { oldEntries, new ->
        "Given a book has existing orders of (${orderEntriesAsString(
            oldEntries
        )}) , when a ${new.a} ${new.b} ${new.c.code} order ${new.d} at ${new.e} is placed, then no trade is executed and the full quantity of the order is cancelled" {
            val oldCommands = oldEntries.map {
                randomPlaceOrderCommand(
                    bookId = bookId,
                    side = it.a,
                    entryType = it.b,
                    timeInForce = it.c,
                    size = it.d,
                    price = Price(it.e),
                    whoRequested = Client(firmId = "firm1", firmClientId = "client1")
                ) as Command<BookId, Books>
            }

            val repo = aRepoWithABooks(
                bookId = bookId,
                commands = oldCommands as List<Command<BookId, Books>>
            )
            val command = randomPlaceOrderCommand(
                bookId = bookId,
                side = new.a,
                entryType = new.b,
                timeInForce = new.c,
                size = new.d,
                price = new.e?.let { Price(it) },
                whoRequested = Client(firmId = "firm2", firmClientId = "client2")
            )

            val result = command.execute(repo.read(bookId)) commitOrThrow repo

            var oldBookEventId = 0L
            val oldBookEntries = oldCommands.map {
                expectedBookEntry(
                    command = it as PlaceOrderCommand,
                    eventId = EventId((oldBookEventId++ * 2) + 1)
                )
            }

            val orderPlacedEventId = EventId(5)
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
                    side.sameSideBook(it).entries.size() shouldBe 0
                    side.oppositeSideBook(it).entries.values() shouldBe oldBookEntries.filter { entry ->
                        entry.side.oppositeSide() == side
                    }
                }
            }
        }
    }
})
