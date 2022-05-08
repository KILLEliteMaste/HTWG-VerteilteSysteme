package aqua.blatt1.common.msgtypes;

import java.io.Serializable;

@SuppressWarnings("serial")
public record DeregisterRequest(String id) implements Serializable {


}
