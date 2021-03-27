package akkadocumentation.iotexample

import akka.actor.typed.ActorRef
import akka.actor.typed.Behavior
import akka.actor.typed.PostStop
import akka.actor.typed.javadsl.AbstractBehavior
import akka.actor.typed.javadsl.ActorContext
import akka.actor.typed.javadsl.Behaviors
import akka.actor.typed.javadsl.Receive

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
}