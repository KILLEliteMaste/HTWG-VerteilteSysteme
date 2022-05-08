package aqua.blatt1.common.msgtypes;

import aqua.blatt1.common.Direction;

import java.io.Serializable;
import java.net.InetSocketAddress;

public record NeighborUpdate(InetSocketAddress newtInetSocketAddress, Direction direction) implements Serializable {
}
