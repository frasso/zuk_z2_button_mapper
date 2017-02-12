package frassom.buttonmapper;

import android.content.Context;
import android.util.SparseArray;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.Nullable;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.NoSuchElementException;
import java.util.Properties;
import java.util.Scanner;

import static android.content.Context.MODE_PRIVATE;

/**
 *	----SCANCODES----
 * scancode 102	//press
 * scancode 158	//touch
 * scancode 183	//long-touch
 * scancode 249	//swipe right
 * scancode 254	//swipe left
 */
class Helper {

	private final static String KEYLAYOUT_PATH = "/system/usr/keylayout/";
	final static String SOFT_TOUCH_KEYLAYOUT_NAME = "fpc1020tp.kl";
	final static String PRESS_KEYLAYOUT_NAME = "gpio-keys.kl";
	private final static String SOFT_TOUCH_KEYLAYOUT_PATH = KEYLAYOUT_PATH + SOFT_TOUCH_KEYLAYOUT_NAME;
	private final static String PRESS_KEYLAYOUT_PATH = KEYLAYOUT_PATH + PRESS_KEYLAYOUT_NAME;

	//keylayout disabled key
	final static String DISABLE = "null";
	final static String MAINKEYS = "qemu.hw.mainkeys";

	private Context ctx;
	private Process su;
	private DataOutputStream shellInput;
	private Scanner shellOutput;
	private boolean rooted;
	private String appFileDir;

