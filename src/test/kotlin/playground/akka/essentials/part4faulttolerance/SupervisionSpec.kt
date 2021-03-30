package playground.akka.essentials.part4faulttolerance

import akka.actor.*
import akka.japi.pf.DeciderBuilder
import akka.testkit.EventFilter
import akka.testkit.javadsl.TestKit
import com.typesafe.config.ConfigFactory
import org.junit.AfterClass
import org.junit.BeforeClass
import org.junit.Test
import scala.Option
import java.time.Duration
import kotlin.test.assertEquals

class SupervisionSpec {

    companion object {
        lateinit var testKit: TestKit

        @BeforeClass
        @JvmStatic
        fun setup() {
            val system =
                ActorSystem.create("SupervisionSpec", ConfigFactory.load().getConfig("interceptingLogMessages"))
            testKit = TestKit(system)
        }

        @AfterClass
        @JvmStatic
        fun tearDown() {
            TestKit.shutdownActorSystem(testKit.system)
        }
    }

    object Report
    open class Supervisor : AbstractActor() {

        override fun supervisorStrategy(): SupervisorStrategy {
            return OneForOneStrategy(-1, Duration.ofMinutes(1),
                DeciderBuilder
                    .match(NullPointerException::class.java) { SupervisorStrategy.restart() as SupervisorStrategy.Directive? }
                    .match(IllegalArgumentException::class.java) { SupervisorStrategy.stop() as SupervisorStrategy.Directive? }
                    .match(RuntimeException::class.java) { SupervisorStrategy.resume() as SupervisorStrategy.Directive? }
                    .match(Exception::class.java) { SupervisorStrategy.escalate() as SupervisorStrategy.Directive? }
                    .build()
            )
        }

        override fun createReceive(): Receive {
            return receiveBuilder()
                .match(Props::class.java) {
                    val childRef = context.actorOf(it)
                    sender.tell(childRef, self)
                }
                .build()
        }

    }

    class NoDeathOnRestartSupervisor: Supervisor() {
        override fun preRestart(reason: Throwable?, message: Option<Any>?) {
        }
    }

    class AllForOneSupervisor: Supervisor() {
        override fun supervisorStrategy(): SupervisorStrategy {
            return AllForOneStrategy(-1, Duration.ofMinutes(1),
                DeciderBuilder
                    .match(NullPointerException::class.java) { SupervisorStrategy.restart() as SupervisorStrategy.Directive? }
                    .match(IllegalArgumentException::class.java) { SupervisorStrategy.stop() as SupervisorStrategy.Directive? }
                    .match(RuntimeException::class.java) { SupervisorStrategy.resume() as SupervisorStrategy.Directive? }
                    .match(Exception::class.java) { SupervisorStrategy.escalate() as SupervisorStrategy.Directive? }
                    .build()
            )
        }
    }

    class FussyWordCounter : AbstractActor() {
        private var words = 0

        override fun createReceive(): Receive {
            return receiveBuilder()
                .matchEquals("") {
                    throw NullPointerException("Sentence is empty")
                }
                .match(String::class.java) {
                    if (it.length > 20) throw RuntimeException("Sentence is too big")
                    else if (!Character.isUpperCase(it[0])) throw IllegalAccessException("Sentence must start with uppercase")
                    else words += it.split(" ").size
                }
                .match(Report::class.java) {
                    sender.tell(words, self)
                }
                .matchAny {
                    throw Exception("Can only receive strings")
                }
                .build()
        }

    }

    @Test
    fun `A supervisor should resume its child in case of minor fault`() {
        testKit.run {
            val supervisor = system.actorOf(Props.create(Supervisor::class.java))
            supervisor.tell(Props.create(FussyWordCounter::class.java), testActor)
            val child = expectMsgAnyClassOf<ActorRef>(ActorRef::class.java)

            child.tell("I love Akka", testActor)
            child.tell(Report, testActor)
            expectMsg(3)

            //resume strategy
            child.tell("Akka is awesome because i am learning to think in a whole new way", testActor)
            child.tell(Report, testActor)
            expectMsg(3)
        }
    }

