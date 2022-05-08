package aqua.blatt1.common.msgtypes;

import java.io.Serializable;

public record RegisterResponse(String id, int lease) implements Serializable {
}
