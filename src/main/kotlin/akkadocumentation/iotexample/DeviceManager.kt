package akkadocumentation.iotexample

import akka.actor.typed.ActorRef
import akka.actor.typed.Behavior
import akka.actor.typed.PostStop
import akka.actor.typed.javadsl.AbstractBehavior
import akka.actor.typed.javadsl.ActorContext
import akka.actor.typed.javadsl.Behaviors
import akka.actor.typed.javadsl.Receive

class DeviceManager(context: ActorContext<Command>) : AbstractBehavior<DeviceManager.Command>(context) {
    companion object {
        fun create(): Behavior<Command> {
            return Behaviors.setup { ctx -> DeviceManager(ctx) }
        }
    }

    val groupIdToActor = mutableMapOf<String, ActorRef<DeviceGroup.Command>>()

    interface Command {}

    class RequestTrackDevice(val groupId: String, val deviceId: String, val replyTo: ActorRef<DeviceRegistered?>) :
        DeviceManager.Command, DeviceGroup.Command

    class DeviceRegistered(val device: ActorRef<Device.Command>)

    data class DeviceGroupTerminated(val groupId: String) : Command

    override fun createReceive(): Receive<Command> {
        return newReceiveBuilder()
            .onMessage(RequestTrackDevice::class.java) { msg -> onTrackDevice(msg) }
            .onMessage(RequestDeviceList::class.java) { msg -> onRequestDeviceList(msg) }
            .onMessage(DeviceGroupTerminated::class.java) { msg -> onTerminated(msg) }
            .onSignal(PostStop::class.java) { onPostStop() }
            .build()
    }

    private fun onTrackDevice(trackMsg: RequestTrackDevice): DeviceManager {
        val groupId = trackMsg.groupId
        val ref = groupIdToActor.getOrPut(groupId) {
            context.log.info("Creating device group actor for {}", groupId)
            val deviceGroupActor = context.spawn(DeviceGroup.create(groupId), "group-" + groupId)
            context.watchWith(deviceGroupActor, DeviceGroupTerminated(groupId))
            deviceGroupActor
        }
        ref.tell(trackMsg)
        return this
    }

    private fun onRequestDeviceList(request: RequestDeviceList): DeviceManager {
        val ref = groupIdToActor[request.groupId]
        if (ref != null)
            ref.tell(request)
        else
            request.replyTo.tell(ReplyDeviceList(request.requestId, setOf()))
        return this
    }

    private fun onTerminated(t: DeviceGroupTerminated): DeviceManager {
        context.log.info("Device group actor for {} has been terminated", t.groupId)
        groupIdToActor.remove(t.groupId)
        return this
    }

    private fun onPostStop(): DeviceManager {
        context.log.info("DeviceManager Stopped")
        return this
    }
}