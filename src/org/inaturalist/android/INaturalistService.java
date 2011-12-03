package org.inaturalist.android;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.util.EntityUtils;
import org.json.JSONArray;
import org.json.JSONException;

import android.app.IntentService;
import android.content.ContentValues;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.util.Base64;
import android.util.Log;

public class INaturalistService extends IntentService {
	public static String TAG = "INaturalistService";
	public static String HOST = "http://192.168.1.12:3000";
	public static String ACTION_PASSIVE_SYNC = "passive_sync";
	private String mLogin;
	private String mCredentials;
	private SharedPreferences mPreferences;
	private boolean mPassive;
	
	public INaturalistService() {
		super("INaturalistService");
	}

	@Override
	protected void onHandleIntent(Intent intent) {
		mPreferences = getSharedPreferences("iNaturalistPreferences", MODE_PRIVATE);
		mLogin = mPreferences.getString("username", null);
		mCredentials = mPreferences.getString("credentials", null);
		String action = intent.getAction();
		
		// TODO dispatch intent actions
		// TODO postObservations()
		// TODO postPhotos()
		if (action.equals(ACTION_PASSIVE_SYNC)) {
			mPassive = true;
		} else {
			mPassive = false;
		}
		getUserObservations();
	}
	
	private void getUserObservations() {
		if (ensureCredentials() == false) {
			return;
		}
		JSONArray json = get(HOST + "/observations/" + mLogin + ".json");
		Log.d(TAG, "json: " + json);
		if (json == null || json.length() == 0) { return; }
		syncJson(json);
	}
	
	private static JSONArray get(String url) {
		return get(url, false);
	}
	
	private static JSONArray get(String url, boolean authenticated) {
		return request(url, "get", authenticated);
	}
	
	private static JSONArray request(String url, String method, boolean authenticated) {
		Log.d(TAG, "requesting " + url);
		DefaultHttpClient client = new DefaultHttpClient();
		HttpRequestBase request = method == "get" ? new HttpGet(url) : new HttpPost(url);
		request.setHeader("Content-Type", "application/json");
		try {
			HttpResponse response = client.execute(request);

			HttpEntity entity = response.getEntity();
			String content = EntityUtils.toString(entity);
			Log.d(TAG, "OK: " + content.toString());
			try {
				JSONArray json = new JSONArray(content);
				return json;
			} catch (JSONException e) {
				Log.e(TAG, "JSONException: " + e.toString());
			}
		}
		catch (IOException e) {
			request.abort();
			Log.w(TAG, "Error for URL " + url, e);
		}
		return null;
	}
	
	private boolean ensureCredentials() {
		if (mCredentials != null) { return true; }
		
		// request login unless passive
		Log.d(TAG, "ensuring creds, mPassive: " + mPassive);
		if (!mPassive) {
			Intent intent = new Intent(getBaseContext(), INaturalistPrefsActivity.class);
			intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
			getApplication().startActivity(intent);
		}
		stopSelf();
		return false;
	}
	
	public static boolean verifyCredentials(String credentials) {
		DefaultHttpClient client = new DefaultHttpClient();
		String url = HOST + "/observations/new.json";
		HttpRequestBase request = new HttpGet(url);
		request.setHeader("Authorization", "Basic "+credentials);
		request.setHeader("Content-Type", "application/json");
		
		try {
			HttpResponse response = client.execute(request);
			HttpEntity entity = response.getEntity();
			String content = EntityUtils.toString(entity);
			Log.d(TAG, "OK: " + content.toString());
			if (response.getStatusLine().getStatusCode() == HttpStatus.SC_OK) {
				return true;
			} else {
				Log.e(TAG, "Authentication failed: " + content);
				return false;
			}
		}
		catch (IOException e) {
			request.abort();
			Log.w(TAG, "Error for URL " + url, e);
		}
		return false;
	}
	
	public static boolean verifyCredentials(String username, String password) {
		String credentials = Base64.encodeToString(
			(username + ":" + password).getBytes(), Base64.URL_SAFE|Base64.NO_WRAP
		);
		return verifyCredentials(credentials);
	}
	
	public void syncJson(JSONArray json) {
		ArrayList<Integer> ids = new ArrayList<Integer>();
		ArrayList<Integer> existingIds = new ArrayList<Integer>();
		ArrayList<Integer> newIds = new ArrayList<Integer>();
		HashMap<Integer,Observation> jsonObservationsById = new HashMap<Integer,Observation>();
		Observation observation;
		Observation jsonObservation;
		
		BetterJSONObject o;
		for (int i = 0; i < json.length(); i++) {
			try {
				o = new BetterJSONObject(json.getJSONObject(i));
				ids.add(o.getInt("id"));
				jsonObservationsById.put(o.getInt("id"), new Observation(o));
			} catch (JSONException e) {
				Log.e(TAG, "JSONException: " + e.toString());
			}
		}
		// find obs with existing ids
		String joinedIds = StringUtils.join(ids, ",");
		// TODO why doesn't selectionArgs work for id IN (?)
		Cursor c = getContentResolver().query(Observation.CONTENT_URI, 
				Observation.PROJECTION, 
				"id IN ("+joinedIds+")", null, Observation.DEFAULT_SORT_ORDER);
		
		// update existing
		c.moveToFirst();
        while (c.isAfterLast() == false) {
        	observation = new Observation(c);
        	jsonObservation = jsonObservationsById.get(observation.id);
        	observation.merge(jsonObservation);
        	getContentResolver().update(observation.getUri(), observation.getContentValues(), null, null);
        	existingIds.add(observation.id);
       	    c.moveToNext();
        }
        c.close();
        
        // insert new
        newIds = (ArrayList<Integer>) CollectionUtils.subtract(ids, existingIds);
        Log.d(TAG, "ids: " + ids);
        Log.d(TAG, "existingIds: " + existingIds);
        Log.d(TAG, "newIds: " + newIds);
		for (int i = 0; i < newIds.size(); i++) {			
			jsonObservation = jsonObservationsById.get(newIds.get(i));
			ContentValues cv = jsonObservation.getContentValues();
			getContentResolver().insert(Observation.CONTENT_URI, cv);
		}
	}
}
