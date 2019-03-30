package jasition.matching.domain.quote.command

import arrow.core.Either
import io.vavr.kotlin.list
import jasition.cqrs.*
import jasition.matching.domain.book.BookId
import jasition.matching.domain.book.Books
import jasition.matching.domain.book.BooksNotFoundException
import jasition.matching.domain.client.Client
import jasition.matching.domain.quote.QuoteRejectReason
import jasition.matching.domain.quote.QuoteRejectReason.*
import jasition.matching.domain.quote.cancelExistingQuotes
import jasition.matching.domain.quote.event.MassQuoteCancelRejectedEvent
import jasition.monad.appendIfNotNullOrBlank
import jasition.monad.ifNotEqualsThenUse
import java.time.Instant
import java.util.function.BiFunction

data class CancelMassQuoteCommand(
    val whoRequested: Client,
    val bookId: BookId,
    val whenRequested: Instant
) : Command<BookId, Books> {
    private val validation = CompleteValidation(list(
        SymbolMustMatch,
        TradingStatusAllows
    ), BiFunction { left, right ->
        right.copy(
            rejectReason = ifNotEqualsThenUse(left.rejectReason, right.rejectReason, OTHER),
            rejectText = appendIfNotNullOrBlank(left.rejectText, right.rejectText, "; ")
        )
    })

    override fun execute(aggregate: Books?): Either<Exception, Transaction<BookId, Books>> {
        if (aggregate == null) return Either.left(BooksNotFoundException("Books $bookId not found"))

        validation.validate(this, aggregate)?.let {
            return Either.right(it playAsTransaction aggregate)
        }

        cancelExistingQuotes(
            books = aggregate,
            eventId = aggregate.lastEventId,
            whoRequested = whoRequested,
            whenHappened = whenRequested
        )?.let {
            return Either.right(it playAsTransaction aggregate)
        }

        return Either.right(
            toRejectedEvent(
                books = aggregate,
                rejectReason = NO_QUOTE_FOUND,
                rejectText = "No quote was found"
            ) playAsTransaction aggregate
        )
    }

    private fun toRejectedEvent(
        books: Books,
        rejectReason: QuoteRejectReason,
        rejectText: String
    ): MassQuoteCancelRejectedEvent = MassQuoteCancelRejectedEvent(
        eventId = books.lastEventId.inc(),
        bookId = books.bookId,
        whoRequested = whoRequested,
        whenHappened = whenRequested,
        rejectReason = rejectReason,
        rejectText = rejectText
    )

    object SymbolMustMatch : Validation<BookId, Books, CancelMassQuoteCommand, MassQuoteCancelRejectedEvent> {
        override fun validate(command: CancelMassQuoteCommand, aggregate: Books): MassQuoteCancelRejectedEvent? =
            if (command.bookId != aggregate.bookId)
                command.toRejectedEvent(
                    books = aggregate,
                    rejectReason = UNKNOWN_SYMBOL,
                    rejectText = "Unknown book ID : ${command.bookId.bookId}"
                )
            else null
    }

    object TradingStatusAllows : Validation<BookId, Books, CancelMassQuoteCommand, MassQuoteCancelRejectedEvent> {
        override fun validate(command: CancelMassQuoteCommand, aggregate: Books): MassQuoteCancelRejectedEvent? =
            if (!aggregate.tradingStatuses.effectiveStatus().allows(command))
                command.toRejectedEvent(
                    books = aggregate,
                    rejectReason = EXCHANGE_CLOSED,
                    rejectText = "Placing mass quote is currently not allowed : ${aggregate.tradingStatuses.effectiveStatus()}"
                )
            else null
    }
}
