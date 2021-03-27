package akkadocumentation.iotexample.example3

import akka.actor.typed.ActorRef
import akka.actor.typed.Behavior
import akka.actor.typed.SupervisorStrategy
import akka.actor.typed.javadsl.AbstractBehavior
import akka.actor.typed.javadsl.ActorContext
import akka.actor.typed.javadsl.Behaviors
import akka.actor.typed.javadsl.Receive

class SupervisingActor private constructor(actorContext: ActorContext<String>) :
    AbstractBehavior<String>(actorContext) {

    val child: ActorRef<String> =
        context.spawn(
            Behaviors.Supervise(SupervisedActor.create()).onFailure(SupervisorStrategy.restart()),
            "SupervisedActor"
        )

    companion object {
        fun create(): Behavior<String> {
            return Behaviors.setup { ctx -> SupervisingActor(ctx) }
        }
    }

    override fun createReceive(): Receive<String> {
        return newReceiveBuilder()
            .onMessageEquals("failChild") { onFailChild() }
            .build()
    }

    private fun onFailChild(): Behavior<String> {
        child.tell("fail")
        return this
    }
}