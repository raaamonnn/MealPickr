package com.example.projexample

import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.location.Location
import android.os.Bundle
import android.util.Log
import android.view.*
import androidx.core.os.bundleOf
import androidx.databinding.DataBindingUtil
import androidx.fragment.app.Fragment
import androidx.fragment.app.setFragmentResult
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import androidx.preference.PreferenceManager
import com.example.projexample.database.RestaurantDatabase
import com.example.projexample.databinding.FragmentHomeBinding
import com.example.projexample.model.YelpRestaurant
import com.example.projexample.model.YelpSearchResult
import com.example.projexample.service.SVCYelp
import com.example.projexample.viewmodel.RestaurantViewModel
import com.example.projexample.viewmodel.RestaurantViewModelFactory
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.net.URL
import kotlin.random.Random


private const val YELP_KEY = "J0dsBRluxFk2aGoeqvKv4G4tceXQMHtR3arQq3_DBLbTAXDq20QDhXXqTj_4E2UCGQBg0WHpfaWt4MEIDOGCn8vXRdmAI02Tg0QopOELt2yDgzSpuNK8NKCruSVOYXYx"
private const val TAG = "MapsActivity"

class HomeFragment : Fragment(), OnMapReadyCallback, GoogleMap.OnInfoWindowClickListener {

    private lateinit var mMap: GoogleMap
    private lateinit var binding: FragmentHomeBinding

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var userLocation: LatLng
    private val TAG = "HomeFragment"

    public val restaurants = mutableListOf<YelpRestaurant>()

    lateinit var settings: SharedPreferences
    var listTitles = mutableListOf<CharSequence>()
    var distin = listTitles.distinct().toTypedArray()
    //default properties if the preferencemanager can not be accessedd
    var filterNum = "0"
    var range = 10

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        settings = PreferenceManager.getDefaultSharedPreferences(activity?.baseContext)
        // Inflate the layout for this fragment
        binding = FragmentHomeBinding.inflate(layoutInflater)

        binding.button.setOnClickListener {
            mMap.clear()
            getLocationNoCamera()

            //gets the list of restaurants
            var listRestaurants = ArrayList<YelpRestaurant>()
            //re-populate the restaurants for listRestaurants
            var listRestaurantsCat = ArrayList<YelpRestaurant>()
            //gets repopulated
            listRestaurants = getRestuarantCategories(listRestaurantsCat) as ArrayList<YelpRestaurant>
            //gets the categories of the restaurants
            listTitles = getCategoriesTitles(listRestaurants)
            //repopulate the restaurants so that its filtered out
            listRestaurants =
                getFilteredRestaurantCategories(listRestaurants, listTitles.distinct().toMutableList()) as ArrayList<YelpRestaurant>
            //gets a random restaurant
            var randomFilteredRestaurant = getRandomFilteredRestaurantsList(listRestaurants)
            //displays the restaurants onto the map
            displayRandomRestaurant(randomFilteredRestaurant, listRestaurants, listTitles.distinct().toMutableList())

            // moves the camera
            var randRestLocation = LatLng(
                randomFilteredRestaurant.coordinates.latitude,
                randomFilteredRestaurant.coordinates.longitude
            )
            mMap.moveCamera(CameraUpdateFactory.newLatLng(randRestLocation))
        }
        val mapFragment = childFragmentManager.findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(activity)

