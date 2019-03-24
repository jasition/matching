package jasition.cqrs

import io.vavr.collection.List
import io.vavr.collection.Seq

/**
 * [Event] is what has happened to an [Aggregate] in the domain, and in most cases [Event] results in state changes.
 * Please note that one Event can relate to one and only one [Aggregate].
 */
interface Event<KEY, AGG : Aggregate<KEY>> {
    fun aggregateId(): KEY
    fun eventId(): EventId
    fun play(aggregate: AGG): AGG
}

data class EventId(val value: Long) : Comparable<EventId> {
    init {
        if (value < 0) throw IllegalArgumentException("Event ID must be non-negative. value=$value")
    }

    operator fun inc(): EventId = EventId(if (value == Long.MAX_VALUE) 0 else value + 1)

    fun isNextOf(other: EventId): Boolean =
        if (value == 0L && other.value == Long.MAX_VALUE) true
        else (value == other.value + 1)

    override fun compareTo(other: EventId): Int =
        if (value == Long.MAX_VALUE && other.value == 0L) -1
        else if (other.value == Long.MAX_VALUE && value == 0L) 1
        else value.compareTo(other.value)
}

infix fun <K, A : Aggregate<K>> Seq<Event<K, A>>.play(initial: A): A =
    fold(initial) { aggregate, event -> event.play(aggregate) }

infix fun <K, A : Aggregate<K>> Event<K, A>.playAsTransaction(initial: A): Transaction<K, A> =
    Transaction(aggregate = play(initial), events = List.of(this))