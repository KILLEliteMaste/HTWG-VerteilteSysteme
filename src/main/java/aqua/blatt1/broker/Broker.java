package aqua.blatt1.broker;

import aqua.blatt1.common.Direction;
import aqua.blatt1.common.FishModel;
import aqua.blatt1.common.msgtypes.*;
import aqua.blatt2.broker.PoisonPill;
import messaging.Endpoint;
import messaging.Message;

import java.io.Serializable;
import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class Broker {
    private static final Endpoint endpoint = new Endpoint(4711);
    private static final ClientCollection<InetSocketAddress> clients = new ClientCollection<>();
    private static final ExecutorService executor = Executors.newFixedThreadPool(3);
    private static final ReadWriteLock READ_WRITE_LOCK = new ReentrantReadWriteLock();
    //private static final AtomicBoolean stopRequested = new AtomicBoolean(true);

    //Heimatgestützt
    //Namespace - TankId zu InetSocketAddress
    private static final Map<String, InetSocketAddress> namespace = new HashMap<>();

    public static void main(String[] args) {
        System.out.println("Start broker Task");
        /*
        executor.submit(() -> {
            int a = JOptionPane.showConfirmDialog(null,"Click the button to stop the broker","Broker Stop", JOptionPane.DEFAULT_OPTION);
            if (a == 0){
                stopRequested.set(false);
            }
        });
        */

        while (true) {
            Message message = endpoint.blockingReceive();
            if (message.getPayload() instanceof PoisonPill) {
                break;
            }
            executor.execute(() -> new BrokerTask(message).handleMessage());
        }
    }


    static final class BrokerTask {
        private final Message message;
        private final Serializable serializable;

        public BrokerTask(Message message) {
            System.out.println("Start broker Task");
            this.message = message;
            this.serializable = message.getPayload();
        }

        private void handleMessage() {
            if (serializable instanceof RegisterRequest) {
                System.out.println("Register request received");
                register(message.getSender());
            } else if (serializable instanceof DeregisterRequest deregisterRequest) {
                System.out.println("DeregisterRequest");
                deregister(message.getSender(), deregisterRequest);
            } else if (serializable instanceof HandoffRequest handoffRequest) {
                System.out.println("HandoffRequest");
                handoffFish(handoffRequest.getFish(), message.getSender());
            } else if (serializable instanceof NameResolutionRequest e) {
                handleNameResolutionRequest(message.getSender(), e);
            } else {
                System.out.println(serializable.toString());
            }
        }

        private void handleNameResolutionRequest(InetSocketAddress sender, NameResolutionRequest nrr) {
            READ_WRITE_LOCK.readLock().lock();
            endpoint.send(sender, new NameResolutionResponse(namespace.get(nrr.tankId()), nrr.requestId()));
            READ_WRITE_LOCK.readLock().unlock();
        }

        private void register(InetSocketAddress sender) {
            String newId = "tank" + (clients.size() + 1);
            READ_WRITE_LOCK.writeLock().lock();
            clients.add(newId, sender);
            namespace.put(newId, sender);
            READ_WRITE_LOCK.writeLock().unlock();

            READ_WRITE_LOCK.readLock().lock();


            endpoint.send(sender, new NeighborUpdate(clients.getLeftNeighborOf(clients.indexOf(sender)), Direction.LEFT));
            endpoint.send(sender, new NeighborUpdate(clients.getRightNeighborOf(clients.indexOf(sender)), Direction.RIGHT));


            endpoint.send(sender, new RegisterResponse(newId));
            endpoint.send(clients.getLeftNeighborOf(clients.indexOf(sender)), new NeighborUpdate(sender, Direction.RIGHT));
            endpoint.send(clients.getRightNeighborOf(clients.indexOf(sender)), new NeighborUpdate(sender, Direction.LEFT));


            if (clients.size() == 1) {
                endpoint.send(sender, new Token());
            }
            READ_WRITE_LOCK.readLock().unlock();
        }

        private void deregister(InetSocketAddress sender, DeregisterRequest dr) {

            READ_WRITE_LOCK.writeLock().lock();

            InetSocketAddress inetSocketAddressToBeRemoved = clients.getClient(clients.indexOf(dr.getId()));

            endpoint.send(clients.getLeftNeighborOf(clients.indexOf(inetSocketAddressToBeRemoved)), new NeighborUpdate(clients.getRightNeighborOf(clients.indexOf(dr.getId())), Direction.RIGHT));
            endpoint.send(clients.getRightNeighborOf(clients.indexOf(inetSocketAddressToBeRemoved)), new NeighborUpdate(clients.getLeftNeighborOf(clients.indexOf(dr.getId())), Direction.LEFT));

            clients.remove(clients.indexOf(dr.getId()));
            namespace.remove(dr.getId());

            READ_WRITE_LOCK.writeLock().unlock();
        }

        private void handoffFish(FishModel fishModel, InetSocketAddress sender) {
            if (fishModel.getDirection() == Direction.LEFT) {
                READ_WRITE_LOCK.readLock().lock();
                InetSocketAddress leftNeighbour = clients.getLeftNeighborOf(clients.indexOf(sender));
                READ_WRITE_LOCK.readLock().unlock();
                endpoint.send(leftNeighbour, new HandoffRequest(fishModel));

            } else if (fishModel.getDirection() == Direction.RIGHT) {
                READ_WRITE_LOCK.readLock().lock();
                InetSocketAddress rightNeighbour = clients.getRightNeighborOf(clients.indexOf(sender));
                READ_WRITE_LOCK.readLock().unlock();
                endpoint.send(rightNeighbour, new HandoffRequest(fishModel));
            }
        }
    }
}
