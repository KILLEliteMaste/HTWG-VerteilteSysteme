package aqua.blatt1.broker;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/*
 * This class is not thread-safe and hence must be used in a thread-safe way, e.g. thread confined or 
 * externally synchronized. 
 */

public class ClientCollection<T> {
	public class Client {
		final String id;
		final T client;

        final Instant instant;

		Client(String id, T client, Instant instant) {
			this.id = id;
			this.client = client;
            this.instant = instant;
		}
	}

	private final List<Client> clients;

	public ClientCollection() {
		clients = new ArrayList<>();
	}

	public ClientCollection<T> add(String id, T client, Instant instant) {
		clients.add(new Client(id, client, instant));
		return this;
	}

	public ClientCollection<T> remove(int index) {
		clients.remove(index);
		return this;
	}

	public int indexOf(String id) {
		for (int i = 0; i < clients.size(); i++)
			if (clients.get(i).id.equals(id))
				return i;
		return -1;
	}

	public int indexOf(T client) {
		for (int i = 0; i < clients.size(); i++)
			if (clients.get(i).client.equals(client))
				return i;
		return -1;
	}

    public String getIdOf(int index) {
        return clients.get(index).id;
    }

	public T getClient(int index) {
		return clients.get(index).client;
	}

    public List<Client> getClients() {
        return clients;
    }


    public int size() {
		return clients.size();
	}

	public T getLeftNeighborOf(int index) {
        System.out.println("SIZE: "+clients.size());
		return index == 0 ? clients.get(clients.size() - 1).client : clients.get(index - 1).client;
	}

	public T getRightNeighborOf(int index) {
		return index < clients.size() - 1 ? clients.get(index + 1).client : clients.get(0).client;
	}

}
