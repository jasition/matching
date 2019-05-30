package jasition.matching.domain.order.command

import arrow.core.Either
import io.vavr.collection.List
import io.vavr.kotlin.list
import jasition.cqrs.*
import jasition.matching.domain.book.BookId
import jasition.matching.domain.book.Books
import jasition.matching.domain.book.BooksNotFoundException
import jasition.matching.domain.book.entry.BookEntry
import jasition.matching.domain.book.entry.EntryStatus
import jasition.matching.domain.book.entry.EntryStatus.REJECTED
import jasition.matching.domain.book.entry.Side
import jasition.matching.domain.client.Client
import jasition.matching.domain.client.ClientRequestId
import jasition.matching.domain.order.event.OrderCancelReason
import jasition.matching.domain.order.event.OrderCancelRejectReason
import jasition.matching.domain.order.event.OrderCancelRejectReason.UNKNOWN_ORDER
import jasition.matching.domain.order.event.OrderCancelRejectedEvent
import jasition.monad.appendIfNotNullOrBlank
import jasition.monad.ifNotEqualsThenUse
import java.time.Instant
import java.util.function.BiFunction
import java.util.function.Predicate

data class CancelOrderCommand(
    val requestId: ClientRequestId,
    val whoRequested: Client,
    val bookId: BookId,
    val side: Side,
    val whenRequested: Instant
) : Command<BookId, Books> {
    private val validation = CompleteValidation(
        list(
            SymbolMustMatch,
            TradingStatusAllows
        ), BiFunction { left, right ->
            right.copy(
                rejectReason = ifNotEqualsThenUse(left.rejectReason, right.rejectReason, OrderCancelRejectReason.OTHER),
                rejectText = appendIfNotNullOrBlank(left.rejectText, right.rejectText, "; ")
            )
        }
    )

    override fun execute(aggregate: Books?): Either<Exception, Transaction<BookId, Books>> {
        if (aggregate == null) return Either.left(BooksNotFoundException("Books ${bookId.bookId} not found"))

        validation.validate(this, aggregate)?.let {
            return Either.right(it playAsTransaction aggregate)
        }

        val existing = aggregate.findBookEntries(predicate = Predicate {
            it.whoRequested == whoRequested && it.requestId.current == requestId.original
        }, side = side)

        if (existing.isEmpty) {
            val rejectedEvent = toRejectedEvent(
                books = aggregate,
                rejectReason = UNKNOWN_ORDER,
                rejectText = "Order not found (reference ${requestId.original}"
            )
            return Either.right(rejectedEvent playAsTransaction aggregate)
        }

        return Either.right(cancelOrders(requestId = requestId, entries = existing, books = aggregate))
    }

    private fun cancelOrders(
        requestId: ClientRequestId,
        entries: List<BookEntry>,
        books: Books,
        offset: Int = 0,
        eventId: Long = books.lastEventId.value,
        transaction: Transaction<BookId, Books> = Transaction(aggregate = books, events = List.empty())
    ): Transaction<BookId, Books> {
        if (offset >= entries.size()) return transaction

        val cancelledEvent = entries[offset].toOrderCancelledEvent(
            eventId = EventId(eventId + 1),
            bookId = books.bookId,
            whenHappened = whenRequested,
            reason = OrderCancelReason.CANCELLED_UPON_REQUEST
        ).copy(requestId = requestId)

        val newBooks = cancelledEvent.play(books)
        val newTxn = Transaction(
            aggregate = newBooks,
            events = transaction.events.append(cancelledEvent)
        )

        return cancelOrders(
            requestId = requestId,
            entries = entries,
            books = newBooks,
            offset = offset + 1,
            eventId = eventId + 1,
            transaction = newTxn
        )
    }

    private fun toRejectedEvent(
        books: Books,
        status: EntryStatus = REJECTED,
        rejectReason: OrderCancelRejectReason = OrderCancelRejectReason.OTHER,
        rejectText: String?
    ): OrderCancelRejectedEvent = OrderCancelRejectedEvent(
        eventId = books.lastEventId.inc(),
        requestId = requestId,
        whoRequested = whoRequested,
        bookId = bookId,
        status = status,
        whenHappened = whenRequested,
        rejectReason = rejectReason,
        rejectText = rejectText
    )

    object SymbolMustMatch : Validation<BookId, Books, CancelOrderCommand, OrderCancelRejectedEvent> {
        override fun validate(command: CancelOrderCommand, aggregate: Books): OrderCancelRejectedEvent? =
            if (command.bookId != aggregate.bookId)
                command.toRejectedEvent(
                    books = aggregate,
                    rejectReason = OrderCancelRejectReason.UNKNOWN_SYMBOL,
                    rejectText = "Unknown book ID : ${command.bookId.bookId}"
                ) else null

    }

    object TradingStatusAllows : Validation<BookId, Books, CancelOrderCommand, OrderCancelRejectedEvent> {
        override fun validate(command: CancelOrderCommand, aggregate: Books): OrderCancelRejectedEvent? =
            if (!aggregate.tradingStatuses.effectiveStatus().allows(command))
                command.toRejectedEvent(
                    books = aggregate,
                    rejectReason = OrderCancelRejectReason.EXCHANGE_CLOSED,
                    rejectText = "Cancelling orders is currently not allowed : ${aggregate.tradingStatuses.effectiveStatus()}"
                ) else null
    }
}

