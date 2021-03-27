package akkadocumentation.iotexample

import akka.actor.typed.ActorRef

class DeviceManager {

    interface Command {}

    class RequestTrackDevice(val groupId: String, val deviceId: String, val replyTo: ActorRef<DeviceRegistered?>) : DeviceManager.Command, DeviceGroup.Command

    class DeviceRegistered(val device: ActorRef<Device.Command>)
}