package akkadocumentation.iotexample

import akka.actor.typed.ActorRef
import akka.actor.typed.Behavior
import akka.actor.typed.PostStop
import akka.actor.typed.javadsl.AbstractBehavior
import akka.actor.typed.javadsl.ActorContext
import akka.actor.typed.javadsl.Behaviors
import akka.actor.typed.javadsl.Receive
import java.time.Duration

class DeviceGroup private constructor(actorContext: ActorContext<DeviceGroup.Command>, var groupId: String) :
    AbstractBehavior<DeviceGroup.Command>(actorContext) {

    private val deviceIdToActor = mutableMapOf<String, ActorRef<Device.Command>>()

    interface Command {}

    private class DeviceTerminated(
        val device: ActorRef<Device.Command>,
        val groupId: String,
        val deviceId: String
    ) : Command


    companion object {
        fun create(groupId: String): Behavior<Command> {
            return Behaviors.setup { ctx -> DeviceGroup(ctx, groupId) }
        }
    }

    init {
        context.log.info("DeviceGroup {} started", groupId)
    }

    override fun createReceive(): Receive<Command> {
        return newReceiveBuilder()
            .onMessage(DeviceManager.RequestTrackDevice::class.java) { msg -> onTrackDevice(msg) }
            .onMessage(RequestDeviceList::class.java) { msg -> onDeviceList(msg) }
            .onMessage(DeviceTerminated::class.java) { msg -> onTerminated(msg) }
            .onMessage(RequestAllTemperatures::class.java, { it.groupId == groupId }) { msg -> onAllTemperatures(msg) }
            .onSignal(PostStop::class.java) { onPostStop() }
            .build()
    }

    private fun onPostStop(): DeviceGroup {
        context.log.info("DeviceGroup {} stopped", groupId)
        return this
    }

    private fun onTrackDevice(trackMsg: DeviceManager.RequestTrackDevice): DeviceGroup {
        if (groupId == trackMsg.groupId) {
            deviceIdToActor.getOrPut(trackMsg.deviceId) {
                context.log.info("Creating device actor for {}", trackMsg.deviceId)
                var deviceActor =
                    context.spawn(Device.create(trackMsg.groupId, trackMsg.deviceId), "device-" + trackMsg.deviceId)
                context.watchWith(deviceActor, DeviceTerminated(deviceActor, groupId, trackMsg.deviceId))
                trackMsg.replyTo.tell(DeviceManager.DeviceRegistered(deviceActor))
                deviceActor
            }
        } else {
            context.log.warn(
                "Ignoring TrackDevice request for {}. This actor is responsible for {}.",
                trackMsg.groupId,
                this.groupId
            )
        }
        return this
    }

    private fun onDeviceList(request: RequestDeviceList): DeviceGroup {
        request.replyTo.tell(ReplyDeviceList(request.requestId, deviceIdToActor.keys))
        return this
    }

    private fun onTerminated(deviceTerminated: DeviceTerminated): DeviceGroup {
        context.log.info("Device actor for {} has been terminated", deviceTerminated.deviceId)
        deviceIdToActor.remove(deviceTerminated.deviceId)
        return this
    }

    private fun onAllTemperatures(r: RequestAllTemperatures): DeviceGroup {
        val deviceIdToActorCopy = deviceIdToActor.toMap()
        context.spawnAnonymous(
            DeviceGroupQuery.create(
                deviceIdToActorCopy,
                r.requestId,
                r.replyTo,
                Duration.ofSeconds(3)
            )
        )
        return this
    }

}