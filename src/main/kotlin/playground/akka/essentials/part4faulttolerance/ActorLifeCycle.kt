package playground.akka.essentials.part4faulttolerance

import akka.actor.*
import akka.event.Logging
import scala.Option

class ActorLifeCycle {
    class Child : AbstractActor() {
        private val log = Logging.getLogger(context.system, this)
        override fun createReceive(): Receive {
            return receiveBuilder()
                .match(Fail::class.java) {
                    log.warning("${self.path()} child will fail now")
                    throw RuntimeException("I Failed")
                }
                .match(Check::class.java) {
                    log.info("${self.path()} alive and kicking")
                }
                .build()
        }

        override fun preStart()  = log.info("${self.path()} Supervised child started")
        override fun postStop() = log.info("${self.path()} Supervised child stopped")
        override fun preRestart(reason: Throwable?, message: Option<Any>?) {
            log.info("${self.path()} Supervised actor restarting because of ${reason?.message}")
        }

        override fun postRestart(reason: Throwable?) {
            log.info("${self.path()} Supervised actor restarted")
        }
    }

    class Parent: AbstractActor() {
        val child = context.actorOf(Props.create(Child::class.java), "supervisedChild")

        override fun createReceive(): Receive {
            return receiveBuilder()
                .match(FailChild::class.java) {
                    child.tell(Fail, self)
                }
                .match(CheckChild::class.java) {
                    child.tell(Check, self)
                }
                .build()
        }

    }
}

fun main() {
    val system = ActorSystem.create("LifecycleDemo")
    /*val parent = system.actorOf(Props.create(LifecycleActor::class.java), "parent")
    parent.tell(StartChild, ActorRef.noSender())
    parent.tell(PoisonPill.getInstance(), ActorRef.noSender())*/

    /**
     * restart
     */
    val supervisor = system.actorOf(Props.create(ActorLifeCycle.Parent::class.java))
    supervisor.tell(FailChild, ActorRef.noSender())
    supervisor.tell(CheckChild, ActorRef.noSender())

    // default supervision strategy
    // if an actor throws an exception while processing a message:
    //      * the message that caused the exception will be removed from mailbox and wont be put there again
    //      * the actor will be restarted
    //      * means that mailbox wont be lost
}

object StartChild
object Fail
object FailChild
object CheckChild
object Check
class LifecycleActor : AbstractActor() {
    private val log = Logging.getLogger(context.system, this)

    override fun createReceive(): Receive {
        return receiveBuilder()
            .match(StartChild::class.java) {
                context.actorOf(Props.create(LifecycleActor::class.java), "child")
            }
            .build()
    }

    override fun preStart() = log.info("${self.path()} I am starting")
    override fun postStop() = log.info("${self.path()} I have stopped")


}



