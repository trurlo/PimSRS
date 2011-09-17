package net.trurlo.PimSRS;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.TreeSet;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.os.PowerManager;
import android.speech.tts.TextToSpeech;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

//import com.zeemote.zc.Configuration;
import com.zeemote.zc.Controller;
import com.zeemote.zc.event.BatteryEvent;
import com.zeemote.zc.event.ButtonEvent;
import com.zeemote.zc.event.ControllerEvent;
import com.zeemote.zc.event.DisconnectEvent;
import com.zeemote.zc.event.IButtonListener;
import com.zeemote.zc.event.IJoystickListener;
import com.zeemote.zc.event.IStatusListener;
import com.zeemote.zc.event.JoystickEvent;
import com.zeemote.zc.ui.android.ControllerAndroidUi;


public class PimSRS extends Activity implements OnClickListener,IStatusListener, IJoystickListener, IButtonListener  {
    /** Called when the activity is first created. */
	private static final String TAG = "PimSRS";
	private TextView txtCueFile;
	private TextView txtAnkiFile;
	private TextView txtAudioFile;
	private TextView txtSilent;
	private TextView txtNoOfLines;
	private TextView txtCurrentLine;
	private TextView txtTimestamp;
	private ArrayList<String> fileList = new ArrayList<String>();
	private ArrayList<String> fullFileList = new ArrayList<String>();
	private ProgressDialog pDialog;
	private ArrayList<PimLine> pimLines = new ArrayList<PimLine>();
    AlertDialog.Builder ab;
    private int currentPimLineIndex = 0;
    private String currentCueFileStub;
    private String currentFullCueFileStub;
    private Button btnPrev;
    private Button btnPlay;
    private Button btnNext;
    private Button btnAutoplay;
    private Button btnAddPair;
    private Button btnTest;
    private MediaPlayer mp;
	private Controller controller;
	private ControllerAndroidUi controllerUi;
	public static final int MSG_ZEEMOTE = 0x101;
	public static final int MSG_ZEEMOTE_BUTTON_A = 0x102;
	public static final int MSG_ZEEMOTE_BUTTON_B = 0x103;
	public static final int MSG_ZEEMOTE_BUTTON_C = 0x104;
	public static final int MSG_ZEEMOTE_BUTTON_D = 0x105;
	public static final int MSG_ZEEMOTE_STATUS = 0x106;
	public static final int MSG_ZEEMOTE_JOYSTICK = 0x107;
	public static final int MSG_AUTOPLAY = 0x108;
	private String info;
	private TextToSpeech mTts;
	private boolean autoplay = false;
	private String ankiFileName = "";
	private boolean cueFileRead = false;
	private boolean ankiFileRead = false;
	private SQLiteDatabase ankiDB;
    private static TreeSet<Long> sIdTree;
    private static long sIdTime;
	private PowerManager.WakeLock wakeLock;

	Handler ZeemoteHandler = new Handler() {
		
		public void handleMessage(Message msg){
			switch(msg.what){
			case MSG_ZEEMOTE:
				txtCueFile.setText(info);
				break;
			case MSG_ZEEMOTE_BUTTON_A:
				plPlayNextNonSilent();
				break;
			case MSG_ZEEMOTE_BUTTON_B:
				plPlayPrevNonSilent();
				break;
			case MSG_ZEEMOTE_BUTTON_C:
				toggleAutoPlay();
				break;
			case MSG_ZEEMOTE_BUTTON_D:
				addCurrentPair();
				break;
			case MSG_AUTOPLAY:
				continueAutoplay();
				break;
			}
			super.handleMessage(msg);
		}
	};
	
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
        txtAudioFile = (TextView) findViewById(R.id.audio_file);
        txtCueFile = (TextView) findViewById(R.id.cue_file);
        txtAnkiFile = (TextView) findViewById(R.id.anki_file);
        txtCurrentLine = (TextView) findViewById(R.id.current_line);
        txtNoOfLines = (TextView) findViewById(R.id.no_of_lines);
        txtSilent = (TextView) findViewById(R.id.silent);
        txtTimestamp = (TextView) findViewById(R.id.timestamp);
        btnPrev = (Button) findViewById(R.id.btn_prev);
        btnPrev.setOnClickListener(this);
        btnPlay = (Button) findViewById(R.id.btn_play);
        btnPlay.setOnClickListener(this);
        btnNext = (Button) findViewById(R.id.btn_next);
        btnNext.setOnClickListener(this);
        btnAutoplay = (Button) findViewById(R.id.btn_autoplay);
        btnAutoplay.setOnClickListener(this);
        btnAddPair = (Button) findViewById(R.id.btn_add_pair);
        btnAddPair.setOnClickListener(this);
        btnTest = (Button) findViewById(R.id.btn_test);
        btnTest.setOnClickListener(this);
        
