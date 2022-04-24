package aqua.blatt1.client;

import java.net.InetSocketAddress;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import aqua.blatt1.common.Direction;
import aqua.blatt1.common.FishModel;
import aqua.blatt1.common.msgtypes.NeighborUpdate;
import aqua.blatt1.common.msgtypes.SnapshotToken;
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

    //SNAPSHOT
    protected RecordMode recordMode = RecordMode.IDLE;
    private int localFishCount = 0;
    protected boolean isInitiator = false;
    protected boolean hasSnapshotToken = false;
    protected boolean localSnapshotDone = false;
    protected boolean globalSnapshotDone = false;
    protected SnapshotToken snapshotToken = null;
    protected int cntFishies = 0;


    private InetSocketAddress leftNeighbour;
    private InetSocketAddress rightNeighbour;

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

            cntFishies++;
            FishModel fish = new FishModel("fish" + (++fishCounter) + "@" + getId(), x, y,
                    rand.nextBoolean() ? Direction.LEFT : Direction.RIGHT);

            fishies.add(fish);
        }
    }

    synchronized void receiveFish(FishModel fish) {
        cntFishies++;
        if ((recordMode == RecordMode.LEFT && fish.getDirection() == Direction.RIGHT)
                || (recordMode == RecordMode.RIGHT && fish.getDirection() == Direction.LEFT)
                || recordMode == RecordMode.BOTH) {
            localFishCount++;
        }

        fish.setToStart();
        fishies.add(fish);
    }

    synchronized void receiveToken(Token tokenRequest) {
        token = true;

        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                if (token) {
                    token = false;
                    forwarder.handOffToken(leftNeighbour);
                }
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

    public boolean hasToken() {
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

            if (fish.hitsEdge()) {
                if (token) {
                    cntFishies--;
                    forwarder.handOff(fish.getDirection() == Direction.LEFT ? leftNeighbour : rightNeighbour, fish);
                } else {
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

    public synchronized void initiateSnapshot(boolean initiator, RecordMode mode) {
        localFishCount = cntFishies;
        this.isInitiator = initiator;
        recordMode = mode;

        //Sende den snapshot marker in beide richtungen
        forwarder.sendSnapshotMarker(leftNeighbour);
        forwarder.sendSnapshotMarker(rightNeighbour);
    }

    // Skript5 17-18
    public void receiveSnapshotMarker(InetSocketAddress sender){
        // falls es sich nicht im Aufzeichungsmodus befinden
        if(recordMode == RecordMode.IDLE) {
            // starte Aufzeichnungsmodus für alle anderen Eingangskanäle
            if(sender.equals(leftNeighbour)){
                initiateSnapshot(false, RecordMode.RIGHT);
            } else {
                initiateSnapshot(false, RecordMode.LEFT);
            }
        } else if(recordMode == RecordMode.LEFT || recordMode == RecordMode.RIGHT) {
            recordMode = RecordMode.IDLE;

            if(isInitiator) {
                SnapshotToken token = new SnapshotToken();
                token.increaseFishCount(localFishCount);
                forwarder.sendSnapshotToken(leftNeighbour, token);
            } else {
                localSnapshotDone = true;
                // Hatte Token schon aber war nicht fertig mit dem Snapshot. Jetzt bin ich fertig mit Snapshot, dann schicke ich es weiter.
                if(hasSnapshotToken){
                    snapshotToken.increaseFishCount(localFishCount);
                    forwarder.sendSnapshotToken(leftNeighbour, snapshotToken);
                    hasSnapshotToken = false;
                    localSnapshotDone = false;
                }
            }
        } else if(sender.equals(leftNeighbour) && recordMode == RecordMode.BOTH) {
            recordMode = RecordMode.RIGHT;
        } else if(sender.equals(rightNeighbour) && recordMode == RecordMode.BOTH) {
            recordMode = RecordMode.LEFT;
        }
    }

    public void receiveSnapshotToken(SnapshotToken token) {
        if (isInitiator) {
            // Token ist zurück gekommen. Zeige Anzahl an Fishies
            globalSnapshotDone = true;
            snapshotToken = token;
        } else {
            hasSnapshotToken = true;
            snapshotToken = token;
            // Hab Token und bin fertig mit dem Snapshot, dann gleich schicken
            if(localSnapshotDone) {
                token.increaseFishCount(localFishCount);
                forwarder.sendSnapshotToken(leftNeighbour, token);
                hasSnapshotToken = false;
                localSnapshotDone = false;
            }
        }
    }
}