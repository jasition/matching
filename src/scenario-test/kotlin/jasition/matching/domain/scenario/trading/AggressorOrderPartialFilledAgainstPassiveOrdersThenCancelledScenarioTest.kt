package jasition.matching.domain.scenario.trading

import arrow.core.Tuple2
import arrow.core.Tuple5
import arrow.core.Tuple8
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
import jasition.matching.domain.book.entry.EntrySizes
import jasition.matching.domain.book.entry.EntryStatus.FILLED
import jasition.matching.domain.book.entry.EntryStatus.PARTIAL_FILL
import jasition.matching.domain.book.entry.EntryType.LIMIT
import jasition.matching.domain.book.entry.EntryType.MARKET
import jasition.matching.domain.book.entry.Price
import jasition.matching.domain.book.entry.Side.BUY
import jasition.matching.domain.book.entry.Side.SELL
import jasition.matching.domain.book.entry.TimeInForce.GOOD_TILL_CANCEL
import jasition.matching.domain.book.entry.TimeInForce.IMMEDIATE_OR_CANCEL
import jasition.matching.domain.client.Client
import jasition.matching.domain.order.command.PlaceOrderCommand
import jasition.matching.domain.trade.event.TradeEvent

internal class `Aggressor order partial filled against passive orders then cancelled` : StringSpec({
    val bookId = aBookId()

    forall(
        /**
         * 1. Passive  : side, type, time in force, size, price
         * 2. Aggressor: side, type, time in force, size, price
         * 3. Trade    : passive entry index (even = buy(0, 2), odd = sell(1, 3)), size, price,
         *               aggressor status, aggressor available size, aggressor traded size,
         *               passive status, passive available size
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
            Tuple5(BUY, LIMIT, IMMEDIATE_OR_CANCEL, 16, 12L),
            list(Tuple8(0, 6, 12L, PARTIAL_FILL, 10, 6, FILLED, 0))
        ),
        row(
            list(
                Tuple5(SELL, LIMIT, GOOD_TILL_CANCEL, 6, 12L),
                Tuple5(SELL, LIMIT, GOOD_TILL_CANCEL, 7, 13L)
            ),
            Tuple5(BUY, LIMIT, IMMEDIATE_OR_CANCEL, 16, 13L),
            list(
                Tuple8(0, 6, 12L, PARTIAL_FILL, 10, 6, FILLED, 0),
                Tuple8(1, 7, 13L, PARTIAL_FILL, 3, 13, FILLED, 0)
            )
        ),
        row(
            list(
                Tuple5(BUY, LIMIT, GOOD_TILL_CANCEL, 6, 11L),
                Tuple5(BUY, LIMIT, GOOD_TILL_CANCEL, 7, 10L)
            ),
            Tuple5(SELL, LIMIT, IMMEDIATE_OR_CANCEL, 16, 11L),
            list(Tuple8(0, 6, 11L, PARTIAL_FILL, 10, 6, FILLED, 0))
        ),
        row(
            list(
                Tuple5(BUY, LIMIT, GOOD_TILL_CANCEL, 6, 11L),
                Tuple5(BUY, LIMIT, GOOD_TILL_CANCEL, 7, 10L)
            ),
            Tuple5(SELL, LIMIT, IMMEDIATE_OR_CANCEL, 16, 10L),
            list(
                Tuple8(0, 6, 11L, PARTIAL_FILL, 10, 6, FILLED, 0),
                Tuple8(1, 7, 10L, PARTIAL_FILL, 3, 13, FILLED, 0)
            )
        ),
        row(
            list(
                Tuple5(SELL, LIMIT, GOOD_TILL_CANCEL, 6, 12L),
                Tuple5(SELL, LIMIT, GOOD_TILL_CANCEL, 7, 13L)
            ),
            Tuple5(BUY, MARKET, IMMEDIATE_OR_CANCEL, 16, null),
            list(
                Tuple8(0, 6, 12L, PARTIAL_FILL, 10, 6, FILLED, 0),
                Tuple8(1, 7, 13L, PARTIAL_FILL, 3, 13, FILLED, 0)
            )
        ),
        row(
            list(
                Tuple5(BUY, LIMIT, GOOD_TILL_CANCEL, 6, 11L),
                Tuple5(BUY, LIMIT, GOOD_TILL_CANCEL, 7, 10L)
            ),
            Tuple5(SELL, MARKET, IMMEDIATE_OR_CANCEL, 16, null),
            list(
                Tuple8(0, 6, 11L, PARTIAL_FILL, 10, 6, FILLED, 0),
                Tuple8(1, 7, 10L, PARTIAL_FILL, 3, 13, FILLED, 0)
            )
        )
    ) { oldEntries, new, expectedTrades ->
        "Given a book has existing orders of (${orderEntriesAsString(
            oldEntries
        )}) , when a ${new.a} ${new.b} ${new.c.code} order ${new.d} at ${new.e} is placed, then the trade is executed ${tradesAsString(
            expectedTrades.map { Tuple2(it.b, it.c) }
        )} and the rest of order is cancelled" {
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
            var eventId = orderPlacedEventId
            val lastNewBookEntry = expectedTrades.last().let {
                expectedBookEntry(
                    command = command,
                    eventId = orderPlacedEventId,
                    sizes = EntrySizes(available = it.e, traded = it.f, cancelled = 0),
                    status = it.d
                )
            }

            with(result) {
                events shouldBe List.of<Event<BookId, Books>>(
                    expectedOrderPlacedEvent(command, orderPlacedEventId)
                ).appendAll(expectedTrades.map { trade ->
                    TradeEvent(
                        bookId = command.bookId,
                        eventId = ++eventId,
                        size = trade.b,
                        price = Price(trade.c),
                        whenHappened = command.whenRequested,
                        aggressor = expectedTradeSideEntry(
                            bookEntry = expectedBookEntry(
                                command = command,
                                eventId = orderPlacedEventId,
                                sizes = EntrySizes(available = trade.e, traded = trade.f, cancelled = 0),
                                status = trade.d
                            )
                        ),
                        passive = expectedTradeSideEntry(
                            bookEntry = oldBookEntries[trade.a].copy(
                                sizes = EntrySizes(
                                    available = trade.h,
                                    traded = trade.b,
                                    cancelled = 0
                                ),
                                status = trade.g
                            )
                        )
                    )
                }).append(
                    expectedOrderCancelledEvent(
                        bookId = bookId,
                        eventId = ++eventId,
                        entry = lastNewBookEntry
                    )
                )
            }

            repo.read(bookId).let {
                with(command) {
                    side.sameSideBook(it).entries.size() shouldBe 0
                    side.oppositeSideBook(it).entries.values() shouldBe updatedBookEntries(
                        side = side,
                        oldBookEntries = oldBookEntries,
                        expectedTrades = expectedTrades
                    )
                }
            }
        }
    }
})
