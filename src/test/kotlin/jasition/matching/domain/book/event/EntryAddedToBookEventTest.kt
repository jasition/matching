package jasition.matching.domain.book.event

import io.kotlintest.shouldBe
import io.kotlintest.shouldThrow
import io.kotlintest.specs.StringSpec
import io.mockk.every
import io.mockk.spyk
import io.vavr.collection.List
import jasition.matching.domain.*
import jasition.matching.domain.book.BookId
import jasition.matching.domain.book.Books
import java.time.Instant

internal class EntryAddedToBookEventPropertyTest : StringSpec({
    val eventId = anEventId()
    val bookId = aBookId()
    val event = EntryAddedToBookEvent(
        bookId = bookId,
        whenHappened = Instant.now(),
        eventId = eventId,
        entry = aBookEntry(eventId = eventId)
    )
    "Has Book ID as Aggregate ID" {
        event.aggregateId() shouldBe bookId
    }
    "Has Event ID as Event ID" {
        event.eventId() shouldBe eventId
    }
    "Is a Side-effect event" {
        event.eventType() shouldBe EventType.SIDE_EFFECT
    }
})

internal class `Given an entry is added to an empty book` : StringSpec({
    val bookId = aBookId()
    val entryEventId = anEventId()
    val entry = aBookEntry(eventId = entryEventId)
    val originalBooks = spyk(Books(bookId))
    val newBooks = spyk(Books(bookId))
    every { originalBooks.addBookEntry(entry) } returns newBooks

    "When the event ID is the next event ID of the book, then the entry exists in the book" {
        EntryAddedToBookEvent(
            bookId = bookId,
            whenHappened = Instant.now(),
            eventId = entryEventId,
            entry = entry
        ).play(originalBooks) shouldBe Transaction<BookId, Books>(
            aggregate = newBooks,
            events = List.empty()
        )
    }
    "When a wrong event ID is used in the event, then an exception is thrown" {
        val wrongEventId = entryEventId.next()
        every { originalBooks.verifyEventId(wrongEventId) } throws IllegalArgumentException()

        shouldThrow<IllegalArgumentException> {
            EntryAddedToBookEvent(
                bookId = bookId,
                whenHappened = Instant.now(),
                eventId = wrongEventId,
                entry = entry
            ).play(originalBooks)
        }
    }
    "When a wrong event ID is used in the entry, then an exception is thrown" {
        val wrongEventId = entry.key.eventId
        every { originalBooks.verifyEventId(wrongEventId) } throws IllegalArgumentException()

        shouldThrow<IllegalArgumentException> {
            EntryAddedToBookEvent(
                bookId = bookId,
                whenHappened = Instant.now(),
                eventId = entryEventId.next(),
                entry = entry
            ).play(originalBooks)
        }
    }
})