package aqua.blatt1.common.msgtypes;

import aqua.blatt1.common.Direction;

import java.io.Serializable;
import java.net.InetSocketAddress;

public class NeighborUpdate implements Serializable {
    private final InetSocketAddress newtInetSocketAddress;
    private final Direction direction;

    public NeighborUpdate(InetSocketAddress newtInetSocketAddress, Direction direction) {
        this.direction = direction;
        this.newtInetSocketAddress = newtInetSocketAddress;
    }

    public InetSocketAddress getNewtInetSocketAddress() {
        return newtInetSocketAddress;
    }

    public Direction getDirection() {
        return direction;
    }
}
