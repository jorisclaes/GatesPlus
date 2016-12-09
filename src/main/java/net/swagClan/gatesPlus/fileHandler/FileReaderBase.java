package net.swagClan.gatesPlus.fileHandler;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.Scanner;

import net.swagClan.gatesPlus.Main;

public class FileReaderBase {
	private Scanner scan;
	private Main main;

	private static boolean usingDB;
	private static String path;

	public FileReaderBase(Main main) {
		this.main = main;
		this.path = "config/" + Main.MODID + "/";
		;
	}

	/**
	 * Opens the date reader stream from file
	 */
	public void openStream() {
		scan = new Scanner(path);
	}

	/**
	 * closes the data reader stream from file
	 */
	public void closeStreem() {
		scan.close();
	}

	/**
	 * return the base path
	 * 
	 * @return
	 */
	public static String getBasePath() {
		return path;
	}

	/**
	 * returns true is there is a DB
	 * 
	 * @return
	 */
	public static boolean isUsingDB() {
		return usingDB;
	}

	/**
	 * sets true if there is a DB false for file based DB
	 * 
	 * @param usingDB
	 */
	public static void setUsingDB(boolean usingDB) {
		FileReaderBase.usingDB = usingDB;
	}

	/**
	 * checks if the file exits
	 * 
	 * @param fileName
	 * @return
	 */
	public boolean isFileExisting(String fileName) {
		File file = new File(path + fileName);
		if (file.exists() && !file.isDirectory()) {
			return true;
		}
		return false;
	}

	/**
	 * Creates the file and saves the content
	 * 
	 * @param fileName
	 * @param content
	 * @return null when non successful
	 */
	public File saveFile(String fileName, String content) {
		try {
			File f = new File(path + fileName);
			File dir = new File(path);
			if (!dir.exists()) {
				dir.mkdirs();
			}
			Writer w = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(f)));
			w.write(content);
			w.close();
			return f;
		} catch (IOException ex) {
			ex.printStackTrace();
		}
		return null;
	}

	/**
	 * Creates the file directory if it does not exist
	 */
	private void createDirr() {
		File f = new File(path);
		if (f.exists()) {
			return;
		}
		f.mkdirs();
	}
}
