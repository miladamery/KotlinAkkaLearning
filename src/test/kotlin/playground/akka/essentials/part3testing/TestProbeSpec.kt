package playground.akka.essentials.part3testing

import akka.actor.AbstractActor
import akka.actor.ActorRef
import akka.actor.ActorSystem
import akka.actor.Props
import akka.testkit.javadsl.TestKit
import org.junit.AfterClass
import org.junit.BeforeClass
import org.junit.Test
import java.time.Duration

class TestProbeSpec {

    // Scenario
    /*
        word counting actor hierarchy master-slave

        send some work to the master
            - master sends the slave the piece of work
            - slave processes the work and replies to master
            - master aggregates the result
        master sends the total count to the original requester
     */
    companion object {
        lateinit var system: ActorSystem
        lateinit var testKit: TestKit

        @BeforeClass
        @JvmStatic
        fun setup() {
            system = ActorSystem.create("TestProbeSpec")
            testKit = TestKit(system)
        }

        @AfterClass
        @JvmStatic
        fun tearDown() {
            TestKit.shutdownActorSystem(system)
        }

        data class Register(val slaveRef: ActorRef)
        data class Work(val text: String)
        data class SlaveWork(val text: String, val originalRequester: ActorRef)
        data class WorkCompleted(val count: Int, val originalRequester: ActorRef)
        data class Report(val totalCount: Int)
        object RegistrationAck

        class Master : AbstractActor() {
            override fun createReceive(): Receive {
                return receiveBuilder()
                    .match(Register::class.java) {
                        sender.tell(RegistrationAck, self)
                        context.become(online(it, 0))
                    }
                    .matchAny() {}
                    .build()
            }

            private fun online(register: Register, totalWordCount: Int): Receive {
                return receiveBuilder()
                    .match(Work::class.java){
                        register.slaveRef.tell(SlaveWork(it.text, sender), self)
                    }
                    .match(WorkCompleted::class.java) {
                        val newTotalWordCount = totalWordCount + it.count
                        it.originalRequester.tell(Report(newTotalWordCount), self)
                        context.become(online(register, newTotalWordCount))
                    }
                    .build()
            }
        }
    }

    @Test
    fun `A master actor should register a slave`() {
        testKit.run {
            val master = system.actorOf(Props.create(Master::class.java))
            val slave = TestKit(system)

            master.tell(Register(slave.ref), testActor)
            expectMsg(RegistrationAck)
        }
    }

    @Test
    fun `A Master actor should send the work to slave actor`() {
        testKit.run {
            val master = system.actorOf(Props.create(Master::class.java))
            val slaveProbe = TestKit(system) // this is a testProbe
            master.tell(Register(slaveProbe.ref), testActor)
            expectMsg(RegistrationAck)

            val workLoadString = "I Love Akka"
            master.tell(Work(workLoadString), testActor)
            slaveProbe.expectMsg(SlaveWork(workLoadString, testActor))
            slaveProbe.reply(WorkCompleted(3, testActor))

            expectMsg(Report(3))
        }
    }

    @Test
    fun `A master actor should aggregate data correctly`() {
        testKit.run {
            val master = system.actorOf(Props.create(Master::class.java))
            val slaveProbe = TestKit(system) // this is a testProbe
            master.tell(Register(slaveProbe.ref), testActor)
            expectMsg(RegistrationAck)

            val workLoadString = "I Love Akka"
            master.tell(Work(workLoadString), testActor)
            master.tell(Work(workLoadString), testActor)

            // in the meantime i dont have a slave actor
            slaveProbe.receiveWhile(Duration.ofSeconds(3)) {
                slaveProbe.reply(WorkCompleted(3, testActor))
            }

            expectMsg(Report(3))
            expectMsg(Report(6))
        }
    }
}