package com.jude.lbschat.presentation;

import android.Manifest;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import com.amap.api.maps.AMap;
import com.amap.api.maps.CameraUpdateFactory;
import com.amap.api.maps.MapView;
import com.amap.api.maps.UiSettings;
import com.amap.api.maps.model.BitmapDescriptorFactory;
import com.amap.api.maps.model.CameraPosition;
import com.amap.api.maps.model.LatLng;
import com.amap.api.maps.model.LatLngBounds;
import com.amap.api.maps.model.Marker;
import com.amap.api.maps.model.MarkerOptions;
import com.bumptech.glide.Glide;
import com.github.clans.fab.FloatingActionButton;
import com.jude.beam.bijection.RequiresPresenter;
import com.jude.beam.expansion.BeamBaseActivity;
import com.jude.lbschat.R;
import com.jude.lbschat.data.LocationModel;
import com.jude.lbschat.domain.entities.PersonBrief;
import com.jude.swipbackhelper.SwipeBackHelper;
import com.jude.tagview.TAGView;
import com.jude.utils.JUtils;
import com.tbruyelle.rxpermissions.RxPermissions;

import java.util.ArrayList;
import java.util.HashMap;

import butterknife.Bind;
import butterknife.ButterKnife;

@RequiresPresenter(MainPresenter.class)
public class MainActivity extends BeamBaseActivity<MainPresenter> implements AMap.OnMarkerClickListener, AMap.OnMapClickListener {

    @Bind(R.id.map)
    MapView mMapView;
    @Bind(R.id.type)
    ImageView type;
    @Bind(R.id.location)
    ImageView location;
    @Bind(R.id.zoom_in)
    ImageView zoomIn;
    @Bind(R.id.zoom_out)
    ImageView zoomOut;
    @Bind(R.id.edit)
    FloatingActionButton edit;

    private AMap aMap;
    private UiSettings mUiSettings;
    private HashMap<Marker, PersonBrief> mMarkerMap;
    private Marker mMyLocation;
    private int mStatus = 0;

    private static final int MIN_ZOOM_MARKER_COUNT = 5;
    private static final int MIN_ZOOM= 13;

    private final static int[] ZOOM_LEVEL = {
            500000, 500000, 500000, 500000, 500000, 200000, 100000, 50000, 30000, 20000, 10000, 5000, 2000, 1000, 500, 200, 100, 50, 25, 10
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        ButterKnife.bind(this);
        RxPermissions.getInstance(this).request(
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION)
                .subscribe(aBoolean -> {
                    if (!aBoolean){
                        JUtils.Toast("抱歉~");
                        finish();
                    }
                });
        getSupportActionBar().setDisplayHomeAsUpEnabled(false);
        SwipeBackHelper.getCurrentPage(this).setSwipeBackEnable(false);

        mMapView.onCreate(savedInstanceState);
        zoomIn.setOnClickListener(v -> {
            aMap.animateCamera(CameraUpdateFactory.zoomIn());
        });
        zoomOut.setOnClickListener(v -> {
            aMap.animateCamera(CameraUpdateFactory.zoomOut());
        });
        location.setOnClickListener(v -> {
            moveTo(mMyLocation.getPosition().latitude, mMyLocation.getPosition().longitude);
        });
        type.setOnClickListener(v -> {
            if (mStatus == 0) {
                aMap.setMapType(AMap.MAP_TYPE_SATELLITE);
                mStatus = 1;
                type.setImageResource(R.drawable.ic_map_paper_orange);
            } else {
                aMap.setMapType(AMap.MAP_TYPE_NORMAL);
                mStatus = 0;
                type.setImageResource(R.drawable.ic_map_moon_orange);
            }
        });
        initMap();
    }


    private void initMap() {
        aMap = mMapView.getMap();
        mUiSettings = aMap.getUiSettings();
        mUiSettings.setZoomControlsEnabled(false);
        mUiSettings.setScaleControlsEnabled(true);
        mUiSettings.setMyLocationButtonEnabled(false);
        moveTo(LocationModel.getInstance().getCurLocation().getLatitude(), LocationModel.getInstance().getCurLocation().getLongitude(), 13);
        initMyPoint();

        aMap.setOnMarkerClickListener(this);
        aMap.setOnMapClickListener(this);
        aMap.setInfoWindowAdapter(new AMap.InfoWindowAdapter() {
            @Override
            public View getInfoWindow(Marker marker) {
                JUtils.Log("Create View");

                PersonBrief placeBrief = mMarkerMap.get(marker);
                if (placeBrief == null) return null;
                View infoContent = getLayoutInflater().inflate(
                        R.layout.window_info, null);

                ((TextView) (infoContent.findViewById(R.id.tv_name))).setText(placeBrief.getName());
                //((TextView) (infoContent.findViewById(R.id.tv_distance))).setText(DistanceFormat.parse(LocationModel.getInstance().getDistance(placeBrief.getLat(), placeBrief.getLng())));
                ((TextView) (infoContent.findViewById(R.id.tv_intro))).setText(placeBrief.getIntro());
                TAGView tagView = $(infoContent,R.id.tag_gender);
                if (placeBrief.getGender()==0){
                    tagView.setBackgroundColor(getResources().getColor(R.color.blue));
                    tagView.setIcon(R.drawable.ic_male);
                }else {
                    tagView.setBackgroundColor(getResources().getColor(R.color.pink));
                    tagView.setIcon(R.drawable.ic_female);
                }
                Glide.with(MainActivity.this)
                        .load(placeBrief.getAvatar())
                        //.bitmapTransform(new CropCircleTransformation(MainActivity.this))
                        .into((ImageView) $(infoContent,R.id.img_avatar));

                infoContent.setOnClickListener(v -> {
//                    Intent i = new Intent(MainActivity.this, PlaceDetailActivity.class);
//                    i.putExtra("id", mMarkerMap.get(marker).getId());
//                    getContext().startActivity(i);
                    JUtils.Toast("Click"+placeBrief.getName());
                });

                return infoContent;
            }

            @Override
            public View getInfoContents(Marker marker) {
                return null;
            }
        });
        mMarkerMap = new HashMap<>();
    }

