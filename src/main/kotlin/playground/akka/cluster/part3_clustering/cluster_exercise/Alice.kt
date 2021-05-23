package playground.akka.cluster.part3_clustering.cluster_exercise

class Alice {
}

fun main() {
    val alice = ChatApp("Alice", 2551)
    alice.readLineAndSendToCluster()
}