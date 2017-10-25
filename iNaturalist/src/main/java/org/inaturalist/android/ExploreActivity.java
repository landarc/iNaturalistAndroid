package org.inaturalist.android;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Point;
import android.graphics.Typeface;
import android.location.Location;
import android.os.Build;
import android.os.Bundle;
import android.support.design.widget.TabLayout;
import android.support.v4.view.PagerAdapter;
import android.support.v4.view.ViewCompat;
import android.support.v4.view.ViewPager;
import android.support.v7.app.ActionBar;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.flurry.android.FlurryAgent;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.CameraPosition;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.VisibleRegion;

import org.apache.commons.lang3.StringUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.lang.reflect.InvocationTargetException;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ExploreActivity extends BaseFragmentActivity {
    private static final int NOT_LOADED = -1;

    private static final int VIEW_OBSERVATION_REQUEST_CODE = 0x100;
    private static final int MAX_RESULTS = 50;

    private INaturalistApp mApp;
    private ActivityHelper mHelper;

    private TabLayout mTabLayout;
    private ViewPager mViewPager;

    private static final int VIEW_TYPE_OBSERVATIONS = 0;
    private static final int VIEW_TYPE_SPECIES = 1;
    private static final int VIEW_TYPE_OBSERVERS = 2;
    private static final int VIEW_TYPE_IDENTIFIERS = 3;

    private int mActiveViewType;

    private int[] mTotalResults = { NOT_LOADED, NOT_LOADED, NOT_LOADED, NOT_LOADED };

    private ExploreSearchFilters mSearchFilters;

    // Current search results
    private List<JSONObject>[] mResults = (List<JSONObject>[]) new List[] {null, null, null, null };

    private ExploreResultsReceiver mExploreResultsReceiver;
    private LocationReceiver mLocationReceiver;

    private ProgressBar mLoadingObservationsGrid;
    private TextView mObservationsGridEmpty;
    private GridViewExtended mObservationsGrid;
    private ObservationGridAdapter mGridAdapter;
    private GoogleMap mObservationsMap;
    private ViewGroup mObservationsMapContainer;

    private ImageView mObservationsViewModeGrid;
    private ImageView mObservationsViewModeMap;
    private ImageView mObservationsChangeMapLayers;
    private ImageView mObservationsMapMyLocation;
    private ViewGroup mRedoObservationsSearch;
    private ProgressBar mPerformingSearch;

    private ListView[] mList = new ListView[] { null, null, null, null };
    private ArrayAdapter[] mListAdapter =  new ArrayAdapter[] { null, null, null, null };
    private ProgressBar[] mLoadingList = new ProgressBar[] { null, null, null, null };
    private TextView[] mListEmpty = new TextView[] { null, null, null, null };
    private ViewGroup[] mListHeader = new ViewGroup[] { null, null, null, null };


    private static final int OBSERVATIONS_VIEW_MODE_GRID = 0;
    private static final int OBSERVATIONS_VIEW_MODE_MAP = 1;
    private int mObservationsViewMode = OBSERVATIONS_VIEW_MODE_GRID;


    private int[] mCurrentResultsPage = { 0, 0, 0, 0 };
    private boolean[] mLoadingNextResults = { false, false, false, false };
    private HashMap<String, JSONObject> mMarkerObservations;
    private int mObservationsMapType = GoogleMap.MAP_TYPE_TERRAIN;
    private float mMyLocationZoomLevel = 0;
    private LatLngBounds mLastMapBounds = null;
    private boolean mMapMoved = false;
    private LatLng mLastPosition;

    @Override
	protected void onStart()
	{
		super.onStart();
		FlurryAgent.onStartSession(this, INaturalistApp.getAppContext().getString(R.string.flurry_api_key));
		FlurryAgent.logEvent(this.getClass().getSimpleName());
	}

	@Override
	protected void onStop()
	{
		super.onStop();		
		FlurryAgent.onEndSession(this);
	}



    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();

        inflater.inflate(R.menu.explore_menu, menu);

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.search:
                return true;

            case R.id.filters:
                return true;
        }
        return super.onOptionsItemSelected(item);
    }


    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        ActionBar actionBar = getSupportActionBar();
        actionBar.setElevation(0);

        actionBar.setCustomView(R.layout.explore_action_bar_new);
        actionBar.setDisplayShowCustomEnabled(true);

        mHelper = new ActivityHelper(this);

        setContentView(R.layout.explore);

        if (savedInstanceState == null) {
            mActiveViewType = VIEW_TYPE_OBSERVATIONS;

            mTotalResults = new int[] { NOT_LOADED, NOT_LOADED, NOT_LOADED, NOT_LOADED };
            mResults = (List<JSONObject>[]) new List[] {null, null, null, null };

            mSearchFilters = new ExploreSearchFilters();

            loadAllResults();

            mLastMapBounds = null;

        } else {
            mActiveViewType = savedInstanceState.getInt("mActiveViewType");
            mTotalResults = savedInstanceState.getIntArray("mTotalResults");
            mObservationsViewMode = savedInstanceState.getInt("mObservationsViewMode");
            mSearchFilters = (ExploreSearchFilters) savedInstanceState.getSerializable("mSearchFilters");
            mCurrentResultsPage = savedInstanceState.getIntArray("mCurrentResultsPage");
            mLoadingNextResults = savedInstanceState.getBooleanArray("mLoadingNextResults");
            mObservationsMapType = savedInstanceState.getInt("mObservationsMapType", GoogleMap.MAP_TYPE_TERRAIN);
            mMapMoved = savedInstanceState.getBoolean("mMapMoved");
            mMarkerObservations = mHelper.loadMapFromBundle(savedInstanceState, "mMarkerObservations");

            mResults = (List<JSONObject>[]) new List[] {null, null, null, null };
            mResults[VIEW_TYPE_OBSERVATIONS] = mHelper.loadListFromBundle(savedInstanceState, "mObservations");
            mResults[VIEW_TYPE_SPECIES] = mHelper.loadListFromBundle(savedInstanceState, "mSpecies");
            mResults[VIEW_TYPE_OBSERVERS] = mHelper.loadListFromBundle(savedInstanceState, "mObservers");
            mResults[VIEW_TYPE_IDENTIFIERS] = mHelper.loadListFromBundle(savedInstanceState, "mIdentifiers");

            VisibleRegion vr = savedInstanceState.getParcelable("mapRegion");
            mLastMapBounds = new LatLngBounds(new LatLng(vr.nearLeft.latitude, vr.farLeft.longitude), new LatLng(vr.farRight.latitude, vr.farRight.longitude));
        }

        onDrawerCreate(savedInstanceState);

        // Tab Initialization
        initializeTabs();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        outState.putInt("mActiveViewType", mActiveViewType);
        outState.putIntArray("mTotalResults", mTotalResults);
        outState.putInt("mObservationsViewMode", mObservationsViewMode);
        outState.putIntArray("mCurrentResultsPage", mCurrentResultsPage);
        outState.putBooleanArray("mLoadingNextResults", mLoadingNextResults);
        outState.putInt("mObservationsMapType", mObservationsMapType);
        outState.putBoolean("mMapMoved", mMapMoved);
        mHelper.saveMapToBundle(outState, mMarkerObservations, "mMarkerObservations");

        outState.putSerializable("mSearchFilters", mSearchFilters);

        mHelper.saveListToBundle(outState, mResults[VIEW_TYPE_OBSERVATIONS], "mObservations");
        mHelper.saveListToBundle(outState, mResults[VIEW_TYPE_SPECIES], "mSpecies");
        mHelper.saveListToBundle(outState, mResults[VIEW_TYPE_OBSERVERS], "mObservers");
        mHelper.saveListToBundle(outState, mResults[VIEW_TYPE_IDENTIFIERS], "mIdentifiers");

        saveListViewOffset(mObservationsGrid, outState, "mObservationsGrid");

        saveListViewOffset(mList[VIEW_TYPE_SPECIES], outState, "mList" + VIEW_TYPE_SPECIES);
        saveListViewOffset(mList[VIEW_TYPE_OBSERVERS], outState, "mList" + VIEW_TYPE_OBSERVERS);
        saveListViewOffset(mList[VIEW_TYPE_IDENTIFIERS], outState, "mList" + VIEW_TYPE_IDENTIFIERS);

        if (mObservationsMap != null) {
            VisibleRegion vr = mObservationsMap.getProjection().getVisibleRegion();
            outState.putParcelable("mapRegion", vr);
        }


        super.onSaveInstanceState(outState);
    }

    Map<String, Integer> listViewIndex = new HashMap<>();
    Map<String, Integer> listViewOffset = new HashMap<>();

    private void loadListViewOffset(final AbsListView listView, Bundle extras, String key) {
        if (listView == null) return;

        Integer index, offset;

        if (extras == null) {
            index = listViewIndex.get(key);
            offset = listViewOffset.get(key);
            if (index == null) index = 0;
            if (offset == null) offset = 0;
        } else {
            index = extras.getInt(key + "Index");
            offset = extras.getInt(key + "Offset");
        }

        final Integer finalIndex = index, finalOffset = offset;
        listView.post(new Runnable() {
            @Override
            public void run() {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    if (listView instanceof GridView) {
                        // Weird Android issue - if it's a grid view, setting the offset will reset the
                        // row number to zero (so we can only set the row number, but not the offset)
                        listView.setSelection(finalIndex);
                    } else {
                        listView.setSelectionFromTop(finalIndex, finalOffset);
                    }
                } else {
                    listView.setSelection(finalIndex);
                }
            }
        });

    }

    private void saveListViewOffset(AbsListView listView, Bundle outState, String key) {
        if (listView != null) {
            View firstVisibleRow = listView.getChildAt(0);

            if (firstVisibleRow != null) {
                Integer offset = firstVisibleRow.getTop() - listView.getPaddingTop();
                Integer index = listView.getFirstVisiblePosition();

                listViewIndex.put(key, index);
                listViewOffset.put(key, offset);

                outState.putInt(key + "Index", index);
                outState.putInt(key + "Offset", offset);
            }
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        BaseFragmentActivity.safeUnregisterReceiver(mExploreResultsReceiver, this);
        BaseFragmentActivity.safeUnregisterReceiver(mLocationReceiver, this);

        mLoadingNextResults = new boolean[] { false, false, false, false };
    }

    @Override
    public void onResume() {
        super.onResume();
        if (mApp == null) {
            mApp = (INaturalistApp) getApplicationContext();
        }

        mExploreResultsReceiver = new ExploreResultsReceiver();
        IntentFilter filter = new IntentFilter();
        filter.addAction(INaturalistService.EXPLORE_GET_OBSERVATIONS_RESULT);
        filter.addAction(INaturalistService.EXPLORE_GET_SPECIES_RESULT);
        filter.addAction(INaturalistService.EXPLORE_GET_IDENTIFIERS_RESULT);
        filter.addAction(INaturalistService.EXPLORE_GET_OBSERVERS_RESULT);
        BaseFragmentActivity.safeRegisterReceiver(mExploreResultsReceiver, filter, this);

        mLocationReceiver = new LocationReceiver();
        IntentFilter filter2 = new IntentFilter();
        filter2.addAction(INaturalistService.GET_CURRENT_LOCATION_RESULT);
        BaseFragmentActivity.safeRegisterReceiver(mLocationReceiver, filter2, this);


        refreshViewState();
    }

     // Method to add a TabHost
    private void addTab(int position, View tabContent) {
        TabLayout.Tab tab = mTabLayout.getTabAt(position);
        tab.setCustomView(tabContent);
    }


    // Tabs Creation
    private void initializeTabs() {
        mTabLayout = (TabLayout) findViewById(R.id.tabs);
        mViewPager = (ViewPager) findViewById(R.id.view_pager);

        mViewPager.setOffscreenPageLimit(3); // So we wouldn't have to recreate the views every time
        ExplorePagerAdapter adapter = new ExplorePagerAdapter(this);
        mViewPager.setAdapter(adapter);
        mTabLayout.setupWithViewPager(mViewPager);

        addTab(0, createTabContent(getString(R.string.project_observations), 1000));
        addTab(1, createTabContent(getString(R.string.project_species), 2000));
        addTab(2, createTabContent(getString(R.string.observers), 3000));
        addTab(3, createTabContent(getString(R.string.project_identifiers), 4000));

        TabLayout.OnTabSelectedListener tabListener = new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                TextView tabNameText = (TextView) tab.getCustomView().findViewById(R.id.tab_name);

                tabNameText.setTypeface(null, Typeface.BOLD);
                tabNameText.setTextColor(Color.parseColor("#000000"));

                mViewPager.setCurrentItem(tab.getPosition());
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {
                View tabView = tab.getCustomView();
                TextView tabNameText = (TextView) tabView.findViewById(R.id.tab_name);

                tabNameText.setTypeface(null, Typeface.NORMAL);
                tabNameText.setTextColor(Color.parseColor("#ACACAC"));
            }

            @Override
            public void onTabReselected(TabLayout.Tab tab) {

            }
        };
        mTabLayout.setOnTabSelectedListener(tabListener);

        ViewPager.OnPageChangeListener pageListener = new ViewPager.OnPageChangeListener() {
            @Override
            public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {
            }

            @Override
            public void onPageSelected(int position) {
                mActiveViewType = position;
            }

            @Override
            public void onPageScrollStateChanged(int state) {
            }
        };
        mViewPager.addOnPageChangeListener(pageListener);

        tabListener.onTabSelected(mTabLayout.getTabAt(mActiveViewType));

        if (getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE) {
            mTabLayout.setTabMode(TabLayout.MODE_FIXED);
        } else {
            mTabLayout.setTabMode(TabLayout.MODE_SCROLLABLE);
        }
    }

    private View createTabContent(String tabName, int count) {
        ViewGroup view = (ViewGroup) LayoutInflater.from(this).inflate(R.layout.my_observations_tab, null);
        TextView countText = (TextView) view.findViewById(R.id.count);
        TextView tabNameText = (TextView) view.findViewById(R.id.tab_name);

        DecimalFormat formatter = new DecimalFormat("#,###,###");
        countText.setText(formatter.format(count));
        tabNameText.setText(tabName);

        int width;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            Point size = new Point();
            getWindowManager().getDefaultDisplay().getSize(size);
            width = size.x;
        } else {
            width = getWindowManager().getDefaultDisplay().getWidth();
        }
        width = (int)(width * 0.283);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(width, ViewGroup.LayoutParams.WRAP_CONTENT);
        view.setLayoutParams(params);

        return view;
    }

    private void addObservationMarker(JSONObject o) throws JSONException {
    	if (o == null) return;

        if ((!o.has("location") || o.isNull("location"))) {
            return;
        }

        LatLng latLng;
        String locationParts[] = o.getString("location").split(",");
        latLng = new LatLng(Double.valueOf(locationParts[0]), Double.valueOf(locationParts[1]));
        String iconicTaxonName = o.has("taxon") && !o.isNull("taxon") ? o.getJSONObject("taxon").getString("iconic_taxon_name") : null;

        MarkerOptions opts = new MarkerOptions()
            .position(latLng)
            .icon(TaxonUtils.observationMarkerIcon(iconicTaxonName))
            .draggable(false);

        Marker m = mObservationsMap.addMarker(opts);
        mMarkerObservations.put(m.getId(), o);
    }

    private class LocationReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            Bundle extras = intent.getExtras();

            Location location = extras.getParcelable(INaturalistService.LOCATION);

            if ((location == null) || (mObservationsMap == null)) {
                return;
            }

            mLastPosition = new LatLng(location.getLatitude(), location.getLongitude());
            mObservationsMap.moveCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(location.getLatitude(), location.getLongitude()), mMyLocationZoomLevel));
        }
    }

    private class ExploreResultsReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            Bundle extras = intent.getExtras();

            int index = 0;
            if (intent.getAction().equals(INaturalistService.EXPLORE_GET_OBSERVATIONS_RESULT)) {
                index = VIEW_TYPE_OBSERVATIONS;
            } else if (intent.getAction().equals(INaturalistService.EXPLORE_GET_SPECIES_RESULT)) {
                index = VIEW_TYPE_SPECIES;
            } else if (intent.getAction().equals(INaturalistService.EXPLORE_GET_IDENTIFIERS_RESULT)) {
                index = VIEW_TYPE_IDENTIFIERS;
            } else if (intent.getAction().equals(INaturalistService.EXPLORE_GET_OBSERVERS_RESULT)) {
                index = VIEW_TYPE_OBSERVERS;
            }

            mLoadingNextResults[index] = false;

            if (index == VIEW_TYPE_OBSERVATIONS) mMapMoved = false;

            String error = extras.getString("error");
            if (error != null) {
                mHelper.alert(String.format(getString(R.string.couldnt_load_results), error));
                return;
            }

            boolean isSharedOnApp = intent.getBooleanExtra(INaturalistService.IS_SHARED_ON_APP, false);
            BetterJSONObject resultsObject;
            SerializableJSONArray resultsJSON;

            if (isSharedOnApp) {
                resultsObject = (BetterJSONObject) mApp.getServiceResult(intent.getAction());
            } else {
                resultsObject = (BetterJSONObject) intent.getSerializableExtra(INaturalistService.RESULTS);
            }

            JSONArray results = null;
            int totalResults = 0;

            if (resultsObject != null) {
                resultsJSON = resultsObject.getJSONArray("results");
                Integer count = resultsObject.getInt("total_results");
                mCurrentResultsPage[index] = resultsObject.getInt("page");
                if (count != null) {
                    totalResults = count;
                    results = resultsJSON.getJSONArray();
                }
            }

            if (results == null) {
                refreshViewState();
                return;
            }

            ArrayList<JSONObject> resultsArray = new ArrayList<JSONObject>();

            for (int i = 0; i < results.length(); i++) {
				try {
					JSONObject item = results.getJSONObject(i);
					resultsArray.add(item);
				} catch (JSONException e) {
					e.printStackTrace();
				}
            }

            if (mCurrentResultsPage[index] == 1) {
                // Fresh results - overwrite old ones
                mResults[index] = resultsArray;
                mTotalResults[index] = totalResults;

                if ((index == VIEW_TYPE_OBSERVATIONS) && ((mCurrentResultsPage[index] == 1) || (mMarkerObservations == null))) {
                    // New search - clear all observation markers on map
                    mObservationsMap.clear();
                    mMarkerObservations = new HashMap<>();
                }

            } else {
                // Paginated results - append to old ones
                mResults[index].addAll(resultsArray);
                mTotalResults[index] = totalResults;
            }

            refreshViewState();
        }
    }

    private void refreshActionBar() {
        ActionBar actionBar = getSupportActionBar();
        final TextView title = (TextView) actionBar.getCustomView().findViewById(R.id.title);
        final TextView subTitle = (TextView) actionBar.getCustomView().findViewById(R.id.sub_title);

        if (mSearchFilters.isEmpty()) {
            // No filters / search was made yet
            title.setText(R.string.exploring_all);
            subTitle.setText(R.string.nearby);

            return;
        }

        if (mSearchFilters.taxon != null) {
            // Searching for a specific taxa
            title.setText(TaxonUtils.getTaxonName(this, mSearchFilters.taxon));
        } else if (mSearchFilters.iconicTaxa.size() > 0) {
            // Searching for an iconic taxa - display their names
            String titleString = StringUtils.join(mSearchFilters.iconicTaxa, ", ");
            title.setText(titleString);
        }

        if (mSearchFilters.place == null) {
            // Nearby place
            subTitle.setText(R.string.nearby);
        } else {
            // Specific place
            subTitle.setText(mSearchFilters.place.optString("display_name"));
        }
    }

    private void refreshTabTitles() {
        DecimalFormat formatter = new DecimalFormat("#,###,###");

        for (int i = 0; i < mTotalResults.length; i++) {
            if (mTotalResults[i] == NOT_LOADED) {
                // Still loading
                ((TextView) mTabLayout.getTabAt(i).getCustomView().findViewById(R.id.count)).setVisibility(View.GONE);
                ((ProgressBar) mTabLayout.getTabAt(i).getCustomView().findViewById(R.id.loading)).setVisibility(View.VISIBLE);
            } else {
                // Already loaded - set the count
                ((TextView) mTabLayout.getTabAt(i).getCustomView().findViewById(R.id.count)).setVisibility(View.VISIBLE);
                ((ProgressBar) mTabLayout.getTabAt(i).getCustomView().findViewById(R.id.loading)).setVisibility(View.GONE);
                ((TextView) mTabLayout.getTabAt(i).getCustomView().findViewById(R.id.count)).setText(formatter.format(mTotalResults[i]));
            }
        }
    }

    private void refreshResultsView(final int resultsType, final Class<? extends ArrayAdapter> adapterClass) {
         if (mLoadingList[resultsType] == null) {
            // View hasn't loaded yet
            return;
        }

        if (mTotalResults[resultsType] == NOT_LOADED) {
            mLoadingList[resultsType].setVisibility(View.VISIBLE);
            mList[resultsType].setVisibility(View.GONE);
            mListEmpty[resultsType].setVisibility(View.GONE);
            if (mListHeader[resultsType] != null) mListHeader[resultsType].setVisibility(View.GONE);
        } else {
            mLoadingList[resultsType].setVisibility(View.GONE);

            if (mResults[resultsType].size() == 0) {
                mListEmpty[resultsType].setVisibility(View.VISIBLE);
            } else {
                mListEmpty[resultsType].setVisibility(View.GONE);
            }

            if (mListHeader[resultsType] != null) mListHeader[resultsType].setVisibility(View.VISIBLE);

            Runnable setResults = new Runnable() {
                @Override
                public void run() {
                    if ((mListAdapter[resultsType] != null) && (mCurrentResultsPage[resultsType] > 1)) {
                        // New results appended - don't reload the entire adapter
                        mListAdapter[resultsType].notifyDataSetChanged();
                    } else {
                        try {
                            // Create a new adapter
                            mListAdapter[resultsType] = adapterClass.getDeclaredConstructor(Context.class, ArrayList.class).newInstance(ExploreActivity.this, (ArrayList<JSONObject>) mResults[resultsType]);
                            mList[resultsType].setAdapter(mListAdapter[resultsType]);
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                }
            };
            mList[resultsType].post(setResults);

            mList[resultsType].setVisibility(View.VISIBLE);

            loadListViewOffset(mList[resultsType], getIntent().getExtras(), "mList" + resultsType);
        }
    }

    private void refreshObservations() {
        if (mLoadingObservationsGrid == null) {
            // View hasn't loaded yet
            return;
        }

        mObservationsGrid.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> arg0, View view, int position, long arg3) {
                JSONObject item = (JSONObject) view.getTag();
                Intent intent = new Intent(ExploreActivity.this, ObservationViewerActivity.class);
                intent.putExtra("observation", item.toString());
                intent.putExtra("read_only", true);
                intent.putExtra("reload", true);
				startActivityForResult(intent, VIEW_OBSERVATION_REQUEST_CODE);

                try {
                    JSONObject eventParams = new JSONObject();
                    eventParams.put(AnalyticsClient.EVENT_PARAM_VIA, AnalyticsClient.EVENT_VALUE_EXPLORE_GRID);

                    AnalyticsClient.getInstance().logEvent(AnalyticsClient.EVENT_NAME_NAVIGATE_OBS_DETAILS, eventParams);
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
        });

        if (mTotalResults[VIEW_TYPE_OBSERVATIONS] == NOT_LOADED) {
            mLoadingObservationsGrid.setVisibility(View.VISIBLE);
            mObservationsGrid.setVisibility(View.GONE);
            mObservationsGridEmpty.setVisibility(View.GONE);
            mObservationsMapContainer.setVisibility(View.GONE);
        } else {
            mLoadingObservationsGrid.setVisibility(View.GONE);

            if (mResults[VIEW_TYPE_OBSERVATIONS].size() == 0) {
                mObservationsGridEmpty.setVisibility(View.VISIBLE);
            } else {
                mObservationsGridEmpty.setVisibility(View.GONE);
            }

            Runnable setObsInGrid = new Runnable() {
                @Override
                public void run() {
                    if ((mGridAdapter != null) && (mCurrentResultsPage[VIEW_TYPE_OBSERVATIONS] > 1)) {
                        // New results appended - don't reload the entire adapter
                        mGridAdapter.notifyDataSetChanged();
                    } else if (mObservationsGrid.getColumnWidth() > 0) {
                        mGridAdapter = new ObservationGridAdapter(ExploreActivity.this, mObservationsGrid.getColumnWidth(), mResults[VIEW_TYPE_OBSERVATIONS]);
                        mObservationsGrid.setAdapter(mGridAdapter);
                    } else if (mObservationsGrid.getColumnWidth() == 0) {
                        mObservationsGrid.postDelayed(this, 100);
                    }
                }
            };
            mObservationsGrid.post(setObsInGrid);

            mObservationsGrid.setVisibility(View.VISIBLE);

            for (int i = mMarkerObservations.size(); i < mResults[VIEW_TYPE_OBSERVATIONS].size(); i++) {
                try {
                    addObservationMarker(mResults[VIEW_TYPE_OBSERVATIONS].get(i));
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
        }

        loadListViewOffset(mObservationsGrid, getIntent().getExtras(), "mObservationsGrid");


        mObservationsGrid.setOnScrollListener(new AbsListView.OnScrollListener() {
            @Override
            public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount, int totalItemCount) {
                if((firstVisibleItem + visibleItemCount >= totalItemCount) && (totalItemCount > 0)) {
                    // The end has been reached - load more observations
                    loadNextResultsPage(VIEW_TYPE_OBSERVATIONS, false);
                }
            }

            @Override
            public void onScrollStateChanged(AbsListView view, int scrollState){ }
        });


        if (mObservationsViewMode == OBSERVATIONS_VIEW_MODE_GRID) {
            mObservationsViewModeGrid.setSelected(true);
            mObservationsViewModeGrid.setColorFilter(Color.parseColor("#ffffff"));
            mObservationsViewModeMap.setSelected(false);
            mObservationsViewModeMap.setColorFilter(Color.parseColor("#676767"));

            mObservationsMapContainer.setVisibility(View.GONE);
        } else {
            mObservationsViewModeGrid.setSelected(false);
            mObservationsViewModeGrid.setColorFilter(Color.parseColor("#676767"));
            mObservationsViewModeMap.setSelected(true);
            mObservationsViewModeMap.setColorFilter(Color.parseColor("#ffffff"));

            mObservationsMapContainer.setVisibility(View.VISIBLE);
            mObservationsGrid.setVisibility(View.GONE);
            mObservationsGridEmpty.setVisibility(View.GONE);
        }

        mObservationsViewModeGrid.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mObservationsViewMode = OBSERVATIONS_VIEW_MODE_GRID;
                refreshObservations();
            }
        });
        mObservationsViewModeMap.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mObservationsViewMode = OBSERVATIONS_VIEW_MODE_MAP;
                refreshObservations();
            }
        });

        mObservationsChangeMapLayers.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mObservationsMapType == GoogleMap.MAP_TYPE_SATELLITE) {
                    mObservationsMapType = GoogleMap.MAP_TYPE_TERRAIN;
                } else {
                    mObservationsMapType = GoogleMap.MAP_TYPE_SATELLITE;
                }

                refreshMapType();
            }
        });

        refreshMapType();

        mObservationsMap.setOnMapLoadedCallback(new GoogleMap.OnMapLoadedCallback() {
            @Override
            public void onMapLoaded() {
                if (mLastMapBounds != null) mLastMapBounds = null;

                mObservationsMap.setOnCameraChangeListener(new GoogleMap.OnCameraChangeListener() {
                    @Override
                    public void onCameraChange(CameraPosition position) {
                        // User moved the map view - allow him to make a new search on those new map bounds
                        if ((mLastPosition == null) || (!position.target.equals(mLastPosition))) {
                            mMapMoved = true;
                            mRedoObservationsSearch.setVisibility(View.VISIBLE);
                            mLastPosition = position.target;
                        }
                    }
                });
            }
        });

        mRedoObservationsSearch.setVisibility(mMapMoved ? View.VISIBLE : View.GONE);
        mPerformingSearch.setVisibility(mLoadingNextResults[VIEW_TYPE_OBSERVATIONS] ? View.VISIBLE : View.GONE);

        mRedoObservationsSearch.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Re-search under the current map bounds
                VisibleRegion vr = mObservationsMap.getProjection().getVisibleRegion();
                LatLngBounds bounds = new LatLngBounds(new LatLng(vr.nearLeft.latitude, vr.farLeft.longitude), new LatLng(vr.farRight.latitude, vr.farRight.longitude));
                mSearchFilters.mapBounds = bounds;
                loadAllResults();

                mPerformingSearch.setVisibility(View.VISIBLE);
            }
        });
    }

    private void refreshMapType() {
        if (mObservationsMapType == GoogleMap.MAP_TYPE_SATELLITE) {
            mObservationsChangeMapLayers.setImageResource(R.drawable.ic_terrain_black_48dp);
        } else {
            mObservationsChangeMapLayers.setImageResource(R.drawable.ic_satellite_black_48dp);
        }

        mObservationsMap.setMapType(mObservationsMapType);
    }

    private void loadAllResults() {
        loadNextResultsPage(VIEW_TYPE_OBSERVATIONS, true);
        loadNextResultsPage(VIEW_TYPE_SPECIES, true);
        loadNextResultsPage(VIEW_TYPE_IDENTIFIERS, true);
        loadNextResultsPage(VIEW_TYPE_OBSERVERS, true);
    }

    private void loadNextResultsPage(int resultsType, boolean resetResults) {
        if (resetResults) {
            mCurrentResultsPage[resultsType] = 0;
        }

        if (!mLoadingNextResults[resultsType] && ((resetResults) || (mResults[resultsType] == null) || (mResults[resultsType].size() < mTotalResults[resultsType]))) {
            mLoadingNextResults[resultsType] = true;

            String action = null;
            switch (resultsType) {
                case VIEW_TYPE_OBSERVATIONS:
                    action = INaturalistService.ACTION_EXPLORE_GET_OBSERVATIONS;
                    break;
                 case VIEW_TYPE_SPECIES:
                    action = INaturalistService.ACTION_EXPLORE_GET_SPECIES;
                    break;
                 case VIEW_TYPE_IDENTIFIERS:
                    action = INaturalistService.ACTION_EXPLORE_GET_IDENTIFIERS;
                    break;
                 case VIEW_TYPE_OBSERVERS:
                    action = INaturalistService.ACTION_EXPLORE_GET_OBSERVERS;
                    break;
            }

            Intent serviceIntent = new Intent(action, null, this, INaturalistService.class);
            serviceIntent.putExtra(INaturalistService.FILTERS, mSearchFilters);
            serviceIntent.putExtra(INaturalistService.PAGE_NUMBER, mCurrentResultsPage[resultsType] + 1);
            if (resultsType != VIEW_TYPE_OBSERVATIONS) serviceIntent.putExtra(INaturalistService.PAGE_SIZE, MAX_RESULTS);
            startService(serviceIntent);
        }
    }

    private void refreshViewState() {
        refreshActionBar();
        refreshTabTitles();
        refreshObservations();
        refreshResultsView(VIEW_TYPE_SPECIES, TaxonAdapter.class);
        refreshResultsView(VIEW_TYPE_OBSERVERS, ProjectUserAdapter.class);
        refreshResultsView(VIEW_TYPE_IDENTIFIERS, ProjectUserAdapter.class);
    }



    public class ExplorePagerAdapter extends PagerAdapter {
        final int PAGE_COUNT = 4;
        private Context mContext;

        public ExplorePagerAdapter(Context context) {
            mContext = context;
        }

        @Override
        public int getCount() {
            return PAGE_COUNT;
        }

        @Override
        public Object instantiateItem(ViewGroup collection, int position) {
            int layoutResource = 0;

            switch (position) {
                case VIEW_TYPE_OBSERVATIONS:
                    layoutResource = R.layout.explore_observations;
                    break;
                case VIEW_TYPE_SPECIES:
                    layoutResource = R.layout.project_species;
                    break;
                 case VIEW_TYPE_IDENTIFIERS:
                    layoutResource = R.layout.project_identifiers;
                    break;
                 case VIEW_TYPE_OBSERVERS:
                    layoutResource = R.layout.project_people;
                    break;
            }

            LayoutInflater inflater = LayoutInflater.from(mContext);
            ViewGroup layout = (ViewGroup) inflater.inflate(layoutResource, collection, false);


            if (position == VIEW_TYPE_OBSERVATIONS) {
                mLoadingObservationsGrid = (ProgressBar) layout.findViewById(R.id.loading_observations_grid);
                mObservationsGridEmpty = (TextView) layout.findViewById(R.id.observations_grid_empty);
                mObservationsGrid = (GridViewExtended) layout.findViewById(R.id.observations_grid);
                mObservationsMap = ((SupportMapFragment)getSupportFragmentManager().findFragmentById(R.id.observations_map)).getMap();
                mObservationsMapContainer = (ViewGroup) layout.findViewById(R.id.observations_map_container);
                ViewCompat.setNestedScrollingEnabled(mObservationsGrid, true);

                mObservationsViewModeGrid = (ImageView) layout.findViewById(R.id.observations_grid_view_button);
                mObservationsViewModeMap = (ImageView) layout.findViewById(R.id.observations_map_view_button);
                mObservationsChangeMapLayers = (ImageView) layout.findViewById(R.id.change_map_layers);
                mObservationsMapMyLocation = (ImageView) layout.findViewById(R.id.my_location);
                mRedoObservationsSearch = (ViewGroup) layout.findViewById(R.id.redo_search);
                mPerformingSearch = (ProgressBar) layout.findViewById(R.id.performing_search);

                mObservationsMapMyLocation.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        mMyLocationZoomLevel = mObservationsMap.getCameraPosition().zoom;

                        Intent serviceIntent = new Intent(INaturalistService.ACTION_GET_CURRENT_LOCATION, null, ExploreActivity.this, INaturalistService.class);
                        startService(serviceIntent);
                    }
                });

                if (mLastMapBounds == null) {
                    // Initially zoom to current location
                    mObservationsMapMyLocation.performClick();
                }

                mObservationsMap.setOnMarkerClickListener(new GoogleMap.OnMarkerClickListener() {
                    @Override
                    public boolean onMarkerClick(Marker marker) {
                        JSONObject item = mMarkerObservations.get(marker.getId());

                        Intent intent = new Intent(ExploreActivity.this, ObservationViewerActivity.class);
                        intent.putExtra("observation", item.toString());
                        intent.putExtra("read_only", true);
                        intent.putExtra("reload", true);
                        startActivityForResult(intent, VIEW_OBSERVATION_REQUEST_CODE);

                        try {
                            JSONObject eventParams = new JSONObject();
                            eventParams.put(AnalyticsClient.EVENT_PARAM_VIA, AnalyticsClient.EVENT_VALUE_EXPLORE_MAP);

                            AnalyticsClient.getInstance().logEvent(AnalyticsClient.EVENT_NAME_NAVIGATE_OBS_DETAILS, eventParams);
                        } catch (JSONException e) {
                            e.printStackTrace();
                        }

                        return true;
                    }
                });

                mObservationsMap.setMyLocationEnabled(true);
                mObservationsMap.getUiSettings().setMyLocationButtonEnabled(false);
                mObservationsMap.getUiSettings().setMapToolbarEnabled(false);
                mObservationsMap.getUiSettings().setCompassEnabled(false);
                mObservationsMap.getUiSettings().setIndoorLevelPickerEnabled(false);
                mObservationsMap.setIndoorEnabled(false);
                mObservationsMap.setTrafficEnabled(false);

            } else {
                int loadingListResource = 0;
                int listEmptyResource = 0;
                int listResource = 0;
                int listHeaderResource = 0;
                AdapterView.OnItemClickListener itemClickHandler = null;

                switch (position) {
                    case VIEW_TYPE_SPECIES:
                        loadingListResource = R.id.loading_species_list;
                        listEmptyResource = R.id.species_list_empty;
                        listResource = R.id.species_list;

                        itemClickHandler = new AdapterView.OnItemClickListener() {
                            @Override
                            public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
                                JSONObject item = (JSONObject) view.getTag();
                                Intent intent = new Intent(ExploreActivity.this, TaxonActivity.class);
                                intent.putExtra(TaxonActivity.TAXON, new BetterJSONObject(item));
                                intent.putExtra(TaxonActivity.DOWNLOAD_TAXON, true);
                                startActivity(intent);
                            }
                        };

                        break;
                    case VIEW_TYPE_OBSERVERS:
                        loadingListResource = R.id.loading_people_list;
                        listEmptyResource = R.id.people_list_empty;
                        listResource = R.id.people_list;
                        listHeaderResource = R.id.people_list_header;

                        itemClickHandler = new AdapterView.OnItemClickListener() {
                            @Override
                            public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
                                JSONObject item = (JSONObject) view.getTag();
                                Intent intent = new Intent(ExploreActivity.this, UserProfile.class);
                                intent.putExtra("user", new BetterJSONObject(item));
                                startActivity(intent);
                            }
                        };
                        break;

                    case VIEW_TYPE_IDENTIFIERS:
                        loadingListResource = R.id.loading_identifiers_list;
                        listEmptyResource = R.id.identifiers_list_empty;
                        listResource = R.id.identifiers_list;
                        listHeaderResource = R.id.identifiers_list_header;

                        itemClickHandler = new AdapterView.OnItemClickListener() {
                            @Override
                            public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
                                JSONObject item = (JSONObject) view.getTag();
                                Intent intent = new Intent(ExploreActivity.this, UserProfile.class);
                                intent.putExtra("user", new BetterJSONObject(item));
                                startActivity(intent);
                            }
                        };
                        break;
                }


                mLoadingList[position] = (ProgressBar) layout.findViewById(loadingListResource);
                mListEmpty[position] = (TextView) layout.findViewById(listEmptyResource);
                mList[position] = (ListView) layout.findViewById(listResource);
                if (listHeaderResource != 0) mListHeader[position] = (ViewGroup) layout.findViewById(listHeaderResource);
                ViewCompat.setNestedScrollingEnabled(mList[position], true);

                mList[position].setOnItemClickListener(itemClickHandler);
            }

            collection.addView(layout);

            refreshViewState();

            return layout;
        }

        @Override
        public void destroyItem(ViewGroup collection, int position, Object view) {
            collection.removeView((View) view);
        }
        @Override
        public boolean isViewFromObject(View view, Object object) {
            return view == object;
        }
    }


    @Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		super.onActivityResult(requestCode, resultCode, data);
		if (requestCode == VIEW_OBSERVATION_REQUEST_CODE) {
			if (resultCode == ObservationViewerActivity.RESULT_FLAGGED_AS_CAPTIVE) {
				// Refresh the results (since the user flagged the result as captive)
                loadAllResults();
				return;
			}
		}
	}

}
