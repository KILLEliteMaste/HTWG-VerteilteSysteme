package aqua.blatt1.broker;

import aqua.blatt1.common.Direction;
import aqua.blatt1.common.FishModel;
import aqua.blatt1.common.msgtypes.*;
import aqua.blatt2.broker.PoisonPill;
import messaging.Endpoint;
import messaging.Message;

import java.io.Serializable;
import java.net.InetSocketAddress;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
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


    private static final int LEASE_TIME = 5000;
    private static final Timer timer = new Timer();


    public static void main(String[] args) {
        System.out.println("Start broker Task");
        new BrokerTask();
    }


    static final class BrokerTask {

        public BrokerTask() {
            System.out.println("Start broker Task");

            timer.schedule(new TimerTask() {
                @Override
                public void run() {
                    READ_WRITE_LOCK.readLock().lock();
                    clients.getClients().stream()
                            .filter(client -> client.instant.isBefore(Instant.now().minusMillis(LEASE_TIME * 3)))
                            .forEach(client -> {
                                deregister(client.client);
                            });

                    READ_WRITE_LOCK.readLock().unlock();
                }
            }, 5000, 5000/*15000*/);

            while (true) {
                Message message = endpoint.blockingReceive();
                if (message.getPayload() instanceof PoisonPill) {
                    break;
                }
                executor.execute(() -> handleMessage(message, message.getPayload()));
            }
        }

        private void handleMessage(final Message message, final Serializable serializable) {
            if (serializable instanceof RegisterRequest) {
                System.out.println("Register request received");
                register(message.getSender());
            } else if (serializable instanceof DeregisterRequest) {
                System.out.println("DeregisterRequest");
                READ_WRITE_LOCK.writeLock().lock();
                deregister(message.getSender());
                READ_WRITE_LOCK.writeLock().unlock();
            } else if (serializable instanceof HandoffRequest handoffRequest) {
                System.out.println("HandoffRequest");
                handoffFish(handoffRequest.fish(), message.getSender());
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
            READ_WRITE_LOCK.writeLock().lock();
            int index = clients.indexOf(sender);
            if (index != -1) {
                String id = clients.getIdOf(index);
                clients.getActualClient(index).instant = Instant.now();
                READ_WRITE_LOCK.writeLock().unlock();
                return;
            }

            String newId = "tank" + (clients.size() + 1);

            clients.add(newId, sender, Instant.now());
            namespace.put(newId, sender);
            READ_WRITE_LOCK.writeLock().unlock();

            READ_WRITE_LOCK.readLock().lock();

            endpoint.send(sender, new NeighborUpdate(clients.getLeftNeighborOf(clients.indexOf(sender)), Direction.LEFT));
            endpoint.send(sender, new NeighborUpdate(clients.getRightNeighborOf(clients.indexOf(sender)), Direction.RIGHT));


            endpoint.send(sender, new RegisterResponse(newId, LEASE_TIME));
            endpoint.send(clients.getLeftNeighborOf(clients.indexOf(sender)), new NeighborUpdate(sender, Direction.RIGHT));
            endpoint.send(clients.getRightNeighborOf(clients.indexOf(sender)), new NeighborUpdate(sender, Direction.LEFT));


            if (clients.size() == 1) {
                endpoint.send(sender, new Token());
            }
            READ_WRITE_LOCK.readLock().unlock();
        }

        private void deregister(InetSocketAddress sender) {
            READ_WRITE_LOCK.writeLock().lock();

            InetSocketAddress inetSocketAddressToBeRemoved = clients.getClient(clients.indexOf(sender));

            endpoint.send(clients.getLeftNeighborOf(clients.indexOf(inetSocketAddressToBeRemoved)), new NeighborUpdate(clients.getRightNeighborOf(clients.indexOf(sender)), Direction.RIGHT));
            endpoint.send(clients.getRightNeighborOf(clients.indexOf(inetSocketAddressToBeRemoved)), new NeighborUpdate(clients.getLeftNeighborOf(clients.indexOf(sender)), Direction.LEFT));

            String id = clients.getIdOf(clients.indexOf(sender));
            clients.remove(clients.indexOf(sender));
            namespace.remove(id);

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
