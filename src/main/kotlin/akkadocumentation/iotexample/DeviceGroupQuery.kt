package akkadocumentation.iotexample

import akka.actor.typed.ActorRef
import akka.actor.typed.Behavior
import akka.actor.typed.javadsl.AbstractBehavior
import akka.actor.typed.javadsl.ActorContext
import akka.actor.typed.javadsl.Receive
import akka.actor.typed.javadsl.TimerScheduler
import akka.actor.typed.javadsl.Behaviors
import java.time.Duration

class DeviceGroupQuery private constructor(
    private val deviceIdToActor: Map<String, ActorRef<Device.Command>>,
    private val requestId: Long,
    private val requester: ActorRef<RespondAllTemperatures>,
    private val timeout: Duration,
    context: ActorContext<Command>,
    private val timers: TimerScheduler<Command>) : AbstractBehavior<DeviceGroupQuery.Command>(context) {

    private val repliesSoFar = mutableMapOf<String, TemperatureReading>()
    private var stillWaiting = mutableSetOf<String>()

    companion object {
        fun create(deviceIdToActor: Map<String, ActorRef<Device.Command>>,
                   requestId: Long,
                   requester: ActorRef<RespondAllTemperatures>,
                   timeout: Duration,): Behavior<Command> {
            return Behaviors.setup {
                ctx -> Behaviors.withTimers { timers -> DeviceGroupQuery(deviceIdToActor, requestId, requester, timeout, ctx, timers) }
            }
        }
    }

    interface Command
    enum class CollectionTimeout: Command { INSTANCE }
    data class WrappedRespondTemperature(val response: Device.RespondTemperature): Command
    data class DeviceTerminated(val deviceId: String): Command

    init {
        timers.startSingleTimer(CollectionTimeout.INSTANCE, timeout)
        val respondTemperatureAdapter = context.messageAdapter(Device.RespondTemperature::class.java) { WrappedRespondTemperature(it)}
        deviceIdToActor.forEach { deviceIdEntry ->
            context.watchWith(deviceIdEntry.value, DeviceTerminated(deviceIdEntry.key))
            deviceIdEntry.value.tell(Device.ReadTemperature(0L, respondTemperatureAdapter))
        }
        stillWaiting = deviceIdToActor.keys as MutableSet<String>
    }

    override fun createReceive(): Receive<Command> {
        return newReceiveBuilder()
            .onMessage(WrappedRespondTemperature::class.java) {msg -> onRespondTemperature(msg)}
            .onMessage(DeviceTerminated::class.java) {msg -> onDeviceTerminated(msg)}
            .onMessage(CollectionTimeout::class.java) {msg -> onCollectionTimeout(msg)}
            .build()
    }

    private fun onRespondTemperature(r: WrappedRespondTemperature): Behavior<Command> {
        val reading: TemperatureReading = r.response.value?.run { Temperature(r.response.value) } ?: TemperatureNotAvailable.INSTANCE
        //val reading = if (r.response.value != null) Temperature(r.response.value) else TemperatureNotAvailable.INSTANCE
        repliesSoFar[r.response.deviceId] = reading
        stillWaiting.remove(r.response.deviceId)
        return respondWhenAllCollected()
    }

    private fun onDeviceTerminated(terminated: DeviceTerminated): Behavior<Command> {
        if (stillWaiting.contains(terminated.deviceId)) {
            repliesSoFar[terminated.deviceId] = DeviceNotAvailable.INSTANCE
            stillWaiting.remove(terminated.deviceId)
        }
        return respondWhenAllCollected()
    }

    private fun onCollectionTimeout(timeout: CollectionTimeout): Behavior<Command> {
        stillWaiting.forEach { deviceId ->
            repliesSoFar[deviceId] = DeviceTimedOut.INSTANCE
        }
        stillWaiting.clear()
        return respondWhenAllCollected()
    }

    private fun respondWhenAllCollected(): Behavior<Command> {
        return if (stillWaiting.isEmpty()) {
            requester.tell(RespondAllTemperatures(requestId, repliesSoFar))
            Behaviors.stopped()
        } else {
            this
        }
    }
}