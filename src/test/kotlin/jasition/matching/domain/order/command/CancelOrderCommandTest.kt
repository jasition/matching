package jasition.matching.domain.order.command

import arrow.core.Either
import io.kotlintest.matchers.beOfType
import io.kotlintest.should
import io.kotlintest.shouldBe
import io.kotlintest.specs.StringSpec
import io.vavr.kotlin.list
import jasition.cqrs.Command
import jasition.cqrs.Transaction
import jasition.matching.domain.aBookId
import jasition.matching.domain.aFirmWithClient
import jasition.matching.domain.aPrice
import jasition.matching.domain.aRepoWithABooks
import jasition.matching.domain.book.BookId
import jasition.matching.domain.book.Books
import jasition.matching.domain.book.BooksNotFoundException
import jasition.matching.domain.book.TradingStatus.SYSTEM_MAINTENANCE
import jasition.matching.domain.book.TradingStatuses
import jasition.matching.domain.book.entry.*
import jasition.matching.domain.client.ClientRequestId
import jasition.matching.domain.order.event.OrderCancelReason
import jasition.matching.domain.order.event.OrderCancelRejectReason.*
import jasition.matching.domain.order.event.OrderCancelRejectedEvent
import jasition.matching.domain.order.event.OrderCancelledEvent
import java.time.Instant

internal class CancelOrderCommandTest : StringSpec({
    val bookId = aBookId()
    val oldCommand = PlaceOrderCommand(
        requestId = ClientRequestId(current = "req1"),
        whoRequested = aFirmWithClient(),
        bookId = bookId,
        entryType = EntryType.LIMIT,
        side = Side.BUY,
        price = aPrice(),
        size = 10,
        timeInForce = TimeInForce.GOOD_TILL_CANCEL,
        whenRequested = Instant.now()
    )

    val command = CancelOrderCommand(
        requestId = ClientRequestId(current = "req2", original = oldCommand.requestId.current),
        whoRequested = oldCommand.whoRequested,
        bookId = oldCommand.bookId,
        side = oldCommand.side,
        whenRequested = Instant.now()
    )

    val repo = aRepoWithABooks(
        bookId = bookId,
        commands = list<Command<BookId, Books>>(oldCommand)
    )

    val books = repo.read(bookId)
    val nextEventId = books.lastEventId.inc()

    "Exception when the books did not exist" {
        command.execute(null)
            .swap().toOption().orNull() should beOfType<BooksNotFoundException>()
    }
    "When the wrong book ID is used, then the order is rejected" {
        val wrongBookId = "Wrong ID"
        command.copy(bookId = BookId(wrongBookId)).execute(books) shouldBe Either.right(
            Transaction<BookId, Books>(
                aggregate = books.copy(lastEventId = nextEventId),
                events = list(
                    OrderCancelRejectedEvent(
                        eventId = nextEventId,
                        requestId = command.requestId,
                        whoRequested = command.whoRequested,
                        bookId = BookId(wrongBookId),
                        whenHappened = command.whenRequested,
                        rejectReason = UNKNOWN_SYMBOL,
                        rejectText = "Unknown book ID : $wrongBookId",
                        status = EntryStatus.REJECTED
                    )
                )
            )
        )
    }
    "When the effective trading status disallows cancelling order, then the cancellation is rejected" {
        command.execute(books.copy(tradingStatuses = TradingStatuses(SYSTEM_MAINTENANCE))) shouldBe Either.right(
            Transaction<BookId, Books>(
                aggregate = books.copy(
                    tradingStatuses = TradingStatuses(SYSTEM_MAINTENANCE),
                    lastEventId = nextEventId
                ),
                events = list(
                    OrderCancelRejectedEvent(
                        eventId = nextEventId,
                        requestId = command.requestId,
                        whoRequested = command.whoRequested,
                        bookId = bookId,
                        whenHappened = command.whenRequested,
                        rejectReason = EXCHANGE_CLOSED,
                        rejectText = "Cancelling orders is currently not allowed : ${SYSTEM_MAINTENANCE.name}",
                        status = EntryStatus.REJECTED
                    )
                )
            )
        )
    }
    "When the wrong book ID is used and the effective trading status disallows cancelling order, then the cancellation is rejected" {
        val wrongBookId = "Wrong ID"
        command.copy(bookId = BookId(wrongBookId)).execute(books.copy(tradingStatuses = TradingStatuses(SYSTEM_MAINTENANCE))) shouldBe Either.right(
            Transaction<BookId, Books>(
                aggregate = books.copy(
                    tradingStatuses = TradingStatuses(SYSTEM_MAINTENANCE),
                    lastEventId = nextEventId
                ),
                events = list(
                    OrderCancelRejectedEvent(
                        eventId = nextEventId,
                        requestId = command.requestId,
                        whoRequested = command.whoRequested,
                        bookId = BookId(wrongBookId),
                        whenHappened = command.whenRequested,
                        rejectReason = OTHER,
                        rejectText = "Unknown book ID : $wrongBookId; Cancelling orders is currently not allowed : ${SYSTEM_MAINTENANCE.name}",
                        status = EntryStatus.REJECTED
                    )
                )
            )
        )
    }
    "When the working order is not found due to wrong original order ID, then the cancellation is rejected" {
        val wrongRequestId = command.requestId.copy(original = "Wrong ID")
        command.copy(requestId = wrongRequestId).execute(books) shouldBe Either.right(
            Transaction<BookId, Books>(
                aggregate = books.copy(lastEventId = nextEventId),
                events = list(
                    OrderCancelRejectedEvent(
                        eventId = nextEventId,
                        requestId = wrongRequestId,
                        whoRequested = command.whoRequested,
                        bookId = bookId,
                        whenHappened = command.whenRequested,
                        rejectReason = UNKNOWN_ORDER,
                        rejectText = "Order not found (reference ${wrongRequestId.original}",
                        status = EntryStatus.REJECTED
                    )
                )
            )
        )
    }
    "When the working order is not found due to wrong client, then the cancellation is rejected" {
        val wrongClient = command.whoRequested.copy(firmId = "Wrong Firm ID")
        command.copy(whoRequested = wrongClient).execute(books) shouldBe Either.right(
            Transaction<BookId, Books>(
                aggregate = books.copy(lastEventId = nextEventId),
                events = list(
                    OrderCancelRejectedEvent(
                        eventId = nextEventId,
                        requestId = command.requestId,
                        whoRequested = wrongClient,
                        bookId = bookId,
                        whenHappened = command.whenRequested,
                        rejectReason = UNKNOWN_ORDER,
                        rejectText = "Order not found (reference ${command.requestId.original}",
                        status = EntryStatus.REJECTED
                    )
                )
            )
        )
    }
    "When the working order is found, then it order is cancelled" {
        command.execute(books) shouldBe Either.right(
            Transaction<BookId, Books>(
                aggregate = Books(bookId).copy(lastEventId = nextEventId),
                events = list(
                    OrderCancelledEvent(
                        eventId = nextEventId,
                        requestId = command.requestId,
                        whoRequested = command.whoRequested,
                        bookId = bookId,
                        whenHappened = command.whenRequested,
                        status = EntryStatus.CANCELLED,
                        entryType = oldCommand.entryType,
                        side = command.side,
                        sizes = EntrySizes(available = 0, traded = 0, cancelled = oldCommand.size),
                        price = oldCommand.price,
                        timeInForce = oldCommand.timeInForce,
                        reason = OrderCancelReason.CANCELLED_UPON_REQUEST
                    )
                )
            )
        )
    }
})