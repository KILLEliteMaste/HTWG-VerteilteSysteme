package aqua.blatt1.common.msgtypes;

import java.io.Serializable;

public class SnapshotToken implements Serializable {
    int fishCount = 0;

    public void increaseFishCount(int count) {
        fishCount = fishCount + count;
    }

    public int getFishCount() {
        return fishCount;
    }
}
