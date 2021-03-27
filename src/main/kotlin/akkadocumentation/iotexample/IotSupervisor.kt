package akkadocumentation.iotexample

import akka.actor.typed.Behavior
import akka.actor.typed.PostStop
import akka.actor.typed.javadsl.AbstractBehavior
import akka.actor.typed.javadsl.ActorContext
import akka.actor.typed.javadsl.Behaviors
import akka.actor.typed.javadsl.Receive

class IotSupervisor private constructor(actorContext: ActorContext<Unit>) : AbstractBehavior<Unit>(actorContext) {

    companion object {
        fun create(): Behavior<Unit> {
            return Behaviors.setup { ctx -> IotSupervisor(ctx) }
        }
    }

    init {
        context.log.info("IoT Application started")
    }

    override fun createReceive(): Receive<Unit> {
        return newReceiveBuilder()
            .onSignal(PostStop::class.java) { onPostStop() }
            .build()
    }

    private fun onPostStop(): IotSupervisor {
        context.log.info("IoT Application stopped")
        return this
    }
}