package swagClan.net.gatesPlus.bestandHandler;

import java.util.Scanner;

import swagClan.net.gatesPlus.Main;

public class Bestandslezer {
	private Scanner scan;
	private String path;
	private Main main;
	
	public Bestandslezer(Main main, String path){
		this.main = main;
		this.path = path;
	}
	
	/**
	 * Opens the date reader stream from file
	 */
	public void openStream(){
		scan = new Scanner(path);
	}
	
	/**
	 * closes the data reader stream from file
	 */
	public void closeStreem(){
		scan.close();
	}
}