        //return view;
        return binding.root
    }


    override fun onResume() {
        super.onResume()
        //displayFilteredRestaurants()
    }

    /**
     * Manipulates the map once available.
     * This callback is triggered when the map is ready to be used.
     * This is where we can add markers or lines, add listeners or move the camera. In this case,
     * we just add a marker near Sydney, Australia.
     * If Google Play services is not installed on the device, the user will be prompted to install
     * it inside the SupportMapFragment. This method will only be triggered once the user has
     * installed Google Play services and returned to the app.
     */
    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap
        getLocation()
    }

    private fun getLocation() {
        checkPermission()
        fusedLocationClient.lastLocation
            .addOnSuccessListener { location : Location? ->
                if (location != null) {
                    userLocation = LatLng(location.latitude, location.longitude)
                    //adds the marker
                    mMap.addMarker(MarkerOptions().position(userLocation).title("Your Location")
                        .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_ORANGE)))
                    //moves the camera when startup
                    mMap.moveCamera(CameraUpdateFactory.newLatLng(userLocation))

                    //Sets the zoom
                    mMap.setMaxZoomPreference(25f)
                    mMap.setMinZoomPreference(12f)

//                    //loads restaurants after userLocation is known
                    getRestaurants()
                } else {
                    print("No Previous Location")
                }
            }
    }

    //gets the location pin without moving the camera
    private fun getLocationNoCamera() {
        checkPermission()
        fusedLocationClient.lastLocation
            .addOnSuccessListener { location : Location? ->
                if (location != null) {
                    userLocation = LatLng(location.latitude, location.longitude)
                    //adds the marker
                    mMap.addMarker(MarkerOptions().position(userLocation).title("Your Location")
                        .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_ORANGE)))

                } else {
                    print("No Previous Location")
                }
            }
    }

    //gets the restaurant categories from the restaurant object
    fun getRestuarantCategories(listRestaurants: MutableList<YelpRestaurant>) : MutableList<YelpRestaurant> {
        restaurants.forEach { restaurant ->
            for (i in restaurant.categories) {
                listRestaurants.add(restaurant)
            }
        }
        Log.i(TAG, "categories ${listRestaurants}")
        return listRestaurants
    }

    //gets the titles of the categories
    fun getCategoriesTitles(listRestaurants: MutableList<YelpRestaurant>): MutableList<CharSequence> {
        if (this::settings.isInitialized) {
            range = settings.getInt("rangeSelector", 10)
        }
        for (names in listRestaurants) {
            if (names.distance < (range * 1000) && names.is_closed == "false") {
                for (titles in names.categories) {
                    listTitles.add(titles.title)
                }
            }
        }
        return listTitles
    }

    //gets the filtered categories from the restaurants
    fun getFilteredRestaurantCategories(listRestaurants: MutableList<YelpRestaurant>, listTitles: MutableList<CharSequence>): MutableList<YelpRestaurant> {
        if (this::settings.isInitialized) {
            filterNum = settings.getString("categoryPicker", "").toString()
            range = settings.getInt("rangeSelector", 10) //meters 1000 meters = 1km
        }
        var filter: CharSequence? = null
        var listRestaurantsCat = ArrayList<YelpRestaurant>()
        if (filterNum != "0") {
            if (filterNum != null) {
                filter = listTitles[filterNum.toInt() - 1]
            }
        } else {
            filter = null
        }
        //contains duplicates because it is getting called multiple times
        listRestaurants.forEach { restaurant ->
            if (restaurant.distance < (range * 1000) && restaurant.is_closed == "false") {
                for (i in restaurant.categories) {
                    if (filter == null || i.title.contains(filter.toString(), ignoreCase = true)) {
                        listRestaurantsCat.add(restaurant)
                    }
                }
            }
        }
        return listRestaurantsCat
    }

    private fun checkPermission() {

    }

    //Network request. Range == Meters
    private fun getRestaurants() {
        val retrofit = Retrofit.Builder().baseUrl("https://api.yelp.com/v3/").addConverterFactory(GsonConverterFactory.create()).build()
        val svcYelp = retrofit.create(SVCYelp::class.java)
        //10km radius
        svcYelp.searchRestaurants("Bearer $YELP_KEY", 10000, userLocation.longitude, userLocation.latitude).enqueue(object :
            Callback<YelpSearchResult> {
            override fun onResponse(call: Call<YelpSearchResult>, response: Response<YelpSearchResult>) {
                val body = response.body()
                if (body == null) {
                    Log.i(TAG, "Invalid Response Body from Yelp API")
                    return
                }
                restaurants.addAll(body.restaurants)
                displayFilteredRestaurants()

                //Send Category over to settings
                var listRestaurants = ArrayList<YelpRestaurant>()
                var listRestaurantsCat = ArrayList<YelpRestaurant>()

                listRestaurants = getRestuarantCategories(listRestaurantsCat) as ArrayList<YelpRestaurant>
                Log.i(TAG, "This listRest in button ${listRestaurants.size}")
                listRestaurants =
                    getFilteredRestaurantCategories(listRestaurants, listTitles.distinct().toMutableList()) as ArrayList<YelpRestaurant>
                listTitles = getCategoriesTitles(listRestaurants)
                //need to convert to the right datatype to send it
                distin = listTitles.distinct().toTypedArray()
                //sends the list of categories to the settingsfragment
                setFragmentResult("dataFromHome", bundleOf("list" to distin))
            }

            override fun onFailure(call: Call<YelpSearchResult>, t: Throwable) {
                Log.i(TAG, "Failed To Get Restaurants $t")
            }
        })
    }

    // gets the random restaurant
    fun getRandomFilteredRestaurantsList(listRestaurants: MutableList<YelpRestaurant>) : YelpRestaurant {
        val randomIndex = Random.nextInt(listRestaurants.size);
        return listRestaurants[randomIndex]
    }

    //display filtered restaurants
    private fun displayFilteredRestaurants() {
        val range = settings.getInt("rangeSelector", 10) //meters 1000 meters = 1km
        var listRestaurants = ArrayList<YelpRestaurant>()
        var listRestaurantsCat = ArrayList<YelpRestaurant>()

        listRestaurants = getRestuarantCategories(listRestaurantsCat) as ArrayList<YelpRestaurant>
        Log.i(TAG, "This listRest in button ${listRestaurants.size}")
        listTitles = getCategoriesTitles(listRestaurants)
        listRestaurants =
            getFilteredRestaurantCategories(listRestaurants, listTitles.distinct().toMutableList()) as ArrayList<YelpRestaurant>

        listRestaurants.forEach { restaurant ->
            if (restaurant.distance < (range * 1000) && restaurant.is_closed == "false") {
                Log.i(TAG, "This rest in display ${restaurant}")
                for (i in restaurant.categories) {
                    val url = URL("${restaurant.image}")
                    mMap.addMarker(
                        MarkerOptions()
                            .position(LatLng(restaurant.coordinates.latitude, restaurant.coordinates.longitude))
                            .title("${restaurant.name}\n")
                            .snippet("${restaurant.phone}")
                            .draggable(true)
                    )
                    mMap.setOnInfoWindowClickListener(this);
                }
            }
        }
    }

    //displays the restaurants to the map
    private fun displayRandomRestaurant(randR: YelpRestaurant, listRestaurants: MutableList<YelpRestaurant>, titleList: MutableList<CharSequence>) {
        val filterNum = settings.getString("categoryPicker", "")
        var filter: CharSequence? = null
        var listRestaurantsCat = ArrayList<YelpRestaurant>()
        if (filterNum != "0") {
            if (filterNum != null) {
                filter = titleList[filterNum.toInt() - 1]
            }
        } else {
            filter = null
        }
        var range = settings.getInt("rangeSelector", 0) //meters 1000 meters = 1km

        listRestaurants.forEach { restaurant ->
            if (restaurant.distance < (range * 1000) && restaurant.is_closed == "false") {
                for (i in restaurant.categories) {
                    if(filter == null || i.title.contains(filter.toString(), ignoreCase = true)) {
                        val url = URL("${restaurant.image}")
                        if(randR != restaurant) {
                            mMap.addMarker(
                                MarkerOptions()
                                    .position(
                                        LatLng(
                                            restaurant.coordinates.latitude,
                                            restaurant.coordinates.longitude
                                        )
                                    )
                                    .title("${restaurant.name}\n")
                                    .snippet("${restaurant.phone}")
                                    .draggable(true)
                            )
                        } else {
                            val mark = mMap.addMarker(
                                MarkerOptions()
                                    .position(
                                        LatLng(
                                            restaurant.coordinates.latitude,
                                            restaurant.coordinates.longitude
                                        )
                                    )
                                    .title("${restaurant.name}\n")
                                    .snippet("${restaurant.phone}")
                                    .draggable(true)
                                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_BLUE))
                            )
                            mark.showInfoWindow()
                        }
                        mMap.setOnInfoWindowClickListener(this);
                    }
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        setHasOptionsMenu(true)
        super.onCreate(savedInstanceState)
    }

    //Opens new infowindow page
    override fun onInfoWindowClick(p0: Marker) {
        val restaurant = findRestaurantByPhone(p0.snippet)
        if (restaurant != null) {
            this.findNavController().navigate(
                HomeFragmentDirections
                    .actionHomeFragmentToInfoWindow(restaurant.name, restaurant.price, restaurant.image, restaurant.rating.toString(), restaurant.categories[0].title, restaurant.distance.toString(), restaurant.phone, restaurant.location.address)
            )
        }
    }
    fun findRestaurantByPhone(phone: String): YelpRestaurant? {
        restaurants.forEach { restaurant ->
            if(restaurant.phone == phone) {
                return restaurant
            }
        }
        return null
    }
}