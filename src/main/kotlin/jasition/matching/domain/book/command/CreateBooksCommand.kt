package jasition.matching.domain.book.command

import arrow.core.Either
import jasition.cqrs.Command
import jasition.cqrs.Transaction
import jasition.cqrs.playAsTransaction
import jasition.matching.domain.book.*
import jasition.matching.domain.book.event.BooksCreatedEvent
import java.time.LocalDate

data class CreateBooksCommand(
    val bookId: BookId,
    val businessDate: LocalDate = LocalDate.now(),
    val defaultTradingStatus: TradingStatus
) : Command<BookId, Books> {
    override fun execute(aggregate: Books?): Either<Exception, Transaction<BookId, Books>> {
        if (aggregate != null) return Either.left(BooksAlreadyExistsException("Books ${bookId} already exists"))

        return Either.right(
            BooksCreatedEvent(
                bookId = bookId,
                businessDate = businessDate,
                tradingStatuses = TradingStatuses(default = defaultTradingStatus)
            ) playAsTransaction Books(bookId))
    }
}