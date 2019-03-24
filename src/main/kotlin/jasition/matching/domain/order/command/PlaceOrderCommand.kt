package jasition.matching.domain.order.command

import arrow.core.Either
import io.vavr.kotlin.list
import jasition.cqrs.*
import jasition.matching.domain.book.BookId
import jasition.matching.domain.book.Books
import jasition.matching.domain.book.BooksNotFoundException
import jasition.matching.domain.book.entry.*
import jasition.matching.domain.client.Client
import jasition.matching.domain.client.ClientRequestId
import jasition.matching.domain.order.event.OrderPlacedEvent
import jasition.matching.domain.order.event.OrderRejectReason
import jasition.matching.domain.order.event.OrderRejectReason.*
import jasition.matching.domain.order.event.OrderRejectedEvent
import jasition.matching.domain.trade.matchAndFinalise
import jasition.monad.appendIfNotNullOrBlank
import jasition.monad.ifNotEqualsThenUse
import java.time.Instant
import java.util.function.BiFunction

data class PlaceOrderCommand(
    val requestId: ClientRequestId,
    val whoRequested: Client,
    val bookId: BookId,
    val entryType: EntryType,
    val side: Side,
    val size: Int,
    val price: Price?,
    val timeInForce: TimeInForce,
    val whenRequested: Instant
) : Command<BookId, Books> {
    private val validation = CompleteValidation(
        list(
            SymbolMustMatch,
            TradingStatusAllows,
            SizesAreCorrect,
            PricePresentBasedOnEntryType,
            ValidEntryTypeTimeInForceCombo
        ), BiFunction { left, right ->
            right.copy(
                rejectReason = ifNotEqualsThenUse(left.rejectReason, right.rejectReason, OTHER),
                rejectText = appendIfNotNullOrBlank(left.rejectText, right.rejectText, "; ")
            )
        }
    )

    override fun execute(aggregate: Books?): Either<Exception, Transaction<BookId, Books>> {
        if (aggregate == null) return Either.left(BooksNotFoundException("Books ${bookId.bookId} not found"))

        validation.validate(this, aggregate)?.let {
            return Either.right(it playAsTransaction aggregate)
        }

        val placedEvent = toPlacedEvent(books = aggregate, currentTime = whenRequested)
        val placedAggregate = placedEvent.play(aggregate)

        return Either.right(
            Transaction<BookId, Books>(placedAggregate, list(placedEvent))
                .append(matchAndFinalise(placedEvent.toBookEntry(), placedAggregate))
        )
    }

    private fun toPlacedEvent(
        books: Books,
        currentTime: Instant = Instant.now()
    ): OrderPlacedEvent = OrderPlacedEvent(
        eventId = books.lastEventId.inc(),
        requestId = requestId,
        whoRequested = whoRequested,
        bookId = bookId,
        entryType = entryType,
        side = side,
        sizes = EntrySizes(
            available = size,
            traded = 0,
            cancelled = 0
        ),
        price = price,
        timeInForce = timeInForce,
        whenHappened = currentTime
    )

    private fun toRejectedEvent(
        books: Books,
        currentTime: Instant = Instant.now(),
        rejectReason: OrderRejectReason = OTHER,
        rejectText: String?
    ): OrderRejectedEvent = OrderRejectedEvent(
        eventId = books.lastEventId.inc(),
        requestId = requestId,
        whoRequested = whoRequested,
        bookId = bookId,
        entryType = entryType,
        side = side,
        size = size,
        price = price,
        timeInForce = timeInForce,
        whenHappened = currentTime,
        rejectReason = rejectReason,
        rejectText = rejectText
    )

    object SymbolMustMatch : Validation<BookId, Books, PlaceOrderCommand, OrderRejectedEvent> {
        override fun validate(command: PlaceOrderCommand, aggregate: Books): OrderRejectedEvent? =
            if (command.bookId != aggregate.bookId)
                command.toRejectedEvent(
                    books = aggregate,
                    currentTime = command.whenRequested,
                    rejectReason = UNKNOWN_SYMBOL,
                    rejectText = "Unknown book ID : ${command.bookId.bookId}"
                ) else null

    }

    object TradingStatusAllows : Validation<BookId, Books, PlaceOrderCommand, OrderRejectedEvent> {
        override fun validate(command: PlaceOrderCommand, aggregate: Books): OrderRejectedEvent? =
            if (!aggregate.tradingStatuses.effectiveStatus().allows(command))
                command.toRejectedEvent(
                    books = aggregate,
                    currentTime = command.whenRequested,
                    rejectReason = EXCHANGE_CLOSED,
                    rejectText = "Placing orders is currently not allowed : ${aggregate.tradingStatuses.effectiveStatus()}"
                ) else null

    }

    object SizesAreCorrect : Validation<BookId, Books, PlaceOrderCommand, OrderRejectedEvent> {
        override fun validate(command: PlaceOrderCommand, aggregate: Books): OrderRejectedEvent? =
            if (command.size <= 0)
                command.toRejectedEvent(
                    books = aggregate,
                    currentTime = command.whenRequested,
                    rejectReason = INCORRECT_QUANTITY,
                    rejectText = "Order sizes must be positive : ${command.size}"
                ) else null
    }

    object PricePresentBasedOnEntryType : Validation<BookId, Books, PlaceOrderCommand, OrderRejectedEvent> {
        override fun validate(command: PlaceOrderCommand, aggregate: Books): OrderRejectedEvent? =
            if (command.entryType.isPriceRequiredOrMustBeNull() != (command.price != null))
                command.toRejectedEvent(
                    books = aggregate,
                    currentTime = command.whenRequested,
                    rejectReason = UNSUPPORTED_ORDER_CHARACTERISTIC,
                    rejectText = "Price must be ${if (command.entryType.isPriceRequiredOrMustBeNull()) "pre" else "ab"}sent for ${command.entryType} order"
                ) else null
    }

    object ValidEntryTypeTimeInForceCombo : Validation<BookId, Books, PlaceOrderCommand, OrderRejectedEvent> {
        override fun validate(command: PlaceOrderCommand, aggregate: Books): OrderRejectedEvent? =
            if (!EntryTypeTimeInForceCombo.isValid(command.entryType, command.timeInForce))
                command.toRejectedEvent(
                    books = aggregate,
                    currentTime = command.whenRequested,
                    rejectReason = UNSUPPORTED_ORDER_CHARACTERISTIC,
                    rejectText = "${command.entryType} ${command.timeInForce.code} is not supported"
                ) else null
    }
}

