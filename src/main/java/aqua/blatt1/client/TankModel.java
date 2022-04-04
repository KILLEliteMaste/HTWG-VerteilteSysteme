package aqua.blatt1.client;

import java.net.InetSocketAddress;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import aqua.blatt1.common.Direction;
import aqua.blatt1.common.FishModel;
import aqua.blatt1.common.msgtypes.NeighborUpdate;
import aqua.blatt1.common.msgtypes.Token;

public class TankModel extends Observable implements Iterable<FishModel> {

    public static final int WIDTH = 600;
    public static final int HEIGHT = 350;
    protected static final int MAX_FISHIES = 5;
    protected static final Random rand = new Random();
    protected volatile String id;
    protected final Set<FishModel> fishies;
    protected int fishCounter = 0;
    protected final ClientCommunicator.ClientForwarder forwarder;

    private InetSocketAddress leftNeighbour;
    private InetSocketAddress rightNeighbour ;

    private boolean token = false;
    Timer timer = new Timer();


    public TankModel(ClientCommunicator.ClientForwarder forwarder) {
        this.fishies = Collections.newSetFromMap(new ConcurrentHashMap<FishModel, Boolean>());
        this.forwarder = forwarder;


    }

    synchronized void onRegistration(String id) {
        this.id = id;
        newFish(WIDTH - FishModel.getXSize(), rand.nextInt(HEIGHT - FishModel.getYSize()));
    }

    public synchronized void newFish(int x, int y) {
        if (fishies.size() < MAX_FISHIES) {
            x = x > WIDTH - FishModel.getXSize() - 1 ? WIDTH - FishModel.getXSize() - 1 : x;
            y = y > HEIGHT - FishModel.getYSize() ? HEIGHT - FishModel.getYSize() : y;

            FishModel fish = new FishModel("fish" + (++fishCounter) + "@" + getId(), x, y,
                    rand.nextBoolean() ? Direction.LEFT : Direction.RIGHT);

            fishies.add(fish);
        }
    }

    synchronized void receiveFish(FishModel fish) {
        fish.setToStart();
        fishies.add(fish);
    }

    synchronized void receiveToken(Token tokenRequest) {
        token = true;

        System.out.println("HAS TOKEN: "+id);
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                if (token){
                    token = false;
                    System.out.println("SEND TOKEN TO LEFT");
                    forwarder.handOffToken(leftNeighbour);

                }
                //System.out.println("TIMER TASK FINISHED IN: ");
            }
        }, 5000);
    }

    synchronized void updateNeighbor(NeighborUpdate neighborUpdate) {
        if (neighborUpdate.getDirection() == Direction.LEFT) {
            leftNeighbour = neighborUpdate.getNewtInetSocketAddress();
        } else if (neighborUpdate.getDirection() == Direction.RIGHT) {
            rightNeighbour = neighborUpdate.getNewtInetSocketAddress();
        }
    }

    public String getId() {
        return id;
    }

    public boolean hasToken(){
        return token;

    }

    public synchronized int getFishCounter() {
        return fishCounter;
    }

    public synchronized Iterator<FishModel> iterator() {
        return fishies.iterator();
    }

    private synchronized void updateFishies() {
        for (Iterator<FishModel> it = iterator(); it.hasNext(); ) {
            FishModel fish = it.next();

            fish.update();

            if (fish.hitsEdge()){
                if (token){
                    forwarder.handOff(fish.getDirection() == Direction.LEFT ? leftNeighbour : rightNeighbour, fish);
                }else {
                    fish.reverse();
                }
            }

            if (fish.disappears())
                it.remove();
        }
    }

    private synchronized void update() {
        updateFishies();
        setChanged();
        notifyObservers();
    }

    protected void run() {
        forwarder.register();

        try {
            while (!Thread.currentThread().isInterrupted()) {
                update();
                TimeUnit.MILLISECONDS.sleep(10);
            }
        } catch (InterruptedException consumed) {
            // allow method to terminate
        }
    }

    public synchronized void finish() {
        forwarder.deregister(id);
    }

}