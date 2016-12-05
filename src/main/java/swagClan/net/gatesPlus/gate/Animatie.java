package swagClan.net.gatesPlus.gate;

import swagClan.net.gatesPlus.Main;

public class Animatie extends Thread {

	private Main main;
	private Gate gate;
	private boolean running;
	private boolean done;
	private int interval;

	public Animatie(Main main, Gate gate) {
		this.main = main;
		this.gate = gate;
		this.running = true;
		this.done = true;
	}

	public void stopTimer() {
		this.running = false;
	}

	@Override
	public void run() {
		if (gate.gateNeedstoOpen()) {
			// gate opens
		} else {
			// gate closes
		}

		if (running) {
			try {
				this.sleep(interval);
				if (!done) {
					this.start();
				}
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		} else {
			// eventueele clearing van variabele
		}
	}

}