    	ab = new AlertDialog.Builder(this);   	  
    	pDialog = new ProgressDialog(this);
    	
    	setVolumeControlStream(AudioManager.STREAM_MUSIC);
    	
		if (Prefs.getUseZeemote(this)){
    	 controller = new Controller(Controller.CONTROLLER_1);       
		 controller.addStatusListener(this);
		 controller.addButtonListener(this);
		 controller.addJoystickListener(this);
		 controllerUi = new ControllerAndroidUi(this, controller);
  		 controllerUi.startConnectionProcess();
		}
		
		if (Prefs.getReadRecent(this)){
		SharedPreferences preferences = getPreferences(MODE_PRIVATE);
		readCueFile(preferences.getString("CueFileStub", ""));
		currentPimLineIndex = preferences.getInt("CurrentCueIndex", -1);
		if (currentPimLineIndex>-1) displayCurrentPimLine();
		readAnkiFile(preferences.getString("AnkiFile", ""));
		}
		
		mTts = new TextToSpeech(this, null);
    }
    
    protected void continueAutoplay() {
    	if (autoplay) {
    		playCurrent();
    		int curtime = pimLines.get(currentPimLineIndex).timestamp;
    		if (plNextNonSilent()) {
    		Message msg = new Message();
    		msg.what = MSG_AUTOPLAY;
    		ZeemoteHandler.sendMessageDelayed(msg, pimLines.get(currentPimLineIndex).timestamp-curtime);
    		} else autoplay = false;
    	}
	}
   
	protected void toggleAutoPlay() {
    	if (autoplay) {
    		autoplay = false;
    		if (mp != null) mp.release();
    		btnAutoplay.setText("Auto play");
    	}
    	else {
        		autoplay = true;
        		btnAutoplay.setText("Auto pause");
        		continueAutoplay();
    	}
	}
    
	private boolean copyFile(String src, String dst){
		Log.d("PimSRS","copying file");
		File srcFile = new File(src);
		Log.d("PimSRS"," src: "+src);
		File dstFile = new File(dst);
		Log.d("PimSRS"," dst: "+dst);
		if (srcFile.exists()){
          try {
    	   Log.d("PimSRS"," src exists, copying");
		   FileChannel srcChannel = new FileInputStream(srcFile).getChannel();
           FileChannel dstChannel = new FileOutputStream(dstFile).getChannel();
           dstChannel.transferFrom(srcChannel, 0, srcChannel.size());
           srcChannel.close();
           dstChannel.close();
   	       return true;
          }
          catch (Exception e){
      		Log.d("PimSRS"," copying error: "+e.getMessage());
        	  return false;
          }
		}
		return false;
	}
	
	private void addCurrentPair() {
		if (currentPimLineIndex < 1){
			Toast.makeText(getApplicationContext(), "This line cannot be an answer", Toast.LENGTH_SHORT).show();
			return;
		}
		if ((autoplay)&&(Prefs.getAutopauseWhenAdding(this))) toggleAutoPlay();
		if (Prefs.getUseZeemote(this)) mTts.speak("Adding pair", TextToSpeech.QUEUE_FLUSH, null);

		//copy files to media folder
		//check if folder exists, create it otherwise
		File mediaFolder = new File(ankiFileName.replace(".anki", ".media"));
		if (!mediaFolder.exists()) mediaFolder.mkdir();
		String strQ = String.format("[sound:%s_%03d.mp3]",currentCueFileStub,pimLines.get(currentPimLineIndex-1).index);
		String strA = String.format("[sound:%s_%03d.mp3]",currentCueFileStub,pimLines.get(currentPimLineIndex).index);
		String srcFileQ = String.format("%s_%03d.mp3",currentFullCueFileStub,pimLines.get(currentPimLineIndex-1).index);
		String srcFileA = String.format("%s_%03d.mp3",currentFullCueFileStub,pimLines.get(currentPimLineIndex).index);
		String dstFileQ = String.format("%s/%s_%03d.mp3",mediaFolder.getPath(),currentCueFileStub,pimLines.get(currentPimLineIndex-1).index);
		String dstFileA = String.format("%s/%s_%03d.mp3",mediaFolder.getPath(),currentCueFileStub,pimLines.get(currentPimLineIndex).index);
		String strNow = Double.toString(System.currentTimeMillis()/1000.0);

		copyFile(srcFileQ,dstFileQ);
		copyFile(srcFileA,dstFileA);
		
		//SQLite stuff
		if (ankiDB.isOpen()) {
		 ankiDB.beginTransaction();
		 Cursor cursor = null;
		 try
		 {
			 //get some initial values;

			 //Tag
			 long _tagId = -1;
			 cursor = ankiDB.rawQuery("select id from tags where tag = \""+currentCueFileStub+"\"", null);
			 if (cursor.moveToFirst()) {
				 _tagId = cursor.getLong(0);
				 Log.d(TAG,"tag: "+currentCueFileStub+" found: "+_tagId);
			 } else {
				 Log.d(TAG,"tag: "+currentCueFileStub+" not found. adding");
				 //getting max tagID
				 cursor = ankiDB.rawQuery("select max(id)+1 from tags", null);
				 if (cursor.moveToFirst()) {
					 _tagId = cursor.getLong(0);
					 Log.d(TAG,"next tagID: "+_tagId);
					 ankiDB.execSQL("insert into tags(id,tag,priority) values("+_tagId+", \""+currentCueFileStub+"\",2)");
					 Log.d(TAG,"tag added");
				 }
				 
			 }
			 //_maxCardId
			 long _maxCardId = -1;
			 cursor = ankiDB.rawQuery("select max(id) from cards", null);
			 if (cursor.moveToFirst()) {
				 _maxCardId = cursor.getLong(0);
				 Log.d(TAG,"_maxCardId: "+_maxCardId);
			 	}
			 else {
				 Log.d(TAG,"_maxCardId not found.");
			 }
			 //_modelId
			 long _modelId = -1;
			 cursor = ankiDB.rawQuery("select id from models where name = \"Basic\" and deckId = 1", null);
			 if (cursor.moveToFirst()) {
				 _modelId = cursor.getLong(0);
				 Log.d(TAG,"_modelId: "+_modelId);
			 	}
			 else {
				 Log.d(TAG,"_modelId not found.");
			 }
			 
			 //_cardModelId
			 long _cardModelId = -1;
			 cursor = ankiDB.rawQuery("select id from cardModels where modelId = "+_modelId+" and active = 1 and name = \"Forward\"", null);
			 if (cursor.moveToFirst()) {
				 _cardModelId = cursor.getLong(0);
				 Log.d(TAG,"_cardModelId: "+_cardModelId);
			 	}
			 else {
				 Log.d(TAG,"_cardModelId not found.");
			 }
			 
			 //_fieldModelIdQ
			 long _fieldModelIdQ = -1;
			 cursor = ankiDB.rawQuery("select id from fieldModels where ordinal = 0 and modelId = "+_modelId, null);
			 if (cursor.moveToFirst()) {
				 _fieldModelIdQ = cursor.getLong(0);
				 Log.d(TAG,"_fieldModelIdQ: "+_fieldModelIdQ);
			 	}
			 else {
				 Log.d(TAG,"_fieldModelIdQ not found.");
			 }
			 
			 //_fieldModelIdA
			 long _fieldModelIdA = -1;
			 cursor = ankiDB.rawQuery("select id from fieldModels where ordinal = 1 and modelId = "+_modelId, null);
			 if (cursor.moveToFirst()) {
				 _fieldModelIdA = cursor.getLong(0);
				 Log.d(TAG,"_fieldModelIdA: "+_fieldModelIdA);
			 	}
			 else {
				 Log.d(TAG,"_fieldModelIdA not found.");
			 }
			 
			 //_maxCardTagID
			 long _maxCardTagID = -1;
			 cursor = ankiDB.rawQuery("select max(id) from cardTags", null);
			 if (cursor.moveToFirst()) {
				 _maxCardTagID = cursor.getLong(0);
				 Log.d(TAG,"_maxCardTagID: "+_maxCardTagID);
			 	}
			 else {
				 Log.d(TAG,"_maxCardTagID not found.");
			 }
			
			 //increment counts in [decks]
			 ankiDB.execSQL("update decks set cardCount = cardCount+1, factCount = factCount+1, newCount = newCount + 1, modified = "+strNow+" where id = 1");
			 
			 //add fact
			 long factId = genId();
			 ContentValues values = new ContentValues();
			 values.put("id", factId);
			 values.put("modelId", _modelId);
			 values.put("created", now());
			 values.put("modified", now());
			 values.put("tags", currentCueFileStub);
			 values.put("spaceUntil", 0);
			 ankiDB.insert("facts", null, values);
			 
			 //add fields
			 long fieldQId = genId();
			 long fieldAId = genId();
			 //question
			 values.clear();
			 values.put("id", fieldQId);
			 values.put("factId", factId);
			 values.put("fieldModelId", _fieldModelIdQ);
			 values.put("ordinal", 0);
			 values.put("value", strQ);
			 ankiDB.insert("fields", null, values);
			 //answer
			 values.clear();
			 values.put("id", fieldAId);
			 values.put("factId", factId);
			 values.put("fieldModelId", _fieldModelIdA);
			 values.put("ordinal", 1);
			 values.put("value", strA);
			 ankiDB.insert("fields", null, values);

			 //add cards
			 values.clear();
			    values.put("id", ++_maxCardId);
		        values.put("factId", factId);
		        values.put("cardModelId", _cardModelId);
		        values.put("created", now());
		        values.put("modified", now());
		        values.put("tags", "");
		        values.put("ordinal", 0);
		        values.put("question", strQ);
		        values.put("answer", strA);
		        values.put("priority", 2);
		        values.put("interval", 0);
		        values.put("lastInterval", 0);
		        values.put("due", now());
		        values.put("lastDue", 0);
		        values.put("factor", 2.5);
		        values.put("lastFactor", 2.5);
		        values.put("firstAnswered", 0);
		        values.put("reps", 0);
		        values.put("successive", 0);
		        values.put("averageTime", 0);
		        values.put("reviewTime", 0);
		        values.put("youngEase0", 0);
		        values.put("youngEase1", 0);
		        values.put("youngEase2", 0);
		        values.put("youngEase3", 0);
		        values.put("youngEase4", 0);
		        values.put("matureEase0", 0);
		        values.put("matureEase1", 0);
		        values.put("matureEase2", 0);
		        values.put("matureEase3", 0);
		        values.put("matureEase4", 0);
		        values.put("yesCount", 0);
		        values.put("noCount", 0);
		        values.put("spaceUntil", 0);
		        values.put("isDue", 0);
		        values.put("type", 2);
		        values.put("combinedDue", now());
		        values.put("relativeDelay", 0.0);
		        ankiDB.insert("cards", null, values);
			 
		        //card tags
		        //4-1
		        values.clear();
		        values.put("id", ++_maxCardTagID);
		        values.put("cardId", _maxCardId);
		        values.put("tagId", 4);
		        values.put("src", 1);
		        ankiDB.insert("cardTags", null, values);
		        //5-2
		        values.clear();
		        values.put("id", ++_maxCardTagID);
		        values.put("cardId", _maxCardId);
		        values.put("tagId", 5);
		        values.put("src", 2);
		        ankiDB.insert("cardTags", null, values);
		        //my tag
		        values.clear();
		        values.put("id", ++_maxCardTagID);
		        values.put("cardId", _maxCardId);
		        values.put("tagId", _tagId);
		        values.put("src", 0);
		        ankiDB.insert("cardTags", null, values);
			   
			 ankiDB.setTransactionSuccessful();
		 }
		 finally {
			 if (cursor != null) {
				 cursor.close();
			 }
			 ankiDB.endTransaction();
		 }
		 Toast.makeText(getApplicationContext(), "Pair was added", Toast.LENGTH_SHORT).show();
		}
	}

    public static double now() {
        return (System.currentTimeMillis() / 1000.0);
    }
	
    public static long genId() {
        long time = System.currentTimeMillis();
        long id;
        long rand;

        if (sIdTree == null) {
            sIdTree = new TreeSet<Long>();
            sIdTime = time;
        } else if (sIdTime != time) {
            sIdTime = time;
            sIdTree.clear();
        }

        while (true) {
            rand = UUID.randomUUID().getMostSignificantBits();
            if (!sIdTree.contains(new Long(rand))) {
                sIdTree.add(new Long(rand));
                break;
            }
        }
        id = rand << 41 | time;
        return id;
    }

	@Override
    protected void onPause(){
    	super.onPause();
    	savePimState();
    }
    
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.menu, menu);
        return true;
    }    
    
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
        case R.id.mnu_read_cue:
            readCue();
            return true;
        case R.id.mnu_read_anki:
        	readAnki();
            return true;
        case R.id.mnu_settings:
            startActivity(new Intent(this, Prefs.class));
            return true;
        case R.id.mnu_exit:
            exitGracefully();
            return true;
        }
       
        return super.onOptionsItemSelected(item);
    }    
    
    private void savePimState(){
   	    SharedPreferences preferences = getPreferences(MODE_PRIVATE);
    	SharedPreferences.Editor editor = preferences.edit();
    	if (cueFileRead)
    	 editor.putString("CueFileStub", currentFullCueFileStub+".cue");
    	if (ankiFileRead)
    	 editor.putString("AnkiFile", ankiFileName);
    	if (currentPimLineIndex > -1)
     	 editor.putInt("CurrentCueIndex", currentPimLineIndex);
    	editor.commit();    	
    }

    private void exitGracefully(){
		try {
			if (controller.isConnected()) controller.disconnect();
			if ((ankiDB != null)&&(ankiDB.isOpen())) ankiDB.close();
			savePimState();
		} catch (Exception e) {}
		this.finish();
    }
    
    private class SearchForCues extends AsyncTask<Void, Void, Void> {
    	@Override
    	protected Void doInBackground(Void... arg0) {
			  File dirAudioAnki = Environment.getExternalStoragePublicDirectory("AudioAnki");
		      addCuesInDir(dirAudioAnki);
		      return null;
		}
	    protected void onPreExecute(){
	          pDialog.setMessage("Looking for .cues...");
              fileList.clear();
              fullFileList.clear();
	          pDialog.setIndeterminate(true);
	          pDialog.setCancelable(false);
	          pDialog.show();
	    }
		
		protected void onPostExecute(Void arg0) {
	         Log.d(TAG,"fileList count: "+fileList.size());
	         ab.setTitle("Pick a .cue");
	         final String[] fileArray = fileList.toArray(new String[fileList.size()]); 
	         ab.setItems(fileArray, new DialogInterface.OnClickListener() {
				@Override
				public void onClick(DialogInterface dialog, int which) {
					readCueFile(fullFileList.get(which));
				}
			});
	         AlertDialog ad = ab.create();
	         ad.show();
	    	 pDialog.dismiss();
	     }
    
    }
    
    private boolean readCueFile(String filename){
    	if ((filename == null) || (filename == "")||(filename == "null")) return false; 
		currentFullCueFileStub = filename.replace(".cue", "");
		cueFileRead = true;
    	boolean lSilence;
    	int lIndex;
    	int lTimestamp;
    	Pattern pttrnTrack = Pattern.compile("TRACK +([0-9]+) +AUDIO");
    	Pattern pttrnSilence = Pattern.compile("REM +@SELECTED +\"([01])\"");
    	Pattern pttrntimestamp = Pattern.compile("INDEX +01 +([0-9]+)\\:([0-9]+)\\:([0-9]+)");
    	Matcher m;
    	int cnt = 0;
    	txtCueFile.setText(filename);
    	File cuefile = new File(filename);
    	currentCueFileStub = cuefile.getName().replace(".cue", "");
    	try {
    	    BufferedReader br = new BufferedReader(new FileReader(cuefile));
    	    String line;
    	    pimLines.clear();
	    	lSilence = true;
	    	lIndex = -1;
	    	lTimestamp = 0;

    	    while ((line = br.readLine()) != null) {
    	    	m = pttrnTrack.matcher(line);
    	    	if (m.find()){
    	    		lIndex = Integer.parseInt(m.group(1));
    	    		cnt++;
    	    		continue;
    	    	}
    	    	m = pttrnSilence.matcher(line);
    	    	if (m.find()){
    	    		lSilence = m.group(1).equals("0");
    	    		continue;
    	    	}
    	    	m = pttrntimestamp.matcher(line);
    	    	if (m.find()){
    	    		lTimestamp = Integer.parseInt(m.group(1))*60000+
    	    					 Integer.parseInt(m.group(2))*1000+
    	    					 Integer.parseInt(m.group(3))*10;
    	    		if (!lSilence) pimLines.add(new PimLine(lIndex,lSilence,lTimestamp));
    	    	}
    	    }
    	    txtNoOfLines.setText(Integer.toString(cnt));
    	}
    	catch (IOException e) {
    	    pimLines.clear();
    	    currentCueFileStub = "";
    	    currentFullCueFileStub = "";
    	    cueFileRead = false;
    	    return false;
    	}
    	if (pimLines.size()>0) {
    		currentPimLineIndex=0;
    		displayCurrentPimLine();
    	}
    	updateEnableds();
    	return true;
    }
    
    private void addCuesInDir(File dir){
    	Log.d(TAG,"checking dir: "+dir.getName());
    	File[] files = dir.listFiles();
    	if (files != null){
    		for (File f : files){
    			
    			if (f.isDirectory()){
    				Log.d(TAG,"descending into "+f.getName());
    				addCuesInDir(f);
    			}
    			else
    			{
    				if (f.getName().contains(".cue"))
    				{
    					Log.d(TAG,"Adding file: "+f.getName());
    					fileList.add(f.getName());
    					fullFileList.add(f.getPath());
    				}
    			}
    		}
    	}
    }

    private void readAnki(){
     final ArrayList<String> ankiFileList = new ArrayList<String>();
     File dirAnkiDroid = Environment.getExternalStoragePublicDirectory("AnkiDroid");
     File[] files = dirAnkiDroid.listFiles();
     if (files != null){
    	 for (File f : files){
    		 if ((f.isFile())&&(f.getName().contains(".anki"))){
    			 ankiFileList.add(f.getPath());
    		 }
    	 }
    	 if (ankiFileList.size()>0){
    		 AlertDialog.Builder aab = new AlertDialog.Builder(this);
	         aab.setTitle("Pick an .anki");
	         final String[] ankiFileArray = ankiFileList.toArray(new String[ankiFileList.size()]); 
	         aab.setItems(ankiFileArray, new DialogInterface.OnClickListener() {
				@Override
				public void onClick(DialogInterface dialog, int which) {
					readAnkiFile(ankiFileList.get(which));
				}
			});
	         AlertDialog aad = aab.create();
	         aad.show();
    		 
    	 }
     }
    }
    
    private boolean readAnkiFile(String ankiFile){
		Log.d("PimSRS","reading "+ankiFile);
    	if ((ankiFile == null)||(ankiFile == "")) return false;
    	try 
    	{
    	if ((ankiDB != null)&&(ankiDB.isOpen())) ankiDB.close();
    	ankiDB = SQLiteDatabase.openDatabase(ankiFile, null,SQLiteDatabase.OPEN_READWRITE);
    	Log.d(TAG,"Anki db opened :)");
    	txtAnkiFile.setText(ankiFile);
        this.ankiFileName = ankiFile;
        ankiFileRead = true;
        updateEnableds();
    	return true;
    	}
    	catch (Exception e){
    		Log.d("PimSRS"," opening error: "+e.getMessage());
    		ankiFileRead = false;
    		txtAnkiFile.setText("(no .anki opened)");
    		this.ankiFileName = "";
    		ankiDB = null;
    		return false;
    	}
    	
    }
    
	private void readCue(){
      Log.d(TAG,"Looking for .cues");
      new SearchForCues().execute();
	}

	private class PimLine {
		private int index;
		private boolean silence;
		private int timestamp;
		public PimLine(int index, boolean silence, int timestamp){
			this.index = index;
			this.silence = silence;
			this.timestamp = timestamp;
		}
		
	}
	
	private void displayCurrentPimLine(){
		if (currentPimLineIndex < pimLines.size()-1)
		{
			PimLine pl = pimLines.get(currentPimLineIndex);
			txtCurrentLine.setText(Integer.toString(pl.index));
			txtSilent.setText(Boolean.toString(pl.silence));
			txtAudioFile.setText((pl.silence ? "" : currentCueFileStub+String.format("_%03d.mp3", pl.index)));
			txtTimestamp.setText(Integer.toString(pl.timestamp));
		}
	}
	
	private void playLine(int ind){
		playPimLine(pimLines.get(ind));
	}
	
	private void playPimLine(PimLine pimLine){
		if (pimLine.silence) return;
		if (mp != null) mp.release();
		mp = new MediaPlayer();
		try {
			mp.setDataSource(String.format("%s_%03d.mp3",currentFullCueFileStub,pimLine.index));
			mp.prepare();
			mp.start();
		} catch (IllegalArgumentException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IllegalStateException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
	}

	private void updateEnableds(){
		btnPrev.setEnabled(currentPimLineIndex>0);
		btnNext.setEnabled(currentPimLineIndex<pimLines.size());
		btnPlay.setEnabled(!pimLines.get(currentPimLineIndex).silence);
		btnAutoplay.setEnabled(cueFileRead);
		btnAddPair.setEnabled(cueFileRead&&ankiFileRead);
		btnTest.setEnabled(true);
	}
	
	@Override
	public void onClick(View v) {
		switch (v.getId()){
		case R.id.btn_prev:
			plPrev();
			break;
		case R.id.btn_play:
			playCurrent();
			break;
		case R.id.btn_next:
			plNext();
			break;
		case R.id.btn_autoplay:
			toggleAutoPlay();
			break;
		case R.id.btn_add_pair:
			addCurrentPair();
			break;
		case R.id.btn_test:
			testArea();
			break;
		}
		
	}

	private void playCurrent(){
		playLine(currentPimLineIndex);		
	}
	
	private void plNext(){
		if (currentPimLineIndex<pimLines.size()) currentPimLineIndex++;
		displayCurrentPimLine();
		updateEnableds();
	}
	private void plPrev(){
		if (currentPimLineIndex>0) currentPimLineIndex--;
		displayCurrentPimLine();
		updateEnableds();
	}
	
	private boolean plNextNonSilent(){
		int curin = currentPimLineIndex+1;
		while ((curin<pimLines.size()) && (pimLines.get(curin).silence)) curin++;
		if ((curin<pimLines.size())&&(!(pimLines.get(curin).silence))){
			currentPimLineIndex = curin;
			displayCurrentPimLine();
			updateEnableds();
			return true;
		}
		else { return false;}
	}
		
	private boolean plPrevNonSilent(){
		int curin = currentPimLineIndex-1;
		while ((curin>0) && (pimLines.get(curin).silence)) curin--;
		if ((curin<pimLines.size())&&(!(pimLines.get(curin).silence))){
			currentPimLineIndex = curin;
			displayCurrentPimLine();
			updateEnableds();
			return true;
		}
		else
		{ return false;}
	}
	
	private void plPlayNextNonSilent(){
		if (plNextNonSilent()) playCurrent();
		else mTts.speak("Cannot advance forward", TextToSpeech.QUEUE_FLUSH, null);
	}

	private void plPlayPrevNonSilent(){
		if (plPrevNonSilent()) playCurrent();
		else mTts.speak("Cannot advance backwards", TextToSpeech.QUEUE_FLUSH, null);
	}
	
	@Override
	public void buttonPressed(ButtonEvent arg0) {
		Message msg = new Message();
		// TODO Auto-generated method stub
		switch (arg0.getButtonID()){
		case 0: msg.what = MSG_ZEEMOTE_BUTTON_A;
			break;
		case 1: msg.what = MSG_ZEEMOTE_BUTTON_B;
			break;
		case 2: msg.what = MSG_ZEEMOTE_BUTTON_C;
			break;
		case 3: msg.what = MSG_ZEEMOTE_BUTTON_D;
			break;
		default: msg.what = -1;
		}
		if (msg.what > -1) this.ZeemoteHandler.sendMessage(msg);
	}

	@Override
	public void buttonReleased(ButtonEvent arg0) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void joystickMoved(JoystickEvent arg0) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void batteryUpdate(BatteryEvent arg0) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void connected(ControllerEvent arg0) {
		// TODO Auto-generated method stub
        final PowerManager pm = (PowerManager)getSystemService(Context.POWER_SERVICE);
        wakeLock = pm.newWakeLock(PowerManager.SCREEN_DIM_WAKE_LOCK, "PimSRS");
        wakeLock.acquire();
	}

	@Override
	public void disconnected(DisconnectEvent arg0) {
		// TODO Auto-generated method stub
		if (wakeLock!=null) {
			wakeLock.release();
			wakeLock = null;
		}
	}
	
	private void testArea(){
		Toast.makeText(getApplicationContext(), "Test area", Toast.LENGTH_SHORT).show();
	}
	
}