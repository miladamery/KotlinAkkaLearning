package playground.akka.essentials.par4faulttolerance

import akka.actor.*
import akka.event.Logging
import akka.event.LoggingAdapter

fun main() {
    val system = ActorSystem.create("StoppingActorsDemo")

    /**
     * # Method #1 - using context.stop
     */
    /*val parent = system.actorOf(Props.create(Parent::class.java), "parent")
    parent.tell(Parent.Companion.StartChild("child1"), ActorRef.noSender())
    Thread.sleep(1000)
    val child = system.actorSelection("akka://StoppingActorsDemo/user/parent/child1")
    child.tell("Hi Kid", ActorRef.noSender())

    parent.tell(Parent.Companion.StopChild("child1"), ActorRef.noSender())
    *//*(1..50).forEach { _ ->
        child.tell("are you there?", ActorRef.noSender())
    }*//*

    parent.tell(Parent.Companion.StartChild("child2"), ActorRef.noSender())
    Thread.sleep(1000)
    val child2 = system.actorSelection("akka://StoppingActorsDemo/user/parent/child2")
    child2.tell("Hi, second child", ActorRef.noSender())
    parent.tell(Parent.Companion.Stop, ActorRef.noSender())
    (1..10).forEach { _ -> parent.tell("parent are you still there?", ActorRef.noSender()) } // should not be received
    (1..100).forEach { child2.tell("[$it]Are you still alive?", ActorRef.noSender()) } // should not be received*/

    /**
     * method #2 - using special messages
     */
    /*val looseActor = system.actorOf(Props.create(Child::class.java))
    looseActor.tell("Hello loose actor", ActorRef.noSender())
    looseActor.tell(PoisonPill.getInstance(), ActorRef.noSender())
    looseActor.tell("loose actor, are you still there?", ActorRef.noSender())

    val abruptlyTerminatedActor = system.actorOf(Props.create(Child::class.java))
    abruptlyTerminatedActor.tell("you are about to be terminated", ActorRef.noSender())
    abruptlyTerminatedActor.tell(Kill.getInstance(), ActorRef.noSender())
    abruptlyTerminatedActor.tell("you have been terminated", ActorRef.noSender())*/

    /**
     * Death Watch
     */
    val watcher = system.actorOf(Props.create(Watcher::class.java), "watcher")
    watcher.tell(Parent.Companion.StartChild("watchedChild"), ActorRef.noSender())
    val watchedChild = system.actorSelection("/user/watcher/watchedChild")
    Thread.sleep(1000)

    watchedChild.tell(PoisonPill.getInstance(), ActorRef.noSender())
}

class Parent : AbstractActor() {
    private val logger: LoggingAdapter = Logging.getLogger(context.system, this)

    companion object {
        data class StopChild(val name: String)
        data class StartChild(val name: String)
        object Stop
    }

    override fun createReceive(): Receive = withChildren(mapOf())

    private fun withChildren(children: Map<String, ActorRef>): Receive {
        return receiveBuilder()
            .match(StartChild::class.java) {
                logger.info("Starting child ${it.name}")
                val childActor = context.actorOf(Props.create(Child::class.java), it.name)
                logger.info(childActor.path().toString())
                context.become(withChildren(children + (it.name to childActor)))
            }
            .match(StopChild::class.java) {
                logger.info("Stopping child with name ${it.name}")
                val childOption = children[it.name]
                childOption?.run {
                    context.stop(this)
                }
            }
            .match(Stop::class.java) {
                logger.info("Stopping myself")
                context.stop(self)
            }
            .matchAny(){
                logger.info(it.toString())
            }
            .build()
    }

}

class Child : AbstractActor() {
    private val logger: LoggingAdapter = Logging.getLogger(context.system, this)

    override fun createReceive(): Receive {
        return receiveBuilder()
            .matchAny() {
                logger.info(self.path().toString())
                logger.info(it.toString())
            }
            .build()
    }

}

class Watcher : AbstractActor() {
    private val log: LoggingAdapter = Logging.getLogger(context.system, this)
    override fun createReceive(): Receive {
        return receiveBuilder()
            .match(Parent.Companion.StartChild::class.java) {
                val child = context.actorOf(Props.create(Child::class.java), "watchedChild")
                log.info("Started and watching child ${it.name}")
                context.watch(child)
            }
            .match(Terminated::class.java) {
                log.info("the reference that im watching ${it.actor} has been stopped")
            }
            .build()
    }
}