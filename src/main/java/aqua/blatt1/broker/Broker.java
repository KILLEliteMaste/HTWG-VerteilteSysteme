package aqua.blatt1.broker;

import aqua.blatt1.common.Direction;
import aqua.blatt1.common.FishModel;
import aqua.blatt1.common.msgtypes.DeregisterRequest;
import aqua.blatt1.common.msgtypes.HandoffRequest;
import aqua.blatt1.common.msgtypes.RegisterRequest;
import aqua.blatt1.common.msgtypes.RegisterResponse;
import aqua.blatt2.broker.PoisonPill;
import messaging.Endpoint;
import messaging.Message;

import java.io.Serializable;
import java.net.InetSocketAddress;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

@SuppressWarnings("InfiniteLoopStatement")
public class Broker {
    private static final Endpoint endpoint = new Endpoint(4711);
    private static final ClientCollection<InetSocketAddress> clients = new ClientCollection<>();
    private static final ExecutorService executor = Executors.newFixedThreadPool(3);
    private static final ReadWriteLock READ_WRITE_LOCK = new ReentrantReadWriteLock();
    //private static final AtomicBoolean stopRequested = new AtomicBoolean(true);

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
            executor.submit(() -> new BrokerTask(message).handleMessage());
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
                deregister(deregisterRequest.getId());
            } else if (serializable instanceof HandoffRequest handoffRequest) {
                System.out.println("HandoffRequest");
                handoffFish(handoffRequest.getFish(), message.getSender());
            } else {
                System.out.println(serializable.toString());
            }
        }

        private void register(InetSocketAddress sender) {
            String newId = "tank" + (clients.size() + 1);
            READ_WRITE_LOCK.writeLock().lock();
            clients.add(newId, sender);
            READ_WRITE_LOCK.writeLock().unlock();
            endpoint.send(sender, new RegisterResponse(newId));
        }

        private void deregister(String id) {
            READ_WRITE_LOCK.writeLock().lock();
            clients.remove(clients.indexOf(id));
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
