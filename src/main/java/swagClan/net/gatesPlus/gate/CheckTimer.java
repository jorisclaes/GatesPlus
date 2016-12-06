package swagClan.net.gatesPlus.gate;

import java.awt.List;
import java.util.ArrayList;

import net.minecraft.block.Block;
import swagClan.net.gatesPlus.Main;

public class CheckTimer extends Thread {

	static int check_interval = 200;

	private Main main;
	private ArrayList poweredBloks;
	private Gate gate;
	private boolean running;

	public CheckTimer(Main main, Gate gate) {
		this.main = main;
		this.gate = gate;
		this.running = true;
	}

	/**
	 * 
	 * @return check time in ms
	 */
	public static int getCheck_interval() {
		return check_interval;
	}

	/**
	 * set the check interval in ms
	 * 
	 * @param check_interval
	 *            int
	 */
	public static void setCheck_interval(int check_interval) {
		CheckTimer.check_interval = check_interval;
	}

	/**
	 * this stops the timer from running
	 */
	public void stopTimer() {
		this.running = false;
	}

	@Override
	public void run() {
		boolean nietsgevonden = true;
		for (Object o : poweredBloks) {
			if (o instanceof Block) {
				Block block = ((Block) o);
				// TODO: if block is powered
				gate.setOpengate(true);
				nietsgevonden = false;
				break;
			}
		}
		if (nietsgevonden) {
			gate.setOpengate(false);
		}

		if (running) {
			try {
				this.sleep(check_interval);
				this.start();
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		} else {
			// eventuele clearing variabele
		}
	}
}
