package net.trurlo.PimSRS;

import android.preference.PreferenceActivity;
import android.preference.PreferenceManager;
import android.content.Context;
import android.os.Bundle;

public class Prefs extends PreferenceActivity {
	@Override
	protected void onCreate(Bundle savedInstanceState){
		super.onCreate(savedInstanceState);
		addPreferencesFromResource(R.xml.settings);
	}
	public static boolean getUseZeemote(Context context){
		return PreferenceManager.getDefaultSharedPreferences(context).getBoolean("use_zeemote", false);
	}
	public static boolean getAutoadvance(Context context){
		return PreferenceManager.getDefaultSharedPreferences(context).getBoolean("autoadvance", false);
	}
	public static boolean getReadRecent(Context context){
		return PreferenceManager.getDefaultSharedPreferences(context).getBoolean("read_recent", false);
	}
	public static boolean getConfirmPair(Context context){
		return PreferenceManager.getDefaultSharedPreferences(context).getBoolean("confirm_pair", false);
	}
	public static boolean getAutopauseWhenAdding(Context context){
		return PreferenceManager.getDefaultSharedPreferences(context).getBoolean("autopause_when_adding", false);
	}
} 
