package playground.akka.essentials.part3testing

import akka.actor.AbstractActor
import akka.actor.ActorSystem
import akka.actor.Props
import akka.testkit.javadsl.TestKit
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import playground.akka.essentials.tell

class BasicSpec {

    companion object {
        lateinit var system: ActorSystem
        lateinit var testKit: TestKit

        @BeforeAll
        @JvmStatic
        fun setup() {
            system = ActorSystem.create("BasicSpec")
            testKit = TestKit(system)
        }

        @AfterAll
        @JvmStatic
        fun tearDown() {
            TestKit.shutdownActorSystem(system)
        }

        class SimpleActor: AbstractActor() {
            override fun createReceive(): Receive {
                return receiveBuilder()
                    .matchAny() {
                        sender tell it
                    }
                    .build()
            }

        }
    }

    @Test
    fun `A simpleActor should send back same message`() {
        testKit.run {
            val simpleActor = system.actorOf(Props.create(SimpleActor::class.java))
            simpleActor tell "hello, test"
        }
    }
}