package jasition.matching.domain.quote.command

import arrow.core.Either.Companion.right
import io.kotlintest.data.forall
import io.kotlintest.matchers.beOfType
import io.kotlintest.should
import io.kotlintest.shouldBe
import io.kotlintest.specs.StringSpec
import io.kotlintest.tables.row
import io.vavr.kotlin.list
import jasition.cqrs.EventId
import jasition.cqrs.Transaction
import jasition.matching.domain.*
import jasition.matching.domain.book.*
import jasition.matching.domain.book.entry.Side.BUY
import jasition.matching.domain.book.entry.Side.SELL
import jasition.matching.domain.book.entry.SizeAtPrice
import jasition.matching.domain.book.entry.TimeInForce
import jasition.matching.domain.book.event.EntryAddedToBookEvent
import jasition.matching.domain.quote.QuoteModelType
import jasition.matching.domain.quote.QuoteRejectReason.*
import jasition.matching.domain.quote.event.MassQuoteCancelledEvent
import jasition.matching.domain.quote.event.MassQuotePlacedEvent
import jasition.matching.domain.quote.event.MassQuoteRejectedEvent
import java.time.Instant

internal class PlaceMassQuoteCommandTest : StringSpec({
    val bookId = aBookId()
    val books = aBooks(bookId)
    val command = PlaceMassQuoteCommand(
        quoteId = "quote1",
        whoRequested = aFirmWithoutClient(),
        bookId = bookId,
        quoteModelType = QuoteModelType.QUOTE_ENTRY,
        timeInForce = TimeInForce.GOOD_TILL_CANCEL,
        entries = list(
            aQuoteEntry(
                bid = SizeAtPrice(size = randomSize(), price = randomPrice(from = 11, until = 12)),
                offer = SizeAtPrice(size = randomSize(), price = randomPrice(from = 13, until = 14))
            ),
            aQuoteEntry(
                bid = SizeAtPrice(size = randomSize(), price = randomPrice(from = 9, until = 10)),
                offer = SizeAtPrice(size = randomSize(), price = randomPrice(from = 15, until = 16))
            )
        ), whenRequested = Instant.now()
    )

    val expectedBuyEntries = list(
        expectedBookEntry(command = command, eventId = EventId(1), side = BUY, entry = command.entries[0]),
        expectedBookEntry(command = command, eventId = EventId(1), side = BUY, entry = command.entries[1])
    )

    val expectedSellEntries = list(
        expectedBookEntry(command = command, eventId = EventId(1), side = SELL, entry = command.entries[0]),
        expectedBookEntry(command = command, eventId = EventId(1), side = SELL, entry = command.entries[1])
    )

    "Exception if the books did not exist" {
        command.execute(null)
            .swap().toOption().orNull() should beOfType<BooksNotFoundException>()
    }
    "When the request is valid, the mass quote is placed" {
        command.execute(books) shouldBe right(
            Transaction<BookId, Books>(
                aggregate = books.copy(
                    buyLimitBook = books.buyLimitBook.add(expectedBuyEntries[0]).add(expectedBuyEntries[1]),
                    sellLimitBook = books.sellLimitBook.add(expectedSellEntries[0]).add(expectedSellEntries[1]),
                    lastEventId = EventId(5)
                ),
                events = list(
                    MassQuotePlacedEvent(
                        eventId = books.lastEventId.inc(),
                        quoteId = command.quoteId,
                        whoRequested = command.whoRequested,
                        bookId = bookId,
                        quoteModelType = command.quoteModelType,
                        timeInForce = command.timeInForce,
                        entries = command.entries,
                        whenHappened = command.whenRequested
                    ),
                    EntryAddedToBookEvent(bookId = bookId, eventId = EventId(2), entry = expectedBuyEntries[0]),
                    EntryAddedToBookEvent(bookId = bookId, eventId = EventId(3), entry = expectedSellEntries[0]),
                    EntryAddedToBookEvent(bookId = bookId, eventId = EventId(4), entry = expectedBuyEntries[1]),
                    EntryAddedToBookEvent(bookId = bookId, eventId = EventId(5), entry = expectedSellEntries[1])
                )
            )
        )
    }
    "When the request is valid, the mass quote is placed and existing quotes are cancelled" {
        val anotherQuoteId = "anotherQuoteId"
        val anotherCommand = command.copy(quoteId = anotherQuoteId)
        val anotherClientRequestIds = list(
            expectedBuyEntries[0].requestId.copy(parentId = anotherQuoteId),
            expectedBuyEntries[1].requestId.copy(parentId = anotherQuoteId)
        )
        val anotherBuyEntries = list(
            expectedCancelledBookEntry(expectedBuyEntries[0].copy(requestId = anotherClientRequestIds[0])),
            expectedCancelledBookEntry(expectedBuyEntries[1].copy(requestId = anotherClientRequestIds[1]))
        )

        val anotherSellEntries = list(
            expectedCancelledBookEntry(expectedSellEntries[0].copy(requestId = anotherClientRequestIds[0])),
            expectedCancelledBookEntry(expectedSellEntries[1].copy(requestId = anotherClientRequestIds[1]))
        )

        val existingBooks = aRepoWithABooks(
            bookId = bookId,
            commands = list(anotherCommand)
        ).read(bookId)


        command.execute(existingBooks) shouldBe right(
            Transaction<BookId, Books>(
                aggregate = books.copy(
                    buyLimitBook = books.buyLimitBook
                        .add(expectedBuyEntries[0].withKey(eventId = EventId(7)))
                        .add(expectedBuyEntries[1].withKey(eventId = EventId(7))),
                    sellLimitBook = books.sellLimitBook
                        .add(expectedSellEntries[0].withKey(eventId = EventId(7)))
                        .add(expectedSellEntries[1].withKey(eventId = EventId(7))),
                    lastEventId = EventId(11)
                ),
                events = list(
                    MassQuoteCancelledEvent(
                        eventId = EventId(6),
                        bookId = bookId,
                        entries = list(
                            anotherBuyEntries[0],
                            anotherBuyEntries[1],
                            anotherSellEntries[0],
                            anotherSellEntries[1]
                        ), whoRequested = command.whoRequested,
                        whenHappened = command.whenRequested
                    ),
                    MassQuotePlacedEvent(
                        eventId = EventId(7),
                        quoteId = command.quoteId,
                        whoRequested = command.whoRequested,
                        bookId = bookId,
                        quoteModelType = command.quoteModelType,
                        timeInForce = command.timeInForce,
                        entries = command.entries,
                        whenHappened = command.whenRequested
                    ),
                    EntryAddedToBookEvent(
                        bookId = bookId,
                        eventId = EventId(8),
                        entry = expectedBuyEntries[0].withKey(eventId = EventId(7))
                    ),
                    EntryAddedToBookEvent(
                        bookId = bookId,
                        eventId = EventId(9),
                        entry = expectedSellEntries[0].withKey(eventId = EventId(7))
                    ),
                    EntryAddedToBookEvent(
                        bookId = bookId,
                        eventId = EventId(10),
                        entry = expectedBuyEntries[1].withKey(eventId = EventId(7))
                    ),
                    EntryAddedToBookEvent(
                        bookId = bookId,
                        eventId = EventId(11),
                        entry = expectedSellEntries[1].withKey(eventId = EventId(7))
                    )
                )
            )
        )
    }
    "Single-sided quoting on the BID side is allowed" {
        val quoteEntry = aQuoteEntry(offerSize = null, bidPrice = 10)
        val newCommand = command.copy(entries = list(quoteEntry))
        val expectedEntry =
            expectedBookEntry(command = newCommand, eventId = EventId(1), side = BUY, entry = quoteEntry)

        newCommand.execute(books) shouldBe right(
            Transaction<BookId, Books>(
                aggregate = books.copy(
                    buyLimitBook = books.buyLimitBook.add(expectedEntry),
                    sellLimitBook = books.sellLimitBook,
                    lastEventId = EventId(2)
                ),
                events = list(
                    MassQuotePlacedEvent(
                        eventId = books.lastEventId.inc(),
                        quoteId = command.quoteId,
                        whoRequested = command.whoRequested,
                        bookId = bookId,
                        quoteModelType = command.quoteModelType,
                        timeInForce = command.timeInForce,
                        entries = list(quoteEntry),
                        whenHappened = command.whenRequested
                    ),
                    EntryAddedToBookEvent(
                        bookId = bookId,
                        eventId = EventId(2),
                        entry = expectedEntry
                    )
                )
            )
        )
    }
    "Single-sided quoting on the OFFER side is allowed" {
        val quoteEntry = aQuoteEntry(bidSize = null, offerPrice = 10)
        val newCommand = command.copy(
            entries = list(quoteEntry)
        )
        val expectedEntry =
            expectedBookEntry(command = newCommand, eventId = EventId(1), side = SELL, entry = quoteEntry)

        newCommand.execute(books) shouldBe right(
            Transaction<BookId, Books>(
                aggregate = books.copy(
                    buyLimitBook = books.buyLimitBook,
                    sellLimitBook = books.sellLimitBook.add(expectedEntry),
                    lastEventId = EventId(2)
                ),
                events = list(
                    MassQuotePlacedEvent(
                        eventId = books.lastEventId.inc(),
                        quoteId = command.quoteId,
                        whoRequested = command.whoRequested,
                        bookId = bookId,
                        quoteModelType = command.quoteModelType,
                        timeInForce = command.timeInForce,
                        entries = list(quoteEntry),
                        whenHappened = command.whenRequested
                    ),
                    EntryAddedToBookEvent(
                        bookId = bookId,
                        eventId = EventId(2),
                        entry = expectedEntry
                    )
                )
            )
        )
    }
    "When the wrong book ID is used, then the mass quote is rejected" {
        val wrongBookId = "Wrong ID"
        command.copy(bookId = BookId(wrongBookId)).execute(books) shouldBe right(
            Transaction<BookId, Books>(
                aggregate = books.copy(
                    buyLimitBook = books.buyLimitBook,
                    sellLimitBook = books.sellLimitBook,
                    lastEventId = EventId(1)
                ),
                events = list(
                    MassQuoteRejectedEvent(
                        eventId = books.lastEventId.inc(),
                        quoteId = command.quoteId,
                        whoRequested = command.whoRequested,
                        bookId = bookId,
                        quoteModelType = command.quoteModelType,
                        timeInForce = command.timeInForce,
                        entries = command.entries,
                        whenHappened = command.whenRequested,
                        rejectReason = UNKNOWN_SYMBOL,
                        rejectText = "Unknown book ID : $wrongBookId"
                    )
                )
            )
        )
    }
    forall(
        row(0, 2),
        row(2, 0),
        row(randomSize(from = -10, until = -1), 4),
        row(7, randomSize(from = -20, until = -11)),
        row(-15, -15)
    ) { bidSize, offerSize ->

        val min = minOf(bidSize, offerSize)
        "When the request has non-positive sizes (bid=$bidSize, offer=$offerSize), the mass quote is rejected" {
            val newEntries = list(
                aQuoteEntry(
                    bid = SizeAtPrice(size = bidSize, price = randomPrice(from = 10, until = 12)),
                    offer = SizeAtPrice(size = offerSize, price = randomPrice(from = 13, until = 15))
                )
            )
            command.copy(
                entries = newEntries
            ).execute(books) shouldBe right(
                Transaction<BookId, Books>(
                    aggregate = books.copy(
                        buyLimitBook = books.buyLimitBook,
                        sellLimitBook = books.sellLimitBook,
                        lastEventId = EventId(1)
                    ),
                    events = list(
                        MassQuoteRejectedEvent(
                            eventId = books.lastEventId.inc(),
                            quoteId = command.quoteId,
                            whoRequested = command.whoRequested,
                            bookId = bookId,
                            quoteModelType = command.quoteModelType,
                            timeInForce = command.timeInForce,
                            entries = newEntries,
                            whenHappened = command.whenRequested,
                            rejectReason = INVALID_QUANTITY,
                            rejectText = "Quote sizes must be positive : $min"
                        )
                    )
                )
            )
        }
    }
    forall(
        row(list(aQuoteEntry(bidPrice = 14, offerPrice = 14)), 14, 14),
        row(
            list(aQuoteEntry(bidSize = null, offerPrice = 12), aQuoteEntry(bidPrice = 13, offerSize = null)),
            12,
            13
        ),
        row(list(aQuoteEntry(bidPrice = 14, offerPrice = 12), aQuoteEntry(bidPrice = 13, offerPrice = 13)), 12, 14)
    ) { newEntries, minSell, maxBuy ->
        "When the request has crossed prices (minSell=$minSell, maxBuy=$maxBuy), the mass quote is rejected" {
            command.copy(
                entries = newEntries
            ).execute(books) shouldBe right(
                Transaction<BookId, Books>(
                    aggregate = books.copy(
                        buyLimitBook = books.buyLimitBook,
                        sellLimitBook = books.sellLimitBook,
                        lastEventId = EventId(1)
                    ),
                    events = list(
                        MassQuoteRejectedEvent(
                            eventId = books.lastEventId.inc(),
                            quoteId = command.quoteId,
                            whoRequested = command.whoRequested,
                            bookId = bookId,
                            quoteModelType = command.quoteModelType,
                            timeInForce = command.timeInForce,
                            entries = newEntries,
                            whenHappened = command.whenRequested,
                            rejectReason = INVALID_BID_ASK_SPREAD,
                            rejectText = "Quote prices must not cross within a mass quote: lowestSellPrice=$minSell, highestBuyPrice=$maxBuy"
                        )
                    )
                )
            )
        }
    }
    "When the effective trading status disallows placing order, then the order is rejected" {
        val tradingStatuses = TradingStatuses(TradingStatus.NOT_AVAILABLE_FOR_TRADING)
        command.execute(books.copy(tradingStatuses = tradingStatuses)) shouldBe right(
            Transaction<BookId, Books>(
                aggregate = books.copy(
                    buyLimitBook = books.buyLimitBook,
                    sellLimitBook = books.sellLimitBook,
                    lastEventId = EventId(1),
                    tradingStatuses = tradingStatuses
                ),
                events = list(
                    MassQuoteRejectedEvent(
                        eventId = books.lastEventId.inc(),
                        quoteId = command.quoteId,
                        whoRequested = command.whoRequested,
                        bookId = bookId,
                        quoteModelType = command.quoteModelType,
                        timeInForce = command.timeInForce,
                        entries = command.entries,
                        whenHappened = command.whenRequested,
                        rejectReason = EXCHANGE_CLOSED,
                        rejectText = "Placing mass quote is currently not allowed : ${TradingStatus.NOT_AVAILABLE_FOR_TRADING.name}"
                    )
                )
            )
        )
    }
    "When the effective trading status disallows placing order and it has negative size, then the order is rejected" {
        val newEntries = list(
            aQuoteEntry(
                bid = SizeAtPrice(size = -1, price = randomPrice(from = 10, until = 12)),
                offer = SizeAtPrice(size = 1, price = randomPrice(from = 13, until = 15))
            )
        )
        val tradingStatuses = TradingStatuses(TradingStatus.NOT_AVAILABLE_FOR_TRADING)
        command.copy(
            entries = newEntries
        ).execute(books.copy(tradingStatuses = tradingStatuses)) shouldBe right(
            Transaction<BookId, Books>(
                aggregate = books.copy(
                    buyLimitBook = books.buyLimitBook,
                    sellLimitBook = books.sellLimitBook,
                    lastEventId = EventId(1),
                    tradingStatuses = tradingStatuses
                ),
                events = list(
                    MassQuoteRejectedEvent(
                        eventId = books.lastEventId.inc(),
                        quoteId = command.quoteId,
                        whoRequested = command.whoRequested,
                        bookId = bookId,
                        quoteModelType = command.quoteModelType,
                        timeInForce = command.timeInForce,
                        entries = newEntries,
                        whenHappened = command.whenRequested,
                        rejectReason = OTHER,
                        rejectText = "Placing mass quote is currently not allowed : NOT_AVAILABLE_FOR_TRADING; Quote sizes must be positive : -1"
                    )
                )
            )
        )
    }
})