    private void moveToAdjustPlace(ArrayList<PersonBrief> personBriefs) {
        double maxDistance = 0;
        double myLat = LocationModel.getInstance().getCurLocation().getLatitude();
        double myLng = LocationModel.getInstance().getCurLocation().getLongitude();
        for (PersonBrief placeBrief : personBriefs) {
            double distance = JUtils.distance(myLng,myLat, placeBrief.getLng(), placeBrief.getLat());
            if (distance > maxDistance) {
                maxDistance = distance;
            }
        }
        int unit = (int) (maxDistance / 8);
        for (int i = ZOOM_LEVEL.length - 1; i >= 1; i--) {
            if (unit > ZOOM_LEVEL[i] && unit < ZOOM_LEVEL[i-1]) {
                moveTo(LocationModel.getInstance().getCurLocation().getLatitude(), LocationModel.getInstance().getCurLocation().getLongitude(), i - 1);
                return;
            }
        }
    }

    private void initMyPoint() {
        MarkerOptions markerOption = new MarkerOptions();
        markerOption.icon(BitmapDescriptorFactory
                .fromResource(R.drawable.location_marker));
        mMyLocation = aMap.addMarker(markerOption);
        LocationModel.getInstance().registerLocationChange(location -> {
            JUtils.Log("latitude"+location.latitude+"  longitude"+location.longitude);
            mMyLocation.setPosition(new LatLng(location.latitude, location.longitude));
        });
    }

    public void clearMarker(){
        if (aMap!=null){
            aMap.clear();
            initMyPoint();
            zoomMarkerList.clear();
        }
    }

    ArrayList<PersonBrief> zoomMarkerList = new ArrayList<>();
    public void addMarker(PersonBrief place) {
        MarkerOptions markerOption = new MarkerOptions();
        markerOption.position(new LatLng(place.getLat(), place.getLng()));
        markerOption.title(place.getName()).snippet(place.getAddressBrief());
        markerOption.icon(BitmapDescriptorFactory
                .fromResource(R.drawable.location_point_red));
        Marker marker = aMap.addMarker(markerOption);
        marker.setToTop();
        mMarkerMap.put(marker, place);
        if (mMarkerMap.size() < MIN_ZOOM_MARKER_COUNT+1) zoomMarkerList.add(place);
        if (mMarkerMap.size()== MIN_ZOOM_MARKER_COUNT+1) moveToAdjustPlace(zoomMarkerList);
    }


    /**
     *  不用post会报“the mMapView must have a size”错误，why
     *
     * @param person
     */
    private void zoomMarker(ArrayList<PersonBrief> person) {
        mMapView.post(() -> {
            LatLngBounds.Builder boundsBuild = new LatLngBounds.Builder();
            boundsBuild.include(mMyLocation.getPosition());
            for (PersonBrief placeBrief : person) {
                boundsBuild.include(new LatLng(placeBrief.getLat(), placeBrief.getLng()));
            }
            aMap.moveCamera(CameraUpdateFactory.newLatLngBounds(boundsBuild.build(), 10));
        });
    }

    private void moveTo(double lat, double lng, float zoom) {
        aMap.animateCamera(CameraUpdateFactory.newCameraPosition(new CameraPosition(
                new LatLng(lat, lng), zoom, 0, 0
        )));
    }

    private void moveTo(double lat, double lng) {
        aMap.animateCamera(CameraUpdateFactory.newCameraPosition(new CameraPosition(
                new LatLng(lat, lng), aMap.getCameraPosition().zoom, 0, 0
        )));
    }


    Marker lastMarker;

    @Override
    public boolean onMarkerClick(Marker marker) {
        if (marker.equals(mMyLocation))return false;
        if (lastMarker != null) lastMarker.setIcon(BitmapDescriptorFactory
                .fromResource(R.drawable.location_point_red));
        moveTo(marker.getPosition().latitude, marker.getPosition().longitude);
        marker.setIcon(BitmapDescriptorFactory
                .fromResource(R.drawable.location_point_bigger_red));
        lastMarker = marker;
        return false;
    }

    @Override
    public void onMapClick(LatLng latLng) {
        if (lastMarker != null) lastMarker.hideInfoWindow();
    }



    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }
}
