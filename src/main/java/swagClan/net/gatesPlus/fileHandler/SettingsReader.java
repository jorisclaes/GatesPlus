package swagClan.net.gatesPlus.fileHandler;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.Scanner;

import swagClan.net.gatesPlus.Main;
import swagClan.net.gatesPlus.db.SQL;

public class SettingsReader extends FileReaderBase{
	
	private static String fileName = "GatesPlus.settings";

	public SettingsReader(Main main){
		super(main);
		readFile();
	}
	
	/**
	 * Reads the file if the file does not exists it will create it
	 */
	public void readFile() {
		if(super.isFileExisting(fileName)){
			//file bestaat
			System.out.println("read file");
			read();
		}else{
			//file bestaat niet
			System.out.println("create file");
			createFile();
		}
	}
	
	/**
	 * Creates the file
	 */
	private void createFile(){
		String content = 
				"GebruikDB: 'false'\n"+
						"\tURL: ''\n"+
						"\tuserName: ''\n"+
						"\tpass: ''\n"+
				"";
		super.saveFile(fileName, content);
		super.setUsingDB(false);
		//TODO: maak de sql little juist / init
	}
	
	/**
	 * Reads the file
	 */
	private void read(){
		try {
			Scanner scan = new Scanner(new File(super.getBasePath()+fileName));
			while(scan.hasNext()){
				String scaned = scan.nextLine();
				if(scaned.contains("GebruikDB")){
					String[] args = scaned.split(":");
					if(args[1].contains("true")){
						super.setUsingDB(true);
					}else{
						super.setUsingDB(false);
					}
				}
				if(scaned.contains("URL")){
					String[] args = scaned.split(":");
					args[1] = args[1].substring(args[1].indexOf("'")+1);
					args[1] = args[1].substring(0, args[1].indexOf("'"));
					System.out.println("URL = " + args[1]);
					SQL.url = args[1];
				}
				if(scaned.contains("userName")){
					String[] args = scaned.split(":");
					args[1] = args[1].substring(args[1].indexOf("'")+1);
					args[1] = args[1].substring(0, args[1].indexOf("'"));
					System.out.println("username = " + args[1]);
					SQL.username = args[1];
				}
				if(scaned.contains("pass")){
					String[] args = scaned.split(":");
					args[1] = args[1].substring(args[1].indexOf("'")+1);
					args[1] = args[1].substring(0, args[1].indexOf("'"));
					SQL.passwoord = args[1];
					System.out.println("passwoord = " + args[1]);
				}
			}
			scan.close();
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		}
	}
}
