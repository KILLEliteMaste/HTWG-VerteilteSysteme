package aqua.blatt1.client;

import java.net.InetSocketAddress;

import aqua.blatt1.common.msgtypes.*;
import messaging.Endpoint;
import messaging.Message;
import aqua.blatt1.common.FishModel;
import aqua.blatt1.common.Properties;

public class ClientCommunicator {
	private final Endpoint endpoint;

	public ClientCommunicator() {
		endpoint = new Endpoint();
	}

	public class ClientForwarder {
		private final InetSocketAddress broker;

		private ClientForwarder() {
			this.broker = new InetSocketAddress(Properties.HOST, Properties.PORT);
		}

		public void register() {
			endpoint.send(broker, new RegisterRequest());
		}

		public void deregister(String id) {
			endpoint.send(broker, new DeregisterRequest(id));
		}

		public void handOff(InetSocketAddress inetSocketAddress, FishModel fish) {
			endpoint.send(inetSocketAddress, new HandoffRequest(fish));
		}

		public void handOffToken(InetSocketAddress inetSocketAddress) {
			endpoint.send(inetSocketAddress, new Token());
		}

		public void sendSnapshotMarker(InetSocketAddress inetSocketAddress) {
			endpoint.send(inetSocketAddress, new SnapshotMarker());
		}

		public void sendSnapshotToken(InetSocketAddress neigbour, SnapshotToken snapshotToken) {
			endpoint.send(neigbour, snapshotToken);
		}

        public void sendLocationRequest(InetSocketAddress neighbor, String fishId){
            endpoint.send(neighbor, new LocationRequest(fishId));
        }

        public void sendNameResolutionRequest(NameResolutionRequest resolutionRequest) {
            endpoint.send(broker, resolutionRequest);
        }

        public void sendLocationUpdate(InetSocketAddress homeTank, LocationUpdate locationUpdate) {
            endpoint.send(homeTank, locationUpdate);
        }
	}

	public class ClientReceiver extends Thread {
		private final TankModel tankModel;

		private ClientReceiver(TankModel tankModel) {
			this.tankModel = tankModel;
		}

		@Override
		public void run() {
			while (!isInterrupted()) {
				Message msg = endpoint.blockingReceive();

				if (msg.getPayload() instanceof RegisterResponse)
					tankModel.onRegistration(((RegisterResponse) msg.getPayload()).getId());

				if (msg.getPayload() instanceof HandoffRequest)
					tankModel.receiveFish(((HandoffRequest) msg.getPayload()).getFish());

				if (msg.getPayload() instanceof NeighborUpdate)
					tankModel.updateNeighbor(((NeighborUpdate) msg.getPayload()));

				if (msg.getPayload() instanceof Token)
					tankModel.receiveToken(((Token) msg.getPayload()));

				if (msg.getPayload() instanceof SnapshotMarker)
					tankModel.receiveSnapshotMarker(msg.getSender());

				if (msg.getPayload() instanceof SnapshotToken)
					tankModel.receiveSnapshotToken((SnapshotToken) msg.getPayload());

                if(msg.getPayload() instanceof LocationRequest lr){
                    //Vorwärtsreferenzen
                    //tankModel.locateFishGlobally(((LocationRequest) msg.getPayload()).getFishId());

                    //Heimatgestützt
                    tankModel.locateFishLocally(lr.fishId());
                }

                if(msg.getPayload() instanceof NameResolutionResponse nrr){
                    tankModel.receiveNameResolutionResponse(nrr);
                }

                if(msg.getPayload() instanceof LocationUpdate lu){
                    tankModel.updateFishLocation(lu, msg.getSender());
                }
			}
			System.out.println("Receiver stopped.");
		}
	}

	public ClientForwarder newClientForwarder() {
		return new ClientForwarder();
	}

	public ClientReceiver newClientReceiver(TankModel tankModel) {
		return new ClientReceiver(tankModel);
	}

}
