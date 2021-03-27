package playground.akka.essentials.part3testing

import akka.actor.AbstractActor
import akka.actor.ActorSystem
import akka.actor.Props
import akka.testkit.javadsl.TestKit
import org.junit.AfterClass
import org.junit.BeforeClass
import org.junit.Test
import playground.akka.essentials.tell
import java.time.Duration
import java.util.*

class BasicSpec {

    companion object {
        lateinit var system: ActorSystem
        lateinit var testKit: TestKit

        @BeforeClass
        @JvmStatic
        fun setup() {
            system = ActorSystem.create("BasicSpec")
            testKit = TestKit(system)
        }

        @AfterClass
        @JvmStatic
        fun tearDown() {
            TestKit.shutdownActorSystem(system)
        }

        class SimpleActor : AbstractActor() {
            override fun createReceive(): Receive {
                return receiveBuilder()
                    .matchAny() {
                        sender tell it
                    }
                    .build()
            }
        }

        class Blackhole : AbstractActor() {
            override fun createReceive(): Receive {
                return receiveBuilder().build()
            }

        }

        class LabTestActor : AbstractActor() {
            override fun createReceive(): Receive {
                return receiveBuilder()
                    .matchEquals("greeting") {
                        if (Random().nextBoolean()) sender.tell("hi", self) else sender.tell("hello", self)
                    }
                    .matchEquals("favoriteTech") {
                        sender.tell("Scala", self)
                        sender.tell("Akka", self)
                    }
                    .match(String::class.java) {
                        sender.tell(it.toUpperCase(), self)
                    }
                    .build()
            }

        }
    }

    @Test
    fun `A simpleActor should send back same message`() {
        testKit.run {
            val simpleActor = system.actorOf(Props.create(SimpleActor::class.java))
            val message = "hello, test"
            simpleActor.tell(message, testActor)
            expectMsg(message)
        }
    }

    @Test
    fun `A blackholeActor should not send back any message`() {
        testKit.run {
            val blackholeActor = system.actorOf(Props.create(Blackhole::class.java))
            blackholeActor.tell("Message", testActor)
            expectNoMessage(Duration.ofSeconds(1))
        }
    }

    @Test
    fun `a labTestActor should turn a string into uppercase`() {
        testKit.run {
            val labTestActor = system.actorOf(Props.create(LabTestActor::class.java))
            labTestActor.tell("I love Akka", testActor)
            val s = expectMsg("I LOVE AKKA")
        }
    }

    @Test
    fun `A labTestActor should greet on greeting message`() {
        testKit.run {
            val labTestActor = system.actorOf(Props.create(LabTestActor::class.java))
            labTestActor.tell("greeting", testActor)
            expectMsgAnyOf("hi", "hello")
        }
    }

    @Test
    fun `A labTestActor with multiple replies`() {
        testKit.run {
            val labTestActor = system.actorOf(Props.create(LabTestActor::class.java))
            labTestActor.tell("favoriteTech", testActor)
            val messages = receiveN(2) // getting reply messages
            //expectMsgAllOf("Scala", "Akka")
        }
    }
}