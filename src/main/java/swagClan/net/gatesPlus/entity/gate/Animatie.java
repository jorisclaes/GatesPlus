package swagClan.net.gatesPlus.entity.gate;

import swagClan.net.gatesPlus.Main;

public class Animatie extends Thread {

	private Main main;
	private Gate gate;
	private boolean running;
	private boolean done;
	private boolean animatie;
	private int interval;

	public Animatie(Main main, Gate gate) {
		this.main = main;
		this.gate = gate;
		this.running = true;
		this.done = true;
	}

	/**
	 * Stops the timer from running
	 */
	public void stopTimer() {
		this.running = false;
	}

	/**
	 * returns current interval
	 * 
	 * @return
	 */
	public int getInterval() {
		return interval;
	}

	/**
	 * Sets the interval witch the timer uses in ms
	 * 
	 * @param interval
	 */
	public void setInterval(int interval) {
		this.interval = interval;
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
				e.printStackTrace();
			}
		} else {
			// eventueele clearing van variabele
		}
	}

}
