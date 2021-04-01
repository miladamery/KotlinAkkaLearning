package akkadocumentation.iotexample

import akka.actor.typed.ActorRef
import akka.actor.typed.Behavior
import akka.actor.typed.PostStop
import akka.actor.typed.javadsl.AbstractBehavior
import akka.actor.typed.javadsl.ActorContext
import akka.actor.typed.javadsl.Behaviors
import akka.actor.typed.javadsl.Receive
import akkadocumentation.iotexample.Device.TemperatureRecorded

import akkadocumentation.iotexample.Device.RecordTemperature


class Device private constructor(
    actorContext: ActorContext<Device.Command>,
    private val groupId: String,
    private val deviceId: String
) : AbstractBehavior<Device.Command>(actorContext) {

    private var lastTemperatureReading: Double? = null

    companion object {
        fun create(groupId: String, deviceId: String): Behavior<Command> {
            return Behaviors.setup { ctx -> Device(ctx, groupId, deviceId) }
        }
    }

    init {
        context.log.info("Device actor $groupId-$deviceId started")
    }

    interface Command {}

    data class ReadTemperature(val requestId: Long, val replyTo: ActorRef<RespondTemperature>) : Command

    data class RespondTemperature(var requestId: Long,val deviceId: String, val value: Double?)

    data class RecordTemperature(val requestId: Long, val value: Double?, val replyTo: ActorRef<TemperatureRecorded>) :
        Command

    data class TemperatureRecorded(val requestId: Long)

    enum class Passivate : Command {
        INSTANCE
    }

    override fun createReceive(): Receive<Command> {
        return newReceiveBuilder()
            .onMessage(ReadTemperature::class.java) { msg -> onReadTemperature(msg) }
            .onMessage(RecordTemperature::class.java) { msg -> onRecordTemperature(msg) }
            .onMessage(Passivate::class.java) { Behaviors.stopped() }
            .onSignal(PostStop::class.java) { onPostStop() }
            .build()
    }

    private fun onReadTemperature(r: ReadTemperature): Behavior<Command> {
        val rrr = RespondTemperature(r.requestId, deviceId, lastTemperatureReading)
        r.replyTo.tell(rrr)
        return this
    }

    private fun onRecordTemperature(r: RecordTemperature): Behavior<Command?>? {
        context.log.info("Recorded temperature reading {} with {}", r.value, r.requestId)
        lastTemperatureReading = r.value
        r.replyTo.tell(TemperatureRecorded(r.requestId))
        return this
    }

    private fun onPostStop(): Device? {
        context.log.info("Device actor {}-{} stopped", groupId, deviceId)
        return this
    }
}
