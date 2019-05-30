package jasition.matching.domain.book.entry

enum class EntryStatus {
    NEW {
        override fun isFinal(): Boolean = false
    },
    PARTIAL_FILL {
        override fun isFinal(): Boolean = false
    },
    FILLED {
        override fun isFinal(): Boolean = true
    },
    CANCELLED {
        override fun isFinal(): Boolean = true
    },
    REJECTED {
        override fun isFinal(): Boolean = true
    };

    abstract fun isFinal(): Boolean;

    fun traded(newSizes: EntrySizes): EntryStatus =
        if (newSizes.available == 0) FILLED else PARTIAL_FILL

}