    @Test
    fun `A supervisor should restart its child in case of an empty sentence`() {
        testKit.run {
            val supervisor = system.actorOf(Props.create(Supervisor::class.java))
            supervisor.tell(Props.create(FussyWordCounter::class.java), testActor)
            val child = expectMsgAnyClassOf<ActorRef>(ActorRef::class.java)

            // changing internal state of child
            child.tell("I love Akka", testActor)
            child.tell(Report, testActor)
            expectMsg(3)

            // restart strategy
            child.tell("", testActor)
            child.tell(Report, testActor)
            expectMsg(0)
        }
    }

    @Test
    fun `A supervisor should terminate its child in case of a major error`() {
        testKit.run {
            val supervisor = system.actorOf(Props.create(Supervisor::class.java))
            supervisor.tell(Props.create(FussyWordCounter::class.java), testActor)
            val child = expectMsgAnyClassOf<ActorRef>(ActorRef::class.java)

            watch(child) // register testActor to watch child
            child.tell(
                "akka is nice",
                testActor
            ) // this will stop child actor which result in Terminated message to testActor
            val terminatedMessage =
                expectMsgAnyClassOf<Terminated>(Terminated::class.java) // testing if we got Terminated message
            assertEquals(terminatedMessage.actor, child) // testing if Terminated object belongs to out child actor
        }
    }

    @Test
    fun `A supervisor should escalate an error when it doesnt know what to do`() {
        testKit.run {
            val supervisor = system.actorOf(Props.create(Supervisor::class.java), "supervisor")
            supervisor.tell(Props.create(FussyWordCounter::class.java), testActor)
            val child = expectMsgAnyClassOf<ActorRef>(ActorRef::class.java)

            watch(child) // register testActor to watch child
            child.tell(43, testActor) // this will result in escalate strategy in child actor
            // when an actor escalates it stops all its children and escalated to the parent
            val terminatedMessage =
                expectMsgAnyClassOf<Terminated>(Terminated::class.java) // testing if we got Terminated message
            assertEquals(terminatedMessage.actor, child) // testing if Terminated object belongs to out child actor
        }
    }

    @Test
    fun `A kinder supervisor should not kill children in case its restarted or escalated failure`() {
        testKit.run {
            val supervisor = system.actorOf(Props.create(NoDeathOnRestartSupervisor::class.java), "NoDeathOnRestartSupervisor")
            supervisor.tell(Props.create(FussyWordCounter::class.java), testActor)
            val child = expectMsgAnyClassOf<ActorRef>(ActorRef::class.java)

            child.tell("Akka is cool", testActor)
            child.tell(Report, testActor)
            expectMsg(3)

            child.tell(45, testActor) /* This causes to exception escalated means that exception will reach
            to user guardian actor that restarts NoDeathOnRestartSupervisor. but we overrided preRestart fun so no child
            will be stopped. result is we expect that child will be still alive.
            */
            child.tell(Report, testActor) // expecting child to be alive
            /*
                but what message we should get back? because User guardian will restart everything
                then the expected message is restarted child with restarted state that is zero/0.
            */
            expectMsg(0)
        }
    }

    //@Test
    fun `An all-for-one supervisor should apply the all-for-one strategy`() {
        testKit.run {
            val supervisor = system.actorOf(Props.create(AllForOneSupervisor::class.java), "NoDeathOnRestartSupervisor")
            supervisor.tell(Props.create(FussyWordCounter::class.java), testActor)
            val child = expectMsgAnyClassOf<ActorRef>(ActorRef::class.java)

            supervisor.tell(Props.create(FussyWordCounter::class.java), testActor)
            val secondChild = expectMsgAnyClassOf<ActorRef>(ActorRef::class.java)

            secondChild.tell("Testing supervision", testActor)
            secondChild.tell(Report, testActor)
            expectMsg(2)

            child.tell("", testActor)
            // This causes the child throw exception. this results in restart strategy
            // but because strategy is all-for-one it causes that all children to be restarted. which means the secondChild
            // will restart too

            akka.testkit.javadsl.EventFilter(NullPointerException::class.java, system)
                .intercept {
                    child.tell("", testActor)
                }

            secondChild.tell(Report, testActor)
            expectMsg(0)
        }
    }
}