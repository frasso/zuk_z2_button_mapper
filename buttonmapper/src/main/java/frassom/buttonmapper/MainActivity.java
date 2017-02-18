package frassom.buttonmapper;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.PreferenceFragment;
import android.preference.PreferenceManager;
import android.util.SparseArray;
import android.view.View;
import android.widget.Button;
import android.widget.ListView;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Properties;

/**
 * La gestione della rotazione dello schermo non è necessaria
 * però per evitare di dover ricreare sempre tutto da capo è utile
 * per le performance*/
public class MainActivity extends Activity {

	MyPreferenceFragment mPreferenceFragment;

	@Override
	protected void onCreate(final Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		//per evitare che l'onCreate del fragment venga richiamato molteplici volte alla rotazione dello schermo
		if (savedInstanceState == null){
			mPreferenceFragment = new MyPreferenceFragment();
			getFragmentManager().beginTransaction().replace(android.R.id.content, mPreferenceFragment, "fragmentPref").commit();
		}else{
			mPreferenceFragment = (MyPreferenceFragment) getFragmentManager().findFragmentByTag("fragmentPref");
		}
	}

	public static class MyPreferenceFragment extends PreferenceFragment
			implements SharedPreferences.OnSharedPreferenceChangeListener {
		@SuppressWarnings("unused")
		private static final String TAG = "PreferenceFragment";

		//KEY per il salvataggio nel OnSaveInstanceState()
		private static final String KEY_SCANCODES = "scancode";
		private static final String KEY_VALUES = "values";
		private static final String KEY_MAINKEYS = "mainkeys";
		
		//SparseArray per il salvataggio di tutti i tasti
		SparseArray<String> mKeylayout = new SparseArray<>();

		//Properties per il salvataggio del build.prop
		Properties mProperties = new Properties();
		boolean hasMainkeysInFile = true;

		//Button 'applica modifiche'
		Button applyButton;

		Helper helper;

		Context mContext;

		@Override
		public void onCreate(final Bundle savedInstanceState) {
			super.onCreate(savedInstanceState);

			helper = new Helper(mContext);

			if(helper.isRooted()) {
				//se l'app viene aperta per la prima volta crea tutto da zero
				// portando i file in system nella cartella locale
				if (savedInstanceState == null) {

					//copio i keylayout e il build.prop da /system...
					helper.pull();

					//aggiungo i pair dei *.kl allo SparseArray
					helper.addToMap(mKeylayout, Helper.PRESS_KEYLAYOUT_NAME);
					helper.addToMap(mKeylayout, Helper.SOFT_TOUCH_KEYLAYOUT_NAME);
				} else {
					//restore dei valori precedenti se è presente un savedInstanceState
					int[] scancodes = savedInstanceState.getIntArray(KEY_SCANCODES);
					String[] val = savedInstanceState.getStringArray(KEY_VALUES);

					//popolo gli sparse array
					if (scancodes != null && val != null) {
						for (int i = 0; i < scancodes.length; i++) {
							mKeylayout.put(scancodes[i], val[i]);
						}
					}
				}

				//creo il Properties dal file locale (sempre valido)
				helper.addBuildProperties(mProperties);
				hasMainkeysInFile = !mProperties.getProperty(Helper.MAINKEYS, "null").equals("null");
				if(hasMainkeysInFile) {
					//se c'è un precedente stato salvato correggo la key Helper.MAINKEYS
					if (savedInstanceState != null)
						mProperties.setProperty(Helper.MAINKEYS,
								savedInstanceState.getString(KEY_MAINKEYS, mProperties.getProperty(Helper.MAINKEYS, "1")));
				}

				//inizializzo i valori delle sharedPreference per mostrarli successivamente
				SharedPreferences.Editor editor = PreferenceManager.getDefaultSharedPreferences(getContext()).edit().
						putString("press", mKeylayout.get(102, Helper.DISABLE)).
						putString("touch", mKeylayout.get(158, Helper.DISABLE)).
						putString("long_touch", mKeylayout.get(183, Helper.DISABLE)).
						putString("swipe_right", mKeylayout.get(249, Helper.DISABLE)).
						putString("swipe_left", mKeylayout.get(254, Helper.DISABLE));
				if(hasMainkeysInFile)
					editor.putBoolean("softkeys", mProperties.getProperty(Helper.MAINKEYS, "1").equals("0"));
				editor.apply();

				//mostro il preferenceScreen
				setPreferenceScreen(null);
				addPreferencesFromResource(R.xml.preferences);

				if(!hasMainkeysInFile)
					getPreferenceScreen().removePreference(getPreferenceManager().findPreference("softkeys"));
			}else{
				//l'app non viene eseguita come root, mostro il dialog per informare l'utente

				new AlertDialog.Builder(mContext)
						.setCancelable(false)
						.setTitle(R.string.root_dialog_title)
						.setMessage(R.string.root_dialog_message)
						.setPositiveButton(R.string.close, new DialogInterface.OnClickListener() {
							@Override
							public void onClick(DialogInterface dialog, int which) {
								((Activity) mContext).finish();
							}
						})
						.create()
						.show();
			}
		}

		@Override
		public void onPause() {
			super.onPause();
			getPreferenceManager().getSharedPreferences().unregisterOnSharedPreferenceChangeListener(this);
		}

		@Override
		public void onResume() {
			super.onResume();
			getPreferenceManager().getSharedPreferences().registerOnSharedPreferenceChangeListener(this);
		}

		@Override
		public void onAttach(Context context) {
			super.onAttach(context);
			mContext = context;
		}

		@Override
		public void onDetach() {
			super.onDetach();
			mContext = null;
		}

		@Override
		public void onActivityCreated(Bundle savedInstanceState) {
			super.onActivityCreated(savedInstanceState);

			// elimino i divisori tra gli item e assegno l'applyButton
			View rootView = getView();
			if (rootView != null) {
				ListView list = (ListView) rootView.findViewById(android.R.id.list);
				list.setDivider(null);
				@SuppressLint("InflateParams")
				View buttonLayout = ((Activity)mContext).getLayoutInflater().inflate(R.layout.apply_button, null);
				applyButton = (Button) buttonLayout.findViewById(R.id.apply);
				list.addFooterView(buttonLayout);
			}
		}

		@Override
		public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {

			//associo la key nelle preference allo scancode del keylayout
			//(guarda la classe Helper per le associazioni)
			int finalKey = -1;
			switch (key) {
				case "press": finalKey = 102;break;
				case "touch": finalKey = 158;break;
				case "long_touch": finalKey = 183;break;
				case "swipe_right": finalKey = 249;break;
				case "swipe_left": finalKey = 254;break;
				case "softkeys":
					mProperties.put(Helper.MAINKEYS, sharedPreferences.getBoolean(key,false)?"0":"1");
			}
			if(finalKey != -1)
				mKeylayout.put(finalKey, sharedPreferences.getString(key, Helper.DISABLE));
		}

		//AsyncTask usato per applicare le modifiche ai keylayout e
		//del build.prop in /system alla pressione dell'applyButton
		public class commitTask extends AsyncTask<Void,Void,Void> {

			@Override
			protected Void doInBackground(Void... params) {
				StringBuilder sb = new StringBuilder();

				//fpc1020tp.kl
				for (int i = 0; i < mKeylayout.size(); i++) {
					int key = mKeylayout.keyAt(i);

					if (key != 102 && key != -2) {
						if (mKeylayout.get(key).equalsIgnoreCase(Helper.DISABLE))
							sb.append('#');
						sb.append("key ")
								.append(key)
								.append("   ")
								.append(mKeylayout.get(key));
						if (key == 158 || key == 183) {
							for (int j = 0; j < 25 - (10 + mKeylayout.get(key).length()); j++) {
								sb.append(' ');
							}
							sb.append("VIRTUAL");
						}
						sb.append('\n');
					}
				}
				helper.makeFile(sb.toString(), Helper.SOFT_TOUCH_KEYLAYOUT_NAME);

				sb.setLength(0);

				//gpio-mKeylayout.kl
				sb.append("key 115   VOLUME_UP")
						.append('\n')
						.append("key 114   VOLUME_DOWN")
						.append('\n')
						.append("key 528   FOCUS")
						.append('\n')
						.append("key 766   CAMERA");
				if (!mKeylayout.get(102, "none").equalsIgnoreCase("none")) {
					if (!mKeylayout.get(102).equalsIgnoreCase(Helper.DISABLE))
						sb.append("\nkey ")
								.append(102)
								.append("   ")
								.append(mKeylayout.get(102));
				}
				helper.makeFile(sb.toString(), Helper.PRESS_KEYLAYOUT_NAME);

				//build.brop
				if(hasMainkeysInFile){
					String appFilesDir = getContext().getFilesDir().getPath();

					try {
						FileOutputStream os = new FileOutputStream(new File(appFilesDir, "build.prop"));
						mProperties.store(os, null);
						os.close();
					} catch (IOException e) {
						e.printStackTrace();
					}
					helper.setPermission(appFilesDir + "/build.prop", "600");
				}

				helper.push();
				return null;
			}

			@Override
			protected void onPostExecute(Void aVoid) {
				super.onPostExecute(aVoid);

				//mostro il dialog per la richiesta di riavvio
				new AlertDialog.Builder(getContext()).setTitle(R.string.reboot_dialog_title)
						.setCancelable(false)
						.setMessage(R.string.reboot_dialog_message)
						.setPositiveButton(R.string.reboot, new DialogInterface.OnClickListener() {
							@Override
							public void onClick(DialogInterface dialog, int which) {
								try {
									Runtime.getRuntime().exec("su -c reboot");
								} catch (IOException e) {
									e.printStackTrace();
								}
							}
						})
						.setNegativeButton(R.string.do_not_reboot, null)
						.create().show();
			}
		}

		@Override
		public void onSaveInstanceState(Bundle outState) {
			super.onSaveInstanceState(outState);
			int size = mKeylayout.size();
			int[] scancodes = new int[size];
			String[] val = new String[size];

			for(int i=0; i<size; i++){
				scancodes[i] = mKeylayout.keyAt(i);
				val[i] = mKeylayout.get(scancodes[i]);
			}
			outState.putStringArray(KEY_VALUES, val);
			outState.putIntArray(KEY_SCANCODES, scancodes);
			outState.putString(KEY_MAINKEYS, mProperties.getProperty(Helper.MAINKEYS, "1"));
		}
	}

	//click dell'applyButton
	public void buttonClick(View v){
		mPreferenceFragment.new commitTask().execute();
	}
}
