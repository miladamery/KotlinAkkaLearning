package playground.akka.essentials.part2actors.typed

import akka.actor.testkit.typed.javadsl.TestKitJunitResource
import akka.actor.typed.ActorRef
import org.junit.ClassRule
import org.junit.Test
import org.junit.jupiter.api.Assertions.*

class MomKidTest {

    companion object {
        @get:ClassRule
        @JvmStatic
        val testKit = TestKitJunitResource()
    }

    fun `when mom gives veggies kid is sad`() {
        val kidProbe = testKit.createTestProbe(KidReject::class.java)
        val momActor = testKit.spawn(Mom.create())
        momActor.tell(MomStart(kidProbe.ref as ActorRef<MomKidCommand>))
        assertTrue(kidProbe.receiveMessage() is KidReject)
    }
}