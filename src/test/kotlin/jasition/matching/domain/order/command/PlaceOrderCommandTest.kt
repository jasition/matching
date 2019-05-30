package jasition.matching.domain.order.command

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
import jasition.matching.domain.book.BookId
import jasition.matching.domain.book.Books
import jasition.matching.domain.book.BooksNotFoundException
import jasition.matching.domain.book.TradingStatus.NOT_AVAILABLE_FOR_TRADING
import jasition.matching.domain.book.TradingStatuses
import jasition.matching.domain.book.entry.EntrySizes
import jasition.matching.domain.book.entry.EntryStatus
import jasition.matching.domain.book.entry.EntryType.LIMIT
import jasition.matching.domain.book.entry.EntryType.MARKET
import jasition.matching.domain.book.entry.Side
import jasition.matching.domain.book.entry.TimeInForce.GOOD_TILL_CANCEL
import jasition.matching.domain.book.entry.TimeInForce.IMMEDIATE_OR_CANCEL
import jasition.matching.domain.book.event.EntryAddedToBookEvent
import jasition.matching.domain.order.event.OrderPlacedEvent
import jasition.matching.domain.order.event.OrderRejectReason.*
import jasition.matching.domain.order.event.OrderRejectedEvent
import java.time.Instant

internal class PlaceOrderCommandTest : StringSpec({
    val bookId = aBookId()
    val books = aBooks(bookId)
    val command = PlaceOrderCommand(
        requestId = aClientRequestId(),
        whoRequested = aFirmWithClient(),
        bookId = bookId,
        entryType = LIMIT,
        side = Side.BUY,
        price = aPrice(),
        size = 10,
        timeInForce = GOOD_TILL_CANCEL,
        whenRequested = Instant.now()
    )

    forall(
        row(LIMIT, GOOD_TILL_CANCEL)
    ) { entryType, timeInForce ->
        "When the request is valid, then the $entryType ${timeInForce.code} order is placed" {
            val orderPlacedEvent = OrderPlacedEvent(
                eventId = EventId(1),
                requestId = command.requestId,
                whoRequested = command.whoRequested,
                bookId = command.bookId,
                entryType = entryType,
                side = command.side,
                sizes = EntrySizes(available = command.size, traded = 0, cancelled = 0),
                price = command.price,
                timeInForce = timeInForce,
                whenHappened = command.whenRequested,
                status = EntryStatus.NEW
            )
            command.execute(books) shouldBe right(
                Transaction(
                    aggregate = books.copy(
                        buyLimitBook = books.buyLimitBook.add(orderPlacedEvent.toBookEntry()),
                        lastEventId = EventId(2)
                    ),
                    events = list(
                        orderPlacedEvent,
                        EntryAddedToBookEvent(
                            bookId = bookId,
                            eventId = EventId(2),
                            entry = orderPlacedEvent.toBookEntry()
                        )
                    )
                )
            )
        }
    }
    "When the request is valid, then the MARKET IOC order is placed" {
        val entryType = MARKET
        val timeInForce = IMMEDIATE_OR_CANCEL
        val orderPlacedEvent = OrderPlacedEvent(
            eventId = EventId(1),
            requestId = command.requestId,
            whoRequested = command.whoRequested,
            bookId = command.bookId,
            entryType = entryType,
            side = command.side,
            sizes = EntrySizes(available = command.size, traded = 0, cancelled = 0),
            price = null,
            timeInForce = timeInForce,
            whenHappened = command.whenRequested,
            status = EntryStatus.NEW
        )
        command.copy(
            entryType = entryType,
            timeInForce = timeInForce,
            price = null
        ).execute(books) shouldBe right(
            Transaction(
                aggregate = books.copy(
                    buyLimitBook = books.buyLimitBook,
                    lastEventId = EventId(2)
                ),
                events = list(
                    orderPlacedEvent,
                    expectedOrderCancelledEvent(
                        bookId = bookId,
                        eventId = EventId(2),
                        entry = orderPlacedEvent.toBookEntry()
                    )
                )
            )
        )
    }

    "Exception when the books did not exist" {
        command.execute(null)
            .swap().toOption().orNull() should beOfType<BooksNotFoundException>()
    }
    "When the wrong book ID is used, then the order is rejected" {
        val wrongBookId = "Wrong ID"
        command.copy(bookId = BookId(wrongBookId)).execute(books) shouldBe right(
            Transaction<BookId, Books>(
                aggregate = books.copy(
                    lastEventId = EventId(1)
                ),
                events = list(
                    OrderRejectedEvent(
                        eventId = books.lastEventId.inc(),
                        requestId = command.requestId,
                        whoRequested = command.whoRequested,
                        bookId = BookId(wrongBookId),
                        entryType = command.entryType,
                        side = command.side,
                        size = command.size,
                        price = command.price,
                        timeInForce = command.timeInForce,
                        whenHappened = command.whenRequested,
                        rejectReason = UNKNOWN_SYMBOL,
                        rejectText = "Unknown book ID : $wrongBookId"
                    )
                )
            )
        )
    }
    "When the request uses negative sizes, then the order is rejected" {
        command.copy(size = -1).execute(books) shouldBe right(
            Transaction<BookId, Books>(
                aggregate = books.copy(
                    lastEventId = EventId(1)
                ),
                events = list(
                    OrderRejectedEvent(
                        eventId = books.lastEventId.inc(),
                        requestId = command.requestId,
                        whoRequested = command.whoRequested,
                        bookId = command.bookId,
                        entryType = command.entryType,
                        side = command.side,
                        size = -1,
                        price = command.price,
                        timeInForce = command.timeInForce,
                        whenHappened = command.whenRequested,
                        rejectReason = INCORRECT_QUANTITY,
                        rejectText = "Order sizes must be positive : -1"
                    )
                )
            )
        )
    }
    forall(
        row(MARKET, GOOD_TILL_CANCEL, null)
    ) { entryType, timeInForce, price ->
        "When the request uses $entryType ${timeInForce.code}, then the order is rejected" {
            command.copy(
                entryType = entryType,
                timeInForce = timeInForce,
                price = price
            ).execute(books) shouldBe right(
                Transaction<BookId, Books>(
                    aggregate = books.copy(
                        lastEventId = EventId(1)
                    ),
                    events = list(
                        OrderRejectedEvent(
                            eventId = books.lastEventId.inc(),
                            requestId = command.requestId,
                            whoRequested = command.whoRequested,
                            bookId = command.bookId,
                            entryType = entryType,
                            side = command.side,
                            size = command.size,
                            price = price,
                            timeInForce = timeInForce,
                            whenHappened = command.whenRequested,
                            rejectReason = UNSUPPORTED_ORDER_CHARACTERISTIC,
                            rejectText = "$entryType ${timeInForce.code} is not supported"
                        )
                    )
                )
            )
        }
    }
    "When the effective trading status disallows placing order and the request uses negative sizes, then the order is rejected with combined reason" {
        command.copy(size = -1).execute(books.copy(tradingStatuses = TradingStatuses(NOT_AVAILABLE_FOR_TRADING))) shouldBe right(
            Transaction<BookId, Books>(
                aggregate = books.copy(
                    lastEventId = EventId(1),
                    tradingStatuses = TradingStatuses(NOT_AVAILABLE_FOR_TRADING)
                ),
                events = list(
                    OrderRejectedEvent(
                        eventId = books.lastEventId.inc(),
                        requestId = command.requestId,
                        whoRequested = command.whoRequested,
                        bookId = command.bookId,
                        entryType = command.entryType,
                        side = command.side,
                        size = -1,
                        price = command.price,
                        timeInForce = command.timeInForce,
                        whenHappened = command.whenRequested,
                        rejectReason = OTHER,
                        rejectText = "Placing orders is currently not allowed : ${NOT_AVAILABLE_FOR_TRADING.name}; Order sizes must be positive : -1"
                    )
                )
            )
        )
    }
    "When the effective trading status disallows placing order, then the order is rejected" {
        command.execute(books.copy(tradingStatuses = TradingStatuses(NOT_AVAILABLE_FOR_TRADING))) shouldBe right(
            Transaction<BookId, Books>(
                aggregate = books.copy(
                    tradingStatuses = TradingStatuses(default = NOT_AVAILABLE_FOR_TRADING),
                    lastEventId = EventId(1)
                ),
                events = list(
                    OrderRejectedEvent(
                        eventId = books.lastEventId.inc(),
                        requestId = command.requestId,
                        whoRequested = command.whoRequested,
                        bookId = command.bookId,
                        entryType = command.entryType,
                        side = command.side,
                        size = command.size,
                        price = command.price,
                        timeInForce = command.timeInForce,
                        whenHappened = command.whenRequested,
                        rejectReason = EXCHANGE_CLOSED,
                        rejectText = "Placing orders is currently not allowed : ${NOT_AVAILABLE_FOR_TRADING.name}"
                    )
                )
            )
        )
    }
    "When placing LIMIT order without price, then the order is rejected" {
        command.copy(
            entryType = LIMIT,
            price = null
        ).execute(books) shouldBe right(
            Transaction<BookId, Books>(
                aggregate = books.copy(
                    lastEventId = EventId(1)
                ),
                events = list(
                    OrderRejectedEvent(
                        eventId = books.lastEventId.inc(),
                        requestId = command.requestId,
                        whoRequested = command.whoRequested,
                        bookId = command.bookId,
                        entryType = LIMIT,
                        side = command.side,
                        size = command.size,
                        price = null,
                        timeInForce = command.timeInForce,
                        whenHappened = command.whenRequested,
                        rejectReason = UNSUPPORTED_ORDER_CHARACTERISTIC,
                        rejectText = "Price must be present for LIMIT order"
                    )
                )
            )
        )
    }
    "When placing MARKET order with price, then the order is rejected" {
        command.copy(
            entryType = MARKET,
            timeInForce = IMMEDIATE_OR_CANCEL,
            price = aPrice()
        ).execute(books) shouldBe right(
            Transaction<BookId, Books>(
                aggregate = books.copy(
                    lastEventId = EventId(1)
                ),
                events = list(
                    OrderRejectedEvent(
                        eventId = books.lastEventId.inc(),
                        requestId = command.requestId,
                        whoRequested = command.whoRequested,
                        bookId = command.bookId,
                        entryType = MARKET,
                        side = command.side,
                        size = command.size,
                        price = aPrice(),
                        timeInForce = IMMEDIATE_OR_CANCEL,
                        whenHappened = command.whenRequested,
                        rejectReason = UNSUPPORTED_ORDER_CHARACTERISTIC,
                        rejectText = "Price must be absent for MARKET order"
                    )
                )
            )
        )
    }
})