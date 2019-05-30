package jasition.matching.domain.book.entry

import io.kotlintest.shouldBe
import io.kotlintest.specs.StringSpec

internal class EntryStatusTest : StringSpec({
    "NEW Status is not final" {
        EntryStatus.NEW.isFinal() shouldBe false
    }
    "PARTIAL_FILL Status is not final" {
        EntryStatus.PARTIAL_FILL.isFinal() shouldBe false
    }
    "FILLED Status is final" {
        EntryStatus.FILLED.isFinal() shouldBe true
    }
    "CANCELLED Status is final" {
        EntryStatus.CANCELLED.isFinal() shouldBe true
    }
    "REJECTED Status is final" {
        EntryStatus.REJECTED.isFinal() shouldBe true
    }
    "Positive available sizes is partial fill"{
        EntryStatus.PARTIAL_FILL.traded(
            EntrySizes(
                available = 30,
                traded = 10,
                cancelled = 0
            )
        ) shouldBe EntryStatus.PARTIAL_FILL
    }
    "Zero available sizes is filled"{
        EntryStatus.PARTIAL_FILL.traded(
            EntrySizes(
                available = 0,
                traded = 10,
                cancelled = 0
            )
        ) shouldBe EntryStatus.FILLED
    }
})