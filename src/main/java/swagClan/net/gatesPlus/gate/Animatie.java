package swagClan.net.gatesPlus.gate;

import swagClan.net.gatesPlus.Main;

public class Animatie extends Thread {

	private Main main;
	private Gate gate;

	public Animatie(Main main, Gate gate) {
		this.main = main;
		this.gate = gate;
	}

	@Override
	public void run() {
		if (gate.gateNeedstoOpen()) {
			// gate opens
		} else {
			// gate closes
		}
	}

}
