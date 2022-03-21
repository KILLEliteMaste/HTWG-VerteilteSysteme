package aqua.blatt1.broker;

import aqua.blatt1.common.Direction;
import aqua.blatt1.common.FishModel;
import aqua.blatt1.common.msgtypes.DeregisterRequest;
import aqua.blatt1.common.msgtypes.HandoffRequest;
import aqua.blatt1.common.msgtypes.RegisterRequest;
import aqua.blatt1.common.msgtypes.RegisterResponse;
import messaging.Endpoint;
import messaging.Message;

import java.io.Serializable;
import java.net.InetSocketAddress;

public class Broker {
    private static final Endpoint endpoint = new Endpoint(4711);
    private static final ClientCollection<InetSocketAddress> clients = new ClientCollection<>();

    public static void main(String[] args) {

        System.out.println("Start broker");
        while (true) {
            Message message = endpoint.blockingReceive();
            Serializable serializable = message.getPayload();

            if (serializable instanceof RegisterRequest) {
                System.out.println("Register request received");
                register(message.getSender());
            } else if (serializable instanceof DeregisterRequest) {
                System.out.println("DeregisterRequest");
                deregister(((DeregisterRequest) serializable).getId());
            } else if (serializable instanceof HandoffRequest) {
                System.out.println("HandoffRequest");
                handoffFish(((HandoffRequest) serializable).getFish(), message.getSender());
            } else {
                System.out.println(serializable.toString());
            }
        }
    }

    private static void register(InetSocketAddress sender) {
        String newId = "tank" + (clients.size() + 1);
        clients.add(newId, sender);
        endpoint.send(sender, new RegisterResponse(newId));
    }

    private static void deregister(String id) {
        clients.remove(clients.indexOf(id));
    }

    private static void handoffFish(FishModel fishModel, InetSocketAddress sender) {
        if (fishModel.getDirection() == Direction.LEFT) {
            InetSocketAddress leftNeighbour = clients.getLeftNeighborOf(clients.indexOf(sender));
            endpoint.send(leftNeighbour, new HandoffRequest(fishModel));

        } else if (fishModel.getDirection() == Direction.RIGHT) {
            InetSocketAddress rightNeighbour = clients.getRightNeighborOf(clients.indexOf(sender));
            endpoint.send(rightNeighbour, new HandoffRequest(fishModel));
        }
    }
}
