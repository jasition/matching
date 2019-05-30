package jasition.matching.domain.client

import io.kotlintest.shouldBe
import io.kotlintest.specs.StringSpec
import jasition.matching.domain.aClientRequestId
import jasition.matching.domain.anotherClientRequestId

internal class ClientRequestIdTest : StringSpec({
    val originalRequestId = aClientRequestId()

    "links to original if request IDs are identical" {
        requestLinksToOriginal(original = originalRequestId, new = originalRequestId) shouldBe true
    }

    "links to original if current request IDs match" {
        requestLinksToOriginal(original = originalRequestId, new = ClientRequestId(current = originalRequestId.current)) shouldBe true
    }

    "links to original if original request IDs match" {
        requestLinksToOriginal(original = originalRequestId, new = anotherClientRequestId(original = originalRequestId.current)) shouldBe true
    }

    "does not link to original if neither current nor original ID matches" {
        requestLinksToOriginal(original = originalRequestId, new = anotherClientRequestId(original = "something else")) shouldBe false
    }
})