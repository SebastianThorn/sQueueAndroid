package com.example.squeue;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.HashMap;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.util.EntityUtils;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import android.os.Bundle;
import android.os.Handler;
import android.app.ActionBar;
import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.AutoCompleteTextView;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.SearchView;
import android.widget.SimpleAdapter;
import android.widget.Toast;

public class MainActivity extends Activity implements SearchView.OnQueryTextListener {

	//Variables
	private SearchView mSearchView;
	private ListView mListView;
	private XMLParser parser;
	private String trackXML;
	private Handler mHandler;

	static final String URL = "http://ws.spotify.com/search/1/track?q=";
	static final String SERVER_ADD = "add/";
	protected static SharedPreferences settings;

	// XML node keys
	static final String KEY_TRACKS = "tracks";
	static final String KEY_TRACK = "track";
	static final String KEY_ARTIST = "artist";
	static final String KEY_ALBUM = "album";
	static final String KEY_NAME = "name";
	static final String KEY_URI = "href";
	static final String KEY_QITEM = "qItem";

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		getWindow().requestFeature(Window.FEATURE_ACTION_BAR);

		setContentView(R.layout.activity_main);

		mListView = (ListView) findViewById(R.id.listViewRes);
		parser = new XMLParser();
		trackXML = null;
		mHandler = new Handler();
		settings = getSharedPreferences("Info", Context.MODE_PRIVATE);
		AutoCompleteTextView userEdit = (AutoCompleteTextView) findViewById(R.id.userEdit);
		AutoCompleteTextView serverEdit = (AutoCompleteTextView) findViewById(R.id.serverEdit);
		userEdit.setText(settings.getString("User", "").toString());
		serverEdit.setText(settings.getString("Server", "").toString());

		// Set up the actionbar.
		final ActionBar actionBar = getActionBar();
		actionBar.setDisplayShowHomeEnabled(false);
		//actionBar.setDisplayShowTitleEnabled(false);

	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		super.onCreateOptionsMenu(menu);

		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.main, menu);
		MenuItem searchItem = menu.findItem(R.id.action_search);
		mSearchView = (SearchView) searchItem.getActionView();
		setupSearchView(searchItem);

		return true;
	}
	
    // fastToast(); Show a fast toast to the user
    private void fastToast(String text) {
    	Context context = getApplicationContext();
    	int duration = Toast.LENGTH_SHORT;
    	Toast toast = Toast.makeText(context, text, duration);
    	toast.show();
    }

	private void setupSearchView(MenuItem searchItem) {
		mSearchView.setOnQueryTextListener(this);
	}

	public boolean onQueryTextChange(String newText) {
		return false;
	}

	public boolean onQueryTextSubmit(String query) {
		Log.i("Debug", "onQueryTextSubmit(query) " + query);

		getTracks(URL+query.replace(" ", "%20"));
		return false;
	}

	private void getTracks(final String address) {
		Log.i("Debug", "getTracks(address) " + address);
		new Thread(new Runnable() {
			public void run() {
				try {
					DefaultHttpClient httpClient = new DefaultHttpClient();
					HttpGet httpGet = new HttpGet(address);
					HttpResponse httpResponse = httpClient.execute(httpGet);
					HttpEntity httpEntity = httpResponse.getEntity();
					trackXML = EntityUtils.toString(httpEntity);
				} catch (UnsupportedEncodingException e) {
					e.printStackTrace();
				} catch (ClientProtocolException e) {
					e.printStackTrace();
				} catch (IOException e) {
					e.printStackTrace();
				}
				Log.i("Debug", "Response below @@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@");
				//Log.i("Debug", trackXML);
				mHandler.post(getTracksUpdate);
			}
		}).start();
	}

	final Runnable getTracksUpdate = new Runnable() {
		public void run() {
			Log.i("Debug", "getTracksUpdate()");

			System.out.println("Response type: " + trackXML.substring(0,5));
			if (trackXML.substring(0,5).toString().compareToIgnoreCase("<?xml") == 0) {
				try{
					Document doc = parser.getDomElement(trackXML); // getting DOM element
					final ArrayList<HashMap<String, String>> menuItems = new ArrayList<HashMap<String, String>>();
					NodeList nl = doc.getElementsByTagName(KEY_TRACK);
					// looping through all item nodes <item>
					for (int i = 0; i < nl.getLength(); i++) {
						HashMap<String, String> map = new HashMap<String, String>();
						Element e = (Element) nl.item(i);

						// Variables
						String track;
						String artist = "";
						String album;
						String uri = "";

						// Trackname and track-URI
						track = parser.getValue(e, KEY_NAME);
						uri = e.getAttribute(KEY_URI);

						// Artist, may be more then one artist per track
						NodeList artists = e.getElementsByTagName(KEY_ARTIST);
						for (int j = 0; j < artists.getLength(); j++) {
							Element f = (Element) artists.item(j);

							if (j != 0) {
								artist += ", ";
							}
							artist += parser.getValue(f, KEY_NAME);
						}

						// Album
						NodeList nAlbum = e.getElementsByTagName(KEY_ALBUM);
						Element f = (Element) nAlbum.item(0);
						album = parser.getValue(f, KEY_NAME);

						String out = (artist + " - " + track + ", " + album);

						// adding each child node to HashMap key => value
						map.put(KEY_URI, uri);
						map.put(KEY_QITEM, out);
						map.put(KEY_NAME, track);
						map.put(KEY_ARTIST, artist);
						map.put(KEY_ALBUM, album);

						// adding HashList to ArrayList
						menuItems.add(map);

						// Adding menuItems to ListView
						ListAdapter adapter = new SimpleAdapter(
								MainActivity.this,
								menuItems,
								R.layout.list_item,
								new String[] { KEY_QITEM },
								new int[] {R.id.queryItem}
								);

						mListView.setAdapter(adapter);

						mListView.setOnItemClickListener(new OnItemClickListener() {

							@Override
							public void onItemClick(AdapterView<?> arg0, View arg1, int arg2, long arg3) {
								String uri = menuItems.get(arg2).get(KEY_URI);
								Log.i("Debug", "Click: " + uri);
								postTrack(uri);
							}
						});
					}

				} catch (Exception ex) {
					ex.printStackTrace();
				}
			} else {
				fastToast("Got bad result from Spotify");
			}

		}
	};

	private void postTrack(final String uri) {
		Log.i("Debug", "postTrack(uri) " + uri);
		new Thread(new Runnable() {
			public void run() {
				try {
					DefaultHttpClient httpClient = new DefaultHttpClient();

					AutoCompleteTextView userEdit = (AutoCompleteTextView) findViewById(R.id.userEdit);
					AutoCompleteTextView serverEdit = (AutoCompleteTextView) findViewById(R.id.serverEdit);

					SharedPreferences.Editor editor = settings.edit();
					editor.putString("User", userEdit.getText().toString());
					editor.putString("Server", serverEdit.getText().toString());
					editor.commit();

					String command = "http://" + settings.getString("Server", "").toString() + "/add/" + 
							settings.getString("User", "").toString() + "/" + uri;

					Log.i("Debug", "command: " + command);
					HttpGet httpGet = new HttpGet(command);
					httpClient.execute(httpGet);
				} catch (UnsupportedEncodingException e) {
					e.printStackTrace();
				} catch (ClientProtocolException e) {
					e.printStackTrace();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}).start();
	}
	
	public boolean onClose() {
		return false;
	}

	protected boolean isAlwaysExpanded() {
		return false;
	}

}