	Helper(Context ctx){
		this.ctx = ctx;
		su = null;
		this.appFileDir = ctx.getFilesDir().getPath();

		rooted = checkRoot();

		//creo i file iniziali per potervi accedere successivamente, controllo che non esistano di già
		File propFile = new File(appFileDir+"/build.prop");
		if(!propFile.exists()) {
			try {
				FileOutputStream fos = ctx.openFileOutput(Helper.PRESS_KEYLAYOUT_NAME, MODE_PRIVATE);
				fos.close();
				fos = ctx.openFileOutput(Helper.SOFT_TOUCH_KEYLAYOUT_NAME, MODE_PRIVATE);
				fos.close();
				fos = ctx.openFileOutput("build.prop", MODE_PRIVATE);
				fos.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}

	/** Controlla che si abbia l'accesso root */
	private boolean checkRoot() {
		String id = null;
		setup();

		if(su != null) {
			try {
				shellInput.writeBytes("id\n");
				shellInput.flush();
				id = shellOutput.nextLine();
			} catch (IOException | NoSuchElementException e) {
				e.printStackTrace();
			} finally {
				close();
			}

			return !(id == null || !id.contains("root"));	//id contains root

		} else
			return false;	//su process == null

	}

	/**
	 * Crea il file shellOutput filePath e assegna i permessi a rw-r--r--*/
	void makeFile(String fileContent, String fileName){
		String filePath = appFileDir + "/" + fileName;
		//creo un file da sostituire successivamente al file shellOutput /system
		try {
			FileOutputStream fos;
			fos = new FileOutputStream(filePath);
			byte[] buffer = fileContent.getBytes();
			fos.write(buffer, 0, buffer.length);
			fos.close();
		} catch (IOException e) {
			e.printStackTrace();
		}

		//setto i permessi rw-r--r-- (0644)
		setPermission(filePath, "644");
	}

	void setPermission(String filePath, String permissions){
		if(!rooted)
			return;

		setup();
		try {
			shellInput.writeBytes("chmod " + permissions + " " + filePath + "\n");
			shellInput.writeBytes("exit\n");
			shellInput.flush();
			su.waitFor();
		} catch (IOException | InterruptedException e) {
			e.printStackTrace();
		}finally {
			close();
		}
	}

	/**
	 * Copia i file di mapping nella directory passata per la modifica,
	 * viene montato /system shellOutput automatico (richiede accesso su)
	 * */
	void pull(){
		if(!rooted)
			return;

		setup();
		try {
			//monto system r/w
			shellInput.writeBytes("mount -o rw,remount -t ext4 /dev/block/bootdevice/by-name/system /system\n");

			//copio i file
			shellInput.writeBytes("cp " + SOFT_TOUCH_KEYLAYOUT_PATH + " " + appFileDir + "\n");
			shellInput.writeBytes("cp " + PRESS_KEYLAYOUT_PATH + " " + appFileDir + "\n");

			shellInput.writeBytes("cp /system/build.prop " + appFileDir + "\n");
			shellInput.writeBytes("chmod 777 "+ appFileDir +"/build.prop\n");

			//smonto /system r/o
			shellInput.writeBytes("mount -o ro,remount -t ext4 /dev/block/bootdevice/by-name/system /system\n");

			shellInput.writeBytes("exit\n");
			shellInput.flush();

			su.waitFor();
		}catch(IOException | InterruptedException e){
			e.printStackTrace();
		}finally {
			close();
		}
	}

	/**
	 * Copia i file dalla directory passata a /system,
	 * viene montato /system shellOutput automatico (richiede accesso su)
	 * */
	void push(){
		if(!rooted)
			return;

		setup();
		try {
			//monto system r/w
			shellInput.writeBytes("mount -o rw,remount -t ext4 /dev/block/bootdevice/by-name/system /system\n");

			//copio il file
			shellInput.writeBytes("cp " + appFileDir +"/"+SOFT_TOUCH_KEYLAYOUT_NAME + " " + KEYLAYOUT_PATH + "\n");
			shellInput.writeBytes("cp " + appFileDir +"/"+PRESS_KEYLAYOUT_NAME + " " + KEYLAYOUT_PATH + "\n");
			shellInput.writeBytes("cp " + appFileDir +"/build.prop /system/build.prop\n");

			//monto /system r/o
			shellInput.writeBytes("mount -o ro,remount -t ext4 /dev/block/bootdevice/by-name/system /system\n");

			shellInput.writeBytes("exit\n");
			shellInput.flush();

			su.waitFor();
		}catch(IOException | InterruptedException e){
			e.printStackTrace();
		}finally {
			close();
		}
	}

	/**
	 * Aggiunge gli scancode prestabiliti insieme al keycode allo sparsearray passato*/
	void addToMap(SparseArray<String> mProp, String fileName){

		try {
			BufferedReader br = new BufferedReader(new InputStreamReader(ctx.openFileInput(fileName)));
			String line;

			while ((line = br.readLine()) != null) {
				if(line.length() > 0 && line.charAt(0) == 'k') {
					int scanstart = -1;
					int keystart = -1;
					int keyend = -1;
					for (int i = 0; i < line.length(); i++){
						if (Character.isDigit(line.charAt(i)) && scanstart==-1)
							scanstart = i;
						if (line.charAt(i) >= 'A' && line.charAt(i) <= 'Z' && keystart==-1)
							keystart = i;
						if(keystart!=-1 && (line.charAt(i) == ' ')){
							keyend = i;
							break;
						}
					}
					int scanCode = Integer.parseInt(line.substring(scanstart,scanstart+3));
					if(keyend==-1)
						keyend = line.length();
					String keyCode = line.substring(keystart,keyend);
					if(isScancode(scanCode))
						mProp.put(scanCode,keyCode);
				}
			}
			br.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	/**
	 * Aggiunge al build.prop shellOutput appFilesDir un record relativo al qemu.hw.mainkeys*/
	void addBuildProperties(Properties properties) {
		try {
			BufferedReader br = new BufferedReader(new InputStreamReader(ctx.openFileInput("build.prop")));
			properties.load(br);
			br.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private void setup(){
		try {
			su = Runtime.getRuntime().exec("su");
		} catch (IOException e) {
			e.printStackTrace();
		}

		//init e root detect
		if(su != null){
			shellInput = new DataOutputStream(su.getOutputStream());
			shellOutput = new Scanner(su.getInputStream());
		}
	}

	private void close(){
		if(su != null)
			su.destroy();

		if (shellOutput != null)
		shellOutput.close();

		try {
			if(shellInput != null)
				shellInput.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	@Contract(pure = true)
	private static boolean isScancode(int scancode){
		return scancode == 102 ||	//press
				scancode == 158 ||	//touch
				scancode == 183 ||	//long-touch
				scancode == 249 ||	//swipe right
				scancode == 254;	//swipe left
	}

	boolean isRooted() {
		return rooted;
	}
}
