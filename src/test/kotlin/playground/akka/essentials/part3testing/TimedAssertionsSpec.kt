package playground.akka.essentials.part3testing

import akka.actor.AbstractActor
import akka.actor.ActorSystem
import akka.actor.Props
import akka.testkit.javadsl.TestKit
import com.typesafe.config.ConfigFactory
import org.junit.AfterClass
import org.junit.BeforeClass
import org.junit.Test
import java.time.Duration
import kotlin.test.assertTrue

class TimedAssertionsSpec {

    companion object {
        lateinit var testKit: TestKit

        @BeforeClass
        @JvmStatic
        fun setup() {
            testKit = TestKit(ActorSystem.create("TimedAssertionsSpec"/*, ConfigFactory.load().getConfig("specialTimedAssertionsConfig")*/))
        }

        @AfterClass
        @JvmStatic
        fun tearDown() {
            TestKit.shutdownActorSystem(testKit.system)
        }

        data class WorkResult(val result: Int)
        class WorkerActor : AbstractActor() {
            override fun createReceive(): Receive {
                return receiveBuilder()
                    .matchEquals("work") {
                        Thread.sleep(500)
                        sender.tell(WorkResult(42), self)
                    }
                    .matchEquals("workSequence") {
                        val r = java.util.Random()
                        (1..10).forEach {
                            Thread.sleep(r.nextInt(50).toLong())
                            sender.tell(WorkResult(1), self)
                        }
                    }
                    .build()
            }

        }
    }

    @Test
    fun `A worker actor should reply with the meaning of lif in a timely manner`() {
        testKit.run {
            val workerActor = testKit.system.actorOf(Props.create(WorkerActor::class.java))
            within(Duration.ofMillis(500), Duration.ofSeconds(1)) {
                workerActor.tell("work", testActor)
                expectMsg(WorkResult(42))
            }
        }
    }

    @Test
    fun `A worker actor should reply with valid work at a reasonable cadence`() {
        testKit.run {
            val workerActor = testKit.system.actorOf(Props.create(WorkerActor::class.java))
            within(Duration.ofSeconds(1)) {
                workerActor.tell("workSequence", testActor)
                val results = receiveWhile(Duration.ofSeconds(2), Duration.ofMillis(500), 10) { msg ->
                    (msg as WorkResult).result
                }
                assertTrue { results.sum() > 5 }
            }
        }
    }

    @Test
    fun `Reply to a test probe in a timely manner`() {
        testKit.run {
            within(Duration.ofSeconds(1)) {
                val workerActor = testKit.system.actorOf(Props.create(WorkerActor::class.java))
                val testProbe = TestKit(testKit.system)
                testProbe.send(workerActor, "work")
                testProbe.expectMsg(WorkResult(42)) // timeout with 0.3 seconds
            }
        }
    }
}